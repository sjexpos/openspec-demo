# Apply Progress — kan-12-brandimage-asset-lifecycle

Branch: `feat/kan-12-brandimage-asset-lifecycle` (created from `main`, task 0.1).

## Completed

- [x] 0.1 Feature branch created and verified as current.
- [x] 1.1 RED `AssetStatusTests` (17 tests) — failed compilation on missing `AssetStatus` type.
- [x] 1.2 GREEN `AssetStatus` enum — 17/17 pass; exhaustive switch, no framework imports.
- [x] 2.1 RED `BlobTypeTests` extended add-only — failed on missing `isKeyOf`; existing
  `isCanonicalKey` tests untouched.
- [x] 2.2 GREEN `BlobType.isKeyOf` + `isCanonicalKey` delegation — 49/49 pass.
- [x] 3.1 Migration `V0.1.1__brand_images_asset_lifecycle.sql` created (rename, status + CHECK
  with default dropped, 6 audit columns, 3 indexes).
- [x] 3.2 Migration proven: Flyway validated 2 migrations, applied V0.1.1; existing
  `BrandRepositoryTests` 6/6 green on top.
- [x] 4.1 RED `BrandImageTests` (22 tests) — failed compilation on missing `BrandImage` type.
- [x] 4.2 GREEN `BrandImage` entity — 22/22 pass (plus phases 1–2 suites, 88 total green).
- [x] 5.1 RED `BrandImageRepositoryTests` (11 tests) — failed compilation on missing
  `BrandImageRepository` type.
- [x] 5.2 GREEN `BrandImageRepository` — 11/11 pass (native string, CHECK, unique, FK, NOT NULL,
  filtering, tombstone).
- [x] 6.1 Mapping-vs-schema parity: `ddl-auto: validate` reports exactly one drift
  (`brand_images.id` serial vs bigint); proven pre-existing and table-generic via pristine-tree
  run (same failure on `brand_types.id`). All V0.1.1-added columns match the mapping exactly;
  see step-8 report. Deviation from design "zero drift" expectation documented, not coded around.
- [x] 6.2 Existing-test review: full unit scope 179/179 green; full integration scope 80/80
  green; pre-existing `BlobTypeTests.isCanonicalKey` cases untouched and passing.
- [x] 7.1 `docs/data-model.md` Brand Images section + ER block updated (`image_key`, `status`,
  audit columns with unused-`deleted_at`/`deleted_by` note, indexes, tombstone model).
- [x] 7.2 `docs/backend-standards.md` enum-persistence convention registered
  (`@Enumerated(STRING)` + `varchar` + `CHECK`, never ordinals/native PG enums).
- [x] 7.3 Delta specs (`asset-lifecycle`, `brands-management`, `blob-storage`) confirmed
  consistent with shipped behavior — no spec edits needed.
- [x] 8.1–8.4 Baseline recorded, targeted + broader suites green, step-8 report created.
- [x] 9.1 No-endpoint scope confirmed (no `presentation/` changes; endpoint suites green
  unchanged); step-9 report created.
- [x] 10.1 Docs consistency pass: English-only, no stale `image_url` refs, DDD layering kept
  (domain-only slice), Flyway + validate evidence referenced in step-8 report.

## Change statistics (uncommitted, for orchestrator PR-split decision)

- Tracked modifications: 4 files, +136/−8 (docs + `BlobType` + `BlobTypeTests`).
- New files: 3 production (~205 lines), 1 migration (24 lines), 3 test files (~609 lines),
  2 reports (~88 lines). Total new ≈ 926 lines.
- Combined ≈ 1070 changed lines — at the top of the forecasted 700–1000 band; single working
  tree, nothing committed per instructions.
