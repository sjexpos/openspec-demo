# Tasks: kan-16-spring-cloud-aws-foundation

> Schema: `story-sdd`. TDD mandatory (failing test first, `should_[expected]_when_[condition]`, AAA, >= 90% lines/branches on all new classes). Baby steps, one task at a time, incremental. No business handling of `S3Event` (KAN-13 seam only). No REST endpoint, no Flyway migration, no `SqsTemplate` producer, no DLQ alarm, no production queue-policy scoping.

## Phase 1 — Build foundation: BOM + dependencies first

- [x] 1.1 Add `spring-cloud-aws.version=4.1.0` + `aws-lambda-events.version=3.16.0` pins to `pom.xml` `<properties>` (never floating; re-verify `4.1.x` on Maven Central at implementation time and record the taken version in the PR).
  - Acceptance: versions pinned exactly once in `<properties>`; no hardcoded version in `<dependencies>`.
  - Test-first: N/A (build declaration; verified by 1.3).
- [x] 1.2 Add `spring-cloud-aws-dependencies` BOM import (after existing AWS SDK BOM, which stays first) + `spring-cloud-aws-starter-s3` + `spring-cloud-aws-starter-sqs` (BOM-managed, no version) + `com.amazonaws:aws-lambda-java-events:${aws-lambda-events.version}` to `pom.xml`.
  - Acceptance: classpath contains BOM + 3 dependencies; no manual version on starters.
  - Test-first: N/A (build declaration; verified by 1.3).
- [x] 1.3 Run `mvn verify` BEFORE writing any production code (duplicate-finder gate: `failBuildInCaseOfConflict=true`, `checkRuntimeClasspath=true`).
  - Acceptance: `mvn verify` green, or a conflict is resolved with a justified `<ignoredDependencies>` entry recorded in the PR (never widened silently). This is the top build risk (R1).
  - Test-first: N/A (build gate proof).

## Phase 2 — Configuration migration: properties + S3 onto `spring.cloud.aws.*`

- [x] 2.1 RED: add failing binding test `should_bindGapKnobs_when_ymlBlockIsPresent` for the new 4-key `AwsSqsProperties` (`assets-events-queue`, `acknowledgement-interval`, `acknowledgement-threshold`, `api-call-timeout`) via `ApplicationContextRunner`, no network.
  - Acceptance: test fails (record missing, not typo); covers enriched 6.1.1.
- [x] 2.2 GREEN: create `infrastructure/config/AwsSqsProperties.java` as `@ConfigurationProperties(prefix="aws.sqs") @Validated` record (`@NotBlank` queue, `@NotNull` interval/timeout, `@Positive` threshold), registered via `@EnableConfigurationProperties` on `SqsConfig`.
  - Acceptance: 2.1 passes; record carries ONLY the 4 gap keys (no region/endpoint/listener/observation re-declaration — DRY).
- [x] 2.3 RED+GREEN: validation test `should_rejectInvalidThreshold_when_ackThresholdIsNotPositive` (TDD: watch it fail, then keep 2.2 green).
  - Acceptance: non-positive threshold fails binding validation (enriched 6.1.2).
- [x] 2.4 RED+GREEN: migration-guard tests `should_readListenerTuningFromLibrary_when_springCloudKeysAreSet` (assert `SqsProperties.getListener()` concurrency/poll/timeouts/autoStartup bind from `spring.cloud.aws.sqs.listener.*`), `should_leaveCredentialsUnset_when_noStaticKeysAreConfigured` (default-chain contract), `should_defaultObservationDisabled_when_libraryFlagIsAbsent` (library default `false` guard).
  - Acceptance: all three pass with no network; enriched 6.1.3–6.1.5 covered.
- [x] 2.5 Migrate `AwsS3Properties` to the slim domain gap: keep ONLY `bucket` (`develop-assets`) + `presignTtl` (`PT15M`); delete `region`/`endpoint`/`pathStyleAccess` as sources of truth. Update/retire existing override tests so both-ways endpoint coverage runs through `spring.cloud.aws.s3.endpoint` (set vs absent) + empty-string → `null` contract on the library keys.
  - Acceptance: spec `aws-s3-integration` scenarios "Slim properties keep bucket and TTL only" + "Legacy keys are not a source of truth" hold; no `aws.region`/`aws.s3.endpoint` read remains.
  - Test-first: update existing `S3Config` override suites first (RED on new keys), then migrate (GREEN).
