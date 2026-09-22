# KAN-11 Brand Image Upload Target Endpoint — Archive Report

- **Change:** `kan-11-brand-image-upload-target` — First HTTP surface over the asset pipeline (KAN-12 F1): `POST /api/brands/{brandId}/images` wiring brand resolution → `BlobStorage.createUploadTarget(BRAND_IMAGE)` → `BrandImage.pending` persistence, returning a credential-free presigned `PUT` target; establishes the reusable asset-creation contract
- **Schema:** `story-sdd`
- **Branch:** `feat/kan-11-brand-image-upload-target` (based on KAN-12 `50448c1`; working tree only, no commits per automatic-chain rule)
- **Archived on:** 2026-09-22
- **Archived to:** `openspec/archive/2026-09-22-kan-11-brand-image-upload-target/`

## Summary

The KAN-11 brand-image upload-target change was archived. The full SDD pipeline completed with the following results:

- **Proposal phase:** Follow-up F1 of KAN-12 and first HTTP surface over the asset pipeline — composes the shipped KAN-10 port and KAN-12 persistence path behind a `brands-management` operation, keeping image bytes off the API via client-to-S3 direct upload. Decisions D1 (`POST` + `201`, `PUT` → `405`) and D2 (exactly three `String` fields, row invisible — structurally forcing KAN-12 F2 confirmation to S3 → SQS) closed at proposal; D3–D5 dissolved by D2; D4 (no `PENDING` cap) and D6 (separate service) recommendations adopted. Out-of-scope items explicitly named as KAN-12 follow-ups (F2 confirmation, F3 listing/reading, F4 delete/reclaim/sweep, F7 size/virus, F12 MIME, auth/rate limiting).
- **Spec phase:** No new capability (composition only). `ADDED` `brands-management` requirement (Create brand image upload target, 11 scenarios) + `MODIFIED` `brands-management` requirement (Standard error response shape, fixed-message 502 scenario) + blocking corrective `MODIFIED` `blob-storage` requirement (Scope exclusions narrow — without it the shipped "no HTTP endpoint wrapping the port" scenario contradicts this change).
- **Design phase:** 8 decisions (D1–D8) with trade-offs named, mermaid flow/class/sequence diagrams, explicit CORS prerequisite (A1: browser caller confirmed — highest risk), zero-migration plan with rollback (presigned URLs expire via `aws.s3.presign-ttl`, orphaned `PENDING` rows await F4 sweep), and open questions deferred to F11/F12.
- **Tasks phase:** 28/28 checkboxes complete across phases 1–9 (service contract, service orchestration + 9 unit tests + regression, 502 handler, API/DTO/controller + exact-JSON, integration endpoints + credential-free PUT, full regression, CORS, curl + mandatory browser preflight, docs + spec deltas + scope gate). TDD red-first throughout; coverage gate 100% lines/branches on new classes (JaCoCo).
- **Apply phase:** All phases delivered per `apply-progress.md` — `BrandImageService`/`BrandImageServiceImpl` (resolve → presign → persist, key-only logging), `BrandImageApi`/`CreateBrandImageResponse` (3 × `String`)/`BrandImageController` (`no-store`, no entity touch, no `Location`), `BlobStorageException → 502` handler, narrowed bucket CORS, curl + real-Chrome preflight reports, spec deltas + `docs/data-model.md` §9 + `docs/backend-standards.md` (asset-creation convention, 502 mapping, presigned-URL handling, CORS-as-DoD) applied. 3 documented deviations, all principled (see Decisions).
- **Verify phase:** `PASS WITH WARNINGS` — targeted 22/22 re-executed by the verifier (10 service + 7 controller + 5 handler) + SpotBugs green re-executed; all AC1–AC14 scenarios evidenced; D1/D2/D3/D5 conformant; scope gate clean (no migration, no new package, untouched list intact); 2 warnings (W1/W2, accepted below) + 2 suggestions (S1/S2, optional).
- **Code Review phase:** `PASS WITH GAPS` (adversarial red-team) — no Blockers, no Majors, no CRITICAL; 4 Minors (W1 rollback-test gap + redundant `@ResponseStatus` + `Allow` header + 404-controller `never()` companion) and 3 accepted-risk/debt Questions (production CORS values, unauthenticated minting gate, `PENDING` sweep), all non-blocking and tracked below. Archiving explicitly advised.

## Decisions (final state)

