# KAN-7 Delete Brand Endpoint — Archive Report

- **Change:** `KAN-7-delete-brand-endpoint` — Delete brand via `DELETE /api/brands/{brandId}`
- **Schema:** `be-user-story` (`openspec/config.yaml`)
- **Branch:** `feat/KAN-7-delete-brand-endpoint`
- **Archived on:** 2026-11-26
- **Archived to:** `openspec/archive/2026-11-26-KAN-7-delete-brand-endpoint/`

## Summary

The KAN-7 delete-brand change was archived. The verify phase concluded **PASS WITH WARNINGS**
(all 7 spec scenarios implemented with corresponding tests; targeted reruns green; 0 CRITICAL
findings), and the code-review phase concluded **PASS WITH GAPS** (no Blocker, no Major,
no CRITICAL finding; 5 Minor + 2 Suggestion + 1 Question, all non-blocking). The non-blocking
gaps listed below were explicitly accepted as documented exceptions, not fixed, per the
code-review recommendation.

## What Was Delivered

- **New route** `DELETE /api/brands/{brandId}` returning `200 OK` with `DataResponse<RemoveBrandResponse>`,
  following the `DELETE /api/dispensaries/{id}` precedent (same layering, envelope, transaction shape).
- **Soft-delete only**: `BrandService.deleteById` resolves the brand, applies the referential guard,
  then sets `deletedAt` and saves. No physical row removal; the brand is excluded from all brand queries.
- **404 before guard**: a missing or already-soft-deleted brand is answered `404 Not Found`
  (`Brand not found with ID: {id}`); the guard never runs for it.
- **Referential-integrity guard**: native count query `countLiveProductsByBrandId` (only `products`
  with matching `brand_id` and `deleted_at IS NULL`); a live reference rejects with `409 Conflict`
  (`Brand with ID {id} cannot be deleted because it is used by products`) with no state mutation.
- **Atomicity**: the only mutation is the last statement; every rejection throws before it.
- **Layers**: new `RemoveBrandResponse` record, `BrandRepository` count method, `BrandService.deleteById`
  contract + implementation, `BrandApi.delete` mapping (declares `200`/`404`/`409`), thin
  `BrandController.delete` handler. No migration, no `GlobalExceptionHandler` change, no existing-route change.

## Final Verdicts

| Phase | Verdict | Source |
|-------|---------|--------|
| verify | **PASS WITH WARNINGS** — 7/7 scenarios implemented and tested; 0 CRITICAL; 4 warnings + 1 suggestion | `verify-report.md` |
| code-review | **PASS WITH GAPS** — no Blocker/Major/CRITICAL; 5 Minor + 2 Suggestion + 1 Question, none blocking archive | `reports/2026-11-26-code-review.md` |

## Accepted Non-Blocking Gaps

Carried into archive exactly as adjudicated by code-review; none blocks archive:

1. **Task 0.2 unchecked (61/62 tasks complete).** Task 0.2 requires confirming a clean worktree before
   any code. The current tree cannot honestly prove pre-code cleanliness, so the box was deliberately
   left unchecked. Do NOT check it retroactively. Exception recorded in code-review.
2. **Whole-table test teardown wording.** `BrandControllerEndpointsTests.tearDown()` issues unqualified
   `DELETE FROM` statements (aggravated copy of a previously accepted pattern), while the step reports
   and task 4.1 claim row-scoped cleanup. The prose is superseded by the code-review correction;
   not a production defect. Follow-up only: amend the reports or scope the deletes.
3. **RED-before-GREEN not auditable.** Task ordering and the report's RED command list support the TDD
   claim, but no failing-test output or commit chronology is retained. Treated as an evidence
   limitation, not an implementation defect.
4. **90% coverage claim unsubstantiated.** No JaCoCo percentage or report was retained, and the build
   has no `jacoco:check` enforcement (pre-existing tooling gap). New branches are exercised by tests;
   that is not a measured 90%. Do not repeat the 90% figure without a real report.

## Status-Code Decision: 409, Not 400

The brand-in-use case returns **`409 Conflict`**, not the `400 Bad Request` loosely worded in the
source ticket. The ticket wording is untrusted input and is superseded by the project standard
(`docs/backend-standards.md`: `400` is reserved for malformed input; business-state conflicts are
`409`), the spec traceability note, and design D5. Verified in code (`ConflictException` path),
OpenAPI (`409` declared), and tests (assert `409`, not `400`).

## Specs Synced

| Domain | Action | Details |
|--------|--------|---------|
| brands-management | Updated | 1 added requirement (`Delete brand via DELETE /api/brands/{brandId}`), 7 scenarios |

The delta spec (`## ADDED Requirements` wrapper stripped per OpenSpec convention) was applied to the
main spec by `openspec archive KAN-7-delete-brand-endpoint --yes` (run with `--yes` because task 0.2
is intentionally unchecked). Archive note: the main `brands-management` spec predated the strict
validator and lacked the required `## Purpose` section, so a minimal Purpose paragraph was added to
`openspec/specs/brands-management/spec.md` before archiving to let validation pass; no requirement
text was altered. The archived spec delta is retained verbatim in the archive folder.

## Archive Contents

Preserved from `openspec/changes/KAN-7-delete-brand-endpoint/`:

- `proposal.md` ✅
- `specs/brands-management/spec.md` ✅ (delta spec retained in archive)
- `design.md` ✅
- `tasks.md` ✅ (61/62 complete; task 0.2 intentionally unchecked — see above)
- `verify-report.md` ✅ (PASS WITH WARNINGS)
- `reports/` ✅
  - `2026-11-26-code-review.md` (PASS WITH GAPS)
  - `2026-11-26-step-N+1-unit-test-and-db-verification.md`
  - `2026-11-26-step-N+1-manual-curl-verification.md`
- `archive-report.md` ✅ (this file)

Archive note: the OpenSpec CLI staged the archived change under `openspec/changes/archive/`; the
folder was relocated to `openspec/archive/2026-11-26-KAN-7-delete-brand-endpoint/` to match this
repository's archive convention. `openspec list` confirms no active changes remain.

## Source of Truth Updated

The following spec now reflects the new behavior:

- `openspec/specs/brands-management/spec.md` — contains `Requirement: Delete brand via DELETE /api/brands/{brandId}` with all 7 scenarios (verified present; `openspec validate --specs` passes for `brands-management`).

## SDD Cycle Complete

The change has been fully planned, implemented, verified, and archived.
Ready for the next change.
