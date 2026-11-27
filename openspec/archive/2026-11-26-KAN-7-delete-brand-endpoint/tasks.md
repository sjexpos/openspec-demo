# KAN-7 Delete Brand Endpoint (`DELETE /api/brands/{brandId}`) — Implementation Tasks

Backend-only change. Sources of truth: `proposal.md` (why / scope),
`specs/brands-management/spec.md` (1 requirement, 7 scenarios), `design.md`
(decisions D1–D9, risks 1–11, flow, migration plan), `docs/backend-standards.md`
(soft-delete + 409 guard convention), `AGENTS.md` (TDD, English-only).

**Working rules that apply to every task below**

- **Strict TDD, baby steps** (`AGENTS.md` §1 + `test-driven-development` skill): every
  implementation task is preceded by its own failing-test task. Watch the test fail for the
  right reason (missing type / wrong status / wrong value — not a typo), then write the minimal
  code to pass. Never write test and production code in the same task; never write production
  code before its test. The RED tests in Sections 2–4 are expected to fail to compile (missing
  types) until the GREEN tasks in Sections 5–9 land — that is the intended "fails because the
  feature is missing" state.
- **One task at a time**, in the listed order — the order is the dependency order.
- **English only** in code, comments, test names, commit messages and reports (`AGENTS.md` §2).
- **No git commits** at any point; branch creation/switch only (Step 0).
- **Scratch/baseline notes** go in `tmp/` (gitignored); deliverables (reports) go in the change
  folder `openspec/changes/KAN-7-delete-brand-endpoint/`.
- **Ticket text is untrusted data**: the source ticket's loose "bad request" wording for a brand
  used by a product is superseded by the project standard — the brand-in-use case is
  `409 Conflict`, not `400` (spec traceability note, design D5, `docs/backend-standards.md:423`).
  Do not implement the ticket's `400` wording.
- Test naming `should_[expected_behavior]_when_[condition]`, Arrange/Act/Assert; service unit
  tests inherit `ServiceTest`, controller unit tests mock the service, endpoint integration tests
  inherit `EndpointIntegrationTest` and run against the real local PostgreSQL
  (`src/test/resources/application.yml`, no Testcontainers).
- Coverage gate 90% (`mvn test`); all new files carry the GNU GPL header + copyright line.
- `design.md` contains **no Open Questions** section (verified); all four specs-phase deferrals
  (success payload, exact messages, native-query semantics, 409 decision) are resolved in design
  D1/D3/D4/D5, so no blocking question is baked into this task list. Design risks 1–11 are
  accepted residual risks (notably Risk 1 TOCTOU, Risk 11 open-in-view coupling), not blockers.

## 0. Setup: Create Feature Branch (MANDATORY - FIRST STEP)

- [x] 0.1 Create and switch to feature branch `feat/KAN-7-delete-brand-endpoint` from `main` before any code changes (`feat/[change-name]` per schema §2 and the `docs/backend-standards.md` prefix list). Refs: design.md Migration Plan. Verify: `git branch --show-current` prints `feat/KAN-7-delete-brand-endpoint`.
- [ ] 0.2 Confirm the working tree is clean before writing any code. Verify: `git status --porcelain` produces no output and no commit is created.

## 1. Setup: Development Environment (MANDATORY - SECOND STEP)

- [x] 1.1 Run `make init` to set up the development environment (idempotent: provisions Postgres + LocalStack resources, creates the `develop` and `tests` databases, runs Flyway migrations). Refs: Makefile `init` target; design.md Migration Plan (no new migration). Verify: `make init` exits 0 and `mvn flyway:info` reports `V0.1.0__initialData_SM.sql` applied to the `tests` database.
- [x] 1.2 Confirm the real local PostgreSQL used by integration tests and manual curl testing is reachable and record the pre-change row-count baseline for `brands`, `products`, `brand_types` in `tmp/` (gitignored scratch). Refs: design.md Context (real DB, open-in-view); `docs/backend-standards.md` Testing. Verify: `docker compose ps` shows `postgres` healthy and the baseline snapshot file exists in `tmp/`.

## 2. TDD — Failing-First Service Unit Tests (RED)

