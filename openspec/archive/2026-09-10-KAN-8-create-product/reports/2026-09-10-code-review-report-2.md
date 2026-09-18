# Code Review Report (Pass 2) — KAN-8 Create Product

- Date: 2026-09-10
- Change: `KAN-8-create-product`
- Branch: `feat/KAN-8-create-product` (vs. `main`), HEAD `3207be7`, working tree clean
- Agent: `code-review` (second adversarial pass, after the pass-1 **FAIL** and the Batch 6 corrective pass)
- Prior artifacts in the audit trail (none overwritten):
  - `reports/2026-09-10-code-review-report.md` — pass 1, **FAIL** (2 Major + 8 Minor)
  - `reports/2026-09-10-verify-report.md` — verify pass 1, PASS WITH WARNINGS
  - `reports/2026-09-10-verify-report-2.md` — verify pass 2, PASS WITH WARNINGS (W1–W5, S1–S4)

## Scope of this pass

Because two independent verify passes already re-proved the two pass-1 Major findings at the
mutation level, this pass did **not** re-derive them from scratch. It (1) spot-checked the closures,
(2) re-reviewed the eight pass-1 Minor findings for adequate closure or honest deferral, (3) ran a
**fresh adversarial pass over surface the corrective work did not touch**, and (4) took a position on
verify pass 2's residual open items.

Item (3) is where this pass adds new information: **it found a Major finding that both verify passes
missed, because both mutated only the half of `ProductController` that the corrective pass fixed.**

## Experiments executed by this agent

Four controlled mutations, each applied to a pristine tree, run through the full suite, then reverted
and byte-compared against a pre-experiment backup.

