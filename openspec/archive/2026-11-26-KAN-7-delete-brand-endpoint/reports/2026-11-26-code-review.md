# Code Review — KAN-7 Delete Brand Endpoint

**Change**: `KAN-7-delete-brand-endpoint`
**Date**: 2026-11-26
**Reviewer mode**: code-review phase sub-agent (review only; no production code changed; no git commit)
**Schema**: `be-user-story` (`openspec/config.yaml`). Position: proposal → specs → design → tasks → apply → verify → **code-review** → archive.
**Branch**: `feat/KAN-7-delete-brand-endpoint` at `c0e3fd4` (even with `main`). All implementation is an uncommitted working-tree diff plus one untracked DTO. No feature commits exist, so RED-before-GREEN chronology cannot be reconstructed from git history.
**Sources**:
- `openspec/changes/KAN-7-delete-brand-endpoint/proposal.md`
- `openspec/changes/KAN-7-delete-brand-endpoint/specs/brands-management/spec.md`
- `openspec/changes/KAN-7-delete-brand-endpoint/design.md`
- `openspec/changes/KAN-7-delete-brand-endpoint/tasks.md`
- `openspec/changes/KAN-7-delete-brand-endpoint/verify-report.md`
- `openspec/changes/KAN-7-delete-brand-endpoint/reports/2026-11-26-step-N+1-unit-test-and-db-verification.md`
- `openspec/changes/KAN-7-delete-brand-endpoint/reports/2026-11-26-step-N+1-manual-curl-verification.md`
- `docs/backend-standards.md`, `AGENTS.md`, `openspec/config.yaml`
- Working-tree diff (`git diff`) of the eight modified files, plus untracked `src/main/java/com/example/demo/presentation/api/model/RemoveBrandResponse.java`
**Skills applied**: adversarial-review, code-auditing (including `references/audit-methodology.md` security/quality categories), solid-principles, dry-principle. `.claude/context/memory/learnings.md` is absent; nothing was recorded there.
**Suite re-execution**: not repeated in this phase. Behavioral claims below are from source inspection. The verify report's targeted rerun (service/controller unit tests and `BrandControllerEndpointsTests`) is cited only as recorded evidence, not as a result this review reproduced.

## Adversarial review

**Scope**: KAN-7 uncommitted working tree (`DELETE /api/brands/{id}`)
**Sources**: spec/design/tasks/verify/reports listed above, plus `git diff` against `HEAD`

### Spec and task alignment

The one added requirement and all seven scenarios are implemented on the path the spec requires. This review re-checked the five behavior locks the phase was asked to confirm; all five hold in code, not only in the verify narrative.

| Lock | Result | Evidence |
|------|--------|----------|
| Soft-delete only; row is not physically removed | Holds. `deleteById` sets `deletedAt` and `save`s. It never calls `repository.delete` / `deleteById`. `deleted_at` is an updatable column, so the timestamp is a normal dirty field, not only an `@SQLDelete` side effect. `@PreRemove` is not on this path. | `BrandServiceImpl.java:179-180`; `BaseEntity.java:52-53, 69-72`; `Brand.java:47-48`; integration row-still-present assertion `BrandControllerEndpointsTests.java:495-504` |
| 404 before guard, including an already-soft-deleted brand that still has a live product | Holds structurally and in tests. `findById` / `NotFoundException` runs before `countLiveProductsByBrandId`. `@SQLRestriction("deleted_at IS NULL")` makes an already-deleted brand invisible to `findById`, so no manual `deletedAt` filter was added (design D2). | `BrandServiceImpl.java:171-175`; `Brand.java:47-48`; unit `never()` on the count `BrandServiceTests.java:409-423`; integration seeds a live product then expects 404 `BrandControllerEndpointsTests.java:528-542` |
| 409, not 400, for brand-in-use | Holds. The guard throws `ConflictException`. `GlobalExceptionHandler.handleConflict` maps that type to HTTP 409 with field `general`. No `BadRequest` / `400` status is raised on this path. OpenAPI declares `409`, and does not copy `DispensaryApi`'s `204` drift. Ticket "bad request" wording was not implemented. | `BrandServiceImpl.java:175-177`; `GlobalExceptionHandler.java:52-56`; `BrandApi.java:90-97`; `DispensaryApi.java:77-81`; `docs/backend-standards.md:337-343, 423` |
| Exact messages | Holds. 404 is `Brand not found with ID: {id}` (colon). 409 is `Brand with ID {id} cannot be deleted because it is used by products` (no colon). 400 non-numeric id is the existing handler: field `id`, message `Invalid value for parameter: id`, and does not use `ex.getMessage()`. | `BrandServiceImpl.java:174, 176-177`; `GlobalExceptionHandler.java:94-99`; asserted at `BrandServiceTests.java:369, 387-388` and `BrandControllerEndpointsTests.java:523, 555-558, 587-589` |
| Atomicity of a rejected delete | Holds in the service. The only mutation is the last statement. `NotFoundException` and `ConflictException` extend `DomainException` → `RuntimeException`, and the method is `@Transactional(rollbackFor = Exception.class, propagation = REQUIRED)`, so a throw before `save` does not commit a tombstone. | `BrandServiceImpl.java:168-180`; `DomainException.java:20`; unit `never().save` `BrandServiceTests.java:370, 389`; integration count + `deleted_at` unchanged across 400/404/409 `BrandControllerEndpointsTests.java:594-611` |