- **D1** `POST` + `201`, no body, `PUT` → `405` — non-idempotent collection create; matches `docs/backend-standards.md` and all three existing creates. Deviation 1 (explicit 405 handler) required and justified: the shared `Exception → 500` catch-all swallows Spring's default (watched RED: PUT returned 500).
- **D2** Exactly three `String` fields; row is an invisible side effect — `status` always `PENDING` (constant noise), `imageKey` internal, `id` deliberately withheld (forces F2 to S3 → SQS and requires widening the `products/`-only notification filter). Dissolves ticket record, `application/services/model/` package, and `Location` header; neutralises the lazy-`Brand`/`open-in-view` trap structurally.
- **D3** Separate `BrandImageService` (SRP/ISP) — confirm/list/delete (F2/F3) land next to it, not on the wide `BrandService`.
- **D4** Service returns `BlobUploadTarget` directly (DIP: depends on the port, never the S3 concretion). Deviation 2 (explicit constructor vs `@RequiredArgsConstructor`) justified by `BrandServiceImpl` convention.
- **D5** `BlobStorageException` → `502` fixed message `Blob storage is currently unavailable` (`field: "general"`, `log.error` keeps stack trace) — first HTTP exposure of the port.
- **D6** In-transaction presign is an explicit, justified exception — offline SigV4 HMAC only (no `S3Client` call, p95 < 5 ms), keeping resolve → presign → persist atomic (AC9). Guard recorded: if the port ever gains network I/O, presign moves outside the transaction. Deviation 3 (`spotbugs-exclude.xml` services `EI_EXPOSE_REP2`, mirroring existing entries) justified; `spotbugs:check` green.
- **D7** `String` DTO types load-bearing (`url().toString()` / `method().name()` / `expiresAt().toString()`; `JsonConfig` bare mapper, no `JavaTimeModule`) — pinned by exact-JSON tests. Global `JavaTimeModule` registration deferred to F9.
- **D8** `IllegalArgumentException` from `BrandImage.pending()` stays `500` (unreachable programming error; a 400 handler would mask bugs codebase-wide) — pinned by unit test.

## Spec Sync

Per the `story-sdd` schema (ADDED + MODIFIED reconciliation), the delta specs were sync-promoted into the main OpenSpec spec store as canonical content, following the KAN-10/KAN-12 precedent (delta `## ADDED`/`## MODIFIED Requirements` wrappers stripped to canonical `## Requirements`):

- **Updated canonical spec:** `openspec/specs/brands-management/spec.md`
  - The delta `ADDED` requirement `Create brand image upload target` (+ 11 scenarios) appended after the existing `Brand image persistence and lifecycle` requirement; no existing requirement reordered or removed.
  - The delta `MODIFIED` requirement `Standard error response shape` was **replaced in place**: body gained the upstream blob-store 502 sentence and the `Blob store failure returns fixed-message 502` scenario; all pre-existing paragraphs and scenarios untouched. Now 10 requirements.
- **Updated canonical spec:** `openspec/specs/blob-storage/spec.md`
  - The delta `MODIFIED` requirement `Scope exclusions` was **replaced in place**: the capability itself SHALL NOT expose HTTP/endpoint/entity/repo/service/migration while consuming capabilities MAY orchestrate the port; the shipped "no HTTP endpoint wrapping the port" scenario reworded to "no HTTP endpoint of its own" (blocking corrective — the suite is coherent again). All pre-existing paragraphs and scenarios otherwise untouched.
  - No other existing specs modified (`asset-lifecycle`, `aws-s3-integration`, `products-management` untouched).

## Specs Synced

| Domain | Action | Details |
|--------|--------|---------|
| brands-management | Updated | 1 requirement ADDED (Create brand image upload target, 11 scenarios) + 1 requirement MODIFIED (Standard error response shape, +1 scenario); now 10 requirements |
| blob-storage | Updated | 1 requirement MODIFIED in place (Scope exclusions corrective narrow); requirement/scenario counts unchanged |

## Archive Contents

Preserved verbatim from `openspec/changes/kan-11-brand-image-upload-target/`:

