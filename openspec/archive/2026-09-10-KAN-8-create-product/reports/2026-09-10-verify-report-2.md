# Verification Report (Pass 2) — KAN-8 Create Product

- Date: 2026-09-10
- Change: `KAN-8-create-product`
- Branch: `feat/KAN-8-create-product` (vs. `main`), working tree clean, HEAD `d3c57d7`
- Agent: `opsx-verify` (second pass, after the `opsx-code-review` FAIL and the Batch 6 corrective pass)
- Mode: **Re-verification.** Independent, evidence-first. Every claim in `apply-progress.md`,
  `tasks.md` and `design.md` was treated as needing spot-checking, not trust — this pipeline has
  already produced a fabricated SpotBugs count, a fabricated `git stash` methodology claim, and an
  overstated "zero compiler warnings" claim. Two mutation experiments were executed by this agent.
- Prior artifacts: `reports/2026-09-10-verify-report.md` (PASS WITH WARNINGS, W1–W4),
  `reports/2026-09-10-code-review-report.md` (**FAIL**, 2 Major + 8 Minor). This report does **not**
  overwrite either; both remain part of the audit trail.

---

## 1. Artifact completeness

| Artifact | Expected | Found | Verdict |
|---|---|---|---|
| `openspec/config.yaml` | schema `story-sdd`, routing `proposal->specs->design->tasks->apply->verify->code-review->archive` | Present, unchanged | PASS |
| `proposal.md` | 12 open items (up from 9) | 12 items; new #11 (unbounded `description`) and #12 (existence-oracle) present and correctly scoped as *disclosed, not fixed* | PASS |
| `specs/products-management/spec.md` | 8 requirements, 18 scenarios | 8 requirements, 18 scenarios; **unmodified** by the corrective pass (correct — no behaviour changed) | PASS |
| `design.md` | corrected file counts + SpotBugs-deferral note | Deferral note present (Risks, D2 block). File counts **still wrong** — see W1 | **PARTIAL** |
| `tasks.md` | 108 original + corrective 3.10/6.11/6.12/8.15/12.6–12.8/13.8–13.10/14.2, all `[x]` | All present, all `[x]`; `grep -c "^- \[ \]"` → `0` | PASS |
| `apply-progress.md` | Batches 1–6 incl. the two post-gate corrections | Batches 1–6 all present. Batch 5 (retroactive, 78 lines) and Batch 6 (101 lines) both substantive, not stubs | PASS |
| `reports/` | prior verify + code-review reports tracked | All 4 reports **tracked in git** (`git ls-files`) — closes code-review housekeeping item 7 | PASS |

---

## 2. Build, test and static-analysis evidence (all re-run by this agent)

| Command | Result | Claim being checked | Verdict |
|---|---|---|---|
| `mvn -o -Dspotbugs.skip=true clean compile` | `BUILD SUCCESS`. Exactly **one** compiler diagnostic: `CreateDispensaryRequest.java uses or overrides a deprecated API` | "one pre-existing, unrelated warning remains, not zero" | **CONFIRMED** |
| `git diff main -- CreateDispensaryRequest.java` | empty | the remaining warning is pre-existing and out of KAN-8 scope | **CONFIRMED** |
| `mvn -o -Dspotbugs.skip=true test` | `Tests run: 52, Failures: 0, Errors: 0` | claimed 52 (up from 50) | **CONFIRMED** |
| `mvn -o -Dspotbugs.skip=true verify` | `BUILD SUCCESS`; Surefire **52**, Failsafe **58** = **110 green** (was 108) | claimed 110 | **CONFIRMED** |
| `mvn -o -Dspotbugs.skip=true spotless:check` | `BUILD SUCCESS`, clean | claimed clean | **CONFIRMED** |
| `git status --porcelain` after every experiment | empty | tree left byte-identical | **CONFIRMED** |

Per-class Failsafe/Surefire breakdown observed: `ProductServiceTests` 16, `ProductControllerTests`
10, `ProductEndpointsTests` 19, `ProductRepositoryTests` 6, `ProductReferenceDataRepositoryTests` 4,
`StrainRepositoryTests` 2, `BrandControllerEndpointsTests` 15, `GlobalExceptionHandlerTest` 4,
`BrandServiceTests` 12, `BrandControllerTests` 9, `BrandRepositoryTests` 6, `ActuatorEndpointsTests`
4, `DispensaryServiceTests` 1, `DispensaryEndpointsTests` 1, `DispensaryRepositoryTests` 1.

