# Tasks: kan-10-blob-storage-port — Blob Storage Port + S3 Adapter

> Source: `tmp/KAN-10-enriched-us-opus.md` §5 (testing plan), §6 (DoD), §11 (task breakdown).
> Specs: `specs/blob-storage/spec.md`, `specs/aws-s3-integration/spec.md`. Design: `design.md`.
> Method: TDD mandatory (AGENTS.md). Every production class is preceded by a failing test (RED),
> verified failing for the expected reason, then minimal implementation (GREEN), then refactor.
> Test naming: `should_[expected]_when_[condition]`; structure: Arrange/Act/Assert.
> No code in this artifact — tasks only.

## Phase 1 — `BlobType` enum + `isCanonicalKey` (domain, no AWS)

- [x] 1.1 RED: write `BlobTypeTests.should_exposeDistinctPrefix_when_eachBlobTypeIsInspected` (`@ParameterizedTest` over all six types; prefixes pairwise distinct, all ending with `/`).
  TDD note: RED first — test must fail (no `BlobType` class exists). Acceptance mapping: spec `blob-storage` §"Blob type to key prefix mapping" / scenario "Each blob type maps to its own distinct prefix"; enriched §5.1.1; DoD §6.2.
  Files touched: `src/test/java/com/example/demo/domain/models/BlobTypeTests.java` (new).
- [x] 1.2 GREEN: implement `BlobType` enum with six constants, `prefix` field and `prefix()` accessor (minimal code to pass 1.1).
  TDD note: GREEN — run 1.1, confirm pass, keep output pristine. Acceptance mapping: same as 1.1.
  Files touched: `src/main/java/com/example/demo/domain/models/BlobType.java` (new).
- [x] 1.3 RED: write `should_startWithProductsPrefix_when_blobTypeIsProductImageOrVideo` (pins `PRODUCT_IMAGE`/`PRODUCT_VIDEO` start with `products/`; brand/strain keys do NOT match the `products/` filter).
  TDD note: RED first — must fail until the nested prefixes exist per type. Acceptance mapping: spec scenario "Product keys preserve the object-created notification prefix"; enriched §5.1.2; DoD §6.2; protects the S3 → SQS `products/` filter contract.
  Files touched: `src/test/java/com/example/demo/domain/models/BlobTypeTests.java`.
- [x] 1.4 RED: write `should_acceptKey_when_keyIsCanonical` (one case per blob type: known prefix + 32 lowercase hex).
  TDD note: RED first — `isCanonicalKey` does not exist yet. Acceptance mapping: spec scenario "Upload target issued for a blob type" (key shape); enriched §5.1.3.
  Files touched: `src/test/java/com/example/demo/domain/models/BlobTypeTests.java`.
- [x] 1.5 GREEN: implement `BlobType.isCanonicalKey` derived from the same `prefix` constants (DRY — never hardcode the prefix alternation a second time) so 1.3–1.4 pass.
  TDD note: GREEN — run full `BlobTypeTests`, confirm green. Acceptance mapping: same as 1.3–1.4; DDD: validation lives with the data it validates (SRP); OCP: seventh type extends validation automatically.
  Files touched: `src/main/java/com/example/demo/domain/models/BlobType.java`.
- [x] 1.6 RED: write rejection tests `should_rejectKey_when_prefixIsUnknown` (e.g. `dispensaries/images/<hex32>`), `should_rejectKey_when_randomPartIsNotHex32` (too short, too long, uppercase hex, contains `/`), `should_rejectKey_when_keyIsNullOrBlank`, `should_rejectKey_when_keyAttemptsPathTraversal` (`products/images/../../secret`).
  TDD note: RED first — each must fail for the expected reason (missing/incorrect rejection), not typos. Acceptance mapping: spec scenario "Non-canonical keys are distinguishable from port-issued keys"; enriched §5.1.4–5.1.7; DoD §6.4 (validation contract).
  Files touched: `src/test/java/com/example/demo/domain/models/BlobTypeTests.java`.
- [x] 1.7 GREEN + REFACTOR: extend `isCanonicalKey` (null → `false`; prefix match + `RANDOM_PART` hex32 match on the suffix) until all Phase 1 tests pass; refactor (extract constants, no behavior change) staying green.
  TDD note: verify GREEN for the whole class; JaCoCo branches on the new class must be covered. Acceptance mapping: closes spec §"Blob type to key prefix mapping"; DoD §6.2–6.3.
  Files touched: `src/main/java/com/example/demo/domain/models/BlobType.java`.