- `proposal.md` ✅
- `design.md` ✅
- `tasks.md` ✅ (28/28 checkboxes complete)
- `apply-progress.md` ✅ (phases 1–9, 3 principled deviations, environment notes, no-commit boundary noted)
- `verify-report.md` ✅ (PASS WITH WARNINGS: W1/W2 + S1/S2, non-blocking)
- `specs/` ✅ (`specs/brands-management`, `specs/blob-storage` deltas retained in archive; canonical specs promoted separately in `openspec/specs/`)
- `reports/` ✅
  - `2026-09-22-code-review.md` (PASS WITH GAPS: 4 Minors + 3 Questions, non-blocking)
  - `2026-09-22-manual-curl-verification.md` (201 + no-store + 3 fields, PENDING row, credential-free PUT 200 + `headObject`, error matrix 404/400/405 + OPTIONS 200)
  - `2026-09-22-manual-browser-preflight.md` (mandatory DoD: real headless Chrome from `http://localhost:3000`, forced preflight, `STATUS:200`)
- `archive-report.md` ✅ (this report)

## Source of Truth Updated

The canonical spec store now reflects the new behavior:

- `openspec/specs/brands-management/spec.md` — brand image upload-target creation operation + fixed-message 502 error shape (source of truth).
- `openspec/specs/blob-storage/spec.md` — scope exclusion narrowed so consuming capabilities may orchestrate the port (source of truth).

Technical documentation per `docs/documentation-standards.md` was updated inside the change itself (no further doc action at archive, assessed under the `update-docs` skill): `docs/data-model.md` (§9 write path: created only by `POST`, always `PENDING`, port-issued key; confirmation/reclamation absent) and `docs/backend-standards.md` (asset-creation convention, 502 mapping, presigned-URL handling incl. 405 note, CORS-as-DoD rule). README has no endpoint list → N/A. English-only holds in all new artifacts.

## Verification and Code-Review History

### Verification (opsx-verify): `PASS WITH WARNINGS`, zero CRITICAL

- **Tests:** targeted 22/22 re-executed (`BrandImageServiceTests` 10 + `BrandImageControllerTests` 7 + `GlobalExceptionHandlerTest` 5), BUILD SUCCESS; SpotBugs green re-executed (0 instances). Full suite (197 unit + 86 integration, BUILD SUCCESS) and 6/6 integration endpoints accepted as recorded evidence (infra-dependent here; see W2).
- **Quality gates:** coverage 100% lines/branches on `BrandImageServiceImpl`, `BrandImageController`, `CreateBrandImageResponse`; TDD red-first records per phase accepted as consistent with test structure; English-only PASS.
- **Spec compliance:** all AC1–AC14 scenarios traced to passing tests and/or manual reports; D1/D2/D3/D5 conformant; no spec drift found.

### Code Review (adversarial red-team): `PASS WITH GAPS`, no Blocker, no Major

All findings non-blocking; carried as follow-ups below.

## Accepted Gaps (explicit archive acceptance)

- **W1 — AC9 persistence-failure rollback has no dedicated failing-insert test.** Guarantee rests on `@Transactional(rollbackFor = Exception.class, REQUIRED)` + URL returned only after `save`. Ordering is correct and the annotation is inspected; no test forces `save` to throw. **Accepted at archive**: Spring rollback semantics for an unchecked exception escaping a `REQUIRED` transaction are well-established and the code path cannot return the URL after a throw. Recommended follow-up: one unit test (`save` throws → exception propagates, target never returned).
- **W2 — Full suite (197 + 86) and integration endpoints not re-executed by verifier/CI-independent sessions** (no live Postgres/LocalStack in those sessions). Targeted 22/22 + SpotBugs green re-executed; integration behavior cross-checked via code inspection + curl/browser reports; apply reports bucket residue fully cleaned (bucket empty, demo tables at 0 rows, app stopped). **Accepted at archive** on recorded evidence. Recommended follow-up: confirm `mvn verify` green on the current tree (with the documented `--add-opens` flags) before merge if any file changed since apply.

## Production Gates (MUST hold before any production exposure)

- **Prod CORS gate:** the shipped bucket rule (`localstack-resources.yml`, id `brandImagesBrowserUploads`: `PUT`, `content-type`, `MaxAge: 3000`, `*` local-only with explicit comment) proves the mechanism via real-browser preflight. **Production bucket MUST list explicit app origins** — rides the infrastructure track (KAN-14 / KAN-12 F7). Not proven here.
- **Auth gate:** no auth exists codebase-wide; this browser-reachable endpoint makes unauthenticated capability minting non-theoretical. **Accepted risk behind the no-production-exposure gate** — F8 (auth + rate limiting) gates production.