**Coverage.** Unchanged from pass 1 and still unenforced: `pom.xml` binds `jacoco-maven-plugin`
only to `prepare-agent`/`prepare-agent-integration`; there is no `jacoco:check` goal anywhere, so
the "90% gate" named in `docs/backend-standards.md`, `design.md` and `tasks.md` is not enforced by
any Maven goal. This is now honestly recorded in `tasks.md` 13.2 and deliberately deferred in
`design.md` 13.10 (see W3).

---

## 3. The two Major code-review findings — independently re-proven

### Major #1 — response/mapping test adequacy: **CLOSED, proven by this agent**

Static confirmation:

- `ProductControllerTests.create_shouldReturn201_when_validRequest`
  (`src/test/java/com/example/demo/presentation/controllers/ProductControllerTests.java:138-170`)
  asserts **all 18** response fields. The fixture `savedProduct(100L)` uses seven **distinct**
  reference ids — `collectionId=1, categoryId=2, subcategoryId=3, brandId=4, strainId=5,
  formatUnitId=6, contentUnitId=7` — plus distinct `formatValue=10`/`contentValue=20` and
  `thc=15`/`cbd=5`. Ids are genuinely distinct, not one id repeated.
- `ProductRepositoryTests.should_returnAllMappedColumnsWithCorrectReferenceIds_when_productExists`
  (`:185-191`) now uses `assertEquals(expected.<x>Id(), found.get<X>().getId())` for all seven FKs,
  captured into a `ReferenceIds` record from the seeding helpers. The old `assertNotNull` calls on
  the FK getters are gone.
- `ProductEndpointsTests.should_return201AndPersistProduct_when_requestIsValid` (`:267-286`) extends
  its native query to `collection_id, category_id, subcategory_id, brand_id, strain_id,
  format_unit_id, content_unit_id` and asserts each against its own seeded id.

**Mutation experiment executed by this agent (deliberately a *different* pair than the corrective
pass used, so this is an independent proof, not a replay):**

1. Swapped `product.getCollection().getId()` ↔ `product.getCategory().getId()` in
   `ProductController.toCreateProductResponse`.
2. `mvn -o -Dspotbugs.skip=true test -Dtest=ProductControllerTests` →
   `Tests run: 10, Failures: 1` / `AssertionError: JSON path "$.data.collectionId" expected:<1> but was:<2>`.
   **The suite is no longer blind to a transposed reference id.**
3. Reverted via `git checkout --`; `git diff` empty, `git status --porcelain` empty.
4. Re-ran → `Tests run: 10, Failures: 0` / `BUILD SUCCESS`.

### Major #2 — `@Size` boundary coverage: **CLOSED, proven by this agent**

- `create_shouldReturn400_when_ocpcExceedsMaxLength` — `"a".repeat(65)` → 400, `$.errors[0].field == "ocpc"`.
- `create_shouldReturn400_when_titleExceedsMaxLength` — `"a".repeat(256)` → 400, `$.errors[0].field == "title"`.

**Second mutation experiment executed by this agent:** removed `@Size(max = 64)` from
`CreateProductRequest.ocpc` → `create_shouldReturn400_when_ocpcExceedsMaxLength` fails
(`Status expected:<400> but was:<500>`, i.e. validation no longer rejects the over-long value).
Reverted; tree clean; re-ran → 10/10 green. The `@Size` bound is now genuinely pinned.

**Residual (see W2):** code-review Major #2 also named `@NotNull` on the seven reference ids,
`contentValue = 0`, and negative `cbd` as having no test instance. Only the two `@Size` tests were
added. `grep -rn "brandId(null)\|contentValue(0)\|cbd(-" src/test/java` → **no hits**. Task 6.11's
own scope was only the two `@Size` tests, so the task is honestly marked, but the finding is only
partially closed.

---

## 4. Spec compliance matrix (re-derived from code, not from the prior report)

