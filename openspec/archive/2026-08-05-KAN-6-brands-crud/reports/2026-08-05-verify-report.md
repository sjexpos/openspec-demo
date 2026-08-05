# KAN-6 Brands CRUD — Verification Report

- **Change:** `KAN-6-brands-crud` — Brands CRUD (backend)
- **Change directory:** `openspec/changes/KAN-6-brands-crud/`
- **Branch:** `feature/KAN-6-brands-crud-backend`
- **Mode:** `verify` (adversarial review pass, independent of implementation session)
- **Date:** 2026-08-05
- **Spec under test:** `openspec/changes/KAN-6-brands-crud/specs/brands-management/spec.md`
- **Commands executed:** `mvn test -Dtest="Brand*"`, `mvn clean verify -Dpitest.skip=true`, `mvn jacoco:report`

---

## 1. Completeness

| Artifact | Present | Verified against code |
|---|---|---|
| `proposal.md` | ✅ | Scope (4 endpoints, no DELETE, soft-delete, no migration) matches |
| `design.md` | ✅ | D1–D6 decisions all reflected in code |
| `specs/brands-management/spec.md` | ✅ | 7 requirements / 17 scenarios — see compliance matrix |
| `tasks.md` | ✅ | All 40 items across sections 0–13 marked `[x]`; implemented |
| `apply-progress.md` | ✅ | Worklog consistent with git state and test results |

New main files (13) and new test files (5) match the design's package/file layout exactly (0 modified production
files, 0 new migrations). One expected test-fixture modification: `ServiceTest` gained two `@MockitoBean` mocks.

## 2. Build / Tests / Coverage evidence

### Targeted Brand tests — `mvn test -Dtest="Brand*"` (all green)

| Test class | Run | Failures | Errors | Skipped |
|---|---|---|---|---|
| `BrandServiceTests` | 12 | 0 | 0 | 0 |
| `BrandControllerTests` | 9 | 0 | 0 | 0 |
| `BrandControllerEndpointsTests` | 14 | 0 | 0 | 0 |
| `BrandRepositoryTests` | 6 | 0 | 0 | 0 |
| **Total** | **41** | **0** | **0** | **0** |

### Full suite — `mvn clean verify -Dpitest.skip=true` → BUILD SUCCESS

| Phase | Run | Failures | Errors | Skipped |
|---|---|---|---|---|
| Surefire (unit) — 9 controller + 3 `GlobalExceptionHandler` + 1 `DispensaryService` + 12 `BrandService` | 25 | 0 | 0 | 0 |
| Failsafe (integration) — 1 `DispensaryRepository` + 6 `BrandRepository` + 14 `BrandControllerEndpoints` + 4 `ActuatorEndpoints` | 25 | 0 | 0 | 0 |
| **Total** | **50** | **0** | **0** | **0** |

SpotBugs + Modernizer run as part of `verify`; clean build passes (the stale-`target/` spotbugs
`EI_EXPOSE_REP` note documented in apply-progress was not observed on the clean build).

### Coverage (independent `mvn jacoco:report` from `target/jacoco.exec`)

| Class | Line | Branch | Method |
|---|---|---|---|
| `BrandServiceImpl` | 100% | 100% | 100% |
| `BrandController` | 100% | 100% | 100% |
| `BrandService`/`BrandApi`/repositories/DTOs (interface/record lines) | 100% | — | 100% |
| `Brand` entity (incl. Lombok accessors/`equals`/`hashCode`/builder) | ~31% | — | — |
| `BrandType` entity (incl. Lombok accessors/`equals`/`hashCode`) | ~20% | — | — |

Entity-class coverage is dragged down by Lombok-generated accessors — the same baseline as the pre-existing
`Dispensary` entity. **No `jacoco` `check` rule exists in `pom.xml`**, so the 90% gate in `tasks.md` §12 is
documentational, not machine-enforced (see W1).

## 3. Spec compliance matrix (7 requirements / 17 scenarios)

