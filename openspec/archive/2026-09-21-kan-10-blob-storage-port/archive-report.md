# KAN-10 Blob Storage Port + S3 Adapter — Archive Report

- **Change:** `kan-10-blob-storage-port` — Storage-agnostic `BlobStorage` port plus first AWS S3 implementation (presigned PUT targets + validated batch removal)
- **Schema:** `story-sdd`
- **Branch:** `feat/KAN-10-blob-storage-port`
- **Archived on:** 2026-09-21
- **Archived to:** `openspec/archive/2026-09-21-kan-10-blob-storage-port/`

## Summary

The KAN-10 Blob Storage Port change was archived. The full SDD pipeline completed with the following results:

- **Proposal phase:** Scope is the developer-facing foundation slice unblocking the four queued asset stories (`brand_images`, `brand_videos`, `strain_images`, `product_images`); consumes the KAN-14 `aws.s3.bucket` / `aws.s3.presign-ttl` properties (zero consumers until now). Non-goals explicitly named (HTTP endpoint, persistence, size/type restriction, orphan reclamation, second implementation, bucket hardening). Eight open assumptions recorded for sign-off.
- **Spec phase:** New `blob-storage` capability — 5 requirements, 17 scenarios, all concrete and testable; plus a `MODIFIED` reconciliation of the KAN-14 `aws-s3-integration` scope fence (1 requirement updated, 1 scenario added).
- **Design phase:** 5 decisions (D1–D5) with trade-offs named, mermaid architecture + sequence + class diagrams, full `createUploadTarget` / `remove` algorithms, error-translation table, and KAN-14 reconciliation rationale (§5.5).
- **Tasks phase:** 32/33 checkboxes complete across phases 1–12; 10.2 (optional TTL-expiry test, timing-flake risk) explicitly skipped per task optionality.
- **Apply phase:** All 12 phases delivered per `apply-progress.md` — `BlobType` enum + `isCanonicalKey`, `BlobUploadTarget` record + `BlobStorageException`, frozen 2-operation port, mocked-presigner `createUploadTarget`, `@NotBlank bucket` hardening (+ `@Valid` cascade deviation, documented), fail-fast `remove` guards, 1000-key chunking, `errors()` translation, `ListAppender` confidentiality pin, 6/6 LocalStack integration tests, README + backend-standards docs, green quality gates. Documented deviations: `@Valid` cascade addition, `S3Error` (not `Error`) per-key error type in SDK v2 2.55.1, 33-hex-char test fixture corrected by the guard itself.
- **Verify phase:** `PASS WITH WARNINGS` — `mvn verify` re-run green (120 unit + 69 integration, 0 failures); 100% line/branch coverage on all new classes; all 17 `blob-storage` scenarios traced to passing tests; DoD §6 items 1–11 fully met, item 12 (commit boundary) delegated to archive.
- **Code Review phase:** `PASS WITH GAPS` (adversarial red-team) — no Blocker, no Major; 4 Minor + 2 Questions, all non-blocking and tracked as follow-ups. Archiving explicitly advised.

## Decisions (final state)

- **D1** Presigned PUT, not POST policy — simplest contract (`url` + `method` + `key`), matches existing CORS rule; no server-side size/type enforcement (hardening follow-up raised, not built).
- **D2** Nested prefixes (`products/images/`, not `product-images/`) — preserves the existing `products/` SQS filter on `develop-products-assets-events-queue`.
- **D3** Opaque UUID key, no file extension — zero caller input, zero path-traversal surface, safe verbatim in `varchar` URL columns.
- **D4** `org.springframework.http.HttpMethod` as the `method` type — a protocol type, not a vendor type; reversible for a 6-line bespoke enum.
- **D5** `expiresAt` included (`Instant.now().plus(ttl)`), no injected `Clock` (YAGNI; tolerance-window tests).

## Spec Sync

Per the `story-sdd` schema (new capability + MODIFIED reconciliation), the delta specs were sync-promoted into the main OpenSpec spec store as canonical content, following the KAN-14 precedent (delta `## ADDED`/`## MODIFIED Requirements` wrappers stripped to canonical `## Requirements`; Purpose retained verbatim for the new capability):

