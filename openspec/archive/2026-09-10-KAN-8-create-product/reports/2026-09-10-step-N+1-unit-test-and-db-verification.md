# Step N+1 Report - Unit Tests and Database Verification

- Date: 2026-09-10
- Change: KAN-8-create-product
- Agent: opsx-apply (Claude Sonnet 5)

## Commands Executed

- `docker ps --filter name=postgres --format '{{.Names}} {{.Status}}'` (confirm Postgres up)
- `PGPASSWORD=test psql -h localhost -p 5432 -U test -d tests -t -c "SELECT ... count(*) ..."` (pre-test baseline, per table)
- `PGPASSWORD=test psql -h localhost -p 5432 -U test -d tests -t -c "select version from flyway_schema_history;"`
- `mvn -o -Dspotbugs.skip=true test -Dtest='Product*,Strain*,GlobalExceptionHandler*'` (targeted)
- `mvn -o -Dspotbugs.skip=true test` (broader Surefire unit suite)
- `mvn -o -Dspotbugs.skip=true -Dpitest.skip=true verify` (Failsafe integration suite, required — this
  project excludes `com.example.demo.integration.**` from Surefire and only runs it under Failsafe;
  `pitest.skip=true` because the project's `pitest-maven` plugin is bound to `verify` and is unrelated
  to this task's scope)
- Post-test re-run of the same `psql` baseline queries

## Unit Test Results

- Targeted tests (`Product*,Strain*,GlobalExceptionHandler*` under Surefire's scope):
  **59 passed**, 0 failed, 0 skipped (`ProductServiceTests` 16, `ProductControllerTests` 8,
  `GlobalExceptionHandlerTest` 4, plus every other Surefire-run class matched by the glob;
  `Strain*` matches no Surefire-run class here since `StrainRepositoryTests` lives under
  `integration/**`, excluded from Surefire by design — its 2 tests are covered by the Failsafe
  run below).
- Full/required Surefire suite (`mvn -o -Dspotbugs.skip=true test`): **50 passed**, 0 failed, 0 skipped.
  Runtime: ~6.1s. This is 49 (batch-4 baseline) + 1 new (task 9.1's
  `handleMessageNotReadable_shouldReturn400WithStaticMessage`).
- Failsafe integration suite (`mvn -o -Dspotbugs.skip=true -Dpitest.skip=true verify`): **58 passed**,
  0 failed, 0 skipped. Runtime: ~12.6s (build total). This is 39 (batch-4 baseline) + 19 new
  (`ProductEndpointsTests`, Section 8 of this batch).
- Grand total this batch: **108 tests green** (50 unit + 58 integration).
- Notes: no flaky test observed; both suites were run twice each during Section 8/9 development
  (before and after adding the new test file/method) with identical pass counts each time.

## Database State Verification

- Pre-test baseline (all tables, `tests` database):
  - `products`: 0
  - `collections`: 0
  - `categories`: 0
  - `subcategories`: 0
  - `units`: 0
  - `brands`: 0
  - `brand_types`: 0
  - `strains`: 0
  - `strain_types`: 0
  - `seed_companies`: 0
  - `flyway_schema_history`: `0.1.0` (unchanged)
- Post-test validation (same tables, same database, immediately after `mvn verify` completed):
  - `products`: 0
  - `collections`: 0
  - `categories`: 0
  - `subcategories`: 0
  - `units`: 0
  - `brands`: 0
  - `brand_types`: 0
  - `strains`: 0
  - `strain_types`: 0
  - `seed_companies`: 0
  - `flyway_schema_history`: `0.1.0` (unchanged)
- State restored: Yes (no restoration action was needed — every fixture-seeding test, including the
  new `ProductEndpointsTests`, cleans up in its own `@AfterEach`/`@Transactional` rollback, exactly as
  the existing `Brand`/`Strain`/`Product` repository and endpoint test classes already do).
- Restoration actions (if any): None required.

## Outcome

- Step N+1 status: **PASS**
- Blocking issues: none