Payload shape matches design D1: `RemoveBrandResponse` is a six-component record (`id`, `name`, `description`, `stateLicense`, `brandType`, `adminId`) with the GPL header and copyright line. The controller maps each component from the entity returned by the service, including `brand.getBrandType().getName()`. Guard scope is `products` only; `BrandServiceImpl` does not inject `ProductRepository`; no inverse `products` collection was added.

Design-accepted residuals were not reopened as defects: TOCTOU between count and save (`design.md` Risk 1), open-in-view lazy `brandType` after commit (`design.md` Risk 11, `BrandController.java:144`), and ungarded `brand_images` / `brand_featured_products` / `fav_user_brands` (`design.md` Decision 7). The integration happy path does resolve `brandType` to `"grower"` (`BrandControllerEndpointsTests.java:493`), so the OSIV coupling works under the current `spring.jpa.open-in-view: true` setting. It remains a documented 500 if that setting is later turned off.

SOLID: the controller stays a thin mapper (SRP); the service depends on `BrandRepository`, not a concrete product repository (DIP); the new repository method is one question, not a fat interface (ISP). The native count is closed to future referencing tables (OCP residual = design Risk 10), which the spec explicitly does not widen. DRY: production messages have a single source in `BrandServiceImpl`. Test-fixture overlap with `ProductEndpointsTests` was not extracted (second copy, different purpose, and the DRY skill forbids extracting test setup for its own sake).

Security (code-auditing categories): the count query is a constant SQL string with a named `:brandId` parameter, not concatenated input. New error strings are fixed templates plus the caller-supplied id; they do not echo `ex.getMessage()`. No secrets, no new auth bypass relative to sibling brand routes. The application has no `SecurityFilterChain` / `@PreAuthorize` anywhere, so this DELETE is unauthenticated in the same way as `POST` / `PATCH /api/brands`. That is a pre-existing product exposure, not a regression against a protected sibling, and it is not a spec violation. It is recorded below as a question, not a blocker.

### Adjudication of the four verify warnings

#### 1. Unqualified whole-table DELETEs vs row-specific cleanup claims

**Decision: aggravated pre-existing accepted pattern, plus a real report-honesty defect. Does not block archive.**

What the diff actually did:

- Before this change, `BrandControllerEndpointsTests.tearDown()` already issued unqualified `DELETE FROM brands` and `brandTypeRepository.deleteAll()`. The brands delete is the unchanged line in the hunk; only the surrounding deletes are new.
- This change added eight more unqualified deletes: `products`, `strains`, `seed_companies`, `strain_types`, `subcategories`, `categories`, `collections`, `units` (`BrandControllerEndpointsTests.java:104-112`).
- That list is a copy of `ProductEndpointsTests.tearDown()` (`ProductEndpointsTests.java:132-140`). KAN-8 task 8.18 explicitly accepted that pattern as a deliberate, simpler choice on a test schema with no concurrent writers, and recorded it as non-blocking code-review Minor m5. It was not invented here.
- The new deletes are also what makes the new product fixtures removable. A row-scoped brand delete would fail the products FK unless products (and their reference rows) are removed first. Widening the blast radius was the mechanical consequence of seeding products inside this class.

What is actually wrong:

