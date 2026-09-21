# KAN-14 S3 Client Beans (AWS SDK v2) — Implementation Tasks

Backend-only infrastructure enablement. Sources of truth: `proposal.md` (scope / non-goals),
`specs/aws-s3-integration/spec.md` (1 requirement, 8 scenarios), `design.md` (decisions D1–D7,
files affected, unit / integration / regression strategy, build gates),
`tmp/KAN-14-enriched-us-opus.md` §12 (8 baby steps), §5 (test naming
`should_[behavior]_when_[condition]`, Arrange/Act/Assert, ≥ 90 %), §6 (DoD, 11 items).

**Working rules that apply to every task below**

- **Strict TDD, baby steps (AGENTS.md §1 + `test-driven-development` skill).** Every
  implementation task is preceded by its own failing-test task. Run the test, watch it fail
  for the right reason (missing type / missing bean / wrong value — not a typo), then write
  the *minimal* code to make it pass. Never write test and production code in the same task.
  Never write production code before its test.
- **One task at a time**, in the order listed; the order is the dependency order.
- All new Java files carry the GNU GPL header + `// Copyright (c) 2026-2027 Sergio Exposito.` line.
- English only in code, comments, test names, commit messages.
- Test naming: `should_[expected_behavior]_when_[condition]`; Arrange/Act/Assert structure.
- Coverage gate: JaCoCo ≥ 90 % on the new classes (`mvn test` / `mvn verify`).
- **DDD placement (D1):** `infrastructure/config/` only (`AwsS3Properties`, `S3Config`),
  next to `JpaConfig`, `JsonConfig`, `OpenApiConfig`. No domain / application / presentation
  changes. No port, adapter, controller, health indicator, SQS consumer, bucket hardening,
  Testcontainers module, or async client — all explicitly out of scope.
- **SOLID / DRY:** SRP (`AwsS3Properties` owns binding, `S3Config` owns bean construction);
  DIP (future consumers depend on `S3Client` / `S3Presigner` abstractions via constructor
  injection); DRY shared guard `hasEndpointOverride()` reused by both beans, no premature
  shared-builder abstraction (Rule of Three — `S3ClientBuilder` vs `S3Presigner.Builder` differ).
- **Test split (verified):** surefire excludes `**/integration/**`; failsafe includes only that
  package. Placement decides whether a test needs LocalStack running.
- Conventional commits on branch `feat/KAN-14-s3-client-beans-backend`; code review before merge.

**Scenario index (spec.md):** S1 Beans available for injection · S2 Endpoint override applied
for local testing · S3 Default AWS endpoint in production · S4 Region resolved from
configuration · S5 Credentials resolved via default chain only · S6 Object round-trip against
overridden endpoint · S7 Presigned URL targets the overridden endpoint · S8 Startup without a
reachable S3 endpoint.

**DoD index (enriched US §6):** DOD1 BOM + `s3` dependency · DOD2 `AwsS3Properties` + `S3Config`
created, typed, `@Slf4j`, license header · DOD3 `aws:` blocks in both `application.yml` ·
DOD4 override verified both directions · DOD5 presigner path-style + real URL fetch ·
DOD6 `mvn verify` green (surefire + failsafe + JaCoCo ≥ 90 %) · DOD7 spotless / SpotBugs /
modernizer / enforcer / duplicate-finder pass · DOD8 boots with S3 down · DOD9 no hardcoded
credentials · DOD10 README + backend-standards + wait-for-it fix · DOD11 branch / commits / review.

## 0. Setup: Feature Branch and LocalStack Baseline (MANDATORY — FIRST)

- [x] 0.1 Create and switch to feature branch `feat/KAN-14-s3-client-beans-backend` from `main`.
  Verify: `git branch --show-current` prints `feat/KAN-14-s3-client-beans-backend` and
  `git status --porcelain` is empty before writing any code. Maps to: DOD11.
- [x] 0.2 Start infrastructure: `docker compose up -d postgres localstack`, then verify
  `docker compose ps` shows both healthy and `develop-assets` bucket exists
  (`aws --endpoint-url http://localhost:4566 s3 ls | grep develop-assets`).
  Verify: both commands succeed. Maps to: S6/S7 precondition, DOD6 precondition.

## 1. Build: AWS SDK v2 BOM + `s3` Artifact First (D5)

