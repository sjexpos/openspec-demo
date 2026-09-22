# KAN-11 — Enriched User Story: create a brand image upload target

> **Jira note**: no Jira MCP server is configured in this workspace (`mcpServers` is empty both
> globally and for this project), so the ticket could not be read or updated automatically.
> This document is the `[enhanced]` section: paste it into KAN-11 below the `[original]` text and
> move the ticket from *To refine* to *Pending refinement validation* manually.

---

## Executive Summary

KAN-11 is **follow-up F1 of KAN-12** and the first HTTP surface over the asset pipeline. The two
halves already exist and are unused together:

| Shipped | Ticket | What it gives us |
|---|---|---|
| `BlobStorage.createUploadTarget(BlobType)` → `BlobUploadTarget(key, url, PUT, expiresAt)` | KAN-10 | A canonical key `brands/images/<32 hex>` + a credential-free presigned `PUT` URL |
| `BrandImage.pending(brand, imageKey)` + `BrandImageRepository` + `brand_images.status` | KAN-12 | A `PENDING` row that records the *intent* to upload |

KAN-11 wires them: **resolve the brand → mint the target → persist the `PENDING` row → return the
target to the caller**. Nothing else in the system can currently produce a `BrandImage`.

**Three findings that changed the ticket as written** (findings 1 and 2 are now **resolved**;
finding 3 is still a blocking prerequisite):

1. ~~**`PUT` is the wrong verb.**~~ **RESOLVED — the endpoint is `POST /api/brands/{brandId}/images`
   returning `201 Created`** (confirmed by the product owner, 2026-09-22). The original draft said
   `PUT`, but the operation is *not idempotent* — every call mints a new key and inserts a new row,
   so `N` calls produce `N` images. `PUT` on a collection URI means "replace the collection"
   (RFC 9110 §9.3.4) and requires a request body, which this operation does not have.
   `docs/backend-standards.md` maps create to `POST`, every existing create in the codebase is
   `POST`, and KAN-12's own follow-up table already names this endpoint
   `POST /api/brands/{brandId}/images`. **The rest of this document assumes `POST`.**
2. ~~**The ticket omits the response, which is the whole point.**~~ **RESOLVED — the response is
   exactly three fields: `uploadUrl`, `uploadMethod`, `expiresAt`** (confirmed 2026-09-22). No
   `id`, no `imageKey`, no `status`. The draft returned only the row, which would leave the client
   with a `PENDING` record and no way to upload bytes. The agreed shape is the minimal one: the
   `BrandImage` row is a **server-side side effect the client never sees**. Two consequences, both
   material — see §2 D2:
   - **The client has no handle on the created image**, so the "client confirms the upload" model
     is structurally impossible. **KAN-12 F2 is now forced to the S3 → SQS server-side model**,
     which requires widening the notification filter beyond `products/`.
   - **`BrandImageUploadTicket` is no longer needed** (D5 dissolved): the service can return the
     `BlobUploadTarget` alone, so no new package and no new record.
3. **This endpoint violates a shipped spec sentence.** `openspec/specs/blob-storage/spec.md`
   → *Requirement: Scope exclusions* → *Scenario: Out-of-scope surfaces are absent* asserts
   "the system SHALL expose **no HTTP endpoint wrapping the port**". KAN-11 does exactly that. The
   requirement must be narrowed to the `blob-storage` capability itself before this change is
   applied, or the spec suite becomes false. See §10.3.

**The caller is a browser or a mobile app** (confirmed 2026-09-22). This closes assumption 11.1 the
*expensive* way: a browser performing a cross-origin `PUT` to S3 issues a **preflight `OPTIONS`
first**, so **bucket CORS is now a hard prerequisite, not a nice-to-have**. LocalStack does not
enforce CORS, so the entire test suite goes green while the first real browser upload fails. See
§9.1 — this is the single highest-risk item in the ticket.

**Two implementation landmines found in the current code** (§6):

- `JsonConfig` exposes a bare `new ObjectMapper()` with **no `JavaTimeModule`**, so an `Instant`
  field in the response DTO throws `InvalidDefinitionException` at serialization time (a `500` on
  the happy path). `ErrorResponse.timestamp` is a `String` for exactly this reason.
- `org.springframework.http.HttpMethod` is not a Jackson-friendly type — it serializes as
  `{"name":"PUT"}`, not `"PUT"`.

**Good news worth stating explicitly**: this slice **cannot create an orphan blob**. Presigning is a
local SigV4 computation with no network call and no object created; if the row insert fails, the URL
is never disclosed to any client, so no bytes can ever be uploaded against it. Orphans only appear
once a client *does* upload and never confirms — that is F2/F4's problem, not KAN-11's.

---

## 1. User story

> **As** a brand administrator,
> **I want** to register a new image for a brand and receive a short-lived, credential-free upload
> location for it,
> **so that** I can upload the image bytes directly to object storage without the API ever handling
> the file and without holding any storage credentials.

### Business value

- **Unblocks the entire brand-media feature.** `brand_images` has an entity, a repository and a
  lifecycle since KAN-12, but **zero** write paths. Today the table can only be populated by a test.
- **Keeps binaries off the API.** Bytes travel client → S3. The API stays stateless, small-heap and
  unaffected by image size; no multipart parsing, no temp files, no request-size tuning.
- **Establishes the reusable asset-creation pattern.** `brand_videos`, `strain_images`,
  `product_images` and `dispensary_images` will copy this controller/service shape verbatim
  (KAN-12 F5). Getting the contract right here is worth more than the endpoint itself.
- **Costs nothing to abandon.** A `PENDING` row whose upload never happens is swept by F4 using the
  `ix_brand_images_pending_created` partial index that KAN-12 already shipped for this purpose.

### Acceptance criteria