| # | Mutation (in `ProductController.create`, request→command block) | Result |
|---|---|---|
| M1 | `.thc(request.getThc())` / `.cbd(request.getCbd())` **transposed** | `BUILD SUCCESS` — Surefire 52, Failsafe 58 = **110/110 green. Bug undetected.** |
| M2 | `.formatValue(...)` / `.contentValue(...)` **transposed** | `BUILD SUCCESS` — **110/110 green. Bug undetected.** |
| M4 | `.isCoreProduct(...)` / `.approved(...)` **transposed** | `BUILD SUCCESS` — **110/110 green. Bug undetected.** |
| M3 (control) | `.collectionId(...)` / `.categoryId(...)` **transposed** | `BUILD FAILURE` — 14 `ProductEndpointsTests` failures (independently re-verified by gatekeeper, correcting this report's original count of 7): `should_return201AndPersistProduct_when_requestIsValid`, `should_return409_when_ocpcIsAlreadyUsed`, `should_return201_when_ocpcHolderIsSoftDeleted`, `should_return404_when_collectionIsUnknown`, `should_return404_when_categoryIsUnknown`, `should_return404_when_subcategoryIsUnknown`, `should_return404_when_formatUnitIsUnknown`, `should_return404_when_contentUnitIsUnknown`, `should_return404_when_brandIsUnknown`, `should_return404_when_strainIsUnknown`, `should_return409_when_subcategoryBelongsToAnotherCategory`, `should_persistNullOptionalAttributes_when_theyAreOmitted`, `should_persistSuppliedFlags_when_theyAreExplicit`, `should_leaveProductCountUnchanged_when_requestIsRejected`. **Correctly detected** (the qualitative claim holds; the original quantitative count was an error, not the direction of the result). |

Restoration evidence: `diff` against backup → byte-identical; `git status --porcelain` → empty;
post-experiment `mvn -o -Dspotbugs.skip=true -Dpitest.skip=true verify` → `BUILD SUCCESS`, 52 + 58 =
110 green. Database left at its zero-row baseline (`products`, `collections`, `categories`, `brands`,
`strains`, `units` all `0`).

M3 is the control that makes M1/M2/M4 meaningful: the seven **reference ids** in the request→command
block *are* genuinely pinned end-to-end (a swap there fails resolution or the taxonomy guard). The
gap is precisely and only the **non-reference fields**.

## Findings

| Severity | Area | Finding | Evidence | Suggested fix |
|---|---|---|---|---|
| **Major** | Test adequacy / spec R1, R6 | **Pass-1 Major #1 is only half-closed, and the artifacts claim it is fully closed.** Pass 1 named *both* mapping blocks in `ProductController`: *"(17-field request→command, 18-field entity→response)"*. The corrective pass closed the **response** half only. The **request→command** half (`ProductController.java:45-64`) remains unverified for every non-reference field: a silent transposition of `thc`/`cbd`, `formatValue`/`contentValue`, or `isCoreProduct`/`approved` passes the entire 110-test suite. Root cause: `ProductControllerTests` stubs the service with `any(CreateProductCommand.class)` and **never captures the command** (`grep -rn "ArgumentCaptor" src/test/java` → zero hits), so the block is invisible to the unit test; and `ProductEndpointsTests.should_return201AndPersistProduct_when_requestIsValid`'s native query selects `ocpc, title, created_at` + the seven FK columns but **not** `format_value, content_value, thc, cbd, description`. The two tests that do read those columns are both transposition-invariant by construction: `should_persistNullOptionalAttributes_when_theyAreOmitted` removes all five optional fields and asserts all are `NULL`; `should_persistSuppliedFlags_when_theyAreExplicit` sets all three flags to `false` and asserts all three are `false`. | **Proven experimentally**: M1, M2 and M4 above — three distinct transpositions, each `BUILD SUCCESS` with 110/110 green. Control M3 fails as expected. `ProductController.java:55-63`; `ProductControllerTests.java:140` (`any(...)`, no captor); `ProductEndpointsTests.java:271-285` (query column list), `:573-579`, `:587-608`. | **Tests** (three cheap edits, no production-code change): (1) add an `ArgumentCaptor<CreateProductCommand>` to `create_shouldReturn201_when_validRequest` and assert all 17 command fields against the request fixture — requires giving the three booleans distinct values in `validRequestBuilder()`, which are currently all `Boolean.TRUE`; (2) extend the endpoint happy-path native query to `description, format_value, content_value, thc, cbd` and assert against `validBody`'s already-distinct `10`/`100`/`15`/`5`; (3) in `should_persistSuppliedFlags_when_theyAreExplicit`, use a distinct combination (e.g. `false`/`true`/`false`) instead of all-`false`. |
| Minor | Code quality / dead comment | `ProductControllerTests.java:51-54` carries a stale class-level comment stating *"The empty/malformed-body scenario (D9) is deliberately not covered here: it is Section 7's job … **TODO Section 7.**"* — while `create_shouldReturn400WithStaticMessage_when_bodyIsEmpty` sits at line 325 **in that same class**. Section 7 is complete and the scenario *is* covered here. This is the only `TODO`/`FIXME` in the entire `src/main` + `src/test` tree. Untouched by the corrective pass. | `grep -rn "TODO\|FIXME" src/main/java src/test/java` → exactly one hit, `ProductControllerTests.java:54`. | **Code**: delete the last two sentences of the comment and the `TODO`. |
| Minor | Artifact quality | `tasks.md:180` (task 13.6) contains an unedited stream-of-consciousness self-correction in a living artifact: *"… so 4 extra files exist: the `ProductServiceTest` base, the fixtures helper, and — no, `ProductEndpointsTests` was already anticipated by the Testing Plan's '4. Endpoint integration tests' — the genuinely uncounted extras are …"*. Separately, the **task premises** of 12.5 and 13.6 still assert wrong counts (*"5 new test files"*, *"6 new test files"*, *"only two modified files"*); each is corrected by an appended verification note, so a reader must read to the end of a 400-word bullet to learn the premise is wrong. Under CLAUDE.md §7 (documentation is the source of truth) a living artifact should state the corrected fact, not narrate the derivation. | `tasks.md:168` (12.5), `tasks.md:180` (13.6). | **Artifacts**: rewrite 12.5/13.6 to state the settled figures (19 / 8 / 8) directly, keeping a one-line "corrected from N" note. |
| Minor | Test adequacy (confirms + upgrades verify S2) | Verify's S2 flagged the all-`TRUE` boolean triple in `savedProduct(...)` as a *suspected* residual blind spot. **M4 proves it**, and proves it is wider than S2 stated: the blindness exists on the request→command side *and* the response side *and* end-to-end, because all three of the fixture (`Boolean.TRUE`×3), the endpoint flag test (`false`×3) and the endpoint null test (`NULL`×5) are transposition-invariant. | M4; `ProductControllerTests.java:128-132`; `ProductEndpointsTests.java:587-608`. | **Tests**: folded into the Major's fix (3). |
| Minor | Artifact honesty (verify W5, confirmed) | `tasks.md:184` labels task 13.10 *"**Corrective … code-review question row**"* but its body records the SpotBugs/JaCoCo phase-binding deferral, i.e. findings m1/m7. The actual Question row — D3's anti-transposition rationale versus `toCreateProductResponse`'s 18 positional arguments — is still unanswered: `CreateProductResponse` remains a plain `record` with no `@Builder`, and `design.md` D3 records no exemption. So the Question is both unaddressed and marked as addressed. **This pass raises its stakes**: M1/M2/M4 demonstrate that argument-selection hazards in this exact controller are real, not theoretical. | `tasks.md:184`; `ProductController.java:70-90`; `CreateProductResponse`. | **Artifacts/code**: either add `@Builder` to `CreateProductResponse` or record the exemption in D3, and relabel 13.10. |
| Minor | Artifact accuracy (verify S3, confirmed still open) | `design.md:810` Migration Plan still reads *"delete the 19 new files and revert **the two modified ones**"*. The m6 correction updated the layout section and the Totals line to 8 modified files but not this sentence, so `design.md` now contradicts itself two sections apart. | `design.md:810` vs. `design.md:526`. | **Artifacts**: one-word fix ("the eight modified ones"). |
| Minor | Artifact accuracy (verify S4, confirmed still open) | `tasks.md:178` (task 13.4) cites `find src/main/resources/db/migration` as its evidence for zero schema drift. That path does not exist; migrations live in `flyway/release_0.1/`. The **conclusion** is correct (independently re-confirmed here: one migration file, unmodified, `git diff main -- src/main/resources src/test/resources` empty), but the cited command could not have produced it. | `tasks.md:178`. | **Artifacts**: correct the cited path. |
| Minor | Test adequacy (verify W2, confirmed; severity held at Minor) | Pass-1 Major #2 named four untested validation instances; the corrective pass closed two (`@Size` on `ocpc`/`title`). `@NotNull` on the seven reference ids, `contentValue = 0` and negative `cbd` remain untested (`grep -rn "brandId(null)\|contentValue(0)\|cbd(-" src/test/java` → no hits). **This pass does not escalate these**, and explicitly disagrees with treating them as Major: unlike `@Size`, which was a distinct annotation with *zero* instances anywhere (hence Major in pass 1), `@NotNull` and `@Positive` each already have working, asserted instances in `ProductControllerTests` (`missingTitle`, `formatValue(0)`, `thc(-1)`), so the mechanism is proven and only the per-field application is unpinned. Task 6.11's scope was explicitly the two `@Size` tests, so nothing is mis-marked. | `ProductControllerTests.java:174-246`; verify report 2 §7 W2. | **Tests** (follow-up, non-blocking): `brandId(null)` and `contentValue(0)`. |

### Position on the two deliberate deferrals

Both were reviewed on the merits, not accepted on assertion:

- **`@Size` on `description` (pass-1 m3) — deferral is REASONABLE.** Spec R2 does not bound
  `description`; adding a bound is a new *normative* validation requirement, and CLAUDE.md §7
  requires the spec to move first. It is disclosed as `proposal.md` open item 11 with exactly that
  reasoning and is correctly paired with open item 4 (no authentication), which dominates the risk.
  Deferring is the more spec-disciplined choice than a silent code fix.
- **SpotBugs phase-binding and `jacoco:check` (pass-1 m1/m7) — deferral is REASONABLE, with one
  caveat.** Both are pre-existing build-infrastructure defects, not KAN-8 regressions; `pom.xml` is
  confirmed untouched (`git diff --stat main -- pom.xml` → empty), and fixing either would newly fail
  the build on 9 pre-existing `Brand`/`Dispensary` findings in files this proposal declares "reused
  unchanged". Recording the decision in `design.md` Risks and task 13.10 is the right disposition.
  **Caveat**: the consequence is that DoD item 9 ("code passes Spotless/SpotBugs") is *vacuously*
  satisfied for this change, and the new `spotbugs-exclude.xml` block is never exercised by the
  standard build. No artifact should be read as claiming DoD 9 was meaningfully demonstrated. A
  follow-up ticket is required, not optional.

### Confirmed closed (spot-checked, not re-derived)

| Pass-1 finding | Status | Evidence checked in this pass |
|---|---|---|
| Major #1 — response mapping (18 fields) | **CLOSED** | `ProductControllerTests.java:146-169` asserts all 18 `$.data.*` fields; fixture ids `1,2,3,4,5,6,7` are genuinely distinct, plus `formatValue=10`/`contentValue=20`, `thc=15`/`cbd=5`. Endpoint query extended to the seven FK columns (`:271-285`). Re-proved twice by verify with two different pairs. |
| Major #2 — `@Size` boundary tests | **CLOSED** | `create_shouldReturn400_when_ocpcExceedsMaxLength` (`"a".repeat(65)` vs `max=64`) and `create_shouldReturn400_when_titleExceedsMaxLength` (`"a".repeat(256)` vs `max=255`) — both boundaries correctly targeted at max+1, both asserting `$.errors[0].field`. |
| m2 — Lombok `callSuper` | **CLOSED** | `Product.java:53` and `Strain.java:43` both `@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)`. |
| m6 / verify W1 — file counts | **CLOSED** | `design.md:526` Totals now reads 19 / 8 / 8. `grep -rn "7 new test files" openspec/changes/KAN-8-create-product/` → the only hit is inside the historical pass-1 report, which is correct as an audit record. Zero hits in living docs. |
| m5 — test naming | **PARTIAL (accepted)** | 13.9 converged `ProductRepositoryTests`, `StrainRepositoryTests`, `ProductReferenceDataRepositoryTests`, `ProductEndpointsTests`. `ProductControllerTests` deliberately excluded — all 10 methods remain `create_shouldReturn*`. Four styles down to two. Cosmetic; not blocking. |
| Housekeeping — untracked reports | **CLOSED** | All five reports tracked; `2026-09-10-verify-report-2.md` committed as `3207be7` at the start of this pass. |

## Status

**FAIL** — blocking on test adequacy, for the second time, on the same finding and in the same method.

To be precise about what this verdict does and does not say: **no functional defect, no security
vulnerability and no spec-behaviour violation was found.** `ProductController.create` is correct as
written — every field maps to its own getter, verified line by line. The suite is 110/110 green in a
fresh full run this agent performed, the database is at its zero-row baseline, schema drift is zero,
scope discipline held completely (`pom.xml` untouched, exactly the 8 modified files `design.md`
lists), and the corrective pass's headline work is genuine.

The blocker is that **pass-1 Major #1 was declared closed when half of it was closed.** Pass 1's own
text named both mapping blocks. `tasks.md` 14.2 and `apply-progress.md` Batch 6 state that the
corrective pass "closed the review's two Major findings"; three mutation experiments run here show a
transposition of the same class, in the same method, one block above the fixed one, still passes the
entire suite. Applying a softer standard on re-review than pass 1 applied on first review — after
pass 1 explicitly rejected the "the code is correct as written, so it is fine" argument and FAILed
anyway — would be inconsistent, and would ratify an artifact claim that the evidence does not
support.

The remaining seven findings are Minor and none of them, individually or together, would warrant a
FAIL. Verify pass 2's W2, W5, S1, S3 and S4 are all confirmed as genuine but Minor; W1 is confirmed
closed; W3 and W4 are correctly deferred with reasons recorded and belong to their own ticket.

Remediation is small and entirely in test code: roughly one `ArgumentCaptor` block, five extra
columns in one existing native query, and one changed boolean triple. No production code needs to
change to clear this verdict.

## Recommended next steps (before archive)

Per CLAUDE.md §7, artifacts first, then code.

1. **Artifacts** — extend the existing Section 6/8 tasks (not a new "bugfix" section) to cover the
   request→command mapping assertions; correct `tasks.md` 12.5/13.6 wording and 13.4's cited path;
   fix `design.md:810` ("the two modified ones" → eight); relabel task 13.10 and either add
   `@Builder` to `CreateProductResponse` or record the D3 exemption.
2. **Tests (Major)** — the three edits in the Major row: `ArgumentCaptor` on the 201 controller test
   with distinct boolean values; five extra columns on the endpoint happy-path query; a distinct
   flag combination in `should_persistSuppliedFlags_when_theyAreExplicit`.
3. **Code (Minor)** — remove the stale comment and `TODO` at `ProductControllerTests.java:51-54`.
4. **Re-prove** — re-run M1, M2 and M4 (thc↔cbd, formatValue↔contentValue, isCoreProduct↔approved)
   and confirm each now **fails**; revert; confirm 110+ green and the database restored.
5. **Follow-up tickets (not this change)** — SpotBugs phase rebinding + `jacoco:check` at 90%;
   `@Size` on `description` plus a request-body size limit; the `@NotNull`/`contentValue=0`/negative
   `cbd` validation instances.
6. Optional tidy — converge `ProductControllerTests` on `should_[behavior]_when_[condition]`.
