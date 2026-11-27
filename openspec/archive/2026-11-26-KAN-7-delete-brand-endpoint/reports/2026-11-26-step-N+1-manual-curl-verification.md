# Step N+1 — Manual Endpoint Testing (curl) Report

**Change**: KAN-7-delete-brand-endpoint (`DELETE /api/brands/{brandId}`)
**Date**: 2026-11-26
**Agent**: opsx-apply (all commands executed by the agent; the user was never asked to run curl)

## 12.1 Infrastructure and baseline

- Backend started with `mvn spring-boot:run` (branch `feat/KAN-7-delete-brand-endpoint`);
  `curl -s localhost:8080/actuator/health` reported `UP` (PostgreSQL component `UP`).
- Database used by the running app: `demo_kan7deletebrandendpoint` (from `env.properties`).
- Pre-test baseline (`tmp/` scratch + queries below): `brands` 0, `products` 0, `brand_types` 0;
  `flyway_schema_history` 2 rows (`0.1.0`, `0.1.1`).

## 12.2 Seeded data (exact ids recorded for 12.9 cleanup)

Seeded via SQL (`psql`, user `develop`):

| Table | Rows | Ids |
|-------|------|-----|
| `brand_types` | `curl-grower` | 1 |
| `brands` | `curl-free` (no products) | 1 |
| `brands` | `curl-guarded` (will own a live product) | 2 |
| `collections` | `curl-collection` | 1 |
| `categories` | `curl-category` | 1 |
| `subcategories` | `curl-sub` | 1 |
| `units` | `curl-gram`, `curl-milligram` | 1, 2 |
| `strain_types` | `Curl Sativa` | 1 |
| `seed_companies` | `Curl Seeds` | 1 |
| `strains` | `CURL-UCPC-1` | 1 |
| `products` | `CURL-OCPC-1` → brand 2 | 1 |

Verify: `GET /api/brands/1` returned `200` with the `curl-free` brand.
OpenAPI (`GET /api-docs`) shows `DELETE /api/brands/{id}` with responses `200`, `404`, `409`
(no `204` drift, no `400` for the guard path).

## 12.3 Happy path (200, soft delete, then restore)

Command:

```bash
curl -i -X DELETE localhost:8080/api/brands/1
```

Response: `HTTP/1.1 200` with body

```json
{"data":{"id":1,"name":"curl-free","description":"curl brand free of products","stateLicense":"CURL-01","brandType":"curl-grower","adminId":7}}
```

DB after: row `brands id=1` still present (`SELECT count(*)` total still 2) with
`deleted_at IS NOT NULL`. Restoration: `UPDATE brands SET deleted_at = NULL WHERE id = 1`
(`UPDATE 1`).

## 12.4 Not found (404, exact message, no further change)

```bash
curl -i -X DELETE localhost:8080/api/brands/999999
# -> HTTP/1.1 404
{"timestamp":"...","status":404,"path":"/api/brands/999999",
 "errors":[{"field":"general","message":"Brand not found with ID: 999999"}]}
```

Already-soft-deleted brand: `DELETE /api/brands/1` (`200`), then `DELETE /api/brands/1`
again → `HTTP/1.1 404` with `errors:[{"field":"general","message":"Brand not found with ID: 1"}]`
— the second delete reports `404`, not `409`, and applies no further change (`deleted_at`
remains set from the first delete). Restoration: `UPDATE brands SET deleted_at = NULL WHERE id = 1`.

## 12.5 Brand in use (409, not 400, no soft delete, then product removed)

```bash
curl -X DELETE localhost:8080/api/brands/2
# -> HTTP 409
{"timestamp":"...","status":409,"path":"/api/brands/2",
 "errors":[{"field":"general","message":"Brand with ID 2 cannot be deleted because it is used by products"}]}
```

DB after: `brands id=2` has `deleted_at = NULL` (not soft-deleted) and
`GET /api/brands/2` still returns `200`. The seeded product was kept for the 12.8 checks
and removed in 12.9 (see below) — no product references the brand at report time.

## 12.6 Referenced only by soft-deleted products (200)

Seeded brand `curl-softref` (id 3) + product `CURL-OCPC-2` (id 2, brand 3), then
`UPDATE products SET deleted_at = now() WHERE id = 2` via native SQL.

```bash
curl -X DELETE localhost:8080/api/brands/3
# -> HTTP 200
{"data":{"id":3,"name":"curl-softref","description":"brand refd only by soft-deleted product","stateLicense":"CURL-03","brandType":"curl-grower","adminId":7}}
```

DB after: `brands id=3` has `deleted_at IS NOT NULL` — the native guard's
`AND p.deleted_at IS NULL` clause is proven end to end. Restoration: brand 3 restored
(`deleted_at = NULL`) and product 2 hard-deleted (`DELETE 1`).

## 12.7 Non-numeric id (400, existing handler, no lookup)

```bash
curl -i -X DELETE localhost:8080/api/brands/not-a-number
# -> HTTP/1.1 400
{"timestamp":"...","status":400,"path":"/api/brands/not-a-number",
 "errors":[{"field":"id","message":"Invalid value for parameter: id"}]}
```

No brand lookup ran; no brand mutated.

## 12.8 Atomicity (catalog unchanged after every rejection)

Before: `SELECT count(*) FROM brands` → 3; all three brands `deleted_at = NULL`.
Executed `400` (`not-a-number`), `404` (`999999`), `409` (brand 2) rejections.
After: count still 3; all three brands `deleted_at = NULL`; `GET /api/brands/2` → `200`
(guarded brand still listed). Pre/post identical for every rejection path.

## 12.9 Database restored, backend stopped

Removed exactly the seeded rows (FK-safe order, native `DELETE FROM`):

```sql
DELETE FROM products; DELETE FROM strains; DELETE FROM seed_companies;
DELETE FROM strain_types; DELETE FROM brands; DELETE FROM subcategories;
DELETE FROM categories; DELETE FROM collections; DELETE FROM units;
DELETE FROM brand_types;
```

Post-restore counts: `brands` 0, `products` 0, `brand_types` 0, `collections` 0,
`strains` 0 — equal to the 12.1 baseline; `flyway_schema_history` still 2 rows
(unchanged). Backend process stopped; `curl localhost:8080/actuator/health` now
refuses connection (`000`) as expected.

## Outcome

All manual curl tests pass (`200` happy path + envelope + soft-delete semantics,
`404` × 2 with exact messages, `409` with the exact message and NOT `400`,
soft-deleted-product-only `200`, `400` type-mismatch, atomicity on all rejections),
the database is restored to its pre-test state, and this report exists.
Step N+2 is complete.
