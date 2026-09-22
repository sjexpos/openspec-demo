# Code Review — kan-11-brand-image-upload-target

**Change**: `kan-11-brand-image-upload-target` (branch `feat/kan-11-brand-image-upload-target`)
**Date**: 2026-09-22
**Reviewer mode**: code-review sub-agent (read-only; no production code written)
**Sources**: `openspec/changes/kan-11-brand-image-upload-target/` (proposal.md, design.md, specs/brands-management/spec.md, specs/blob-storage/spec.md, tasks.md, apply-progress.md, verify-report.md, reports/2026-09-22-manual-curl-verification.md, reports/2026-09-22-manual-browser-preflight.md); working-tree diff vs `main` (tracked diff + 5 new main files + 3 new test files read directly); standards `AGENTS.md`, `docs/backend-standards.md`.
**Skills applied**: code-auditing, adversarial-review, solid-principles, dry-principle, java-jpa-hibernate, test-driven-development.

## Scope verified

- Tracked modifications (7): `docs/backend-standards.md`, `docs/data-model.md`, `localstack-resources.yml`, `spotbugs-exclude.xml`, `GlobalExceptionHandler.java`, `ServiceTest.java`, `GlobalExceptionHandlerTest.java`.
- New files (8): `BrandImageService.java`, `BrandImageServiceImpl.java`, `BrandImageApi.java`, `CreateBrandImageResponse.java`, `BrandImageController.java`, `BrandImageServiceTests.java`, `BrandImageControllerTests.java`, `BrandImageEndpointsTests.java`.
- No migration added (only pre-existing `flyway/release_0.1/V0.1.0` + `V0.1.1` exist; zero migration diffs). No new package (`impl/`, `api/`, `api/model/` pre-exist).
- Untouched list confirmed clean: zero diffs in `BrandImage`, `BrandImageRepository`, `AssetStatus`, `BlobType`, `BlobStorage`, `S3BlobStorageAdapter`, `BrandService`, `BrandRepository`, `JsonConfig`.
- 3 documented deviations in `apply-progress.md` all justified (see Findings G-01..G-03 context below).

## Adversarial review

**Scope**: KAN-11 working-tree changes (`feat/kan-11-brand-image-upload-target` vs `main`)
**Sources**: specs/design/tasks/verify/reports listed above + full diff reference

### Spec and task alignment

- All 28/28 tasks map to implementation: T1.2 interface exact signature; T2.1 mock bag exactly +2 beans; T2.2–T2.9 ten service tests present; T3.1 502 handler + test; T4.1/T4.2 controller/DTO/API + exact-JSON + D2 absence guard + no-store/400/404/502/405; T5.1–T5.5 six integration tests with correct FK cleanup order (`brand_images` before `brands`) and key read-back only via `findByBrandIdAndStatus(..., PENDING)`; T6.1 full regression claimed 197 unit + 86 integration green; T7.1 CORS narrowed; T8.1/T8.2 curl + mandatory browser preflight reports present; T9.1/T9.2/T9.3 spec deltas + docs + scope gate.
- AC1–AC14 matrix in `verify-report.md` §3 re-checked against code: each mapping holds on inspection (ordering resolve→presign→persist, `BRAND_IMAGE` literal, fixed 502, key-only logging, `no-store`, placeholder OpenAPI example, CORS rule).

### Findings

