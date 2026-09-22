# Tasks: kan-11-brand-image-upload-target — Brand Image Upload Target Endpoint

Schema: `story-sdd` | Change: `kan-11-brand-image-upload-target` | Branch: `feat/kan-11-brand-image-upload-target`

Conventions: TDD red-first, one baby step at a time. Each task: RED (write failing test, watch it fail) → GREEN (minimal code) → verify neighbours still green. English-only artifacts. Coverage gate: 90% branches/lines on all new classes (`docs/backend-standards.md`). Parallelizable tasks marked `[P]` — may run concurrently only after their `Depends on` is green. Never skip the mandatory manual browser preflight check (DoD).

Spec refs: `specs/brands-management/spec.md` (Create brand image upload target + Standard error response shape), `specs/blob-storage/spec.md` (Scope exclusions — corrective narrow). Design refs in parentheses per task.

---

## Phase 1 — Branch + service contract (compile-only)

- [x] T1.1 Create branch `feat/kan-11-brand-image-upload-target` from current main; verify clean `git status` and push `-u` tracking branch. (Depends on: none)
- [x] T1.2 RED: add `BrandImageService` interface test-compile probe — `application/services/BrandImageService.java` with `BlobUploadTarget createUploadTarget(Long brandId)`. GREEN: create interface only, no impl, no new record/package (D2/D4/D5). Verify `./mvnw -q compile` passes. (Design §4.4, D3/D4; spec: operation SHALL return upload target, SHALL NOT accept request body)

## Phase 2 — Service orchestration + 9 unit tests + ServiceTest mock updates + regression

GREEN target: `application/services/impl/BrandImageServiceImpl.java` — `@Transactional(rollbackFor = Exception.class, Propagation.REQUIRED)`, resolve → presign → persist ordering (§5.4), key-only logging (AC11), `BRAND_IMAGE` literal, in-transaction presign justified by offline SigV4 (D6). Untouched: `BrandImage`, `BrandImageRepository`, `BrandService`, `BlobStorage`, `S3BlobStorageAdapter`, `JsonConfig`, no migration, no new package.

- [x] T2.1 [P-setup] RED: extend `ServiceTest` shared mock bag — add `@MockitoBean BrandImageRepository` + `@MockitoBean BlobStorage`. GREEN: update file, re-run `BrandServiceTests`, `ProductServiceTests`, `DispensaryServiceTests` unchanged (no behaviour change). (Depends on: T1.2; Trap §6.5)
- [x] T2.2 RED: `BrandImageServiceTests.should_persistPendingImageWithPortIssuedKey_when_brandExists` — `ArgumentCaptor<BrandImage>` asserts `status == PENDING` and `imageKey == target.key()` (captor is PENDING-required: row invisible in response). GREEN: impl steps 1–3 minimal. (Depends on: T2.1; AC2)
- [x] T2.3 RED: `should_returnUploadTargetIssuedByPort_when_brandExists` — service returns exactly the port's `BlobUploadTarget` (D2, no ticket record). GREEN. (Depends on: T2.2)
- [x] T2.4 RED: `should_throwNotFound_when_brandDoesNotExist` + `should_throwNotFound_when_brandIsSoftDeleted` (mock `findById` empty mirroring `@SQLRestriction`; message `Brand not found with ID: {brandId}`). GREEN: `orElseThrow(NotFoundException)`. (Depends on: T2.3; AC5)
- [x] T2.5 RED: `should_notCallBlobStorage_when_brandDoesNotExist` — `verify(blobStorage, never())` (ordering guarantee). GREEN (already ordered resolve-first; test pins it). (Depends on: T2.4; AC7)
- [x] T2.6 RED: `should_notPersist_when_blobStorageFails` — `verify(brandImageRepository, never()).save(any())` + `should_propagateBlobStorageException_when_presignFails` (no swallow/wrap). GREEN. (Depends on: T2.5; AC8)
- [x] T2.7 RED: `should_requestBrandImageBlobType_when_mintingKey` — `ArgumentCaptor<BlobType>` equals `BRAND_IMAGE` (copy-paste guard vs `BRAND_VIDEO`). GREEN. (Depends on: T2.6)
- [x] T2.8 RED: `should_neverLogPresignedUrl_when_imageIsRegistered` — `ListAppender` over service logger (pattern from `S3BlobStorageAdapterTests` ~line 255), assert no URL/query string at any level. GREEN: key-only `log.info(id, brandId, imageKey)`. (Depends on: T2.7; AC11)
- [x] T2.9 RED (unreachable-guard pin): non-canonical key never reaches `BrandImage.pending()` factory path — documents D8 (`IllegalArgumentException` stays `500`, no new handler). Run full service suite green. (Depends on: T2.8)
- [x] T2.10 Regression: run ALL pre-existing service suites (`BrandServiceTests`, `ProductServiceTests`, `DispensaryServiceTests`) unchanged after `ServiceTest` change; zero failures. (Depends on: T2.9)

