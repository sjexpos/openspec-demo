# KAN-6 Brands CRUD — Implementation Tasks

Backend-only change. Every implementation group follows strict TDD (red → green): write the failing test first, then implement the production code that satisfies it. All new files carry the GNU GPL license header + `// Copyright (c) 2026-2027 Sergio Exposito.` line. Coverage gate: 90% for branches, functions, lines, and statements (via `mvn test`).

## 0. Setup: Create Feature Branch (MANDATORY - FIRST STEP)
- [x] 0.1 Create and switch to feature branch `feature/KAN-6-brands-crud-backend` from main/master (backend standards: `feature/[ticket-id]-backend`)

- [x] 0.2 Verify `git branch --show-current` returns `feature/KAN-6-brands-crud-backend` and the working tree is clean before coding

## 1. Domain Layer: Brand entities (order: entities first)

- [x] 1.1 Create `src/main/java/com/example/demo/domain/models/brand/BrandType.java` — `@Entity @Table(name = "brand_types")`, `Long id` (IDENTITY, `@EqualsAndHashCode.Include`), `String name` (`@Column(nullable = false)`), mirroring `LicenseStatus`. GPL header + copyright.
- [x] 1.2 Create `src/main/java/com/example/demo/domain/models/brand/Brand.java` — `@Entity @Table(name = "brands")`, `extends BaseEntity`, `Long id` (IDENTITY, `@EqualsAndHashCode.Include`), fields per design (`name`, `description`, `email`, `stateLicense`, `brandType` `@ManyToOne(LAZY) @JoinColumn(name = "brand_type_id", nullable = false)` (D3), `logoImageUrl`, nullable social/website URLs, `Integer adminId`, `Boolean enabled`). GPL header + copyright.
- [x] 1.3 Verify `mvn compile` succeeds with the two new entities declared (no DDL — `ddl-auto: none`).

## 2. Domain Layer: Repositories (TDD — repository integration tests first)

- [x] 2.1 Write failing repository integration tests in `src/test/java/com/example/demo/integration/repositories/BrandRepositoryTests.java` (RED, `@DataJpaTest` + `TestEntityManager`) covering: `findAllByDeletedAtIsNull()` excludes rows with `deletedAt` set / includes live rows; `findByIdAndDeletedAtIsNull(id)` returns live / empty for soft-deleted; `BrandTypeRepository.findByName(name)` returns match / empty for unknown; persistence-integrity assertion that a soft-deleted `Brand` row remains physically present in `brands`.
- [x] 2.2 Implement `src/main/java/com/example/demo/domain/repositories/BrandTypeRepository.java` — `JpaRepository<BrandType, Long>` + `Optional<BrandType> findByName(String name)` (GREEN). GPL header.
- [x] 2.3 Implement `src/main/java/com/example/demo/domain/repositories/BrandRepository.java` — `JpaRepository<Brand, Long>` + `Iterable<Brand> findAllByDeletedAtIsNull()` + `Optional<Brand> findByIdAndDeletedAtIsNull(Long id)` (GREEN). GPL header.
- [x] 2.4 Run `BrandRepositoryTests` until green.

## 3. Application Layer: Service interface + implementation (TDD — service unit tests first)

- [x] 3.1 Create `src/main/java/com/example/demo/application/services/BrandService.java` interface with `findAll()`, `create(...)`, `getById(Long id)`, `update(...)` signatures per design (positional params, `Optional<Brand> getById`).
- [x] 3.2 Create test base `src/test/java/com/example/demo/application/services/BrandServiceTest.java` mirroring `ServiceTest` — `@ExtendWith(SpringExtension.class)` + `@MockitoBean BrandRepository brandRepository` + `@MockitoBean BrandTypeRepository brandTypeRepository`.
- [x] 3.3 Write failing service unit tests in `src/test/java/com/example/demo/application/services/BrandServiceTests.java` (`@TestConfiguration @ComponentScan`, `@Autowired BrandService`) (RED) covering all 11 cases: `findAll` non-deleted only; `create` with resolvable brand type saves with resolved `BrandType`; `create` unknown type throws `NotFoundException` and never calls `save`; `getById` returns brand for live id; `getById` empty for soft-deleted/non-existent; `update` partial applies only provided fields and saves once; `update` non-existent id throws `NotFoundException` (no save); `update` soft-deleted throws `NotFoundException`; `update` unknown `brandTypeName` throws `NotFoundException` (no save); `update` omitting `enabled` leaves value unchanged (D4); `update` with `enabled: false` applies `false`.
- [x] 3.4 Implement `src/main/java/com/example/demo/application/services/impl/BrandServiceImpl.java` — `@Service @Slf4j`, constructor injection of both repositories, `@Transactional(rollbackFor = Exception.class)` on `create`/`update`, `findByState`-style lookup resolution via `BrandTypeRepository.findByName(...).orElseThrow(NotFoundException)` (D5), updates via non-null-only setters (D4), soft-delete 404 via `findByIdAndDeletedAtIsNull` (D6) (GREEN). GPL header.
- [x] 3.5 Run `BrandServiceTests` to green.

