# KAN-6 Brands CRUD — Apply Progress

Backend-only change. Strict TDD (red → green) per section. All new files carry the GNU GPL license header +
`// Copyright (c) 2026-2027 Sergio Exposito.` line. Dependencies: PostgreSQL running via docker compose (both `demo`
and `tests` DBs), Maven 3.9.3, Java 21 (GraalVM).

## Worklog

### 0. Setup
- Branch `feature/KAN-6-brands-crud-backend` already created; `git branch --show-current` confirms it.
- Working tree clean for source; only in-flight OpenSpec artifacts and tooling tweaks (`opsx-orchestrator.md`,
  `.gitignore`) show as modified — not part of this change's code.
- DONE: 0.1, 0.2.

### 1. Domain Layer — entities
- `BrandType.java` (table `brand_types`) and `Brand.java` (table `brands`, extends `BaseEntity`) created, `Long`
  ids (D1/D2), `@JoinColumn(name = "brand_type_id", nullable = false)` (D3), GPL headers.
- `mvn compile` green after `mvn clean` (see builder note below).
- DONE: 1.1, 1.2, 1.3.

### 2. Domain Layer — repositories (TDD)
- Wrote `BrandRepositoryTests.java` first (RED), then created `BrandTypeRepository` (`findByName`) and
  `BrandRepository` (`findAllByDeletedAtIsNull`, `findByIdAndDeletedAtIsNull`). Tests green (6).
- DONE: 2.1, 2.2, 2.3, 2.4.

### 3. Application Layer — service (TDD)
- `BrandService` interface + `BrandServiceTest` base + `BrandServiceTests` (11 cases → later 12) written first
  (RED), then `BrandServiceImpl` (GREEN). `@Transactional(rollbackFor = Exception.class)` on create/update,
  `findByName(...).orElseThrow(NotFoundException)` (D5), soft-delete 404 via `findByIdAndDeletedAtIsNull` (D6),
  non-null-only setters incl. nullable `Boolean enabled` (D4).
- Tests green: 12.
- DONE: 3.1, 3.2, 3.3, 3.4, 3.5.

### 4. Presentation Layer — DTOs
- `CreateBrandRequest`, `UpdateBrandRequest`, `CreateBrandResponse`, `GetBrandResponse`, `GetAllBrandsResponse`
  created per design (request = `@Data @Builder`, update = nullable class, responses = records).
- DONE: 4.1, 4.2, 4.3, 4.4, 4.5.

### 5. Presentation Layer — API + controller (TDD)
- `BrandApi` interface; `BrandControllerTests` (9) written first (RED) with standalone `MockMvc` +
  `GlobalExceptionHandler`; `BrandController` GREEN. Tests green: 9.
- DONE: 5.1, 5.2, 5.3, 5.4.

### 6. Endpoint integration tests (TDD)
- `BrandControllerEndpointsTests` extending `EndpointIntegrationTest`, seeds a `BrandType` fixture in
  `@BeforeEach`, cleans up in `@AfterEach`. 14 tests map 1:1 to spec scenarios. Green.
- DONE: 6.1, 6.2, 6.3.

### 7. Regression review
- Only regression discovered: `DispensaryServiceTests` uses a non-lazy `@ComponentScan` that now discovers
  `BrandServiceImpl`. Fixed by adding `@MockitoBean BrandRepository`/`BrandTypeRepository` to the shared
  `ServiceTest` base (test-fixture-only change; no production Dispensary code touched).
- Existing suite re-run green.
- DONE: 7.1, 7.2.

### 8. Unit tests + DB verification
- Baseline (`tests` DB): `brands`=0, `brand_types`=0, flyway `0.1.0` applied. Post-test: `brands`=0,
  `brand_types`=0 → no unintended mutations.
- `mvn clean verify -Dpitest.skip=true` → BUILD SUCCESS (26 unit + 25 integration tests).
- Report: `reports/2026-08-05-step-N+1-unit-test-and-db-verification.md` (see below).
- DONE: 8.1, 8.2, 8.3, 8.4, 8.5, 8.6.

### 9. Manual endpoint testing (curl)
- Server started via `mvn spring-boot:run`; seeded a `brand_types` lookup row; ran GET/POST/GET-by-id/PATCH and
  error cases; restored DB to pre-test state (deleted created rows, removed seed).
- DONE: 9.1, 9.2, 9.3, 9.4, 9.5, 9.6, 9.7.

### 10. Playwright E2E
- 10.1 NOT APPLICABLE — backend-only change, no frontend/UI/browser workflow. Confirmed per checklist.

### 11. Documentation
- `docs/data-model.md` brand/`brand_types` mapping notes refreshed (JPA mapping, soft-delete via `deletedAt`,
  `brand_type_id` FK).
- Branch name, GPL headers, English-only artifacts verified consistent.
- DONE: 11.1, 11.2.

### 12. Final verification
- `mvn clean verify -Dpitest.skip=true` green (compile + unit + IT + spotbugs + modernizer).
- Coverage: new Brand domain business logic — `BrandServiceImpl` 100% lines/branches, `BrandController` 100%,
  DTO/repo/interface 100%; entity accessors lower per jacoco (SERIAL/Lombok-generated), matching the existing
  Dispensary entity baseline. No jacoco `check` rule is enforced in the pom.
- DB state clean: only `V0.1.0` migration, no leftover rows.
- DONE: 12.1, 12.2, 12.3, 12.4.

### 13. All complete
- DONE: 13.1.

## Deviations / Notes
- `BrandType` uses a public no-args `@AllArgsConstructor @NoArgsConstructor` (rather than
  `AccessLevel.PROTECTED`) so tests can seed a `brand_types` fixture (design requires seeding; a protected ctor
  would prevent `new BrandType()` in the repository/endpoint tests).
- `ServiceTest` (shared base) gained `@MockitoBean` for the Brand repositories — required by `DispensaryServiceTests`
  whose non-lazy `@ComponentScan` discovers the new `BrandServiceImpl`. No production code changed.
- Executing `mvn test`/`failsafe` from a stale `target/` triggers pre-existing spotbugs EI_EXPOSE_REP findings for
  both the new `Brand` and the existing `Dispensary` `@ManyToOne` accessors. A clean build (`mvn clean ...`) is
  green — this mirrors existing repo behavior (the `@ManyToOne` getter pattern predates this change and is present
  on `Dispensary`).
- `mvn test` runs only surefire (unit); repository/endpoint integration run under failsafe (`mvn verify`). Added
  `-Dpitest.skip=true` to avoid the (very slow) mutation sweep in local verification; pitest does not affect the
  jacoco 90% unit/IT coverage target.