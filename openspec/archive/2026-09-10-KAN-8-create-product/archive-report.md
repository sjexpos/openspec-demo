# KAN-8 Create Product — Archive Report

- **Change:** `KAN-8-create-product` — Product aggregate and write API (`POST /api/products`)
- **Schema:** `story-sdd`
- **Branch:** `feat/KAN-8-create-product`
- **Archived on:** 2026-09-10
- **Archived to:** `openspec/archive/2026-09-10-KAN-8-create-product/`

## Summary

The KAN-8 Create Product change was archived. The full SDD pipeline completed with the following results:

- **Proposal phase:** 13 open items carried forward (documented in `proposal.md`), all appropriately scoped as follow-up work, not blockers.
- **Spec phase:** 8 requirements, 18 scenarios, all concrete and testable.
- **Design phase:** 15 design decisions (D1–D15) fully documented with alternatives considered and trade-offs named.
- **Tasks phase:** 129 checkboxes across sections 0–14, all complete.
- **Apply phase:** 9 batches total in `apply-progress.md` — batches 1–5 delivered the initial implementation (sections 0–3, 4–5, 6, 7, and 8–14 respectively), and batches 6–9 were corrective passes following the code-review FAILs and gatekeeper findings (closing the response-mapping test gap, the request→command mapping test gap, the boolean pigeonhole gap, and code-review pass 3's five Minor findings).
- **Verify phase:** 3 independent verification passes completed.
  - **Pass 1** (opsx-verify-1): `PASS WITH WARNINGS` (108 tests green, 8 spec requirements validated, 17/18 scenarios traced, 4 warnings identified, 0 CRITICAL findings).
  - **Pass 2** (opsx-verify-2): `PASS WITH WARNINGS` — independently re-proved the two code-review Major fixes via its own fresh mutations; found a new documentation defect (an earlier correction had undercounted "new test files" as 7 instead of 8, propagated across `design.md`/`tasks.md`/`apply-progress.md`), fixed before continuing.
  - **Pass 3** (opsx-verify-3): `PASS WITH WARNINGS` — did a complete field-by-field mapping audit and, for the first time, mutation-tested `ProductServiceImpl` directly (not just `ProductController`); found 2 of 4 fresh mutations there were unit-test-blind (only caught by integration tests) — non-blocking since the full suite does catch them, later closed anyway in Batch 9. Also found task `8.17` misfiled in the wrong `tasks.md` section (fixed).
- **Code Review phase:** 3 independent code review passes completed.
  - **Pass 1** (opsx-code-review): `FAIL` — identified 2 Major findings and 4 Minor findings in test coverage and bounds checking.
  - **Pass 2** (opsx-code-review): `FAIL` — re-verified the corrective fixes from pass 1, found 1 additional Major finding (structural test gap via pigeonhole principle for triple-valued boolean mappings).
  - **Pass 3** (opsx-code-review): `PASS WITH GAPS` — all prior gaps closed; 3 Minor findings noted (non-blocking: D6 transaction boundary observability, inconsistent curl URL, cleanup scope).

**Final build result:** 115 tests green (56 unit + 59 integration) on the feature branch; zero schema drift; database at clean baseline; all `tasks.md` checkboxes complete; `mvn -o -Dspotbugs.skip=true spotless:check` clean; `mvn -o spotbugs:check` fails on 9 pre-existing findings in `Brand`/`Dispensary` (confirmed byte-identical to `main`; no new defects introduced by KAN-8).

The change adds a new **`products-management`** capability. Because it is a NEW capability, its delta spec was sync-promoted into the main OpenSpec spec store as canonical content, exactly as precedent at KAN-6.

## Spec Sync

The delta spec `specs/products-management/spec.md` (wrapper `## ADDED Requirements`) was applied to the main specs as a new canonical capability:

- **New canonical spec:** `openspec/specs/products-management/spec.md`
  - Form: canonical `## Requirements` (delta `## ADDED Requirements` wrapper stripped per OpenSpec convention).
  - Content: 8 requirements / 18 scenarios, preserved verbatim from the delta body (verified byte-for-byte from the archive copy).
  - Purpose section: Retained verbatim from delta, as specified in the schema for new capabilities.
  - No existing specs for other capabilities were modified.

## Specs Synced

| Domain | Action | Details |
|--------|--------|---------|
| products-management | Created | 8 requirements / 18 scenarios added to canonical spec |

## Archive Contents

Preserved verbatim from `openspec/changes/KAN-8-create-product/`:

- `proposal.md` ✅
- `design.md` ✅
- `tasks.md` ✅ (all items across sections 0–14 marked complete, 129/129 checkboxes)
- `apply-progress.md` ✅ (9 batch entries: batches 1–5 are the initial implementation, sections 0–3/4–5/6/7/8–14 respectively — batch 5 was created retroactively post-verify to close the W1 traceability gap — and batches 6–9 are corrective passes following the code-review FAILs and gatekeeper findings)
- `specs/products-management/spec.md` ✅ (delta spec retained in archive; canonical spec created separately in `openspec/specs/`)
- `reports/` ✅
  - `2026-09-10-step-N+1-unit-test-and-db-verification.md` (108 tests: 50 unit + 58 integration)
  - `2026-09-10-step-N+2-manual-curl-verification.md` (live endpoint curl tests)
  - `2026-09-10-verify-report.md` (Pass 1: comprehensive independent verification)
  - `2026-09-10-verify-report-2.md` (Pass 2: warning re-checks)
  - `2026-09-10-verify-report-3.md` (Pass 3: finalization)
  - `2026-09-10-code-review-report.md` (Pass 1: FAIL)
  - `2026-09-10-code-review-report-2.md` (Pass 2: FAIL, structural gap found)
  - `2026-09-10-code-review-report-3.md` (Pass 3: PASS with gaps, all major gaps closed)

## Source of Truth Updated

The canonical spec store now reflects the new behavior:

- `openspec/specs/products-management/spec.md` — new Products Management capability (source of truth).

## Verification and Code-Review History

The change underwent 3 verification passes and 3 code review passes, all of which found genuine issues that were resolved:

### Verification (opsx-verify)

**Pass 1 (`2026-09-10-verify-report.md`): `PASS WITH WARNINGS`**

- **Tests:** 108 green (50 unit Surefire + 58 integration Failsafe), all scenario scenarios traced.
- **Spec compliance:** 8/8 requirements implemented, 17/18 scenarios fully traced to tests that genuinely assert the scenario's content (scenario 4 partially: `@Size` bounds on `ocpc`/`title` implemented but not directly tested anywhere).
- **Design decisions:** D1–D15 verified in code, key decisions (D1 guard order, D2 object associations, D4 DRY helper, D6 transaction boundary, D9 exception handler, D13 docs fix) spot-checked.
- **Risks:** Both carried risks confirmed genuine and appropriately disclosed:
  - **SpotBugs:** 9 pre-existing findings in `Brand`/`Dispensary` (confirmed identical on `main`, not introduced by KAN-8); 24 new findings introduced by D2 object associations (excluded via `spotbugs-exclude.xml` scoped to `Product` and `Subcategory`).
  - **OCPC race:** Application-level uniqueness, racy under concurrency, disclosed in four places with named remedy (partial unique index, deferred); confirmed no index exists on `products.ocpc`.
- **Warnings identified:**
  - **W1** — `apply-progress.md` missing Batch 5 entry; two forward-references in `design.md` and `spotbugs-exclude.xml` to the missing section.
  - **W2** — `@Size(max=64)`/`@Size(max=255)` bounds implemented but zero test coverage anywhere (cheap fix: two controller tests).
  - **W3** — "90% coverage gate" asserted in three artifacts but no `jacoco:check` execution exists (misleading claim).
  - **W4** — `mvn verify` fails on the branch (`spotbugs:check` 9 findings), equally true on `main` (pre-existing, not a regression).

### Code Review (opsx-code-review)

**Pass 1 (`2026-09-10-code-review-report.md`): `FAIL`**

Issues identified:
- **Major #1:** `ProductRepositoryTests.findById_when_productExists_returnsAllMappedColumns` used `assertNotNull(found.get<X>().getId())` for seven FK fields, which would pass even if two FK mappings were silently transposed. Fix: Replace with `assertEquals(expectedId, found.get<X>().getId())` assertions capturing and comparing actual persisted ids.
- **Major #2:** `CreateProductResponse.java` and `ProductController.create` mapping untested for non-reference fields (`description`, `formatValue`, `contentValue`, `thc`, `cbd`). The `validRequestBuilder()` fixture used same-valued integers and all-`TRUE` booleans, leaving transposition-pairs undetectable. Fix: (a) Add `ArgumentCaptor` to capture the command passed to the service in `create_shouldReturn201_when_validRequest`, assert every field; (b) Give fixture values distinct integers and distinct booleans; (c) Extend native SQL query to read all persisted columns, not just `ocpc/title/created_at`.
- **Minor W1:** D6's `@Transactional` annotation is not directly exercised by any test (all guards precede the single `save`), though D6 itself and `tasks.md` 8.11 correctly explain why this is belt-and-braces rather than a defect.
- **Minor W2:** No test for `@Size` bounds; test coverage incomplete.
- **Minor W3:** `docs/backend-standards.md` gains only a minor bullet; `docs/data-model.md` changes lack wording fix assertion.
- **Minor W4:** Unclear OpenAPI endpoint URL in documentation.

**Pass 2 (`2026-09-10-code-review-report-2.md`): `FAIL`**

Response to Pass 1 gaps:

- **Major #1 closure:** Task 3.10 retro-added to `ProductRepositoryTests` — strengthened to assert each captured FK id matches its own seeded value. Verified by temporarily swapping FK mappings; test now fails correctly.
- **Major #2 closure — Part A (request→command):** Task 6.13 added `ArgumentCaptor<CreateProductCommand>` to `create_shouldReturn201_when_validRequest`, asserts every 17 command fields against `validRequestBuilder()`'s values. Fixture updated with distinct booleans (originally `isCoreProduct=T, approved=T, enabled=T`; now `isCoreProduct=T, approved=F, enabled=T` to disambiguate). Verified three transpositions now caught at unit level.
- **Major #2 closure — Part B (entity→response):** Task 6.12 extended response assertions to all 18 fields with distinct reference ids (`collectionId=1..7`). Verified by temporarily swapping mappings; test now fails correctly.
- **Major #2 closure — Part C (database end-to-end):** Task 8.16 extended `should_return201AndPersistProduct_when_requestIsValid`'s native query to also select all seven FK columns, plus `description`, `formatValue`, `contentValue`, `thc`, `cbd`, asserting each against `validBody()`'s distinct fixture values. Changed `should_persistSuppliedFlags_when_theyAreExplicit` to use distinct booleans (`isCoreProduct=T, approved=F, enabled=T`) instead of all-`false`, closing the `@PositiveOrZero` gap.

**New Major finding discovered in Pass 2:**
- **Major #3 — Pigeonhole structural gap:** Three-valued `Boolean` fields (`isCoreProduct`, `approved`, `enabled`) with only 2 possible values in a single fixture cannot discriminate all three pairs. Tasks 6.13 and 6.14 together close this: (a) give distinct fixture values that disambiguate two pairs; (b) add a second test, `create_shouldNotTransposeIsCoreProductAndEnabled_when_theirValuesDiffer`, with a fixture where those two specifically differ (`isCoreProduct=F, approved=T, enabled=T`), closing the third pair on all three mapping directions. Verified both gaps: swapping `isCoreProduct`/`enabled` now fails on both request and response sides.

**Pass 3 (`2026-09-10-code-review-report-3.md`): `PASS WITH GAPS`**

Response confirms all prior Major gaps closed:
- Major #1 (FK transposition): Closed by task 3.10.
- Major #2a (request→command): Closed by task 6.13 with `ArgumentCaptor` + distinct booleans.
- Major #2b (entity→response): Closed by task 6.12 with all 18 field assertions + distinct reference ids.
- Major #2c (database e2e): Closed by task 8.16 with extended native query + distinct fixture.
- Major #3 (pigeonhole): Closed by task 6.14 adding the third-pair test.

Remaining gaps are Minor and non-blocking: D6 auditability (3 guard-order tests would have failed even without `@Transactional`, so the annotation is defensive not primary), `@Size` test coverage (two cheap controller tests would close it), minor documentation wording consistency (S3 curl URL inconsistency, S5 cleanup scope).

## Final Test Counts and Quality Metrics

- **Unit tests (Surefire):** 56 passed (includes 50 new product-related tests plus 6 existing baseline regression confirmations)
- **Integration tests (Failsafe):** 59 passed (includes 19 new product endpoint tests plus 40 existing baseline regression confirmations)
- **Grand total:** 115 tests green
- **Coverage:** No enforced gate exists; `jacoco:check` not bound in `pom.xml` (stale claim in three artifacts, now noted).
- **Code quality:** `mvn -o -Dspotbugs.skip=true spotless:check` clean; `mvn -o spotbugs:check` fails on 9 pre-existing findings (confirmed identical on `main`, not regressions).
- **Database state:** All ten tracked tables returned to 0 rows after `mvn verify`; Flyway still at `0.1.0`; no schema drift; zero new migrations.

## Known Carried-Forward Risks and Limitations

1. **SpotBugs: 9 pre-existing findings in `Brand`/`Dispensary`** — Confirmed pre-existing and unrelated to KAN-8. `mvn -o spotbugs:check` red on `main` identically. Not a regression; out of scope per the proposal's Impact boundary.

2. **SpotBugs: 24 new findings from D2 object associations** — Direct, foreseeable consequence of mapping design decision D2 (plain object associations via Lombok `@Getter`/`@Setter`). Mitigated via `spotbugs-exclude.xml` scoped to exactly `Product` and `Subcategory` by class name (not by package). Excluded findings do not silently absorb the pre-existing 9.

3. **OCPC uniqueness is application-level and racy** — Two concurrent POSTs can both pass `existsByOcpc` (no unique index on `products.ocpc`). Disclosed in proposal, design, spec, and this report. Remediation: partial unique index (`CREATE UNIQUE INDEX CONCURRENTLY ux_products_ocpc ON products (ocpc) WHERE deleted_at IS NULL`), separate migration ticket, deferred. Blast radius: duplicate row, not corruption.

4. **Soft-deleted OCPC reuse** — A soft-deleted product releases its `ocpc` for reuse. Disclosed as a design choice, spec scenario 10, and tested end-to-end.

5. **No authentication or authorization** — The endpoint ships open; an auth story must precede production exposure (carried from proposal open item 4).

6. **No unique index on `ocpc`** — Full scan on uniqueness check; acceptable at bootstrap catalogue volumes; same index closes both the race and the scan.

7. **`description` is unbounded end-to-end** — No `@Size` constraint on request DTO, no HTTP body size limit configured. Documented in proposal item 11 as a disclosure, not a silent limitation. Candidate for follow-up hardening story alongside item 5.

8. **Reference-resolution 404s as existence oracle** — Varying one of seven references while holding the others constant leaks binary "exists / does not exist" answers for `collections`, `categories`, `subcategories`, `units`, `brands`, `strains` (proposal item 12). Dominated by and subordinate to item 5 (no auth). Disclosed, awaits auth story resolution.

9. **Unsanitized log injection in `ProductServiceImpl`** — `log.warn("Rejected product creation: OCPC {} already used by a live product", command.ocpc())` logs up to 64 characters of arbitrary client input on an unauthenticated endpoint (proposal item 13, CWE-117). Not fixed in this change; first service in the codebase to log client-supplied text; follow-up should evaluate log-injection sanitization project-wide. No existing precedent to break.

10. **DDD Aggregate Rule 3 deliberately violated** — `Product` holds `@ManyToOne` object associations to `Brand` and `Strain` (other aggregates) rather than bare identifiers (D2). Mitigated by structural guard rails: no `CascadeType`, no `orphanRemoval`, all associations `LAZY`, no inverse collections. Single-aggregate transaction boundary preserved behaviorally. Named follow-up: future refactor to migrate all mappings at once or to adopt value-object identifiers. Disclosed in proposal open item 9, design D2, and `Product.java` class comment.

11. **Anemic domain model** — Taxonomy invariant lives in `ProductServiceImpl`, not in the `Product` aggregate root, because `DomainException` and friends live in the application layer (D15). Real Clean Architecture constraint; future fix requires moving exception hierarchy into domain layer. Named follow-up.

12. **`Brand` field-based `equals`/`hashCode`** — `Product` uses identity-based equality (D14), `Brand` uses field-based (with latent issues), creating inconsistency. Deliberate, out of scope; named follow-up alongside item 11.

13. **~~Partially guessed test coverage~~ — RESOLVED (Batch 9, code-review pass 3 m1)** — `@Size(max=64)`/`@Size(max=255)` bounds originally had zero test instances anywhere (verify pass 1's W2). Closed before archive: `create_shouldReturn201_when_ocpcIsExactlyAtMaxLength` and `create_shouldReturn201_when_titleIsExactlyAtMaxLength` were added to `ProductControllerTests`, proven load-bearing by tightening both bounds and observing the new tests fail, then reverting. No longer an open item; kept here only for audit-trail continuity.

14. **~~Missing Batch 5 apply-progress entry~~ — RESOLVED (corrective pass following verify pass 1's W1)** — Sections 8–14 originally had no batch narrative in `apply-progress.md`. Closed before archive: a "Batch 5" section was added retroactively (see `apply-progress.md`), and the two forward-references in `design.md` and `spotbugs-exclude.xml` now resolve to real content. No longer an open item; kept here only for audit-trail continuity.

Items 1–12 are accepted, disclosed, out-of-scope risks that remain open and are carried forward as follow-up work. Items 13–14 were real gaps caught during this change's own verify/code-review passes and were fully closed before archiving — they are retained above as an audit trail, not as outstanding risk.

## SDD Cycle Complete

The change has been fully planned (proposal + 13 open items), specified (8 requirements, 18 scenarios, concrete and testable), designed (15 decisions with alternatives), implemented and verified (115 tests green, 0 CRITICAL findings, 4 warnings none blocking), code-reviewed (3 passes: FAIL → FAIL → PASS WITH GAPS, with all gaps subsequently closed in a final corrective pass), and archived. The main spec is promoted to `openspec/specs/products-management/spec.md` as the source of truth going forward.

Ready for the next change.
