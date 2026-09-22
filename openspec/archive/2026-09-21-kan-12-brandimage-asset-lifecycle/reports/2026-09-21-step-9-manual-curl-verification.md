# Step 9 — Manual Endpoint Verification (2026-09-21): No-Endpoint Scope

Change: `kan-12-brandimage-asset-lifecycle` (branch `feat/kan-12-brandimage-asset-lifecycle`).

## Scope confirmation

Per `proposal.md` (Out of scope) and `design.md` (Non-Goals), this slice stops at the domain
boundary: it ships **no HTTP endpoint, no application service, no DTO, and no
`GlobalExceptionHandler` change**. There is therefore no endpoint to exercise with curl.

## Route inventory check

No new files were added under `presentation/` (controllers, API interfaces, DTOs) and no existing
controller was modified. Changed/added production files are strictly:

- `src/main/java/com/example/demo/domain/models/AssetStatus.java` (new)
- `src/main/java/com/example/demo/domain/models/BlobType.java` (modified, behaviour-preserving)
- `src/main/java/com/example/demo/domain/models/brand/BrandImage.java` (new)
- `src/main/java/com/example/demo/domain/repositories/BrandImageRepository.java` (new)
- `flyway/release_0.1/V0.1.1__brand_images_asset_lifecycle.sql` (new)

Full integration suite (80 tests, including all endpoint suites:
`BrandControllerEndpointsTests`, `ProductEndpointsTests`, `DispensaryEndpointsTests`,
`ActuatorEndpointsTests`) passes unchanged, confirming no route was added or altered.

## Standing in for endpoint checks

Behavioral proof for this slice is provided at the database level by
`BrandImageRepositoryTests` (11 tests against real Postgres + Flyway): persist-and-reload,
native string persistence proof, `CHECK`/unique/FK/`NOT NULL` proofs, query filtering, and the
`DELETED` tombstone-keeps-row proof — as specified in `design.md` D7.