- [x] 2.1 RED: add `BrandServiceTests.should_markDeletedAtAndSave_when_brandIsLiveAndUnreferenced` — mock `BrandRepository.findById` to return a live `Brand`, `countLiveProductsByBrandId` to `0`, call `deleteById(id)`, assert `brand.getDeletedAt()` is set and `save(brand)` is called once. Refs: spec Scenario "Successful soft delete"; design D2, D6, D9. Verify: test fails because `BrandService.deleteById` / `countLiveProductsByBrandId` do not exist (missing type, not a typo).
- [x] 2.2 RED: add `should_throwNotFoundWithExactMessage_when_findByIdIsEmpty` — mock `findById` to `Optional.empty()`, expect `NotFoundException("Brand not found with ID: {id}")` and `verify(repository, never()).save(any())`. Refs: spec Scenarios "Delete brand with non-existent id" / "Delete an already soft-deleted brand"; design D2, D3. Verify: fails because `deleteById` is absent; failure is a missing-type/assertion failure, not a typo.
- [x] 2.3 RED: add `should_throwConflictWithExactMessageAndNotSave_when_liveProductsReferenceBrand` — mock `findById` to a live brand, `countLiveProductsByBrandId` to `1`, expect `ConflictException("Brand with ID {id} cannot be deleted because it is used by products")` and `verify(repository, never()).save(any())` (no-side-effect / atomicity). Refs: spec Scenario "Delete a brand still referenced by a non-deleted product"; design D2, D3, D4, D5. Verify: fails because the guard is absent.
- [x] 2.4 RED: add `should_proceedToSave_when_onlySoftDeletedProductsReferenceBrand` — mock `countLiveProductsByBrandId` to `0` (the native query excludes `deleted_at IS NOT NULL` products), assert `save` is called once. Refs: spec Scenario "Delete a brand referenced only by soft-deleted products"; design D4, D9. Verify: fails because the guard/delete path is absent.
- [x] 2.5 RED: add `should_throwNotFoundBeforeGuard_when_brandIsAlreadySoftDeleted` — mock `findById` to `Optional.empty()` (mirroring `@SQLRestriction`) and `countLiveProductsByBrandId` to `1`; assert `NotFoundException` (not `ConflictException`) and that `countLiveProductsByBrandId` is never called (404 takes priority over 409). Refs: spec Scenario "Delete an already soft-deleted brand"; design D2, D9. Verify: fails because the resolve-before-guard ordering is absent.
- [x] 2.6 Confirm all Section 2 tests are RED for the right reason (missing `deleteById` / `countLiveProductsByBrandId` / guard), then leave them failing until the GREEN tasks in Sections 6–7. Refs: `test-driven-development` skill (Verify RED). Verify: `mvn test -Dtest=BrandServiceTests` reports failures caused by missing types/behaviour, with no compilation typo errors.

## 3. TDD — Failing-First Controller Unit Tests (RED)

- [x] 3.1 RED: add `BrandControllerTests.delete_shouldReturn200WithRemoveBrandResponse_when_serviceReturnsBrand` — `@WebMvcTest(controllers = BrandController.class)` + `@Import(GlobalExceptionHandler.class)` + `@MockitoBean BrandService`; stub `deleteById(id)` to return a `Brand`, `DELETE /api/brands/{id}`, assert `200`, the `DataResponse` envelope, and every `RemoveBrandResponse` field (`id`, `name`, `description`, `stateLicense`, `brandType`, `adminId`) mapped from the brand. Refs: spec Scenario "Successful soft delete"; design D1, D8, D9. Verify: fails to compile because `RemoveBrandResponse` / `BrandApi.delete` / `BrandController.delete` do not exist (missing type, not a typo).
- [x] 3.2 RED: add `delete_shouldDelegateOnce_when_called` — verify `brandService.deleteById(id)` is called exactly once with the path id and the controller adds no business logic. Refs: design D8 (thin controller). Verify: fails because the delete route/handler is absent.
- [x] 3.3 RED: add `delete_shouldReturn404_when_serviceThrowsNotFound` and `delete_shouldReturn409_when_serviceThrowsConflict` — assert the standard `ErrorResponse` envelope with `$.errors[0].field == "general"` and the exact 404/409 messages from design D3, confirming the controller lets `NotFoundException` / `ConflictException` propagate to the existing `GlobalExceptionHandler` (no new handler). Refs: spec Scenarios 404/409; design D3, D8. Verify: fails because the route/handler is absent.
- [x] 3.4 Confirm all Section 3 tests are RED for the right reason (missing DTO/route/handler), then leave them failing until the GREEN tasks in Sections 5 and 8. Refs: `test-driven-development` skill. Verify: `mvn test -Dtest=BrandControllerTests` reports failures caused by missing types, with no compilation typo errors.