## Phase 2 — `BlobUploadTarget` record + `BlobStorageException` (domain, compile-only)

- [x] 2.1 RED: write compile-level contract tests asserting `BlobUploadTarget` exposes components `(key, url: URI, method: HttpMethod, expiresAt: Instant)` and `BlobStorageException` exposes `getFailedKeys()` with all three constructors (`(message, cause)`, `(message, failedKeys)`, `(message, failedKeys, cause)`), `failedKeys` defaulting to `Set.of()` and stored as immutable copy.
  TDD note: RED first — compilation failure is the failing test for trivial value objects. Acceptance mapping: design §3.2/§3.4; spec §"Failure reporting and presigned URL confidentiality" (`failedKeys`); enriched §2.3/§2.5.
  Files touched: `src/test/java/com/example/demo/domain/models/BlobUploadTargetTests.java` (new), `src/test/java/com/example/demo/domain/repositories/BlobStorageExceptionTests.java` (new).
- [x] 2.2 GREEN: implement `BlobUploadTarget` record and `BlobStorageException` (`extends RuntimeException`, `Set.copyOf` on `failedKeys`).
  TDD note: GREEN — minimal code, tests pass. Acceptance mapping: same as 2.1; DoD §6.1, §6.5 (no AWS type in port-adjacent signatures).
  Files touched: `src/main/java/com/example/demo/domain/models/BlobUploadTarget.java` (new), `src/main/java/com/example/demo/domain/repositories/BlobStorageException.java` (new).

## Phase 3 — `BlobStorage` port interface (contract freeze, no implementation)

- [x] 3.1 Define `BlobStorage` port with exactly two operations: `BlobUploadTarget createUploadTarget(BlobType blobType)` and `void remove(Set<String> keys)` with Javadoc contract (null → `IllegalArgumentException`; empty → no-op; non-canonical → whole-batch rejection; idempotent removal).
  TDD note: no behavior to red-test; contract is frozen here so Phase 4+ tests compile against the abstraction, not the adapter (DIP). Acceptance mapping: spec §§"Upload target issuance", "Validated idempotent batch removal"; design §3.3; DoD §6.1; ISP: exactly two operations.
  Files touched: `src/main/java/com/example/demo/domain/repositories/BlobStorage.java` (new).

## Phase 4 — `S3BlobStorageAdapter.createUploadTarget` (mocked presigner)

- [x] 4.1 RED: write `should_returnCanonicalKeyAndPutMethod_when_uploadTargetIsRequested` (parameterized over all six types; key matches canonical shape with the requested prefix; method is `PUT`).
  TDD note: RED first — adapter class does not exist; Mockito `S3Presigner` + real `AwsS3Properties`. Acceptance mapping: spec scenario "Upload target issued for a blob type"; enriched §5.2.1; DoD §6.1–6.3.
  Files touched: `src/test/java/com/example/demo/infrastructure/adapters/storage/S3BlobStorageAdapterTests.java` (new).
- [x] 4.2 RED: write `should_returnDifferentKeys_when_uploadTargetIsRequestedTwice` and `should_presignAgainstConfiguredBucketAndTtl_when_uploadTargetIsRequested` (`ArgumentCaptor<PutObjectPresignRequest>`; assert `bucket`, `key`, `signatureDuration` equal configured values) and `should_setExpiresAtToNowPlusTtl_when_uploadTargetIsRequested` (tolerance window, no injected `Clock` per YAGNI decision D5/§8.7).
  TDD note: RED first — all fail (no implementation). Acceptance mapping: spec scenarios "Consecutive upload targets are unique" + "Upload target issued for a blob type" (`expiresAt = now + presign-ttl`); enriched §5.2.2–5.2.4.
  Files touched: `src/test/java/com/example/demo/infrastructure/adapters/storage/S3BlobStorageAdapterTests.java`.
