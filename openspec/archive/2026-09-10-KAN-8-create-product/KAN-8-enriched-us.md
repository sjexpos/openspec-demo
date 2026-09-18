# KAN-8 — Enriched User Story: Create Product endpoint (`POST /api/products`)

> **Placeholder key**: `KAN-8`. No Jira MCP was connected in this session, so this story was produced
> locally. Replace the key and paste the content into the ticket (sections `[original]` / `[enhanced]`).

## Executive Summary

Bootstrap the **Product aggregate** by implementing `POST /api/products`. This is the first slice of the
Product domain: today there is only a `products` table (Flyway `V0.1.0`) and a native query in
`BrandRepository.existsProductUsingBrand`, but **no `Product` entity, repository, service or controller**.

The endpoint MUST:

- Return **201 Created** with `DataResponse<CreateProductResponse>` containing the generated `id`.
- Return **400 Bad Request** on bean-validation failure (missing/blank/negative fields).
- Return **404 Not Found** when any referenced parent (collection, category, subcategory, brand, strain, units) does not exist or is soft-deleted.
- Return **409 Conflict** when `ocpc` is already used by a non-deleted product, or when `subcategory` does not belong to `category`.

Scope decision (confirmed with the requester): **flat product only** — `product_images`, `product_uses`
and `product_available_states` are out of scope and become follow-up stories.

---

## 1. User story

> *As a **catalog admin**,
> I want to **create a product through `POST /api/products`**,
> so that **brands' items can be catalogued once and then be listed, priced and stocked by any dispensary**.*

### Business value

The Product aggregate is the hub of the data model (26 of the 39 tables relate to it directly or
transitively: reviews, images, favorites, dispensary stock, brand featured products). Without a create
endpoint the catalog can only be seeded by SQL, which blocks the whole commercial workflow
(dispensary listing → stock → orders). This story unblocks that chain.

### Acceptance criteria

1. `POST /api/products` with a valid body → `201 Created` + `DataResponse<CreateProductResponse>` including the generated `id`, and one new row persisted in `products` with `created_at` set.
2. A required field missing, blank or invalid → `400 Bad Request` with the standard `ErrorResponse` and one `FieldError` per offending field (`field` = the DTO property name).
3. Any FK id that does not resolve to a live (non-soft-deleted) parent → `404 Not Found` with a descriptive message naming the offending reference, and **no row created**.
4. `ocpc` already in use by a non-deleted product → `409 Conflict`, and **no row created**.
5. `subcategoryId` whose `category_id` differs from the supplied `categoryId` → `409 Conflict`, and **no row created**.
6. `enabled`, `approved` and `is_core_product` are optional; when omitted they persist as `NULL`, which the data model treats as *disabled / not approved / not core*.
7. The whole operation is transactional: on any failure nothing is persisted.

---

## 2. Endpoint contract

| Item | Value |
|------|-------|
| Method | `POST` |
| URL | `/api/products` |
| Consumes | `application/json` |
| Produces | `application/json` |
| Success status | `201 Created` (`@ResponseStatus(HttpStatus.CREATED)` on the API interface, as in `BrandApi.create`) |
| Error statuses | `400`, `404`, `409`, `500` |
| Response envelope | `DataResponse<CreateProductResponse>` |
| Error envelope | `ErrorResponse(timestamp, status, path, errors[])` |
| Auth | None yet (no security layer in the codebase) — see §8 assumptions |

### 2.1 Request fields