| # | Given | When | Then |
|---|---|---|---|
| AC1 | An existing, non-soft-deleted brand `{brandId}` | `POST /api/brands/{brandId}/images` | `201 Created`, `DataResponse` body with **exactly three fields**: `uploadUrl`, `uploadMethod: "PUT"`, `expiresAt`. The body MUST NOT contain `id`, `brandId`, `imageKey` or `status` |
| AC2 | AC1 succeeded | The row is read from `brand_images` | Exactly one new row exists with `status = 'PENDING'`, `image_key` equal to the returned `imageKey`, `brand_id = {brandId}`, `created_at` populated |
| AC3 | AC1 succeeded | The client `PUT`s bytes to `uploadUrl` **with no AWS credentials** before `expiresAt` | The object is stored in the configured bucket at exactly the key recorded on the new row (read from the database — it is no longer in the response) |
| AC4 | Any brand | The endpoint is called twice in a row | Two different `uploadUrl` values, and two distinct rows with two different `image_key` values |
| AC5 | `{brandId}` does not exist **or** is soft-deleted (`deleted_at IS NOT NULL`) | The endpoint is called | `404 Not Found`, standard `ErrorResponse`, field `general`, message `Brand not found with ID: {brandId}` |
| AC6 | `{brandId}` is not a number (e.g. `abc`) | The endpoint is called | `400 Bad Request` from the existing `MethodArgumentTypeMismatchException` handler; **no** brand lookup, **no** presign, **no** insert |
| AC7 | `{brandId}` does not exist | The endpoint is called | `BlobStorage.createUploadTarget` is **never** invoked (brand is resolved first — no key is wasted on a bad request) |
| AC8 | The object store rejects presigning | The endpoint is called | `502 Bad Gateway`, standard `ErrorResponse`, **no** row is inserted, and the message contains **no** URL or signature material |
| AC9 | The row insert fails (e.g. constraint violation) | The endpoint is called | The transaction rolls back, no row persists, and the presigned URL is **never** returned to the client |
| AC10 | Any successful call | The response is inspected | The response carries `Cache-Control: no-store` (the presigned URL is a bearer capability) |
| AC11 | Any call, success or failure | Application logs are inspected at every level | **No** log record, exception message or metric tag contains the presigned URL or its query string |
| AC12 | Any successful call | The persisted row is inspected | It contains **only** the opaque key — no URL column, no presigned URL anywhere in the database |
| AC13 | A brand exists | `PUT /api/brands/{brandId}/images` is called (the verb from the original draft, now rejected) | `405 Method Not Allowed` |
| AC14 | A browser on the app origin | It issues the CORS preflight `OPTIONS` to `uploadUrl` before the `PUT` | The bucket answers with `Access-Control-Allow-Origin` and `Access-Control-Allow-Methods: PUT`, so the subsequent `PUT` is permitted (§9.1) |

---

## 2. Decisions

### Resolved

| Id | Decision | Outcome |
|---|---|---|
| **D1** | HTTP verb | **CLOSED 2026-09-22 — `POST` + `201 Created`.** `PUT` is rejected: the operation is not idempotent, has no request body, and targets a collection URI; `POST` matches `docs/backend-standards.md`, the three existing creates (`POST /api/brands`, `/api/products`, `/api/dispensaries`) and KAN-12 follow-up F1. Consequence: `PUT` on the collection URI must return `405` (AC13). |
| **D2** | Response shape | **CLOSED 2026-09-22 — exactly `uploadUrl`, `uploadMethod`, `expiresAt`.** No `id`, no `imageKey`, no `status` (`status` is always `PENDING`, i.e. a constant; `imageKey` is an internal detail; `id` is deliberately withheld). The persisted row is an invisible server-side side effect. **Consequence 1**: client-side confirmation is impossible → KAN-12 F2 is forced to S3 → SQS. **Consequence 2**: D5 dissolves. **Consequence 3**: the response leaks no internal identifier or key scheme — a security and coupling win. |
| **D5** | Where the `(BrandImage, BlobUploadTarget)` pair lives | **DISSOLVED by D2.** The controller never needs the entity, so `BrandImageService` returns `BlobUploadTarget` directly. No `application/services/model/` package, no new record, no `docs/backend-standards.md` project-structure change. |
| **D3** | `Location` header on `201` | **DISSOLVED by D2.** A `Location: /api/brands/{brandId}/images/{id}` would expose the very identifier D2 withholds, and would point at a read endpoint that does not exist (KAN-12 F3). **Omit the header.** Revisit only if the read story ever exposes per-image URIs. |
| **A1** | Caller type | **ANSWERED 2026-09-22 — browser and mobile app.** Browser ⇒ bucket CORS with preflight is a hard prerequisite (§9.1). Mobile ⇒ presign TTL must survive backgrounded/slow uploads (§9.1). |

### Still open

Neither is a contract decision; both are internal and can be taken with the stated defaults.

| Id | Decision | Options | Recommendation |
|---|---|---|---|
| **D4** | Cap on concurrent `PENDING` images per brand | (a) No cap, document the risk (b) Cap (e.g. 20) → `409 Conflict` | **(a) No cap in this slice.** There is no authentication anywhere in the codebase (KAN-12 assumption 9.6), so a cap is not a real control — an unauthenticated caller can also create brands. Record it as an accepted risk gated behind the existing "no production exposure without auth" rule, and let F4's sweeper bound the damage. Revisit the moment auth lands. |
| **D6** | Service placement | (a) New `BrandImageService` (b) Add a 5th method to `BrandService` | **(a).** SRP + ISP (`docs/backend-standards.md`). `BrandService` is already a 13-argument-method interface; and confirm/list/delete (F2/F3) will land next to this method, not next to brand CRUD. |

---

## 3. Scope

### In scope

- `POST /api/brands/{brandId}/images` (D1 closed): resolve brand → mint upload target →
  persist `PENDING` `BrandImage` → return **only the upload target** (D2 closed).
- New `BrandImageApi` interface + `BrandImageController`.
- New `BrandImageService` + `BrandImageServiceImpl` returning `BlobUploadTarget`.
- New response DTO (3 fields). **No ticket/result record** — D5 dissolved by D2.
- **Bucket CORS rule for browser uploads** in `localstack-resources.yml` (§9.1).
- `GlobalExceptionHandler`: map `BlobStorageException` → `502 Bad Gateway` (first HTTP exposure of
  the port; today it falls through to `handleGeneric` → `500`).
- `Cache-Control: no-store` on the success response.
- Unit tests (service, controller, exception handler) + integration tests (endpoint against real
  Postgres + LocalStack, including a real credential-free upload).
- Spec deltas (§10) and doc updates (§8).

### Out of scope (existing KAN-12 follow-ups — do not re-litigate here)

