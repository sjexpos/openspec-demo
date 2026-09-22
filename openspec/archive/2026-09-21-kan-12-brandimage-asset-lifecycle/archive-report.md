# KAN-12 BrandImage Asset Lifecycle — Archive Report

- **Change:** `kan-12-brandimage-asset-lifecycle` — Persistence half of the asset pipeline: shared `AssetStatus` lifecycle enum, `BrandImage` entity, `BrandImageRepository`, `BlobType.isKeyOf` extraction, Flyway `V0.1.1` migration, tests and docs (no endpoint, no service)
- **Schema:** `story-sdd`
- **Branch:** `feat/kan-12-brandimage-asset-lifecycle`
- **Archived on:** 2026-09-21
- **Archived to:** `openspec/archive/2026-09-21-kan-12-brandimage-asset-lifecycle/`

## Summary

The KAN-12 BrandImage asset-lifecycle change was archived. The full SDD pipeline completed with the following results:

- **Proposal phase:** Scope is the persistence-half foundation slice unblocking the four queued asset stories (`brand_videos`, `strain_images`, `product_images`, `dispensary_images`); consumes the KAN-10 storage half (`BlobStorage` port + `S3BlobStorageAdapter`). Non-goals explicitly named as follow-ups F1–F7 (no endpoint, no confirm service, no rollout to other tables, no sweeper, no ordering flags, no auth). Nine assumptions carrying risk recorded.
- **Spec phase:** New `asset-lifecycle` capability — 3 requirements, 8 scenarios, all concrete and testable; plus an `ADDED` `brands-management` requirement (brand image persistence and lifecycle, 5 scenarios) and a `MODIFIED` `blob-storage` requirement (per-type `isKeyOf` check, 1 scenario added).
- **Design phase:** 8 decisions (D1–D8) with trade-offs named, mermaid class diagram, full migration plan with per-statement rationale, and open questions explicitly deferred to F1–F7.
- **Tasks phase:** 22/22 checkboxes complete across phases 0–10 (setup, enum TDD, `BlobType` refactor, migration, entity TDD, repository TDD, parity, docs, unit+DB verification, no-endpoint curl scope, docs consistency pass).
- **Apply phase:** All phases delivered per `apply-progress.md` — `AssetStatus` enum (17 tests), `BlobType.isKeyOf` extraction (49 tests, pre-existing cases untouched), `V0.1.1` migration proven by suite, `BrandImage` entity (22 tests), `BrandImageRepository` (11 tests against real Postgres + Flyway), `docs/data-model.md` + `docs/backend-standards.md` updates, delta specs confirmed consistent with shipped behavior. Documented deviation: `ddl-auto: validate` reports exactly one pre-existing table-generic drift (`id` serial vs bigint), proven on the pristine tree — not coded around.
- **Verify phase:** `PASS WITH WARNINGS` — targeted (88/88) + repository (11/11) + full unit (179/179) green; Spotless/SpotBugs(0)/Modernizer/duplicate-finder pass; 100% hand-written line coverage on new classes; all delta scenarios traced to passing tests.
- **Code Review phase:** `PASS WITH GAPS` (adversarial red-team) — no Blocker, no Major, no CRITICAL; 3 warnings (W1–W3) + 2 minors (M1–M2) + 3 suggestions (S1–S3), all non-blocking and tracked below. Archiving explicitly advised.

## Decisions (final state)

- **D1** Domain-only slice, entity owns invariants — transition table in the enum (single source of truth), guarded `markUploaded()`/`markDeleted()`, factory-only production path; ubiquitous language matches specs verbatim.
- **D2** Enum-owned transition table with exhaustive `switch` (no `default`) — growth becomes a compile error; constant-count test guards the closed 3-state persistence contract.
- **D3** Guarded `BrandImage` entity, immutable unique key, `PENDING`-only factory, no `@SQLDelete`/`@SQLRestriction` — `status` is the single delete marker; `@Builder` kept for fixtures/Lombok consistency (reviewer option recorded, S1).
- **D4** Behaviour-preserving `isKeyOf` extraction — prefix constants stay the single source of truth for construction and both predicates; existing tests untouched and green.
- **D5** Three derived queries on `JpaRepository` convention — `delete()` reachability explicitly deferred to F6 (W2).
- **D6** `varchar(16)` + `CHECK` (not native PG enum), backfill default dropped, three indexes (unique `image_key`, composite `(brand_id, status)`, partial `PENDING`) — registers the `@Enumerated(STRING)` + `varchar` + `CHECK` convention in `backend-standards.md`.
- **D7** TDD red-green with DB-level proofs (native string/CHECK/unique/FK/tombstone) and `validate` parity intent (modulo pre-existing W1).
- **D8** NFRs by construction — LAZY, no inverse collection, audit ownership, no URL logging; observability metrics defined now, dashboarded in follow-ups.

