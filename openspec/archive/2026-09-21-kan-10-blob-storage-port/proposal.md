## Why

Four queued asset stories (`brand_images`, `brand_videos`, `strain_images`, `product_images`) are blocked on object storage: their FK tables already exist in Flyway `V0.1.0` but have no write path, and `product_images` was explicitly deferred by KAN-8 and listed as a Non-Goal by KAN-14. Without a shared port each asset story would reinvent key naming, TTL handling and error translation, and the `products/`-filtered S3 → SQS notification contract would break on the first divergent folder name. As the direct successor of KAN-14, this change finally consumes the `aws.s3.bucket` and `aws.s3.presign-ttl` properties KAN-14 declared with zero consumers, delivering a developer-facing foundation slice — a storage-agnostic `BlobStorage` port plus a first AWS S3 implementation — that unblocks all asset upload stories while keeping binary traffic off the Spring application (presigned PUT is local HMAC, zero network I/O).

## What Changes

- Add `BlobType` enum (`domain/models`) with six values — `BRAND_IMAGE`, `BRAND_VIDEO`, `STRAIN_IMAGE`, `STRAIN_VIDEO`, `PRODUCT_IMAGE`, `PRODUCT_VIDEO` — each owning a distinct nested prefix (`brands/images/`, `brands/videos/`, `strains/images/`, `strains/videos/`, `products/images/`, `products/videos/`) plus `static boolean isCanonicalKey(String)` derived from the same prefix constants (seventh blob type = one-line enum change).
- Add `BlobUploadTarget` immutable record (`domain/models`): `key`, `url` (`URI`), `method` (`HttpMethod.PUT`), `expiresAt` (`now + aws.s3.presign-ttl`).
- Add `BlobStorage` port (`domain/repositories`) with exactly two operations: `BlobUploadTarget createUploadTarget(BlobType blobType)` and `void remove(Set<String> keys)`.
- Add `BlobStorageException` (`domain/repositories`, `extends RuntimeException`, carries `failedKeys` for partial batch failures); no `software.amazon.awssdk` type ever escapes the port.
- Add `S3BlobStorageAdapter` (`infrastructure/adapters/storage`, `@Component`) implementing the port via the KAN-14 `S3Client`/`S3Presigner` beans: presigned PUT against the single configured bucket, opaque key `<prefix><32 lowercase hex UUID without dashes>` generated inside the port, `expiresAt` from `Instant.now().plus(ttl)`.
- Harden `remove`: `null` keys or any non-canonical key throws `IllegalArgumentException` before any store call (fail-fast on the whole set); empty set is a no-op with no store call; unknown canonical keys are a silent no-op (idempotent); batches chunk at 1000 (S3 `DeleteObjects` hard limit); per-key `errors()` collected into `BlobStorageException`.
- Harden `AwsS3Properties`: add `@NotBlank` to `bucket` (now load-bearing). No new configuration property.
- Logging discipline: log blob type and key on issue/remove, warn with failed keys on partial failure; presigned URLs are never logged at any level and never placed in exception messages or metric tags.
- Docs: `README.md` (blob-type/prefix table, key convention, LocalStack upload walkthrough) and `docs/backend-standards.md` (register `infrastructure/adapters/storage/` and the `BlobStorage` port convention).

## Capabilities

### New Capabilities

- `blob-storage`: storage-agnostic blob upload-target and removal port — six blob types mapped to distinct nested prefixes in a single bucket (`aws.s3.bucket`), port-generated opaque keys, presigned `PUT` URLs valid for `aws.s3.presign-ttl`, validated idempotent batch removal with 1000-key chunking, all failures reported as `BlobStorageException` with no vendor-type leakage, no HTTP endpoint.

### Modified Capabilities

- `aws-s3-integration`: reconcile the KAN-14 scope fence (`"SHALL NOT introduce a domain port, storage adapter…"`) — that sentence was written for KAN-14 and its requirement text must be updated so the `S3Client`/`S3Presigner` beans are now consumed by the first storage adapter. No bean behavior changes.

## Impact

- **Code**: new `domain/models/BlobType.java`, `domain/models/BlobUploadTarget.java`, `domain/repositories/BlobStorage.java`, `domain/repositories/BlobStorageException.java`, `infrastructure/adapters/storage/S3BlobStorageAdapter.java` (the only `software.amazon.awssdk.*` importer outside `infrastructure/config`); one-line `AwsS3Properties` hardening. No Flyway migration, no entity, no repository, no service, no controller. DDD layering: application depends on the domain port; adapter implements it (DIP); prefixes live once in `BlobType` (DRY).
- **Config**: single bucket `aws.s3.bucket` (default `develop-assets`) for images and videos; TTL `aws.s3.presign-ttl` (default `PT15M`). Reuses KAN-14 beans; presign is local crypto (target p95 < 5 ms), `remove(n)` costs `ceil(n/1000)` round-trips.
- **Systems**: `PRODUCT_*` keys must stay under `products/` — the S3 → SQS notification on `develop-products-assets-events-queue` filters on that prefix; brand/strain assets deliberately do not match. `GlobalExceptionHandler.handleGeneric` already covers `BlobStorageException` → `500` with a fixed body, so no presentation change is needed.
- **Constraints**: no AWS types outside `infrastructure/config` + the adapter; never log presigned URLs; callers never supply key material (zero path-traversal surface).