| | Why |
|---|---|
| Confirming the upload (`PENDING → UPLOADED`) | KAN-12 F2. **D2 has now decided the model for it**: with no identifier in the response, confirmation *must* be server-side via the S3 → SQS object-created notification, and the filter must be widened beyond `products/` (`localstack-resources.yml` line 46). Sizing F2 accordingly is a refinement output of KAN-11. |
| Listing / reading brand images and deriving public URLs | KAN-12 F3. `BlobStorage` has no `urlFor(key)` operation. |
| Deleting a brand image and reclaiming blobs; sweeping stale `PENDING` rows | KAN-12 F4. |
| **Declared MIME type / content type** | **Confirmed 2026-09-22: its own user story (F12).** `createUploadTarget` takes no content type today and `blob-storage` *Requirement: Scope exclusions* forbids it. **Read §11.9 — that story will change this endpoint's request contract, not just add a field.** |
| Upload size limits / virus-scan policy | KAN-12 F7. |
| Authentication / authorization / rate limiting | No auth exists anywhere in the codebase. Must precede production exposure. |
| Cascading brand soft-delete to its images | KAN-12 assumption 9.4, unresolved. |
| Ordering / "primary image" concept | KAN-12 assumption 9.9. |
| Any database migration | `brand_images` is already correct as of `V0.1.1`. **A migration in this change is a review-blocking smell.** |

---

## 4. API contract

### Request

```
POST /api/brands/{brandId}/images
```

- **Path**: `brandId` — `Long`, required.
- **Body**: **none**. No `@RequestBody`; a body sent by the client is ignored. There is nothing to
  send: the key is generated inside the port and MUST never be supplied by the caller
  (`blob-storage` spec, *Requirement: Blob type to key prefix mapping*).
- **Headers**: none required.

### Response `201 Created`

```http
HTTP/1.1 201 Created
Content-Type: application/json
Cache-Control: no-store
```

```json
{
  "data": {
    "uploadUrl": "https://<bucket>.s3.<region>.amazonaws.com/brands/images/9f2c...?X-Amz-Algorithm=...",
    "uploadMethod": "PUT",
    "expiresAt": "2026-09-22T10:15:30Z"
  }
}
```

**Exactly three fields, all `String`.** This is not cosmetic, see §6.1:

| Field | Java type in DTO | Source | Note |
|---|---|---|---|
| `uploadUrl` | `String` | `target.url().toString()` | Bearer capability. Also the *only* thing the client ever learns about the image. |
| `uploadMethod` | `String` | `target.method().name()` | **Not** `HttpMethod` — see §6.1 |
| `expiresAt` | `String` | `target.expiresAt().toString()` | ISO-8601 UTC. **Not** `Instant` — see §6.1. Same precedent as `ErrorResponse.timestamp`. |

**Deliberately absent** (D2) — an added field here is a review-blocking regression:

| Omitted | Why |
|---|---|
| `status` | Always `PENDING` from this endpoint. A constant in a payload is noise, and shipping it invites clients to branch on a value that can never vary. |
| `id` | Withholding it is what forces server-side confirmation (KAN-12 F2 via S3 → SQS). The client has no handle and needs none. |
| `imageKey` | Internal key scheme. Not exposed, so `brands/images/<32 hex>` stays free to change. |
| `brandId` | The caller already knows it — it is in the request URI. |

**Consequence for the implementation**: the controller never touches the `BrandImage` entity at
all. `BrandImageService` returns `BlobUploadTarget`; the persisted row is a pure side effect. This
is what dissolves D5 and neutralises the lazy-proxy trap of §6.2 by construction.

### Error responses (all in the existing `ErrorResponse` shape)

| Status | Trigger | `errors[0].field` | Handler |
|---|---|---|---|
| `400` | `brandId` not numeric | `brandId` | existing `MethodArgumentTypeMismatchException` handler |
| `404` | Brand missing or soft-deleted | `general` | existing `NotFoundException` handler |
| `405` | Wrong verb on the collection URI | — | Spring default |
| `502` | `BlobStorageException` (presign failure) | `general` | **new** handler, §5.5 |
| `500` | Anything else | `general` | existing `handleGeneric` |

### OpenAPI

`@Tag(name = "Brand Images", description = "Brand image asset endpoints")` on the new API
interface, plus `@Operation` and `@ApiResponses` for `201`, `400`, `404`, `502`, matching the
existing `BrandApi` style. **The `uploadUrl` example in the OpenAPI schema must be a placeholder,
never a real presigned URL.**

---

## 5. Files to create / modify

| # | File | Action |
|---|---|---|
| 1 | `presentation/api/BrandImageApi.java` | **create** — `@RequestMapping("/api/brands/{brandId}/images")`, `@Tag`, `@Validated`, one `@PostMapping` returning `DataResponse<CreateBrandImageResponse>` with `@ResponseStatus(CREATED)` |
| 2 | `presentation/api/model/CreateBrandImageResponse.java` | **create** — `record CreateBrandImageResponse(String uploadUrl, String uploadMethod, String expiresAt) {}` |
| 3 | `presentation/controllers/BrandImageController.java` | **create** — `@RestController implements BrandImageApi`; maps the ticket to the DTO; sets `Cache-Control: no-store` |
| 4 | `application/services/BrandImageService.java` | **create** — `BlobUploadTarget createUploadTarget(Long brandId) throws NotFoundException` |
| 5 | `application/services/impl/BrandImageServiceImpl.java` | **create** — orchestration (§5.4) |
| 7 | `presentation/controllers/GlobalExceptionHandler.java` | **modify** — add `BlobStorageException` → `502` |
| 8 | `test/.../application/services/BrandImageServiceTests.java` | **create** |
| 9 | `test/.../application/services/ServiceTest.java` | **modify** — add `@MockitoBean BrandImageRepository` and `@MockitoBean BlobStorage` |
| 10 | `test/.../presentation/controllers/BrandImageControllerTests.java` | **create** |
| 11 | `test/.../presentation/controllers/GlobalExceptionHandlerTest.java` | **modify** — `502` case |
| 12 | `test/.../integration/endpoints/BrandImageEndpointsTests.java` | **create** |
| 13 | `docs/data-model.md`, `docs/backend-standards.md` | **modify** — §8 |
| 14 | `openspec/.../specs/brands-management/spec.md`, `.../blob-storage/spec.md` | **modify** — §10 |
| 15 | `localstack-resources.yml` | **modify** — add `CorsConfiguration` to the bucket so browser uploads are exercisable locally (§9.1) |