| Field | Type | Required | Validation | DB column |
|-------|------|----------|------------|-----------|
| `ocpc` | String | ✅ | `@NotBlank`, `@Size(max = 64)`, unique among non-deleted products | `ocpc` |
| `title` | String | ✅ | `@NotBlank`, `@Size(max = 255)` | `title` |
| `description` | String | ❌ | free text | `description` |
| `collectionId` | Long | ✅ | `@NotNull`, `@Positive`, must exist | `collection_id` |
| `categoryId` | Long | ✅ | `@NotNull`, `@Positive`, must exist | `category_id` |
| `subcategoryId` | Long | ✅ | `@NotNull`, `@Positive`, must exist **and** belong to `categoryId` | `subcategory_id` |
| `brandId` | Long | ✅ | `@NotNull`, `@Positive`, must exist and not be soft-deleted | `brand_id` |
| `strainId` | Long | ✅ | `@NotNull`, `@Positive`, must exist and not be soft-deleted | `strain_id` |
| `formatValue` | Integer | ✅ | `@NotNull`, `@Positive` | `format_value` |
| `formatUnitId` | Long | ✅ | `@NotNull`, `@Positive`, must exist | `format_unit_id` |
| `contentValue` | Integer | ✅ | `@NotNull`, `@Positive` | `content_value` |
| `contentUnitId` | Long | ✅ | `@NotNull`, `@Positive`, must exist | `content_unit_id` |
| `isCoreProduct` | Boolean | ❌ | — | `is_core_product` |
| `approved` | Boolean | ❌ | — | `approved` |
| `thc` | Integer | ❌ | `@PositiveOrZero` | `thc` |
| `cbd` | Integer | ❌ | `@PositiveOrZero` | `cbd` |
| `enabled` | Boolean | ❌ | — | `enabled` |

Audit columns (`created_at`, `created_by`, `modified_at`, `modified_by`, `deleted_at`, `deleted_by`) are
**not** accepted from the client; they are managed by `BaseEntity` / `AuditingEntityListener`.

### 2.2 Request example

```json
{
  "ocpc": "OCPC-2026-0001",
  "title": "Blue Dream Pre-Roll 1g",
  "description": "Single pre-roll, hybrid, 1 gram",
  "collectionId": 1,
  "categoryId": 2,
  "subcategoryId": 5,
  "brandId": 3,
  "strainId": 8,
  "formatValue": 1,
  "formatUnitId": 1,
  "contentValue": 1,
  "contentUnitId": 1,
  "isCoreProduct": true,
  "approved": false,
  "thc": 22,
  "cbd": 1,
  "enabled": true
}
```

### 2.3 Success response (`201 Created`)

```json
{
  "data": {
    "id": 42,
    "ocpc": "OCPC-2026-0001",
    "title": "Blue Dream Pre-Roll 1g",
    "description": "Single pre-roll, hybrid, 1 gram",
    "collectionId": 1,
    "categoryId": 2,
    "subcategoryId": 5,
    "brandId": 3,
    "strainId": 8,
    "formatValue": 1,
    "formatUnitId": 1,
    "contentValue": 1,
    "contentUnitId": 1,
    "isCoreProduct": true,
    "approved": false,
    "thc": 22,
    "cbd": 1,
    "enabled": true
  }
}
```

### 2.4 Error responses

`400 Bad Request` — bean validation (handled by the existing `MethodArgumentNotValidException` handler):

```json
{
  "timestamp": "2026-09-03T10:00:00Z",
  "status": 400,
  "path": "/api/products",
  "errors": [
    { "field": "title", "message": "title must not be empty" },
    { "field": "formatValue", "message": "formatValue must be positive" }
  ]
}
```

`404 Not Found` — unknown reference (`NotFoundException` → existing handler):

```json
{
  "timestamp": "2026-09-03T10:00:00Z",
  "status": 404,
  "path": "/api/products",
  "errors": [{ "field": "general", "message": "Brand not found with ID: 99" }]
}
```

`409 Conflict` — duplicated `ocpc` or category/subcategory mismatch (`ConflictException` → existing handler):

```json
{
  "timestamp": "2026-09-03T10:00:00Z",
  "status": 409,
  "path": "/api/products",
  "errors": [{ "field": "general", "message": "Product already exists with OCPC: OCPC-2026-0001" }]
}
```

---

## 3. Files to create / modify

