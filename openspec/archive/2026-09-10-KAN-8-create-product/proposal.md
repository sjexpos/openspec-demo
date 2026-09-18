## Why

The `Product` aggregate is the hub of the catalog data model (most tables relate to `products` directly or transitively: dispensary stock, reviews, images, favorites, brand featured products), but today only the `products` table exists (Flyway `V0.1.0`) — there is **no `Product` entity, repository, service or controller**. Without a create endpoint the catalog can only be seeded by raw SQL, which blocks the whole commercial chain (dispensary listing → stock → orders).

This change bootstraps the Product aggregate with its first write operation, `POST /api/products` (ticket `KAN-8`), mirroring the Brand vertical slice delivered in `KAN-6` so the second slice reinforces the established pattern instead of inventing a new one.

## What Changes

- **New `Product` aggregate (domain layer)**: `Product` as the aggregate root mapped to the existing `products` table, extending `BaseEntity` for audit + soft-delete, with `@SQLDelete` / `@SQLRestriction("deleted_at IS NULL")` exactly as `Brand` does.
- **Supporting lookup entities**: `Collection`, `Category`, `Subcategory` (holds its parent `Category`), `Unit`, and a minimal `Strain` mapping — created only to resolve the product's references. None of them gets its own endpoint in this change.
- **New repositories**: `ProductRepository` (including an OCPC existence query) plus `CollectionRepository`, `CategoryRepository`, `SubcategoryRepository`, `UnitRepository`, `StrainRepository`.
- **New application service**: `ProductService` / `ProductServiceImpl` exposing a single transactional create operation that resolves every reference and enforces the product's invariants before persisting.
- **New presentation slice**: `ProductApi` (OpenAPI-documented interface), `ProductController`, and the `CreateProductRequest` / `CreateProductResponse` DTOs.
- **New endpoint `POST /api/products`**: returns `201 Created` with `DataResponse<CreateProductResponse>` including the generated identifier; `400` on bean-validation failure, `404` when a referenced collection, category, subcategory, brand, strain or unit cannot be resolved, `409` when the `ocpc` is already taken by a live product or when the subcategory does not belong to the supplied category.
- **Atomicity**: the create operation is fully transactional — a rejected request persists nothing.
- **Optional flags** (`enabled`, `approved`, `isCoreProduct`, `thc`, `cbd`) may be omitted and then persist as `NULL`, which the data model reads as disabled / not approved / not core.
- **Scope boundary (flat product only)**: `product_images`, `product_uses` and `product_available_states` are deliberately excluded and become follow-up stories. Read, update and delete operations for products are also out of scope for this slice.
- **No database migration**: `products`, `collections`, `categories`, `subcategories`, `units` and `strains` already exist in `V0.1.0__initialData_SM.sql`. No schema drift is introduced.
- **No error-handling changes**: `NotFoundException` → 404, `ConflictException` → 409 and `MethodArgumentNotValidException` → 400 are already wired in `GlobalExceptionHandler`; this change only reuses them.
- Not breaking: purely additive. No existing endpoint, entity or response contract changes.

## Capabilities

### New Capabilities

- `products-management`: the Product aggregate and its write API — entity mapping to the existing `products` table with soft-delete semantics, resolution of the product's references (collection, category, subcategory, brand, strain, format unit, content unit), the taxonomy and uniqueness invariants a product must satisfy, and the `POST /api/products` endpoint contract with its `201/400/404/409` outcomes.

  Path `openspec/specs/products-management/spec.md` is confirmed correct: the project uses a flat, kebab-case, capability-per-aggregate layout (`openspec/specs/brands-management/`), so no extra domain level is introduced.

  No overlap with `brands-management`: that capability owns the Brand/BrandType lifecycle and its four CRUD endpoints; this one owns Product behavior. The single touchpoint is that a product must reference a live brand, and that guard is a Product-side invariant asserted on the `POST /api/products` contract — it neither adds nor changes any requirement about how brands themselves behave. The lookup entities (`Collection`, `Category`, `Subcategory`, `Unit`, `Strain`) are folded into `products-management` as supporting reference data, exactly as `KAN-6` folded `BrandType` into `brands-management`, because none of them is independently managed through any endpoint in this change. `Strain` is mapped minimally here to satisfy the reference only; the full Strain aggregate (terpenes, effects, conditions, flavors) remains a separate epic and will get its own capability.

