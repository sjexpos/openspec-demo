# KAN-6: Brands CRUD — Technical Design

## Context

Platform administrators need to manage cannabis product brands through the API. The `brands` and `brand_types`
tables are already provisioned by Flyway migration `V0.1.0` (`flyway/release_0.1/V0.1.0__initialData_SM.sql`), but no
`Brand` JPA entity exists yet, so there is no way to create, read, update, or soft-delete brands programmatically.

The change introduces a **Brand domain** that mirrors the existing **Dispensary DDD layered pattern**
(domain → repositories → application services → presentation API/controller/DTOs) and exposes four endpoints
(`GET /api/brands`, `POST /api/brands`, `GET /api/brands/{brandId}`, `PATCH /api/brands/{brandId}`) with soft-delete
semantics and validation.

### Current state verified against the codebase

| Concern | Current codebase state |
|---|---|
| DB schema | `brands`: `id SERIAL PK`, `name varchar NOT NULL`, `description text NOT NULL`, `email varchar NOT NULL`, `state_license varchar NOT NULL`, `brand_type_id int NOT NULL` (FK → `brand_types.id`), `logo_image_url varchar NOT NULL`, 4 social/website URL columns nullable, `admin_id int NOT NULL`, `enabled boolean` nullable, plus `created_at/created_by/modified_at/modified_by/deleted_at/deleted_by` |
| Lookup table | `brand_types`: `id SERIAL PK`, `name varchar NOT NULL`. **No seed data exists** (only `license_statuses` is seeded) |
| `BaseEntity` | `@MappedSuperclass` providing `createdAt/createdBy/modifiedAt/modifiedBy/deletedAt/deletedBy`. Soft-delete flag is **`deletedAt`** (`@Column(name = "deleted_at")`) |
| Repository pattern | All existing repositories declare `JpaRepository<X, Long>` (`DispensaryRepository`, `LicenseStatusRepository`, `AddressRepository`) |
| Service pattern | `DispensaryService` interface + `DispensaryServiceImpl` in `application/services/impl`, constructor injection, positional parameters, `@Transactional(rollbackFor = Exception.class)` on mutating methods |
| Lookup resolution precedent | `DispensaryServiceImpl.create()` resolves `licenseStatus` via `LicenseStatusRepository.findByState(...).orElseThrow(NotFoundException)` → HTTP 404 for an invalid lookup value |
| Soft-delete filter precedent | `DispensaryRepository.findAllByDeletedAtIsNull()` derived query; `DispensaryServiceImpl.getById()` filters `deletedAt == null` in memory |
| Error handling | `GlobalExceptionHandler` maps `NotFoundException` → `404` with `ErrorResponse { timestamp, status, path, errors: [FieldError(field, message)] }`; validation failures → `400` |
| Response wrapper | `DataResponse<T>(T data)` record wrapping every payload |
| DTO conventions | Requests: `@Data @Builder @AllArgsConstructor @NoArgsConstructor` + `@Schema` + Jakarta validation. Responses: immutable `record`s |
| Test conventions | Service unit tests mock repositories (`@MockitoBean` + `@TestConfiguration @ComponentScan`); repository integration tests use `@DataJpaTest` + `TestEntityManager`; endpoint integration tests use `@SpringBootTest(MOCK)` + `MockMvc` |

### Known legacy inconsistency (not created by this change)

Existing entities declare `Integer id` (`Dispensary`, `LicenseStatus`, `Address`) while their repositories,
services, and API interfaces use `Long` (`JpaRepository<X, Long>`, `getById(Long)`, `@PathVariable Long`).
This change does **not** fix the legacy entities (out of scope), but the new Brand domain resolves the mismatch
itself — see Decision D1.

## Goals / Non-Goals

**Goals:**
- Full JPA mapping for `Brand` (extends `BaseEntity`) and `BrandType` (lookup) against the existing tables, with
  `ddl-auto: none` (no DDL changes, no new Flyway migration).