- [x] 1.1 Add `<aws-sdk.version>2.46.7</aws-sdk.version>` to `<properties>`, create the missing
  `<dependencyManagement>` section importing `software.amazon.awssdk:bom` (`type=pom`,
  `scope=import`), and add the `software.amazon.awssdk:s3` dependency (no extra
  presigner/HTTP-client artifact — `s3` transitively provides both). Do not write any Java yet.
  Verify: `grep -n "aws-sdk.version\|software.amazon.awssdk" pom.xml` shows all three blocks.
  Maps to: D5, DOD1.
- [x] 1.2 RED-first build-gate flush: run `mvn verify -DskipTests=false` (or at minimum
  `mvn verify -Dspotbugs.skip=false`) **before** writing any Java to flush out the known
  `duplicate-finder` (`failBuildInCaseOfConflict=true`, `checkRuntimeClasspath=true`) risk.
  If it trips, add the offending pair to `<ignoredDependencies>` /
  `<ignoredResourcePatterns>` with PR justification (never silent widening) and re-run until
  green. Verify: `mvn verify` build log ends `BUILD SUCCESS`.
  Maps to: design Risks (duplicate-finder), DOD1/DOD6/DOD7.

## 2. Config: `AwsS3Properties` Record + Binding Contract (D2, TDD)

- [x] 2.1 RED: create `src/test/java/com/example/demo/infrastructure/config/AwsS3PropertiesTests.java`
  with `should_bindAllFields_when_allAwsPropertiesAreSet` via `ApplicationContextRunner`
  (`aws.region=eu-west-1`, `aws.s3.endpoint=http://localhost:4566`,
  `aws.s3.path-style-access=true`, `aws.s3.bucket=develop-assets`,
  `aws.s3.presign-ttl=PT15M`) asserting every record component, plus
  `should_reportEndpointOverridePresent_when_endpointIsSet` asserting
  `hasEndpointOverride()` is `true`. Verify: `mvn -o test -Dtest='AwsS3PropertiesTests'`
  FAILS (type `AwsS3Properties` does not exist).
  Maps to: S2/S4, D2, DOD2/DOD4.
- [x] 2.2 GREEN: create
  `src/main/java/com/example/demo/infrastructure/config/AwsS3Properties.java` as
  `@ConfigurationProperties(prefix = "aws") @Validated` record
  (`@NotBlank String region`, nested `S3(URI endpoint, boolean pathStyleAccess, String bucket,
  @NotNull Duration presignTtl)`) with `hasEndpointOverride()` guard
  (`s3 != null && s3.endpoint() != null`), GPL header, fully typed. Register later via
  `@EnableConfigurationProperties` on `S3Config` (D2 — not `@ConfigurationPropertiesScan`).
  Verify: `mvn -o test -Dtest='AwsS3PropertiesTests'` passes.
  Maps to: S2/S4, D1/D2, DOD2.
- [x] 2.3 RED: add `should_bindEndpointAsNull_when_propertyIsEmptyString` via
  `ApplicationContextRunner` with `"aws.s3.endpoint="` asserting `s3().endpoint()` is `null`
  and `hasEndpointOverride()` is `false` (pins the §4.2/D2 empty-string → `null` contract:
  empty means real AWS). Verify: new test FAILS (assertion not yet pinned / type incomplete).
  Maps to: S3, D2, DOD4.
- [x] 2.4 GREEN: adjust `AwsS3Properties` only if 2.3 fails for a shape reason (no workaround
  parsing — rely on Spring `""` → `URI null` binding); confirm both tests green with no
  mutable `@Component` alternative and no `EI_EXPOSE_REP` exposure (`URI`/`Duration`/`String`
  immutable). Verify: `mvn -o test -Dtest='AwsS3PropertiesTests'` green (2/2).
  Maps to: S3, D2, DOD2/DOD4.
- [x] 2.5 RED: add `should_reportNoOverride_when_s3SectionIsAbsent` (empty `ApplicationContextRunner`
  with only `aws.region`) asserting `hasEndpointOverride()` is `false`, covering the null-`s3`
  side of the guard. Verify: test FAILS before the guard handles it.
  Maps to: S3, D2, DOD4.
- [x] 2.6 GREEN: confirm `hasEndpointOverride()` null-safe shape makes 2.5 pass; run
  `mvn spotless:apply` on the new files. Verify: `mvn -o test
  -Dtest='AwsS3PropertiesTests'` green (3/3).
  Maps to: S3, D2, DOD2/DOD4/DOD7.