## Phase 3 — Handler 502 + test

- [x] T3.1 RED: extend `GlobalExceptionHandlerTest` — `should_return502_when_blobStorageExceptionIsThrown`: `502 BAD_GATEWAY`, standard `ErrorResponse`, body carries fixed message `Blob storage is currently unavailable` (`field: "general"`) and does NOT contain exception's own text / URL / signature. GREEN: add `@ExceptionHandler(BlobStorageException.class)` per §5.5 (`log.error` stack trace kept, fixed message only). Re-run full controller-test package. (Depends on: T2.10; AC8/AC11; spec: fixed-message 502) `[P]` vs Phase 4 RED after T2.10 green.

## Phase 4 — API / DTO / Controller + exact-JSON + D2 absence guard + no-store / 400 / 404 / 502 / 405

GREEN target: `BrandImageApi` (`@RequestMapping("/api/brands/{brandId}/images")`, `@Tag("Brand Images")`, `@Validated`, `@PostMapping` + `@ResponseStatus(CREATED)`, no `@RequestBody`), `CreateBrandImageResponse(String uploadUrl, String uploadMethod, String expiresAt)` (all `String` — D7 load-bearing: `target.url().toString()`, `target.method().name()`, `target.expiresAt().toString()`; placeholder-only OpenAPI example), `BrandImageController implements BrandImageApi` (service → DTO mapping, `Cache-Control: no-store`, never touches entity/proxy, no `Location`, never logs URL).

- [x] T4.1 RED: `BrandImageControllerTests` (`@WebMvcTest`, mocked service) — `201` + exact JSON: `uploadMethod == "PUT"`, `expiresAt` is string, `$.data` has exactly 3 keys; `id`/`brandId`/`imageKey`/`status` absent (D2 regression guard, §6.1/§6.2); `Cache-Control: no-store`; `404` standard shape on `NotFoundException`; `400` on `/api/brands/abc/images` (no lookup/presign/insert); `502` on `BlobStorageException` fixed message; `405` on `PUT`. GREEN: create the three main files. (Depends on: T3.1; AC1/AC6/AC10/AC13)
- [x] T4.2 Verify exact-JSON traps explicitly: `Instant`-as-`String` (no `JavaTimeModule` — `JsonConfig` bare mapper), `HttpMethod` never serialized directly; full controller package green. (Depends on: T4.1)

## Phase 5 — Integration endpoints + real credential-free PUT + FK order + no-URL-persisted

GREEN target: `BrandImageEndpointsTests extends EndpointIntegrationTest` (real Postgres + LocalStack; seed `BrandType` + `Brand`; `@AfterEach DELETE FROM brand_images` BEFORE `DELETE FROM brands` — §6.4 FK order, `BrandImage` has no `@SQLDelete`). Key read-back ONLY via `findByBrandIdAndStatus(brandId, PENDING)` — never parsed from `uploadUrl` (D2 coupling guard).

- [x] T5.1 RED→GREEN: `should_createPendingRowAndReturnUploadTarget_when_brandExists` — `201` three fields + native query exactly one `PENDING` row (AC1/AC2). (Depends on: T4.2)
- [x] T5.2 RED→GREEN: `should_storeObjectAtIssuedKey_when_clientUploadsToReturnedUrl` — `HttpClient PUT` bytes to `uploadUrl` with NO AWS credentials, `S3Client.headObject` confirms object at key read from row (AC3; pattern from `S3BlobStorageAdapterIntegrationTests`). (Depends on: T5.1)
- [x] T5.3 RED→GREEN: `should_createDistinctKeysAndRows_when_calledTwice` — distinct URLs + distinct `image_key` rows (AC4, non-idempotent). (Depends on: T5.2)
- [x] T5.4 RED→GREEN: `should_return404_when_brandIsSoftDeleted` + `should_return404AndNotInsertRow_when_brandDoesNotExist` (row-count assertions; AC5/AC7). (Depends on: T5.3)
- [x] T5.5 RED→GREEN: `should_notPersistAnyUrl_when_imageIsRegistered` — native `SELECT *`, no column holds `X-Amz-Signature` (AC12). (Depends on: T5.4)
- [x] T5.6 Coverage gate: verify 90% branches/lines on all new classes; add tests only for genuine gaps (no test-only production methods). (Depends on: T5.5)

