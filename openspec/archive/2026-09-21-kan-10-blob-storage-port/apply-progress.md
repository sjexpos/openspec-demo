# KAN-10 Blob Storage Port + S3 Adapter — Apply Progress

Date: 2026-09-21
Status: 32/33 tasks complete (10.2 optional skipped) — ready for `opsx-verify`.
No commits made (automatic chain — verify/archive own the commit boundary).

## Phase 1 — `BlobType` enum + `isCanonicalKey` (1.1–1.7) [x]

- 1.1 RED confirmed: `BlobTypeTests` failed compilation (`cannot find symbol: class BlobType`).
- 1.2 GREEN: six-constant enum with `prefix` field + `prefix()` accessor; 1.1 green.
- 1.3 (`products/` SQS-contract pin) passed first run against the 1.2 enum — the nested
  prefixes ARE the fix, no further change needed.
- 1.4 RED confirmed: `cannot find symbol: method isCanonicalKey(String)`.
- 1.5 GREEN: `isCanonicalKey` derived from the same `prefix` constants (DRY) + `RANDOM_PART`
  hex32 pattern; null → `false`.
- 1.6 rejection tests (unknown prefix / non-hex32 / null-or-blank / path-traversal) passed
  first run against the 1.5 implementation — the 1.5 branches already covered them.
- 1.7 GREEN + REFACTOR: full `BlobTypeTests` green (6 + 1 + 6 + 4 + 5 + 4 + 3 cases).

## Phase 2 — `BlobUploadTarget` + `BlobStorageException` (2.1–2.2) [x]

- 2.1 RED confirmed: compilation failure for both missing classes.
- 2.2 GREEN: `BlobUploadTarget(String key, URI url, HttpMethod method, Instant expiresAt)`
  record; `BlobStorageException extends RuntimeException` with all three constructors,
  `Set.copyOf` on `failedKeys`, `Set.of()` default. 5/5 green (incl. immutability test).

## Phase 3 — `BlobStorage` port (3.1) [x]

- Contract frozen: exactly `createUploadTarget(BlobType)` + `remove(Set<String>)` with Javadoc
  contract (null → `IllegalArgumentException`; empty → no-op; non-canonical → whole-batch
  rejection; idempotent removal). No AWS type in any signature.

## Phase 4 — `createUploadTarget` (4.1–4.5) [x]

- 4.1/4.2/4.4 RED confirmed: `cannot find symbol: class S3BlobStorageAdapter`.
- 4.3 GREEN: `@Component @Slf4j @RequiredArgsConstructor` skeleton, `MAX_KEYS_PER_DELETE_REQUEST
  = 1000`, port-generated UUID-hex32 key, `PutObjectPresignRequest` with
  `signatureDuration=ttl`, `log.info` with type + key only.
- 4.4/4.5: `Assert.notNull` guard + `catch (SdkException | URISyntaxException)` →
  `BlobStorageException("Could not issue an upload target for key " + key, ex)` — in the same
  skeleton (null guard and error translation were written with it, tests prove both).
- Fix during phase: `verify(...).deleteObjects(any())` was ambiguous (two `S3Client`
  overloads) → `any(DeleteObjectsRequest.class)`.

## Phase 5 — `AwsS3Properties.bucket` `@NotBlank` (5.1–5.2) [x]

- 5.1 RED confirmed: blank `aws.s3.bucket` started the context successfully (no failure).
- 5.2 GREEN: `@NotBlank` on `bucket` PLUS `@Valid` on the `s3` component — deviation: nested
  validation does not cascade without `@Valid`, so `@NotBlank` alone still passed a blank
  bucket. Minimal one-annotation addition, documented here. 5/5 green.

## Phase 6 — `remove` guards (6.1–6.2) [x]

- Guard tests (empty → `verifyNoInteractions`; null → `IllegalArgumentException`; mixed
  valid+invalid → `IllegalArgumentException` + `verifyNoInteractions`, proving
  validate-all-before-delete-any) were written with the Phase 4 file and failed RED with it;
  GREEN via the `Assert.notNull` / empty early-return / `isCanonicalKey` pre-validation.

## Phase 7 — `remove` happy path + chunking (7.1–7.2) [x]

- 7.1 RED confirmed: `UnsupportedOperationException("remove is not implemented yet")` on both
  chunking tests. (The 8.2 `errors()` handling was briefly implemented ahead of phase and
  reverted to keep the 7.2 scope minimal.)