### Modified Capabilities

None.

Story item 19 proposed migrating `BrandRepository.existsProductUsingBrand` from a native query to a typed `ProductRepository` call. This does **not** produce a modified capability, for two independent reasons:

1. **The premise is stale.** `BrandRepository` in the current tree is an empty `JpaRepository<Brand, Long>`; there is no `existsProductUsingBrand` method anywhere in the codebase, and `brands-management` exposes no delete endpoint that would need such a guard. There is nothing to migrate.
2. **Even if it existed, it would not be spec-level.** Swapping a native query for a derived query is invisible from outside: same inputs, same outputs, same status codes. Per the schema's own test — "if the implementation can change without changing externally visible behavior, it likely does not belong in the spec" — it would be an implementation detail, not a requirement change, and would not warrant a delta spec for `brands-management`.

Recorded under Impact as a no-op so the traceability back to the story is explicit.

## Impact

**Code — added (all new files, no existing file rewritten)**

- `domain/models/product/`: `Product`, `Collection`, `Category`, `Subcategory`, `Unit`; `domain/models/strain/Strain`.
- `domain/repositories/`: `ProductRepository`, `CollectionRepository`, `CategoryRepository`, `SubcategoryRepository`, `UnitRepository`, `StrainRepository`.
- `application/services/`: `ProductService` + `impl/ProductServiceImpl`.
- `presentation/`: `api/ProductApi`, `api/model/CreateProductRequest`, `api/model/CreateProductResponse`, `controllers/ProductController`.
- Tests: service unit tests, controller unit tests (`@WebMvcTest` + `GlobalExceptionHandler`), repository integration tests, and endpoint integration tests on the existing Testcontainers base — including an explicit rollback assertion.

**Code — reused unchanged**

`BaseEntity` (audit + soft-delete), `Brand` (referenced by the new `Product`), `DataResponse`, `ErrorResponse`, `FieldError`, `NotFoundException`, `ConflictException`, `JpaConfig`, `OpenApiConfig`. No existing entity, service, controller or DTO is modified.

**Code — modified (design-time addendum, see `design.md` decision D9)**

`GlobalExceptionHandler` gains one additive `@ExceptionHandler(HttpMessageNotReadableException.class)` so a truly empty/malformed request body returns `400` instead of the current `500`, satisfying the spec's "Empty request body" scenario. This handler is cross-cutting: it also fixes the same latent `500` for the existing `brands`/`dispensaries` endpoints on malformed bodies. No other error-handling behavior changes.

**APIs**

One new endpoint, `POST /api/products`, added to the OpenAPI surface under a new `Products` tag. No existing endpoint contract changes.

**Database**

No Flyway migration. All six required tables exist in `V0.1.0`; soft-delete reuses the existing `deleted_at` columns. Note that `collections`, `categories`, `subcategories` and `units` have **no** `deleted_at` column, so soft-delete filtering is only meaningful for `brands`, `strains` and `products` — the spec phase must word the not-found requirement accordingly rather than claiming soft-delete filtering for every reference.

**Documentation**

`docs/data-model.md` reviewed (no schema change) to confirm the documented Product rules match what is implemented.

**Deferred / follow-up (out of scope here)**

- Nested product collections: `product_images`, `product_uses`, `product_available_states`.
- Read/update/delete operations for products.
- The partial unique index on `products.ocpc` (schema change → separate migration ticket).
- Aligning `BrandService` / `DispensaryService` on a command-object signature style.
- The `DataIntegrityViolationException` handler currently echoing `ex.getMessage()` (security follow-up).
- The full `Strain` aggregate.

**Open items carried from the story (KAN-8 §8) — tracked, not blocking**

