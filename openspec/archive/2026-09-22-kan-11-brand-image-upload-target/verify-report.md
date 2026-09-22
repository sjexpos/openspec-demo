# Verification Report — kan-11-brand-image-upload-target

**Change**: `kan-11-brand-image-upload-target` (branch `feat/kan-11-brand-image-upload-target`)
**Mode**: verify (pre-archive; verifier wrote no production code, read-only + test execution)
**Date**: 2026-09-22
**Verifier inputs**: `specs/brands-management/spec.md`, `specs/blob-storage/spec.md`,
`tasks.md`, `design.md`, `apply-progress.md`,
`reports/2026-09-22-manual-curl-verification.md`,
`reports/2026-09-22-manual-browser-preflight.md`, working tree on the feature branch.
**Skills applied**: test-driven-development, domain-driven-design, solid-principles,
dry-principle, java-jpa-hibernate, code-auditing, adversarial-review.

## 1. Completeness (tasks.md vs. implementation)

| Task | Claimed | Verifier finding |
|---|---|---|
| T1.1 Branch | done | Confirmed: current branch is `feat/kan-11-brand-image-upload-target`, based on KAN-12 (`50448c1`). No commits made (working tree only) — per chain rule. |
| T1.2 `BrandImageService` interface | done | `application/services/BrandImageService.java` exists with exactly `BlobUploadTarget createUploadTarget(Long brandId)`; no impl/record/package. |
| T2.1 `ServiceTest` mock bag | done | Diff shows exactly +2 `@MockitoBean` (`BrandImageRepository`, `BlobStorage`); nothing else touched. |
| T2.2–T2.9 `BrandImageServiceTests` (10 tests) | done | File exists; all 10 tests present as claimed (PENDING captor, exact-target return, 404 ×2, never-presign, never-save + propagation, `BRAND_IMAGE` captor, ListAppender no-URL pin, D8 non-canonical-key pin). Re-run 10/10 green (see §2). |
| T2.10 Service regression | done | Pre-existing suites untouched (no diff); targeted re-run green; full-suite claim accepted as recorded evidence (see W2). |
| T3.1 502 handler + test | done | `GlobalExceptionHandler` gains exactly the `BlobStorageException → 502` case (fixed message `Blob storage is currently unavailable`, `log.error` keeps stack trace); `GlobalExceptionHandlerTest` gains the 502 case asserting fixed message + absence of upstream text/URL/signature. Re-run 5/5 green. |
| T4.1/T4.2 Controller + DTO + Api (7 tests) | done | `BrandImageApi` (`/api/brands/{brandId}/images`, `@Tag("Brand Images")`, no `@RequestBody`), `CreateBrandImageResponse` (3 × `String`, placeholder OpenAPI example `https://example.invalid/...`), `BrandImageController` (service → DTO via `url().toString()` / `method().name()` / `expiresAt().toString()`, `Cache-Control: no-store`, no entity touch, no `Location`, no URL logging). Re-run 7/7 green. |
| T5.1–T5.5 `BrandImageEndpointsTests` (6 tests) | done | File exists with all 6 tests as claimed (201 + one PENDING row, credential-free PUT → `headObject` at row-read key, distinct URLs/keys/rows, 404 soft-deleted + 404 missing with row counts, native `SELECT *` free of `X-Amz-Signature`/`http`). FK cleanup order (`brand_images` before `brands`) present. NOT re-run by verifier (needs Postgres + LocalStack; see W2). Code inspection confirms key read-back only via `findByBrandIdAndStatus(..., PENDING)` — never parsed from `uploadUrl`. |
| T5.6 Coverage gate | done (claimed 100% lines/branches) | Not independently re-measured; new classes are small and every branch is exercised by the re-run tests (all happy/negative paths covered). Accepted as recorded evidence. |
| T6.1 Full regression (197 unit + 86 integration) | done per apply-progress | Not re-run in full (infra-dependent); targeted 22/22 + SpotBugs green re-executed. Transient KAN-10 residue story is plausible and residue is gone (bucket state cannot be checked from here). See W2. |
| T7.1 CORS | done | `localstack-resources.yml` diff narrows to `PUT` / `content-type` / `MaxAge: 3000` / local-only `*` with comment; rule id renamed to `brandImagesBrowserUploads`. Matches browser-report bucket state. |
| T8.1 curl report | done | `reports/2026-09-22-manual-curl-verification.md` exists: 201 + no-store + 3 fields, PENDING row, credential-free PUT `200` + `headObject`, error matrix 404/400/405 + OPTIONS 200. Thorough and credible. |
| T8.2 Browser preflight (mandatory DoD) | done | `reports/2026-09-22-manual-browser-preflight.md` exists: real headless Chrome from `http://localhost:3000` (cross-origin), forced preflight (`PUT` + `application/octet-stream`), `<title>STATUS:200</title>`. DoD satisfied. |
| T9.1 Spec deltas | done | Both delta specs present and coherent with implementation; blob-storage narrow resolves the shipped "no HTTP endpoint wrapping the port" contradiction. |
| T9.2 Docs | done | `docs/data-model.md` write-path note + `docs/backend-standards.md` asset-creation convention / 502 mapping / presigned-URL handling / CORS-as-DoD rule all present in diff. README N/A (no endpoint list) — accepted. |
| T9.3 Scope gate | done | Tracked diff: exactly the 4 design-listed modified files + docs + `spotbugs-exclude.xml` (deviation, justified). Untracked: exactly the 5+3 new files. No migration added (only `flyway/release_0.1/V0.1.0` + `V0.1.1` exist, both pre-existing). No new package (`impl/`, `api/`, `api/model/` pre-exist). Untouched list (`BrandImage`, `BrandImageRepository`, `AssetStatus`, `BlobType`, `BlobStorage`, `S3BlobStorageAdapter`, `BrandService`, `BrandRepository`, `JsonConfig`) — zero diffs. |