| # | File | Action | Layer |
|---|------|--------|-------|
| 1 | `domain/models/product/Product.java` | **Create** — `@Entity @Table(name = "products")`, extends `BaseEntity`, `@SQLDelete` + `@SQLRestriction("deleted_at IS NULL")` (mirror `Brand`) | Domain |
| 2 | `domain/models/product/Collection.java` | **Create** — lookup entity for `collections` | Domain |
| 3 | `domain/models/product/Category.java` | **Create** — lookup entity for `categories` | Domain |
| 4 | `domain/models/product/Subcategory.java` | **Create** — lookup entity for `subcategories` (holds `@ManyToOne Category`) | Domain |
| 5 | `domain/models/product/Unit.java` | **Create** — lookup entity for `units` | Domain |
| 6 | `domain/models/strain/Strain.java` | **Create** — minimal mapping of `strains` (only what the FK needs; full strain story is separate) | Domain |
| 7 | `domain/repositories/ProductRepository.java` | **Create** — `extends JpaRepository<Product, Long>`, `boolean existsByOcpc(String ocpc)` | Domain |
| 8 | `domain/repositories/CollectionRepository.java` | **Create** | Domain |
| 9 | `domain/repositories/CategoryRepository.java` | **Create** | Domain |
| 10 | `domain/repositories/SubcategoryRepository.java` | **Create** | Domain |
| 11 | `domain/repositories/UnitRepository.java` | **Create** | Domain |
| 12 | `domain/repositories/StrainRepository.java` | **Create** | Domain |
| 13 | `application/services/ProductService.java` | **Create** — `Product create(CreateProductCommand command)` | Application |
| 14 | `application/services/impl/ProductServiceImpl.java` | **Create** — `@Slf4j @Service`, `@Transactional`, FK resolution + business guards | Application |
| 15 | `presentation/api/ProductApi.java` | **Create** — `@RequestMapping("/api/products")`, `@Tag`, `@Validated`, `@PostMapping` with Swagger annotations | Presentation |
| 16 | `presentation/api/model/CreateProductRequest.java` | **Create** — Lombok `@Data @Builder`, bean-validation + `@Schema` (mirror `CreateBrandRequest`) | Presentation |
| 17 | `presentation/api/model/CreateProductResponse.java` | **Create** — `record` (mirror `CreateBrandResponse`) | Presentation |
| 18 | `presentation/controllers/ProductController.java` | **Create** — `@RestController implements ProductApi`, mapping only | Presentation |
| 19 | `domain/repositories/BrandRepository.java` | **Modify (optional, recommended)** — replace the native `existsProductUsingBrand` query with `productRepository.existsByBrandIdAndDeletedAtIsNull(...)` now that the entity exists | Domain |
| 20 | `docs/data-model.md` | **Review** — no schema change; confirm the documented Product validation rules match the implementation | Docs |
| 21 | `openspec/specs/products-management/spec.md` | **Create** — new capability spec with the requirement + scenarios below | OpenSpec |

**No Flyway migration is required**: `products`, `collections`, `categories`, `subcategories`, `units`
and `strains` all already exist in `V0.1.0__initialData_SM.sql`.

> ⚠️ **Pattern note (DRY)**: this story creates six lookup repositories that all do "find by id or throw
> 404". Extract a single private helper in `ProductServiceImpl`
> (`<T> T requireById(JpaRepository<T, Long> repo, Long id, String label)`) instead of repeating the
> `orElseThrow` chain 7 times.

---

## 4. Implementation details per layer

### 4.1 Domain — `Product` entity

```java
@Entity
@Table(name = "products")
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLDelete(sql = "UPDATE products SET deleted_at = now() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class Product extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @EqualsAndHashCode.Include
  private Long id;

  @Column(nullable = false)
  private String ocpc;

  @Column(nullable = false)
  private String title;

  @Column(columnDefinition = "TEXT")
  private String description;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "collection_id", nullable = false)
  private Collection collection;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "category_id", nullable = false)
  private Category category;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "subcategory_id", nullable = false)
  private Subcategory subcategory;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "brand_id", nullable = false)
  private Brand brand;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "strain_id", nullable = false)
  private Strain strain;

  @Column(name = "format_value", nullable = false)
  private Integer formatValue;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "format_unit_id", nullable = false)
  private Unit formatUnit;

  @Column(name = "content_value", nullable = false)
  private Integer contentValue;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "content_unit_id", nullable = false)
  private Unit contentUnit;

  @Column(name = "is_core_product")
  private Boolean isCoreProduct;

  private Boolean approved;
  private Integer thc;
  private Integer cbd;
  private Boolean enabled;
}
```

All `@ManyToOne` associations are `LAZY` to avoid eager-fetch cascades (backend standards: *Avoid N+1*).

### 4.2 Application — service contract

The Brand/Dispensary services use long positional parameter lists (12–13 arguments). Product has **17**,
which would be unreadable and error-prone. Introduce a command object:

```java
public record CreateProductCommand(
    String ocpc,
    String title,
    String description,
    Long collectionId,
    Long categoryId,
    Long subcategoryId,
    Long brandId,
    Long strainId,
    Integer formatValue,
    Long formatUnitId,
    Integer contentValue,
    Long contentUnitId,
    Boolean isCoreProduct,
    Boolean approved,
    Integer thc,
    Integer cbd,
    Boolean enabled) {}
```