## Decisions

- **D1 — Presigned PUT, not POST policy**: simplest contract (`url` + `method` + `key`), matches the bucket's existing CORS rule (`GET, PUT`). Trade-off accepted: no server-side size or content-type enforcement; hardening follow-up is raised, not built.
- **D2 — Nested prefixes** (`products/images/`, not `product-images/`): preserves the existing `products/` SQS filter and matches how S3 lifecycle/retention rules are scoped.
- **D3 — Opaque UUID key, no file extension**: keeps the signature `createUploadTarget(BlobType)` with zero caller input to sanitise; key is safe to store verbatim in a `varchar` URL column. Trade-off accepted: generic S3 content type may force download instead of render; mitigations are client-sent `Content-Type` on PUT or CDN headers.
- **D4 — `org.springframework.http.HttpMethod` as the `method` type**, not a bespoke one-constant enum: a protocol type, not a vendor type, so storage-agnosticism holds; the project already tolerates framework types in `domain/models`. Reversible for a 6-line enum if the team wants a strictly framework-free domain.
- **D5 — `expiresAt` included** beyond the ticket's three fields: callers need it to decide when to re-request; adding it later would break every consumer. No injected `Clock` (YAGNI; tests use a tolerance window).

## Non-Goals

Explicitly out of scope (raised as follow-up tickets, not built):

- Any HTTP endpoint wrapping the port.
- Any persistence entity, repository, service, or Flyway migration.
- Upload size or content-type restriction.
- Orphan-blob reclamation (issued-but-never-uploaded, uploaded-but-DB-write-failed).
- Second storage implementation (Azure, local filesystem).
- Bucket public-read hardening and CORS scoping (pre-existing production blockers from KAN-14).

## Open Assumptions (require sign-off, see enriched story §8)

- **8.1** `strain_videos` and `product_videos` tables do not exist in `V0.1.0` — `STRAIN_VIDEO`/`PRODUCT_VIDEO` are storable but not persistable; confirm they are intentional forward declarations with migrations as separate tickets.
- **8.2** `DISPENSARY_IMAGE` is absent from the enum although `dispensary_images` exists and `Dispensary` is a full aggregate — confirm omission is intended, not an oversight.
- **8.3** Who calls `remove`, and when — brands/dispensaries use soft delete; deleting blobs on soft delete breaks its contract, never deleting them grows storage forever. Product decision; KAN-10 only supplies the capability.
- **8.4** Orphan blobs are not handled — needs an S3 lifecycle rule or reconciliation job as follow-up.
- **8.5** No authentication exists in the codebase — an auth story must precede any production endpoint wrapping this port (same caveat as KAN-8).
- **8.6** Single bucket for images and videos — videos may later want different lifecycle/storage-class/CDN behaviour; prefix-scoped rules cover this without a second bucket.
- **8.7** No injected `Clock` — revisit only if deterministic time becomes necessary elsewhere.
- **8.8** `remove` is not transactional with the database — recommended ordering is DB row first, then blob (a leaked blob is cheaper than a broken image link).

## Risks

- **Unbounded upload** — presigned PUT carries no `content-length-range` or `Content-Type` condition; any URL holder can upload arbitrary bytes to an image key. Accepted in KAN-10 (D1) → mitigation is a raised hardening follow-up (presigned POST policy, size lifecycle guard, or post-upload SQS validator).
- **World-readable bucket** — `PublicRead` ACL plus `s3:GetObject` grant to `*`; every blob is publicly downloadable by key. Acceptable for public marketing assets with 122-bit unguessable keys (security by obscurity); bucket must never hold private content; KAN-14 production hardening still pending.
- **Orphan blobs** — every issued-but-unused target and every upload whose DB write fails leaves a paid-for object with no reference. No reclamation in KAN-10 → follow-up ticket plus orphan-rate metric.
- **No auth on future endpoint** — once wrapped, anyone can mint upload URLs. Auth story must land before production exposure.
- **`strain_videos` / `product_videos` tables missing** — two of six blob types have no persistence target today; asset stories for them need migrations first.
- **`DISPENSARY_IMAGE` omission** — if unintended, dispensary assets will need a seventh enum value (one line) plus spec update.
- **Soft-delete vs `remove` ordering** — wrong product decision here either leaks storage forever or makes deletes irreversible; must be decided before asset stories wire `remove` into delete flows.
