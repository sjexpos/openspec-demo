## Purpose

Provides the managed Spring Cloud AWS async messaging foundation — starter auto-configured `S3AsyncClient` and `SqsAsyncClient` singletons plus one ordered `SqsMessageListenerContainer<S3Event>` for asset-event notifications — so follow-up asset stories can consume the S3 → SQS fan-out with managed polling, ordered batch acknowledgement, backpressure, and observability instead of hand-rolled polling boilerplate.

## Requirements

### Requirement: Provide auto-configured async AWS clients

The system SHALL expose exactly one `S3AsyncClient` bean and exactly one `SqsAsyncClient` bean via Spring Cloud AWS auto-configuration. No manual `S3AsyncClient` / `SqsAsyncClient` `@Bean` builder SHALL exist in application code; client customization SHALL use supported customizers only (`AwsClientCustomizer` / `SqsAsyncClientCustomizer`).

Both clients SHALL resolve region from `spring.cloud.aws.region.static`, which SHALL default to `us-east-1`. Both clients SHALL resolve credentials exclusively from the SDK default chain: `spring.cloud.aws.credentials.*` SHALL be left unset in `src/main`, no static or test credentials SHALL be committed, and application code SHALL NOT call `DefaultCredentialsProvider.create()` or use `StaticCredentialsProvider`.

Endpoint resolution SHALL follow the library keys: service-level `spring.cloud.aws.s3.endpoint` / `spring.cloud.aws.sqs.endpoint`, each falling back to the global `spring.cloud.aws.endpoint`. When an override is set to a non-empty URI the corresponding client SHALL target it; when absent or empty the client SHALL use real AWS endpoint resolution for the configured region.

The `SqsAsyncClient` SHALL enforce an API call timeout of 1.5 seconds (`PT1.5S`) via an `AwsClientCustomizer`, sourced from the `aws.sqs.api-call-timeout` gap property. Bean creation SHALL NOT perform network calls.

The compile classpath SHALL contain the pinned `io.awspring.cloud:spring-cloud-aws-dependencies` BOM plus `spring-cloud-aws-starter-s3`, `spring-cloud-aws-starter-sqs`, and the pinned `com.amazonaws:aws-lambda-java-events` (for the `S3Event` type). `mvn verify` SHALL pass with no new `duplicate-finder` conflict, or with a justified exclusion recorded in the PR.

#### Scenario: Async clients available for injection

- **WHEN** the application context starts
- **THEN** exactly one `S3AsyncClient` bean AND exactly one `SqsAsyncClient` bean SHALL be available for constructor injection

#### Scenario: Region defaults to us-east-1

- **WHEN** `spring.cloud.aws.region.static` is absent
- **THEN** both async clients SHALL resolve region `us-east-1`
- **AND** when the property is set the clients SHALL use the configured region

#### Scenario: Credentials resolved via default chain only

- **WHEN** the clients are built in any environment
- **THEN** credentials SHALL be resolved exclusively through the SDK default chain
- **AND** no credentials SHALL be hardcoded in `src/main`

#### Scenario: Endpoint overrides applied for local testing

- **WHEN** `spring.cloud.aws.s3.endpoint` / `spring.cloud.aws.sqs.endpoint` (or the global `spring.cloud.aws.endpoint` fallback) are set to non-empty URIs
- **THEN** the corresponding async client SHALL target that endpoint

#### Scenario: Default AWS endpoints in production

- **WHEN** the endpoint properties are absent or empty
- **THEN** the clients SHALL resolve the standard AWS endpoints for the configured region
- **AND** no endpoint override SHALL be applied

#### Scenario: SQS API call timeout of 1.5 seconds

- **WHEN** the `AwsClientCustomizer` for SQS runs
- **THEN** the `SqsAsyncClient` override configuration SHALL carry an API call timeout of `PT1.5S`

#### Scenario: No manual client builders

- **WHEN** the codebase is inspected
- **THEN** no `S3AsyncClient.builder()` / `SqsAsyncClient.builder()` call SHALL exist outside tests
- **AND** client tuning SHALL be applied through customizers only

### Requirement: Provide managed SQS listener container for asset events

The system SHALL expose one singleton `SqsMessageListenerContainer<S3Event>` bean listening to the queue named by `aws.sqs.assets-events-queue` (default `develop-assets-events-queue`). The container options SHALL be exactly: `acknowledgementMode = ON_SUCCESS`, `acknowledgementOrdering = ORDERED`, `acknowledgementInterval = ${aws.sqs.acknowledgement-interval:PT3S}`, `acknowledgementThreshold = ${aws.sqs.acknowledgement-threshold:10}`. Mode and ordering are a fixed enum contract in code, not tuning knobs.

The container SHALL be wired with a logging `AcknowledgementResultCallback<S3Event>` (ack counts at INFO/DEBUG, failures at ERROR with truncated key only), a `ThroughputBackPressureHandler`, a virtual-threads `TaskExecutor` (`Executors.newVirtualThreadPerTaskExecutor()`), and an `S3EventMessageConverter` (custom `SqsMessagingMessageConverter` using canonical Lambda serialization) mapping the S3→SQS JSON body to `S3Event`.

