## Adversarial review

**Scope**: `KAN-14-s3-client-beans` (branch `feat/KAN-14-s3-client-beans-backend`, uncommitted diff vs `HEAD`)
**Sources**:
- `openspec/changes/KAN-14-s3-client-beans/specs/aws-s3-integration/spec.md` (1 requirement, 8 scenarios)
- `openspec/changes/KAN-14-s3-client-beans/design.md` (D1–D7)
- `openspec/changes/KAN-14-s3-client-beans/tasks.md` (traceability matrix)
- Diff: `pom.xml`, `src/main/.../infrastructure/config/AwsS3Properties.java` (new),
  `S3Config.java` (new), `src/main/resources/application.yml`, `src/test/resources/application.yml`,
  `AwsS3PropertiesTests.java`, `S3ConfigTests.java`, `integration/config/S3ConfigIntegrationTests.java`,
  `integration/endpoints/EndpointIntegrationTest.java`, `docker-compose.yml`, `README.md`,
  `docs/backend-standards.md`
- Standards: `AGENTS.md`, `docs/backend-standards.md`
- Verify-phase inputs (accepted as given): zero CRITICAL, W1 equivalent mutants,
  W2 pre-existing init-script exit 127, W3 tmp-file audit note, S1 surefire add-opens idiom

### Spec and task alignment

- S1 (beans available): covered — `S3Config` declares exactly one `S3Client` + one `S3Presigner`
  `@Bean`; integration test asserts `getBeansOfType` size 1 for each. No extra beans.
- S2 (override applied): covered both directions — unit tests assert `endpointOverride()`
  present/equal when set; presigner URL host/port `localhost:4566` + path prefix
  `/develop-assets/` proves the `S3Configuration.pathStyleAccessEnabled` path (D3a), and the
  integration test fetches a presigned URL over credential-free HTTP.
- S3 (default AWS endpoint): covered both directions — `endpointOverride()` empty when
  `endpoint=null`; presigner URL resolves to `*.amazonaws.com`; empty-string → `null` URI
  binding pinned by `ApplicationContextRunner` test.
- S4 (region): covered — `Region.of(properties.region())` in both builders, non-default
  (`eu-west-1`) unit test kills hardcoding.
- S5 (credentials via default chain only): holds — `DefaultCredentialsProvider.create()` in both
  builders; `grep` over `src/main` returns nothing for `StaticCredentialsProvider`, `AKIA`,
  or `test/test` literals; no endpoint literal in `src/main`. Test-only `test`/`test` creds
  live in `src/test` via junit-pioneer / system properties, per D3b.
- S6/S7 (round-trip + presigned fetch): covered by LocalStack integration tests with
  `@AfterEach` key cleanup (order-independent re-runs).
- S8 (fail-lazy boot): holds by construction — both builders end in `builder.build()` with no
  probe call; no `headBucket`/`listBuckets` at startup. Verified by code inspection; automated
  pin is manual step 7.3a only (see Minor M1).
- Non-goals respected: no port, adapter, controller, health indicator, SQS consumer, bucket
  hardening, Testcontainers module, or async client introduced (grep confirms; the only
  `Controller`/`health` hits are pre-existing files). `bucket`/`presign-ttl` config without a
  consumer is explicitly accepted in D4/Risks as surface stabilisation, not scope creep.
- Build hygiene: BOM `2.46.7` pinned in `<properties>` + `<dependencyManagement>` import scope;
  `s3` artifact only (transitively provides presigner + sync HTTP client — correct, no extra
  artifact needed); `wait-for-it` target fixed `localstack:5432` → `localstack:4566` (the old
  target "worked by accident" via non-strict timeout — genuine flake fix).
- Standards: GPL header + copyright line on all new files; `@Slf4j`; English-only; fully typed
  (`URI`, `Duration`, records); test names `should_*_when_*` with Arrange/Act/Assert comments;
  SRP (`AwsS3Properties` binds, `S3Config` builds); DIP (consumers inject abstractions);
  DRY guard `hasEndpointOverride()` shared by both branches with no premature cross-builder
  abstraction (builders differ — Rule-of-Three compliant).
- S1 surefire add-opens idiom handled correctly: unit tests use `System.setProperty`
  (no add-opens needed under surefire) while `@SetEnvironmentVariable` is confined to
  integration tests running under failsafe, which already declares the `--add-opens` argLine.

### Findings

| Severity | Area | Finding | Evidence | Suggested fix (code / spec / tests) |
|----------|------|---------|----------|--------------------------------------|
| Minor | Tests (S8) | No automated test pins fail-lazy boot with unreachable endpoint; coverage relies on manual step 7.3a | `S3ConfigTests` instantiate config directly (proves offline `build()` by construction, but no context-boot-against-dead-endpoint test) | Tests: add a unit test asserting both beans build while endpoint points at an unroutable port (e.g. `http://localhost:9`), or record 7.3a as the accepted manual DoD in `tasks.md`. No code change. |
| Minor | Tests | `S3ConfigTests` mutates global JVM state via `System.setProperty/clearProperty` for `aws.accessKeyId` | `S3ConfigTests.java:109-136,142-165` | Tests: acceptable as-is under sequential surefire (try/finally restores); if parallel execution is ever enabled, migrate to junit-pioneer `@SetSystemProperty` or system-stub. No action required now. |
| Minor | Tests | `@AfterEach cleanUp()` issues a network `deleteObject` even for tests that never wrote (inject, list-buckets) | `S3ConfigIntegrationTests.java:59-63` | Tests: harmless (S3 delete is idempotent); optionally guard cleanup with a written-flag. No action required. |
| Minor | Style | Fully-qualified `GetObjectPresignRequest` inline instead of an import | `S3ConfigIntegrationTests.java:116-117` | Code (follow-up): import the type; consistency nit only. |
| Minor | Validation | `bucket` carries no `@NotBlank` and absent `s3` section passes validation silently | `AwsS3Properties.java:29-32` | Spec/tests: intentional — `bucket`/`presign-ttl` have no consumer yet (D4 trade-off). Consider `@NotBlank` on `bucket` when the first consumer story lands; do not add now (would constrain follow-ups without a reader). |
| Suggestion | Docs | `docker-compose.yml` `wait-for-it -t 10` remains a short timeout for slow machines | `docker-compose.yml:54` | Infra (follow-up): consider `-t 30` if bucket-bootstrap flakes recur. Out of scope for this story. |

Security pass: no hardcoded credentials or endpoints in `src/main` (grep clean); single `log.info`
logs only the override endpoint URI (D3e-approved debugging aid — no credentials, presigned URLs,
or ARNs); `DefaultCredentialsProvider` preserves IAM-role/IRSA deployment; no PII/auth surface in
scope. No Blocker or Major findings. Known verify-phase notes (W1 equivalent mutants, W2
pre-existing init-script exit 127, W3 tmp-file audit note) are pre-existing / informational and do
not block this diff.

### Verdict
PASS WITH GAPS

### Recommended next steps (before archive)
- Archive via `opsx-archive`; no code changes required.
- Optionally (follow-ups, not archive blockers): M1 automated S8 pin, M4 import nit,
  M5 `@NotBlank bucket` when first consumer lands, S1 `wait-for-it -t 30` if flakes recur.