- Four CRUD endpoints with the exact behavioral contract in `specs/brands-management/spec.md`: list, create,
  get-by-id, partial update, all excluding soft-deleted records.
- `brandTypeName` resolved to the `BrandType` entity on create/update, following the `LicenseStatus` lookup
  precedent (unknown name → `404 Not Found`).
- Soft-delete semantics: never physically delete; deleted records are invisible to all endpoints; operating on a
  deleted brand returns `404`.
- Full test coverage (service unit, controller unit, endpoint integration, repository integration) matching the
  existing Dispensary test patterns and the 90% coverage threshold.

**Non-Goals:**
- No `DELETE /api/brands/{id}` endpoint (the spec/proposal scope is list, create, get-by-id, update only; soft
  deletion can be added later without DB changes).
- No restore/un-delete behavior. The spec treats a soft-deleted brand as not found on PATCH (`404`), so the
  enriched-US note *"allow restoring on update"* is explicitly **rejected** in favor of the spec contract.
- No `BrandType` management endpoints (it is a supporting lookup only).
- No seeding of `brand_types` data (no DB migration in this change; lookups are provisioned out of band or by
  future work).
- No changes to existing Dispensary code, `BaseEntity`, `GlobalExceptionHandler`, or `DataResponse`.
- No new external dependencies.

## Package & File Layout

All new files carry the project's standard GNU GPL license header + `// Copyright (c) 2026-2027 Sergio Exposito.`
Line.

```
src/main/java/com/example/demo/
├── domain/
│   ├── models/
│   │   └── brand/
│   │       ├── Brand.java                  # NEW — entity, table "brands", extends BaseEntity
│   │       └── BrandType.java              # NEW — lookup entity, table "brand_types"
│   └── repositories/
│       ├── BrandRepository.java            # NEW
│       └── BrandTypeRepository.java        # NEW
├── application/
│   └── services/
│       ├── BrandService.java               # NEW — interface
│       └── impl/
│           └── BrandServiceImpl.java       # NEW — implementation
└── presentation/
    ├── api/
    │   ├── BrandApi.java                   # NEW — OpenAPI-annotated endpoint interface
    │   └── model/
    │       ├── CreateBrandRequest.java     # NEW
    │       ├── CreateBrandResponse.java    # NEW (record)
    │       ├── GetBrandResponse.java       # NEW (record)
    │       ├── GetAllBrandsResponse.java   # NEW (record, compact)
    │       └── UpdateBrandRequest.java     # NEW (nullable @Data class for PATCH)
    └── controllers/
        └── BrandController.java            # NEW — implements BrandApi

src/test/java/com/example/demo/
├── application/services/
│   ├── BrandServiceTest.java               # NEW — base with @MockitoBean repositories (mirrors ServiceTest)
│   └── BrandServiceTests.java              # NEW — service unit tests
├── presentation/controllers/
│   └── BrandControllerTests.java           # NEW — controller unit tests (MockMvc standalone + GlobalExceptionHandler)
└── integration/
    ├── endpoints/
    │   └── BrandControllerEndpointsTests.java  # NEW — full-context MockMvc endpoint tests
    └── repositories/
        └── BrandRepositoryTests.java       # NEW — @DataJpaTest repository tests
```

Totals: **13 new main files, 5 new test files, 0 modified files, 0 deleted files, 0 DB migrations.**

## Decisions

### D1 & D2 — ID type: use `Long` uniformly (entity, repository, service, controller path variable)

**Decision:** `Brand.id` and `BrandType.id` are `Long`; `BrandRepository extends JpaRepository<Brand, Long>`,
`BrandTypeRepository extends JpaRepository<BrandType, Long>`; service signatures and the API
`@PathVariable Long id` all use `Long`.

**Rationale:**
- The dominant, actual convention in the real codebase is `Long` for the ID generic: every existing repository
  (`DispensaryRepository`, `LicenseStatusRepository`, `AddressRepository`) declares `JpaRepository<X, Long>`, every
  service uses `getById(Long)`, and every API interface uses `@PathVariable Long id`.