```java
public interface ProductService {
  Product create(CreateProductCommand command) throws NotFoundException, ConflictException;
}
```

> This is a deliberate, documented deviation from the existing Brand/Dispensary signature style.
> Rationale: >7 parameters is a code smell and adjacent `Long` parameters are trivially swappable at the
> call site. Recommend a follow-up ticket to align Brand/Dispensary with the command pattern.

### 4.3 Application — `ProductServiceImpl.create`

Guard order matters and MUST be: **uniqueness (409) → FK resolution (404) → coherence (409) → save**.

```java
@Override
@Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
public Product create(CreateProductCommand command) {
  if (this.productRepository.existsByOcpc(command.ocpc())) {
    throw new ConflictException("Product already exists with OCPC: " + command.ocpc());
  }
  Category category = requireById(this.categoryRepository, command.categoryId(), "Category");
  Subcategory subcategory =
      requireById(this.subcategoryRepository, command.subcategoryId(), "Subcategory");
  if (!subcategory.getCategory().getId().equals(category.getId())) {
    throw new ConflictException(
        "Subcategory " + subcategory.getId() + " does not belong to category " + category.getId());
  }
  // collection, brand, strain, formatUnit, contentUnit resolved via the same helper
  Product product = Product.builder()....build();
  Product saved = this.productRepository.save(product);
  log.info("Product created {}", saved.getId());
  return saved;
}
```

`existsByOcpc` benefits from the `@SQLRestriction("deleted_at IS NULL")` on `Product`, so soft-deleted
products do not block OCPC reuse — **confirm this is the desired business rule** (see §8).

### 4.4 Presentation — `ProductApi`

```java
@RequestMapping("/api/products")
@Tag(name = "Products", description = "Product management endpoints")
@Validated
public interface ProductApi {

  @PostMapping
  @ResponseStatus(value = HttpStatus.CREATED)
  @Operation(
      summary = "Create a new product",
      description = "Creates a new product and returns it with the generated ID")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "Product created successfully"),
    @ApiResponse(responseCode = "400", description = "Invalid input"),
    @ApiResponse(responseCode = "404", description = "Referenced entity not found"),
    @ApiResponse(responseCode = "409", description = "Duplicated OCPC or inconsistent taxonomy")
  })
  DataResponse<CreateProductResponse> create(@Valid @RequestBody CreateProductRequest request);
}
```

### 4.5 Presentation — `ProductController`

Controller contains **no business logic**: DTO → command, service call, entity → DTO. Keep a single
private `toCreateProductResponse(Product)` mapper.

### 4.6 Error handling

No change to `GlobalExceptionHandler` is needed: `NotFoundException` → 404, `ConflictException` → 409,
`MethodArgumentNotValidException` → 400 are already wired.

---

## 5. Testing plan (TDD — write the failing test first)

Naming: `should_[expected_behavior]_when_[condition]`; structure: Arrange/Act/Assert; coverage ≥ 90%.

### 5.1 Service unit tests — `application/services/ProductServiceTests.java`

1. `should_persistProduct_when_commandIsValid` — all repositories stubbed; assert `save` called once and the returned entity carries every mapped field.
2. `should_throwConflict_when_ocpcAlreadyExists` — `existsByOcpc` → `true`; `save` never called.
3. `should_throwNotFound_when_brandDoesNotExist` — brand repo → empty; `save` never called.
4. `should_throwNotFound_when_strainDoesNotExist`.
5. `should_throwNotFound_when_collectionDoesNotExist`.
6. `should_throwNotFound_when_categoryDoesNotExist`.
7. `should_throwNotFound_when_subcategoryDoesNotExist`.
8. `should_throwNotFound_when_formatUnitDoesNotExist`.
9. `should_throwNotFound_when_contentUnitDoesNotExist`.
10. `should_throwConflict_when_subcategoryDoesNotBelongToCategory` — `save` never called.
11. `should_persistNullFlags_when_optionalFieldsAreOmitted` — `enabled`/`approved`/`isCoreProduct`/`thc`/`cbd` null.

### 5.2 Controller unit tests — `presentation/controllers/ProductControllerTests.java`

