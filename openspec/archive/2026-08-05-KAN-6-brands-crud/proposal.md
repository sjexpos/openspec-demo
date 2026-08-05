## Why

Platform administrators need to manage the cannabis product brands that manufacture or supply products in the system. The `brands` and `brand_types` tables already exist in the database (provisioned by Flyway migration `V0.1.0`), but the `Brand` entity is **not JPA-mapped** — there is currently no way to create, read, update, or soft-delete brands through the API. This change delivers the backend CRUD capability so brands can be administered programmatically and persisted.

## What Changes

- **New Brand domain** (DDD layered, mirroring the existing `Dispensary` pattern):
  - Domain entities `Brand` (extends `BaseEntity` for audit + soft-delete) and `BrandType` (lookup entity) in `domain/models/brand/`.
  - Repository interfaces `BrandRepository` and `BrandTypeRepository` in `domain/repositories/`.
- **Application service layer**:
  - `BrandService` interface and `BrandServiceImpl` in `application/services/`.
  - Full CRUD operations: list, create, get-by-id, update.
- **Presentation API**:
  - `BrandApi` interface (OpenAPI documented) and `BrandController` implementation.
  - Request/response DTOs: `CreateBrandRequest`, `CreateBrandResponse`, `GetBrandResponse`, `GetAllBrandsResponse`, `UpdateBrandRequest`.
- **CRUD endpoints**:
  - `GET /api/brands` — list all non-deleted brands.
  - `POST /api/brands` — create a new brand (201).
  - `GET /api/brands/{brandId}` — get a non-deleted brand by ID.
  - `PATCH /api/brands/{brandId}` — partial/full update of a non-deleted brand.
- **Soft-delete semantics**: deleted brands are excluded from list/get; operating on a deleted brand returns 404. Records are never physically removed.
- **BrandType integration**: create/update resolves `brandTypeName` to the `BrandType` entity via the `brands_types` lookup.
- **Testing**: unit tests (service + controller), integration endpoint tests, and repository tests following the `Dispensary` test patterns.
- No new DB migration required — Flyway `V0.1.0` already provides `brands` and `brand_types` tables.

## Capabilities

### New Capabilities
- `brands-management`: covers the Brand/BrandType domain mapping, the repository and service layers, and the four CRUD REST endpoints with soft-delete semantics and validation. BrandType is folded into this capability as a supporting lookup dependency (it is not independently managed through any endpoint in this change), so it does not receive its own capability/spec.

### Modified Capabilities
<!-- No existing specs under openspec/specs/. None modified. -->

## Impact

- **Domain models**: adds `Brand` and `BrandType` entities (new package `domain/models/brand/`). No changes to existing `BaseEntity`, `Dispensary`, `Address`, or `LicenseStatus`.
- **Application services**: adds `BrandService` / `BrandServiceImpl` (new). No changes to existing `DispensaryService`.
- **Presentation (API/controllers)**: adds `BrandApi`, `BrandController`, and brand request/response DTOs. Reuses existing `DataResponse`, `ErrorResponse`, `GlobalExceptionHandler`, and OpenAPI config. No changes to `DispensaryApi`.
- **Repositories**: adds `BrandRepository` and `BrandTypeRepository`. No changes to existing repositories.
- **Tests**: adds service unit tests, controller unit tests, endpoint integration tests, and repository integration tests.
- **Database**: no migration changes — `brands` and `brand_types` already exist via Flyway `V0.1.0`. Soft-delete uses existing `deleted_at` column.
- **Documentation**: possibly refresh `docs/data-model.md` mapping notes for `brands`/`brand_types` if they are updated.