- Task 4.1 is checked, but its verify text ("removes exactly those rows") is not what the code does (`tasks.md:64`; `BrandControllerEndpointsTests.java:93-124`).
- The unit report says tests "seed and remove exactly their own rows" (`reports/2026-11-26-step-N+1-unit-test-and-db-verification.md:71-76`). That sentence is false as a description of the SQL.
- The manual report says "Removed exactly the seeded rows" and then prints unqualified `DELETE FROM` statements (`reports/2026-11-26-step-N+1-manual-curl-verification.md:115-124`). The prose and the SQL in the same section contradict each other.

What is not wrong:

- This is not a production defect in `DELETE /api/brands/{id}`.
- The verification database named in the unit report (`tests_kan7deletebrandendpoint`) had a zero-row baseline, so that particular run's post-counts matching pre-counts does not prove row-scoped cleanup; it is also what a whole-table delete produces on an empty database. Both can be true at once. The mechanism claim is still false.
- Running this class against a shared non-empty `tests` database will wipe those tables, not only ids this class inserted. That hazard already existed for `brands` in this class and for the same table set in `ProductEndpointsTests`. This change extends the brand endpoint class to the product-suite blast radius. It does not introduce a new cleanup style.

Archive impact: do not block. The implementation is correct, the cleanup style was previously accepted, and this review records the false "exactly their own rows" wording so archive does not have to trust it. Amending the two step reports is a follow-up, not a precondition.

#### 2. Task 0.2 unchecked

**Decision: honest incomplete process checkbox. Does not block archive. Do not check it from the current tree.**

Task 0.2 requires a clean `git status --porcelain` before any code, and no commit (`tasks.md:39`). The branch has no commits beyond `main` (`HEAD` is `c0e3fd4`, `main...HEAD` is `0 0`). Every implementation file is an uncommitted modification or an untracked file, which is what this phase was told to review ("NO git commits"). Current porcelain output cannot prove the tree was clean before the first edit, and checking the box now would invent that evidence. Leave it unchecked. An explicit exception in this report is the correct close-out. It is not a product defect.

#### 3. RED-before-GREEN not independently auditable

**Decision: evidence limitation, not an observed implementation defect. Does not block archive.**

`tasks.md` sections 2–4 are ordered before sections 5–9, and the unit report lists RED commands before compile/GREEN commands (`reports/2026-11-26-step-N+1-unit-test-and-db-verification.md:15-22`). Those commands are purposes, not captured failure summaries. With no commit chronology, this review cannot prove tests failed for a missing type before production code existed. The tests that exist now do assert the specified behaviors (exact messages, no `save` on 404/409, guard not called when lookup is empty, 200/404/409/400 at the endpoint). That shows the resulting suite is adequate. It does not show the TDD sequence. Treat the process claim as unproven, not as a failed behavior scenario.

#### 4. 90% coverage claim unsubstantiated

**Decision: the claim is unsubstantiated, and the build does not enforce the documented gate. Does not block archive.**

`docs/backend-standards.md:88-91` and `:578-590` require 90% branches/functions/lines/statements and say `mvn test` generates coverage reports. `pom.xml:549-570` binds JaCoCo `prepare-agent` / `prepare-agent-integration` only. There is no `jacoco:check` execution and no minimum ratio. The reporting block (`pom.xml:807-816`) emits reports from `mvn site`, not a failing gate on `mvn test` or `mvn verify`. A green verify therefore cannot have "satisfied" a 90% gate, because the build does not have one. No JaCoCo HTML/XML report is present under `target/`. Task 11.3 and the unit report assert the gate without a percentage (`tasks.md:109-110`; unit report `:37-53`).

This is a pre-existing tooling gap (the standard and the POM disagree) plus an over-claim in this change's report. Qualitative inspection of the new methods shows every specified branch is exercised (missing id, already deleted, count > 0, count == 0, non-numeric id, response mapping). That is not a measured 90%. It is enough to refuse a coverage-based FAIL. It is not enough to repeat the 90% number.

### Findings