| Severity | Area | Finding | Evidence | Suggested fix (code / spec / tests) |
|----------|------|---------|----------|--------------------------------------|
| Minor | Tests / AC9 rollback | No dedicated failing-insert test: persistence-failure rollback rests on `@Transactional(rollbackFor = Exception.class, REQUIRED)` + URL returned only after `save`, but no test forces `save` to throw and asserts the URL is never returned. | `BrandImageServiceImpl.createUploadTarget` lines 63–90; `BrandImageServiceTests` has `never()`-save on presign failure but no `save`-throws case; verifier W1. | Tests: add one unit test (`brandImageRepository.save` throws `RuntimeException` → exception propagates, target never returned, row absent). Or record explicit acceptance in archive note. Non-blocking: Spring rollback semantics for unchecked exception escaping REQUIRED are well-established and ordering is correct. |
| Minor | API layering | `BrandImageApi.createUploadTarget` carries both `@ResponseStatus(CREATED)` and a `ResponseEntity<DataResponse<...>>` return; the `ResponseEntity.status(CREATED)` in the controller governs, the annotation is redundant. | `BrandImageApi.java:39-40`, `BrandImageController.java:51-53`. | Code (optional cleanup): remove the redundant `@ResponseStatus` or keep deliberately for documentation; no behavior change. Follow-up allowed. |
| Minor | Error UX / 405 | `handleMethodNotSupported` returns fixed `Method not allowed` with no `Allow` header and ignores `ex.getSupportedMethods()`. Consistent with other handlers, AC13 satisfied. | `GlobalExceptionHandler.java:118-123`; verifier S1. | Code (optional): add `Allow: POST` header on the 405 path. Cosmetic; post-archive follow-up acceptable. |
| Minor | Tests (defense in depth) | 404 ordering (`never()` presign/save) is pinned at service level and row-count at integration level, but no `verify(brandImageRepository, never()).save(...)` companion on the 404 controller path. | `BrandImageControllerTests` (7 tests), `BrandImageServiceTests.should_notCallBlobStorage_*`; verifier S2. | Tests (optional): add the companion `never()` verification. Post-archive acceptable. |
| Question | Ops / CORS | Bucket CORS uses `AllowedOrigins: ['*']` locally with an explicit "local-only; production must list explicit app origins" comment; production rule rides KAN-14/KAN-12 F7. Mechanism proven by browser report, production values not proven here. | `localstack-resources.yml` CorsRules (`Id: brandImagesBrowserUploads`, `PUT` / `content-type` / `MaxAge: 3000`); browser report `STATUS:200`. | Spec/infra: confirm production CORS values on the infrastructure track before any production exposure. Not a blocker for this change (no-production-exposure gate). |
| Question | Security (accepted risk) | Unauthenticated capability minting: no auth exists codebase-wide; this browser-reachable endpoint makes the gap non-theoretical. Documented as accepted risk behind the no-production-exposure gate, F8 gates production. | `design.md` Risks; `proposal.md` Impact. | No code fix in this slice; archiver confirms the gate note. F8 (auth + rate limiting) remains mandatory before production. |
| Question | Data lifecycle (accepted debt) | `PENDING` orphans from unconfirmed uploads / retries are by-design debt awaiting F4 sweeper via `ix_brand_images_pending_created`. | `docs/data-model.md` write-path note; design Risks. | No fix here; F4 owns reclamation. |

### Positive controls (adversarial checks that held)

- **No URL leak**: service logs key only (`log.info(id, brandId, imageKey)`, `BrandImageServiceImpl:82-86`); controller never logs; new handlers use fixed strings only — `ex.getMessage()` never used in the two new handler cases (pre-existing `ex.getMessage()` echoes in older handlers are out of scope); OpenAPI `uploadUrl` example is placeholder-only (`https://example.invalid/...`); integration native-row scan asserts no `X-Amz-Signature`/`http` in any column.
- **Fixed 502**: `BlobStorageException → 502` with `field: "general"`, message `Blob storage is currently unavailable`; `log.error` keeps stack trace server-side only.
- **`no-store`**: `CacheControl.noStore()` on the 201 path + controller test pinning the header.
- **CORS**: rule narrowed to documented minimum (`PUT`, `content-type`, `MaxAge: 3000`, local-only `*` with comment); proven by real headless-Chrome preflight `STATUS:200` from `http://localhost:3000`, not just curl OPTIONS.
- **Ordering**: resolve-first pins (`verify(blobStorage, never())` on 404); presign-failure pins (`verify(save, never())` + `assertSame` propagation); `BRAND_IMAGE` copy-paste captor guard; D8 non-canonical-key `IllegalArgumentException → 500` pin with no new 400 handler (correct — avoids masking bugs codebase-wide).
- **Serialization traps (D7)**: DTO is 3×`String` with `url().toString()` / `method().name()` / `expiresAt().toString()`; exact-JSON tests pin `PUT` string + string `expiresAt` + exactly 3 keys; `JsonConfig` untouched.
- **Lazy-proxy trap**: controller maps port-issued `BlobUploadTarget` directly, never touches `BrandImage`/lazy `Brand` proxy — open-in-view-proof by construction.
- **In-transaction presign (D6)**: explicit justified exception (offline SigV4, no `S3Client` call), documented in impl javadoc + design with a move-outside guard if the port ever gains network I/O.
- **Quality gates**: targeted re-verification evidence (22/22 in verify-report) + `spotbugs:check` green; full-suite and integration re-runs accepted as recorded evidence (infra-dependent here).

## Verdict

**PASS WITH GAPS** — no Blockers, no Majors; four Minors (one rollback-test gap + three cosmetic/defense-in-depth optionals) and three accepted-risk/debt Questions (CORS production values, unauthenticated minting gate, PENDING sweep) tracked above.

### Recommended next steps (before archive)

- Add the W1 rollback unit test (small, high-value), or record explicit acceptance in the archive note.
- Confirm full-suite green corresponds to the current tree (re-run `mvn verify` with the documented `--add-opens` flags if any file changed since apply).
- Then proceed to archive (`opsx-archive`); optional Minors (redundant `@ResponseStatus`, `Allow` header, 404-controller `never()` companion) may ride as follow-ups.

## Status

Final verdict `PASS WITH GAPS`.