## Risks Carried Forward (tracked follow-ups)

Explicitly out of scope in KAN-11 (proposal Out of scope + design Non-Goals/Risks + verify/code-review findings); to be raised in the tracker, not built:

- **F2** Upload confirmation `PENDING → UPLOADED` — now forced to the S3 → SQS server-side model by D2 (withheld `id`) and larger than written; requires widening the `products/`-only notification filter. Re-estimate.
- **F3** Listing/reading + `urlFor(key)` (read endpoint does not exist; D2 forbids `Location` until then).
- **F4** Delete/reclaim/sweep — `PENDING` orphans from unconfirmed uploads accepted debt; sweeper path via `ix_brand_images_pending_created`; retention window input from F11.
- **F5** Rollout template to `brand_videos`, `strain_images`, `product_images`, `dispensary_images` (copy the triple verbatim; generic `AssetUploadService<T>` extraction deferred per DRY Rule of Three to the 2nd/3rd occurrence).
- **F7** Size limits / virus scan (not retroactively fixable from the key; no endpoint change designed).
- **F8** Auth + rate limiting — mandatory production gate (see above).
- **F9** Global `JavaTimeModule` registration (cross-cutting; would obsolete the `String`-DTO precedent) — deferred.
- **F10** `ServiceTest` shared mock-bag split (churn risk logged; all service suites re-run green).
- **F11** Presign-TTL sizing for backgrounded mobile uploads (feeds F4's retention window; does not change this design).
- **F12** Declared MIME type — a contract change to this endpoint (port signature + request body + allow-list `400` + likely migration); CORS already forward-compatible (`content-type` allowed). Schedule before F3 if possible (wrong/missing `Content-Type` degrades F3 reads).
- **Optional minors (post-archive eligible):** redundant `@ResponseStatus(CREATED)` on `BrandImageApi` (controller's `ResponseEntity.status(CREATED)` governs); `Allow: POST` header on the 405 path (verifier S1); `verify(brandImageRepository, never()).save(...)` companion on the 404 controller path (verifier S2, defense in depth).

## Branch Ready for PR (for orchestrator)

Working tree uncommitted on `feat/kan-11-brand-image-upload-target` (tracked: 7 modified files; untracked: 8 new source/test files + change artifacts, now archived). Suggested chained PRs recorded in tasks/design:

1. **PR1 service + 502** — `BrandImageService`/`BrandImageServiceImpl` + `BrandImageServiceTests` + `ServiceTest` mock bag + `GlobalExceptionHandler` 502 case + handler test.
2. **PR2 controller** — `BrandImageApi` + `CreateBrandImageResponse` + `BrandImageController` + controller tests.
3. **PR3 integration** — `BrandImageEndpointsTests` (needs Postgres + LocalStack).
4. **PR4 CORS + docs** — `localstack-resources.yml` CORS + `docs/data-model.md` + `docs/backend-standards.md` + canonical spec sync + archive directory.

Single-PR merge remains acceptable given the automatic chain (one composable slice, all gates green), but the split keeps each review focused.

## Commit Boundary — DO NOT COMMIT RESPECTED

Per explicit orchestrator instruction, **nothing was committed by this archive**: the working tree is left uncommitted on branch `feat/kan-11-brand-image-upload-target`, containing the implementation, the canonical spec sync (`openspec/specs/brands-management/spec.md` updated; `openspec/specs/blob-storage/spec.md` updated), and the archive directory move (this report included; active change directory `openspec/changes/kan-11-brand-image-upload-target/` removed). Committing and pushing (conventional commits, English-only) is owned by the orchestrator.

## Symlink Integrity (AGENTS.md §5)

No skill/artifact renames in this change; no symlinks touched. Canonical `.agents` source intact (not modified by archive file operations).

## SDD Cycle Complete

The change has been fully planned, specified (1 ADDED + 2 MODIFIED requirements, 13 scenarios), designed (D1–D8), implemented and verified (28/28 tasks, 197 unit + 86 integration green on record, 22/22 targeted re-executed, 0 CRITICAL, strict gates pass), code-reviewed (PASS WITH GAPS, gaps non-blocking and tracked above), and archived. The main specs are promoted to `openspec/specs/brands-management/spec.md` (ADDED requirement + MODIFIED error shape) and `openspec/specs/blob-storage/spec.md` (MODIFIED scope exclusion) as the source of truth going forward.

Ready for the next change.
