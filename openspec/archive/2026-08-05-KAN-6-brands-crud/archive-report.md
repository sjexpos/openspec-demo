# KAN-6 Brands CRUD — Archive Report

- **Change:** `KAN-6-brands-crud` — Brands CRUD (backend)
- **Schema:** `story-sdd`
- **Branch:** `feature/KAN-6-brands-crud-backend`
- **Archived on:** 2026-08-05
- **Archived to:** `openspec/archive/2026-08-05-KAN-6-brands-crud/`

## Summary

The KAN-6 Brands CRUD change was archived. The verify phase concluded **PASS WITH WARNINGS**: 17/17 spec
scenarios satisfied, 50/50 tests green (`BUILD SUCCESS`, coverage evidence via jacoco), and **no CRITICAL
findings**. The non-blocking warnings/suggestions (W1 coverage-gate enforceability, S1–S4) were recorded as
follow-up items, not blockers.

The change adds a new **`brands-management`** capability. Because it is a NEW capability, its delta spec was
sync-promoted into the main OpenSpec spec store as canonical content.

## Spec Sync

The delta spec `specs/brands-management/spec.md` (wrapper `## ADDED Requirements`) was applied to the main
specs as a new canonical capability:

- **New canonical spec:** `openspec/specs/brands-management/spec.md`
  - Form: canonical `## Requirements` (delta `## ADDED Requirements` wrapper stripped per OpenSpec convention).
  - Content: 7 requirements / 17 scenarios, preserved verbatim from the delta body (verified byte-for-byte).
  - No existing specs for other capabilities were modified.

## Specs Synced

| Domain | Action | Details |
|--------|--------|---------|
| brands-management | Created | 7 requirements / 17 scenarios added to canonical spec |

## Archive Contents

Preserved verbatim from `openspec/changes/KAN-6-brands-crud/`:

- `proposal.md` ✅
- `specs/brands-management/spec.md` ✅ (delta spec retained in archive)
- `design.md` ✅
- `tasks.md` ✅ (all items across sections 0–13 marked complete)
- `apply-progress.md` ✅
- `reports/` ✅
  - `2026-08-05-first-unit-test-and-db-verification.md`
  - `2026-08-05-manual-curl-verification.md`
  - `2026-08-05-verify-report.md`

## Source of Truth Updated

The canonical spec store now reflects the new behavior:

- `openspec/specs/brands-management/spec.md` — new Brands Management capability (source of truth).

## Recorded Follow-up Items (non-blocking)

From the verify report (Section 7):
- **W1** — The 90% coverage "gate" in `tasks.md` §12.2 is not machine-enforced (`pom.xml` has no `jacoco:check`
  bound); entity classes `Brand` (~31%) / `BrandType` (~20%) fall below 90% due to Lombok-generated accessors,
  matching the existing `Dispensary` baseline.
- **S1** — Endpoint test `postUnknownBrandType_shouldReturn404_andNoRowCreated` over-states its name (no row-count
  assertion at endpoint level; guarantee proven at service layer).
- **S2** — PATCH cannot clear a field with explicit `null` (null always means "leave unchanged").
- **S3** — N+1 + `open-in-view` reliance on `getAll()` (mirrors Dispensary).
- **S4** — No list ordering guarantee for `findAllByDeletedAtIsNull()`.
- **Operational notes** (from design Open Questions): `brand_types` lookup seeding not shipped in this change
  (track as follow-up ticket); `adminId` existence check against external user service not performed.

## SDD Cycle Complete

The change has been fully planned, implemented, verified, and archived. Ready for the next change.