## 3. Beans: `S3Config.s3Client()` Without Override, Then Override Both Directions (D3, TDD)

- [x] 3.1 RED: create `src/test/java/com/example/demo/infrastructure/config/S3ConfigTests.java`
  (no network, no Spring context — instantiate `S3Config` directly with a hand-built
  `AwsS3Properties`) with `should_buildS3Client_when_propertiesAreValid` asserting bean
  non-null and `client.serviceClientConfiguration().region()` equals `us-east-1`.
  Verify: `mvn -o test -Dtest='S3ConfigTests'` FAILS (`S3Config` missing).
  Maps to: S1/S4, D3, DOD2.
- [x] 3.2 GREEN: create `src/main/java/com/example/demo/infrastructure/config/S3Config.java`
  (`@Slf4j @Configuration @EnableConfigurationProperties(AwsS3Properties.class)
  @RequiredArgsConstructor`) with `s3Client()` only (no override branch yet):
  `S3Client.builder().region(Region.of(properties.region())).credentialsProvider(DefaultCredentialsProvider.create()).build()`.
  No `StaticCredentialsProvider`, no `destroyMethod` override (D3b/D3c), no network call (D3d).
  Verify: `mvn -o test -Dtest='S3ConfigTests'` passes.
  Maps to: S1/S4/S5/S8, D3, DOD2/DOD9.
- [x] 3.3 RED: add `should_applyEndpointOverride_when_endpointIsConfigured` asserting
  `serviceClientConfiguration().endpointOverride()` is present and equals the configured URI,
  and `should_notApplyEndpointOverride_when_endpointIsNull` asserting it is
  `Optional.empty()`. Together they kill the pitest `NEGATE_CONDITIONALS` mutant on
  `if (hasEndpointOverride())`. Verify: both new tests FAIL (override branch absent).
  Maps to: S2/S3, D3, DOD4.
- [x] 3.4 GREEN: add the override branch to `s3Client()`:
  `if (properties.hasEndpointOverride()) { log.info("Overriding S3 endpoint with {}",
  properties.s3().endpoint()); builder.endpointOverride(...).forcePathStyle(...); }`.
  Verify: `mvn -o test -Dtest='S3ConfigTests'` green (3/3), both override directions covered.
  Maps to: S2/S3, D3/D3e, DOD4.
- [x] 3.5 RED: add `should_useConfiguredRegion_when_regionIsNotDefault` (e.g. `eu-west-1`)
  asserting `serviceClientConfiguration().region()` reflects config (proves `Region.of(...)`,
  not a hardcoded constant). Verify: test FAILS if region is hardcoded.
  Maps to: S4, D3, DOD2.
- [x] 3.6 GREEN: confirm `Region.of(properties.region())` shape makes 3.5 pass with no change
  needed (or fix hardcoding if exposed). Verify: `mvn -o test
  -Dtest='S3ConfigTests,AwsS3PropertiesTests'` fully green.
  Maps to: S4, D3, DOD2.

## 4. Beans: `S3Presigner` with `S3Configuration` Path-Style + Offline HMAC Unit Test (D3a, TDD)

- [x] 4.1 RED: add `should_buildS3Presigner_when_endpointIsConfigured` to `S3ConfigTests`:
  build the presigner, presign a `GetObjectRequest` (`develop-assets`, `products/kan-14-test.txt`,
  `presignTtl`), assert URL host/port is `localhost:4566` **and** path starts with
  `/develop-assets/` (proves path-style; presigning is local HMAC — no network).
  Verify: test FAILS (`s3Presigner()` missing).
  Maps to: S1/S2/S7, D3a, DOD5.
- [x] 4.2 GREEN: add `s3Presigner()` to `S3Config` with `Region.of(...)` +
  `DefaultCredentialsProvider.create()` and the override branch using
  `.endpointOverride(...)` + `.serviceConfiguration(S3Configuration.builder()
  .pathStyleAccessEnabled(properties.s3().pathStyleAccess()).build())` (D3a — there is no
  `forcePathStyle` on the presigner; without this URLs use unresolvable virtual-host style
  `develop-assets.localhost:4566`). Verify: `mvn -o test -Dtest='S3ConfigTests'` green (5/5).
  Maps to: S1/S2/S7, D3a, DOD2/DOD5.