- 7.2 GREEN: chunked `deleteObjects` loop over `MAX_KEYS_PER_DELETE_REQUEST` sub-lists,
  `ObjectIdentifier` per key, `log.info("Removed {} blobs", keys.size())`. 1000 → 1 call,
  1001 → 2 calls (1000 + 1), every key deleted.

## Phase 8 — `remove` error translation (8.1–8.2) [x]

- 8.1 RED confirmed: both tests failed expecting `BlobStorageException` (`errors()` ignored,
  `SdkException` propagated raw).
- Deviation from sketch: per-key error type in AWS SDK v2 2.55.1 is `S3Error`, not `Error`
  (`software.amazon.awssdk.services.s3.model.Error` does not exist in this version).
- Test-data fix (not production): one `okKey` fixture had 33 hex chars and was correctly
  rejected by validation — proves the guard works; fixture corrected to 32 chars.
- 8.2 GREEN: collect every `errors().key()` → `log.warn` + `BlobStorageException(message,
  failedKeys)`; `catch (SdkException)` per operation → `BlobStorageException(message, cause)`.

## Phase 9 — Presigned-URL confidentiality (9.1–9.2) [x]

- `ListAppender` test pins that logs contain the key but never `X-Amz-Signature`/URL/signature
  material. Passed first run — implementation was already clean (`log.info(type, key)` only);
  no production change needed (the outcome task 9.2 anticipates).

## Phase 10 — LocalStack integration (10.1) [x], (10.2 skipped)

- Environment: `docker compose` postgres + localstack healthy; the `develop-assets` bucket was
  missing (ephemeral LocalStack lost it) → re-ran `docker-compose-localstack-init.sh`
  (CloudFormation `develop-stack` redeployed, bucket verified). Pre-existing script quirk:
  stray line 9 (`localstack-resources.yml` as a bare command) prints an error after a
  successful deploy — out of scope, not touched.
- 6/6 green via failsafe (`S3BlobStorageAdapterIntegrationTests`): credential-free PUT → 200 +
  `headObject` length match, `getObject` round-trip equality, `remove` → `NoSuchKeyException`
  on `headObject`, unknown canonical key → silent success, 3-uploads-one-`remove` → all gone,
  one-per-type upload → `listObjectsV2` per prefix returns exactly the issued key.
  `@AfterEach` cleanup via `remove(...)` keeps the shared bucket clean.
- Note: surefire lacks `--add-opens java.base/java.util` so `@SetEnvironmentVariable` tests
  must run under failsafe (`mvn verify` or `-Dsurefire.skip=true`); running the class with
  plain `mvn test -Dtest=...` fails with `PreconditionViolation` (same constraint KAN-14
  recorded). Not a product issue.
- 10.2 (optional TTL-expiry, timing-flake) SKIPPED explicitly per task optionality.

## Phase 11 — Docs + OpenSpec reconciliation (11.1–11.3) [x]

- 11.1 `README.md`: blob-type/prefix table, `<prefix><32 lowercase hex>` key convention,
  `products/` SQS-filter note, LocalStack credential-free `curl -X PUT --upload-file`
  walkthrough, bearer-capability + `BlobStorageException` notes.
- 11.2 `docs/backend-standards.md`: `infrastructure/adapters/storage/` registered in the
  project-structure tree; new `BlobStorage` port convention section (domain port + single-adapter
  ACL, import discipline, DRY prefixes, no-URL-logging rule).
- 11.3 verified, no edit needed: `specs/blob-storage/spec.md` (new capability) exists with full
  scenarios; `specs/aws-s3-integration/spec.md` delta already carries the reconciled wording +
  the "Beans consumed by the blob-storage adapter without behavior change" scenario.

## Phase 12 — Quality gates + follow-up tickets (12.1–12.3) [x]

- `mvn verify`: BUILD SUCCESS. Unit (surefire) 120 tests, 0 failures/errors; integration
  (failsafe) 69 tests, 0 failures/errors. (One stale surefire XML for the integration class
  from the mis-scoped run above is superseded by the green failsafe run in the same build.)
- JaCoCo (new/changed classes, `mvn jacoco:report`): `BlobType` 17/17 lines, 2/2 branches;
  `S3BlobStorageAdapter` 50/50 lines, 6/6 branches; `BlobUploadTarget` 1/1; 
...[truncated 1583 chars]