- The `brands` and `brand_types` PK columns are `SERIAL` (PostgreSQL `int4`). With `spring.jpa.hibernate.ddl-auto:
  none` (set in `application.yml`), Hibernate never regenerates DDL, so a `Long` `@Id` safely reads/writes the
  `int4` column (all values within the 32-bit range), and `GenerationType.IDENTITY` works against `SERIAL`.
- Uniform `Long` **resolves the mismatch the specs phase flagged** instead of reproducing it: the enriched-US sketch
  mixed `JpaRepository<Brand, Long>` with `findByIdAndDeletedAtIsNull(Integer id)` and a `Brand.id` of `Integer`.
  Making everything `Long` in the new domain avoids the legacy entity-`Integer`/repository-`Long` split and
  eliminates any `ClassCastException` risk in `findById(Long)`/path-variable binding.
- The spec does not fix the ID type ("generated identifier", "given identifier"), so no spec conflict.

**Alternatives considered:**
- *Entity `Integer` + repository/service/API `Long`* — replicates the existing Dispensary inconsistency; rejected.
- *Entity `Integer` everywhere* — most literal DB mapping, but diverges from the repository/service/API convention
  used across the codebase and forces converting the path variable; rejected for consistency.

### D3 — `brand_type_id` is NOT NULL: `@JoinColumn(name = "brand_type_id", nullable = false)`

The `brands.brand_type_id` column is `int NOT NULL` with FK `fk_brands_brand_types`. The `@ManyToOne` mapping on
`Brand.brandType` therefore declares `nullable = false`, and a `Brand` is **never persisted without a resolved
`BrandType`**:

- **Create**: `brandTypeName` is `@NotEmpty` (Bean Validation → 400 if blank) and is resolved through
  `BrandTypeRepository.findByName(...)`, which throws `NotFoundException` → 404 if unknown. The brand is saved only
  after resolution succeeds (spec scenario *"Invalid brand type name during create"*).
- **Update**: `brandTypeName` is optional. When provided it is re-resolved (unknown → 404, brand unchanged, per spec
  scenario *"Invalid brand type name during update"*); when omitted, the existing `brandType` is left untouched
  (never set to null).

### D4 — PATCH partial update: class-based `UpdateBrandRequest` with nullable fields, non-null-only application