| Severity | Area | Finding | Evidence | Suggested fix (code / spec / tests) |
|----------|------|---------|----------|--------------------------------------|
| Minor | Tests / report honesty | Teardown is unqualified whole-table `DELETE FROM`, not row-scoped cleanup. This change aggravated a pre-existing accepted pattern (this class already deleted every brand; `ProductEndpointsTests` already deleted the same product-fixture tables, accepted in KAN-8 task 8.18). The step reports and checked task 4.1 still say the opposite. Not a production defect. Does not block archive. | `BrandControllerEndpointsTests.java:93-124` (added lines 104-107 and 109-112; `DELETE FROM brands` at 108 was already present). Same list at `ProductEndpointsTests.java:132-140`. False claim: unit report `:71-76`; manual report `:115-124`; `tasks.md:64`. | Tests or docs, follow-up only: either delete by captured ids, or amend the two reports and task 4.1 verify text to say whole-table cleanup on a disposable test database, matching the KAN-8 acceptance. Do not treat the current prose as evidence of row-scoped cleanup. |
| Minor | Process | Task 0.2 remains unchecked and cannot be closed from this tree without fabricating a clean pre-code status. | `tasks.md:39`. `git status` shows the eight modified files plus untracked `RemoveBrandResponse.java` and the change folder. `HEAD` equals `main` (`c0e3fd4`); no feature commits. | Artifacts: leave the box unchecked and carry this exception into archive. Do not check it. |
| Minor | Process / TDD | RED-before-GREEN is claimed and not auditable. No failing-test output is retained, and the uncommitted tree has tests and production code together. | `tasks.md:46-71`; unit report `:15-22`; `git log` has no KAN-7 commits. | Tests/artifacts, optional: if a later change needs audit-grade TDD proof, keep the RED failure summary. Not required to archive this behavior. |
| Minor | Coverage evidence | "90% gate satisfied" is not supported. JaCoCo does not fail the build on a ratio, and no report with percentages was retained. New branches look exercised; that is not a measurement. | `docs/backend-standards.md:88-91, 578-590`; `pom.xml:549-570, 807-816`; `tasks.md:109-110`; unit report `:37-53`; no `target/site/jacoco` report in the workspace. | Artifacts: stop claiming a number that was not measured. Wiring `jacoco:check` is a project-wide change, out of scope here, and would be a separate ticket because it would start failing builds that today pass. |
| Minor | Tests | The already-soft-deleted integration test proves 404 (and that a live product does not flip it to 409) but does not read `deleted_at` after the second DELETE. The atomicity test never re-issues DELETE against the soft-deleted id, so it also does not pin "no further change" on that row. The service cannot re-stamp on the current path (throw before `save`), so this is a regression-net gap, not a bug found in the implementation. | `BrandControllerEndpointsTests.java:528-542` (no `brandDeletedAt` assertion); `:594-611` (404 target is `999999`, not `softId`). Spec scenario "Delete an already soft-deleted brand" requires no further change (`specs/brands-management/spec.md:20-25`). Task 4.3 asked for `deleted_at` unchanged (`tasks.md:66`). | Tests, optional: capture `deleted_at` before the second DELETE and assert equality. Non-blocking. |
| Suggestion | Tests | Non-numeric id asserts 400, field `id`, the fixed message, and an unchanged brand count. It does not assert the service/repository was not invoked. The typed `Long` path variable is what makes Spring fail conversion before the handler; that is the specified mechanism, and it is easy to regress only if someone changes the parameter type. | `BrandControllerEndpointsTests.java:579-592`; `BrandApi.java:98-99`; `GlobalExceptionHandler.java:94-99`. | Tests, optional: controller-level `verify(brandService, never()).deleteById(...)` on `DELETE /api/brands/not-a-number`. |
| Suggestion | OpenAPI | `BrandApi.delete` documents 200/404/409 and not 400. Design D8 asked for exactly those three. Clients reading OpenAPI will not see the non-numeric 400 that the global handler already returns. | `BrandApi.java:93-97`; design D8 (`design.md:117-119`). | Spec/API docs, optional: add a 400 `@ApiResponse` if the published contract should list type-mismatch. Not a behavior bug. |
| Question | Security (accepted, pre-existing) | DELETE is unauthenticated, as are the other brand routes. There is no security filter in the codebase. The 409 body also tells a caller that live products reference the brand. Both are specified or consistent with the current API, not a new bypass. | No `SecurityFilterChain` / `@PreAuthorize` under `src/`. `BrandApi.java:90-99`. Spec requires the 409 message (`specs/brands-management/spec.md:27-31`). | No code change in this slice. Auth, if required, is a separate capability. Do not block archive on it. |

No Blocker. No Major. No CRITICAL finding.

### Checks that held (these directly close the failure modes that were hunted)