- [x] 4.3 GREEN: implement `S3BlobStorageAdapter` skeleton (`@Component`, constructor-injected `S3Presigner`/`S3Client`/`AwsS3Properties`, `MAX_KEYS_PER_DELETE_REQUEST = 1000`) plus `createUploadTarget` per design §3.5 steps 1–7 (`Assert.notNull`, port-generated `UUID` hex32 key, `PutObjectPresignRequest` with `signatureDuration=ttl`, `log.info` with type + key only, return `BlobUploadTarget`).
  TDD note: GREEN — minimal code until 4.1–4.2 pass. Acceptance mapping: closes spec §"Upload target issuance" happy path; DoD §6.1–6.3; DRY: key built from `BlobType.prefix()`, never a hardcoded regex.
  Files touched: `src/main/java/com/example/demo/infrastructure/adapters/storage/S3BlobStorageAdapter.java` (new).
- [x] 4.4 RED: write `should_throwIllegalArgument_when_blobTypeIsNull` (no store call) and `should_throwBlobStorageException_when_presignerFails` (presigner throws `SdkClientException`; assert thrown type is `BlobStorageException`, AWS cause preserved, no AWS type escapes).
  TDD note: RED first — null guard and error translation missing. Acceptance mapping: spec scenarios "Null blob type is rejected" + "Storage failures are reported without vendor leakage"; enriched §5.2.5–5.2.6; DoD §6.5, §6.9 (message carries key, never URL).
  Files touched: `src/test/java/com/example/demo/infrastructure/adapters/storage/S3BlobStorageAdapterTests.java`.
- [x] 4.5 GREEN: add null guard (`Assert.notNull`) and `catch (SdkException | URISyntaxException)` → `BlobStorageException("Could not issue an upload target for key " + key, ex)`; run full adapter test class green.
  TDD note: GREEN — minimal error-translation code. Acceptance mapping: same as 4.4; design §5.4 row 1–3.
  Files touched: `src/main/java/com/example/demo/infrastructure/adapters/storage/S3BlobStorageAdapter.java`.

## Phase 5 — `AwsS3Properties.bucket` `@NotBlank` hardening

- [x] 5.1 RED: write a binding/startup-validation test asserting blank `aws.s3.bucket` fails validation.
  TDD note: RED first — `@NotBlank` absent so the test fails. Acceptance mapping: spec §"Upload target issuance" ("bucket name SHALL be required non-blank"); design §3.6; DoD §6.6; no new property introduced.
  Files touched: `src/test/java/com/example/demo/infrastructure/config/AwsS3PropertiesTests.java` (new) or existing config test class if present.
- [x] 5.2 GREEN: add `@NotBlank` to the `bucket` component of the nested `S3` record in `AwsS3Properties`.
  TDD note: GREEN — one-line change, test passes. Acceptance mapping: same as 5.1.
  Files touched: `src/main/java/com/example/demo/infrastructure/config/AwsS3Properties.java` (modify).

## Phase 6 — `remove` guards (fail-fast, no store call)

- [x] 6.1 RED: write `should_notCallS3_when_keySetIsEmpty` (`verifyNoInteractions(s3Client)`), `should_throwIllegalArgument_when_keySetIsNull`, `should_throwIllegalArgumentAndNotCallS3_when_anyKeyIsNotCanonical` (mix one valid + one invalid key; `verifyNoInteractions(s3Client)` proves validate-all-before-delete-any).
  TDD note: RED first — `remove` not implemented. Acceptance mapping: spec scenarios "Empty removal request performs no store call", "Null key set rejected before any store call", "Non-canonical key rejects the whole batch before any store call"; enriched §5.2.7–5.2.9; DoD §6.4.
  Files touched: `src/test/java/com/example/demo/infrastructure/adapters/storage/S3BlobStorageAdapterTests.java`.
- [x] 6.2 GREEN: implement `remove` guards (`Assert.notNull`, empty early-return, validate ALL via `BlobType.isCanonicalKey` before any `deleteObjects` call).
  TDD note: GREEN — guards only; happy path still unimplemented. Acceptance mapping: same as 6.1; design §5.2.
  Files touched: `src/main/java/com/example/demo/infrastructure/adapters/storage/S3BlobStorageAdapter.java`.

## Phase 7 — `remove` happy path + chunking

- [x] 7.1 RED: write `should_sendSingleDeleteRequest_when_keyCountIsAtLimit` (exactly 1000 keys → 1 `deleteObjects` call) and `should_chunkDeleteRequests_when_keyCountExceedsLimit` (1001 keys → 2 calls: 1000 + 1; every listed object deleted).
  TDD note: RED first — chunking loop missing. Acceptance mapping: spec scenario "Large removal batches are chunked"; enriched §5.2.10–5.2.11; DoD §6.4; S3 `DeleteObjects` 1000-key hard limit.
  Files touched: `src/test/java/com/example/demo/infrastructure/adapters/storage/S3BlobStorageAdapterTests.java`.
