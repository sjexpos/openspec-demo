# KAN-10 — Enriched User Story: Create generic blob store

## Executive Summary

Create a **storage-agnostic blob-store capability** behind a domain port (`BlobStore`), with **AWS S3 as the first
implementation** (`S3BlobStoreAdapter`). It exposes exactly the two operations the ticket asks for:

1. **Get an upload URL + HTTP method + key for a blob type** — a presigned S3 `PUT` URL (browser/app uploads the
   bytes directly to S3, keeping binaries off the JVM heap and out of the request path).
2. **Remove a set of keys** — batch delete of previously issued keys.

Initial blob types (6): `brand images`, `brand videos`, `strain images`, `strain videos`, `product images`,
`product videos`. Keys live in the **single assets bucket** and are composed as
`<prefix-by-blob-type><32-hex-uuid>[.<extension>]`, e.g. `products/images/9f3ac41be2d7489aa1c7f0e55b6d3c2a.jpg`.

This ticket creates **no REST endpoint and no DB migration**: it is a domain/infrastructure capability that the
upcoming per-aggregate upload endpoints (brand/strain/product images & videos) will consume. It builds directly on
the KAN-14 foundation (`S3Client` + `S3Presigner` singleton beans, typed `aws.*` properties, LocalStack override).

---

## 1. User story (revised)

> *As a **backend developer**,
> I want a **generic `BlobStore` port with an S3-backed implementation that issues presigned upload URLs per blob
> type and deletes sets of keys**,
> so that **brand, strain and product image/video uploads share one abstract, testable storage seam instead of
> each feature wiring the AWS SDK directly**.*

### Business value

- Every media column/relation in `docs/data-model.md` (`brand_images`, `brand_videos`, `strain_images`,
  `product_images`, …) currently stores a URL string with **no producer**: this ticket provides the producer.
- Direct-to-S3 uploads (presigned `PUT`) keep large binaries out of the request path — a decision that is
  expensive to retrofit once upload endpoints exist.
- The port/adapter split (DIP) means a second backend (GCS, Azure Blob, local Cieco…) is a new adapter class,
  not a rewrite of every consumer (OCP).

### Acceptance criteria

1. `BlobStore.generateUploadUrl(BlobType, String contentType)` returns a `PresignedUpload(url, httpMethod, key,
   expiresAt)` with `httpMethod = "PUT"`, `key` starting with the blob type's prefix, and `expiresAt ≈ now +
   aws.s3.presign-ttl`.
2. The returned `url`, used with an HTTP `PUT` of bytes matching `contentType` and **no credentials**, stores the
   object under `key` in the configured bucket (proven by a real LocalStack round trip).
3. `contentType` not in the blob type's allowlist (images: `image/jpeg`, `image/png`, `image/webp`; videos:
   `video/mp4`, `video/webm`) → `StorageException` (business-rule rejection), no S3 call performed.
4. `BlobStore.deleteObjects(Set<String> keys)` with an empty/null set is a **no-op** (no S3 call).
5. `deleteObjects` with keys deletes **exactly those objects** (verified by `headObject` → 404 afterwards) in a
   **single `DeleteObjects` call per ≤1000-key chunk**.
6. `deleteObjects` with a key whose prefix matches **no known `BlobType`** → `StorageException`, **nothing is
   deleted** (fail-fast validation before any S3 call).
7. S3-side failure (partial `Errors` in `DeleteObjectsResponse`, SDK exception) → `StorageException` with a
   non-leaking message; success path returns normally.
8. Application code (services, controllers) depends **only on the `BlobStore` port**; the AWS SDK appears only in
   `infrastructure/` (verified by package inspection / ArchUnit-style grep, no `software.amazon.awssdk` import
   outside `infrastructure/config` and `infrastructure/adapters`).
9. `mvn verify` green with LocalStack up; new classes ≥ 90% line/branch coverage; no hardcoded credentials,
   bucket names, or endpoints in `src/main`.

---

## 2. Contracts (no new REST endpoint in this ticket)

| Item | Value |
|------|-------|
| REST endpoint | **None** — internal capability; per-aggregate upload endpoints are follow-ups |
| DB migration | **None** — no JPA entity; keys are opaque strings persisted later by consumers |
| Config additions | **None** — reuses `aws.s3.bucket` and `aws.s3.presign-ttl` (KAN-14) |

### 2.1 `BlobType` (domain enum)

| Constant | Prefix (bucket-relative) | Kind | Accepted `contentType` values |
|----------|--------------------------|------|-------------------------------|
| `BRAND_IMAGE` | `brands/images/` | image | `image/jpeg`, `image/png`, `image/webp` |
| `BRAND_VIDEO` | `brands/videos/` | video | `video/mp4`, `video/webm` |
| `STRAIN_IMAGE` | `strains/images/` | image | `image/jpeg`, `image/png`, `image/webp` |
| `STRAIN_VIDEO` | `strains/videos/` | video | `video/mp4`, `video/webm` |
| `PRODUCT_IMAGE` | `products/images/` | image | `image/jpeg`, `image/png`, `image/webp` |
| `PRODUCT_VIDEO` | `products/videos/` | video | `video/mp4`, `video/webm` |

Extension mapping for key suffix: `image/jpeg → .jpg`, `image/png → .png`, `image/webp → .webp`,
`video/mp4 → .mp4`, `video/webm → .webm`.

> Prefixes are **stable identifiers**, not configuration: consumers persist full keys/URLs, so renaming a prefix
> later orphans stored objects. New blob types are added as new enum constants (OCP), never by editing existing
> prefixes.

### 2.2 `BlobStore` port + `PresignedUpload` value object

```java
// domain/storage/BlobStore.java
public interface BlobStore {