- **New canonical spec:** `openspec/specs/blob-storage/spec.md`
  - Form: canonical `## Requirements` (delta `## ADDED Requirements` wrapper stripped).
  - Content: 5 requirements / 17 scenarios, preserved verbatim from the delta body (verified: diff shows only the wrapper-header change).
  - Purpose section: retained verbatim from the delta.
- **Modified canonical spec:** `openspec/specs/aws-s3-integration/spec.md`
  - The KAN-14 construction-time fence (`"SHALL NOT introduce a domain port, storage adapter…"`) replaced with the reconciliation sentence recording the beans as consumed by the first storage adapter with unchanged behavior.
  - Scenario `"Beans consumed by the blob-storage adapter without behavior change"` appended (canonical spec now 1 requirement / 9 scenarios).
  - Verified: diff of delta body (minus `## MODIFIED` header) against the canonical requirement body is empty apart from the pre-existing `## Purpose` block.
  - No other existing specs modified (`brands-management`, `products-management` untouched).

## Specs Synced

| Domain | Action | Details |
|--------|--------|---------|
| blob-storage | Created | 5 requirements / 17 scenarios added to canonical spec |
| aws-s3-integration | Updated | 1 requirement reconciled (scope fence), 1 scenario added; now 1 requirement / 9 scenarios |

## Archive Contents

Preserved verbatim from `openspec/changes/kan-10-blob-storage-port/`:

- `proposal.md` ✅
- `design.md` ✅
- `tasks.md` ✅ (32/33 checkboxes complete; 10.2 optional skipped)
- `apply-progress.md` ✅ (phases 1–12, deviations documented, no-commit boundary noted)
- `verify-report.md` ✅ (PASS WITH WARNINGS: 3 warnings + 2 suggestions, non-blocking)
- `specs/` ✅ (`specs/blob-storage/spec.md` delta retained in archive; canonical spec created separately in `openspec/specs/`; `specs/aws-s3-integration/spec.md` delta retained, canonical spec updated separately)
- `reports/` ✅
  - `2026-09-21-code-review.md` (PASS WITH GAPS: 4 minors + 2 questions, non-blocking)

## Source of Truth Updated

The canonical spec store now reflects the new behavior:

- `openspec/specs/blob-storage/spec.md` — new Blob Storage capability (source of truth).
- `openspec/specs/aws-s3-integration/spec.md` — beans recorded as consumed by the blob-storage adapter, behavior unchanged (source of truth).

Technical documentation per `docs/documentation-standards.md` was updated inside the change itself (no further doc action at archive): `README.md` (blob-type/prefix table, key convention, LocalStack credential-free walkthrough), `docs/backend-standards.md` (`infrastructure/adapters/storage/` registered, `BlobStorage` port convention). `docs/data-model.md` unaffected (no entities, no migrations) — assessed under the `update-docs` skill, no change required.

## Verification and Code-Review History

### Verification (opsx-verify): `PASS WITH WARNINGS`, zero CRITICAL

- **Tests:** 120 unit (surefire) + 69 integration (failsafe, real LocalStack + Postgres), `mvn verify` BUILD SUCCESS.
- **Spec compliance:** 17/17 `blob-storage` scenarios traced to passing tests; `aws-s3-integration` beans still green (`S3ConfigTests` + `S3ConfigIntegrationTests`).
- **Notes (non-blocking, carried as follow-ups):**
  - **WARNING-1** — SpotBugs `EI_EXPOSE_REP2` exclusion is package-wide (`infrastructure.adapters.storage.*`); the finding on `getFailedKeys()` is a false positive (immutable `Set.copyOf`), but a future mutable getter would also be silenced.
  - **WARNING-2** — TDD RED evidence is recorded per phase, not independently re-runnable (final GREEN state re-run only).
  - **WARNING-3** — Pitest `RUN_ERROR` minion warnings (×2) on unrelated classes; pitest is report-only (no threshold gate).
  - **SUGGESTION-1** — Partial-chunk failure aborts remaining chunks (fail-fast, retry-safe); caller contract implicit.
  - **SUGGESTION-2** — DoD §6.12 commit boundary outstanding at verify time; resolved by this archive (see Commit Boundary).