**Explicitly NOT touched**: `BrandImage`, `BrandImageRepository`, `AssetStatus`, `BlobType`,
`BlobStorage`, `S3BlobStorageAdapter`, `BrandService`, `BrandRepository`, `JsonConfig`, and
**no Flyway migration** and **no new package**.
If a reviewer sees a diff in any of these, the change has scope-crept.

### 5.4 `BrandImageServiceImpl` — the ordering is the design

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class BrandImageServiceImpl implements BrandImageService {

  private final BrandRepository brandRepository;
  private final BrandImageRepository brandImageRepository;
  private final BlobStorage blobStorage;

  /** Returns only the upload target; the persisted PENDING row is an invisible side effect (D2). */
  @Override
  @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
  public BlobUploadTarget createUploadTarget(Long brandId) {
    // 1. Resolve first. @SQLRestriction("deleted_at IS NULL") makes soft-deleted brands invisible,
    //    so this single call covers both "missing" and "soft-deleted" (AC5).
    //    Doing this before step 2 is what satisfies AC7: no key is minted for a bad request.
    Brand brand =
        this.brandRepository
            .findById(brandId)
            .orElseThrow(() -> new NotFoundException("Brand not found with ID: " + brandId));

    // 2. Mint the key. Local SigV4 computation, no network call, no object created.
    //    Throws BlobStorageException -> 502, transaction rolls back, nothing persisted (AC8).
    BlobUploadTarget target = this.blobStorage.createUploadTarget(BlobType.BRAND_IMAGE);

    // 3. Record the intent. The entity factory re-validates key ownership (BRAND_IMAGE.isKeyOf)
    //    and forces PENDING; the service never sets the status itself.
    BrandImage saved = this.brandImageRepository.save(BrandImage.pending(brand, target.key()));

    // 4. Log the key only. NEVER the URL (AC11).
    log.info("Brand image {} registered as PENDING for brand {} with key {}",
        saved.getId(), brandId, saved.getImageKey());

    // The saved row is a side effect the caller never sees (D2): only the target is returned.
    return target;
  }
}
```

**Why this order, and why it is safe**

- Resolve → presign → persist is the only order that wastes nothing on a bad request (AC7) and
  cannot leak a usable URL on a failed write (AC9).
- Presigning inside `@Transactional` is normally an anti-pattern (external call holding a DB
  connection). It is acceptable **only** because `S3Presigner.presignPutObject` is an offline
  computation — verified in `S3BlobStorageAdapter`, no `S3Client` call on that path. This must be
  stated in `design.md` as an explicit, justified exception; if the port ever gains a network call
  on this path, the presign must move outside the transaction.
- **No orphan blob is reachable from this endpoint.** A presigned URL that is never returned is
  never used, and no object exists until someone `PUT`s bytes.

### 5.5 `GlobalExceptionHandler` — `BlobStorageException` → `502`

```java
@ExceptionHandler(BlobStorageException.class)
public ResponseEntity<ErrorResponse> handleBlobStorage(
    BlobStorageException ex, HttpServletRequest request) {
  log.error("Blob storage operation failed", ex);   // key only; the port never logs URLs
  List<FieldError> fieldErrors =
      List.of(new FieldError("general", "Blob storage is currently unavailable"));
  return build(HttpStatus.BAD_GATEWAY, request, fieldErrors);
}
```

`502`, not `500`: the failure is in an upstream dependency, not in this service. Not `503`: we are
not asking the client to retry after a known interval. **The client-facing message is a fixed
string** — `ex.getMessage()` is not echoed, because it embeds the blob key and, for batch removals,
the failed-key set (defence in depth for AC11).

---

## 6. Traps found in the current codebase (read this before coding)

### 6.1 Jackson will break the happy path if the DTO uses rich types

`infrastructure/config/JsonConfig` defines:

```java
@Bean
public ObjectMapper objectMapper() {
  return new ObjectMapper();   // no findAndRegisterModules(), no JavaTimeModule
}
```

Because this bean overrides Spring Boot's auto-configured mapper, `MappingJackson2HttpMessageConverter`
uses it:

- `java.time.Instant` → `InvalidDefinitionException: Java 8 date/time type ... not supported by
  default` → a `500` on a *successful* create. `jackson-datatype-jsr310` being on the classpath does
  **not** help: `new ObjectMapper()` registers no modules. This is exactly why
  `ErrorResponse.timestamp` is a `String` holding `Instant.now().toString()`.
- `org.springframework.http.HttpMethod` is a class, not an enum → serializes as `{"name":"PUT"}`.

**Decision for this slice**: keep `expiresAt` and `uploadMethod` as `String` in the DTO, consistent
with the `ErrorResponse.timestamp` precedent. Registering `JavaTimeModule` globally is a
cross-cutting change that would alter every future DTO and belongs in its own ticket (see §12 F9).

**Required test**: an exact-JSON assertion on the `201` body (`jsonPath("$.data.expiresAt").isString()`,
`jsonPath("$.data.uploadMethod").value("PUT")`), not just a status-code assertion. A status-only test
passes while the body is wrong.

### 6.2 The lazy-`Brand` / `open-in-view` trap — neutralised by D2, keep it that way

`Brand` is a `FetchType.LAZY` `@ManyToOne` on `BrandImage`. Mapping an entity-derived response
after the service transaction commits would resolve that proxy through
`spring.jpa.open-in-view: true` — the load-bearing coupling `docs/backend-standards.md`
§Lazy-Association Coupling and KAN-7 risk 11 warn about, which fails deterministically with
`LazyInitializationException` (`500`) the day that flag is turned off.

**D2 removes the hazard structurally**: the controller receives a `BlobUploadTarget`, never a
`BrandImage`, so no proxy is ever touched and the endpoint survives `open-in-view: false` for free.
This is a design property to protect, not an accident — **the moment anyone adds `id`, `brandId` or
`imageKey` back to the response, re-read this section.**

### 6.3 `BrandImage.pending()` throws `IllegalArgumentException`, which maps to `500`

The factory rejects a null/transient brand and a non-canonical key with `IllegalArgumentException`,
which today falls into `handleGeneric` → `500`. That is **correct and intended**: with the ordering
in §5.4 those conditions are unreachable programming errors, not client errors. Do **not** add an
`IllegalArgumentException → 400` handler — it would silently convert real bugs into client errors
across the whole API. A unit test should assert that a non-canonical key never reaches the factory.

### 6.4 Integration-test cleanup order

`BrandControllerEndpointsTests` deletes brands with `DELETE FROM brands` native SQL (because
`@SQLDelete` soft-deletes). `brand_images.brand_id` is a **FK**, and `BrandImage` deliberately has
no `@SQLDelete`. The new endpoint test's `@AfterEach` must run `DELETE FROM brand_images` **before**
`DELETE FROM brands`, or the suite fails with a FK violation. Copy the `EntityManagerFactory` +
native-query pattern already in that class.

### 6.5 `ServiceTest` is a shared mock bag

`ServiceTest` declares `@MockitoBean` for every repository. Adding `BrandImageRepository` and
`BlobStorage` there affects all service unit tests. Verify `BrandServiceTests`,
`ProductServiceTests` and `DispensaryServiceTests` still pass unchanged. (Longer term this base
class is a growing anti-pattern — logged as F10.)

---

## 7. Testing plan (TDD — red first, one baby step at a time)

### 7.1 `application/services/BrandImageServiceTests.java` (unit, all mocks)

| Test | Proves |
|---|---|
| `should_persistPendingImageWithPortIssuedKey_when_brandExists` | `ArgumentCaptor<BrandImage>`: `status == PENDING` and `imageKey == target.key()` — the row must be verified through the captor, since it is no longer observable in the response |
| `should_returnUploadTargetIssuedByPort_when_brandExists` | The service returns exactly the port's `BlobUploadTarget` (D2) |
| `should_throwNotFound_when_brandDoesNotExist` | AC5 |
| `should_throwNotFound_when_brandIsSoftDeleted` | AC5 — mock `findById` empty, mirroring `@SQLRestriction` |
| `should_notCallBlobStorage_when_brandDoesNotExist` | **AC7 — the ordering guarantee.** `verify(blobStorage, never())` |
| `should_notPersist_when_blobStorageFails` | AC8 — `verify(brandImageRepository, never()).save(any())` |
| `should_propagateBlobStorageException_when_presignFails` | Exception type is not swallowed or wrapped |
| `should_requestBrandImageBlobType_when_mintingKey` | `ArgumentCaptor<BlobType>` equals `BRAND_IMAGE` (guards a copy-paste to `BRAND_VIDEO`) |
| `should_neverLogPresignedUrl_when_imageIsRegistered` | AC11 — `ListAppender` over the service logger, reusing the pattern already in `S3BlobStorageAdapterTests` (~line 255) |

### 7.2 `presentation/controllers/BrandImageControllerTests.java` (`@WebMvcTest`, mocked service)

`201` + exact JSON body, all three fields (§6.1) · **`should_exposeOnlyUploadFields_when_imageIsRegistered`
— asserts `$.data` has exactly 3 keys and that `id`, `brandId`, `imageKey` and `status` are
absent (AC1; this is the regression guard for D2)** · `Cache-Control: no-store` · `404` on
`NotFoundException` with the standard error shape · `400` on `/api/brands/abc/images` · `502` on
`BlobStorageException` · `405` on `PUT`.

### 7.3 `presentation/controllers/GlobalExceptionHandlerTest.java` (extend)

`should_return502_when_blobStorageExceptionIsThrown` — and assert the body message does **not**
contain the exception's own message (§5.5).

### 7.4 `integration/endpoints/BrandImageEndpointsTests.java` (real Postgres + LocalStack)

Extends `EndpointIntegrationTest`; seeds a `BrandType` + `Brand` like `BrandControllerEndpointsTests`.

> **Note on assertions.** The key is no longer in the response (D2), so every database assertion
> must read it back via `brandImageRepository.findByBrandIdAndStatus(brandId, PENDING)` — the
> derived query KAN-12 already shipped. Do **not** parse the key out of `uploadUrl`: that would
> bake into the tests exactly the client-side coupling D2 was designed to prevent.

1. `should_createPendingRowAndReturnUploadTarget_when_brandExists` — `201` with the three fields;
   then a native query asserts exactly one `brand_images` row in `status = 'PENDING'` for the brand
   (AC1, AC2).
2. `should_storeObjectAtIssuedKey_when_clientUploadsToReturnedUrl` — **the acceptance test.**
   `HttpClient` `PUT`s bytes to `uploadUrl` with **no** AWS credentials, then `S3Client.headObject`
   confirms the object at the key **read from the persisted row** (AC3). Pattern already proven in
   `S3BlobStorageAdapterIntegrationTests.should_storeObjectAtIssuedKey_...`.
3. `should_createDistinctKeysAndRows_when_calledTwice` — two different `uploadUrl` values in the
   responses and two rows with different `image_key` values (AC4).
4. `should_return404_when_brandIsSoftDeleted` — soft-delete via the repository, then call (AC5).
5. `should_return404AndNotInsertRow_when_brandDoesNotExist` — plus a row-count assertion.
6. `should_notPersistAnyUrl_when_imageIsRegistered` — native `SELECT *`; no column holds
   `X-Amz-Signature` (AC12).

**Coverage gate**: 90% branches/lines on all new classes (`docs/backend-standards.md`).

### 7.5 What the automated suite CANNOT prove (§9.1)

**AC14 (browser CORS preflight) is not testable here.** LocalStack does not enforce CORS and
MockMvc/`HttpClient` are not browsers, so the suite goes green whether or not the bucket has a CORS
rule. AC14 must be verified by an explicit **manual browser check** — a `fetch(uploadUrl, {method:
'PUT', body: blob})` from the real app origin against a bucket configured as in §9.1 — and the
result recorded in the change's `reports/` folder alongside the `curl` verification, following the
KAN-8/KAN-12 precedent. **Treat a missing browser check as an incomplete Definition of Done.**

---

## 8. Documentation to update

- **`docs/data-model.md` → §9 Brand Images**: add the write path — a row is created only by
  `POST /api/brands/{brandId}/images`, always as `PENDING`, with the key issued by the port; state
  that confirmation and reclamation are still absent.
- **`docs/backend-standards.md`**:
  - §REST Endpoints — register the **asset-creation convention**: `POST /<parent>/{id}/<assets>`
    with no request body returns `201` + `{ uploadUrl, uploadMethod, expiresAt }` and
    `Cache-Control: no-store`. The persisted asset row is a server-side side effect: **no
    identifier, key or lifecycle state is exposed**, which is what keeps confirmation server-side
    and the key scheme free to change. This is the template for the five remaining asset tables.
  - §Error Handling / §Storage Ports — `BlobStorageException → 502 Bad Gateway`, with a fixed
    client-facing message (never `ex.getMessage()`).
  - §Storage Ports — "presigned URLs may be returned in a response body but never logged, never
    persisted, never placed in an OpenAPI example, and never cached (`no-store`)".
  - §Storage Ports — **bucket CORS is part of the definition of done for any browser-facing
    presigned-upload endpoint**, and it cannot be proven by the test suite (LocalStack does not
    enforce CORS): a manual browser check is required (§7.5, §9.1).
- **`README.md`** — if it carries an endpoint list, add the new route.

---

## 9. Non-functional requirements

### 9.1 Browser and mobile callers — the CORS prerequisite (highest risk in this ticket)

The caller is a **browser or a mobile app** (answered 2026-09-22). These two clients have different
failure modes and both must be designed for.

**Browser — bucket CORS is a hard prerequisite.**
A cross-origin `PUT` from a web page to `https://<bucket>.s3.<region>.amazonaws.com/...` is not a
"simple request": the browser first issues a **preflight `OPTIONS`** to the same URL. If the bucket
has no CORS configuration, S3 answers the preflight without the `Access-Control-Allow-*` headers,
the browser blocks the `PUT`, and **no upload ever reaches the bucket** — while the API returned a
perfectly valid `201` and a `PENDING` row now sits there forever.

