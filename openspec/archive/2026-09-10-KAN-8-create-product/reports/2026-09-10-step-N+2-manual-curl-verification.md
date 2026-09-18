# Step N+2 Report - Manual Endpoint Testing with curl

- Date: 2026-09-10
- Change: KAN-8-create-product
- Agent: opsx-apply (Claude Sonnet 5)

## Pre-test state (before seeding)

- Postgres: already-running, healthy container (same one used by the automated integration suite),
  `tests` database, schema `public` at Flyway version `0.1.0`.
- Backend: started with `mvn -o -Dspotbugs.skip=true spring-boot:run`, with
  `SPRING_DATASOURCE_USERNAME=test`, `SPRING_DATASOURCE_PASSWORD=test`,
  `SPRING_DATASOURCE_SCHEMA=tests` exported first — the shipped `application.yml` defaults to a
  `demo` database with `postgres`/`postgres` credentials, neither of which exist on this host's
  Postgres instance (only the `tests` database with `test`/`test` credentials does, per the
  batch-1 environment note); this override points the running app at the exact same database the
  automated test suite uses, without touching any committed file.
- `curl -s localhost:8080/actuator/health` → `{"status":"UP", ...}` confirmed before any product
  test began.
- Pre-test row counts (all ten tables): `products` 0, `collections` 0, `categories` 0,
  `subcategories` 0, `units` 0, `brands` 0, `brand_types` 0, `strains` 0, `strain_types` 0,
  `seed_companies` 0.
- Reference data seeded via native SQL (task 11.2) — ids recorded for exact restoration:
  - `collections.id = 322` ("Flowers")
  - `categories.id = 389` ("Edibles"), `categories.id = 390` ("Concentrates")
  - `subcategories.id = 376` ("Gummies", under category 389), `subcategories.id = 377` ("Wax",
    under category 390)
  - `units.id = 452` ("gram", format), `units.id = 453` ("milligram", content)
  - `brand_types.id = 1078` ("grower-curl")
  - `brands.id = 937` ("curl-brand")
  - `strain_types.id = 334` ("Sativa-curl"), `seed_companies.id = 334` ("Acme-curl")
  - `strains.id = 391` ("Blue Dream", `ucpc = UCPC-CURL-001`)
- Also confirmed (task 8.14) via `curl -s localhost:8080/api-docs`: the `/api/products` path's
  `POST` operation is tagged `Products` and documents responses `201`, `400`, `404`, `409`
  (equivalent evidence to browsing `/swagger-ui.html`, same underlying OpenAPI document).

## Step N+2.1 - `POST /api/products` — happy path (201)

```bash
curl -s -i -X POST localhost:8080/api/products -H "Content-Type: application/json" -d '{
  "ocpc": "OCPC-CURL-VALID-001", "title": "Sour Diesel Gummies", "description": "A tasty gummy",
  "collectionId": 322, "categoryId": 389, "subcategoryId": 376, "brandId": 937, "strainId": 391,
  "formatValue": 10, "formatUnitId": 452, "contentValue": 100, "contentUnitId": 453,
  "isCoreProduct": true, "approved": true, "thc": 15, "cbd": 5, "enabled": true
}'
```

- Response: `201`, body:
  `{"data":{"id":271,"ocpc":"OCPC-CURL-VALID-001","title":"Sour Diesel Gummies",...}}`
- DB after: `SELECT id, ocpc, created_at FROM products WHERE id=271` returned exactly one row,
  `created_at = 2026-09-10 17:48:09.205219` (non-null).
- Restored: row `id=271` was left in place to drive Step N+2.2 (duplicate-OCPC test), then deleted
  in Step N+2.2's own restoration.

## Step N+2.2 - `POST /api/products` — duplicated OCPC (409)

```bash
curl -s -i -X POST localhost:8080/api/products -H "Content-Type: application/json" -d '{
  "ocpc": "OCPC-CURL-VALID-001", "title": "Duplicate attempt", "collectionId": 322,
  "categoryId": 389, "subcategoryId": 376, "brandId": 937, "strainId": 391, "formatValue": 10,
  "formatUnitId": 452, "contentValue": 100, "contentUnitId": 453
}'
```

- Response: `409`,
  `{"status":409,"errors":[{"field":"general","message":"Product already exists with OCPC: OCPC-CURL-VALID-001"}]}`
- DB after: `SELECT count(*) FROM products` = 1 (unchanged from Step N+2.1, no second row).
- Restored: `DELETE FROM products WHERE id=271;` — confirmed `products` count back to 0.

## Step N+2.3 - `POST /api/products` — unknown reference matrix (404 × 7)

```bash
curl -s -i -X POST localhost:8080/api/products -H "Content-Type: application/json" -d '{...same base body, one of collectionId/categoryId/subcategoryId/formatUnitId/contentUnitId/brandId/strainId set to 999999...}'
```

- Responses (one per reference), all `404`:
  - `collectionId=999999` → `"Collection not found with ID: 999999"`
  - `categoryId=999999` → `"Category not found with ID: 999999"`
  - `subcategoryId=999999` → `"Subcategory not found with ID: 999999"`
  - `formatUnitId=999999` → `"FormatUnit not found with ID: 999999"`
  - `contentUnitId=999999` → `"ContentUnit not found with ID: 999999"`
  - `brandId=999999` → `"Brand not found with ID: 999999"`
  - `strainId=999999` → `"Strain not found with ID: 999999"`
- DB after: `SELECT count(*) FROM products` = 0 across all seven calls.
- Restored: nothing to restore (no row was ever created).

## Step N+2.4 - soft-deleted brand and strain (404)