**Decision:** `UpdateBrandRequest` is a plain class annotated `@Data` (not a record), with **all** fields nullable
and no `@NotNull/@NotEmpty` constraints. `email` keeps `@Email` (Bean Validation treats `null` as valid, so an
omitted email passes; a provided malformed email triggers `400`, covering the spec scenario *"Update brand with
invalid provided value"*).

Only non-null fields are applied. `enabled` is declared as `Boolean` (nullable, not primitive `boolean`), so:

- `enabled` omitted → `request.getEnabled() == null` → `setEnabled(...)` is **skipped**, the current value is kept;
- `enabled: false` explicitly sent → `request.getEnabled()` is `Boolean.FALSE` (non-null) → applied.

The service exposes `update(...)` with positional parameters (mirroring `DispensaryService.create(...)` style); the
controller maps each request field to a parameter, passing `null` when the field was omitted. The implementation
applies a setter only when the parameter is non-null, so "omitted" and "unchanged" collapse correctly for all
fields (including the nullable `enabled`).

**Alternatives considered:**
- `Map<String, Object>` + manual field dispatch — loses compile-time type safety and Bean Validation; rejected.
- Record + `@Builder` — records cannot distinguish an omitted builder value from `null` without a sentinel flag per
  field; rejected as over-engineered.

### D5 — Invalid `brandTypeName` → HTTP 404 (confirmed)

Confirmed following the existing `DispensaryServiceImpl` precedent: an unknown lookup value resolves to
`orElseThrow(new NotFoundException(...))`, which `GlobalExceptionHandler.handleNotFound` turns into `404` with
`FieldError("general", message)`. Message format follows the existing style: `"Invalid brand type: " + name`.
On update the resolution happens **before** any mutation inside a `@Transactional(rollbackFor = Exception.class)`
method, so the brand is guaranteed unchanged on failure (spec: *"MUST NOT change the brand"*).

### D6 — Soft-delete on update → HTTP 404 (confirmed)

`update(...)` loads the brand through `BrandRepository.findByIdAndDeletedAtIsNull(Long id)` (a derived query on
`BaseEntity.deletedAt`). If the row does not exist **or** has `deletedAt != null`, it throws
`NotFoundException("Brand not found with ID: " + id)` → 404. There is no restore-on-update (see Non-Goals).

### Supporting decisions

- **Repository queries** (derived, no custom SQL): `Iterable<Brand> findAllByDeletedAtIsNull()` (mirrors
  `DispensaryRepository`) and `Optional<Brand> findByIdAndDeletedAtIsNull(Long id)` (lets the DB filter soft-deleted
  rows instead of filtering in memory, and is what `getById`/`update` use). `BrandTypeRepository` exposes
  `Optional<BrandType> findByName(String name)`.
- **LAZY `brandType` + N+1**: `@ManyToOne(fetch = FetchType.LAZY)`. Response mapping calls `getBrandType().getName()`
  in the controller; this works because `spring.jpa.open-in-view: true` is set (same mechanism the Dispensary
  controller relies on when mapping `getLicenseStatus().getState()`). No N+1 mitigation needed for a single
  ManyToOne on list endpoints (one extra SELECT per row, matching existing Dispensary behavior). If profiling ever
  shows a hotspot, a fetch join can be added without API change.
- **No DELETE endpoint**: out of scope (Non-Goals); soft-deletion remains available for future work via the existing
  `deletedAt`/`@PreRemove` mechanics in `BaseEntity`.
- **Response DTO split**: `CreateBrandResponse` and `GetBrandResponse` are separate records even though identical,
  mirroring the Dispensary API's separate `CreateDispensaryResponse`/`GetDispensaryResponse`.
- **Compact list DTO**: `GetAllBrandsResponse` contains `id, name, description, email, stateLicense, brandTypeName,
  logoImageUrl, enabled` — omitting social URLs and `adminId`, per the enriched-US sketch and the spec's "compact
  view".
- **`adminId` type**: `Integer` (not `Long`) — it is a foreign reference to an external user table whose column is
  `int NOT NULL`; matches `Dispensary.adminId`/`DispensaryService` and the DTOs.
- **Transactional semantics**: `create` and `update` are `@Transactional(rollbackFor = Exception.class,
  propagation = Propagation.REQUIRED)`; read methods are not annotated (same as `DispensaryServiceImpl`).
- **Logging**: `@Slf4j` on `BrandServiceImpl` (backend standard).

## Domain Layer

### `BrandType` (lookup, no `BaseEntity`) — `com.example.demo.domain.models.brand`

Mirrors `LicenseStatus`:

```java
@Entity
@Table(name = "brand_types")
@Getter @Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BrandType {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @EqualsAndHashCode.Include
  private Long id;                       // SERIAL PK (int4), Long per D1/D2

  @Column(nullable = false)
  private String name;                   // NOT NULL
}
```

### `Brand` — `com.example.demo.domain.models.brand`, extends `BaseEntity`

Mirrors `Dispensary`:

```java
@Entity
@Table(name = "brands")
@Getter @Setter
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor @AllArgsConstructor @Builder
public class Brand extends BaseEntity {

  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @EqualsAndHashCode.Include
  private Long id;                              // SERIAL PK (int4), Long per D1/D2

  @Column(nullable = false)
  private String name;                          // varchar NOT NULL

  @Column(columnDefinition = "TEXT", nullable = false)
  private String description;                   // text NOT NULL

  @Column(nullable = false)
  private String email;                         // varchar NOT NULL

  @Column(name = "state_license", nullable = false)
  private String stateLicense;                  // varchar NOT NULL

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "brand_type_id", nullable = false)   // D3
  private BrandType brandType;                  // int NOT NULL FK → brand_types.id

  @Column(name = "logo_image_url", nullable = false)
  private String logoImageUrl;                  // varchar NOT NULL

  @Column(name = "instagram_url") private String instagramUrl;  // nullable
  @Column(name = "twitter_url")  private String twitterUrl;     // nullable
  @Column(name = "facebook_url") private String facebookUrl;    // nullable
  @Column(name = "website_url")  private String websiteUrl;     // nullable

  @Column(name = "admin_id", nullable = false)
  private Integer adminId;                      // int NOT NULL, external user ref

  private Boolean enabled;                      // boolean nullable
}
```

`BaseEntity` already provides `createdAt/createdBy/modifiedAt/modifiedBy/deletedAt/deletedBy` and the
`@PrePersist/@PreUpdate` hooks. Soft-delete is represented **only** by `deletedAt`; no `@SQLDelete` filter is used,
so plain `findAll()` must never be exposed through the API.

## Application Layer

### `BrandService` (interface) — `com.example.demo.application.services`

```java
public interface BrandService {
  Iterable<Brand> findAll();
  Brand create(String name, String description, String email, String stateLicense,
               String brandTypeName, String logoImageUrl, String instagramUrl,
               String twitterUrl, String facebookUrl, String websiteUrl,
               Integer adminId, Boolean enabled);
  Optional<Brand> getById(Long id);
  Brand update(Long id, String name, String description, String email, String stateLicense,
               String brandTypeName, String logoImageUrl, String instagramUrl,
               String twitterUrl, String facebookUrl, String websiteUrl,
               Integer adminId, Boolean enabled) throws NotFoundException;
}
```

Positional parameters mirror `DispensaryService`. `NotFoundException` extends `DomainException` (unchecked), so the
`throws` clause is optional/documentary (kept to mirror `deleteById`).

### `BrandServiceImpl` — `com.example.demo.application.services.impl`

`@Service @Slf4j`, constructor injection of `BrandRepository` + `BrandTypeRepository` (mirrors
`DispensaryServiceImpl`). Responsibilities:

| Method | Responsibilities |
|---|---|
| `findAll()` | Return `brandRepository.findAllByDeletedAtIsNull()` (deleted rows filtered at the DB). |
| `create(...)` | 1) `brandTypeRepository.findByName(brandTypeName).orElseThrow(() -> new NotFoundException("Invalid brand type: " + brandTypeName))` → 404 (D5). 2) Build `Brand` (via builder or setters). 3) `brandRepository.save(...)`. `@Transactional`. |
| `getById(Long id)` | Return `brandRepository.findByIdAndDeletedAtIsNull(id)`. |
| `update(...)` | 1) Load via `brandRepository.findByIdAndDeletedAtIsNull(id)`, else `throw new NotFoundException("Brand not found with ID: " + id)` → 404 (D6). 2) If `brandTypeName != null`, re-resolve the `BrandType` (unknown → `NotFoundException`, transaction rolls back, brand unchanged). 3) Apply each non-null parameter via the corresponding setter (D4). 4) `brandRepository.save(...)` and return. `@Transactional`. |