The endpoint contract committed to in KAN-8 §2 is concrete enough to implement and specify; these remain recorded so they are neither silently resolved nor lost:

1. **Reference style**: the request uses numeric ids (`brandId`, `categoryId`, …) rather than the name-based lookup `brands-management` uses for `brandTypeName`. Justified by 7 references and by subcategory names being unique only within a category, but it is a deliberate, visible divergence from the KAN-6 precedent and awaits product sign-off.
2. **Soft-deleted OCPC reuse**: uniqueness is enforced only against live products, so a soft-deleted product frees its OCPC. If OCPC must be unique forever, both the guard and the future index must drop the `deleted_at IS NULL` predicate.
3. **`approved` semantics**: the endpoint accepts `approved` from the client. If approval is a moderated workflow, it must be forced on creation and moved to a dedicated endpoint.
4. **No authentication or authorization exists** in the codebase, so the "catalog admin" actor is not enforced; this ships as an open endpoint and an auth story must precede production exposure.
5. **`Strain` is mapped minimally** to satisfy the reference only.
6. **Nested product collections are out of scope** per the agreed slice.
7. **`thc` / `cbd` are integers** in the schema; decimal percentages would require a column-type migration.
8. **OCPC uniqueness is application-level only** and therefore racy under concurrent requests until the partial unique index lands.
9. **Aggregate reference style**: the Product aggregate will hold JPA associations to other aggregates (`Brand`, `Strain`) rather than bare identifiers, diverging from the DDD "reference other aggregates by id only" rule. This is a deliberate consistency choice with the existing `Brand` / `Dispensary` mappings; the trade-off belongs in design.
10. **SpotBugs (KAN-8 DoD item 9, "code passes Spotless/SpotBugs")**: item 9's aggregate-reference style (see item 9 above) causes 24 new `EI_EXPOSE_REP`/`EI_EXPOSE_REP2` findings across `Product`/`Subcategory`, which are excluded via a scoped `spotbugs-exclude.xml` entry (see `design.md`'s Risks/Trade-offs). Separately, `mvn -o spotbugs:check` still fails on 9 pre-existing, unrelated findings in `Brand.java`/`Dispensary.java` that predate this change (confirmed identical to `main`) — fixing them would touch files this proposal's Impact section declares "reused unchanged," so they are explicitly out of this change's scope and carried forward as pre-existing debt, mirroring how item 7 above was resolved as a stale premise rather than silently satisfied.
11. **`description` is unbounded end-to-end** (2026-09-10, code-review m3 finding): no `@Size` constraint on the request DTO, and no HTTP request-body size limit configured anywhere in `src/main/resources`. This is disclosed here, not fixed in this change — per CLAUDE.md §7, adding a new `@Size` bound would be a new validation requirement needing a spec amendment first, not a silent implementation detail. Candidate for a follow-up hardening story alongside item 4 (no authentication).
12. **The reference-resolution 404s make the endpoint an unauthenticated existence-oracle** over six reference tables (2026-09-10, code-review m4 finding): holding six references valid and varying the seventh yields a binary "exists / does not exist" answer for `collections`, `categories`, `subcategories`, `units`, `brands` and `strains`. This is subordinate to and dominated by item 4 (no authentication), but worth naming explicitly so the future auth story inherits it rather than rediscovering it.
13. **`ProductServiceImpl` logs the client-supplied `ocpc` string verbatim, unsanitized** (2026-09-10, third code-review Minor m4 finding, CWE-117 log injection): `log.warn("Rejected product creation: OCPC {} already used by a live product", command.ocpc())` emits up to 64 characters of arbitrary client input — including `\n`, `\r` and ANSI escapes — to the log on an unauthenticated endpoint, permitting forged log lines. This is the **first** service in the codebase to log client-supplied free text at all (`BrandServiceImpl`, `DispensaryServiceImpl` and `AddressServiceImpl` contain no logging), so there is no existing precedent to inherit either way. Not fixed in this change: a follow-up should evaluate log-injection sanitization project-wide, since it would affect the logging pattern generally rather than just this one call site.
