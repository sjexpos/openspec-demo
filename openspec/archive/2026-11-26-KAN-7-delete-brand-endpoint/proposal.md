## Why

Administrators can create, read and update cannabis product brands through the API, but there is no way to retire a brand that is no longer valid: the `Brand` aggregate already carries soft-delete semantics, yet no `DELETE` route exists, so obsolete brands remain visible in every brand listing forever and nothing prevents removing a brand that live products still reference. This change exposes brand deletion as a first-class API capability so the catalog can be curated without breaking the products that depend on it.

## What Changes

- **New `DELETE /api/brands/{brandId}` endpoint** that soft-deletes a brand and returns HTTP `200 OK` with the standard `DataResponse` envelope, following the soft-delete convention already documented in `docs/backend-standards.md` and the existing `DELETE /api/dispensaries/{id}` precedent.
- **Soft delete only**: the brand row is never physically removed — `deleted_at` is set and the brand disappears from every brand query (`GET /api/brands` and `GET /api/brands/{brandId}`), consistent with the soft-delete semantics already required by the `brands-management` capability.
- **Not found is answered first**: a brand that does not exist, or that was already soft-deleted, is reported as `404 Not Found` with the standard error response. The brand is resolved before any other check, so a missing or already-deleted brand is never reported as a conflict.
- **Referential-integrity guard**: a brand that is still referenced by at least one non-deleted product cannot be deleted; the request is rejected with `409 Conflict` and a descriptive message naming the reason (e.g. `"... cannot be deleted because it is used by products"`). `409` is the business-conflict answer established in `docs/backend-standards.md` — `400 Bad Request` is reserved there for malformed input, so the acceptance wording carried in the ticket (a bad request for a brand used in a product) is superseded by the project standard.
- **Malformed identifier**: a non-numeric brand id is rejected with `400 Bad Request` by the existing type-mismatch handler, before any brand lookup runs.
- **Atomicity**: a rejected delete leaves the catalog exactly as it was — no brand is soft-deleted when the request fails.
- **No database migration**: `brands.deleted_at` already exists (Flyway `V0.1.0`) and the `Brand` entity already carries the `@SQLDelete` / `@SQLRestriction` soft-delete mapping; this change only adds the write path that uses them.
- **Testing, test-first**: failing-first tests for the new behaviour — service unit test, controller unit test and endpoint integration test — covering the successful delete, the not-found paths, the referential-integrity guard and the no-side-effect guarantee of a rejected delete.

## Capabilities

### New Capabilities
<!-- None. The change extends the existing brand write API inside the `brands-management` capability; it introduces no new domain area and no new capability path. -->

### Modified Capabilities
- `brands-management`: adds the delete-brand behaviour to the existing brand write API — the `DELETE /api/brands/{brandId}` endpoint, its soft-delete outcome, the `404 Not Found` answer for a missing or already-deleted brand, and the `409 Conflict` answer for a brand still referenced by non-deleted products.

## Impact

- **Presentation layer**: `BrandApi` gains the `DELETE /api/brands/{id}` mapping and `BrandController` implements it; a removal response DTO is added alongside the existing brand DTOs in `presentation/api/model/`. The existing `DataResponse`, `ErrorResponse` and `GlobalExceptionHandler` conventions are reused unchanged.
- **Application layer**: `BrandService` / `BrandServiceImpl` gain the delete operation — resolve the brand, apply the referential-integrity guard, perform the soft delete — reusing the existing `NotFoundException` (`404`) and `ConflictException` (`409`) application exceptions.
- **Domain / infrastructure**: `BrandRepository` gains the read needed to detect live product references. The `Brand` entity is unchanged (its soft-delete mapping already exists) and no inverse `products` collection is added to it — the `Product` aggregate keeps the owning side of that association, as its structural guard rails already require.
- **Database**: no schema change; the existing `deleted_at` column carries the deletion.
- **API surface**: one new route, `DELETE /api/brands/{brandId}`. No existing route, payload or response contract changes — no **BREAKING** change.
- **Documentation**: `docs/data-model.md` and `docs/backend-standards.md` already describe the brand soft-delete lifecycle and the delete-with-referential-guard convention this change implements, so no documentation rewrite is required.
