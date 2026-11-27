# Verification Report — KAN-7 Delete Brand Endpoint

**Change:** `KAN-7-delete-brand-endpoint`  
**Mode:** OpenSpec `be-user-story` verification  
**Date:** 2026-11-26  
**Dependency chain:** proposal → specs → design → tasks → apply → verify → code-review → archive (`openspec/config.yaml:1, 23-25`)

## Executive summary

The implementation matches all seven behavior scenarios in the brands-management spec. The service resolves the brand before guarding, throws the required exact 404/409 messages, counts only live product references, and mutates only `deletedAt` followed by `save`; the API returns the requested 200 `DataResponse<RemoveBrandResponse>`. The targeted service/controller suites and the brand endpoint integration class passed when rerun.

No functional or design defect was found in the delete path. Verification is **PASS WITH WARNINGS** because (1) the required clean-tree task remains unchecked, (2) RED-before-GREEN is recorded as a claim but its failure output/chronology is not independently preserved, (3) the endpoint integration test teardown deletes whole tables despite reports claiming row-specific cleanup, and (4) the stated coverage threshold has no numerical coverage evidence. The cleanup/report discrepancy is the most operationally significant warning; see below.

## Completeness

| Area | Result | Evidence |
|---|---|---|
| Behavior contract | Complete: 1 requirement, all 7 scenarios have at least one corresponding integration test | Spec `specs/brands-management/spec.md:3-48`; scenario matrix below |
| Implementation layers | Complete: DTO, repository count guard, service contract/implementation, API mapping, controller mapping | `RemoveBrandResponse.java:27-33`; `BrandRepository.java:39-43`; `BrandService.java:60-74`; `BrandServiceImpl.java:156-181`; `BrandApi.java:90-100`; `BrandController.java:124-146` |
| Tests | Complete for scenario mapping; targeted reruns pass | `BrandControllerEndpointsTests.java:483-611`; rerun evidence below |
| OpenSpec tasks | 61/62 checked; task 0.2 remains unchecked | `tasks.md:36-40`; only unchecked task is at line 39 |
| Scope | No unrelated production behavior or schema change found in the requested implementation files; no migration is specified | `proposal.md:24-31`; `design.md:27-33, 184-189` |
| TDD process evidence | Claimed by task ordering and reports, but not independently proven from retained failure output or history | `tasks.md:46-71`; unit report `reports/2026-11-26-step-N+1-unit-test-and-db-verification.md:15-22` |

## Build, tests, and coverage evidence

| Command / source | Result | Scope and limitations |
|---|---|---|
| `mvn test -Dtest='BrandServiceTests,BrandControllerTests'` (rerun during verification) | **PASS** — 30 tests, 0 failures, 0 errors, 0 skipped; service 17 and controller 13 | Direct rerun. Maven emitted Java-agent/deprecation warnings; no test failures. |
| `mvn verify -Dit.test='BrandControllerEndpointsTests' -DfailIfNoTests=false -Dspotbugs.skip=true` (rerun during verification) | **BUILD SUCCESS** — 219 Surefire unit tests passed; selected Failsafe class: 22 tests, 0 failures/errors/skips | Direct rerun against local PostgreSQL `tests_kan7deletebrandendpoint`. This filters Failsafe to the brand endpoint class and is not a fresh run of all 97 integration tests. SpotBugs was explicitly skipped for this command. |
| Step N+1 report | Reports full `mvn verify`: 219 unit + 97 integration tests, all passing; also reports Spotless and SpotBugs success | Claim cross-checked against source/test contents and compatible with the reruns, but raw full-suite output is not retained in the report (`reports/2026-11-26-step-N+1-unit-test-and-db-verification.md:27-60, 78-82`). |
| Coverage | **Not independently verified** | Backend standards require 90% coverage (`docs/backend-standards.md:88-91, 578-590`). The report/task says the gate was satisfied (`tasks.md:109-110`, unit report `:37-53`) but provides no percentage or JaCoCo report path. The rerun was filtered and does not establish the full integration-suite coverage. |

## Spec compliance matrix