| # | Requirement | Implementation evidence | Verdict |
|---|---|---|---|
| R1 | Create product via `POST /api/products` | `ProductApi` — `@RequestMapping("/api/products")`, `@Tag("Products")`, `@PostMapping`, `@ResponseStatus(CREATED)`, `DataResponse<CreateProductResponse>`, `@ApiResponses` 201/400/404/409. All 18 response fields now asserted (Major #1) | PASS |
| R2 | Validation of the payload | Full D8 set verified in `CreateProductRequest`: `@NotBlank`+`@Size(max=64)` `ocpc`; `@NotBlank`+`@Size(max=255)` `title`; `@NotNull @Positive` on all 7 ids and `formatValue`/`contentValue`; `@PositiveOrZero` `thc`/`cbd`; nothing on `description` + 3 flags; `@Valid` on the API parameter. Both `@Size` bounds now have boundary tests | PASS (was PARTIAL) |
| R3 | Resolution of references | `ProductServiceImpl.create` — seven `resolveOrNotFound(...)` calls in declaration order, each naming its reference (`"FormatUnit"`/`"ContentUnit"` disambiguated). `@SQLRestriction` on `Brand`/`Strain` gives soft-delete → 404. 7 unit + 9 endpoint tests | PASS |
| R4 | OCPC uniqueness among non-deleted | `existsByOcpc` as guard 1; `@SQLRestriction("deleted_at IS NULL")` on `Product`. Pinned by `should_returnFalse_when_onlyOcpcHolderIsSoftDeleted` and `should_return201_when_ocpcHolderIsSoftDeleted` | PASS |
| R5 | Taxonomy coherence | `Objects.equals(subcategory.getCategory().getId(), category.getId())` after all resolutions; proxy id read without initialisation, pinned by `should_returnParentIdWithoutInitialisingProxy_when_subcategoryPersisted` | PASS |
| R6 | Optional attributes | Builder copies every optional verbatim — no `orElse`, no `Boolean.TRUE.equals`, no defaulting (read line by line). `should_persistNulls_when_optionalAttributesAreOmitted` + endpoint NULL assertions | PASS |
| R7 | Atomicity | Exactly one `@Transactional(rollbackFor = Exception.class, propagation = REQUIRED)` in the slice, on `ProductServiceImpl.create` (grep-confirmed on impl, interface and controller). All three guards run before `save`. `should_leaveProductCountUnchanged_when_requestIsRejected` covers all five rejection paths | PASS |
| R8 | Standard envelopes / no leakage | `ErrorResponse(timestamp, status, path, errors)`; D9 handler uses a static message and never reads `ex`. `should_reportDescriptiveMessageWithoutInternalDetails_when_requestFails` | PASS with caveat (the untouched `DataIntegrityViolationException` handler still echoes `ex.getMessage()` — pre-existing, unreachable on Product paths, tracked as a proposal follow-up) |

All 18 scenarios retain at least one owning test; scenario 4 (*Required field present but invalid*)
moves from PARTIAL to PASS on the `@Size` bounds, with the narrower `@NotNull`/`contentValue`/`cbd`
instances still untested (W2).

---

## 5. Correctness and scope discipline

| Check | Evidence | Verdict |
|---|---|---|
| `pom.xml` untouched | `git diff --stat main -- pom.xml` → empty. SpotBugs/JaCoCo gate config unchanged, deliberately deferred per `design.md` 13.10 | **CONFIRMED** |
| `description` has no new `@Size` | `CreateProductRequest:51-52` — `@Schema` only, no constraint | **CONFIRMED** |
| No auth / OCPC-index / `DataIntegrityViolationException` work | No security or auth file in the diff; no new Flyway script; `pg_indexes` on `products` shows only `products_pkey`; `GlobalExceptionHandler` diff is `+10` lines, the single additive D9 handler — `handleDataIntegrityViolation` untouched | **CONFIRMED** |
| Modified-file set | Exactly **8**: `docs/backend-standards.md`, `docs/data-model.md`, `spotbugs-exclude.xml`, `GlobalExceptionHandler.java`, `DispensaryServiceTests.java`, `BrandControllerEndpointsTests.java`, `DispensaryEndpointsTests.java`, `GlobalExceptionHandlerTest.java` — matches `design.md`'s list exactly | **CONFIRMED** |
| New main files | **19** — matches | **CONFIRMED** |
| New test files | **8** — `design.md`'s Totals line says 7 (see W1) | **DISCREPANCY** |
| `callSuper = false` applied | Present on `Product.java:53` and `Strain.java:43`; the two Lombok warnings are gone from the compile log | **CONFIRMED** |
| `spotbugs-exclude.xml` scoping | One new `<Match>` naming exactly `...product.Product` and `...product.Subcategory` by `<Class name>`, with a comment explaining D2/D12. Does not silence the 9 pre-existing `Brand`/`Dispensary` findings | **CONFIRMED** |
| Database state | All ten reference tables at **0** rows after the full `verify` run (`products`, `collections`, `categories`, `subcategories`, `units`, `brands`, `brand_types`, `strains`, `strain_types`, `seed_companies`) | **CONFIRMED** |
| Schema drift | One migration file only (`flyway/release_0.1/V0.1.0__initialData_SM.sql`, unmodified); `flyway_schema_history` = 1 row, `0.1.0`; `ddl-auto: none`; `git diff main -- src/main/resources src/test/resources` empty | **ZERO DRIFT** |

---

## 6. Design coherence

| Decision | Implemented as designed? | Note |
|---|---|---|
| D1 guard order | Yes — `existsByOcpc` → 7 resolutions → taxonomy → `save`, read line by line | Pinned by two guard-order tests |
| D2 object associations | Yes — all seven `@ManyToOne(LAZY)`, no cascade, no `orphanRemoval`, no inverse collections | Disclosed DDD deviation; guard rails hold |
| D3 command record | Yes | See W5 — the response side remains 18 positional args, the exact hazard D3 argues against |
| D4 `resolveOrNotFound` | Yes — one private generic helper, seven call sites | |
| D5 minimal `Strain` | Yes — `@SQLDelete` + `@SQLRestriction`, bare `Integer` FK columns | |
| D6 transaction boundary | Yes — exactly one `@Transactional`, on the impl method | |
| D7 `existsByOcpc` | Yes — derived query relying on `@SQLRestriction` | |
| D8 validation set | Yes — full set verified field by field | |
| D9 additive handler | Yes — `+10` lines, static message, `ex` never read | |
| D13 docs correction | Yes — `docs/data-model.md` + `docs/backend-standards.md` modified | |
| D14 identity equality | Yes — now with explicit `callSuper = false` | |
| 13.10 SpotBugs/JaCoCo deferral | Recorded in `design.md` Risks | Deferral is legitimate and honestly scoped |

---

## 7. Issues

### CRITICAL

**None.** No functional defect, no security vulnerability, no spec-behaviour violation, no schema
drift, no database residue, and no unresolved Major finding.

### WARNING

**W1 — `design.md`'s file-count Totals line is still wrong, and the error is propagated into two
other artifacts.** Task 12.6 claimed to correct the counts to "19 new main / 7 new test / 8
modified" in response to code-review m6. Actual, from `git diff --name-status main`: **19 new main
(correct), 8 new test, 8 modified (correct)**. `design.md`'s own file-layout tree *lists all eight*
test files, so the tree and the Totals line one paragraph below it contradict each other. The same
"7" is repeated in `tasks.md` 12.6 and in `apply-progress.md` Batch 5 — while `tasks.md` 13.6
independently and correctly states 8, so `tasks.md` now contradicts itself. This is the same class
of artifact-accuracy defect the corrective pass was invoked to fix, and it matters under CLAUDE.md
§7 (documentation is the source of truth). One-line fix in three files.

**W2 — code-review Major #2 is only partially closed.** The finding named four untested validation
instances; two were closed (`@Size` on `ocpc`/`title`), two were not (`@NotNull` on the seven
reference ids; `contentValue = 0`; negative `cbd`). Verified absent:
`grep -rn "brandId(null)\|contentValue(0)\|cbd(-" src/test/java` → no hits. Task 6.11's scope was
explicitly only the two `@Size` tests, so nothing is mis-marked — but a reader of `tasks.md` 14.2
("closed the review's two Major findings") would reasonably infer full closure. Spec R2 names
"absent, `null` or blank" for the id fields normatively. Two cheap tests would close it.

**W3 (carried from pass 1, now properly documented, still open) — no enforced coverage gate.**
`docs/backend-standards.md` mandates 90% for branches/functions/lines/statements; no `jacoco:check`
execution exists in `pom.xml`, and `jacoco.exec` / `jacoco-it.exec` are never merged. The corrective
pass deliberately deferred this (task 13.10) with a defensible reason — adding the gate would newly
fail the build on pre-existing findings — and now records the deferral in `design.md`. Severity
reduced from pass 1 because it is disclosed rather than silently asserted, but the gap is real and
should carry a follow-up ticket.

**W4 (carried from pass 1, unchanged) — the SpotBugs gate is inert in a one-shot build, and
`spotbugs:check` against populated classes still fails.** `spotbugs:check` is bound to
`process-resources`, which Maven runs before `compiler:compile`, so `mvn clean verify` analyses an
empty `target/classes`. Against populated classes it still reports the 9 pre-existing
`Brand.java`/`Dispensary.java` findings (identical to `main`). Consequence: the new
`spotbugs-exclude.xml` block is never exercised by the standard build, and DoD item 9 ("code passes
Spotless/SpotBugs") is vacuously satisfied. Deliberately deferred (task 13.10); needs its own
ticket. Not a KAN-8 regression.

**W5 — the code-review's "Question" row was never answered, and `tasks.md` implies it was.** The
Question asked whether D3's own anti-transposition rationale ("17 parameters, of which 7 are
consecutive `Long` … are silently transposable and the compiler cannot help") was knowingly
accepted or overlooked when `toCreateProductResponse` constructs `CreateProductResponse` with 18
positional arguments including 7 adjacent `Long`s. `CreateProductResponse` is still a plain
`record` with no `@Builder`, and `design.md` D3 contains no note on the response side. Task 13.10 is
labelled "**code-review question row**" but actually records the SpotBugs/JaCoCo deferral (findings
m1/m7) — so the Question row is both unaddressed and mislabelled as addressed. Note the mitigation
is now real: the strengthened 18-field test (Major #1) is exactly what makes the positional hazard
detectable, so this is an artifact-honesty gap, not a code risk. Either add `@Builder` or record the
exemption in D3.

### SUGGESTION

- **S1 — test-naming convergence (code-review m5) is partial.** Task 13.9 renamed
  `ProductRepositoryTests`, `StrainRepositoryTests`, `ProductReferenceDataRepositoryTests` and
  `ProductEndpointsTests` to `should_[behavior]_when_[condition]`, but deliberately excluded
  `ProductControllerTests`, which still uses `create_shouldReturn201_when_validRequest`. Four styles
  are down to two; one mechanical rename would finish it.
- **S2 — one residual blind spot in the strengthened 201 fixture.** `savedProduct(...)` sets
  `isCoreProduct`, `approved` and `enabled` all to `Boolean.TRUE`, so a transposition among those
  three specifically would still pass. All 15 other fields carry distinct values. Setting one of the
  three to `FALSE` would close it.
- **S3 — `design.md` Migration Plan is stale.** Line 806 still says "delete the 19 new files and
  revert **the two modified ones**"; the m6 correction updated the layout section to 8 modified
  files but not this sentence.
- **S4 — `tasks.md` 13.4 cites a path that does not exist.** It records
  `find src/main/resources/db/migration` as the evidence for zero schema drift; migrations actually
  live in `flyway/release_0.1/`. The conclusion is correct (independently re-verified here), but the
  cited command could not have produced it.

---

## 8. Verdict

**PASS WITH WARNINGS**

Both Major findings from `2026-09-10-code-review-report.md` are genuinely closed, and this agent
proved it rather than accepting the corrective pass's word: an independent mutation
(collection↔category, a different pair from the one the corrective pass used) is now caught with a
precise assertion failure, and removing the `@Size(max = 64)` bound now breaks a test that
previously did not exist. The suite grew 108 → 110 and is fully green on a fresh `mvn -o
-Dspotbugs.skip=true verify`. Scope discipline held completely: `pom.xml` untouched, no `@Size` on
`description`, no auth / OCPC-index / `DataIntegrityViolationException` work, exactly the 8 modified
files `design.md` lists. The compile is down to one pre-existing, unrelated deprecation notice —
confirmed present, confirmed not zero, and confirmed out of KAN-8's scope, matching the corrected
claim exactly. The database is at its zero-row baseline with zero schema drift.

The five warnings are documentation-accuracy and test-depth items, none of which blocks archiving on
correctness grounds. W1 is the one worth closing before `opsx-archive`, because it is the *same*
artifact-accuracy defect the corrective pass was invoked to fix and it now leaves `tasks.md`
contradicting itself — a two-minute edit in three files. W2 and W5 are honest-scoping gaps where the
artifacts claim slightly more closure than was delivered. W3 and W4 are pre-existing build-gate
weaknesses, correctly deferred with reasons recorded, and belong to their own ticket.
