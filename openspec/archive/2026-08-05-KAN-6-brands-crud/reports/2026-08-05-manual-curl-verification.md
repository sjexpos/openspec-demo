# KAN-6 Brands CRUD — Manual Endpoint (curl) Verification

Date: 2026-08-05
Server: `mvn spring-boot:run` on `demo` PostgreSQL DB (`localhost:8080`).

## Pre-test state (before seeding)
- `demo` DB: `brands` = 0 rows, `brand_types` = 0 rows.
- Seeded one lookup row for POST/PATCH resolution:
  `INSERT INTO brand_types (name) VALUES ('grower');` → `brand_types` = 1.

## 9.2 GET /api/brands
```bash
curl -s http://localhost:8080/api/brands
```
- Response: `{"data":[]}` → `HTTP 200` (empty list, `DataResponse` wrapper). ✓

## 9.3 POST /api/brands (valid)
```bash
curl -s -X POST http://localhost:8080/api/brands -H "Content-Type: application/json" \
  --data '{"name":"CurlTestBrand","description":"brand seeded for curl tests","email":"curltest@yopmail.com","stateLicense":"CAL-2026-001","brandTypeName":"grower","logoImageUrl":"https://example.com/logo.png","adminId":7,"enabled":true}'
```
- Response: `201 Created`, `{"data":{"id":1,"name":"CurlTestBrand",...}}`.
- Restored afterwards: `DELETE FROM brands WHERE name='CurlTestBrand'`.

## 9.4 GET /api/brands/1
```bash
curl -s http://localhost:8080/api/brands/1
```
- Response: `200 OK` with full brand data (id, name, email, stateLicense, brandTypeName, ...).

## 9.5 PATCH /api/brands/1 (partial body)
```bash
curl -s -X PATCH http://localhost:8080/api/brands/1 -H "Content-Type: application/json" --data '{"description":"updated by curl"}'
```
- Response: `200 OK`; only `description` changed to "updated by curl", `name` remained "CurlTestBrand"
  (non-provided fields left unchanged — spec partial update scenario).
- Bonus (D4): `PATCH ... {"enabled":false}` → `200`, `enabled` set to `false`.

## 9.6 Error cases
| Case | Command body | Result |
|---|---|---|
| POST missing required field | `{"email":"x@y.com",...}` (no name/description) | `400` with `errors[].field=name`,`description` |
| POST invalid email | `"email":"not-an-email"` | `400` with `errors[].field=email` |
| GET unknown id | `GET /api/brands/999999` | `404` `{ "field":"general","message":"Brand not found with ID: 999999" }` |
| PATCH unknown id | `PATCH /api/brands/999999` | `404` `"Brand not found with ID: 999999"` |
| POST unknown brandTypeName | `"brandTypeName":"does-not-exist"` | `404` `"Invalid brand type: does-not-exist"` |

Verification: after the error cases only the single created brand row existed (`SELECT id,name FROM brands` →
`1 | CurlTestBrand`), confirming 404 paths did not create rows.

## 9.7 Restoration / post-test state
```bash
DELETE FROM brands  WHERE name='CurlTestBrand';
DELETE FROM brand_types WHERE name='grower';
```
- Post-check (`demo` and `tests`): `brands` = 0, `brand_types` = 0 → DB matches pre-test baseline.