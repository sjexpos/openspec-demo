# Code Review Report (Pass 3) — KAN-8 Create Product

- Date: 2026-09-10
- Change: `KAN-8-create-product`
- Branch: `feat/KAN-8-create-product` (vs. `main`), HEAD `3e6b335`, working tree clean
- Agent: `code-review` (third adversarial pass, after two FAILs and three corrective batches)
- Mode: **Independent re-derivation.** The five prior reports were read in full for context, but every
  conclusion below was re-derived from the code, from builds this agent ran, or from **nine mutation
  experiments this agent executed**. Prior self-reports were treated as claims to be checked — this
  pipeline has already produced a fabricated SpotBugs count, a fabricated `git stash` methodology
  claim, and an overstated "zero compiler warnings" claim.
- Prior artifacts in the audit trail (none overwritten):
  `reports/2026-09-10-code-review-report.md` (pass 1, **FAIL**),
  `reports/2026-09-10-code-review-report-2.md` (pass 2, **FAIL**),
  `reports/2026-09-10-verify-report.md`, `-2.md`, `-3.md` (all PASS WITH WARNINGS).

---

## 1. What this pass did differently

The three prior corrective batches were driven by mutations of `ProductController`. Verify pass 3
added four mutations of `ProductServiceImpl`. **Every one of those seven mutations was a
same-typed-field transposition.** This pass deliberately went outside that class: it mutated the
JPA persistence mapping, the validation-constraint *values*, the transaction boundary, and
field pairs no prior pass had selected — on the premise that three rounds of fixing transpositions
is exactly the condition under which a different failure class hides.

### Build evidence (re-run by this agent from a clean tree)

| Command | Result |
|---|---|
| `mvn -o -Dspotbugs.skip=true -Dpitest.skip=true clean verify` | `BUILD SUCCESS` — Surefire **53/53**, Failsafe **59/59** = **112 green**. Matches verify pass 3 exactly. |
| `mvn -o -Dspotbugs.skip=true spotless:check` | exit 0, clean |
| Database after all experiments | `products`, `brands`, `strains`, `units` all **0 rows**; `pg_indexes` on `products` → `products_pkey` only (zero schema drift) |
| Restoration | All four mutated files `diff`-confirmed **byte-identical** to their pre-experiment backups; `git status --porcelain` empty |

### Mutation experiments executed by this agent

Each applied to a pristine file, run through the full `verify` suite, then reverted and
byte-compared. **None of these nine mutations was run by any prior pass.**