| # | Requirement / Scenario | Status | Evidence |
|---|---|---|---|
| R1 | Brand↔`brands`, BrandType↔`brand_types` JPA mapping, extends `BaseEntity` | ✅ | `Brand.java` (`@Table("brands")`, extends `BaseEntity`), `BrandType.java` (`@Table("brand_types")`); columns verified against Flyway `V0.1.0` (incl. `state_license`, `logo_image_url`, nullable social URLs, `admin_id int`, `enabled boolean`, `deleted_at`) |
| R1-S1 | Soft-deleted excluded from list and get-by-id | ✅ | `findAllByDeletedAtIsNull()` / `findByIdAndDeletedAtIsNull(Long)`; endpoint tests `getAll_shouldExcludeSoftDeletedBrand`, `getByIdSoftDeleted_shouldReturn404` |
| R1-S2 | Rows never physically removed | ✅ | `softDeletedBrand_when_deleted_rowStillPresentPhysically` (JPQL count on the row) |
| R2 | `POST /api/brands` → 201 `DataResponse`, all 12 fields, 7 required + `@Email` | ✅ | `BrandApi` (`@ResponseStatus(CREATED)`), `CreateBrandRequest` (`@NotEmpty`×6 + `@NotNull adminId` + `@Email`), controller returns created brand with generated `id` |
| R2-S1 | Valid creation → 201 + created data | ✅ | `postValidBrand_shouldReturn201_andEcho`; `create_shouldReturn201_when_validRequest`; `create_shouldSaveBrand_withResolvedType` |
| R2-S2 | Missing/blank required → 400 field error | ✅ | `postMissingName_shouldReturn400_withFieldError`, `postBlankName_shouldReturn400`; controller test `create_shouldReturn400_when_missingName` (field `name`, service never called) |
| R2-S3 | Invalid email → 400 `email` field error | ✅ | `postInvalidEmail_shouldReturn400`, `create_shouldReturn400_when_invalidEmail` |
| R3 | `GET /api/brands` → 200 `DataResponse` compact list | ✅ | `GetAllBrandsResponse` (8 compact fields, omits social URLs/`adminId`); controller maps all rows |
| R3-S1 | Returns only non-deleted | ✅ | `findAll` → `findAllByDeletedAtIsNull`; `findAll_shouldReturn_nonDeletedBrandsOnly` |
| R3-S2 | Excludes every soft-deleted brand | ✅ | `getAll_shouldExcludeSoftDeletedBrand` (length 1, deleted excluded) |
| R4 | `GET /api/brands/{id}` → 200 `DataResponse`; else 404 | ✅ | Controller `orElseThrow(NotFoundException("Brand not found with ID: " + id))` |
| R4-S1 | Existing id → 200 | ✅ | `getByIdLive_shouldReturn200`, `getById_shouldReturn200_when_liveBrand`, `getById_shouldReturnBrand_when_live` |
| R4-S2 | Non-existent id → 404 standard error | ✅ | `getByIdNonExistent_shouldReturn404` (field `general`), controller test 404 |
| R4-S3 | Soft-deleted id → treated as not found, 404 | ✅ | `getByIdSoftDeleted_shouldReturn404`; `getById_shouldReturnEmpty_when_softDeletedOrNonExistent` |
| R5 | `PATCH /api/brands/{id}` → 200 `DataResponse`; only provided fields change; 404 if missing; validation on provided fields | ✅ | `UpdateBrandRequest` nullable `@Data` class, `@Email` only; service applies setters only when non-null; `@Transactional(rollbackFor = Exception.class)` |
| R5-S1 | Partial update changes only provided fields | ✅ | `update_shouldApplyPartial_andSaveOnce` (name/email/website changed; description + brandType unchanged); `patchPartial_shouldUpdateOnlyProvidedFields` |
| R5-S2 | Non-existent id → 404 | ✅ | `patchNonExistent_shouldReturn404`, `update_shouldThrow_when_nonexistent_andDoNotSave` |
| R5-S3 | Soft-deleted id → 404 | ✅ | `patchSoftDeleted_shouldReturn404`, `update_shouldThrow_when_softDeleted` |
| R5-S4 | Invalid provided value → 400 | ✅ | `patchInvalidEmail_shouldReturn400`, `update_shouldReturn400_when_invalidEmail` |
| R6 | `brandTypeName` resolved via `findByName`; unknown → 404 | ✅ | `BrandTypeRepository.findByName(String)`; service `orElseThrow(NotFoundException("Invalid brand type: " + name))` (D5 precedent) |
| R6-S1 | Resolve during create | ✅ | `create_shouldSaveBrand_withResolvedType` (asserts resolved `BrandType` attached) |
| R6-S2 | Re-associate during update | ✅ | `update_shouldApplyAllFields_when_allProvided` (brandType asserted) |
| R6-S3 | Invalid name during create → 404, MUST NOT create | ✅ | `create_shouldThrow_whenUnknownBrandType_andDoNotSave` (save never called); `postUnknownBrandType_shouldReturn404_andNoRowCreated` (404; no-row guarantee proven at service layer — see S1) |
| R6-S4 | Invalid name during update → 404, MUST NOT change brand | ✅ | `update_shouldThrow_whenUnknownBrandType_andDoNotSave` (save never called); `patchInvalidBrandType_shouldReturn404_andBrandUnchanged` (GET after shows brandTypeName still `grower`) |
| R7 | Errors in `GlobalExceptionHandler`/`ErrorResponse` shape; 400 field errors; 404 with detail | ✅ | No handler changes needed — controller unit tests wire `setControllerAdvice(new GlobalExceptionHandler())`; endpoint tests assert `$.status`, `$.errors[0].field` (`general` for 404) |
| R7-S1 | Validation → 400 listing offending fields | ✅ | 400 tests above assert `field` entries |
| R7-S2 | Not found → 404 standard error + descriptive message | ✅ | `FieldError("general", "Brand not found with ID: {id}")` asserted in controller/endpoint 404 tests |