## Presentation Layer

### Request/Response DTOs — `com.example.demo.presentation.api.model`

**`CreateBrandRequest`** — `@Data @Builder @AllArgsConstructor @NoArgsConstructor` (mirrors `CreateDispensaryRequest`),
with `@Schema` annotations:

| Field | Type | Validation |
|---|---|---|
| `name` | `String` | `@NotEmpty(message = "name must not be empty")` |
| `description` | `String` | `@NotEmpty` |
| `email` | `String` | `@NotEmpty` + `@Email` |
| `stateLicense` | `String` | `@NotEmpty` |
| `brandTypeName` | `String` | `@NotEmpty` |
| `logoImageUrl` | `String` | `@NotEmpty` |
| `instagramUrl` / `twitterUrl` / `facebookUrl` / `websiteUrl` | `String` | nullable, no constraint |
| `adminId` | `Integer` | `@NotNull` |
| `enabled` | `Boolean` | nullable |

Spec contract: required fields are "present and non-empty" → `@NotEmpty` (implies non-null). A missing/blank field
yields `MethodArgumentNotValidException` → `GlobalExceptionHandler` → `400` with per-field errors.

**`UpdateBrandRequest`** — `@Data` class, **all** fields nullable, `@Email` on `email` only (D4).

**`CreateBrandResponse`** and **`GetBrandResponse`** — identical `record`s:
`(Long id, String name, String description, String email, String stateLicense, String brandTypeName,
String logoImageUrl, String instagramUrl, String twitterUrl, String facebookUrl, String websiteUrl,
Integer adminId, Boolean enabled)`. `brandTypeName` is populated from `brand.getBrandType().getName()`.