- [x] 2.6 Migrate `S3Config`: pick ONE option, do not mix. Option A preferred — delete manual `S3Client`/`S3Presigner` `@Bean` methods and delegate to `spring-cloud-aws-starter-s3` auto-configuration from `spring.cloud.aws.*`. Fallback Option B — keep `@Bean` shape but re-source from library `AwsRegionProvider` + `@Value("${spring.cloud.aws.s3.endpoint:}")` / `@Value("${spring.cloud.aws.endpoint:}")` (service-over-global precedence) + `path-style-access-enabled`; never `Region.of(...)` from own properties again.
  - Acceptance: no `DefaultCredentialsProvider.create()` in application code; no `aws.region`/`aws.s3.endpoint` reads; no network probe at bean creation (`listBuckets`/`headBucket` forbidden); endpoint override applied to both `S3Client` and `S3Presigner` (path-style on both) when set, real AWS resolution when absent/empty; `blob-storage` consumers keep working unchanged.
  - Test-first: both-ways override tests through library keys (RED → GREEN); record the chosen option (A or B) in the PR.

## Phase 3 — Listener wiring: customizer, callback, executor, factory, seam, container

- [x] 3.1 RED: customizer test `should_applyApiCallTimeout_when_customizerRuns` — apply `sqsApiTimeoutCustomizer` to a builder with a hand-built gap record (no Spring context, no network); assert `overrideConfiguration().apiCallTimeout()` is `PT1.5S`.
  - Acceptance: test fails before the bean exists (enriched 6.2.1).
- [x] 3.2 GREEN: add `AwsClientCustomizer<SqsAsyncClientBuilder> sqsApiTimeoutCustomizer()` bean in `SqsConfig` applying `overrideConfiguration(cfg -> cfg.apiCallTimeout(properties.apiCallTimeout()))`. No manual `SqsAsyncClient`/`S3AsyncClient` `@Bean` (customizers only — clash rule).
  - Acceptance: 3.1 passes; DoD grep finds no `SqsAsyncClient.builder()`/`S3AsyncClient.builder()` outside tests.
- [x] 3.3 RED+GREEN: callback tests `should_logWithoutPayload_when_ackSucceeds` + `should_logErrorWithoutPayload_when_ackFails` (unit, no network), then create `infrastructure/messaging/sqs/SqsAcknowledgementLoggingCallback.java` (`@Component implements AcknowledgementResultCallback<S3Event>`: DEBUG counts on success, ERROR counts + throwable type on failure).
  - Acceptance: generic type is `<S3Event>` (raw type compiles but never fires); never logs payloads, keys, URLs, queue URLs with account IDs, or exception messages embedding key material.
- [x] 3.4 RED+GREEN: executor wiring test (part of `should_wireBackpressureAndCallbackAndExecutor_when_factoryIsBuilt`), then add the single virtual-threads `TaskExecutor` bean (`Executors.newVirtualThreadPerTaskExecutor()`, in `SqsConfig` or a small `SqsListenerTaskExecutorConfig` — one bean, one place). Adapt `TaskExecutor` vs `Executor` to the `4.1.0` factory signature via `TaskExecutorAdapter` semantics; do NOT introduce `ThreadPoolTaskExecutor` (concurrency is governed by `maxConcurrentMessages`).
  - Acceptance: bean exists exactly once; factory receives it non-null.
- [x] 3.5 RED: factory options tests `should_buildFactoryWithOrderedOnSuccess_when_propertiesAreValid` (mode `ON_SUCCESS`, ordering `ORDERED`, gap interval `PT3S`/threshold `10`, listener concurrency/poll/timeouts from injected `SqsProperties`) + `should_wireBackpressureAndCallbackAndExecutor_when_factoryIsBuilt` (`ThroughputBackPressureHandler`, logging callback, virtual-thread executor non-null) with hand-built gap record + hand-built library `SqsProperties` + `NOOP` registry (no network, no context).
  - Acceptance: tests fail before the factory exists (enriched 6.2.2–6.2.3).