## Phase 6 — Full regression

- [x] T6.1 MANDATORY: run EVERY pre-existing unit + integration suite; confirm zero regressions (especially from `ServiceTest` mock-bag change and new `502` handler). Record results. Missing green = DoD incomplete. (Depends on: T5.6)

## Phase 7 — CORS localstack

- [x] T7.1 Modify `localstack-resources.yml`: explicit bucket `CorsConfiguration` — `AllowedMethods: [PUT]`, `AllowedOrigins: [<app origins>]` (`*` local-only), `AllowedHeaders: [content-type]` (F12 forward-compat), `MaxAge: 3000`; restart local stack and confirm pickup. (Depends on: T6.1; AC14) `[P]` runnable alongside Phase 6 run, but verification (T8.x) waits for both.

## Phase 8 — curl + manual browser preflight reports

- [x] T8.1 Manual `curl` verification vs LocalStack: `POST /api/brands/{brandId}/images` → copy `uploadUrl` → `curl -X PUT --upload-file` → verify object; record as report in change `reports/` (KAN-8/KAN-12 precedent). (Depends on: T7.1)
- [x] T8.2 MANDATORY manual browser preflight check (AC14 — NOT provable by suite: LocalStack ignores CORS; MockMvc/`HttpClient` are not browsers): `fetch(uploadUrl, {method: 'PUT', body: blob})` from real app origin; record result in `reports/`. Missing browser check = incomplete DoD. (Depends on: T8.1)

## Phase 9 — Docs + spec deltas applied

- [x] T9.1 `[P]` Apply spec deltas: `brands-management` new requirement + error-shape extension + `blob-storage` Scope-exclusions narrow (blocking corrective) per enriched §10 / change `specs/` — verify spec suite coherent (no "no HTTP endpoint wrapping the port" contradiction). (Depends on: T8.2)
- [x] T9.2 `[P]` Update `docs/data-model.md` (§9 write path: created only by `POST`, always `PENDING`, port-issued key; confirmation/reclamation absent) + `docs/backend-standards.md` (asset-creation convention, `502` mapping, presigned-URL handling, CORS-as-DoD rule) + `README.md` endpoint list if present. (Depends on: T8.2)
- [x] T9.3 Final gate: `git status` clean of scope creep — diffs ONLY in the 5+3 new files + 4 modified files listed in design §Files Affected; any diff in explicitly-untouched list (`BrandImage`, `BrandImageRepository`, `AssetStatus`, `BlobType`, `BlobStorage`, `S3BlobStorageAdapter`, `BrandService`, `BrandRepository`, `JsonConfig`, migration, new package) = scope creep, revert. Confirm no migration added. (Depends on: T9.1, T9.2)

---

## Dependencies summary

T1.1 → T1.2 → T2.1 → … → T2.10 → T3.1 → T4.1 → T4.2 → T5.1 → … → T5.6 → T6.1 → T7.1 → T8.1 → T8.2 → {T9.1 ∥ T9.2} → T9.3. `[P]` tasks (T3.1 vs T4 RED prep; T9.1 ∥ T9.2; T7.1 vs T6.1 execution) may overlap only after their listed `Depends on` is green; TDD red-first within every task is strictly sequential.

## Definition of Done

- All boxes checked in order; every RED watched fail for the expected reason before GREEN.
- Coverage ≥ 90% branches/lines on new classes.
- Full suite green (T6.1) with `ServiceTest` change intact.
- `curl` + **manual browser preflight** reports present in `reports/` (browser check mandatory — absence = incomplete).
- Spec deltas + docs applied; no scope-creep diffs; no migration; no new package.
- Response contract holds exactly: `201` + 3 `String` fields, no `id`/`brandId`/`imageKey`/`status`, `Cache-Control: no-store`, `502` fixed message, URL never logged/persisted/exemplified.