(`@WebMvcTest` + mocked `ProductService` + `@Import(GlobalExceptionHandler.class)`, mirroring `BrandControllerTests`.)

1. `should_return201AndBody_when_productCreated` — assert `status().isCreated()`, `$.data.id`, `$.data.ocpc`.
2. `should_return400_when_requiredFieldIsMissing` — e.g. blank `title`; assert `$.errors[0].field` = `"title"`.
3. `should_return400_when_formatValueIsNotPositive` — `formatValue = 0`.
4. `should_return400_when_thcIsNegative`.
5. `should_return404_when_serviceThrowsNotFound` — assert `$.errors[0].field` = `"general"`.
6. `should_return409_when_serviceThrowsConflict`.

### 5.3 Repository integration tests — `integration/repositories/ProductRepositoryTests.java`

1. `should_returnTrue_when_ocpcExists`.
2. `should_returnFalse_when_ocpcDoesNotExist`.
3. `should_excludeSoftDeletedProducts_when_queryingById` — validates `@SQLRestriction`.

### 5.4 Endpoint integration tests — `integration/endpoints/ProductEndpointsTests.java`

(extends the existing `EndpointIntegrationTest` base, real Postgres/Testcontainers as for Brands.)

1. `should_return201AndPersistRow_when_payloadIsValid` — verify the row exists with `created_at` not null.
2. `should_return409_when_ocpcIsDuplicated` — POST twice.
3. `should_return404_when_brandIdIsUnknown`.
4. `should_return409_when_subcategoryBelongsToAnotherCategory`.
5. `should_return400_when_bodyIsEmpty`.
6. `should_notPersistAnything_when_requestFails` — count rows before/after a failing POST (transaction rollback proof).

---

## 6. Definition of Done

1. `POST /api/products` implemented across the four layers (API interface, controller, service + impl, repositories) with `Product` and its lookup entities mapped to the existing tables.
2. `CreateProductRequest` / `CreateProductResponse` created; success wrapped in `DataResponse`, errors in `ErrorResponse`.
3. Guards implemented in the documented order: OCPC uniqueness → FK resolution → category/subcategory coherence.
4. All unit, repository and endpoint tests above pass; `mvn test` green; coverage ≥ 90% for the new classes.
5. Swagger/OpenAPI documents the 201/400/404/409 responses; endpoint visible in `/swagger-ui`.
6. No Flyway migration added; no schema drift (`mvn flyway:info` unchanged).
7. `BrandRepository.existsProductUsingBrand` migrated from native SQL to the typed `ProductRepository` query (or a follow-up ticket explicitly created).
8. `openspec/specs/products-management/spec.md` created with the requirement + scenarios; `docs/data-model.md` reviewed.
9. Code passes Spotless/SpotBugs, respects DDD layering, SOLID, DRY, `@Slf4j` logging, license header, English-only artifacts.
10. Branch named with the `-backend` suffix; conventional commits.

---

## 7. Non-functional requirements

### Security
- Validate **every** input at the presentation boundary (bean validation) and re-check business invariants in the application layer; never trust client-supplied ids.
- Reject client-supplied audit fields (`createdBy`, `deletedAt`, …) — they are simply absent from the DTO.
- Never echo SQL, stack traces or constraint names to the client; `DataIntegrityViolationException` currently leaks `ex.getMessage()` in `GlobalExceptionHandler` → **raise a security follow-up ticket** and make sure the Product happy/error paths do not rely on that handler.
- `@Size` caps on `ocpc`/`title` bound the payload and prevent oversized-string abuse.