**Result: 17/17 scenarios satisfied.** No CRITICAL (spec) findings.

## 4. Correctness table

| Concern | Verdict | Evidence |
|---|---|---|
| Soft-delete semantics (never physical, always filtered) | ✅ | Repos + service + endpoint tests |
| Create status 201 / body `DataResponse` | ✅ | `@ResponseStatus(CREATED)`; tests assert 201 + `$.data` |
| List/Get/Patch status 200; 400/404 error paths | ✅ | All status codes asserted in integration tests |
| `@Email` on create **and** PATCH (`null` passes validation) | ✅ | `@Email` on both DTOs; null treated as valid → omitted email OK |
| PATCH non-null-only incl. nullable `Boolean enabled` (`false` applied, omitted unchanged) | ✅ | `update_shouldLeaveEnabled_when_omitted`, `update_shouldApplyFalse_when_enabledFalse` |
| BrandType invalid → 404 (not 400) on create and update; brand unchanged | ✅ | Per D5/D6 + tests |
| Transactional rollback on update failure (brand unchanged) | ✅ | `@Transactional(rollbackFor = Exception.class)`; save never reached on unknown type |
| ID typing uniform `Long` (D1/D2) vs `SERIAL int4` | ✅ | Safe under `ddl-auto: none`; no ClassCast risk |

## 5. Design coherence table

| Design decision | Implemented | Note |
|---|---|---|
| D1/D2 — `Long` id everywhere | ✅ | Entity, repository generics, service, `@PathVariable Long` |
| D3 — `@JoinColumn(name = "brand_type_id", nullable = false)` | ✅ | `Brand.java` |
| D4 — class-based `UpdateBrandRequest`, non-null-only, `Boolean enabled` | ✅ | `UpdateBrandRequest` + `BrandServiceImpl.update` |
| D5 — invalid `brandTypeName` → 404, `"Invalid brand type: " + name` | ✅ | Exact message in service + tests |
| D6 — update on soft-deleted/non-existent → 404, no restore | ✅ | `findByIdAndDeletedAtIsNull`; no restore branch |
| Supporting — response DTO split, compact list DTO, `adminId Integer` | ✅ | Records exactly per design; `Integer adminId` |
| Supporting — `@Slf4j`, constructor injection, `@Transactional(REQUIRED)` on mutating methods | ✅ | `BrandServiceImpl` |
| Deviation — `BrandType` uses public `@AllArgsConstructor @NoArgsConstructor` (vs protected) | ✅ accepted | Documented in apply-progress; required to seed test fixtures |
| Non-goals honored — no DELETE endpoint, no restore, no BrandType endpoints, no seed data, no migration | ✅ | Verified; Flyway history still only `V0.1.0` |