## 4. Presentation Layer: Request/Response DTOs

- [x] 4.1 Create `src/main/java/com/example/demo/presentation/api/model/CreateBrandRequest.java` — `@Data @Builder @AllArgsConstructor @NoArgsConstructor` + `@Schema`, validations per DTO table: `@NotEmpty` on `name`, `description`, `email`, `stateLicense`, `brandTypeName`, `logoImageUrl`; `@Email` on `email`; `@NotNull` on `adminId`; nullable `instagramUrl`/`twitterUrl`/`facebookUrl`/`websiteUrl`/`enabled`. GPL header.
- [x] 4.2 Create `src/main/java/com/example/demo/presentation/api/model/UpdateBrandRequest.java` — `@Data` class, all fields nullable, `@Email` on `email` only (D4), `Boolean enabled` (nullable). GPL header.
- [x] 4.3 Create `src/main/java/com/example/demo/presentation/api/model/CreateBrandResponse.java` — immutable `record` with all response fields (id, name, description, email, stateLicense, brandTypeName, logoImageUrl, instagram/twitter/facebook/website URL, adminId, enabled). GPL header.
- [x] 4.4 Create `src/main/java/com/example/demo/presentation/api/model/GetBrandResponse.java` — immutable `record`, identical to `CreateBrandResponse`. GPL header.
- [x] 4.5 Create `src/main/java/com/example/demo/presentation/api/model/GetAllBrandsResponse.java` — compact `record` (`id, name, description, email, stateLicense, brandTypeName, logoImageUrl, enabled`) omitting social URLs and `adminId`. GPL header.

## 5. Presentation Layer: API interface + Controller (TDD — controller unit tests first)

- [x] 5.1 Define `src/main/java/com/example/demo/presentation/api/BrandApi.java` — `@RequestMapping("/api/brands") @Tag(name = "Brands") @Validated`, methods `getAll()` (GET → 200 `DataResponse<List<GetAllBrandsResponse>>`), `create(@Valid CreateBrandRequest)` (POST → 201 `DataResponse<CreateBrandResponse>`), `getById(@PathVariable Long id)` (GET → 200 `DataResponse<GetBrandResponse>`), `update(@PathVariable Long id, @Valid UpdateBrandRequest)` (PATCH → 200 `DataResponse<GetBrandResponse>`), each with `@ResponseStatus`, `@Operation`, `@ApiResponses` per `DispensaryApi`. Uses `@PathVariable Long id` (D1/D2). GPL header.
- [x] 5.2 Write failing controller unit tests in `src/test/java/com/example/demo/presentation/controllers/BrandControllerTests.java` (standalone `MockMvc` + `BrandController(new mockBrandService)` + `setControllerAdvice(new GlobalExceptionHandler())`) (RED): GET all → 200 DataResponse list; POST valid → 201 wrapped; POST missing `name` → 400 `FieldError("name")`; POST invalid email → 400 field error; GET by id → 200; GET empty service → 404; PATCH valid → 200; PATCH service `NotFoundException` → 404; PATCH invalid email → 400.
- [x] 5.3 Implement `src/main/java/com/example/demo/presentation/controllers/BrandController.java` — `@RestController` implements `BrandApi`, constructor-injects `BrandService`, maps entities → response records and wraps in `DataResponse` (`brandTypeName` from `brand.getBrandType().getName()`), `getById` throws `NotFoundException` when service returns empty, `update` passes request getters positionally (null when omitted) (GREEN). GPL header.
- [x] 5.4 Run `BrandControllerTests` to green.

## 6. Endpoint integration tests (TDD — full-context MockMvc)