- [x] 7.2 GREEN: implement chunked `deleteObjects` loop (`MAX_KEYS_PER_DELETE_REQUEST` sub-lists, `ObjectIdentifier` per key, success `log.info("Removed {} blobs", keys.size())`); unknown canonical keys naturally silent no-op.
  TDD note: GREEN — run full adapter unit class. Acceptance mapping: spec scenarios "Blobs removed by key" + "Removal of unknown canonical keys is idempotent"; design §3.5 steps 4–9.
  Files touched: `src/main/java/com/example/demo/infrastructure/adapters/storage/S3BlobStorageAdapter.java`.

## Phase 8 — `remove` error translation

- [x] 8.1 RED: write `should_throwBlobStorageExceptionWithFailedKeys_when_deleteResponseContainsErrors` (`DeleteObjectsResponse.errors()` non-empty → `BlobStorageException` carrying `failedKeys`) and `should_throwBlobStorageException_when_s3ClientThrows` (`SdkException`/`S3Exception` → `BlobStorageException` with cause preserved, no vendor type escapes).
  TDD note: RED first — `errors()` unread / SDK throws unhandled. Acceptance mapping: spec scenarios "Partial batch failure carries failed keys" + "Storage failures are reported without vendor leakage"; enriched §5.2.12–5.2.13; DoD §6.5.
  Files touched: `src/test/java/com/example/demo/infrastructure/adapters/storage/S3BlobStorageAdapterTests.java`.
- [x] 8.2 GREEN: collect every `errors().key()` into `failedKeys`, `log.warn` + throw `BlobStorageException(message, failedKeys)` when non-empty; wrap `SdkException` per chunk into `BlobStorageException(message, cause)`.
  TDD note: GREEN — `errors()` MUST be read (classic silent-data-loss bug if ignored). Acceptance mapping: same as 8.1; design §5.4 rows 4–5.
  Files touched: `src/main/java/com/example/demo/infrastructure/adapters/storage/S3BlobStorageAdapter.java`.

## Phase 9 — Presigned-URL confidentiality test

- [x] 9.1 RED: write `should_notLogPresignedUrl_when_uploadTargetIsIssued` (capture log output via `ListAppender`; assert it contains the key but NOT `X-Amz-Signature`/URL/signature material; assert no exception message or metric tag contains the URL).
  TDD note: RED first — proves the bearer-capability secret is not leaked; KAN-14 rule becomes load-bearing here. Acceptance mapping: spec scenario "Presigned URLs are never logged"; enriched §5.2.14; DoD implicit in §6.7 quality gates (SpotBugs/confidentiality).
  Files touched: `src/test/java/com/example/demo/infrastructure/adapters/storage/S3BlobStorageAdapterTests.java`.
- [x] 9.2 GREEN: remove any URL/signature material from log statements and exception messages if the test exposes a leak (expected: already clean via `log.info(type, key)` — test then pins the discipline).
  TDD note: GREEN — test passes; discipline pinned against regression. Acceptance mapping: same as 9.1; design §5.1.
  Files touched: `src/main/java/com/example/demo/infrastructure/adapters/storage/S3BlobStorageAdapter.java` (only if a leak is found).

## Phase 10 — LocalStack integration tests (real `S3Client`/`S3Presigner`)

- [x] 10.1 Write and run `S3BlobStorageAdapterIntegrationTests` (`@SpringBootTest`, real LocalStack, `AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY=test`, requires `docker compose up`): `should_storeObjectAtIssuedKey_when_presignedUrlIsUploadedWithoutCredentials` (`HttpClient` PUT → `200`, `headObject` length match), `should_roundTripContent_when_objectIsUploadedThroughPresignedUrl` (`getObject` body equality), `should_deleteObject_when_removeIsCalled` (`headObject` throws after `remove`), `should_succeed_when_removingUnknownKey` (idempotent canonical-but-never-uploaded key), `should_deleteAllObjects_when_removeIsCalledWithMultipleKeys` (3 uploads, one `remove`, all gone), `should_isolateBlobTypes_when_keysAreListedByPrefix` (one upload per type; `listObjectsV2` per prefix returns exactly one).
  TDD note: integration-first against the finished adapter is acceptable here (unit RED→GREEN already proved behavior in Phases 4–9); `@AfterEach` cleanup via `remove(...)` so the shared `develop-assets` bucket stays clean. Acceptance mapping: spec scenarios "Credential-free upload through the issued URL", "Blobs removed by key", "Removal of unknown canonical keys is idempotent"; enriched §5.3.1–5.3.6; DoD §6.7 (`mvn verify` green).
  Files touched: `src/test/java/com/example/demo/integration/adapters/S3BlobStorageAdapterIntegrationTests.java` (new).