## 6. Scope drift / regression check

- `git diff --stat` (tracked): only `ServiceTest.java` (test base, 2 `@MockitoBean` lines — expected/accepted),
  `docs/data-model.md` (KAN-6 mapping notes, per tasks §11), `.gitignore` (`tmp/` — tooling), and
  `.agents/prompts/opsx-orchestrator.md` (tooling prompt wording). **No production Dispensary/Address/LicenseStatus/
  BaseEntity/GlobalExceptionHandler/DataResponse code changed.**
- Regression suite: `DispensaryServiceTests` (1), `DispensaryRepositoryTests` (1), `ActuatorEndpointsTests` (4),
  `GlobalExceptionHandlerTest` (3) all green.
- Consistency: GPL header + copyright line present in all 18 new files; English-only; no dead code observed.

## 7. Findings

### CRITICAL
None. No spec requirement is unmet and no test fails.

### WARNING
- **W1 — 90% coverage "gate" is not machine-enforced and entity classes fall below it.**
  `pom.xml` declares jacoco `prepare-agent`/`prepare-agent-integration` only — there is **no `jacoco:report` or
  `jacoco:check` execution bound** (report was generated ad-hoc here), so the 90% gate in `tasks.md` §12.2 cannot be
  enforced by the build. Independently measured coverage: `BrandServiceImpl` 100% / `BrandController` 100% (lines,
  branches, methods), but the entity classes `Brand` (~31%) and `BrandType` (~20%) sit well below 90% due to
  Lombok-generated accessors/`equals`/`hashCode` — matching the existing `Dispensary` entity baseline, but meaning
  the aggregate "90%" claim is not literally true for the whole new domain.
  **Fix location:** process/build (`pom.xml` jacoco `check` rule scoped to business-logic packages, or correct the
  documented gate); **or** document entity accessor exclusion as done for Dispensary.

### SUGGESTION
- **S1 — Endpoint test does not assert the "no brand row created" half of R6-S3.**
  `postUnknownBrandType_shouldReturn404_andNoRowCreated` verifies the 404 but never queries `brandRepository` to
  prove no row was created. The guarantee *is* proven at the service layer (`create_shouldThrow_whenUnknownBrandType_andDoNotSave`:
  `save` never called), so this is not a correctness gap — just an over-stated test name at the endpoint level.
  **Fix location:** tests — add a row-count/`findAll` assertion in the endpoint test.
- **S2 — PATCH cannot clear a field with explicit `null`.** Per D4, `null` always means "leave unchanged", so a
  client can never null-out e.g. `instagramUrl`. This satisfies the current spec (only *provided* fields change) but
  is an undocumented API behavior worth a Swagger note or a future null-marker convention.
  **Fix location:** documentation (API docs) or future change.
- **S3 — N+1 + `open-in-view` reliance.** `getAll()` triggers one lazy `brand_types` SELECT per row and response
  mapping depends on `spring.jpa.open-in-view: true` (mirrors `Dispensary`). Acceptable now; add
  `@EntityGraph`/fetch join only if profiling flags a hotspot.
  **Fix location:** code (deferred), documented in design Risks.
- **S4 — No list ordering guarantee.** `findAllByDeletedAtIsNull()` has no `OrderBy`; row order is unspecified.
  Spec does not mandate ordering, so this is purely an API-docs nicety.

## 8. Verdict

**PASS WITH WARNINGS** — 17/17 spec scenarios satisfied, 50/50 tests green (`BUILD SUCCESS`), no CRITICAL findings,
no production scope drift. The two warnings (W1 coverage-gate enforceability, and the residual test-level gap S1)
are non-blocking for archive; W1 and S1 should be tracked as follow-ups.

Archiving is **advisable** in the current state, with W1/S1–S4 recorded as follow-up items rather than blockers.
