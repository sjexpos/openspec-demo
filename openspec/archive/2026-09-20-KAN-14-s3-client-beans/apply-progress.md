# KAN-14 S3 Client Beans — Apply Progress

Branch: `feat/KAN-14-s3-client-beans-backend`
Date: 2026-09-20
Status: ALL 31 tasks complete — ready for `opsx-verify`.

## Phase 0 — Setup (0.1–0.2) [x]
- Branch created from `main`; `git status` clean before work.
- `docker compose up -d postgres localstack` healthy; `develop-assets` bucket verified.

## Phase 1 — Build (1.1–1.2) [x]
- `aws-sdk.version=2.46.7` + BOM `<dependencyManagement>` + `s3` artifact in `pom.xml`.
- `mvn verify` BEFORE Java code: BUILD SUCCESS, no duplicate-finder conflict.

## Phase 2 — AwsS3Properties (2.1–2.6) [x]
- `AwsS3PropertiesTests`: 4 tests (bind-all, override-present, empty-string→null,
  s3-absent). Notes: 2.3 and 2.5 passed on first run — Spring already binds `""`→`null`
  and the record was written null-safe per the design shape; both now pin the contract.
- `AwsS3Properties` record created; spotless applied.

## Phase 3 — s3Client (3.1–3.6) [x]
- `S3ConfigTests`: RED confirmed (`S3Config` missing), GREEN with `Region.of` +
  `DefaultCredentialsProvider`, override branch both directions (3.3 failed before fix),
  region `eu-west-1` proves no hardcoding.

## Phase 4 — s3Presigner (4.1–4.4) [x]
- RED confirmed (`s3Presigner()` missing); GREEN with `S3Configuration` path-style.
- Deviation: unit presign test supplies credentials via `aws.accessKeyId` /
  `aws.secretAccessKey` system properties (try/finally cleared) instead of
  `@SetEnvironmentVariable`, because surefire lacks the `--add-opens` flag
  (only failsafe declares it). Integration tests use `@SetEnvironmentVariable` per design.
- 6/6 green; spotless applied.

## Phase 5 — Wiring (5.1–5.2) [x]
- `aws:` blocks in main + test `application.yml` exactly per D4.
- `EndpointIntegrationTest` carries `@SetEnvironmentVariable` test/test credentials.
- Deviation: `S3ConfigIntegrationTests` (in `integration/config` per D7) does NOT extend
  `EndpointIntegrationTest` because the base class is package-private in
  `integration/endpoints`; it mirrors the same annotations instead
  (`@SpringBootTest` + `@AutoConfigureMockMvc` + `@Import(FlywayAutoConfiguration)` +
  credential env vars), which the enriched US explicitly allows ("Extends/mirrors").

## Phase 6 — Integration (6.1–6.5) [x]
- 4/4 LocalStack tests green: inject, listBuckets, put/get round-trip, presigned fetch
  over plain HTTP (200 + matching body).
- Full `mvn verify`: 66 unit + 63 integration, BUILD SUCCESS.

## Phase 7 — Infra/docs/gates (7.1–7.4) [x]
- `wait-for-it localstack:5432` → `localstack:4566`.
- README (`aws.*` table + LocalStack workflow) + backend-standards (Tech Stack) updated.
- Boot with LocalStack stopped: `Started DemoApplication`, no S3 failure (fail-lazy).
- Hygiene greps empty (no `StaticCredentialsProvider`/`AKIA`/test-test/endpoint in `src/main`).
- Final gates: spotless/SpotBugs/modernizer/enforcer/duplicate-finder pass;
  JaCoCo 100% on both new classes; pitest NEGATE_CONDITIONALS kills confirmed on both
  `if (hasEndpointOverride())` branches (surviving NonVoidMethodCall mutants on
  builder-chain calls are equivalent — SDK defaults apply when a builder call is
  removed; project `mutationThreshold=0` gate passes).
- No commits made (automatic chain — verify/archive own the commit boundary).