  /**
   * Issues a presigned upload URL for the given blob type.
   *
   * @param blobType the blob category, determines the key prefix and allowed content types
   * @param contentType MIME type the client will upload, e.g. "image/jpeg"; enforced in the signature
   * @return presigned upload descriptor (url, HTTP method, key, expiry)
   * @throws StorageException if the content type is not allowed for the blob type
   */
  PresignedUpload generateUploadUrl(BlobType blobType, String contentType);

  /**
   * Deletes the given keys. Empty or null input is a no-op.
   *
   * @param keys S3 keys previously issued by generateUploadUrl
   * @throws StorageException if any key has an unknown prefix or the backend reports a failure
   */
  void deleteObjects(Set<String> keys);
}
```

```java
// domain/storage/PresignedUpload.java
public record PresignedUpload(String url, String httpMethod, String key, Instant expiresAt) {}
```

Key format: `<prefix><32 lowercase hex chars><extension>`, e.g.
`products/images/9f3ac41be2d7489aa1c7f0e55b6d3c2a.jpg`. The 32-hex segment is
`UUID.randomUUID().toString().replace("-", "")` (122 bits of entropy; collision probability negligible, no
existence check needed — one fewer S3 round trip).

---

## 3. Files to create / modify

| # | File | Action | Layer |
|---|------|--------|-------|
| 1 | `domain/storage/BlobType.java` | **Create** — enum with `prefix`, `kind`, allowlist + `extensionFor(contentType)` | Domain |
| 2 | `domain/storage/PresignedUpload.java` | **Create** — immutable record `(url, httpMethod, key, expiresAt)` | Domain |
| 3 | `domain/storage/BlobStore.java` | **Create** — port interface (see §2.2) | Domain |
| 4 | `application/exceptions/StorageException.java` | **Create** — extends `DomainException` (mirrors `ConflictException`/`NotFoundException`) | Application |
| 5 | `infrastructure/adapters/S3BlobStoreAdapter.java` | **Create** — implements `BlobStore` via `S3Presigner` + `S3Client` | Infrastructure |
| 6 | `src/test …/domain/storage/BlobTypeTests.java` | **Create** — unit tests, no Spring, no network | Test |
| 7 | `src/test …/infrastructure/adapters/S3BlobStoreAdapterTests.java` | **Create** — unit tests, mocked `S3Client`/`S3Presigner` | Test |
| 8 | `src/test …/integration/adapters/S3BlobStoreAdapterIntegrationTests.java` | **Create** — real LocalStack round trip | Test |
| 9 | `README.md` | **Modify** — short "Blob storage" section (port, blob types, LocalStack workflow) | Docs |

**Not modified:** `presentation/**` (no endpoint), `domain/models/**`, `flyway/*` (no migration),
`infrastructure/config/S3Config.java` / `AwsS3Properties.java` (beans and `aws.s3.*` properties already exist via
KAN-14), `application.yml` files (no new properties).

---

## 4. Implementation details per layer

### 4.1 Domain — `BlobType.java`

```java
public enum BlobType {
  BRAND_IMAGE("brands/images/", Kind.IMAGE),
  BRAND_VIDEO("brands/videos/", Kind.VIDEO),
  STRAIN_IMAGE("strains/images/", Kind.IMAGE),
  STRAIN_VIDEO("strains/videos/", Kind.VIDEO),
  PRODUCT_IMAGE("products/images/", Kind.IMAGE),
  PRODUCT_VIDEO("products/videos/", Kind.VIDEO);

  public enum Kind { IMAGE, VIDEO; }

  private final String prefix;
  private final Kind kind;

  // + getters, isContentTypeAllowed(String), extensionFor(String),
  // + static fromPrefix(String key) returning Optional<BlobType>
}
```

Rules:

- `isContentTypeAllowed` is case-insensitive on the MIME value, exact match against the kind's allowlist (§2.1);
  `null`/blank → not allowed.
- `fromPrefix(key)` matches the key against every constant's prefix; used by the adapter's delete guard.
- The enum carries **no AWS types** — it must compile with zero `software.amazon.awssdk` imports (port purity).

### 4.2 Domain — `PresignedUpload.java` and `BlobStore.java`

- `PresignedUpload` is a plain `record`; `expiresAt` is an `Instant` computed as
  `Instant.now().plus(properties.s3().presignTtl())` by the adapter (single clock call, testable via assertion
  window rather than a fixed clock — no `Clock` abstraction needed for one call site).
- `BlobStore` depends only on domain types (`BlobType`, `PresignedUpload`) and the application exception —
  never on SDK types (DIP: high-level policy owns the abstraction).

### 4.3 Application — `StorageException.java`

```java
public class StorageException extends DomainException {
  public StorageException(String message) {
    super(message);
  }

  public StorageException(String message, Throwable cause) {
    super(message);
    initCause(cause);
  }
}
```

> Check `DomainException` first: if it already supports `(message, cause)`, mirror `ConflictException`'s
> constructors exactly instead of calling `initCause`. Message wording must never include credentials, bucket
> internals, or SDK stack detail — e.g. `"Failed to delete blobs"`, `"Content type not allowed for blob type"`.

### 4.4 Infrastructure — `S3BlobStoreAdapter.java`

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class S3BlobStoreAdapter implements BlobStore {

  private static final int DELETE_BATCH_LIMIT = 1000;

  private final S3Presigner presigner;
  private final S3Client s3Client;
  private final AwsS3Properties properties;

  @Override
  public PresignedUpload generateUploadUrl(BlobType blobType, String contentType) {
    if (!blobType.isContentTypeAllowed(contentType)) {
      throw new StorageException(
          "Content type " + contentType + " is not allowed for blob type " + blobType);
    }
    String key = blobType.getPrefix() + randomHex() + blobType.extensionFor(contentType);
    PutObjectRequest put =
        PutObjectRequest.builder()
            .bucket(this.properties.s3().bucket())
            .key(key)
            .contentType(contentType)
            .build();
    PresignedPutObjectRequest presigned =
        this.presigner.presignPutObject(
            b -> b.putObjectRequest(put).signatureDuration(this.properties.s3().presignTtl()));
    log.info("Issued upload URL for blob type {} key {}", blobType, key);
    return new PresignedUpload(
        presigned.url().toString(), "PUT", key, Instant.now().plus(this.properties.s3().presignTtl()));
  }

  @Override
  public void deleteObjects(Set<String> keys) {
    if (keys == null || keys.isEmpty()) {
      return; // no-op, no S3 call
    }
    List<String> ordered = List.copyOf(keys);
    for (String key : ordered) {
      if (BlobType.fromPrefix(key).isEmpty()) {
        throw new StorageException("Unknown blob key prefix for key: " + safe(key));
      }
    }
    for (List<String> chunk : partition(ordered, DELETE_BATCH_LIMIT)) {
      DeleteObjectsRequest req =
          DeleteObjectsRequest.builder()
              .bucket(this.properties.s3().bucket())
              .delete(Delete.builder().objects(chunk.stream().map(k ->
                  ObjectIdentifier.builder().key(k).build()).toList()).build())
              .build();
      try {
        DeleteObjectsResponse res = this.s3Client.deleteObjects(req);
        if (res.hasErrors() && !res.errors().isEmpty()) {
          throw new StorageException("Failed to delete " + res.errors().size() + " blob(s)");
        }
      } catch (StorageException e) {
        throw e;
      } catch (RuntimeException e) {
        throw new StorageException("Failed to delete blobs", e);
      }
      log.info("Deleted {} blob(s)", chunk.size());
    }
  }
}
```

Non-obvious details the implementer must not miss:

1. **Presigning is a local HMAC operation** (no network) using the injected singleton `S3Presigner` from
   KAN-14 — never build a presigner per call.
2. **`contentType` is embedded in the `PutObjectRequest`** so S3 enforces it on `PUT`: a client uploading
   `video/mp4` bytes against an `image/jpeg` URL gets `403 SignatureDoesNotMatch`, not a silent mislabeled
   object.
3. **Never log the presigned URL** — it is a bearer capability. Log blob type + key only.
4. **Validate all keys before deleting any** (fail-fast loop first, S3 calls second) so one foreign key cannot
   cause a partial delete.
5. **`safe(key)`** in the guard message must truncate/escape the key (keys originate from callers; keep the
   message informative but bounded, e.g. first 128 chars).
6. **Size limits cannot be enforced in a SigV4 presigned `PUT`** (no content-length condition, unlike presigned
   `POST` policies) — size is documented as a follow-up (§9), enforced client-side until then.
7. License header + `@Slf4j` per `docs/backend-standards.md`; `mvn spotless:apply` before committing.

---

## 5. Testing plan (TDD — failing test first; `should_[expected]_when_[condition]`, AAA, ≥ 90%)

### 5.1 Domain unit tests — `domain/storage/BlobTypeTests.java` (no Spring, no network)

1. `should_exposeSixBlobTypes_when_enumIsQueried` — exactly the 6 constants with the prefixes in §2.1.
2. `should_allowImageTypes_when_kindIsImage` / `should_allowVideoTypes_when_kindIsVideo` — allowlist membership.
3. `should_rejectMismatchedType_when_imageTypeUsedForVideo` (and vice versa) — `isContentTypeAllowed` false.
4. `should_rejectNullOrBlank_when_contentTypeIsMissing` — null/empty/blank → false, no exception.
5. `should_mapExtension_when_contentTypeIsKnown` — `image/jpeg → .jpg`, `video/mp4 → .mp4`, … (full table).
6. `should_resolveType_when_keyHasKnownPrefix` / `should_returnEmpty_when_prefixIsUnknown` — `fromPrefix`
   round trip for every prefix plus a foreign key (e.g. `other/x`).

### 5.2 Adapter unit tests — `infrastructure/adapters/S3BlobStoreAdapterTests.java` (mocked SDK)

Mock `S3Presigner`, `S3Client`, and `AwsS3Properties` (Mockito); never touch the network.

1. `should_returnPutMethodAndPrefixedKey_when_contentTypeIsAllowed` — stub presigner to return a fixed URL;
   assert `httpMethod == "PUT"`, key matches `^products/images/[0-9a-f]{32}\.jpg$`, `expiresAt` within
   `[now, now + ttl + 60s]`.
2. `should_throwStorageException_when_contentTypeIsNotAllowed` — no presigner interaction
   (`verifyNoInteractions`), `deletedAt`-style state untouched.
3. `should_embedContentType_when_presigning` — capture the `PutObjectRequest` via the presign lambda/`ArgumentCaptor`;
   assert bucket, key, and `contentType` propagation.
4. `should_doNothing_when_keysAreEmptyOrNull` — `verifyNoInteractions(s3Client)` for both `Set.of()` and `null`.
5. `should_throwStorageException_when_keyHasUnknownPrefix` — `verifyNoInteractions(s3Client)`, nothing deleted.
6. `should_deleteInSingleCall_when_keysFitOneBatch` — 3 keys → one `deleteObjects` with 3 `ObjectIdentifier`s.
7. `should_chunkRequests_when_keysExceedOneThousand` — 1001 keys → two calls (1000 + 1).
8. `should_throwStorageException_when_responseContainsErrors` — stub `DeleteObjectsResponse` with one `S3Error`;
   assert exception, message contains no key material beyond the count.
9. `should_throwStorageException_when_sdkThrows` — stub `s3Client` to throw `S3Exception`; assert cause-chained
   `StorageException`.

### 5.3 Adapter integration tests — `integration/adapters/S3BlobStoreAdapterIntegrationTests.java` (LocalStack)

Follows the `EndpointIntegrationTest`/KAN-14 LocalStack pattern (`@SpringBootTest`, test `application.yml`
pointing at `http://localhost:4566`, `develop-assets` bucket, `@SetEnvironmentVariable` creds):

1. `should_uploadAndDelete_when_presignedUrlIsUsed` — presign `PRODUCT_IMAGE/image/jpeg`, HTTP-`PUT` bytes with
   no credentials → 200; `headObject` finds the key; `deleteObjects(Set.of(key))`; `headObject` → 404.
2. `should_rejectMismatchedContentType_when_uploadingWithWrongType` — presign for `image/jpeg`, `PUT` with
   `Content-Type: video/mp4` → S3 403; object absent.
3. `should_preserveOtherObjects_when_deletingASubset` — upload 2 keys, delete 1, assert the other still exists.

### 5.4 Regression

Full `mvn verify` (surefire + failsafe) green; every existing endpoint suite still starts — the new beans must not
destabilise the Spring context.

---

## 6. Definition of Done

1. `BlobType`, `PresignedUpload`, `BlobStore` created in `domain/storage/` with zero AWS imports.
2. `StorageException` created in `application/exceptions/`, mirroring existing exception conventions.
3. `S3BlobStoreAdapter` created in `infrastructure/adapters/`, constructor-injected singletons only.
4. All tests in §5 pass; `mvn verify` green with LocalStack up; JaCoCo ≥ 90% on all new classes.
5. `mvn spotless:apply` clean; SpotBugs / modernizer / enforcer / duplicate-finder pass (no new exclusions).
6. No credentials, bucket names, endpoints, or presigned URLs hardcoded or logged in `src/main`.
7. `README.md` documents the port, the 6 blob types, and the LocalStack workflow.
8. No new REST endpoint, no Flyway migration, no new `application.yml` properties.
9. Code review before merge; branch `feat/KAN-10-blob-store-backend`; conventional commits.

---

## 7. Non-functional requirements

### Security

- Credentials exclusively via `DefaultCredentialsProvider` (inherited from KAN-14 beans) — none in code, config,
  or tests outside env vars.
- Presigned URLs are bearer capabilities: **never log them**; default TTL `PT15M` (existing
  `aws.s3.presign-ttl`), never exceed 1 h.
- `contentType` allowlist per kind blocks smuggling executable/SVG content through image slots; S3 enforces the
  signed content type on upload (§4.4.2).
- Delete guard rejects keys outside known prefixes **before any S3 call**, so a compromised caller cannot turn
  the adapter into an arbitrary-bucket delete primitive.
- `endpointOverride` continues to come from `application.yml`/env only, never from a request.

### Performance

- Presigning is local HMAC (no network, sub-millisecond); the adapter is a singleton — no per-request clients.
- Deletes use batch `DeleteObjects` (1 call per ≤1000 keys), not N `DeleteObject` calls.
- No existence check before upload (uuid entropy suffices); no DB involved.

### Maintainability / architecture

- Strict DDD layering: `domain/storage` (pure), `application/exceptions` (business error),
  `infrastructure/adapters` (SDK). DIP satisfied: consumers depend on the port.
- OCP satisfied: new blob type = one enum constant; new backend = one adapter class implementing `BlobStore`.
- `@Slf4j` logging, license header, `google-java-format` via spotless — same as every other class.

---

## 8. Critical assumptions to validate

1. **Single bucket for everything** (`aws.s3.bucket`, today `develop-assets`): ticket-literal. Per-type buckets
   would be a config + adapter change, no port change.
2. **Extension appended from `contentType`**, not from a client-supplied filename: avoids path-traversal via
   `../../` filenames entirely. Confirm the frontend can render extension-bearing keys (it receives the full key).
3. **No size cap enforceable in presigned PUT**: 5 GB S3 single-PUT ceiling applies implicitly; explicit per-type
   caps (e.g. 10 MB images / 200 MB videos) need presigned-POST-with-conditions — proposed as follow-up (§9).
4. **SQS side effect**: `localstack-resources.yml` notifies `develop-products-assets-events-queue` only on the
   `products/` prefix. `PRODUCT_IMAGE/VIDEO` uploads will emit events (presumably intended); brand/strain uploads
   will **not** — confirm with the team, and note the future consumer must handle keys it did not issue.
5. **No versioning / lifecycle / CDN in scope**: deleted means deleted; confirm no soft-delete-on-S3 or
   CloudFront invalidation requirement hides behind "remove a set of keys".
6. **`develop-assets` bucket name vs `develop-assets`**: `application.yml` default is `develop-assets` while
   `localstack-resources.yml` provisions `develop-assets` — verify which is canonical before the integration
   tests run (both appear in the repo; KAN-14 §8.5-adjacent inconsistency).

---

## 9. Out of scope (explicit follow-ups)

| Follow-up | Rationale |
|-----------|-----------|
| Per-aggregate upload endpoints (`POST /api/brands/{id}/images`, …) | Consume this port; need their own auth/validation specs |
| Persisting issued keys into `brand_images` / `product_images` / … tables | Belongs to the aggregate stories, not the store |
| Presigned-POST variant with content-length conditions | Only way to enforce upload size server-side |
| SQS consumer for `develop-products-assets-events-queue` | Queue exists, nothing reads it (also noted in KAN-14) |
| Bucket hardening (drop `PublicRead`, scope CORS) + CDN | Production blocker, not local-dev scope |
| Second `BlobStore` backend (GCS/local) | Proves the abstraction; premature before the second real need |
| S3 versioning / lifecycle policies | Needs product decision on retention |

---

## 10. Suggested success metrics

| Metric | Target |
|--------|--------|
| Line/branch coverage on new classes | ≥ 90%, 0 surviving pitest mutants on guards |
| Presigned-URL issuance latency (p95) | < 50 ms (local signing, no I/O) |
| Round-trip integration tests vs LocalStack | 3/3 green, zero AWS calls |
| Hardcoded credentials / endpoints in `src/main` | 0 |
| Follow-up stories unblocked | ≥ 4 (brand, strain, product media endpoints) |
| AWS SDK imports outside `infrastructure/` | 0 |