```bash
UPDATE brands SET deleted_at = now() WHERE id=937;
curl ... -d '{"ocpc":"OCPC-CURL-SOFTDEL-BRAND", ..., "brandId": 937, ...}'
UPDATE brands SET deleted_at = NULL WHERE id=937;

UPDATE strains SET deleted_at = now() WHERE id=391;
curl ... -d '{"ocpc":"OCPC-CURL-SOFTDEL-STRAIN", ..., "strainId": 391, ...}'
UPDATE strains SET deleted_at = NULL WHERE id=391;
```

- Responses: both `404` — `"Brand not found with ID: 937"` and `"Strain not found with ID: 391"`.
- DB after: `products` count = 0 both times.
- Restored: `SELECT deleted_at FROM brands WHERE id=937` and
  `SELECT deleted_at FROM strains WHERE id=391` both empty (`NULL`) after the restoring `UPDATE`s.

## Step N+2.5 - subcategory belonging to another category (409)

```bash
curl -s -i -X POST localhost:8080/api/products -H "Content-Type: application/json" -d '{
  "ocpc": "OCPC-CURL-TAXONOMY", ..., "categoryId": 389, "subcategoryId": 377, ...
}'
```

- Response: `409`, `"Subcategory 377 does not belong to category 389"`.
- DB after: `products` count = 0.
- Restored: nothing to restore.

## Step N+2.6 - malformed/invalid payloads (400 × 3, D9 + validation)

```bash
curl -s -i -X POST localhost:8080/api/products -H "Content-Type: application/json" --data ''
curl -s -i -X POST localhost:8080/api/products -H "Content-Type: application/json" -d '{"ocpc":"OCPC-CURL-BLANK-TITLE","title":"   ", ...}'
curl -s -i -X POST localhost:8080/api/products -H "Content-Type: application/json" -d '{"ocpc":"OCPC-CURL-INVALID-VALUES", ..., "formatValue": 0, "thc": -1}'
```

- Responses:
  - Empty body → `400`, `{"field":"general","message":"Malformed or missing request body"}` (D9).
  - Blank `title` → `400`, `{"field":"title","message":"title must not be blank"}`.
  - `formatValue = 0` and `thc = -1` → `400`, two field errors:
    `{"field":"formatValue","message":"formatValue must be positive"}` and
    `{"field":"thc","message":"thc must not be negative"}`.
- Confirmed no response body contains SQL, a constraint name, or a stack trace (visually inspected
  all three bodies above; none contain `insert into`, `fk_products_`, `Exception`, or similar).
- DB after: `products` count = 0 across all three calls.
- Restored: nothing to restore.

## Step N+2.7 - omitted optional attributes persist NULL (201)

```bash
curl -s -i -X POST localhost:8080/api/products -H "Content-Type: application/json" -d '{
  "ocpc": "OCPC-CURL-NULLS", "title": "Nulls test", "collectionId": 322, "categoryId": 389,
  "subcategoryId": 376, "brandId": 937, "strainId": 391, "formatValue": 10, "formatUnitId": 452,
  "contentValue": 100, "contentUnitId": 453
}'
```

- Response: `201`, body echoes `"description":null,"isCoreProduct":null,"approved":null,"thc":null,"cbd":null,"enabled":null`.
- DB after: `SELECT enabled, approved, is_core_product, thc, cbd FROM products WHERE id=272` — all
  five columns `NULL` (not `false`/`0`), confirming no defaulting.
- Restored: `DELETE FROM products WHERE id=272;` — `products` count back to 0.

## Step N+2.8 - regression check on the D9 cross-cutting handler (`brands`/`dispensaries`)

```bash
curl -s -i -X POST localhost:8080/api/brands -H "Content-Type: application/json" --data ''
curl -s -i -X POST localhost:8080/api/dispensaries -H "Content-Type: application/json" --data ''
```

- Responses: both `400` with the static message
  `{"field":"general","message":"Malformed or missing request body"}` — the previously-500
  behavior D9 fixed, confirmed live against the running server (not just the automated
  `BrandControllerEndpointsTests`/`DispensaryEndpointsTests` regression tests from Section 7).
- DB after: no row created by either call (`brands` count remained exactly 1 — the one seeded
  fixture row `id=937`; `dispensaries` count remained 0).

## Restoration / post-test state

Every product row created during this step (`id=271`, `id=272`) was deleted immediately after its
owning test. Every reference row seeded in "Pre-test state" was then deleted in FK-safe order:

```sql
DELETE FROM strains WHERE id=391;
DELETE FROM seed_companies WHERE id=334;
DELETE FROM strain_types WHERE id=334;
DELETE FROM brands WHERE id=937;
DELETE FROM brand_types WHERE id=1078;
DELETE FROM subcategories WHERE id IN (376,377);
DELETE FROM categories WHERE id IN (389,390);
DELETE FROM collections WHERE id=322;
DELETE FROM units WHERE id IN (452,453);
```

| Table | Pre-test baseline | Post-test |
|---|---|---|
| `products` | 0 | 0 |
| `collections` | 0 | 0 |
| `categories` | 0 | 0 |
| `subcategories` | 0 | 0 |
| `units` | 0 | 0 |
| `brands` | 0 | 0 |
| `brand_types` | 0 | 0 |
| `strains` | 0 | 0 |
| `strain_types` | 0 | 0 |
| `seed_companies` | 0 | 0 |

- Database state: matches the pre-test baseline exactly on every table.
- Flyway state: unchanged, still `0.1.0` (no migration ran or was needed).
- Backend server state: stopped after the restoration step (`kill` on the `spring-boot:run`
  process); confirmed down via `curl --max-time 2 localhost:8080/actuator/health` failing to
  connect and `ps aux | grep DemoApplication` returning no process.