**`GetAllBrandsResponse`** — compact `record`:
`(Long id, String name, String description, String email, String stateLicense, String brandTypeName,
String logoImageUrl, Boolean enabled)`.

### `BrandApi` (interface) — `com.example.demo.presentation.api`

`@RequestMapping("/api/brands") @Tag(name = "Brands", description = "Brand management endpoints") @Validated`
(mirrors `DispensaryApi`):

| Method | Endpoint | Request body | Success | Failure | `@Operation` summary |
|---|---|---|---|---|---|
| `getAll()` | `GET /api/brands` | — | `200` `DataResponse<List<GetAllBrandsResponse>>` | — | "List all brands" — non-deleted only |
| `create(...)` | `POST /api/brands` | `@Valid CreateBrandRequest` | `201` `DataResponse<CreateBrandResponse>` | `400` | "Create a new brand" |
| `getById(...)` | `GET /api/brands/{brandId}` | — | `200` `DataResponse<GetBrandResponse>` | `404` | "Get brand by ID" |
| `update(...)` | `PATCH /api/brands/{brandId}` | `@Valid UpdateBrandRequest` | `200` `DataResponse<GetBrandResponse>` | `400`, `404` | "Update a brand" |

Path variable is `@PathVariable Long id` (D1/D2). Each method is annotated with `@ResponseStatus(HttpStatus.…)`,
`@Operation`, and `@ApiResponses` describing 200/201/400/404, exactly like `DispensaryApi`.

### `BrandController` — `com.example.demo.presentation.controllers`

`@RestController`, implements `BrandApi`, constructor injection of `BrandService`. Maps entities → response records
and wraps in `DataResponse`:

- `getAll()` — stream `service.findAll()` → map to `GetAllBrandsResponse` → `new DataResponse<>(list)`
  (mirrors `DispensaryController.getAll()`).
- `create(request)` — pass all request getters positionally to `service.create(...)` → wrap
  `CreateBrandResponse` in `DataResponse` (201 via `@ResponseStatus`).
- `getById(id)` — `service.getById(id).orElseThrow(() -> new NotFoundException("Brand not found with ID: " + id))`
  → wrap `GetBrandResponse` (mirrors `DispensaryController.getById`).
- `update(id, request)` — pass `request.getX()` for each field (null when omitted, D4) to `service.update(...)` →
  wrap `GetBrandResponse`.

## Error Handling Flow

Reuses the existing `GlobalExceptionHandler` — no new handler code:

1. **Validation failure** (create or update): `@Valid` on the request body triggers
   `MethodArgumentNotValidException` → `handleValidation` → `400` with one `FieldError(field, message)` per offending
   field. Covers spec scenarios: missing required field, invalid email, invalid provided value on PATCH.
2. **Brand not found / soft-deleted** (GET or PATCH by id): `NotFoundException` thrown by the service/controller →
   `handleNotFound` → `404` with `FieldError("general", "Brand not found with ID: {id}")`.
3. **Invalid `brandTypeName`**: `NotFoundException("Invalid brand type: {name}")` → `404` (same path as 2).
4. **Unexpected**: existing generic handler → `500` (unchanged behavior).

Example responses (from the existing contract):