### Code Review (adversarial red-team): `PASS WITH GAPS`, no Blocker, no Major

All findings Minor/Question, non-blocking per AGENTS.md §6 (reported, not fixed inline); carried as follow-ups:

- **Minor (SpotBugs scope)** — narrow the `EI_EXPOSE_REP2` match to the specific class + justification comment (follow-up, spec-update-first).
- **Minor (chunk fail-fast doc)** — document the fail-fast retry contract (`retry with the full key set`) in `BlobStorage.remove` Javadoc + spec line.
- **Minor (SdkException-only translation)** — decide the policy for non-`SdkException` runtime failures (null `presigned.url()`, null `errors()`, null `properties.s3()`); either widen translation or record that only `SdkException` is translated.
- **Minor (large partial-failure message)** — cap the exception message to count + first-N keys (full set stays in `getFailedKeys()`).
- **Question (Assert message key echo)** — confirm the team is comfortable with invalid keys echoed in the `IllegalArgumentException` message.
- **Question (null-`s3` tolerance)** — is absent-`s3` a supported runtime shape (then fail fast explicitly) or test-only?

## Follow-up Tickets to Raise (not archive blockers)

Explicitly out of scope in KAN-10 (proposal Non-Goals + assumptions §8); to be raised in the tracker, not built:

1. Upload hardening — presigned POST policy / size and content-type enforcement (proposal risk: unbounded upload).
2. Orphan-blob reclamation — lifecycle rule or reconciliation job + orphan-rate metric (issued-never-uploaded, uploaded-DB-write-failed).
3. Bucket public-read hardening + CORS scoping (pre-existing KAN-14 production blockers; bucket must never hold private content).
4. `strain_videos` / `product_videos` Flyway migrations (assumption 8.1 — two blob types storable but not persistable today).
5. `DISPENSARY_IMAGE` decision (assumption 8.2 — confirm omission intended; seventh type is a one-line enum change if not).
6. Remove-vs-soft-delete product decision (assumption 8.3 — who calls `remove`, and when; deleting blobs on soft delete breaks its contract).
7. Auth story before any production endpoint wrapping the port (assumption 8.5 — anyone can mint upload URLs once wrapped).
8. Code-review gaps (items 1–4 and 2 questions above): SpotBugs match narrowing, chunk fail-fast Javadoc, error-translation policy, message-size cap.

## Commit Boundary

Per DoD §6.12 (conventional commits, English-only) the archive owns the commit boundary deferred by the apply chain. Branch `feat/KAN-10-blob-storage-port` created from `main`; work committed in two conventional commits:

1. `feat(KAN-10): add BlobStorage port with S3 presigned-upload adapter` — implementation, tests, docs, and canonical spec sync.
2. `docs(KAN-10): archive kan-10-blob-storage-port change` — archive directory (this report included) plus removal of the active change directory.

Implementation commit SHA recorded below; the archive commit SHA is the HEAD commit carrying this report.

- **Implementation commit SHA:** `b01696527c7d5d0ed6564f6a1d01b7fc68119833`
- **English-only:** confirmed (code, Javadoc, specs, tests, commits all English).

## Symlink Integrity (AGENTS.md §5)

Verified before archiving: canonical `.agents` source intact; no broken symlinks in `.claude`, `.opencode` (`.cursor` absent); no stale targets introduced by this change (no skill/artifact renames).

## SDD Cycle Complete

The change has been fully planned, specified (5 requirements, 17 scenarios + 1 reconciled requirement), designed (D1–D5), implemented and verified (189 tests green, 0 CRITICAL, strict gates pass), code-reviewed (PASS WITH GAPS, gaps non-blocking and tracked above), and archived. The main specs are promoted to `openspec/specs/blob-storage/spec.md` (new) and `openspec/specs/aws-s3-integration/spec.md` (reconciled) as the source of truth going forward.

Ready for the next change.