| # | Mutation | File | Result |
|---|---|---|---|
| N1 | `.formatUnit(formatUnit)` ↔ `.contentUnit(contentUnit)` — the resolved **objects** in the builder (verify-3's mutation C swapped the *ids at resolution*, a different site) | `ProductServiceImpl` | **KILLED** — `ProductServiceTests.should_persistProductOnce_when_commandIsValid:148` |
| N2 | `.approved(...)` ↔ `.enabled(...)` — the boolean pair verify pass 3 did **not** test | `ProductServiceImpl` | **KILLED** — but unit suite **53/53 green**; killed only by `ProductEndpointsTests.should_persistSuppliedFlags_when_theyAreExplicit:618` |
| N3 | `.formatValue(...)` ↔ `.cbd(...)` — a same-typed `Integer` pair no prior pass considered | `ProductServiceImpl` | **KILLED** — but *accidentally*, by `should_persistNulls_when_optionalAttributesAreOmitted:361`, **not** by the field-by-field mapping assertion that is supposed to be the guard |
| N4 | `@JoinColumn(name="format_unit_id")` ↔ `@JoinColumn(name="content_unit_id")` — **JPA persistence mapping**, a class no prior pass mutated at all | `Product` | **KILLED** — `ProductEndpointsTests.should_return201AndPersistProduct_when_requestIsValid:287` |
| N5 | `getSubcategory().getId()` ↔ `getBrand().getId()` in the 18-argument positional record constructor | `ProductController` | **KILLED** — `ProductControllerTests.create_shouldReturn201_when_validRequest:182` |
| **N6** | `@Size(max=64)`→`40` on `ocpc` **and** `@Size(max=255)`→`40` on `title` (bounds **tightened**, not removed) | `CreateProductRequest` | **SURVIVED — `BUILD SUCCESS`, 112/112 green** |
| **N8** | `@Transactional(rollbackFor=..., propagation=REQUIRED)` **deleted entirely** | `ProductServiceImpl` | **SURVIVED — `BUILD SUCCESS`, 112/112 green** |
| **N9** | `@NotNull` deleted from `brandId` | `CreateProductRequest` | **SURVIVED — `BUILD SUCCESS`, 112/112 green** |

Six killed, three survived. **Every mutation that would change observable behaviour on a valid
request was killed.** The three survivors change behaviour only for inputs the suite never sends
(N6, N9) or change nothing observable at all today (N8). That is a categorically different state
from the two prior FAILs, where a mutation silently corrupted the data a valid request returned
(pass 1) or persisted (pass 2).

---

## 2. Independent judgment on the mapping/construction logic (brief item 1)

I re-derived the type inventories and the three mapping sites myself rather than re-reading
verify pass 3's table. A transposition is only *possible* between fields of the same Java type
(the compiler kills the rest) and only *detectable* if some fixture gives the pair different
values. My independent tabulation:

| Site | Same-typed groups | Discrimination | Verdict |
|---|---|---|---|
| `ProductController.create` (request → command, 17 named-builder fields) | `String`{ocpc,title,description}, `Long`{7 ids}, `Integer`{formatValue,contentValue,thc,cbd}, `Boolean`{isCoreProduct,approved,enabled} | `ArgumentCaptor` asserts all 17 against distinct fixture values; booleans need 2 fixtures (pigeonhole) and have them: `(T,F,T)` + `(F,T,T)` | **Adequate** |
| `ProductController.toCreateProductResponse` (18 **positional** record args) | `Long`{id=100, 1..7} — 8 distinct; `Integer`{10,20,15,5}; `String` distinct; `Boolean` 2 fixtures | All 18 `$.data.*` asserted; N5 confirms empirically | **Adequate** |
| `ProductServiceImpl.create` (command + refs → entity, 17 named-builder fields) | Only `formatUnit`/`contentUnit` share a Java type among the 7 refs; `Integer` and `Boolean` groups as above | Killed end-to-end (N1–N3), but see m3 on the unit-level half | **Adequate at suite level** |

**Things I looked for that no prior report examined, and found clean:**

- **The raw-JSON wire contract.** `ProductControllerTests` serialises a `CreateProductRequest`
  *object* with the same Lombok accessors Jackson uses to deserialise it, so a property-naming
  divergence (e.g. Lombok/Jackson resolving `Boolean isCoreProduct` to `coreProduct`) would
  round-trip symmetrically and be **invisible** in that file. This is a real hazard that the
  controller test structurally cannot see. It is closed by `ProductEndpointsTests.validBody()`,
  which builds a raw `ObjectNode` with all 17 literal JSON keys, combined with the happy-path
  native-query assertions (15 of 17 fields) and the two flag tests (the remaining 3). All 17
  wire-level property names are therefore pinned. Directly mitigates an undocumented risk.
- **JPA column/`@JoinColumn` mapping** against `V0.1.0__initialData_SM.sql:205-230` — all 17
  mapped attributes match their columns; N4 confirms the riskiest pair is pinned end-to-end.
- **Mass assignment** — `CreateProductRequest` declares no audit/lifecycle/`id` field, so Spring
  Boot's default `FAIL_ON_UNKNOWN_PROPERTIES=false` cannot be abused to set one.
- **Duplication mutations** (reading the same getter twice rather than transposing two) are caught
  by the same `ArgumentCaptor`/`jsonPath` assertions.

**Conclusion on brief item 1: yes, the mapping and construction logic in this slice is now
adequately tested.** I could not construct a mapping mutation that survives.

---

## 3. Verdict on verify-pass-3's `ProductServiceImpl` finding (brief item 2)

**My answer: non-blocking — but verify pass 3 understated its scope, and I am recording the
sharper version rather than deferring to its label.**

What I found by re-deriving it: `ProductServiceTests.validCommandBuilder()` sets
`isCoreProduct/approved/enabled` all `TRUE` (so it discriminates **zero** of the three boolean
pairs), and `formatValue=1, contentValue=1, cbd=1` — **three** same-typed `Integer` fields sharing
the value `1`, not the two verify-3 named. The other two boolean fixtures are all-`FALSE` and
all-`null`, equally undiscriminating. Consequences I proved:

- N2 (`approved`↔`enabled`) — a pair verify pass 3 never tested — leaves the unit suite **53/53
  green**; killed only by an integration test.
- N3 (`formatValue`↔`cbd`) is killed at unit level, but only *incidentally* by the
  optional-attributes-null test (cbd is nulled there, formatValue is not), not by
  `should_persistProductOnce_when_commandIsValid`'s field-by-field assertions. That kill would
  evaporate if the nulls test were ever narrowed.

So the blind spot is at least four mutation classes wide, not two.

**Why I still do not treat it as a blocker:**

1. The question a code review must answer is *can a defect ship undetected?* It cannot. The gate
   this project actually runs is `mvn verify`; there is no pipeline stage that gates on Surefire
   alone. Every mutation in this class dies before merge.
2. The prior two FAILs were qualitatively different: there, the **entire** suite — unit *and*
   integration, 108 and 110 tests respectively — was blind, and wrong data could ship. Applying
   the same severity here would flatten a real distinction, not enforce a consistent standard.
3. The integration fixtures doing the killing are not incidental coverage. Batches 7 and 8 built
   them deliberately for this purpose, with in-code comments naming the pigeonhole argument. They
   are documented, intentional, load-bearing assertions.
4. `ProductEndpointsTests` is not a fragile backstop: it runs in the standard `verify`, against a
   real PostgreSQL, and I re-ran it green three times during this pass.

**Why it is still worth fixing before the follow-up ticket goes stale:** it is a single point of
failure. The project's own `tasks.md` repeatedly records `mvn test` as a verification step, and
that command now returns a false green for four distinct service-layer mapping defects. The fix is
~4 lines of fixture change with zero production risk. Recorded as **m3**, the highest-value
remaining follow-up.

---

## 4. Findings

| Severity | Area | Finding | Evidence | Suggested fix (code / spec / tests) |
|---|---|---|---|---|
| **Minor (NEW)** | Test adequacy / spec R2 | **m1 — the two `@Size` bounds are pinned only in the *reject* direction.** `create_shouldReturn400_when_ocpcExceedsMaxLength` / `_titleExceedsMaxLength` prove max+1 is rejected, but **nothing proves a value at the documented limit is accepted**. A silently tightened bound is invisible to the whole suite. This is the surviving residue of pass-1 Major #2, which no prior report identified: pass 1, verify 2 and task 6.11 all tested *removal* or *widening*, never *tightening*. `docs/backend-standards.md:480` mandates "Edge Cases: **Boundary values**", which means both sides of a boundary. | **Proven experimentally (N6)**: `@Size(max=64)`→`40` on `ocpc` **and** `@Size(max=255)`→`40` on `title`, simultaneously → `mvn -o -Dspotbugs.skip=true -Dpitest.skip=true verify` = `BUILD SUCCESS`, 53 + 59 = **112/112 green**. (Only a bound below ~21 is caught incidentally, because the longest fixture value is `"OCPC-ATOMIC-TAXONOMY"` at 20 chars.) Tree reverted byte-identical. | **Tests**: two controller tests — `ocpc = "a".repeat(64)` → 201 and `title = "a".repeat(255)` → 201. Pairs with the existing max+1 tests to give a true two-sided boundary. |
| **Minor (NEW)** | Artifact accuracy / design D6 | **m2 — D6's transaction boundary is not pinned by any test, and three artifacts claim it is.** `tasks.md` 8.11 calls `should_leaveProductCountUnchanged_when_requestIsRejected` "**the end-to-end proof of D6**"; `verify-report-3` §6 records "Atomicity (D6) … **proven end-to-end by row-count invariance** across all 5 rejection paths"; `verify-report.md`'s R7 row reads similarly. That test proves the **spec requirement R7** (a rejected request persists nothing), which this implementation achieves by *guard ordering* — all three guards precede the single `save`. It does not exercise the transaction at all. The spec claim is sound; the mapping of that test onto D6's *mechanism* is an overreach, and it is the fourth overstated self-report in this pipeline. | **Proven experimentally (N8)**: deleting `@Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)` from `ProductServiceImpl.create` entirely → **112/112 green**, `BUILD SUCCESS`. Not a defect: with exactly one write and Spring Data's own `@Transactional` on `SimpleJpaRepository.save`, behaviour is identical today — which is precisely why no test can see it. | **Artifacts**: reword `tasks.md` 8.11 to "the end-to-end proof of spec R7"; note in `design.md` D6 that the annotation is correct and required for future multi-write evolution but is **not** test-observable while `create` performs a single write. No code change — the annotation should stay. |
| **Minor (NEW, sharpens verify-3 S1)** | Test adequacy | **m3 — `ProductServiceTests`'s unit-level blindness is wider than S1 recorded.** `validCommandBuilder()` has all three booleans `TRUE` **and** `formatValue=1, contentValue=1, cbd=1` (three same-typed `Integer`s sharing a value, not two). At least four service-layer mapping mutation classes leave `mvn test` green. See §3 for my full reasoning on severity. | N2 (`approved`↔`enabled`, a pair verify-3 did not test) → unit **53/53 green**; N3 (`formatValue`↔`cbd`) killed only *accidentally* by the nulls test. Fixture at `ProductServiceTests.java:94-113`. | **Tests** (~4 lines, test-only): give `validCommandBuilder()` distinct booleans (`T/F/T`) plus one variant with `isCoreProduct != enabled`, and distinct `formatValue`/`contentValue`/`cbd`. |
| **Minor (NEW)** | Security | **m4 — first instance of logging client-controlled free text (CWE-117, log injection).** `ProductServiceImpl:81` emits `log.warn("Rejected product creation: OCPC {} already used by a live product", command.ocpc())`. `ocpc` carries only `@NotBlank` + `@Size(max = 64)`, so up to 64 characters of arbitrary client input — including `\n`, `\r` and ANSI escapes — reach the log verbatim on an unauthenticated endpoint (proposal item 4), permitting forged log lines. Impact is low here (no log-based security control, and Logback's default pattern is not consumed by a downstream parser), but it is a **new** pattern for this codebase and is disclosed nowhere. | `grep -rln "log\." src/main/java` → only `ProductServiceImpl.java` and `GlobalExceptionHandler.java`. `BrandServiceImpl`, `DispensaryServiceImpl` and `AddressServiceImpl` contain **no** logging at all, so there is no precedent to inherit. The other three call sites are safe: they log `Long` ids or the hardcoded `referenceName`. | **Artifacts**: add to `proposal.md`'s open items beside 11/12. **Code (optional, follow-up)**: sanitise or elide `ocpc` in that one statement — the id-based `log.info` at `:133` already shows the safe pattern. |
| **Minor (NEW)** | Audit-trail hygiene | **m5 — verify pass 1's S5 silently dropped out of the audit trail.** `ProductEndpointsTests.tearDown` (`:126-150`) issues unconditional `DELETE FROM` against nine tables plus `brandTypeRepository.deleteAll()`, rather than deleting the ids it seeded. Verify pass 1 raised this as S5; passes 2 and 3 do not mention it, no corrective task references it, and no living artifact records it as accepted. Safe against the current all-zero baseline (re-confirmed by me after four full runs), but it would wipe pre-existing rows on any seeded database. The defect worth naming is not the `DELETE` — it is that an open item vanished without an accept/reject decision. | `ProductEndpointsTests.java:126-150`; `grep` for S5 across `tasks.md` / `apply-progress.md` → no hits. | **Artifacts**: record an explicit accept-or-defer decision. **Tests (optional)**: scope the cleanup to the ids created in `setUp()`. |
| Minor (carried, now **proven**) | Test adequacy / spec R2 | **m6 — verify W2 residuals confirmed by mutation, not just by `grep`.** `@NotNull` on the seven reference ids, `contentValue = 0` and negative `cbd` still have no test instance. Every prior pass established this by searching for absent tests; I established it by deletion. | **Proven experimentally (N9)**: deleting `@NotNull` from `brandId` → **112/112 green**, `BUILD SUCCESS`. Note the blast radius (by code reading, not measured): with `@NotNull` gone, `@Positive` passes `null` per Bean Validation semantics, so `resolveOrNotFound` calls `findById(null)` → `SimpleJpaRepository`'s `Assert.notNull` → `IllegalArgumentException` → the catch-all handler → **500**, not the R2-mandated 400. The annotation is load-bearing for the error contract. | **Tests**: `brandId(null)` → 400 and `contentValue(0)` → 400. I **concur with pass 2 and verify 3 at Minor** — the `@NotNull` and `@Positive` mechanisms each already have a working, asserted instance; only per-field application is unpinned. |
| Minor (carried, correctly deferred) | Build config | **m7 — SpotBugs gate inert; no `jacoco:check`.** `spotbugs:check` binds to `process-resources` (before `compile`), so `mvn verify` analyses empty classes; against populated classes it still fails on 9 pre-existing `Brand`/`Dispensary` findings. No JaCoCo `check` goal exists despite `docs/backend-standards.md` mandating 90%. `pom.xml` is byte-identical to `main`. | Re-confirmed: `git diff --stat main...HEAD -- pom.xml` → empty. | **Deferral is correct and honestly scoped** (task 13.10, `design.md` Risks, proposal item 10). Needs its own ticket. Not a KAN-8 regression. |
| Minor (carried, disclosed) | Security | **m8 — unbounded `description`, no request-body size limit, no auth, reference-existence oracle.** All four remain open. | `proposal.md` open items 4, 11, 12; `CreateProductRequest:51-52`. | **Deferral is correct** — bounding `description` is a new normative validation requirement and CLAUDE.md §7 requires the spec to move first. |
| Minor (carried, accepted) | Consistency | **m9 — test-naming convergence still partial.** `ProductControllerTests`'s 11 methods remain `create_shouldReturn*`; the other four classes were converged in task 13.9. Two styles, down from four. | Method-name listing. | Cosmetic; mechanical rename whenever convenient. |

**Blocker: none. Major: none.**

---

## 5. Do the three corrective batches hold together as a whole? (brief item 4)

I re-checked each prior Major and Minor against the code rather than against the closure claims.

| Prior finding | Claimed status | My independent check | Verdict |
|---|---|---|---|
| Pass-1 Major #1 — 18-field response mapping | closed (Batch 6) | Re-proved with a **pair no prior pass used** (N5: `subcategoryId`↔`brandId`) → precise `jsonPath` failure | **GENUINELY CLOSED** |
| Pass-1 Major #2 — `@Size` boundaries | closed (Batch 6) | Both tests present and correctly targeted at max+1. N6 shows the *lower* side is still open → **m1** | **CLOSED as scoped**; residue recorded |
| Pass-2 Major — request→command mapping | closed (Batch 7) | `ArgumentCaptor` at `:199-219` asserts all 17 against distinct values; re-derived by reading | **GENUINELY CLOSED** |
| Gatekeeper pigeonhole gap | closed (Batch 8) | Re-derived both pairwise tables from the test source. `ProductControllerTests` `(T,F,T)` + `(F,T,T)`; `ProductEndpointsTests` `(T,F,T)` + `(F,T,T)`. Union covers all 3 pairs, **on both mapping directions and through the real database**. N2 independently confirms the third pair is live | **GENUINELY CLOSED** |
| Verify-3 W1 — misfiled task 8.17 | fixed (`3e6b335`) | `tasks.md` — `8.17` now sits at the end of Section 8 after `8.16`, with an in-line note recording the move | **CLOSED** |
| File counts 19 / 8 / 8 | corrected | `git diff --name-status main...HEAD` → 19 new `src/main`, 8 new `src/test`, 8 modified. Matches `design.md:526`, `tasks.md` 12.5/12.6/13.6 | **CLOSED** |
| Pass-1 m2 (`callSuper`), m5 (naming), m6 (counts), pass-2 stale `TODO`, `design.md:810`, `tasks.md` 13.4 path, 13.10 relabel, 13.12 D3 Question | closed | All spot-checked present and correct. `grep -rn "TODO\|FIXME" src/main/java src/test/java` → no hits | **CLOSED** |

**Overclaim audit.** Applying the same skepticism that caught the three prior fabrications, I
re-checked the load-bearing quantitative claims: the 112-test count (**confirmed**, my own run),
the 19/8/8 file counts (**confirmed**), `pom.xml` untouched (**confirmed**), the boolean pairwise
tables (**re-derived, correct**), task 6.11's "fail if the annotation is removed **or its bound
widened**" (**true** — a widened bound makes the max+1 test hit the unstubbed mock and return 500,
not 400), and task 13.12's "both ends of the mapping now have field-level test coverage"
(**true**, and N5 confirms it empirically). **One overclaim found: m2**, the "end-to-end proof of
D6" framing, which appears in `tasks.md` 8.11 and is repeated in two verify reports. It is an
artifact-accuracy defect, not a code defect.

**Layering, SOLID, DDD, DRY, dead code — re-checked, no new findings.** `ProductController` holds
no repository and depends only on the `ProductService` interface (DIP); it contains no business
logic. `ProductServiceImpl` owns all three invariants; exactly one `@Transactional` exists in the
slice, on the impl method. `CreateProductCommand` carries no `jakarta.validation` or `io.swagger`
import, so the Clean Architecture direction holds. `resolveOrNotFound` is a correct DRY extraction
(seven instances — rule of three satisfied twice over — kept private to its single consumer).
D2's guard rails are intact: all seven associations `LAZY`, no `CascadeType`, no `orphanRemoval`,
no inverse collections, and no setter is ever called on a resolved reference, so the create
transaction stays single-aggregate. Ubiquitous language is respected (`Product`, `Collection`,
`Subcategory`, `Strain`, `ocpc`); no `Manager`/`Helper`/`Processor` naming smells. No dead code,
no unused imports, no `TODO`/`FIXME`, one `orElseThrow` in the whole service.

---

## 6. Status

**PASS WITH GAPS** — archiving is advisable once m1 and m3 are closed or explicitly accepted.

To be precise about what separates this verdict from the two prior FAILs. Both earlier FAILs
rested on a demonstrated, shipping-relevant blindness: a mutation that silently corrupted the data
a valid request **returned** (pass 1) or **persisted** (pass 2), invisible to the entire suite.
I attacked that same property from nine new angles — including three failure classes nobody had
mutated before (JPA persistence mapping, constraint *values*, the transaction boundary) — and
**could not reproduce it**. Every mutation that alters what a valid request returns or persists
was killed. The three survivors are regression-safety gaps of a strictly weaker kind:

- **N6** requires an input the suite never sends (a 41-to-64-character `ocpc`), and the constraint
  is present and correct today, verified by reading. The failure mode is over-strict validation
  rejecting a legitimate value, not silent data corruption.
- **N9** is the same shape, already adjudicated at Minor twice by prior passes on reasoning I
  independently agree with; I have merely upgraded the evidence from `grep` to mutation.
- **N8** changes nothing observable at all today; it is an artifact-wording issue, not a defect.

None of the three, alone or together, would justify a FAIL under the standard the two prior passes
set. Applying a *stricter* standard now than pass 1 and pass 2 applied would be as inconsistent as
applying a softer one.

The remaining open items are all legitimately non-blocking and — with four exceptions I am adding
here (m1, m2, m4, m5) — adequately disclosed. The SpotBugs/JaCoCo debt is pre-existing, provably
identical on `main`, and deferred with reasoning recorded in three places; the unbounded
`description`, the absent auth and the existence oracle are carried as proposal items 4, 11 and 12
with the correct CLAUDE.md §7 justification for not fixing them in-slice.

Suite is 112/112 green in a fresh full run by this agent, the working tree is byte-identical to
its pre-review state, the database is at its zero-row baseline, and schema drift is zero.

---

## 7. Recommended next steps (before archive)

Per CLAUDE.md §7, artifacts first, then tests.

1. **Artifacts** — reword `tasks.md` 8.11's "end-to-end proof of D6" to "proof of spec R7" and add
   the D6 note to `design.md` (m2); add the log-injection item to `proposal.md`'s open items (m4);
   record an explicit accept-or-defer decision on the `tearDown` cleanup (m5). Extend the existing
   Section 6 / Section 8 tasks for the test work below — not a new "bugfix" section.
2. **Tests (m1)** — `ocpc = "a".repeat(64)` → 201 and `title = "a".repeat(255)` → 201, completing
   the two-sided boundary `docs/backend-standards.md` requires.
3. **Tests (m3)** — distinct booleans (`T/F/T` plus an `isCoreProduct != enabled` variant) and
   distinct `formatValue`/`contentValue`/`cbd` in `ProductServiceTests.validCommandBuilder()`, so
   `mvn test` alone stops returning a false green for service-layer mapping defects.
4. **Re-prove** — re-run N6 and N2 and confirm each now **fails**; revert; confirm 112+ green and
   the database restored.
5. **Follow-up tickets (not this change)** — SpotBugs phase rebinding + `jacoco:check` at 90%
   (m7); `@Size` on `description` + a request-body size limit + the auth story (m8); the
   `@NotNull` / `contentValue = 0` / negative-`cbd` validation instances (m6).
6. **Optional tidy** — converge `ProductControllerTests` on
   `should_[behavior]_when_[condition]` (m9).

---

## Result contract

- **status**: `PASS WITH GAPS`
- **executive_summary**: Third adversarial pass. Independently reproduced the claimed **112 green**
  (53 Surefire + 59 Failsafe) from a clean tree, with `spotless:check` clean, the database at its
  zero-row baseline and zero schema drift. Rather than re-litigating the seven transposition
  mutations three prior passes already ran, I executed **nine new mutations across five production
  files**, deliberately including failure classes nobody had touched: JPA `@JoinColumn` mapping,
  validation-constraint *values*, and the transaction boundary. **Six were killed, three survived.**
  All prior Major findings are genuinely closed, re-proved with field pairs no prior pass used
  (N5: `subcategoryId`↔`brandId` in the 18-argument positional record; N1/N4 on the service and
  persistence layers). I found **no blocker and no Major**, and could not construct any mapping
  mutation that survives — the property both earlier FAILs rested on is gone. The three survivors
  are strictly weaker regression-safety gaps: the `@Size` bounds are pinned only in the reject
  direction, so simultaneously tightening `ocpc` to 40 and `title` to 40 leaves 112/112 green (m1,
  NEW); deleting `@Transactional` entirely leaves 112/112 green, which is harmless today but means
  three artifacts overclaim in calling one test "the end-to-end proof of D6" (m2, NEW — the fourth
  overstated self-report this pipeline has produced); and deleting `@NotNull` from `brandId` leaves
  112/112 green, upgrading verify's W2 from `grep`-based to mutation-based evidence (m6). Two
  further new Minors: `ProductServiceImpl:81` is the codebase's **first** instance of logging
  client-controlled free text (CWE-117 log injection on an unauthenticated endpoint — no other
  service class logs at all), and verify pass 1's S5 on the unconditional `DELETE FROM` teardown
  silently dropped out of the audit trail with no accept/reject decision. On the brief's specific
  question about `ProductServiceImpl`: I judge it **non-blocking** but record a **sharper** version
  than verify pass 3's — the unit-level blind spot is at least four mutation classes wide, not two
  (`approved`↔`enabled`, which verify-3 never tested, leaves `mvn test` at 53/53 green, and the
  `formatValue`↔`cbd` kill is accidental via the nulls test), because `validCommandBuilder()` has
  three same-typed `Integer`s sharing the value `1` as well as three equal booleans. It is not a
  coverage gap — `mvn verify` is the gate the project actually runs and it kills every one — but it
  is a single point of failure worth 4 lines of fixture change. Working tree left byte-identical
  to its pre-review state.
- **artifacts**:
  - `/Users/sjavierexposito/tmp/openspec-demo/openspec/changes/KAN-8-create-product/reports/2026-09-10-code-review-report-3.md` (this report, created)
  - Read and independently re-derived: `ProductController.java`, `ProductServiceImpl.java`,
    `Product.java`, `CreateProductRequest.java`, `CreateProductResponse.java`,
    `GlobalExceptionHandler.java`, `ProductControllerTests.java`, `ProductServiceTests.java`,
    `ProductServiceTest.java`, `ProductEndpointsTests.java`, `spotbugs-exclude.xml`,
    `flyway/release_0.1/V0.1.0__initialData_SM.sql`, `docs/backend-standards.md`,
    `proposal.md`, `design.md`, `tasks.md`, `apply-progress.md` (Batches 5-8), and all five
    prior reports
  - Mutation logs (session scratchpad, not committed): `N1`–`N9`, `baseline`, `final2`
  - **No production or test file modified**: all four mutated files `diff`-confirmed
    byte-identical to their backups; `git status --porcelain` empty
- **next_recommended**: A short corrective pass closing **m1** (two accept-at-limit boundary
  tests) and **m3** (distinct fixture values in `ProductServiceTests.validCommandBuilder()`), plus
  the four artifact edits for m2, m4 and m5 — artifacts first, per CLAUDE.md §7. Then re-run N6
  and N2 to confirm both now fail, revert, confirm green, and proceed to `opsx-archive`. If the
  orchestrator judges m1 and m3 acceptable as disclosed follow-ups, this change may archive as-is
  provided the four artifact corrections land first, since m2 is an artifact claim the evidence
  does not support.
- **risks**:
  - *Regression safety (new, m1)*: a tightened `@Size` bound on `ocpc`/`title` passes the entire
    suite; the constraints are correct today, verified by reading.
  - *Artifact accuracy (new, m2)*: `tasks.md` 8.11 and two verify reports describe a guard-ordering
    test as proving the `@Transactional` boundary; deleting the annotation leaves the suite green.
  - *Single point of failure (new scope, m3)*: four service-layer mapping mutation classes are
    caught only by `ProductEndpointsTests`; `mvn test` alone returns a false green for them.
  - *Security, low (new, m4)*: CWE-117 log injection via `ocpc` on an unauthenticated endpoint;
    first such instance in the codebase, undisclosed.
  - *Audit hygiene (new, m5)*: verify-1's S5 (unconditional `DELETE FROM` teardown) dropped out of
    the trail without a decision; safe against the current baseline only.
  - *Regression safety (carried, m6)*: `@NotNull` on the seven ids, `contentValue = 0` and negative
    `cbd` untested — now proven by deletion, not just by absence of tests.
  - *Pre-existing, accepted (m7)*: SpotBugs gate inert + 9 `Brand`/`Dispensary` findings identical
    on `main`; no enforced coverage gate despite the 90% mandate in `docs/backend-standards.md`.
  - *Disclosed, deferred (m8)*: unbounded `description`, no request-body size limit, no
    authentication, reference-existence oracle — proposal items 4, 11, 12.
  - *DDD, disclosed*: D2 knowingly holds object references to the `Brand` and `Strain` aggregates;
    I re-verified every compensating guard rail (LAZY, no cascade, no `orphanRemoval`, no inverse
    collections, no setter on a resolved reference), so the create transaction stays
    single-aggregate. DDD adherence ≈ 7/10, unchanged from verify pass 1's assessment.
- **skill_resolution**:
  - **`adversarial-review`** — The governing lens. Its Step 3 instruction to state "how the
    implementation could still fail while the author believed it passed" is what drove the decision
    not to re-run transposition mutations for a fourth time but to attack constraint *values*, the
    persistence mapping and the transaction boundary instead — which is where all three surviving
    mutants came from. Its guardrail against praising implementation to balance criticism is why
    §2's positive findings are limited to the two that directly mitigate documented risks (the raw
    JSON wire contract, and the JPA column mapping).
  - **`code-auditing`** — Supplied the Phase 0/2/4 structure: ran the project's own linters and
    build as a baseline before judging anything; swept for dead code, `TODO`/`FIXME`, unused
    imports and duplicate logic (none found); and applied the Security category checklist, which is
    what surfaced m4 (log injection) — the `grep -rln "log\."` sweep showed `ProductServiceImpl` is
    the codebase's only service that logs at all, so there was no precedent to inherit.
  - **`solid-principles`** — Applied as the layering check. SRP: `ProductController` is
    mapping-only, `ProductServiceImpl` owns all three invariants, neither leaks. DIP: the
    controller depends on the `ProductService` interface and holds no repository; no
    `new ConcreteClass()` in business logic. ISP/LSP: no empty or throwing implementations, no
    `instanceof` dispatch. The one nit worth recording is that `resolveOrNotFound` takes a
    `JpaRepository<T, Long>` — a Spring Data type — inside the application layer; this follows the
    codebase's existing convention (all repositories extend `JpaRepository` from
    `domain/repositories`) so it is consistent rather than a new violation.
  - **`dry-principle`** — Applied to both production and test code. `resolveOrNotFound` satisfies
    the Rule of Three twice over (seven call sites) and is correctly kept private to its single
    consumer, so it does not create a premature shared abstraction. Applied the skill's explicit
    exception — "tests should be self-contained; some duplication in test setup is acceptable" — to
    conclude that `ProductEndpointsTests` re-implementing strain seeding rather than reusing
    `StrainReferenceDataFixtures` is **not** a DRY defect (it is structurally forced: the fixture
    takes a `TestEntityManager`, unavailable under `@SpringBootTest`). Same reasoning applies to
    the two deliberately near-duplicate boolean fixtures added in Batch 8 — the duplication is the
    point, and the in-code pigeonhole comments make it readable.
  - **`domain-driven-design`** — Applied to the model. `Product` is a proper aggregate root with
    identity-based equality (D14), soft-delete and a single `save` per transaction; the four lookup
    entities are correctly modelled as reference data with no lifecycle. Ubiquitous language holds
    throughout, including keeping the business term `Collection` despite the `java.util.Collection`
    clash (D11). The knowing violation of "reference other aggregates by id" (D2) is disclosed in
    the proposal, the design and `Product.java`'s own class comment, and I re-verified the
    compensating guard rails really exist in the mapping. The anemic-model trade-off (the taxonomy
    invariant living in the service because `DomainException` sits in the application layer) is a
    genuine Clean Architecture constraint, honestly argued, with the follow-up recorded. Score
    ≈ 7/10, held back by the by-reference deviation and the invariant living outside the root —
    both deliberate, documented, and with named follow-ups.