## Spec Sync

Per the `story-sdd` schema (new capability + ADDED + MODIFIED reconciliation), the delta specs were sync-promoted into the main OpenSpec spec store as canonical content, following the KAN-10 precedent (delta `## ADDED`/`## MODIFIED Requirements` wrappers stripped to canonical `## Requirements`; Purpose retained verbatim for the new capability):

- **New canonical spec:** `openspec/specs/asset-lifecycle/spec.md`
  - Form: canonical `# asset-lifecycle` + `## Purpose` + `## Requirements` (delta `## ADDED Requirements` wrapper stripped).
  - Content: 3 requirements / 8 scenarios, preserved verbatim from the delta body (verified: body diff empty).
  - Purpose section: retained verbatim from the delta.
- **Updated canonical spec:** `openspec/specs/brands-management/spec.md`
  - The delta `ADDED` requirement `Brand image persistence and lifecycle` (+ 5 scenarios) appended after the existing 7 requirements; no existing requirement touched.
- **Updated canonical spec:** `openspec/specs/blob-storage/spec.md`
  - Per the spec-phase risk note the MODIFIED requirement was **replaced, not appended**: the existing `Blob type to key prefix mapping` requirement body gained the per-type `isKeyOf` paragraph and the `Key ownership is distinguishable per blob type` scenario in place; all pre-existing paragraphs and scenarios untouched (verified: git diff shows only the two additions).
  - No other existing specs modified (`aws-s3-integration`, `products-management` untouched).

## Specs Synced

| Domain | Action | Details |
|--------|--------|---------|
| asset-lifecycle | Created | 3 requirements / 8 scenarios added to canonical spec |
| brands-management | Updated | 1 requirement ADDED (brand image persistence and lifecycle), 5 scenarios added; now 8 requirements |
| blob-storage | Updated | 1 requirement MODIFIED in place (per-type key check paragraph + 1 scenario); now 4 requirements / 18 scenarios |

## Archive Contents

Preserved verbatim from `openspec/changes/kan-12-brandimage-asset-lifecycle/`:

- `proposal.md` ✅
- `design.md` ✅
- `tasks.md` ✅ (22/22 checkboxes complete)
- `apply-progress.md` ✅ (phases 0–10, W1 deviation documented, ~1070-line workload noted, no-commit boundary noted)
- `verify-report.md` ✅ (PASS WITH WARNINGS: W1–W3 + S1–S4, non-blocking)
- `specs/` ✅ (`specs/asset-lifecycle`, `specs/brands-management`, `specs/blob-storage` deltas retained in archive; canonical specs promoted separately in `openspec/specs/`)
- `reports/` ✅
  - `2026-09-21-code-review.md` (PASS WITH GAPS: W1–W3, M1–M2, S1–S3, non-blocking)
  - `2026-09-21-step-8-unit-test-and-db-verification.md` (179 unit + 80 integration green, DB pre/post evidence)
  - `2026-09-21-step-9-manual-curl-verification.md` (no-endpoint scope, route inventory, DB proofs standing in)
- `archive-report.md` ✅ (this report)

## Source of Truth Updated

The canonical spec store now reflects the new behavior:

- `openspec/specs/asset-lifecycle/spec.md` — new shared asset-lifecycle capability (source of truth).
- `openspec/specs/brands-management/spec.md` — brand images as first-class entity with lifecycle (source of truth).
- `openspec/specs/blob-storage/spec.md` — per-type key-ownership check (source of truth).

Technical documentation per `docs/documentation-standards.md` was updated inside the change itself (no further doc action at archive, assessed under the `update-docs` skill): `docs/data-model.md` (Brand Images section + ER block: `image_key`, `status`, audit columns with unused-`deleted_at`/`deleted_by` note, tombstone model, three indexes; stale `image_url` documentation removed) and `docs/backend-standards.md` (enum-persistence convention: `@Enumerated(STRING)` + `varchar` + `CHECK`, never ordinals, never native PG enums). English-only holds in all new artifacts; remaining Spanish ER labels are pre-existing and out of scope.

## Verification and Code-Review History

### Verification (opsx-verify): `PASS WITH WARNINGS`, zero CRITICAL

- **Tests:** targeted 88/88 (`AssetStatusTests` 17 + `BlobTypeTests` 49 + `BrandImageTests` 22) + repository 11/11 (real Postgres + Flyway) + full unit 179/179 + full integration 80/80, BUILD SUCCESS.
- **Quality gates:** Spotless / SpotBugs (0) / Modernizer / duplicate-finder pass; 100% hand-written line coverage on new classes; 3×4 transition matrix incl. null asserted 100%.
- **Spec compliance:** all `asset-lifecycle` / `brands-management` / `blob-storage` delta scenarios traced to passing tests; no spec drift found.