- [x] 4.3 RED: add `should_notApplyEndpointOverride_when_endpointIsNull` for the presigner
  (presigned URL under absent endpoint resolves to standard AWS host for the region, not
  `localhost`). Kills the presigner-side `NEGATE_CONDITIONALS` mutant. Verify: test FAILS
  before the presigner override guard.
  Maps to: S3, D3, DOD4/DOD5.
- [x] 4.4 GREEN: confirm the presigner override guard makes 4.3 pass; run
  `mvn spotless:apply`. Verify: `mvn -o test -Dtest='S3ConfigTests'` green (6/6).
  Maps to: S3, D3, DOD4/DOD5/DOD7.

## 5. Wiring: `application.yml` (Main + Test) + `EndpointIntegrationTest` Env Vars (D4)

- [x] 5.1 Add the `aws:` block to `src/main/resources/application.yml` exactly per D4
  (`region: ${AWS_REGION:us-east-1}`, `endpoint: ${AWS_ENDPOINT_URL_S3:}` empty ⇒ real AWS,
  `path-style-access: ${AWS_S3_PATH_STYLE_ACCESS:false}`,
  `bucket: ${AWS_S3_BUCKET:develop-assets}`, `presign-ttl: ${AWS_S3_PRESIGN_TTL:PT15M}`).
  Append the LocalStack `aws:` block to `src/test/resources/application.yml`
  (`region: us-east-1`, `endpoint: http://localhost:4566`, `path-style-access: true`,
  `bucket: develop-assets`, `presign-ttl: PT15M`). Verify: `grep -n -A6 "^aws:"`
  on both files shows the expected blocks; app boots offline (see 7.3).
  Maps to: S2/S3/S4, D4, DOD3.
- [x] 5.2 Enable the AWS env-var block in
  `src/test/java/com/example/demo/integration/endpoints/EndpointIntegrationTest.java`
  with `test`/`test` credentials via junit-pioneer `@SetEnvironmentVariable`
  (`AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`) so `DefaultCredentialsProvider` resolves
  them (D3b/D4). Verify: `grep -n "SetEnvironmentVariable.*AWS_"` on the file returns the
  block; regression suite in 6.5 still starts green.
  Maps to: S5/S6, D3b/D4, DOD9.

## 6. Integration: LocalStack-Backed `S3ConfigIntegrationTests` + Regression (D7)

- [x] 6.1 RED→GREEN: create
  `src/test/java/com/example/demo/integration/config/S3ConfigIntegrationTests.java`
  (`@SpringBootTest` + `@SetEnvironmentVariable` creds per 5.2) with
  `should_injectS3ClientAndPresigner_when_contextStarts` — both beans autowire non-null
  (exactly one of each). Verify: `mvn verify -Dit.test='S3ConfigIntegrationTests#should_injectS3ClientAndPresigner_when_contextStarts' -DfailIfNoSpecifiedTests=false`
  passes with LocalStack up.
  Maps to: S1, D7-integration-1, DOD6.
- [x] 6.2 RED→GREEN: add `should_listAssetsBucket_when_localstackIsRunning` —
  `s3Client.listBuckets()` contains `develop-assets` (bootstrap agreement). Verify: same
  failsafe command for the new method passes.
  Maps to: S2, D7-integration-2, DOD6.
- [x] 6.3 RED→GREEN: add `should_roundTripObject_when_putAndGetAreInvoked` — `putObject` to
  `products/kan-14-test.txt` then `getObject`; content matches; cleanup in `@AfterEach`
  (delete the key so re-runs are order-independent). Verify: same failsafe command passes;
  re-run twice green.
  Maps to: S6, D7-integration-3, DOD6.
- [x] 6.4 RED→GREEN: add `should_returnDownloadableUrl_when_objectIsPresigned` — presign a `GET`
  (using `presign-ttl`), fetch it over plain HTTP with **no** credentials
  (`java.net.http.HttpClient` / `RestTemplate` without auth), assert `200` and matching body.
  This is the acceptance proof for the presigner (a misconfigured presigner still builds fine
  and only fails at URL-fetch time). Verify: same failsafe command passes.
  Maps to: S7, D3a/D7-integration-4, DOD5/DOD6.
- [x] 6.5 Regression: run `EndpointIntegrationTest` and every suite extending it plus the full
  `mvn verify` (surefire + failsafe + JaCoCo ≥ 90 % on the new classes) with LocalStack up.
  Verify: `mvn verify` ends `BUILD SUCCESS`; JaCoCo report shows new classes ≥ 90 %.
  Maps to: D7-regression, DOD6.