## 4. TDD — Failing-First Endpoint Integration Tests (RED)

- [x] 4.1 RED: extend `BrandControllerEndpointsTests` (inherits `EndpointIntegrationTest`, real PostgreSQL) with a `@BeforeEach` seeding one `brand_types` + one live `Brand` and an `@AfterEach` that removes exactly those rows; confirm the class runs twice consecutively with identical results (order-independent, no residue). Refs: design D9; `docs/backend-standards.md` Integration Testing. Verify: seeding/cleanup leaves `brands` / `brand_types` / `products` at the Section 1.2 baseline after each run.
- [x] 4.2 RED: add `should_return200AndSoftDelete_when_brandIsLiveAndUnreferenced` — `DELETE /api/brands/{id}` returns `200` + `DataResponse<RemoveBrandResponse>`, a native `SELECT count(*) FROM brands WHERE id = ?` still returns `1` (row not physically removed) with `deleted_at IS NOT NULL`, and subsequent `GET /api/brands` / `GET /api/brands/{id}` exclude the brand. Refs: spec Scenario "Successful soft delete"; design D6, D9. Verify: fails with `405` / `404` because no DELETE route exists yet (missing behaviour, not a typo).
- [x] 4.3 RED: add `should_return404_when_idDoesNotExist` and `should_return404_when_brandIsAlreadySoftDeleted` — assert `404` with the exact `Brand not found with ID: {id}` message, `deleted_at` unchanged, and (for the already-deleted case) that a still-referencing live product does NOT turn the answer into `409`. Refs: spec Scenarios "Delete brand with non-existent id" / "Delete an already soft-deleted brand"; design D2, D3. Verify: fails because the route is absent.
- [x] 4.4 RED: add `should_return409AndNotSoftDelete_when_liveProductReferencesBrand` — seed one non-deleted `products` row with `brand_id = {id}`, `DELETE` returns `409` with the exact `Brand with ID {id} cannot be deleted because it is used by products` message, and `brands.deleted_at` is still `NULL`. Refs: spec Scenario "Delete a brand still referenced by a non-deleted product"; design D3, D4, D5. Verify: fails because the guard/route is absent.
- [x] 4.5 RED: add `should_return200_when_onlySoftDeletedProductsReferenceBrand` — seed a product, soft-delete it via native SQL (`@SQLRestriction` blocks entity reads), then `DELETE` the brand returns `200` and soft-deletes it (proves the native count guard's `AND p.deleted_at IS NULL` clause). Refs: spec Scenario "Delete a brand referenced only by soft-deleted products"; design D4, D9. Verify: fails because the guard/route is absent.
- [x] 4.6 RED: add `should_return400_when_idIsNonNumeric` — `DELETE /api/brands/not-a-number` returns `400` with the existing type-mismatch handler output (`field == "id"`, `Invalid value for parameter: id`), no brand lookup runs, and no brand is soft-deleted. Refs: spec Scenario "Delete brand with a non-numeric identifier"; design D3, D8. Verify: fails because the route is absent (currently `404` / `405`).
- [x] 4.7 RED: add `should_leaveCatalogUnchanged_when_deleteIsRejected` — for each rejection (`400`, `404`, `409`) assert `SELECT count(*) FROM brands` and the target `deleted_at` are identical before and after (atomicity / no-side-effect). Refs: spec Scenario "A rejected delete leaves the catalog unchanged"; design D2, D9. Verify: fails because the route is absent.
- [x] 4.8 Confirm all Section 4 tests are RED for the right reason (no DELETE route / no guard), then leave them failing until the GREEN tasks in Sections 5–9. Refs: `test-driven-development` skill. Verify: `mvn verify -Dit.test=BrandControllerEndpointsTests` reports failures caused by missing behaviour, with no compilation typo errors.

## 5. Implementation — RemoveBrandResponse DTO (GREEN)

- [x] 5.1 GREEN: create `presentation/api/model/RemoveBrandResponse.java` as a `record` with fields `Long id, String name, String description, String stateLicense, String brandType, Integer adminId`, mirroring `RemoveDispensaryResponse` (identity + core business fields + parent lookup-state field + `adminId`), with the GNU GPL header + copyright line. Refs: design D1; `RemoveDispensaryResponse.java`. Verify: `mvn compile` succeeds, the record exposes exactly those six components, and the Section 3.1 test now compiles past the missing-DTO error.

## 6. Implementation — BrandRepository Native Count Guard (GREEN)

- [x] 6.1 GREEN: add to `domain/repositories/BrandRepository.java` the native count query `@Query(value = "SELECT COUNT(*) FROM products p WHERE p.brand_id = :brandId AND p.deleted_at IS NULL", nativeQuery = true) long countLiveProductsByBrandId(@Param("brandId") Long brandId);` — counts only live (non-soft-deleted) products, loads no `Product` entity, adds no inverse `products` collection to `Brand`. Refs: spec Scenario "Delete a brand referenced only by soft-deleted products"; design D4, D7; `docs/backend-standards.md:423` (native query anti-corruption seam). Verify: `mvn compile` succeeds and the Section 4.5 integration test proves a soft-deleted product does not increment the count.
- [x] 6.2 Confirm the guard scope is `products` only — do NOT add guards for `brand_images`, `brand_featured_products`, or `fav_user_brands`. Refs: design D7 (liveness-deadlock rationale), spec alignment. Verify: `grep` over `BrandRepository.java` shows exactly one new count method referencing `products` and no other table; no `ProductRepository` is injected into the brand flow.

## 7. Implementation — BrandService.deleteById (GREEN)

- [x] 7.1 GREEN: declare `Brand deleteById(Long id)` on `application/services/BrandService.java` and implement it in `application/services/impl/BrandServiceImpl.java` annotated `@Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)` (on the impl method only), in the fixed order: (1) **resolve** `brandRepository.findById(id)` → empty → `NotFoundException`; (2) **guard** `countLiveProductsByBrandId(id) > 0` → `ConflictException`; (3) **mark** `brand.setDeletedAt(LocalDateTime.now())` then `brandRepository.save(brand)`; return the managed brand. Refs: spec requirement + all scenarios; design D2, D6. Verify: Section 2.1–2.5 service unit tests pass and `grep -n "@Transactional"` shows exactly one annotation on `deleteById`.
- [x] 7.2 GREEN: use explicit `setDeletedAt` + `save()` (NOT `repository.delete()` / `@SQLDelete`), so the controller can map `RemoveBrandResponse` from the returned managed entity; rely on `@SQLRestriction("deleted_at IS NULL")` for the already-deleted → 404 behaviour (no manual `deletedAt` filter). Refs: design D2, D6. Verify: Section 2.5 (already-deleted → 404, guard never called) and Section 4.3 pass; no `delete(` call appears in `BrandServiceImpl.deleteById`.
- [x] 7.3 Confirm atomicity structurally: the only mutation (`save`) is the last statement and every rejection throws before it, so a rejected delete leaves the catalog unchanged. Refs: spec Scenario "A rejected delete leaves the catalog unchanged"; design D2. Verify: Section 2.3 (`never().save`) and Section 4.7 pass.

## 8. Implementation — BrandApi DELETE Mapping + BrandController (GREEN)

- [x] 8.1 GREEN: add to `presentation/api/BrandApi.java` `@DeleteMapping("/{id}")` + `@ResponseStatus(HttpStatus.OK)` + `DataResponse<RemoveBrandResponse> delete(@PathVariable Long id)` with `@Operation` and `@ApiResponses` declaring `200`, `404` **and `409`** (do not copy `DispensaryApi.delete`'s `204` drift). Refs: design D8; `docs/backend-standards.md` soft-delete convention. Verify: `mvn compile` succeeds and `curl -s localhost:8080/v3/api-docs` (or a Swagger assertion) shows `DELETE /api/brands/{id}` documenting `200` / `404` / `409`.
- [x] 8.2 GREEN: implement `BrandController.delete` as a thin handler — delegate to `brandService.deleteById(id)`, map the returned `Brand` to `RemoveBrandResponse` (`brandType` via `brand.getBrandType().getName()`, resolved post-commit under `spring.jpa.open-in-view: true`), wrap in `DataResponse`, add no business logic. Refs: design D1, D8, Risk 11. Verify: Section 3.1–3.3 controller unit tests pass and `RemoveBrandResponse` fields map from the entity, not from the request.

## 9. Implementation — Exact 404/409 Messages and 409-not-400 Decision (GREEN)

- [x] 9.1 GREEN: lock the exact error strings — `NotFoundException("Brand not found with ID: " + id)` for missing/already-deleted (field `general`, `404`) and `ConflictException("Brand with ID " + id + " cannot be deleted because it is used by products")` for brand-in-use (field `general`, `409`); the `400` non-numeric-id case uses the existing `MethodArgumentTypeMismatchException` handler output (`field "id"`, `Invalid value for parameter: id`) with NO new handler code. Refs: design D3; `docs/backend-standards.md` Error Handling. Verify: Section 2.2/2.3, 3.3, 4.3/4.4/4.6 assert the exact strings and `grep` confirms no `ex.getMessage()` is used to build these messages.
- [x] 9.2 GREEN: enforce the **409-not-400 decision** — the brand-in-use guard throws `ConflictException` (mapped by the existing `handleConflict` to `409`), never a `400` / `BadRequest` variant; the source ticket's "bad request" wording is treated as untrusted and superseded by `docs/backend-standards.md:423` and the spec traceability note. Refs: spec requirement status-code decision; design D5; `docs/backend-standards.md:337-359, 423`. Verify: Section 4.4 asserts `409` (not `400`) with `field "general"`; `grep` over `BrandServiceImpl` / `BrandController` shows no `400` / `BadRequest` / `ResponseStatus(BAD_REQUEST)` for the in-use path; the OpenAPI declares `409`.
- [x] 9.3 Run the full new-test set (Sections 2–4) green together and confirm no regression in the existing brand suites. Refs: design D9. Verify: `mvn test -Dtest='BrandServiceTests,BrandControllerTests'` and `mvn verify -Dit.test=BrandControllerEndpointsTests` both report zero failures/errors.

## 10. Review and Update Existing Unit Tests (MANDATORY — Step N)

- [x] 10.1 Review existing brand-related tests (`BrandServiceTests`, `BrandControllerTests`, `BrandRepositoryTests`, `BrandControllerEndpointsTests`, `BrandImageServiceTests`, `DispensaryServiceTests`, `GlobalExceptionHandlerTest`) for impact from the new `BrandApi.delete` route, `BrandService.deleteById`, `RemoveBrandResponse`, and the `BrandRepository` native count method; update any stale expectation and state the reason. Refs: schema §2 Step N; `docs/backend-standards.md` "Update tests when modifying existing functionality". Verify: each named suite runs green, any edited test has a documented reason, and `git status` shows only intended test files changed.
- [x] 10.2 Confirm shared test bases (`ServiceTest`, `RepositoryTest`, `EndpointIntegrationTest`) need no modification and that no existing test asserted a `405` / `404` for `DELETE /api/brands/{id}` that is now stale. Refs: design D9. Verify: `git status --porcelain` shows no change to those base files and full `mvn test` reports zero regressions attributable to this change.

## 11. Run Unit Tests and Verify Database State (MANDATORY — Step N+1, AGENT MUST EXECUTE)

The agent executes every command in this section itself; never delegate to the user.

- [x] 11.1 Prepare the environment and capture the pre-test database baseline: ensure Postgres is up (`make init` / `docker compose ps`), record row counts for `brands`, `products`, `brand_types` and the `flyway_schema_history` version in the `tests` database, and document the exact test commands to be run (store the baseline in `tmp/`, gitignored). Refs: schema §3 Step N+1. Verify: baseline snapshot exists in `tmp/` and `mvn flyway:info` reports `V0.1.0` applied.
- [x] 11.2 Run targeted tests first: `mvn test -Dtest='BrandServiceTests,BrandControllerTests'` and the endpoint integration class, capturing passed/failed/skipped. Refs: schema §3 Step N+1. Verify: command output summary recorded; zero failures in the targeted scope.
- [x] 11.3 Run the broader required suite (`mvn test`, plus `mvn verify` for the failsafe integration tests) and record total counts, failures, runtime and any flaky behaviour. Refs: schema §3 Step N+1; `docs/backend-standards.md` (90% coverage gate). Verify: totals recorded; zero failures/errors; coverage gate satisfied.
- [x] 11.4 Re-check every indicator captured in 11.1, confirm no unintended mutation remains; if any row survived, restore it and document the restoration action explicitly. Refs: schema §3 Step N+1. Verify: post-test counts match the baseline (or restoration is documented) and `flyway_schema_history` is unchanged.
- [x] 11.5 Create the verification report at `specs/<change-name>/reports/YYYY-MM-DD-step-N+1-unit-test-and-db-verification.md` (schema §3 slot; resolves to the change folder's `reports/` directory `openspec/changes/KAN-7-delete-brand-endpoint/reports/`, matching the KAN-8/KAN-6 precedent; replace `YYYY-MM-DD` with the execution date) following the schema's report template: commands executed, unit-test results, pre/post database comparison, state-restored flag, restoration actions, outcome. Refs: schema §3 Step N+1 report template. Verify: report file exists at the stated path with all template sections filled.
- [x] 11.6 Mark this step complete **only** after the suite passes (or a documented, approved exception exists), the database state is verified/restored, and the report file exists. Refs: schema §3 Step N+1. Verify: all three conditions satisfied before checking this box.

## 12. Manual Endpoint Testing with curl (MANDATORY — Step N+2, AGENT MUST EXECUTE)

The agent executes every curl command in this section itself; never ask the user to run curl. All DELETE operations must restore the database to its pre-test state. (The schema's filename pattern for this report reuses `step-N+1` literally — follow it exactly.)

- [x] 12.1 Ensure infrastructure is up and start the backend (`mvn spring-boot:run`); verify `curl -s localhost:8080/actuator/health` reports `UP`, and record the pre-test row counts of `brands`, `products`, `brand_types` for the database the running app uses. Refs: schema §3 Step N+2. Verify: health is `UP`; baseline recorded in `tmp/`.
- [x] 12.2 Seed the minimum data with SQL (one `brand_types` + one live `Brand`, and a `products` row where needed), recording exact inserted ids so 12.9 can remove precisely these rows. Refs: schema §3 Step N+2. Verify: seeded ids recorded; `GET /api/brands/{id}` returns `200` for the seeded brand.
- [x] 12.3 Test the happy path: `curl -i -X DELETE localhost:8080/api/brands/{id}` — verify `200`, the `DataResponse<RemoveBrandResponse>` envelope, `deleted_at` set and the row still physically present; then **restore** the brand (clear `deleted_at` via SQL) to its original state. Refs: spec Scenario "Successful soft delete"; schema §3 Step N+2. Verify: `200` + envelope captured; DB restored to baseline.
- [x] 12.4 Test `404` for a non-existent id and for an already-soft-deleted brand: `curl -i -X DELETE localhost:8080/api/brands/999999` and a second DELETE on the soft-deleted id — verify `404` with `Brand not found with ID: {id}` and no further change. Refs: spec Scenarios 404; schema §3 Step N+2. Verify: both `404` responses captured; no brand mutated.
- [x] 12.5 Test `409` brand-in-use: seed a live `products` row referencing the brand, `curl -i -X DELETE ...` — verify `409` with `Brand with ID {id} cannot be deleted because it is used by products` and `deleted_at` still `NULL`; then remove the seeded product. Refs: spec Scenario 409; design D5; schema §3 Step N+2. Verify: `409` (not `400`) captured; brand not soft-deleted; product removed.
- [x] 12.6 Test the soft-deleted-product-only case: seed a product, soft-delete it via native SQL, `curl -i -X DELETE ...` — verify `200` and the brand is soft-deleted; then restore the brand and remove the product. Refs: spec Scenario "Delete a brand referenced only by soft-deleted products"; design D4. Verify: `200` captured; DB restored.
- [x] 12.7 Test `400` non-numeric id: `curl -i -X DELETE localhost:8080/api/brands/not-a-number` — verify `400` with `field "id"` / `Invalid value for parameter: id` and no brand lookup/soft-delete. Refs: spec Scenario "Delete brand with a non-numeric identifier"; schema §3 Step N+2 error cases. Verify: `400` captured; no brand mutated.
- [x] 12.8 Verify atomicity/no-side-effect across all rejections (`400` / `404` / `409`): `SELECT count(*) FROM brands` and the target `deleted_at` are identical before and after each rejected DELETE. Refs: spec Scenario "A rejected delete leaves the catalog unchanged". Verify: pre/post counts identical for every rejection path.
- [x] 12.9 Restore the database to its pre-test state (remove all seeded rows, clear any `deleted_at`), stop the backend, and confirm `brands` / `products` / `brand_types` counts and `flyway_schema_history` match the 12.1 baseline. Refs: schema §3 Step N+2 (restore DB state). Verify: post-test table counts equal baseline; backend stopped.
- [x] 12.10 Create the manual endpoint testing report at `specs/<change-name>/reports/YYYY-MM-DD-step-N+1-manual-curl-verification.md` (resolves to `openspec/changes/KAN-7-delete-brand-endpoint/reports/`; replace `YYYY-MM-DD` with the execution date) following the schema's report template: every curl command, response (status + body), DB-after state, restoration action, and the final pre/post table comparison. Refs: schema §3 Step N+2 report template. Verify: report file exists at the stated path with all commands, responses and cleanup documented.
- [x] 12.11 Mark this step complete **only** after all curl tests pass, the database state is restored, and the report file exists. Refs: schema §3 Step N+2. Verify: all three conditions satisfied before checking this box.

## 13. Update Technical Documentation (MANDATORY — Step N+3)

- [x] 13.1 Review `docs/data-model.md`, `docs/backend-standards.md` and the change specs for consistency with the shipped `DELETE /api/brands/{brandId}` behaviour: the soft-delete write path, the `products.brand_id AND deleted_at IS NULL` native guard, the `409`-not-`400` decision, the exact 404/409 messages, and the `RemoveBrandResponse` payload. The proposal states these conventions are already documented, so update only stale references (e.g. add the new route to any API/route inventory) and record a "no rewrite required" justification where nothing changed. Refs: proposal.md Impact (Documentation); design D1–D8, Risk 10/11; `docs/backend-standards.md:423-424`. Verify: docs diff reviewed; any stale reference corrected; English-only confirmed; no contradiction with the spec.
- [x] 13.2 Confirm the change's `specs/brands-management/spec.md` scenarios all trace to the implemented tests (Sections 2–4) and that no spec drift remains. Refs: schema §2; design D9. Verify: a scenario-to-test traceability note is recorded (in `tmp/` or the report) and every one of the 7 scenarios maps to at least one test.

## 14. Verification Checklist (MANDATORY — schema §4, complete before finalizing)

- [x] 14.1 Step 0 (Create Feature Branch) is the FIRST step. Verify: Section 0 precedes all code-change sections.
- [x] 14.2 All mandatory steps from `openspec/config.yaml` are included. Verify: Sections 0, 1, 10, 11, 12, 13 are present.
- [x] 14.3 Steps are numbered sequentially. Verify: `##` headings run 0–14 with no gaps and task ids are `X.Y`.
- [x] 14.4 Mandatory steps are clearly marked with the "(MANDATORY)" label. Verify: Sections 0, 1, 10, 11, 12, 13 headings contain "(MANDATORY)".
- [x] 14.5 Branch naming follows the convention `feat/[name]`. Verify: task 0.1 uses `feat/KAN-7-delete-brand-endpoint`.
- [x] 14.6 Step N+1 includes the report path and naming convention in `specs/<change-name>/reports/`. Verify: task 11.5 names `YYYY-MM-DD-step-N+1-unit-test-and-db-verification.md`.
- [x] 14.7 Manual testing steps explicitly state "AGENT MUST EXECUTE". Verify: Section 12 heading and preamble state it; tasks are agent-executed.
- [x] 14.8 Tasks include database state restoration steps. Verify: tasks 11.4 and 12.3–12.9 include restoration/cleanup.