- Swapping guard and resolve would fail `BrandServiceTests.should_throwNotFoundBeforeGuard_when_brandIsAlreadySoftDeleted` (`never()` on `countLiveProductsByBrandId`, `BrandServiceTests.java:422`) and would turn the integration already-deleted-plus-live-product case into 409 (`BrandControllerEndpointsTests.java:528-542`).
- Returning 400 for brand-in-use would fail `isConflict()` / `$.status == 409` (`BrandControllerEndpointsTests.java:552-553`) and the controller test (`BrandControllerTests.java:330-348`).
- Physical delete, or `repository.delete()` without `setDeletedAt` + `save`, would fail the unit `save` verification (`BrandServiceTests.java:355`) and/or the native `count(*) ... = 1` plus non-null `deleted_at` check (`BrandControllerEndpointsTests.java:495-504`).
- A wrong 404 or 409 string would fail the exact `assertEquals` / `jsonPath` locks cited above. The colon difference between the two messages is pinned, not implied.
- String-field transposition in `RemoveBrandResponse` mapping is pinned by distinct fixture values in `BrandControllerTests.java:283-300` (`name` / `description` / `stateLicense` / `brandType` differ). `id` (`Long`) and `adminId` (`Integer`) cannot be silently swapped in the record constructor; that is a compile error.
- OpenAPI does not repeat the dispensary `204` annotation bug (`DispensaryApi.java:81` vs `BrandApi.java:94-96`).

## Status

Final verdict `PASS WITH GAPS`.

Nothing in this review blocks archive. The delete path matches the spec: soft-delete only, 404 before the guard, 409 not 400, exact messages, and no mutation on the rejection paths. The four verify warnings are real as evidence/process/report issues. None of them is a functional defect in the endpoint. Warning 1 is an aggravated copy of a cleanup style this repository already accepted, plus false "row-specific" wording that this report supersedes.

### Recommended next steps (before archive)

- Archive. Do not check task 0.2. Do not treat the step reports' "exactly their own rows" sentences as true; this review is the correction.
- Optional follow-ups, not archive gates: amend the two step reports (or task 4.1's verify sentence) so they describe whole-table cleanup; add the `deleted_at` stability assertion on the already-deleted integration case; do not invent a 90% figure unless a JaCoCo report is actually produced.
- Do not widen the product guard, add auth, or "fix" open-in-view inside this change. Those are explicit non-goals or accepted risks.

## Return summary

## Adversarial review

**Scope**: KAN-7-delete-brand-endpoint (uncommitted working tree on `feat/KAN-7-delete-brand-endpoint`, `HEAD` `c0e3fd4`)
**Sources**: change proposal, spec, design, tasks, verify-report, both step reports, `docs/backend-standards.md`, `git diff` plus `RemoveBrandResponse.java`

### Findings

| Severity | Area | Finding | Evidence | Suggested fix (code / spec / tests) |
|----------|------|---------|----------|--------------------------------------|
| Minor | Tests / report honesty | Whole-table test deletes contradict row-specific cleanup claims. Aggravated pre-existing accepted pattern. Does not block archive. | `BrandControllerEndpointsTests.java:104-112`; unit report `:71-76`; manual report `:115-124` | Docs/tests follow-up; accept as documented here |
| Minor | Process | Task 0.2 unchecked; current dirty tree cannot honestly close it | `tasks.md:39` | Leave unchecked; exception recorded here |
| Minor | Process / TDD | RED-before-GREEN not auditable | `tasks.md:46-71`; unit report `:15-22`; no feature commits | Optional retained RED output later |
| Minor | Coverage evidence | 90% claim has no number and the POM does not enforce the gate | `pom.xml:549-570`; `tasks.md:109-110` | Stop claiming an unmeasured percentage |
| Minor | Tests | Already-deleted path does not assert `deleted_at` unchanged | `BrandControllerEndpointsTests.java:528-542, 594-611` | Optional assertion |
| Suggestion | Tests | Non-numeric id does not assert "no lookup" directly | `BrandControllerEndpointsTests.java:579-592` | Optional `never()` verify |
| Suggestion | OpenAPI | 400 omitted; design asked for 200/404/409 only | `BrandApi.java:93-97` | Optional annotation |
| Question | Security | Unauthenticated delete, consistent with the rest of the API | no security filter in `src/` | Separate capability; not this change |

### Status
Final verdict `PASS WITH GAPS`

### Recommended next steps (before archive)
Archive. No blocker and no major. Carry the task 0.2 exception and the teardown-claim correction in this report. Do not check task 0.2. Optional test/report cleanups may follow archive.
