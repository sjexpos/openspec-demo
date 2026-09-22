# Step 8 — Unit Test and Database Verification (2026-09-21)

Change: `kan-12-brandimage-asset-lifecycle` (branch `feat/kan-12-brandimage-asset-lifecycle`).

## 8.1 Pre-test database baseline (impacted entity: `brand_images`)

Recorded at session start against the `tests` database (PostgreSQL 14.4, Docker):

- Flyway history: single entry `0.1.0` (`initialData SM`), success.
- `brand_images` shape: `(id serial PK, brand_id integer NOT NULL, image_url varchar NOT NULL)`,
  FK `fk_brand_images_brands` to `brands(id)`, no other indexes/constraints, zero seed rows.

## 8.2 Targeted unit tests for changed modules

Command pattern (unit scope via Surefire):

```bash
mvn test -Dtest='AssetStatusTests,BlobTypeTests,BrandImageTests' ...
mvn test-compile failsafe:integration-test -Dit.test='BrandImageRepositoryTests' ...
```

Results (all green, no new regressions in targeted scope):

| Suite | Tests | Failures | Errors | Notes |
|---|---|---|---|---|
| `AssetStatusTests` (new) | 17 | 0 | 0 | exact-three-constants guard, literal names, full 3x4 transition matrix incl. null, self-rejection, `isTerminal`/`isVisible` |
| `BlobTypeTests` (extended, add-only) | 49 | 0 | 0 | pre-existing `isCanonicalKey` cases untouched and passing; new `isKeyOf` ownership cases passing |
| `BrandImageTests` (new) | 22 | 0 | 0 | factory-only creation, key ownership, guarded transitions, setter-absence + no-`@SQLDelete`/`@SQLRestriction` guards |
| `BrandImageRepositoryTests` (new, real Postgres + Flyway) | 11 | 0 | 0 | persist/reload with audits, native string proof, CHECK/unique/FK/NOT NULL proofs, filtering, hit/miss, round-trip, tombstone |

TDD red evidence per phase: each new suite first failed compilation with `cannot find symbol`
(missing `AssetStatus` / `isKeyOf` / `BrandImage` / `BrandImageRepository`), confirming the tests
guard the new behavior rather than passing vacuously.

## 8.3 Broader suite and post-test database state

- Full unit scope: `mvn test` → **179 tests, 0 failures, 0 errors, 0 skipped. BUILD SUCCESS.**
- Full integration scope: `mvn failsafe:integration-test` → **80 tests, 0 failures, 0 errors,
  0 skipped. BUILD SUCCESS** (includes endpoints, S3 adapter via LocalStack, all repositories).
- Extra mapping-vs-schema parity run (`SPRING_JPA_HIBERNATE_DDL_AUTO=validate`): Hibernate
  reports exactly one drift on `brand_images` — column `id`: `serial (INTEGER)` vs expected
  `bigint`. Proven pre-existing and table-generic by re-running validate on the pristine tree
  (changes stashed, recompiled): identical failure on `brand_types.id`. Every V0.1.0 table uses
  `SERIAL` PKs while every entity uses `Long`/`IDENTITY`; V0.1.1 does not touch `id` and follows
  the `Brand` convention. All V0.1.1-added columns (`image_key`, `status varchar(16)` with no
  default, six audit columns) match the entity mapping exactly (column names, nullability,
  lengths, `STRING` enum mapping), additionally proven by the native-query DB tests.
- Post-test database state:
  - Flyway history: `0.1.0` + `0.1.1` (`brand images asset lifecycle`), both success.
  - `brand_images` row count: **0** — all `@DataJpaTest` work rolls back; no test mutation
    remains, so no restoration was needed. The only persistent delta is the intended migration.
  - Indexes on `brand_images`: `brand_images_pkey`, `uq_brand_images_image_key`,
    `ix_brand_images_brand_status`, `ix_brand_images_pending_created` (all 4 present).
  - `CHECK chk_brand_images_status` over exactly `PENDING`, `UPLOADED`, `DELETED` present.

No flaky behavior observed across repeated runs (targeted suites run 2–3 times each during
red→green cycles plus one full pass each).