### Code Review (adversarial red-team): `PASS WITH GAPS`, no Blocker, no Major

All findings non-blocking; carried as follow-ups below.

## Risks Carried Forward (tracked follow-ups)

Explicitly out of scope in KAN-12 (proposal Out of scope + design Non-Goals + verify/code-review findings); to be raised in the tracker, not built:

- **F1** HTTP endpoint for brand images (confirm/upload flow `PENDING → UPLOADED`: `PATCH` vs S3→SQS notification undecided; SQS filter is `products/`-only today).
- **F2** Application service + read flow (`BlobStorage.urlFor(key)` / CDN public-URL derivation owned by the future read story).
- **F3** Public-URL derivation ownership (see F2; bearer-capability discipline per KAN-10).
- **F4** Orphan-reclamation sweeper (`PENDING` + `created_at < now() - ttl`, served by the shipped partial index) and `DELETED`-row blob-removal job (blob outlives tombstone until then; storage cost accrues).
- **F5** Shared migration-fragment / `AssetEntity` superclass extraction at the second asset table (DRY Rule of Three; `varchar` + `CHECK` block repeats per table until then).
- **F6** Narrowed `Repository` interface (structural `delete()` impossibility) + `@Builder`/setter narrowing (S1) + null-guard on `transitionTo` (M2: builder-backdoor path throws NPE instead of `IllegalStateException`; DB backstop already proven) + `AuditableEntity`/`SoftDeletableEntity` split for the unused `deleted_at`/`deleted_by`.
- **F7** Three-state sufficiency review before the third asset table (`FAILED`/`EXPIRED`/`REMOVED` would touch every asset table's `CHECK`); ordering/primary-image flags; auth story (absent codebase-wide).
- **W1** Pre-existing `SERIAL`/`bigint` `validate` drift — table-generic (12+ tables, pristine-tree proven), needs a codebase-wide follow-up (align `V0.1.0` PK types or document the convention) before it blocks a future `validate` gate.
- **W2** `JpaRepository.delete*()` reachability → tracked under F6; invariant rests on spec + review + zero callers.
- **W3** Brand soft-delete does not cascade to images (`@SQLRestriction` proxy caveat) — product decision required before the delete-endpoint story.
- **M1** Tautological brand assertion (`BrandImageTests.java:50` compares the getter to itself) — one-line test precision fix, post-archive eligible; FK coverage in repository tests already backstops persistence.
- **M2** → tracked under F6 (see above).
- **Spanish ER labels** (`docs/data-model.md`, pre-existing) — docs sweep outside this change.

## PR-Split Recommendation (for orchestrator)

~1070 changed lines uncommitted on `feat/kan-12-brandimage-asset-lifecycle` (apply-progress change statistics). Candidate 3-way split recorded by apply, endorsed at archive:

1. **PR-1 enum + refactor** — `AssetStatus` + tests, `BlobType.isKeyOf` + extended `BlobTypeTests` (pure domain, zero schema impact; easiest to review first).
2. **PR-2 migration + entity + repository** — `V0.1.1`, `BrandImage` + tests, `BrandImageRepository` + repository tests (schema + JPA core; depends on PR-1 conceptually, stackable).
3. **PR-3 docs + reports** — `docs/data-model.md`, `docs/backend-standards.md`, canonical spec sync, archive directory (reviewable without a database).

Single-PR merge remains acceptable (one foundation slice, all gates green), but the split keeps each review under ~400 lines.

## Commit Boundary — DO NOT COMMIT RESPECTED

Per explicit orchestrator instruction, **nothing was committed by this archive**: the working tree is left uncommitted on branch `feat/kan-12-brandimage-asset-lifecycle`, containing the implementation (~1070 lines), the canonical spec sync (`openspec/specs/asset-lifecycle/spec.md` new; `brands-management` + `blob-storage` updated), and the archive directory move (this report included; active change directory `openspec/changes/kan-12-brandimage-asset-lifecycle/` removed). Committing and pushing (conventional commits, English-only) is owned by the orchestrator.

## Symlink Integrity (AGENTS.md §5)

No skill/artifact renames in this change; no symlinks touched. Canonical `.agents` source intact (not modified by archive file operations).

## SDD Cycle Complete

The change has been fully planned, specified (3 + 1 + 1 requirements, 14 scenarios), designed (D1–D8), implemented and verified (270 tests green across unit + integration scopes, 0 CRITICAL, strict gates pass), code-reviewed (PASS WITH GAPS, gaps non-blocking and tracked above), and archived. The main specs are promoted to `openspec/specs/asset-lifecycle/spec.md` (new), `openspec/specs/brands-management/spec.md` (ADDED requirement) and `openspec/specs/blob-storage/spec.md` (MODIFIED requirement) as the source of truth going forward.

Ready for the next change.
