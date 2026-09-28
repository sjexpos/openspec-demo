# Apply progress: kan-16-spring-cloud-aws-foundation

## Version decisions (recorded per tasks 1.1)
- `spring-cloud-aws.version` = **4.1.1** (newer 4.1.x than the 4.1.0 named at
  refinement; verified on Maven Central 2026-09-24: 4.1.1 released Aug 2026,
  latest in the 4.1.x line).
- `aws-lambda-events.version` = **3.16.0** (pinned exactly as specified;
  official aws-lambda-java-libs README documents 3.16.0 as current).
- Added (justified, pinned): `aws-lambda-serialization.version` = **1.2.0**
  (`com.amazonaws:aws-lambda-java-serialization`: canonical S3Event JSON mapping,
  self-contained shaded Jackson, same code path as the Lambda runtime) and
  `software.amazon.awssdk:aws-crt-client` (BOM-managed, no pin needed: enables the
  auto-configured CRT-based `S3AsyncClient` bean; the plain starter only provides
  sync `S3Client` + `S3Presigner`).

## Gate 1.3 evidence
- `duplicate-finder:check` (failBuildInCaseOfConflict=true,
  checkRuntimeClasspath=true) passes with BOM + both starters + lambda-events
  (+ serialization + crt) on the classpath. No `<ignoredDependencies>` entry needed.
  Top build risk R1 cleared.
- Full `mvn verify` BEFORE code was RED for one expected reason only:
  `S3AutoConfiguration.s3ClientBuilder` could not resolve a region because
  `spring.cloud.aws.region.static` was not configured yet (Phase 2/3 delivered
  it). Final `mvn verify` is GREEN (see Phase 4).
- Note: stale `target/` from another branch state (KAN-13 poller classes)
  initially tripped modernizer; `mvn clean` resolved it. No source impact.

## S3Config option choice
- **Option A (delegate to starters)** — executed in 2.5/2.6. `S3Config` keeps only
  `@EnableConfigurationProperties(AwsS3Properties.class)` (registration point);
  `S3Client`/`S3Presigner` come from the starter, `S3AsyncClient` from the CRT
  auto-configuration. `S3ConfigTests` retired (class under test deleted).

## 4.1.1 API adaptations (option set normative, fluent spelling adaptive — R2)
- `AcknowledgementMode` lives in `...acknowledgement.handler` (not `...acknowledgement`).
- Backpressure: `backPressureHandlerFactory(adaptiveThroughputBackPressureHandler())`
  + explicit `backPressureMode(AUTO)`. Plain `throughputBackPressureHandler()` creates a
  handler that `StandardSqsMessageSource` rejects (requires `BatchAware...`) — proven by
  integration failure, fixed, round trip green.
- Executor: options carry `componentsTaskExecutor` + `acknowledgementResultTaskExecutor`
  (no singular `taskExecutor`); both wired to the one virtual-threads bean via
  `support.TaskExecutorAdapter` (Spring 7 moved it to `core.task.support`).
- Converter: `JacksonJsonMessageConverter` does not exist in 4.x AND neither Jackson 2
  nor Jackson 3 plain mappers can bind `S3Event` (capitalized `Records` envelope, no
  default ctor on the record type — proven by spike). New `S3EventMessageConverter
  extends SqsMessagingMessageConverter` with Lambda-serializer payload mapping.
- Observation: no factory-builder method; ternary lives inside `configure(options -> …)`.
- Container: `setPayloadDeserializationType(S3Event.class)` is REQUIRED (a lambda
  listener's generic is erased; without it payloads arrive as String — proven by
  ClassCastException in the round trip, fixed).
- Enriched §3.4 test YAML was internally inconsistent (`max-messages-per-poll: 10` >
  `max-concurrent-messages: 2` violates library validation); test value corrected to `2`.
- Callback logs the exception TYPE (`getClass().getName()`), not `throwable.toString()`,
  so exception messages embedding key material never reach logs (constraint over sketch).
- `SqsPropertiesBindingTests`/`AwsS3PropertiesTests` library-binding guards pass on first
  run (library characterization); RED discipline witnessed on all owned code (2.1, 2.3
  via temporary guard removal, 2.5, 3.1, 3.3, 3.5, 3.7 via temporary ternary break, 3.8,
  3.9).

## Batch log
- [x] Phase 1 (1.1, 1.2, 1.3-as-documented-above)
- [x] Phase 2 (2.1–2.6)
- [x] Phase 3 (3.1–3.11)
- [x] Phase 4 (4.1–4.4: 213 surefire + 90 failsafe green, incl. EndpointIntegrationTest
      suites and S3 migration suites; 4.3 committed as SqsFailLazyIntegrationTests)
- [x] Phase 5 (5.1–5.3: README + backend-standards Messaging/SQS convention;
      spotless clean; DoD greps clean; JaCoCo SqsConfig 100% lines/branches)

## Coverage (unit-measured; failsafe skips the JaCoCo agent via hardcoded argLine)
- SqsConfig 100% lines/branches/methods; callback/listener/converter/payload-converter
  100% lines (1 defensive byte[] branch covered); AwsSqsProperties/AwsS3Properties 100%.
- S3Config 0% (registration-only, matches JpaConfig/JsonConfig/OpenApiConfig convention).