The listener seam (`AssetEventsListener.onAssetEvent`) SHALL only log and delegate; it SHALL perform no business handling and no database access (KAN-13 owns the PENDING → UPLOADED flip). A poison message body SHALL surface as a listener exception so `ON_SUCCESS` withholds the acknowledgement (redrive → DLQ after `maxReceiveCount: 5`); it SHALL never be silently swallowed. Logging SHALL never include message payloads, keys, URLs, or exception content embedding key material.

#### Scenario: Ordered batch acknowledgement of asset events

- **WHEN** `S3Event` messages arrive on the assets-events queue
- **THEN** the container SHALL acknowledge them in `ON_SUCCESS` + `ORDERED` mode with the configured interval and threshold

#### Scenario: Acknowledgement callback logs without payload

- **WHEN** batch acknowledgement succeeds or fails
- **THEN** the callback SHALL log counts at INFO/DEBUG on success and the failure at ERROR on failure
- **AND** no payload, key, URL, or key material SHALL appear in the log output

#### Scenario: Backpressure and virtual-thread execution

- **WHEN** the container factory is built
- **THEN** a `ThroughputBackPressureHandler` SHALL govern polling
- **AND** a virtual-threads `TaskExecutor` SHALL execute listener invocations

#### Scenario: Seam listener performs no business handling

- **WHEN** the seam listener receives an `S3Event`
- **THEN** it SHALL complete without database access or business state change

#### Scenario: Poison message is not silently swallowed

- **WHEN** the message body cannot be converted to `S3Event`
- **THEN** a listener exception SHALL propagate and the acknowledgement SHALL be withheld

### Requirement: Externalise listener tuning and observability

Listener concurrency (`maxConcurrentMessages`), poll size (`maxMessagesPerPoll`), poll timeout (`pollTimeout`), poll backoff (`maxDelayBetweenPolls`), and `autoStartup` SHALL be read from the library `spring.cloud.aws.sqs.listener.*` properties via the injected library `SqsProperties`, never re-declared in custom properties. The observation toggle SHALL be read from the library `spring.cloud.aws.sqs.observation-enabled` (whose library default is `false`; application YAML SHALL opt in with `true`), and the container SHALL be built with the Micrometer `ObservationRegistry` when the flag is `true` and with `ObservationRegistry.NOOP` when `false`.

Queue name, ack-batching interval/threshold, and client timeout SHALL come from the 4-key `aws.sqs.*` gap record only (`assets-events-queue`, `acknowledgement-interval`, `acknowledgement-threshold`, `api-call-timeout`); the library models no property for these knobs. No tuning value SHALL be hardcoded in any `@Bean` method.

#### Scenario: Tuning fully externalised

- **WHEN** an operator sets any `spring.cloud.aws.sqs.listener.*`, `spring.cloud.aws.sqs.observation-enabled`, or gap `aws.sqs.*` property
- **THEN** the container SHALL honour it without a code change

#### Scenario: Observation enabled via library flag

- **WHEN** the library `spring.cloud.aws.sqs.observation-enabled` is `true`
- **THEN** the container SHALL be built with the Micrometer `ObservationRegistry`
- **AND** the Actuator `metrics` / `health` endpoints SHALL stay green

#### Scenario: Noop registry when observation is disabled

- **WHEN** the library `spring.cloud.aws.sqs.observation-enabled` is `false` or absent
- **THEN** the container SHALL be built with `ObservationRegistry.NOOP`

### Requirement: Start without reachable endpoints and prove the LocalStack round trip

Async client bean creation SHALL perform no network call. The application context SHALL start successfully while S3/SQS endpoints are unreachable; connection failures SHALL surface only on first use or poll retry, with the container logging the error and retrying on its backoff instead of preventing startup.

An integration test SHALL prove the LocalStack round trip: `putObject` under `brands/images/<key>` → S3 → SQS notification → the container invokes the `S3Event` listener with the expected bucket and decoded key. The round trip SHALL assert no business or database state (that belongs to KAN-13).

This capability SHALL NOT introduce REST endpoints, Flyway migrations, business handling of `S3Event`, `SqsTemplate` producer beans, DLQ alarms, or production queue-policy scoping.

#### Scenario: Startup without a reachable endpoint

- **WHEN** the application starts while SQS/S3 endpoints are unreachable
- **THEN** the context SHALL start successfully
- **AND** failures SHALL surface only on first use / poll retry

#### Scenario: LocalStack round trip delivers S3Event to the listener

- **WHEN** an object is uploaded under `brands/images/<key>` with the bucket notification fan-out active
- **THEN** the container SHALL invoke the listener with an `S3Event` carrying the expected bucket and decoded key

#### Scenario: No REST, migration, or business handling

- **WHEN** this change is delivered
- **THEN** no REST endpoint, no Flyway migration, no business rule on `S3Event`, and no `SqsTemplate` producer SHALL be introduced