```json
// 201/200 success
{ "data": { "id": 1, "name": "Green Leaf Farms", "brandTypeName": "grower", ... } }

// 400 validation
{ "timestamp": "...", "status": 400, "path": "/api/brands",
  "errors": [ { "field": "email", "message": "must be a well-formed email address" } ] }

// 404
{ "timestamp": "...", "status": 404, "path": "/api/brands/999",
  "errors": [ { "field": "general", "message": "Brand not found with ID: 999" } ] }
```

## Testing Plan

Tests are written first (TDD per `AGENTS.md`) alongside each layer, following the existing patterns and the 90%
coverage threshold.

### 1. Service unit tests — `BrandServiceTests` (extends new `BrandServiceTest` base)

Base class mirrors `ServiceTest`: `@ExtendWith(SpringExtension.class)` + `@MockitoBean BrandRepository
brandRepository` + `@MockitoBean BrandTypeRepository brandTypeRepository`. Test class adds
`@TestConfiguration @ComponentScan` and `@Autowired BrandService`, mirroring `DispensaryServiceTests`.

Cases (name style `should_..._when_...`):
1. `findAll` returns only non-deleted brands (mock `findAllByDeletedAtIsNull`).
2. `create` with a resolvable brand type saves the brand with the resolved `BrandType` attached.
3. `create` with unknown brand type throws `NotFoundException` and never calls `save`.
4. `getById` returns the brand for a live id (mock `findByIdAndDeletedAtIsNull`).
5. `getById` returns empty for a soft-deleted/non-existent id.
6. `update` with a partial set applies only provided fields and saves once.
7. `update` with a non-existent id throws `NotFoundException`; `save` never called.
8. `update` on a soft-deleted brand throws `NotFoundException`.
9. `update` with unknown `brandTypeName` throws `NotFoundException`; `save` never called.
10. `update` omitting `enabled` leaves the existing `enabled` value unchanged (D4).
11. `update` with `enabled: false` sets `enabled` to `false`.

### 2. Controller unit tests — `BrandControllerTests`

Standalone `MockMvc` (`MockMvcBuilders.standaloneSetup(new BrandController(mockBrandService))
.setControllerAdvice(new GlobalExceptionHandler())`), `BrandService` mocked via Mockito.

Cases: GET all → 200 + `DataResponse` list; POST valid → 201 + wrapped response; POST missing `name` → 400 with a
`FieldError` for `name`; POST invalid email → 400 field error; GET by id → 200; GET by id empty service → 404;
PATCH valid → 200; PATCH service throws `NotFoundException` → 404; PATCH invalid email → 400.

### 3. Endpoint integration tests — `BrandControllerEndpointsTests`

Extends `EndpointIntegrationTest` (`@SpringBootTest(MOCK)` + `@AutoConfigureMockMvc`, Flyway applied to the `tests`
PostgreSQL database). `@Autowired MockMvc` + `ObjectMapper`.

Setup requires a `BrandType` row — the migration ships **no** `brand_types` seed, so the test seeds one through the
repositories/`TestEntityManager` (or direct `BrandTypeRepository.save`) in `@BeforeEach`, and cleans up after.

Cases (map 1:1 to spec scenarios): POST valid → 201 and echo; POST missing/blank required field → 400 field error;
POST invalid email → 400; GET all → 200 excluding a brand soft-deleted in setup; GET by live id → 200; GET
non-existent id → 404; GET soft-deleted id → 404; PATCH partial → 200 with only provided fields changed; PATCH
non-existent id → 404; PATCH soft-deleted id → 404; PATCH invalid `brandTypeName` → 404 and brand unchanged; POST
with unknown `brandTypeName` → 404 and no brand row created.

### 4. Repository integration tests — `BrandRepositoryTests`

Extends `RepositoryTest` (`@DataJpaTest` + `TestEntityManager`), mirrors `DispensaryRepositoryTests`.

Cases:
- `findAllByDeletedAtIsNull()` excludes rows whose `deletedAt` is set, includes live rows (set `deletedAt` directly
  via the entity manager and assert on the id sets).