| Spec scenario | Implementation/test evidence | Result |
|---|---|---|
| Successful soft delete, 200 envelope, row remains, GET exclusion | Service sets `deletedAt` then saves (`BrandServiceImpl.java:179-180`); API/controller use `DataResponse<RemoveBrandResponse>` (`BrandApi.java:90-100`, `BrandController.java:135-146`); integration test checks 200, physical row count 1, deleted timestamp, and GET exclusion (`BrandControllerEndpointsTests.java:483-510`) | **PASS** |
| Missing id returns 404, no delete | Exact `NotFoundException` in service (`BrandServiceImpl.java:171-174`); exact response and unchanged row count asserted (`BrandControllerEndpointsTests.java:513-526`); unit no-save assertion (`BrandServiceTests.java:358-370`) | **PASS** |
| Already-soft-deleted id returns 404 before guard, even with a live product reference | `Brand` has `@SQLRestriction("deleted_at IS NULL")` (`Brand.java:47-48`); service resolves before guard (`BrandServiceImpl.java:171-175`); unit verifies guard never called (`BrandServiceTests.java:409-423`); integration test includes a live product and asserts exact 404 (`BrandControllerEndpointsTests.java:528-542`) | **PASS** |
| Live product reference returns 409, no soft delete | Repository native count and service `> 0` conflict path (`BrandRepository.java:39-43`, `BrandServiceImpl.java:175-180`); exact 409 and unchanged timestamp asserted (`BrandControllerEndpointsTests.java:544-562`); unit asserts exact message/no save (`BrandServiceTests.java:373-390`) | **PASS** |
| Only soft-deleted products reference brand: deletion succeeds | Native query includes `p.deleted_at IS NULL` (`BrandRepository.java:39-43`); integration test soft-deletes the product natively then asserts 200 and brand tombstone (`BrandControllerEndpointsTests.java:564-577`); service unit covers count zero (`BrandServiceTests.java:392-407`) | **PASS** |
| Non-numeric id returns 400 | Typed `Long` route parameter (`BrandApi.java:98-100`); integration asserts status, standard field/message, and unchanged brand count (`BrandControllerEndpointsTests.java:579-592`); existing type-mismatch behavior is specified in `docs/backend-standards.md:349-359` | **PASS** |
| Rejected delete leaves catalog unchanged | Service throws before its only mutation (`BrandServiceImpl.java:171-180`); unit asserts no save on 404/409 (`BrandServiceTests.java:358-390`); integration checks count and target timestamps across 400/404/409 (`BrandControllerEndpointsTests.java:594-611`) | **PASS** |

## Correctness checks

| Check | Finding | Evidence |
|---|---|---|
| Soft-delete only; no physical row removal | Correct. Delete code uses `setDeletedAt` + `save`, not repository deletion. Integration test verifies row remains. | `BrandServiceImpl.java:179-180`; `BrandControllerEndpointsTests.java:495-504` |
| 404 resolution precedes guard | Correct. `findById`/404 occurs before the count query; test verifies the guard is not called for an absent lookup. | `BrandServiceImpl.java:171-175`; `BrandServiceTests.java:409-423` |
| Brand-in-use status | Correctly uses `ConflictException` → expected 409, not 400; API annotation includes 409. This follows the standard, which supersedes ticket wording. | `BrandServiceImpl.java:175-177`; `BrandApi.java:93-97`; `docs/backend-standards.md:337-359, 423` |
| Exact messages | Correct exact missing and in-use messages; endpoint tests assert them. | `BrandServiceImpl.java:174, 176-177`; `BrandControllerEndpointsTests.java:521-524, 553-558` |
| Non-numeric id | Framework type conversion occurs on the `Long` path argument before controller invocation; response matches existing 400 handler contract. | `BrandApi.java:98-100`; `BrandControllerEndpointsTests.java:579-592`; `docs/backend-standards.md:349-359` |
| Guard scope | Correctly counts only `products` with matching `brand_id` and `deleted_at IS NULL`; does not load Product entities or add unrelated guards. | `BrandRepository.java:39-43`; `design.md:82-115` |
| Atomicity | Correct structurally: all rejection throws precede the only state mutation; test code checks no save and selected database state remains unchanged. | `BrandServiceImpl.java:171-180`; `BrandServiceTests.java:368-390`; `BrandControllerEndpointsTests.java:594-611` |

## Design coherence

| Design decision | Assessment | Evidence |
|---|---|---|
| Layering / thin presentation handler | Coherent: API interface declares the route; controller delegates to service and maps the response; business checks remain in the service. | `BrandApi.java:90-100`; `BrandController.java:124-146`; `BrandServiceImpl.java:156-180` |
| Response contract | Coherent: the six response components match design D1 and the controller maps each from the returned entity. | `design.md:37-54`; `RemoveBrandResponse.java:27-33`; `BrandController.java:138-145`; controller test `BrandControllerTests.java:283-300` |
| Transaction / repository seam | Coherent: transactional service method, fixed resolve→guard→mark order, native count guard, no inverse products collection or ProductRepository coupling. | `BrandServiceImpl.java:168-180`; `BrandRepository.java:29-43`; `design.md:56-67, 82-94` |
| Existing accepted coupling/risk | `brandType` response mapping uses lazy association access after service return and relies on open-in-view. This is explicitly documented and accepted, not newly discovered scope drift. | `BrandController.java:138-145`; `design.md:16, 182`; `docs/backend-standards.md:424` |
| No scope expansion | No migration, physical delete, cascade, or guards on images/junction tables were introduced in the reviewed sources; these exclusions match the design. | `design.md:27-33, 109-115, 184-189` |