- [x] 3.6 GREEN: add `SqsMessageListenerContainerFactory<S3Event> sqsListenerContainerFactory(...)` per the normative option set (D4/design §5.4): fixed `ON_SUCCESS` + `ORDERED`; interval/threshold from gap record; `maxConcurrentMessages`/`maxMessagesPerPoll`/`pollTimeout`/`maxDelayBetweenPolls`/`autoStartup` from `sqsProperties.getListener()`; `ThroughputBackPressureHandler`; logging callback; virtual-threads executor; `JacksonJsonMessageConverter`; observation ternary (`isObservationEnabled() == true ? registry : NOOP`). Adapt fluent spelling to the `4.1.0` javadoc (`SqsContainerOptionsBuilder` naming moved 3.x→4.x) — option set is normative, spelling is not.
  - Acceptance: 3.5 passes; zero tuning hardcoded in `@Bean` methods.
- [x] 3.7 RED+GREEN: observation ternary both branches — `should_useNoopRegistry_when_libraryObservationIsDisabled` + enabled-branch counterpart (`true` → injected registry). Both branches covered (kills pitest mutants, guards library default-`false` trap, enriched 6.2.4).
  - Acceptance: `false`/absent → `ObservationRegistry.NOOP`; `true` → Micrometer registry.
- [x] 3.8 RED+GREEN: converter test `should_deserialiseS3Event_when_bodyIsS3NotificationJson` with fixture `s3-notification.json` (key `brands%2Fimages%2F<32-hex>`; assert bucket + encoded key) via `JacksonJsonMessageConverter`, then wire the converter into the factory (already in 3.6 — assert it here). Poison body MUST surface as listener exception (ack withheld → redrive → DLQ), never swallowed.
  - Acceptance: fixture exists; bucket + key assertions pass (enriched 6.3.3).
- [x] 3.9 RED+GREEN: seam test `should_completeWithoutThrow_when_listenerReceivesEvent`, then create `infrastructure/messaging/sqs/AssetEventsListener.java` (`@Component`, `void onAssetEvent(S3Event event)` — DEBUG-log `event.getRecords().size()` + delegate hook only; no DB access; KAN-13 replaces the body with `BlobUploadEvent` derivation + `confirmUploaded`).
  - Acceptance: no business handling, no database access (enriched 6.3.4); downstream contract documented (URL-decode key, `BlobType` prefix validation).
- [x] 3.10 GREEN: add `SqsMessageListenerContainer<S3Event> assetEventsContainer(factory, listener)` — ALWAYS created (no `@ConditionalOnProperty` suppression; `autoStartup` from `SqsProperties.getListener()` governs lifecycle): `factory.createContainer("asset-events")`, `setQueueNames(properties.assetsEventsQueue())`, `setMessageListener(listener::onAssetEvent)`, lifecycle managed by Spring. NOTE: enriched §12 step 8 mentions `@ConditionalOnProperty` — design D8 explicitly rejects it; this task follows the design/spec.
  - Acceptance: container bean exists; logical queue name (`develop-assets-events-queue`) resolved via `GetQueueUrl` (LocalStack-compatible).
- [x] 3.11 Wire `src/main/resources/application.yml` (§3.3: `spring.cloud.aws.region.static`, global `endpoint`, `s3.endpoint` + `path-style-access-enabled`, `sqs.endpoint` + `observation-enabled: true` + `listener.*`, credentials absent, slim `aws.s3`/`aws.sqs` with env mappings) and `src/test/resources/application.yml` (§3.4: global `endpoint: http://localhost:4566`, path-style `true`, `observation-enabled: true`, `max-concurrent-messages: 2`/`poll-timeout: PT5S`/`max-delay-between-polls: PT2S`/`auto-startup: false`, fast batching `PT1S`/`2`, `api-call-timeout: PT1.5S`; test creds via `AWS_ACCESS_KEY_ID/SECRET_ACCESS_KEY=test` env, never `StaticCredentialsProvider`, never `spring.cloud.aws.credentials.*` in `src/main`).
  - Acceptance: zero new operator env vars; one `AWS_ENDPOINT_URL` export covers both services unless service-level vars override; test container created-but-stopped.

## Phase 4 — Integration proof: round trip + fail-lazy boot + regression

- [x] 4.1 RED+GREEN: integration test `should_injectAsyncClientsAndContainer_when_contextStarts` (`@SpringBootTest` + LocalStack SQS+S3, `auto-startup: false`, `AWS_*=test`; mirrors `EndpointIntegrationTest`; surefire excludes `integration/**`, failsafe includes it).
  - Acceptance: exactly one `S3AsyncClient` + one `SqsAsyncClient` + one container injectable.