- `findByIdAndDeletedAtIsNull(id)` returns the brand for a live row and empty for a soft-deleted row.
- `BrandTypeRepository.findByName(name)` returns the matching lookup and empty for an unknown name.
- Persistence integrity: a saved `Brand` row is physically present in the `brands` table after soft-deletion is
  simulated on the entity (row-not-removed guarantee).

## Implementation Order

Dependency order (each layer is TDD red→green before moving on):

1. **Domain entities**: `BrandType`, `Brand` (with license header, `Long` ids, D3 mapping).
2. **Repository interfaces**: `BrandTypeRepository`, `BrandRepository` (+ repository integration tests).
3. **Service interface + implementation**: `BrandService`, `BrandServiceImpl` (+ service unit tests).
4. **Request/response DTOs**: `CreateBrandRequest`, `UpdateBrandRequest`, `CreateBrandResponse`,
   `GetBrandResponse`, `GetAllBrandsResponse`.
5. **API interface**: `BrandApi` (+ controller unit tests with mocked service).
6. **Controller**: `BrandController`.
7. **Endpoint integration tests**: `BrandControllerEndpointsTests` (seed `brand_types` fixture).
8. **Verification**: `mvn test` (90% coverage gate), `mvn compile` clean.
9. **Documentation**: refresh `docs/data-model.md` mapping notes for `brands`/`brand_types` if needed (proposal's
   Impact section).

## Risks / Trade-offs

- **`Long` id vs `int4` `SERIAL` column** → Safe while `ddl-auto: none` is enforced. If anyone ever enables
  `ddl-auto`, Hibernate would generate `bigint` PKs and drift from the migration SQL. **Mitigation:** never enable
  DDL generation; DB truth remains the Flyway migrations.
- **`brand_types` has no seed data** → Every `POST /api/brands` returns `404` ("Invalid brand type") until lookup
  rows exist, which could confuse API consumers. **Mitigation:** document the lookup seed requirement in the
  change's report/tasks; endpoint tests seed their own fixture. (Seeding is a separate, out-of-scope concern.)
- **PATCH null semantics**: an explicitly-null JSON value cannot clear a field (null always means "leave unchanged").
  **Mitigation:** documented in D4; the spec only requires *provided* fields to change, so this satisfies the
  contract. A future "clear field" feature would need an explicit null-marker convention.
- **LAZY `brandType` mapping relies on `open-in-view: true`** → matches existing Dispensary behavior, but couples
  response mapping to an open session. **Mitigation:** acceptable short-term; a fetch join or DTO projection can be
  introduced later without API changes if `open-in-view` is ever disabled.
- **N+1 on list** (one lazy `brand_types` SELECT per row) → Mirrors existing Dispensary list behavior; acceptable
  for lookup-cardinality joins. **Mitigation:** monitor; optimize with `@EntityGraph`/fetch join only if profiling
  requires it.
- **Legacy `Integer`-entity/`Long`-repository inconsistency remains in Dispensary code** → Out of scope for this
  change; the new Brand domain is uniform (`Long`) so no new inconsistency is introduced.

## Migration Plan

- **Database:** none — tables already exist (Flyway `V0.1.0`); no new Flyway scripts; `ddl-auto: none`.
- **Deploy:** standard code deploy; new JPA entities are discovered by the existing entity scan
  (`com.example.demo.domain.models`), new repositories by the existing `@EnableJpaRepositories`/component scan.
- **Rollback:** revert the code (remove the new files); the DB is untouched, so rollback is clean.
- **Feature branch:** `feature/KAN-6-brands-crud-backend` (per backend standards: `feature/[ticket-id]-backend`).

## Open Questions

- **None blocking.** Two operational notes (not decisions): (1) whether `brand_types` lookup data seeding should be
  tracked as a follow-up ticket (recommended — otherwise create always 404s against a fresh DB); (2) whether an
  `adminId` existence check against the external user service is required (currently treated as a plain `Integer`
  column, matching `Dispensary`).
