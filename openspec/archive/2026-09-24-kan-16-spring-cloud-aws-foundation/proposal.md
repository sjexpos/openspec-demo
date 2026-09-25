## Why

Every asset flow (`brand_images` today; `strain`/`product` media tomorrow) depends on the S3 → SQS fan-out already provisioned in `localstack-resources.yml` (`develop-assets` bucket → `develop-assets-events-queue`, DLQ + redrive `maxReceiveCount: 5`), yet nothing in `src/main/java` consumes that queue. KAN-13 designed a hand-rolled `@Scheduled` poller precisely to avoid a Spring Cloud dependency — that design is **superseded** by this change. Adopting the managed Spring Cloud AWS async foundation now, before the first consumer (KAN-13 rework) is written, removes polling boilerplate and provides at-least-once + ordered batch-ack semantics, throughput-adaptive polling, and listener observability at the cheapest possible moment.

## What Changes

- Add `io.awspring.cloud:spring-cloud-aws-dependencies` BOM pinned to `4.1.0` plus `spring-cloud-aws-starter-s3`, `spring-cloud-aws-starter-sqs`, and `com.amazonaws:aws-lambda-java-events` pinned to `3.16.0` (for the `S3Event` type).
- Expose singleton `S3AsyncClient` and `SqsAsyncClient` beans **via Spring Cloud AWS auto-configuration** from `spring.cloud.aws.*` (region, endpoint, credentials via the SDK default chain); no manual client builders. `SqsAsyncClient` gets a 1.5 s API call timeout via an `AwsClientCustomizer`.
- Add one managed `SqsMessageListenerContainer<S3Event>` listening to the assets-events queue with `ON_SUCCESS` acknowledgement, `ORDERED` ordering, configurable batching (`acknowledgementInterval = PT3S`, `acknowledgementThreshold = 10`), a logging `AcknowledgementResultCallback`, a `ThroughputBackPressureHandler`, a virtual-threads `TaskExecutor`, and Micrometer observation enabled.
- Migrate sync `S3Config` / `AwsS3Properties` (KAN-14) to the same source of truth: region/endpoint/path-style move to `spring.cloud.aws.*`; `AwsS3Properties` shrinks to `bucket` + `presignTtl`. Add a slim 4-key `AwsSqsProperties` record (`assets-events-queue`, `acknowledgement-interval`, `acknowledgement-threshold`, `api-call-timeout`); listener concurrency/poll/backoff/auto-startup and the observation toggle are read from the library's `SqsProperties` (`spring.cloud.aws.sqs.listener.*` / `spring.cloud.aws.sqs.observation-enabled`).
- Wire `spring.cloud.aws.*` + slim `aws.*` blocks into `src/main/resources/application.yml` and `src/test/resources/application.yml`; add unit + integration tests (LocalStack round trip `putObject` → `S3Event` in listener, no DB assertions); update `README.md` and `docs/backend-standards.md` (Messaging/SQS convention).
- Out of scope: any business handling of `S3Event` (KAN-13 `BrandImageConfirmService` consumes the `AssetEventsListener` seam), REST endpoints, Flyway migrations, `SqsTemplate` producers, DLQ alarms, production queue-policy scoping.

## Capabilities

### New Capabilities

- `spring-cloud-aws-foundation`: Spring Cloud AWS async messaging foundation — starter auto-configured singleton `S3AsyncClient` and `SqsAsyncClient` beans configured from `spring.cloud.aws.region.static` with endpoint overrides from `spring.cloud.aws.s3.endpoint` / `spring.cloud.aws.sqs.endpoint` (falling back to `spring.cloud.aws.endpoint`), credentials from the SDK default chain, 1.5 s SQS API call timeout via customizer; singleton `SqsMessageListenerContainer<S3Event>` on the configured assets-events queue with `ON_SUCCESS` + `ORDERED` acknowledgement, externalised interval/threshold/concurrency/polling, logging acknowledgement callback, throughput backpressure handler, virtual-threads executor, enabled Micrometer observation; fail-lazy bean creation with no network calls at startup.

### Modified Capabilities

- `aws-s3-integration`: the S3 configuration source of truth migrates from `aws.region` / `aws.s3.endpoint` / `aws.s3.path-style-access` to the library keys `spring.cloud.aws.region.static` / `spring.cloud.aws.s3.endpoint` (with `spring.cloud.aws.endpoint` fallback) / `spring.cloud.aws.s3.path-style-access-enabled`; `AwsS3Properties` keeps only `bucket` + `presign-ttl` and `S3Config` delegates client construction to the starters (or re-sources its beans from the library keys). Endpoint-override and default-AWS-resolution behavior is preserved, now driven by the `spring.cloud.aws.*` keys.

## Impact

- **Code**: modify `pom.xml` (BOM + 3 dependencies), `infrastructure/config/AwsS3Properties.java`, `infrastructure/config/S3Config.java`; create `infrastructure/config/AwsSqsProperties.java`, `infrastructure/config/SqsConfig.java`, `infrastructure/messaging/sqs/SqsAcknowledgementLoggingCallback.java`, virtual-threads executor bean, `infrastructure/messaging/sqs/AssetEventsListener.java` (seam for KAN-13). No domain, application, or presentation layer changes; no AWS/Spring-Cloud imports outside `infrastructure/config` and `infrastructure/messaging/sqs`.
- **Config**: `src/main/resources/application.yml` and `src/test/resources/application.yml` (`spring.cloud.aws.*` + 4-key `aws.sqs.*`); no new operator env vars (`AWS_REGION`, `AWS_ENDPOINT_URL_S3`/`AWS_ENDPOINT_URL` mappings preserved).
- **Build**: strict gates apply (`duplicate-finder` with `failBuildInCaseOfConflict=true` is the top risk — run `mvn verify` after adding dependencies first; `spotless`, SpotBugs, JaCoCo ≥ 90% on new classes, pitest on customizer/observation branches); TDD with `should_[expected]_when_[condition]` naming.
- **Docs**: `README.md` (Spring Cloud AWS + LocalStack SQS workflow) and `docs/backend-standards.md` (Messaging/SQS convention); KAN-13 refinement note updated (hand-rolled poller superseded, consumer reworked onto this container).