- [x] 4.2 RED+GREEN: round-trip test `should_deliverS3EventToListener_when_objectIsUploaded` — `putObject` to `develop-assets/brands/images/<32-hex>`, Awaitility latch on test listener, assert received `S3Event` bucket + decoded key; plus `should_startEmpty_when_queueHasNoMessages` (container starts, no poison). No business/DB assertions (KAN-13 owns them).
  - Acceptance: LocalStack round trip green and deterministic (enriched 6.4).
- [x] 4.3 Fail-lazy proof (DoD 8): boot the context with LocalStack STOPPED — context starts, no bean-creation network call; container logs connection errors and retries on backoff.
  - Acceptance: startup exit 0 with endpoints unreachable; failures surface only on first use/poll retry.
- [x] 4.4 Regression: `EndpointIntegrationTest` and all extending suites stay green; `mvn verify` green with LocalStack up (surefire + failsafe + JaCoCo ≥ 90% on new classes). Fix the `wait-for-it` port if still `5432`.
  - Acceptance: full `mvn verify` green (enriched 6.5).

## Phase 5 — Docs, hygiene gates, review

- [x] 5.1 Update `README.md` (Spring Cloud AWS + LocalStack SQS workflow) and `docs/backend-standards.md` (Messaging/SQS convention: container, `S3Event`, ordering, observability, LocalStack). OpenSpec spec artifacts are already done in this change (`specs/spring-cloud-aws-foundation/spec.md`, `specs/aws-s3-integration/spec.md`) — no new spec file in this task.
  - Acceptance: docs describe `spring.cloud.aws.*` + 4-key `aws.sqs.*`, `ON_SUCCESS`+`ORDERED`, observation opt-in, LocalStack env mappings.
- [x] 5.2 Run `mvn spotless:apply` (google-java-format + license header at `validate`); run the DoD grep suite and fix violations: (a) no `S3AsyncClient.builder()`/`SqsAsyncClient.builder()` outside tests, (b) no `DefaultCredentialsProvider.create()`/`StaticCredentialsProvider` in `src/main`, (c) no `aws.region`/`aws.s3.endpoint` reads, (d) no hardcoded credentials/endpoints in `src/main`, (e) no payload/key/URL/account-ID in logs, (f) no `software.amazon.awssdk`/`io.awspring` import outside `infrastructure/` (none in domain/application), (g) no vendor type in domain/application signatures.
  - Acceptance: spotless clean; all greps empty (as specified); JaCoCo ≥ 90% + pitest clean on customizer/observation branches.
- [x] 5.3 Self-review against DoD (enriched §7, 11 items) + KAN-13 supersede note: confirm hand-rolled `@Scheduled` poller is NOT implemented, `ORDERED`/`3s`/`10`/hardcoded mode deliberate, queue/bucket names stable vs `localstack-resources.yml`, branch `feat/KAN-16-spring-cloud-aws` with conventional commits, code review before merge.
  - Acceptance: every DoD item checked; open questions (design §Open Questions) recorded for the PR.

## Traceability

| Spec requirement | Tasks |
|---|---|
| foundation: auto-configured async clients (region/credentials/endpoint/timeout/BOM/no manual builders) | 1.1, 1.2, 1.3, 2.4, 3.1, 3.2, 4.1, 5.2 |
| foundation: managed container (`ON_SUCCESS`+`ORDERED`, callback, backpressure, executor, converter, seam, poison) | 3.3–3.10, 4.2 |
| foundation: externalised tuning + observation ternary | 2.1–2.4, 3.5–3.7, 3.11 |
| foundation: fail-lazy start + LocalStack round trip + no REST/migration/business/`SqsTemplate` | 4.2, 4.3, 4.4 |
| s3-integration: library keys source of truth, slim `bucket`+`presignTtl`, re-sourced/delegated `S3Config`, both-ways endpoint, default chain, no network at creation | 2.5, 2.6, 3.11, 4.4 |

## Out of scope (MUST NOT implement here)

KAN-13 business handling (`BrandImageConfirmService`, `BlobUploadEvent` derivation, key validation), REST endpoints, Flyway migrations, `SqsTemplate` producers, DLQ alarms, production queue-policy scoping, SQS Actuator health indicator, orphan sweeper, strain/product consumers, hand-rolled `@Scheduled` poller (superseded).