Minimum bucket configuration:

```yaml
CorsConfiguration:
  CorsRules:
    - AllowedMethods: [PUT]
      AllowedOrigins: [<app origins — never '*' in production>]
      AllowedHeaders: [content-type]
      MaxAge: 3000
```

- Add it to the bucket in **`localstack-resources.yml`** as part of this ticket (§5, file 15) so
  the local stack mirrors production, and raise the equivalent change for the real bucket on the
  infrastructure track (KAN-14 / KAN-12 F7).
- `AllowedOrigins: '*'` is acceptable locally only. In production it must be the explicit app
  origins, otherwise any site can drive uploads with a URL it obtained.
- **This cannot be caught by the test suite** — see §7.5. It is the textbook "green build, broken
  product" failure.

**Mobile — the TTL is the failure mode, not CORS.**
Native apps make no preflight, but they background, lose connectivity and retry. `aws.s3.presign-ttl`
is evaluated when the `PUT` *starts*; an upload that starts after expiry fails outright.

- A TTL of a few minutes (the current guidance) is fine for a foreground web upload and **too short
  for a backgrounded mobile upload**. Either raise the TTL — which weakens the capability's blast
  radius, since the URL is valid for longer — or let the app request a fresh target on failure,
  which leaves an extra `PENDING` orphan behind every retry.
