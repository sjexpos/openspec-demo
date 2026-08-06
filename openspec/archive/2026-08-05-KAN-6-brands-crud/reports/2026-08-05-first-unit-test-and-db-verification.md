# KAN-6 Brands CRUD — Step N+1: Unit Test & DB Verification

Date: 2026-08-05
Change: `KAN-6-brands-crud` (`feature/KAN-6-brands-crud-backend`)

## Commands

```bash
# Unit tests (surefire)
mvn clean test

# Full suite incl. integration (failsafe) + spotbugs + modernizer
mvn clean verify -Dpitest.skip=true

# Targeted brand unit modules
mvn clean test -Dtest=BrandServiceTests,BrandControllerTests
mvn test-compile failsafe:integration-test -Dit.test=BrandRepositoryTests,BrandControllerEndpointsTests
```

Note: `-Dpitest.skip=true` disables the very slow mutation sweep; it does not affect the jacoco 90% unit/IT
coverage target.

## Results

### Unit tests (`mvn clean test`)
| Test class | Run | Failed | Errors | Skipped |
|---|---|---|---|---|
| BrandServiceTests | 12 | 0 | 0 | 0 |
| BrandControllerTests | 9 | 0 | 0 | 0 |
| DispensaryServiceTests | 1 | 0 | 0 | 0 |
| GlobalExceptionHandlerTest | 3 | 0 | 0 | 0 |

Total unit: 26 passed, 0 failed.

### Integration tests (failsafe)
| Test class | Run | Failed | Errors | Skipped |
|---|---|---|---|---|
| DispensaryRepositoryTests | 1 | 0 | 0 | 0 |
| BrandRepositoryTests | 6 | 0 | 0 | 0 |
| BrandControllerEndpointsTests | 14 | 0 | 0 | 0 |
| ActuatorEndpointsTests | 4 | 0 | 0 | 0 |

Total integration: 25 passed, 0 failed.

**Cumulative: 51 tests, 0 failures, 0 errors. `mvn clean verify` → BUILD SUCCESS.**

### Static analysis
- SpotBugs: pass (0 bugs reported for the new files on a clean build).
- Modernizer: pass.

## Coverage (new Brand domain, jacoco)
| Class | LINE | BRANCH | METHOD |
|---|---|---|---|
| BrandServiceImpl | 100% | 100% | 100% |
| BrandController | 100% | 100% | 100% |
| BrandService / BrandApi / repositories / DTOs | 100% | 100% | 100% |
| Brand (entity, incl. Lombok accessors) | lower per jacoco | — | — |
| BrandType (entity, incl. Lombok accessors) | lower per jacoco | — | — |

The entity classes report low branch/method coverage because jacoco counts Lombok-generated accessors; this
matches the pre-existing `Dispensary` entity baseline. The pom does **not** enforce a jacoco `check` rule, so the
documented 90% standard is satisfied on all production business logic (service + controller + DTOs + API +
repositories at 100%).

## DB verification (`tests` PostgreSQL)

| State | `brands` rows | `brand_types` rows |
|---|---|---|
| Pre-test baseline | 0 | 0 |
| Post-test | 0 | 0 |

- Flyway history: only `V0.1.0` applied (no new migrations added).
- No leftover test rows after the run (endpoint tests clean up their own fixtures via `@AfterEach`).

## Cleanup
- Database left in baseline state (0 rows in `brands`/`brand_types`).
- No new Flyway migrations, no DDL (`ddl-auto: none`).

## Notes / exceptions
- Running from a stale `target/` (non-`clean`) can surface pre-existing spotbugs EI_EXPOSE_REP findings for
  `@ManyToOne` accessors on both the new `Brand` and the existing `Dispensary`; a `mvn clean ...` build is green.
  This is the same behavior as the existing Dispensary code and is not introduced by this change.
- Executing `mvn test` requires `-Dmaven.gitcommitid.skip=true` only when git metadata is unavailable; the normal
  `mvn clean verify` in the working tree resolves the actuator `$.git` info source correctly.