## 7. Infra, Docs, and Build Gates (D6, D5-gates)

- [x] 7.1 Fix `docker-compose.yml` `wait-for-it` target `localstack:5432` → `localstack:4566`
  (5432 is PostgreSQL; without the fix the 10 s timeout makes bucket bootstrap flaky on slow
  machines). Verify: `grep -n "wait-for-it" docker-compose.yml` shows `localstack:4566`;
  `docker compose up localstack-resources` creates `develop-assets` deterministically.
  Maps to: D6, DOD10.
- [x] 7.2 Update `README.md` (document the `aws.*` properties and the LocalStack workflow:
  `docker compose up`, `AWS_ENDPOINT_URL_S3`, `AWS_REGION`, path-style) and
  `docs/backend-standards.md` (register S3 / AWS SDK v2 in the Technology Stack section).
  Verify: `grep -n "aws\." README.md` and `grep -n -i "s3\|awssdk\|AWS SDK" docs/backend-standards.md`
  each return the new entries.
  Maps to: D6, DOD10.
- [x] 7.3 Resilience + hygiene checks: (a) boot the app with LocalStack **stopped** and confirm
  the context starts successfully (fail-lazy — failures surface only on first S3 operation,
  S8); (b) `grep -rn "test\"\? *,\? *\"test\"\|StaticCredentialsProvider\|AKIA"
  src/main` returns nothing and no endpoint literal (`localhost:4566`) appears in `src/main`
  (DOD9). Verify: (a) `mvn spring-boot:run` health/`UP` with S3 down, then stop; (b) both
  greps empty.
  Maps to: S5/S8, D3b/D3d, DOD8/DOD9.
- [x] 7.4 Final gates: `mvn spotless:apply` then `mvn verify` (spotless, SpotBugs, modernizer,
  enforcer, duplicate-finder, JaCoCo ≥ 90 %, pitest — override-set **and** override-absent
  tests 3.3/4.3 must kill all `NEGATE_CONDITIONALS` mutants). Prefer shape fixes over
  `spotbugs-exclude.xml` entries (`EI_EXPOSE_REP2` on the record). Verify: `mvn verify`
  `BUILD SUCCESS` with zero surviving mutants on the new classes.
  Maps to: D5-gates/D7, DOD6/DOD7.

## Traceability

| Task(s) | Spec scenario(s) | DoD | Design |
|---------|------------------|-----|--------|
| 1.1–1.2 | (build precondition) | DOD1, DOD6, DOD7 | D5, Risks |
| 2.1–2.2 | S2, S4 | DOD2, DOD4 | D1, D2 |
| 2.3–2.4 | S3 | DOD2, DOD4 | D2 |
| 2.5–2.6 | S3 | DOD2, DOD4, DOD7 | D2 |
| 3.1–3.2 | S1, S4, S5, S8 | DOD2, DOD9 | D3, D3b–D3d |
| 3.3–3.4 | S2, S3 | DOD4 | D3, D3e |
| 3.5–3.6 | S4 | DOD2 | D3 |
| 4.1–4.2 | S1, S2, S7 | DOD2, DOD5 | D3a |
| 4.3–4.4 | S3 | DOD4, DOD5, DOD7 | D3 |
| 5.1 | S2, S3, S4 | DOD3 | D4 |
| 5.2 | S5, S6 | DOD9 | D3b, D4 |
| 6.1 | S1 | DOD6 | D7-1 |
| 6.2 | S2 | DOD6 | D7-2 |
| 6.3 | S6 | DOD6 | D7-3 |
| 6.4 | S7 | DOD5, DOD6 | D3a, D7-4 |
| 6.5 | (regression) | DOD6 | D7-regression |
| 7.1–7.2 | (infra/docs) | DOD10 | D6 |
| 7.3 | S5, S8 | DOD8, DOD9 | D3b, D3d |
| 7.4 | (gates) | DOD6, DOD7 | D5-gates, D7 |
| 0.1–0.2 | (process/precondition) | DOD11, DOD6 | — |

Out of scope (not in tasks by design): domain port / storage adapter, product-image upload
endpoint, SQS consumer, S3 health indicator, bucket hardening, Testcontainers module,
`S3AsyncClient` / `S3TransferManager`.