### Performance
- All associations `LAZY`; the create path performs at most 8 point lookups by primary key + 1 insert.
- `existsByOcpc` requires an index: `products.ocpc` has no unique index today. Recommend
  `CREATE UNIQUE INDEX CONCURRENTLY ux_products_ocpc ON products (ocpc) WHERE deleted_at IS NULL`
  in a **separate** migration ticket (it changes the schema, out of this story's scope) — until then the
  uniqueness guard is application-level only and racy under concurrency.
- Target p95 < 300 ms.

### Observability
- `log.info("Product created {}", id)` on success; `log.warn` on business rejections; no PII in logs.

### Maintainability
- Follow the existing Brand vertical slice as the reference implementation; any deviation (command object) is documented in this ticket.

---

## 8. Critical assumptions to validate

1. **FK reference style**: the request uses **numeric ids** (`brandId`, `categoryId`, …) rather than the name-based lookup used by `CreateBrandRequest.brandTypeName`. Rationale: 7 FKs, and subcategory names are only unique per category. *Needs product sign-off — it is the one open question from refinement.*
2. **Soft-deleted OCPC reuse**: uniqueness is checked only against non-deleted products. If OCPC must be globally unique forever, the guard and the proposed index must drop the partial predicate.
3. **`approved` semantics**: the endpoint accepts `approved` from the client. If approval is meant to be a moderated workflow, it must be forced to `false`/`null` on creation and moved to a dedicated endpoint (follow-up story).
4. **No authentication/authorization** exists in the codebase, so "catalog admin" is not enforced. The story ships an open endpoint; an auth story must precede production exposure.
5. **`Strain` entity is created minimally** here only to satisfy the FK. The full Strain aggregate (terpenes, effects, conditions, flavors) is a separate epic.
6. Nested collections (`product_images`, `product_uses`, `product_available_states`) are **out of scope** per the agreed slice.
7. `thc`/`cbd` are `int` in the schema; if percentages with decimals are needed the column type must change (migration ticket).

---

## 9. Proposed OpenSpec requirement (for `openspec/specs/products-management/spec.md`)

### Requirement: Create product via POST /api/products

The system SHALL expose `POST /api/products` to create a product and SHALL return HTTP `201 Created`
wrapped in the standard `DataResponse`. The request SHALL accept `ocpc`, `title`, `description`,
`collectionId`, `categoryId`, `subcategoryId`, `brandId`, `strainId`, `formatValue`, `formatUnitId`,
`contentValue`, `contentUnitId`, `isCoreProduct`, `approved`, `thc`, `cbd` and `enabled`. The system
SHALL require every field except `description`, `isCoreProduct`, `approved`, `thc`, `cbd` and `enabled`.
The system SHALL reject the request when the `ocpc` is already used by a non-deleted product, when any
referenced entity does not exist, or when the referenced subcategory does not belong to the referenced
category. Products SHALL never be created partially: the operation MUST be atomic.

#### Scenario: Valid product creation
- **WHEN** a client sends a valid `POST /api/products` request with all required fields
- **THEN** the system SHALL create the product **AND** return HTTP `201 Created` with the created product and its generated identifier

#### Scenario: Missing required field on creation
- **WHEN** a client sends `POST /api/products` with a required field missing or blank
- **THEN** the system SHALL return HTTP `400 Bad Request` with the standard validation error response

#### Scenario: Unknown referenced entity
- **WHEN** a client sends `POST /api/products` referencing a collection, category, subcategory, brand, strain or unit that does not exist or is soft-deleted
- **THEN** the system SHALL return HTTP `404 Not Found` **AND** no product SHALL be created

#### Scenario: Duplicated OCPC
- **WHEN** a client sends `POST /api/products` with an `ocpc` already used by a non-deleted product
- **THEN** the system SHALL return HTTP `409 Conflict` **AND** no product SHALL be created

#### Scenario: Subcategory not belonging to category
- **WHEN** a client sends `POST /api/products` with a `subcategoryId` whose parent category differs from the supplied `categoryId`
- **THEN** the system SHALL return HTTP `409 Conflict` **AND** no product SHALL be created

---

## 10. Success metrics

| Metric | Target |
|--------|--------|
| Endpoint latency p95 | < 300 ms |
| Automated coverage of the new classes | ≥ 90% branches/lines |
| Error scenarios covered by tests | 100% of the documented 400/404/409 cases |
| Orphan/inconsistent products created in staging | 0 (no product with a subcategory outside its category) |
| Manual SQL inserts needed to seed the catalog | 0 after release |

## 11. Suggested task breakdown (baby steps, TDD)

1. `Product` + lookup entities and repositories, with repository integration tests. *(red → green)*
2. `ProductService` contract + `CreateProductCommand`.
3. `ProductServiceImpl` happy path (unit test first).
4. Guards: OCPC uniqueness → FK 404s → category/subcategory coherence (one test per guard).
5. DTOs + `ProductApi` + `ProductController` with controller unit tests.
6. Endpoint integration tests, including the rollback test.
7. Swagger review, `BrandRepository` native-query migration, docs/OpenSpec update.