## TDD and report cross-check

- The task plan orders service/controller/endpoint RED tests in sections 2–4 before implementation sections 5–9 (`tasks.md:46-97`), and the unit report lists RED commands before compile/GREEN commands (`reports/2026-11-26-step-N+1-unit-test-and-db-verification.md:15-22`). The extant tests then exercise the expected behaviors, and targeted Green runs pass.
- This supports the claimed TDD sequence, but the report records command purposes rather than the actual RED failure summaries. The worktree is currently modified, with no commit chronology to independently establish the test-before-production ordering. This is an evidence limitation, not an observed implementation defect.
- The manual report's response claims for happy path, 404/409/400, soft-deleted-product-only, and OpenAPI align with source and integration tests (`reports/2026-11-26-step-N+1-manual-curl-verification.md:33-106`; source/test matrix above). Its database cleanup claim is not consistent with the actual teardown: it says seeded rows were removed exactly (`:115-129`), while the integration test issues unqualified whole-table deletes (`BrandControllerEndpointsTests.java:93-124`). Both verification reports therefore overstate cleanup precision; their reported zero-baseline row counts do not make those delete statements row-scoped.
- The unit report's full-suite counts are self-reported rather than a full suite rerun in this verification; current reruns independently establish only the stated unit suites and selected endpoint integration class.

## Issues

### CRITICAL

None.

### WARNING

1. **[WARNING] Integration-test teardown is broader than the reports/task claim.** `BrandControllerEndpointsTests.tearDown()` performs unqualified `DELETE FROM` statements against `products`, `strains`, `seed_companies`, `strain_types`, `brands`, `subcategories`, `categories`, `collections`, and `units`, then deletes all brand types (`src/test/java/com/example/demo/integration/endpoints/BrandControllerEndpointsTests.java:93-124`). This can erase unrelated pre-existing data in the configured test database. It contradicts the “exactly their own rows” claim in the unit report (`reports/2026-11-26-step-N+1-unit-test-and-db-verification.md:71-76`) and “Removed exactly the seeded rows” in the manual report (`reports/2026-11-26-step-N+1-manual-curl-verification.md:115-129`), and task 4.1's exact-cleanup expectation (`tasks.md:64`). Limit cleanup to captured inserted ids or explicitly enforce an isolated disposable database and make the reports precise.
2. **[WARNING] Mandatory task 0.2 remains incomplete.** It is the sole unchecked task and requires confirming a clean worktree before writing code (`tasks.md:36-40`). The current worktree is modified as expected for the in-progress change, so its historical initial cleanliness cannot be reconstructed from this verification. Close the task only with reliable evidence or document the exception; do not infer it from current status.
3. **[WARNING] RED-before-GREEN is not fully auditable from retained evidence.** Task ordering and the report's RED command list support the claim (`tasks.md:46-71`; unit report `:15-22`), but no observed RED failure result or chronology is preserved, and the current uncommitted worktree provides no independent ordering proof. Preserve the expected failing summaries in a verification artifact if the workflow requires audit-grade proof.
4. **[WARNING] 90% coverage claim is unsubstantiated in the report.** The project standard specifies 90% (`docs/backend-standards.md:88-91, 578-590`) and task 11.3 claims the gate (`tasks.md:109-110`), but the unit report supplies no coverage percentages or generated JaCoCo evidence (`reports/2026-11-26-step-N+1-unit-test-and-db-verification.md:37-53`). The verification rerun did not execute the entire integration suite, so it cannot fill that gap.

### SUGGESTION

1. **[SUGGESTION] Make the “no lookup” assertion explicit for non-numeric ids.** The endpoint test proves 400 and unchanged brand count (`BrandControllerEndpointsTests.java:579-592`), and the typed `Long` parameter means Spring rejects conversion before handler invocation (`BrandApi.java:98-100`). An explicit service/repository non-invocation assertion would make this sub-requirement regression-proof rather than relying on framework behavior.

## Overall verdict

**PASS WITH WARNINGS** — all seven required behavior scenarios are implemented and have corresponding tests; the targeted reruns pass, and no functional mismatch or critical defect was found. Before archive, address or explicitly accept/document the teardown/report inconsistency and close the remaining task/evidence gaps. The next workflow phase is **code-review**.

**Finding counts:** CRITICAL 0 · WARNING 4 · SUGGESTION 1.
