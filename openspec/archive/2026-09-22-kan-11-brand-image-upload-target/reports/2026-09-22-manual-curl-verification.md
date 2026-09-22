# Manual curl verification — Brand image upload target (2026-09-22)

Change: `kan-11-brand-image-upload-target` (branch `feat/kan-11-brand-image-upload-target`).

Environment: app via `mvn spring-boot:run` on `:8080` against local Postgres `demo` DB
(migrated with `mvn flyway:migrate`: `V0.1.0` + `V0.1.1`), LocalStack S3 on `:4566`
(bucket `develop-assets`, CORS narrowed to `PUT` / `content-type` / `MaxAge 3000` per
`localstack-resources.yml`), `AWS_ENDPOINT_URL_S3=http://localhost:4566`,
`AWS_S3_PATH_STYLE_ACCESS=true`, credentials `test`/`test`.

Seed: `INSERT INTO brand_types (name) VALUES ('grower')`, then
`POST /api/brands` created brand `curlbrand` with `id: 1`.

## 1. Happy path — POST returns 201 + no-store + exactly three String fields

```
$ curl -i -X POST http://localhost:8080/api/brands/1/images
HTTP/1.1 201
Cache-Control: no-store
Content-Type: application/json

{"data":{
  "uploadUrl":"http://localhost:4566/develop-assets/brands/images/142f33a41f1d4522a2ae7d6712a14be1?X-Amz-Algorithm=AWS4-HMAC-SHA256&...",
  "uploadMethod":"PUT",
  "expiresAt":"2026-09-22T16:40:40.777535476Z"}}
```

PASS (AC1, AC10): `201`, `Cache-Control: no-store`, exactly
`uploadUrl`/`uploadMethod`/`expiresAt` — no `id`/`brandId`/`imageKey`/`status`.

## 2. Row persisted as PENDING with the issued key

```
$ psql -c "SELECT id, brand_id, image_key, status FROM brand_images;"
 id | brand_id |                   image_key                    | status
----+----------+------------------------------------------------+---------
  1 |        1 | brands/images/142f33a41f1d4522a2ae7d6712a14be1 | PENDING
```

PASS (AC2).

## 3. Credential-free PUT stores the object at the persisted key (AC3)

A second `POST` issued a fresh target; `curl -X PUT --upload-file` with **no AWS
credentials** returned `put=200`, and `s3api head-object` confirmed the object at exactly the
persisted key (`ContentLength: 18`, `ContentType: binary/octet-stream`):

```
put=200
KEY=brands/images/6299d85db8eb47379c510937bf911bc5
{"AcceptRanges": "bytes", "ContentLength": 18, "ETag": ..., "ContentType": "binary/octet-stream", ...}
```

PASS (AC3, AC4 — distinct URL/key/row on the second call).

## 4. Error matrix

| Call | Result |
|---|---|
| `POST /api/brands/999999/images` | `404` (AC5) |
| `POST /api/brands/abc/images` | `400` (AC6) |
| `PUT /api/brands/1/images` | `405` (AC13) |
| `OPTIONS http://localhost:4566/develop-assets/brands/images/x` + `Origin` + `Access-Control-Request-Method: PUT` | `200` (bucket answers preflight; AC14 at HTTP level) |

All PASS.

## Cleanup note

Verification rows (`brand_images` ids 1–3, brand `curlbrand`, uploaded objects) live only in the
local `demo` DB / local bucket — both are throwaway developer state, not repository artifacts.
The uploaded browser/curl objects were removed from the bucket after verification where practical.