- [x] 6.1 Create `src/test/java/com/example/demo/integration/endpoints/BrandControllerEndpointsTests.java` extending `EndpointIntegrationTest` (`@SpringBootTest(MOCK)` + `@AutoConfigureMockMvc`); `@Autowired MockMvc` + `ObjectMapper`; seed a `BrandType` fixture via `BrandTypeRepository.save(...)` in `@BeforeEach` (migration ships no `brand_types` seed) and clean up after.
- [x] 6.2 Write failing endpoint tests (map 1:1 to spec scenarios) (RED): POST valid → 201 + echo; POST missing/blank required field → 400 field error; POST invalid email → 400; GET all → 200 excluding a soft-deleted brand; GET live id → 200; GET non-existent id → 404; GET soft-deleted id → 404; PATCH partial → 200 only provided fields changed; PATCH non-existent → 404; PATCH soft-deleted → 404; PATCH invalid `brandTypeName` → 404 + brand unchanged; POST unknown `brandTypeName` → 404 + no brand row created.
- [x] 6.3 Run `BrandControllerEndpointsTests` to green (verifies full wiring of entities → service → controller → error handler).

## 7. Review and Update Existing Unit Tests (MANDATORY)

- [x] 7.1 Review existing Dispensary tests and shared fixtures for regression impact from the new domain (no existing behavior changes expected per design).
- [x] 7.2 Run the existing suite once to confirm no regressions introduced by the new entities/repositories scan.

## 8. Run Unit Tests and Verify Database State (MANDATORY - AGENT MUST EXECUTE)

- [x] 8.1 Capture pre-test database baseline for `brands` and `brand_types` (row counts / key records) in the `tests` PostgreSQL database.
- [x] 8.2 Run targeted unit tests for the brand modules (`mvn test -Dtest=Brand*,...`) and confirm they pass.
- [x] 8.3 Run the broader required suite (`mvn test`) and record totals (passed/failed/skipped + runtime + notes on flaky tests).
- [x] 8.4 Re-verify post-test database state; confirm no unintended mutations remain, restore if needed and document restoration.
- [x] 8.5 Create verification report `openspec/changes/KAN-6-brands-crud/reports/YYYY-MM-DD-step-N+1-unit-test-and-db-verification.md` following the required template (commands, results, pre/post DB comparison, cleanup).
- [x] 8.6 Mark this step completed only after tests pass (or documented approved exceptions) and the report exists.

## 9. Manual Endpoint Testing with curl (MANDATORY - AGENT MUST EXECUTE)

- [x] 9.1 Ensure the backend server is running (`mvn spring-boot:run`), DB connection active, and seed a `brand_types` lookup row needed by POST/PATCH (note pre-test DB state).
- [x] 9.2 Test `GET /api/brands` with curl — verify 200, `DataResponse` list, non-deleted only.
- [x] 9.3 Test `POST /api/brands` with curl (valid body) — verify 201 + created brand; then restore DB state (delete created row).
- [x] 9.4 Test `GET /api/brands/{brandId}` with curl (live id) — verify 200 + brand data.
- [x] 9.5 Test `PATCH /api/brands/{brandId}` with curl (partial body) — verify 200 + only provided fields changed; then restore original row values.
- [x] 9.6 Test error cases with curl: POST missing required field → 400 field error; invalid email → 400; GET/PATCH unknown id → 404; invalid `brandTypeName` on create → 404 and no row created.
- [x] 9.7 Document all curl commands + responses, and verify DB state matches pre-test state (restore cleanup).

## 10. E2E Testing with Playwright MCP (MANDATORY if applicable - AGENT MUST EXECUTE)

- [x] 10.1 Not applicable for this change — backend-only implementation with no frontend, user-interface, or browser-facing workflow. No Playwright E2E required; confirmation recorded here to satisfy the checklist.

## 11. Update Technical Documentation (MANDATORY)

- [x] 11.1 Refresh `docs/data-model.md` mapping notes for `brands` and `brand_types` (JPA entity mapping for Brand/BrandType, soft-delete via `deletedAt`, `brand_type_id` FK association) if the existing notes are incomplete after implementation.
- [x] 11.2 Confirm branch naming, license headers, and English-only artifacts are consistent throughout the change.

## 12. Final Verification (MANDATORY)

- [x] 12.1 Run `mvn compile` and confirm a clean, error-free build (no DDL drift — `ddl-auto: none`).
- [x] 12.2 Run `mvn test` and confirm the full suite passes with the **90% coverage gate** across branches, functions, lines, and statements for the new Brand domain.
- [x] 12.3 Run lint/format checks per backend/project standards — verify no lint/format violations introduced by the new files (license/GPL headers present, naming conventions, formatting consistent with existing Dispensary code).
- [x] 12.4 Verify database state is clean: no unintended migrations added (only `V0.1.0`), no leftover test rows in `brands`/`brand_types`, and the rollback path (revert code only) is intact.

## 13. All Tasks Complete (marker)

- [x] 13.1 All sections `0..12` cleared — every `- [ ]` item in this file is checked `[x]` and the change is ready for `opsx-apply` final run + archive review.