- [ ] 10.2 (Optional — SKIPPED, timing-flake risk; see apply-progress) TTL-expiry test `should_rejectUpload_when_presignedUrlHasExpired` (`@TestPropertySource("aws.s3.presign-ttl=PT1S")`, wait 2 s → `403`), marked `@Tag("slow")` if the team dislikes timing-dependent tests.
  TDD note: optional; timing-flake risk accepted explicitly. Acceptance mapping: enriched §5.3.7.
  Files touched: `src/test/java/com/example/demo/integration/adapters/S3BlobStorageAdapterIntegrationTests.java`.

## Phase 11 — Docs + OpenSpec reconciliation

- [x] 11.1 Update `README.md` (blob-type/prefix table, key convention `<prefix><32 lowercase hex>`, LocalStack upload walkthrough with credential-free `curl -X PUT --upload-file`).
  TDD note: n/a (docs). Acceptance mapping: DoD §6.9; proposal "Docs" bullet.
  Files touched: `README.md` (modify).
- [x] 11.2 Update `docs/backend-standards.md` (register `infrastructure/adapters/storage/` in the project-structure tree; register the `BlobStorage` port convention: domain port + single-adapter ACL, no `software.amazon.awssdk` outside `infrastructure/config` + adapter).
  TDD note: n/a (docs). Acceptance mapping: DoD §6.9; proposal "Docs" bullet.
  Files touched: `docs/backend-standards.md` (modify).
- [x] 11.3 Verify `openspec/specs/blob-storage/spec.md` (created in this change) and reconcile the KAN-14 scope-fence sentence in `openspec/specs/aws-s3-integration/spec.md` so the `S3Client`/`S3Presigner` beans are recorded as consumed by the first storage adapter with unchanged bean behavior.
  TDD note: n/a (specs). Acceptance mapping: DoD §6.10; design §5.5; proposal "Modified Capabilities".
  Files touched: `openspec/changes/kan-10-blob-storage-port/specs/blob-storage/spec.md`, `openspec/changes/kan-10-blob-storage-port/specs/aws-s3-integration/spec.md` (verify/reconcile wording; no behavior change).

## Phase 12 — Quality gates + follow-up tickets

- [x] 12.1 Run `mvn test` and `mvn verify` green; JaCoCo ≥ 90% branches/lines on all new classes; pitest gate satisfied (empty-set guard branches, chunking boundary 1000/1001, `errors()` branch, `isCanonicalKey` null/blank branches).
  TDD note: gates, not code. Acceptance mapping: DoD §6.7; backend-standards coverage threshold.
  Files touched: none (verification only; fix code under the phase that owns any gap).
- [x] 12.2 Run Spotless (google-java-format + GPL license header), SpotBugs, modernizer, enforcer, duplicate-finder — all green; confirm the adapter is the only `software.amazon.awssdk` importer outside `infrastructure/config` (grep/ArchUnit check).
  TDD note: gates, not code. Acceptance mapping: DoD §6.1 (import discipline), §6.8.
  Files touched: none (verification only).
- [x] 12.3 Raise follow-up tickets (not fixes — explicitly out of scope): upload size/content-type enforcement (§7.1 hardening), orphan-blob reclamation + orphan-rate metric (§8.4), bucket public-read hardening + CORS scoping (pre-existing KAN-14 production blockers), `strain_videos`/`product_videos` Flyway migrations (assumption 8.1), `remove`-on-soft-delete product decision (assumption 8.3), auth story before any production endpoint (assumption 8.5).
  TDD note: n/a. Acceptance mapping: DoD §6.11; proposal Non-Goals + assumptions §8.
  Files touched: none (ticket tracker entries).