- **Decide this explicitly at refinement** and size the value against `PENDING`-row growth. It is
  the main input to the F4 sweeper's retention window.

**Content type is unsigned — deliberately, and only until the MIME story (F12) lands.**
`S3BlobStorageAdapter` presigns a `PutObjectRequest` with **no** content type, so the browser or app
sets it freely (or S3 defaults to `binary/octet-stream`), and nothing validates that the bytes are
an image.

This is **accepted for KAN-11** and handled by its own user story (confirmed 2026-09-22). What the
ticket must state so nobody is surprised:

- Objects created between KAN-11 and F12 may carry a wrong or missing `Content-Type`. That degrades
  `<img>` rendering and CDN behaviour in the read story (KAN-12 F3), and it is **not retroactively
  fixable from the key alone** — a backfill would have to re-`HEAD` or re-upload each object.
  If F3 is scheduled before F12, plan for it.
- The CORS rule in this section already lists `content-type` under `AllowedHeaders`, so it is
  **forward-compatible** with F12: no CORS change will be needed when the header starts being signed.
- **F12 is not an additive field** — see §11.9.

**Security**
- The presigned URL is a **bearer capability**: `Cache-Control: no-store` (AC10); HTTPS in every
  non-local environment; never logged (AC11); never persisted (AC12); never in an OpenAPI example.
- The key is generated inside the port; the caller can never influence it — path traversal and key
  guessing are structurally impossible.
- TTL is `aws.s3.presign-ttl`; no per-endpoint override. Keep it short (minutes).
- `502` leaks no upstream detail.
- The response exposes **no internal identifier and no key scheme** (D2), so the client cannot
  enumerate images, guess keys, or couple itself to `brands/images/<32 hex>`.
- **Open, unmitigated**: no authentication exists in the codebase, so this endpoint lets anyone mint
  unlimited upload capabilities and `PENDING` rows (D4). **Must not reach production before auth.**
  This is inherited from KAN-8/KAN-10/KAN-12, not introduced here — but with a **browser** caller
  the endpoint is directly reachable from any web page, so KAN-11 is the ticket where the gap stops
  being theoretical. Restate it prominently.

**Performance**
- One indexed `SELECT` (PK) + one `INSERT` + one local signature computation. No network call to
  the object store, no image bytes through the API, constant heap regardless of file size.
- Transaction is short; see §5.4 for the justified in-transaction presign.
- `INSERT` cost is bounded by the three indexes `V0.1.1` already created; no new index is needed.

**Observability**
- `INFO` on success with `brandImageId`, `brandId`, `imageKey`. **Never** the URL. Because the
  client receives no identifier, **these logs are the only correlation handle** between an API call
  and the row it created — do not trim them.