## 2. Build / tests / coverage evidence (verifier-executed)

| Check | Command / method | Result |
|---|---|---|
| Targeted unit + controller + handler | `mvn -Dspotbugs.skip=true -Dtest='BrandImageServiceTests,BrandImageControllerTests,GlobalExceptionHandlerTest' test` (with `--add-opens` per apply-progress env note) | **22/22 pass** (10 + 7 + 5), 0 failures/errors/skipped, BUILD SUCCESS |
| SpotBugs | `mvn spotbugs:check` | BugInstance size 0, BUILD SUCCESS |
| Full unit + integration suite | not re-run (needs live Postgres + LocalStack) | Recorded evidence: 197 unit + 86 integration, BUILD SUCCESS (apply-progress T6.1) |
| Integration endpoints | not re-run (same infra reason) | Recorded evidence: 6/6 green + curl/browser reports |
| TDD red-first | apply-progress per-task RED records | Each phase records the watched RED (test-compile failure on missing impl; 500-via-`handleGeneric` before 502 handler; missing controller; 500 on PUT before 405 handler). Accepted as recorded evidence, consistent with test structure. |
| English-only | inspection | New code, tests, specs, docs, reports all English. PASS. |

## 3. Spec compliance matrix

### brands-management — Create brand image upload target

| Scenario (AC) | Implementation | Test proof | Status |
|---|---|---|---|
| Upload target issued, 201 + exactly `uploadUrl`/`uploadMethod=PUT`/`expiresAt`, no `id`/`brandId`/`imageKey`/`status`, one PENDING row with port-issued key (AC1/AC2) | `BrandImageServiceImpl` steps 1–3; `BrandImageController` mapping; `CreateBrandImageResponse` 3 × String | `should_persistPendingImageWithPortIssuedKey…`, `should_returnUploadTargetIssuedByPort…`, controller exact-JSON (3 keys) + D2 absence guard, integration `should_createPendingRowAndReturnUploadTarget…`, curl §1–§2 | PASS |
| Credential-free upload lands at the persisted key (AC3) | Offline SigV4 presign via `BlobStorage` port; no object created at issue time | Integration `should_storeObjectAtIssuedKey…` (plain `HttpClient` PUT, no AWS credentials → `headObject` at row-read key), curl §3 | PASS |
| Non-idempotent: distinct URLs/keys/rows (AC4) | No dedup/caching; each call mints fresh key | Integration `should_createDistinctKeysAndRows…`, curl §3 (second POST) | PASS |
| Missing/soft-deleted → 404 `Brand not found with ID: {brandId}`, no key, no row (AC5/AC7) | Single `findById` (SQLRestriction covers both) → `NotFoundException`; resolve-first ordering | `should_throwNotFound…` ×2 (exact message), `should_notCallBlobStorage…` (`never()`), integration 404 ×2 with row counts, curl §4 | PASS |
| Non-numeric id → 400, no lookup/presign/insert (AC6) | `Long brandId` path variable + existing type-mismatch handler; no code | Controller `shouldReturn400_when_brandIdIsNotNumeric` incl. `verify(..., never())`, curl §4 | PASS |
| Blob-store failure → 502 fixed message, nothing persisted, no leak (AC8) | Exception propagates unwrapped; `never()` save; handler maps to fixed message | `should_notPersist…`, `should_propagate…` (assertSame), handler 502 test (absent upstream text/URL/signature), controller 502 test | PASS |
| Persistence failure → rollback, URL never disclosed (AC9) | `@Transactional(rollbackFor = Exception.class, Propagation.REQUIRED)`; URL returned only after save | Structural (annotation inspected) — see W1: no dedicated failing-insert test | PASS with note (W1) |
| URL never retained + `Cache-Control: no-store` (AC10/AC11/AC12) | Key-only `log.info(id, brandId, imageKey)`; row holds opaque key only; controller `CacheControl.noStore()`; placeholder OpenAPI example | ListAppender no-URL test, integration `should_notPersistAnyUrl…`, controller no-store test, curl `Cache-Control: no-store` header | PASS |
| `PUT` collection URI → 405 (AC13) | Dedicated `HttpRequestMethodNotSupportedException → 405` handler (deviation 1, justified: shared `Exception → 500` catch-all swallowed Spring's default — watched RED) | Controller `shouldReturn405_when_putIsUsed`, curl §4 | PASS |
| Body/caller key ignored, port-issued key only | No `@RequestBody` on `BrandImageApi.createUploadTarget` (verified: zero `RequestBody` in new API); service uses `target.key()` only | Structural + `BRAND_IMAGE` captor guard | PASS |
| Browser preflight permitted (AC14) | Explicit bucket CORS (`PUT`, `content-type`, `MaxAge: 3000`, local `*`) | Browser report `STATUS:200` (real Chrome, forced preflight); curl OPTIONS 200 | PASS |

### brands-management — Standard error response shape (MODIFIED)

| Scenario | Implementation | Test proof | Status |
|---|---|---|---|
| Validation 400 with field errors | Pre-existing handlers, untouched | Pre-existing tests green per claim; new 400 path tested | PASS |
| 404 standard shape with descriptive message | Pre-existing `NotFoundException` handler | Controller 404 asserts `status`/`errors[0].field`/`message` | PASS |
| 502 fixed-message, no URL/signature/upstream text | New handler, `field: "general"`, fixed string | Handler + controller 502 tests assert exact message and absence of leak material | PASS |

### blob-storage — Scope exclusions (MODIFIED, corrective narrow)

| Scenario | Implementation | Test proof | Status |
|---|---|---|---|
| No HTTP endpoint / entity / repo / service / migration of its own; no size/content-type restriction; no reclamation; no second implementation; no new config property | Zero diffs in `BlobType`, `BlobStorage`, `S3BlobStorageAdapter`, `JsonConfig`; no migration; no new package; single bucket + existing TTL | Structural (diff-name inspection) | PASS |

## 4. Correctness table (adversarial spot-checks)

| Probe | Result |
|---|---|
| Non-canonical/foreign key reaching `BrandImage.pending()` | Rejected (`IllegalArgumentException` → 500 via `handleGeneric`); pinned by D8 test; no new 400 handler (correct — would mask bugs codebase-wide). |
| Double-submit / retry | By design non-idempotent; each call mints distinct key/row (AC4 tested). Orphan-PENDING accepted debt with `ix_brand_images_pending_created` sweeper path (F4). Documented, not silent. |
| 404 ordering (no key minted for bad brand) | `verify(blobStorage, never())` pins resolve-first. |
| 502 message echo | Fixed string only; `ex.getMessage()` never used in handler; `log.error` keeps stack trace server-side only. |
| URL leak surfaces (logs, rows, metrics, examples) | Key-only log line; native-row scan test; placeholder OpenAPI example; no metric tags added. |
| `Instant`/`HttpMethod` serialization traps (D7) | DTO uses 3 × `String` with `toString()`/`name()` mapping; exact-JSON tests pin `PUT` string + string `expiresAt` + exactly 3 keys. `JsonConfig` untouched. |
| Lazy-proxy trap (`open-in-view`) | Controller never touches `BrandImage`/proxy — returns port target mapped to DTO. Structurally proof by construction. |
| In-transaction presign (D6) | Justified offline-SigV4 exception, documented in impl javadoc + design; guard recorded for future port changes. |
| Unauthenticated minting | Accepted risk behind no-production-exposure gate (design Risks); not verifiable in code. Flagged for F8, not a blocker here. |
| `PUT` swallowing by catch-all `Exception → 500` | Found and fixed via dedicated 405 handler (deviation 1); now covered by test. Adversarial note: any future specific exception added AFTER the generic handler would still be safe (Spring prefers most-specific), but a new exception type falling through to `handleGeneric` → 500 remains the default — reviewers should keep asserting status per new error path. |

## 5. Design coherence (D1/D2/D3/D5 + deviations)

| Decision | Conformance |
|---|---|
| D1 `POST` + 201, no body, `PUT` → 405 | PASS — deviation 1 (explicit 405 handler) is required and justified, not drift. |
| D2 exactly three `String` fields; row invisible | PASS — exact-JSON + absence-guard tests pin it; service returns port target, never the entity. |
| D3 separate `BrandImageService` (SRP/ISP) | PASS — new service owns only asset-target orchestration; `BrandService` untouched. |
| D5 `BlobStorageException` → 502 fixed message | PASS — handler + tests match design §4.5 exactly. |
| Deviation 2 (explicit constructor vs `@RequiredArgsConstructor`) | Justified (matches `BrandServiceImpl` convention; same SpotBugs behavior). No concern. |
| Deviation 3 (`spotbugs-exclude.xml` services `EI_EXPOSE_REP2`) | Justified (mirrors existing entries; constructor-injected collaborators are not exposure bugs). `spotbugs:check` green. |

SOLID/DDD reading: SRP/ISP (one-operation service, wide `BrandService` untouched), DIP (depends on `BlobStorage` port, never S3 concretion), OCP (second asset copies the triple; generic extraction deferred per DRY Rule of Three — correct call at first occurrence), DRY (key scheme/TTL single-sourced), ubiquitous language (`upload target`, `PENDING`, `imageKey`, `BlobUploadTarget`) consistent across specs/logs/code. No `Manager`/`Helper`/`Utils` smells.

## 6. Issues

### CRITICAL
None.

### WARNING
- **W1 — AC9 (persistence-failure rollback) has no dedicated failing-insert test.** The guarantee rests on `@Transactional(rollbackFor = Exception.class, REQUIRED)` + returning the URL only after `save`. Ordering is correct and the annotation is inspected, but no test forces `brandImageRepository.save` to throw and asserts the URL is never returned / no row persists. Recommend adding one unit test (`save` throws → exception propagates, `BlobUploadTarget` never returned) before archive. Not a blocker: Spring rollback semantics for an unchecked exception escaping a `REQUIRED` transaction are well-established, and the code path cannot return the URL after a throw.
- **W2 — Full suite (197 + 86) and integration endpoints not re-executed by the verifier** (no live Postgres/LocalStack in this session). Targeted 22/22 + SpotBugs green re-executed; integration behavior cross-checked via code inspection + curl/browser reports. Recommend the archiver confirm the last full `mvn verify` state is the current tree (no edits since apply).

### SUGGESTION
- **S1 — `handleMethodNotSupported` ignores `ex.getSupportedMethods()` in the message.** Current fixed message `Method not allowed` is fine and consistent with other handlers; optionally include an `Allow` header. Cosmetic only.
- **S2 — Consider a `verify(brandImageRepository, never()).save(...)` companion on the 404 controller path** (currently pinned at service level + row-count at integration level). Defense in depth for the ordering guarantee; optional.

## 7. Verdict

**PASS WITH WARNINGS** — no CRITICAL findings; all AC1–AC14 scenarios evidenced by tests and/or manual reports; D1/D2/D3/D5 conformant; scope gate clean (no migration, no new package, untouched list intact); docs + CORS + browser DoD present. Warnings W1/W2 should be addressed or explicitly accepted before archive.

### Recommended next steps (before archive)
- Add the W1 rollback unit test (small, high-value), or record explicit acceptance in the archive note.
- Confirm full-suite green corresponds to the current tree (W2) — re-run `mvn verify` with the documented `--add-opens` flags if any file changed since apply.
- Then proceed to code-review → archive.