- `ERROR` on `BlobStorageException` with the stack trace (the port's message carries the key only).
- Useful future metrics (not built here): `brand_image.pending.created` counter and the
  `PENDING`-row age histogram that F4's sweeper will consume.

**Maintainability**
- The controller/service/DTO triple is the copy-paste template for the other five asset tables
  (KAN-12 F5). Extract a generic `AssetUploadService<T>` only at the **second or third** occurrence,
  not now — `dry-principle` skill: duplication is cheaper than the wrong abstraction at N=1.

---

## 10. Proposed OpenSpec artifacts

### 10.1 Modified capability — `brands-management` (new requirement)

```markdown
### Requirement: Create a brand image upload target

The system SHALL expose an operation that registers a new image for an existing brand and returns a
short-lived, credential-free upload target for it. The operation SHALL resolve the brand before any
other work and SHALL reject a missing or soft-deleted brand with `404 Not Found` without issuing any
blob key. On success the system SHALL issue an upload target for the `BRAND_IMAGE` blob type,
SHALL persist exactly one brand image in the `PENDING` state carrying the issued key, and SHALL
return the upload target. The persisted brand image SHALL be a server-side record that is not
observable in the response. The response SHALL NOT expose the identifier, key, or lifecycle
state, and SHALL return `201 Created` carrying **only** the upload URL, the upload HTTP method and
the expiry instant, wrapped in the standard `DataResponse`. The response SHALL NOT expose the brand
image identifier, the blob key, or the lifecycle state. The operation SHALL NOT accept a request
body and SHALL NOT accept a caller-supplied blob key. The operation SHALL NOT be idempotent: each invocation SHALL produce a distinct key and a
distinct brand image. The response SHALL be marked non-cacheable. The upload URL SHALL NOT be
persisted and SHALL NOT appear in any log record, exception message, metric tag, or API example.

#### Scenario: Upload target issued for an existing brand
- **WHEN** an upload target is requested for an existing non-deleted brand
- **THEN** the system SHALL return `201 Created` with the upload URL, the HTTP method `PUT`, and the
  expiry instant
- **AND** the response SHALL NOT contain the brand image identifier, the blob key, or the lifecycle state
- **AND** the system SHALL persist exactly one brand image in the `PENDING` state with the issued key

#### Scenario: Issued target accepts a credential-free upload
- **WHEN** a client uploads content to the returned upload URL without credentials before it expires
- **THEN** the object SHALL be stored at exactly the returned blob key

#### Scenario: No key is issued for an unresolvable brand
- **WHEN** an upload target is requested for a brand that does not exist or has been soft-deleted
- **THEN** the system SHALL return `404 Not Found` with the standard error response
- **AND** the system SHALL NOT issue a blob key
- **AND** the system SHALL NOT persist any brand image

#### Scenario: Each request yields a distinct image
- **WHEN** an upload target is requested twice for the same brand
- **THEN** the system SHALL return two different upload URLs
- **AND** the system SHALL persist two distinct brand images with two different blob keys

#### Scenario: Blob store failure persists nothing
- **WHEN** the blob store fails to issue an upload target
- **THEN** the system SHALL return `502 Bad Gateway` with the standard error response
- **AND** the system SHALL NOT persist any brand image
- **AND** the error response SHALL NOT contain any upload URL or signature material

#### Scenario: Browser uploads are permitted by the store
- **WHEN** a browser on an allowed application origin issues the cross-origin preflight for the
  returned upload URL
- **THEN** the object store SHALL permit the subsequent `PUT` from that origin

#### Scenario: Upload URL is never retained
- **WHEN** an upload target has been issued
- **THEN** no persisted row, log record, exception message, or metric tag SHALL contain the upload URL
- **AND** the response SHALL be marked non-cacheable
```

### 10.2 Modified capability — `brands-management` (extend an existing requirement)

Extend *Requirement: Standard error response shape* so upstream blob-store failures on brand
endpoints are reported as `502 Bad Gateway` with the standard `ErrorResponse` and a fixed message
that does not echo the upstream error text.

### 10.3 Modified capability — `blob-storage` (⚠ corrective, blocking)

`openspec/specs/blob-storage/spec.md` → *Requirement: Scope exclusions* currently reads
"This capability SHALL NOT introduce **an HTTP endpoint** …", with
*Scenario: Out-of-scope surfaces are absent* asserting "the system SHALL expose **no HTTP endpoint
wrapping the port**". KAN-11 makes that scenario false.

**Required delta**: narrow the exclusion to the capability's own surface.

```markdown
### Requirement: Scope exclusions

This capability SHALL NOT itself expose an HTTP endpoint, a persistence entity, a repository, a
service, or a database migration; consuming capabilities MAY expose endpoints that orchestrate this
port. This capability SHALL NOT enforce an upload size or content-type restriction, SHALL NOT
provide orphan-blob reclamation, SHALL NOT provide a second storage implementation, and SHALL NOT
introduce a new configuration property. All blobs SHALL live in the single bucket configured by
`aws.s3.bucket` with validity governed by `aws.s3.presign-ttl`.

#### Scenario: Out-of-scope surfaces are absent
- **WHEN** the capability is delivered
- **THEN** the capability SHALL expose no HTTP endpoint of its own and no persistence entity for blobs
- **AND** the system SHALL enforce no upload size or content-type restriction
- **AND** the system SHALL provide no orphan-blob reclamation and no second storage implementation
```

This edit is **not optional** and **not cosmetic**: leaving it out ships a change whose specs
contradict each other, which is precisely what §6 of `AGENTS.md` forbids.

### 10.4 Unchanged

`asset-lifecycle` and `aws-s3-integration` need no delta. KAN-11 uses only the shipped
`PENDING` creation path.

---

## 11. Critical assumptions to validate at refinement

1. ~~**The client can perform a cross-origin `PUT` to S3.**~~ **ANSWERED 2026-09-22 — the caller is
   a browser or a mobile app, so this is no longer an assumption but a confirmed dependency.**
   Bucket CORS is a prerequisite for the browser path and is now in scope for `localstack-resources.yml`
   (§9.1, §5 file 15); the production bucket rule is a dependency on the infrastructure track.
   The presign TTL must additionally be sized for backgrounded mobile uploads (§9.1) — **that
   sizing is still open and must be decided at refinement.**
2. **A `PENDING` row with no upload is acceptable debt** until F4's sweeper exists. The partial
   index `ix_brand_images_pending_created` is already in place for it.
3. ~~**Confirmation model is still undecided**~~ **DECIDED BY CONSEQUENCE (KAN-12 assumption 9.5 is
   now closed).** Because the response carries no identifier (D2), the client can never tell the
   server "this image is uploaded", so confirmation **must** be the S3 → SQS object-created
   notification. Two knock-on effects that must be sized before F2 is committed:
   (a) the notification filter is `products/`-only today (`localstack-resources.yml` line 46) and
   must be widened or a second subscription added; (b) the consumer resolves the row by key via
   `BrandImageRepository.findByImageKey` — which KAN-12 already shipped, so the persistence side is
   ready. **F2 is now strictly larger than when it was written; re-estimate it.**
4. **No auth** (D4, §9). Confirm the team accepts shipping an unauthenticated capability-minting
   endpoint behind the existing "not production-exposed" gate.
5. **No image metadata is needed at creation** beyond the MIME type deferred to F12 — no alt text,
   caption, position or declared size. If product needs any of those, the endpoint gains a request
   body and a `brand_images` migration. **Confirm before estimating.**
6. **`expiresAt` is advisory**, computed as `Instant.now().plus(ttl)` on the server; it may drift
   from S3's own expiry by the request latency. Acceptable for a UI countdown, not for anything
   security-relevant.
7. **Presigning stays offline.** If a future adapter makes `createUploadTarget` do network I/O, the
   in-transaction presign of §5.4 becomes a real connection-holding anti-pattern and must move out.
8. **Brand soft-delete still does not cascade** to images (KAN-12 assumption 9.4). This endpoint
   correctly refuses to create images for a soft-deleted brand, but pre-existing images of a brand
   soft-deleted later remain untouched.
9. **"No request body" is a provisional property of this endpoint, not a stable one.** The MIME-type
   story (F12) will almost certainly need the client to *declare* the content type **before** the
   URL is signed, because S3 signs the `Content-Type` header into the presigned request and then
   requires the upload to send exactly that value. That means F12 changes, in order:
   `BlobStorage.createUploadTarget(BlobType)` → `createUploadTarget(BlobType, String contentType)`
   (a **port signature change**, affecting `S3BlobStorageAdapter` and every future asset story);
   this endpoint gains a **request body** (e.g. `{ "contentType": "image/jpeg" }`) with an
   allow-list validation and a new `400` case; probably a `content_type` column on `brand_images`
   (**a Flyway migration**); and an amendment to `blob-storage` *Requirement: Scope exclusions*,
   which currently forbids any content-type restriction.
   **Recommendation: make the field required from day one in F12 rather than optional-then-required.**
   Breaking this endpoint is cheap right now — there is no auth and no production exposure — and far
   more expensive after clients exist. Two breaking changes instead of one is the avoidable outcome.

---

## 12. Suggested task breakdown (baby steps, TDD, one at a time)

0. Create branch `feat/kan-11-brand-image-upload-target`.
1. RED/GREEN — `BrandImageService` interface returning `BlobUploadTarget` (compile-only step, no
   behaviour). **No new record and no new package** — D5 dissolved.
2. RED — `BrandImageServiceTests` (§7.1, all nine cases). GREEN — `BrandImageServiceImpl` (§5.4).
   Update `ServiceTest` mocks and re-run every existing service suite (§6.5).
3. RED — `GlobalExceptionHandlerTest` `502` case. GREEN — the new handler (§5.5).
4. RED — `BrandImageControllerTests` (§7.2). GREEN — `BrandImageApi`, `CreateBrandImageResponse`,
   `BrandImageController`. **Assert the exact JSON body** — all three fields present *and* `id`,
   `brandId`, `imageKey`, `status` absent (§6.1, D2) — not only the status code.
5. RED — `BrandImageEndpointsTests` (§7.4), including the real credential-free upload. GREEN — fix
   whatever the real stack exposes. Watch the `@AfterEach` FK order (§6.4).
6. **MANDATORY** — review and re-run every pre-existing unit and integration suite; confirm zero
   regressions from the `ServiceTest` change.
7. Add the bucket `CorsConfiguration` to `localstack-resources.yml` (§9.1) and restart the local
   stack.
8. Manual `curl` verification against LocalStack (`POST` → copy `uploadUrl` → `curl -X PUT --upload-file`
   → verify the object) **and the manual browser preflight check of §7.5**, both recorded as
   reports following the KAN-8/KAN-12 precedent.
9. Docs (§8) and spec deltas (§10, including the corrective `blob-storage` edit).

## 13. Follow-up tickets

| Id | Ticket | Why not here |
|---|---|---|
| F2 | Confirm upload `PENDING → UPLOADED` **via S3 → SQS** (+ widen the `products/`-only notification filter) | KAN-12 F2. **Model now forced by D2 and the ticket is bigger than originally written — re-estimate.** |
| F3 | List brand images + derive public URLs (`BlobStorage.urlFor(key)` or CDN base) | KAN-12 F3 |
| F4 | Delete a brand image + blob-reclamation job + stale-`PENDING` sweeper | KAN-12 F4 |
| F7 | Upload hardening (size limits, virus scan) + **production** bucket CORS and public-read policy | KAN-12 F7. Local CORS ships in KAN-11; the production rule does not. |
| **F12** | **Declared MIME type on upload** — agreed 2026-09-22 as a separate story | Port signature change + request body + `400` allow-list case + likely migration + `blob-storage` spec amendment (§11.9). **Size it as a contract change to this endpoint, not as a new field.** Schedule it before KAN-12 F3 if possible, since objects stored meanwhile keep a wrong `Content-Type`. |
| **F11** | **Size the presign TTL for backgrounded mobile uploads** | Trade-off between capability blast radius and `PENDING` orphan growth (§9.1); input to F4's retention window |
| **F8** | **Authentication/authorization + rate limiting before production exposure** | No auth exists anywhere; gates this endpoint |
| **F9** | **Register `JavaTimeModule` on the `ObjectMapper` bean and migrate `ErrorResponse.timestamp` to `Instant`** | Cross-cutting Jackson change affecting every DTO (§6.1) |
| **F10** | **Split the `ServiceTest` shared mock bag into per-service fixtures** | Growing anti-pattern; out of this slice (§6.5) |

## 14. Success metrics

| Metric | Target |
|---|---|
| Upload-completion rate (`UPLOADED` ÷ created) | > 95% once F2 ships — a low rate means the TTL is too short or the client flow is broken |
| Median time from `201` to object present in the bucket | < 30 s (bounds the TTL) |
| Endpoint p95 latency | < 50 ms (one PK read + one insert + a local signature) |
| Stale `PENDING` rows older than the TTL | Trend flat once F4 ships; a rising trend is the orphan leak |
| `502` rate | < 0.1% |
| Presigned URLs found in logs | **0**, asserted by test (AC11) |
| Browser-origin upload success rate (preflight + `PUT`) | > 99% — anything lower means the bucket CORS rule drifted from the app origins (§9.1) |
| Mobile upload failures attributable to an expired target | < 1% — the signal that the presign TTL is mis-sized (F11) |
| Stored objects with a missing or non-image `Content-Type` | Track from day one; it is the business case and the backfill scope for F12 (§9.1) |
| New-class coverage | ≥ 90% branches/lines |
