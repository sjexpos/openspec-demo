# KAN-16 — Enriched User Story: Add Spring Cloud AWS (S3 + SQS async foundation)

> **Ticket**: `KAN-16` — *Add Spring Cloud AWS*.
> **Type**: Infrastructure enablement (no REST endpoint, no Flyway migration).
> **Status at refinement**: *Por hacer* (To Do). No status transition required by `enrich-us`
> (transition to *Pending refinement validation* applies only from *To refine*).

## Executive Summary

Add the **Spring Cloud AWS async foundation** to the application: the latest
`io.awspring.cloud:spring-cloud-aws` BOM with **S3 and SQS starters**, plus
`com.amazonaws:aws-lambda-java-events` so SQS messages can be typed as
`com.amazonaws.services.lambda.runtime.events.S3Event`.

Concretely this ticket delivers:

1. **Async AWS clients via Spring Cloud AWS auto-configuration** — `S3AsyncClient`
   and `SqsAsyncClient` created by the starters (no manual builders), honouring
   the **library's own properties** for region, endpoint and credentials. The
   existing sync `S3Config` from KAN-14 is **migrated to the same source of
   truth** (see §5.3) so there is exactly one region/endpoint configuration.
2. **One managed listener container** — `SqsMessageListenerContainer<S3Event>`
   listening to the **assets-events queue**, with `ON_SUCCESS` acknowledgement,
   `ORDERED` ordering, configurable batching (`acknowledgementInterval = 3s`,
   `acknowledgementThreshold = 10`), a logging `AcknowledgementResultCallback`,
   `ThroughputBackPressureHandler`, and a **virtual-threads** `TaskExecutor`.
3. **Full externalised tuning** — `maxConcurrentMessages`,
   `maxMessagesPerPoll`, `pollTimeout`, `maxDelayBetweenPolls` all come from
   a small custom `aws.sqs.*` record (the library exposes no properties for
   these container knobs); `SqsAsyncClient` gets an **API call timeout of
   1.5 s** via an `AwsClientCustomizer` (also no library property).
4. **Observability enabled** — Micrometer observation on the SQS container so
   polls, processing and acknowledgements are visible in Actuator/metrics.

> **Property strategy (added 2026-09-24): use the library defaults wherever
> they exist.** Region, endpoint, path-style access and credentials come from
> `spring.cloud.aws.*` (auto-configured clients honour them out of the box).
> Custom `aws.*` remains **only** for what the library does not model:
> `aws.s3.bucket`, `aws.s3.presign-ttl`, `aws.sqs.assets-events-queue` and the
> `aws.sqs.*` listener-tuning/timeout/feature-flag knobs. Migration map in
> §3.1 — no dual source of truth, no manual `Region.of(...)` /
> `DefaultCredentialsProvider.create()` builders.

This is **pure infrastructure**: no business rule, no confirm-service logic
(KAN-13 owns the PENDING → UPLOADED flip and must be reworked on top of this
container — see §8.1), no controller, no migration. The next story simply
injects the container/listener and handles `S3Event`.

---

## 1. User story (revised)

> *As a **backend developer**,
> I want **Spring Cloud AWS (S3 + SQS) with async clients and a managed
> `SqsMessageListenerContainer<S3Event>` listening to the assets-events queue**,
> so that **asset-upload notifications are consumed with managed polling,
> ordered batch acknowledgement, backpressure and observability, identically
> against LocalStack and real AWS**.*

### Business value

Every asset flow (`brand_images` today; `strain`/`product` media tomorrow)
depends on the **S3 → SQS fan-out** already provisioned in
`localstack-resources.yml` (`develop-assets` bucket → `develop-assets-events-queue`,
DLQ + redrive `maxReceiveCount: 5`). Today nothing consumes that queue in code
(KAN-13 designed a hand-rolled `@Scheduled` poller precisely to avoid a Spring
Cloud dependency). A managed container removes polling boilerplate, gives
**at-least-once + ordered batch-ack semantics out of the box**, adapts poll
rate to throughput, and surfaces listener health in metrics — the cheapest
moment to adopt it is **before** the first consumer (KAN-13) is written, not
after.

### Acceptance criteria

1. `spring-cloud-aws-dependencies` BOM (pinned, see §4.1) + `starter-s3` +
   `starter-sqs` + `aws-lambda-java-events` (pinned) are on the compile
   classpath; `mvn verify` passes with no new `duplicate-finder` conflict or a
   justified exclusion.
2. The Spring context exposes exactly one `S3AsyncClient` bean and one
   `SqsAsyncClient` bean **via Spring Cloud AWS auto-configuration**; both
   resolve region from `spring.cloud.aws.region.static` (`us-east-1` default)
   and credentials from the SDK default chain (no `access-key`/`secret-key`
   set in `src/main`, no hardcoded credentials, no manual
   `DefaultCredentialsProvider.create()` in application code).
3. When `spring.cloud.aws.s3.endpoint` / `spring.cloud.aws.sqs.endpoint` (or
   the global `spring.cloud.aws.endpoint` fallback) is **empty/absent**, the
   corresponding auto-configured client uses **real AWS endpoint resolution**.
   When **set**, the override is applied (LocalStack `http://localhost:4566`
   in tests / `http://localstack:4566` in compose). The legacy
   `aws.region` / `aws.s3.endpoint` / `aws.sqs.endpoint` keys are **removed as
   sources of truth** (§3.1 migration map); leftover `aws.*` aliases, if kept
   temporarily, must resolve from the `spring.cloud.aws.*` values, never the
   other way round.
4. `SqsAsyncClient` is built with an **API call timeout of 1.5 s**
   (`overrideConfiguration().apiCallTimeout(PT1.5S)`).
5. The context exposes one `SqsMessageListenerContainer<S3Event>` bean
   listening to `${aws.sqs.assets-events-queue}` (`develop-assets-events-queue`
   default) whose options are exactly:
   `acknowledgementMode = ON_SUCCESS`,
   `acknowledgementOrdering = ORDERED`,
   `acknowledgementInterval = ${aws.sqs.acknowledgement-interval:PT3S}`,
   `acknowledgementThreshold = ${aws.sqs.acknowledgement-threshold:10}`.
6. The container is wired with: an `AcknowledgementResultCallback` that logs
   ack outcomes (counts at INFO/DEBUG, failures at ERROR with truncated key
   only), a `ThroughputBackPressureHandler`, and a virtual-threads
   `TaskExecutor` (`Executors.newVirtualThreadPerTaskExecutor()`).
7. `maxConcurrentMessages`, `maxMessagesPerPoll`, `pollTimeout`,
   `maxDelayBetweenPolls`, `autoStartup` and the observation toggle are all read
   from the library `spring.cloud.aws.sqs.listener.*` /
   `spring.cloud.aws.sqs.observation-enabled` (via injected `SqsProperties`);
   ack-batching and client timeout from the 4-key `aws.sqs.*` gap record (no
   hardcoded tuning in `@Bean` methods).
8. Observability is enabled on the container via the library
   `spring.cloud.aws.sqs.observation-enabled: true` (Micrometer
   `ObservationRegistry` wired; listener observations appear; Actuator
   `metrics`/`health` still green).
9. An integration test proves the LocalStack round trip: `putObject` under
   `brands/images/<key>` → S3 → SQS notification → container invokes the
   `S3Event` listener (no business assertion on DB state — that belongs to
   KAN-13).
10. The application still starts when LocalStack/AWS is unreachable — async
    client bean creation performs **no network call** (fail-lazy); the
    container logs connection errors and retries on its backoff, it does not
    prevent context startup.

---

## 2. Scope: what changes and what does NOT

| In scope | Out of scope (explicit follow-ups) |
|----------|------------------------------------|
| BOM + 3 dependencies (§4.1) | Any business handling of `S3Event` (KAN-13 `BrandImageConfirmService`) — this ticket wires a no-op/logging listener seam only |
| `S3AsyncClient` + `SqsAsyncClient` via auto-config + timeout/path-style customizers (no manual builders) | New manual `S3AsyncClient`/`SqsAsyncClient` `@Bean` builders duplicating the starters (would clash with auto-config) |
| Migration of sync `S3Config`/`AwsS3Properties` to `spring.cloud.aws.*` (region/endpoint/path-style) | Keeping `aws.region` / `aws.s3.endpoint` / `aws.sqs.endpoint` as a second source of truth |
| Small `AwsSqsProperties` record for queue + listener tuning only (§4.2) + `spring.cloud.aws.*` blocks in `application.yml` (main + test) | Flyway migration (none — no table change) |
| `SqsMessageListenerContainer<S3Event>` + factory, converter, callback, backpressure, virtual-thread executor | REST endpoint (none) |
| Observability wiring + docs (`README`, `backend-standards`) | Hand-rolled `@Scheduled` poller (KAN-13 design is **superseded**; do not implement it) |
| Unit + integration tests per §5 | Production bucket hardening / queue-policy scoping (separate infra tickets) |
| Migration notes + OpenSpec spec | DLQ alarm, orphan sweeper, listing endpoints |

**No REST endpoint contract** — this ticket exposes no URL, no DTO, no status
code. **No DB field list** — no column is added or altered.

---

## 3. Configuration contract

### 3.1 Property strategy: library first, `aws.*` only for the gap

| Concern | Library property (source of truth) | Legacy `aws.*` key | Decision |
|---------|------------------------------------|--------------------|----------|
| Region (all clients) | `spring.cloud.aws.region.static` | `aws.region` | **Migrate.** Delete `aws.region` as config; code reads the library's `AwsRegionProvider`. Keep the `AWS_REGION` env var by mapping it in YAML. |
| Global endpoint override (LocalStack) | `spring.cloud.aws.endpoint` | — | **Adopt.** One variable covers every auto-configured client. |
| S3 endpoint override | `spring.cloud.aws.s3.endpoint` | `aws.s3.endpoint` | **Migrate.** Service-level override wins over the global one; empty = real AWS. |
| S3 path-style access (LocalStack) | `spring.cloud.aws.s3.path-style-access-enabled` | `aws.s3.path-style-access` | **Migrate.** Same flag, library spelling. |
| S3 region override (if ever needed) | `spring.cloud.aws.s3.region` | — | Adopt on demand; defaults to the static region. |
| SQS endpoint override | `spring.cloud.aws.sqs.endpoint` | `aws.sqs.endpoint` | **Migrate.** Same semantics as S3. |
| SQS region override (if ever needed) | `spring.cloud.aws.sqs.region` | — | Adopt on demand; defaults to the static region. |
| Credentials | `spring.cloud.aws.credentials.*` (left **unset**) | — | **Leave unset** so the SDK default chain (`DefaultCredentialsProvider`) applies. Never set static/test credentials in `src/main`; tests export `AWS_ACCESS_KEY_ID/SECRET_ACCESS_KEY=test`. |
| Listener concurrency | `spring.cloud.aws.sqs.listener.max-concurrent-messages` | `aws.sqs.max-concurrent-messages` | **Migrate (rev 3).** Library `SqsProperties.Listener` models it and the auto-config maps it onto the default factory (`SqsAutoConfiguration.configureProperties`). Custom factory reads it from injected `SqsProperties` (§5.4) instead of redefining it. |
| Listener poll size | `spring.cloud.aws.sqs.listener.max-messages-per-poll` | `aws.sqs.max-messages-per-poll` | **Migrate (rev 3).** Same as above. |
| Listener poll timeout | `spring.cloud.aws.sqs.listener.poll-timeout` | `aws.sqs.poll-timeout` | **Migrate (rev 3).** Same as above. |
| Listener poll backoff | `spring.cloud.aws.sqs.listener.max-delay-between-polls` | `aws.sqs.max-delay-between-polls` | **Migrate (rev 3).** Same as above. |
| Listener auto-startup | `spring.cloud.aws.sqs.listener.auto-startup` | `aws.sqs.polling-enabled` | **Migrate (rev 3).** `false` creates-but-does-not-start the container (tests start it manually); no `@ConditionalOnProperty` bean suppression. |
| SQS observation toggle | `spring.cloud.aws.sqs.observation-enabled` (library default `false`) | `aws.sqs.observation-enabled` | **Migrate (rev 3).** Must be set `true` explicitly; the auto-config gates `ObservationRegistry` wiring on it for template + default factory, and our custom factory reads the same flag (§5.4). |
| Assets bucket | — (no library property) | `aws.s3.bucket` | **Keep** in `aws.s3`. Domain concern. |
| Presigned-URL TTL | — | `aws.s3.presign-ttl` | **Keep** in `aws.s3`. Domain concern. |
| Queue name | — | `aws.sqs.assets-events-queue` | **Keep** in `aws.sqs`. Domain concern. |
| Ack batching (`acknowledgement-interval/threshold`) | — (`SqsProperties.Listener` has **no** ack-batching fields; verified against `SqsProperties` source) | `aws.sqs.*` | **Keep** in `aws.sqs`. Genuine gap in the library model. |
| Client timeout (`api-call-timeout` 1.5 s) | — (needs an `AwsClientCustomizer`/`SqsAsyncClientCustomizer`) | `aws.sqs.api-call-timeout` | **Keep** in `aws.sqs`; applied via customizer (§5.4). |

Why not keep `aws.region`/`aws.*.endpoint` as aliases? Two region sources
diverge silently (the classic `PermanentRedirect` / wrong-endpoint bug), double
the test matrix, and fight the starters' auto-configuration (which already
reads `spring.cloud.aws.*`). The migration keeps operator-facing env vars
stable (`AWS_REGION`, `AWS_ENDPOINT_URL_S3`, …) by mapping them onto the new
keys in YAML — see §3.2.

### 3.2 Custom properties kept (`aws.*` — the gap only)

| Property | Type | Default | Purpose |
|----------|------|---------|---------|
| `aws.s3.bucket` | `String` | `develop-assets` | Domain bucket (existing, unchanged) |
| `aws.s3.presign-ttl` | `Duration` | `PT15M` | Domain TTL (existing, unchanged) |
| `aws.sqs.assets-events-queue` | `String` | `develop-assets-events-queue` | Queue the container listens to |
| `aws.sqs.acknowledgement-interval` | `Duration` | `PT3S` | Batch-ack interval (ticket requirement; no library equivalent) |
| `aws.sqs.acknowledgement-threshold` | `int` | `10` | Batch-ack size (ticket requirement; no library equivalent) |
| `aws.sqs.api-call-timeout` | `Duration` | `PT1.5S` | `SqsAsyncClient` API call timeout via customizer (ticket requirement) |

Deleted in rev 3 (migrated per §3.1): `aws.region`,
`aws.s3.endpoint`, `aws.s3.path-style-access`, `aws.sqs.endpoint`,
`aws.sqs.max-concurrent-messages`, `aws.sqs.max-messages-per-poll`,
`aws.sqs.poll-timeout`, `aws.sqs.max-delay-between-polls`,
`aws.sqs.polling-enabled`, `aws.sqs.observation-enabled`.
`AwsSqsProperties` carries the 4 `aws.sqs.*` rows above **only** (§5.2);
everything else is read from injected `SqsProperties`.

Acknowledgement **mode** (`ON_SUCCESS`) and **ordering** (`ORDERED`) are
fixed in code per the ticket (they are an enum contract, not tuning knobs).
If the team wants them externalised later, add two enum properties — do not
block this slice on it (flagged in §8.5).

### 3.3 `src/main/resources/application.yml`

```yaml
spring:
  cloud:
    aws:
      region:
        static: ${AWS_REGION:us-east-1}
      endpoint: ${AWS_ENDPOINT_URL:}                       # global LocalStack override (empty => real AWS)
      s3:
        endpoint: ${AWS_ENDPOINT_URL_S3:${AWS_ENDPOINT_URL:}} # service override wins over global
        path-style-access-enabled: ${AWS_S3_PATH_STYLE_ACCESS:false}
      sqs:
        endpoint: ${AWS_ENDPOINT_URL_SQS:${AWS_ENDPOINT_URL:}}
        observation-enabled: true                   # library default is false; must opt in
        listener:
          max-concurrent-messages: ${AWS_SQS_MAX_CONCURRENT_MESSAGES:10}
          max-messages-per-poll: ${AWS_SQS_MAX_MESSAGES_PER_POLL:10}
          poll-timeout: ${AWS_SQS_POLL_TIMEOUT:PT20S}
          max-delay-between-polls: ${AWS_SQS_MAX_DELAY_BETWEEN_POLLS:PT10S}
          auto-startup: ${AWS_SQS_AUTO_STARTUP:true}
      # credentials intentionally absent => SDK default chain (IRSA / instance profile / env in prod)

aws:
  s3:
    bucket: ${AWS_S3_BUCKET:develop-assets}
    presign-ttl: ${AWS_S3_PRESIGN_TTL:PT15M}
  sqs:
    assets-events-queue: ${AWS_SQS_ASSETS_EVENTS_QUEUE:develop-assets-events-queue}
    acknowledgement-interval: ${AWS_SQS_ACK_INTERVAL:PT3S}
    acknowledgement-threshold: ${AWS_SQS_ACK_THRESHOLD:10}
    api-call-timeout: ${AWS_SQS_API_CALL_TIMEOUT:PT1.5S}
```

Operator impact is **zero new env vars**: `AWS_REGION`, `AWS_ENDPOINT_URL_S3`
(and optionally `AWS_ENDPOINT_URL_SQS` / `AWS_ENDPOINT_URL`) keep working;
`AWS_S3_PATH_STYLE_ACCESS` keeps working under its new key. One export
(`AWS_ENDPOINT_URL=http://localstack:4566`) now covers both services unless a
service-level var overrides it.

### 3.4 `src/test/resources/application.yml`

```yaml
spring:
  cloud:
    aws:
      region:
        static: us-east-1
      endpoint: http://localhost:4566
      s3:
        path-style-access-enabled: true
      sqs:
        observation-enabled: true
        listener:
          max-concurrent-messages: 2
          max-messages-per-poll: 10
          poll-timeout: PT5S
          max-delay-between-polls: PT2S
          auto-startup: false                     # container created but not started; tests start it manually

aws:
  sqs:
    assets-events-queue: develop-assets-events-queue
    acknowledgement-interval: PT1S          # faster batching in tests (overrides the 3 s default)
    acknowledgement-threshold: 2
    api-call-timeout: PT1.5S
```

Credentials for tests are environment variables (`AWS_ACCESS_KEY_ID=test`,
`AWS_SECRET_ACCESS_KEY=test`) so the default chain resolves them — never
`StaticCredentialsProvider` and never `spring.cloud.aws.credentials.*` in
`src/main`.

---

## 4. Files to create / modify

| # | File | Action | Notes |
|---|------|--------|-------|
| 1 | `pom.xml` | **modify** | BOM import + 3 dependencies (§5.1) |
| 2 | `src/main/java/com/example/demo/infrastructure/config/AwsSqsProperties.java` | **create** | `@ConfigurationProperties("aws.sqs")` for queue + ack-batching + timeout only, **no endpoint/listener/observation flags** (§5.2) |
| 3 | `src/main/java/com/example/demo/infrastructure/config/AwsS3Properties.java` | **modify (migrate)** | Shrink to `bucket` + `presignTtl`; delete `region`/`endpoint`/`pathStyleAccess` (now `spring.cloud.aws.*`, §5.3) |
| 4 | `src/main/java/com/example/demo/infrastructure/config/S3Config.java` | **modify (migrate)** | Sync beans read the library region/endpoint (or delegate to auto-configured clients); delete manual `Region.of(...)`/`DefaultCredentialsProvider` builders (§5.3) |
| 5 | `src/main/java/com/example/demo/infrastructure/config/SqsConfig.java` | **create** | No manual `SqsAsyncClient` `@Bean`: timeout via `AwsClientCustomizer`, container factory + `SqsMessageListenerContainer<S3Event>` (§5.4) |
| 6 | `src/main/java/com/example/demo/infrastructure/messaging/sqs/SqsAcknowledgementLoggingCallback.java` | **create** | `AcknowledgementResultCallback` that only logs (§5.5) |
| 7 | `src/main/java/com/example/demo/infrastructure/messaging/sqs/SqsListenerTaskExecutorConfig.java` (or bean inside `SqsConfig`) | **create** | Virtual-threads `TaskExecutor` (§5.6) |
| 8 | `src/main/java/com/example/demo/infrastructure/messaging/sqs/AssetEventsListener.java` | **create** | Seam listener `void onAssetEvent(S3Event event)` — logs + delegates hook; KAN-13 replaces the body (§5.7) |
| 9 | `src/main/resources/application.yml` | **modify** | §3.3 block (`spring.cloud.aws.*` + slim `aws.*`) |
| 10 | `src/test/resources/application.yml` | **modify** | §3.4 block |
| 11 | `src/test/java/.../infrastructure/config/AwsSqsPropertiesTests.java` | **create** | Binding tests (§6.1) |
| 12 | `src/test/java/.../infrastructure/config/SqsConfigTests.java` | **create** | Container-options tests, no network (§6.2) |
| 13 | `src/test/java/.../infrastructure/messaging/sqs/*Tests.java` | **create** | Callback + converter + listener unit tests (§6.3) |
| 14 | `src/test/java/.../integration/messaging/SqsContainerIntegrationTests.java` | **create** | LocalStack round trip (§6.4) |
| 15 | `README.md` | **modify** | Spring Cloud AWS + LocalStack SQS workflow |
| 16 | `docs/backend-standards.md` | **modify** | Messaging/SQS convention (container, S3Event, observability) |
| 17 | `spotbugs-exclude.xml` | **modify (only if needed)** | Prefer fixing over excluding |

Package placement follows `docs/backend-standards.md`: Spring setup in
`infrastructure/config/`, AWS specifics in `infrastructure/messaging/sqs/`.
**No `software.amazon.awssdk` / `io.awspring` import is allowed outside those
two packages** (storage-port convention); no vendor type in any domain or
application signature.

---

## 5. Implementation details

### 5.1 `pom.xml`

Pin versions in `<properties>` (never floating):

```xml
<properties>
    ...
    <spring-cloud-aws.version>4.1.0</spring-cloud-aws.version>
    <aws-lambda-events.version>3.16.0</aws-lambda-events.version>
</properties>

<dependencyManagement>
    <!-- existing AWS SDK BOM stays first -->
    <dependencies>
        <dependency>
            <groupId>software.amazon.awssdk</groupId>
            <artifactId>bom</artifactId>
            <version>${aws-sdk.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
        <dependency>
            <groupId>io.awspring.cloud</groupId>
            <artifactId>spring-cloud-aws-dependencies</artifactId>
            <version>${spring-cloud-aws.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

```xml
<!-- Spring Cloud AWS — S3 + SQS -->
<dependency>
    <groupId>io.awspring.cloud</groupId>
    <artifactId>spring-cloud-aws-starter-s3</artifactId>
</dependency>
<dependency>
    <groupId>io.awspring.cloud</groupId>
    <artifactId>spring-cloud-aws-starter-sqs</artifactId>
</dependency>
<!-- Lambda event model (S3Event) -->
<dependency>
    <groupId>com.amazonaws</groupId>
    <artifactId>aws-lambda-java-events</artifactId>
    <version>${aws-lambda-events.version}</version>
</dependency>
```

> **Version notes.** `4.1.0` (22 Jul 2026) is the latest Spring Cloud AWS on
> Maven Central at refinement time and pairs with Spring Cloud `2025.1.x`
> (use `2025.1.2`, the train that adds Boot `4.1.0` compatibility) and Boot
> `4.x` / Framework `7.x` / AWS SDK v2 — matching this repo (Boot `4.1.0`,
> SDK BOM `2.55.1`). Re-verify on Central at implementation time; if a newer
> `4.1.x` exists, take it and record the version in the PR. `aws-lambda-java-events`
> `3.16.0` is the documented current minor (javadoc.io `3.16.1`); any
> `3.11.1+` deserialises `S3Event` correctly, but pin exactly one version.
> Add dependencies **first** and run `mvn verify` before writing code: the
> `duplicate-finder` gate (`failBuildInCaseOfConflict=true`,
> `checkRuntimeClasspath=true`) is the most likely breakage (AWS SDK + Spring
> Cloud AWS both shade Jackson/Netty artefacts).

### 5.2 `AwsSqsProperties` (gap only — queue + ack-batching + timeout)

```java
@ConfigurationProperties(prefix = "aws.sqs")
@Validated
public record AwsSqsProperties(
    @NotBlank String assetsEventsQueue,
    @NotNull Duration acknowledgementInterval,
    @Positive int acknowledgementThreshold,
    @NotNull Duration apiCallTimeout) {}
```

Register via `@EnableConfigurationProperties(AwsSqsProperties.class)` on
`SqsConfig`. Everything else the container needs (concurrency, poll size,
poll timeout, backoff, auto-startup, observation toggle) is owned by the
library's `SqsProperties` (`spring.cloud.aws.sqs.*`, §3.1) and injected
alongside — never re-declared here. Tests assert binding of the 4 gap knobs
plus threshold validation; listener/observation binding is covered by asserting
the factory reads them from `SqsProperties` (§6.2).

### 5.3 `AwsS3Properties` + `S3Config` migration (delete the second source of truth)

`AwsS3Properties` shrinks to what the library does not model:

```java
@ConfigurationProperties(prefix = "aws.s3")
@Validated
public record AwsS3Properties(@NotBlank String bucket, @NotNull Duration presignTtl) {}
```

`S3Config` stops building clients by hand. Two compliant options (pick one in
implementation, do not mix):

- **Option A (preferred): delegate to the starters.** Delete the manual
  `S3Client`/`S3Presigner` `@Bean` methods and let `spring-cloud-aws-starter-s3`
  auto-configure `S3Client`, `S3AsyncClient` and `S3Presigner` from
  `spring.cloud.aws.*` (region/endpoint/path-style/credentials). Keep only
  domain-facing configuration (bucket/TTL) in application code.
- **Option B (minimal diff): keep the `@Bean` methods but re-source them.**
  Inject the library's `AwsRegionProvider` (and endpoint via
  `@Value("${spring.cloud.aws.s3.endpoint:}")` /
  `@Value("${spring.cloud.aws.endpoint:}")` with service-over-global
  precedence + `path-style-access-enabled`) instead of `AwsS3Properties.region()`
  / `s3().endpoint()`. Never `Region.of(this.properties.region())` again.

Either way: no `DefaultCredentialsProvider.create()` in application code (the
starters wire the default chain when `spring.cloud.aws.credentials.*` is
absent), no `aws.region` / `aws.s3.endpoint` reads, and bean creation stays
offline (no `listBuckets`/`headBucket` probe). `S3AsyncClient` needs **no new
`S3AsyncConfig` class** — it arrives with the starter; only add a customizer
if S3 needs non-default tuning (it does not in this slice).

> Clash warning: defining your own `S3AsyncClient`/`SqsAsyncClient` `@Bean`
> while the starters are on the classpath creates duplicate-bean / conditional
> surprises. Customizers (`AwsClientCustomizer`, `SqsAsyncClientCustomizer`)
> are the supported extension point — use them.

### 5.4 `SqsConfig` — customizer, factory, container (no manual client bean)

The starters auto-configure `SqsAsyncClient` from `spring.cloud.aws.*`
(region/endpoint/credentials). This config only **customizes** it and builds
the listener container, sourcing listener tuning + observation from the
library's `SqsProperties` and gap knobs from `AwsSqsProperties`:

```java
@Slf4j
@Configuration
@EnableConfigurationProperties(AwsSqsProperties.class)
@RequiredArgsConstructor
public class SqsConfig {

  private final AwsSqsProperties properties;
  private final SqsProperties sqsProperties; // library-owned: listener.*, observation-enabled
  private final ObservationRegistry observationRegistry; // injected; Noop when actuator absent

  @Bean
  public AwsClientCustomizer<SqsAsyncClientBuilder> sqsApiTimeoutCustomizer() {
    return builder -> builder.overrideConfiguration(cfg ->
        cfg.apiCallTimeout(this.properties.apiCallTimeout())); // 1.5 s ticket requirement
  }

  @Bean
  public SqsMessageListenerContainerFactory<S3Event> sqsListenerContainerFactory(
      SqsAsyncClient sqsAsyncClient, // auto-configured by the starter
      TaskExecutor sqsListenerTaskExecutor,
      AcknowledgementResultCallback<S3Event> acknowledgementResultCallback) {
    return SqsMessageListenerContainerFactory.<S3Event>builder()
        .sqsAsyncClient(sqsAsyncClient)
        .configure(options -> options
            .acknowledgementMode(AcknowledgementMode.ON_SUCCESS)
            .acknowledgementOrdering(AcknowledgementOrdering.ORDERED)
            .acknowledgementInterval(this.properties.acknowledgementInterval())
            .acknowledgementThreshold(this.properties.acknowledgementThreshold())
            // library-owned knobs below — same values the auto-config maps onto
            // its default factory (SqsAutoConfiguration.configureProperties):
            .maxConcurrentMessages(this.sqsProperties.getListener().getMaxConcurrentMessages())
            .maxMessagesPerPoll(this.sqsProperties.getListener().getMaxMessagesPerPoll())
            .pollTimeout(this.sqsProperties.getListener().getPollTimeout())
            .maxDelayBetweenPolls(this.sqsProperties.getListener().getMaxDelayBetweenPolls())
            .autoStartup(this.sqsProperties.getListener().getAutoStartup())
            .backPressureHandler(new ThroughputBackPressureHandler())
            .acknowledgementResultCallback(acknowledgementResultCallback)
            .taskExecutor(sqsListenerTaskExecutor)
            .messageConverter(new JacksonJsonMessageConverter()))
        .observationRegistry(Boolean.TRUE.equals(this.sqsProperties.isObservationEnabled())
            ? this.observationRegistry : ObservationRegistry.NOOP)
        .build();
  }

  @Bean
  public SqsMessageListenerContainer<S3Event> assetEventsContainer(
      SqsMessageListenerContainerFactory<S3Event> factory,
      AssetEventsListener listener) {
    // Always created; library auto-startup flag governs lifecycle (false in
    // tests => created-but-stopped, started manually). No @ConditionalOnProperty.
    SqsMessageListenerContainer<S3Event> container = factory.createContainer("asset-events");
    container.setQueueNames(this.properties.assetsEventsQueue());
    container.setMessageListener(listener::onAssetEvent);
    return container; // lifecycle managed by Spring (start/stop with context)
  }
}
```

Adjust exact builder method names to the `4.1.0` API at implementation time
(`SqsContainerOptionsBuilder` naming moved slightly between 3.x and 4.x) —
the **option set above is the contract**, the fluent spelling is not. The
`String` queue name is the logical name (`develop-assets-events-queue`);
Spring Cloud AWS resolves the URL via `GetQueueUrl` (works against LocalStack).

### 5.5 `SqsAcknowledgementLoggingCallback`

```java
@Slf4j
@Component
public class SqsAcknowledgementLoggingCallback implements AcknowledgementResultCallback<S3Event> {

  @Override
  public void onSuccess(Collection<Message<S3Event>> messages) {
    log.debug("Acknowledged {} asset-event message(s)", messages.size());
  }

  @Override
  public void onFailure(Collection<Message<S3Event>> messages, Throwable throwable) {
    log.error("Failed to acknowledge {} asset-event message(s): {}",
        messages.size(), throwable.toString());
  }
}
```

Rules: **never log payloads, keys, URLs, or exception messages containing
key material** — counts and exception type only. Generic type must match the
container (`<S3Event>`); a raw-type callback compiles but never fires.

### 5.6 Virtual-threads `TaskExecutor`

```java
@Bean
public TaskExecutor sqsListenerTaskExecutor() {
  return Executors.newVirtualThreadPerTaskExecutor(); // Java 21; aligns with spring.threads.virtual.enabled=true
}
```

`TaskExecutor` adapts `ExecutorService` via `TaskExecutorAdapter` on Boot 4 —
check the factory's expected type (`TaskExecutor` vs `Executor`) in `4.1.0`
and adapt without introducing a thread-pool bean (no `ThreadPoolTaskExecutor`
here: virtual threads are uncapped by design; concurrency is governed by
`maxConcurrentMessages`, not pool size).

### 5.7 `AssetEventsListener` (seam for KAN-13)

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class AssetEventsListener {

  public void onAssetEvent(S3Event event) {
    // Seam only: prove typing + conversion. KAN-13 replaces the body with
    // BlobUploadEvent derivation + BrandImageConfirmService.confirmUploaded.
    log.debug("Received asset event with {} record(s)", event.getRecords().size());
  }
}
```

`S3Event.getRecords()` → `S3EventNotificationRecord` →
`record.getS3().getBucket().getName()` /
`record.getS3().getObject().getKey()` (URL-encoded — decode downstream, never
trust a sender-supplied blob-type field). A `JacksonJsonMessageConverter`
maps the S3→SQS JSON body to `S3Event`; a poison body must surface as a
listener exception (→ `ON_SUCCESS` withholds the ack → redrive → DLQ after
`maxReceiveCount: 5`), never silently swallow.

---

## 6. Testing plan (TDD — failing test first)

Naming: `should_[expected]_when_[condition]`. Structure: Arrange/Act/Assert.
Coverage ≥ 90% on all new classes.

### 6.1 Properties — gap record + library binding (no network)

Via `ApplicationContextRunner`:

1. `should_bindGapKnobs_when_ymlBlockIsPresent` (queue + ack interval/threshold + api timeout)
2. `should_rejectInvalidThreshold_when_ackThresholdIsNotPositive` (validation fires)
3. `should_readListenerTuningFromLibrary_when_springCloudKeysAreSet` (assert
   `SqsProperties.getListener()` concurrency/poll/timeouts/autoStartup bind
   from `spring.cloud.aws.sqs.listener.*`; guards the rev-3 migration)
4. `should_leaveCredentialsUnset_when_noStaticKeysAreConfigured` (default chain
   contract — guards against accidentally committing test keys)
5. `should_defaultObservationDisabled_when_libraryFlagIsAbsent` (library default
   is `false`; our YAML must opt in explicitly — guards silent loss of
   observability)

### 6.2 Config — `SqsConfigTests` (no network, no Spring context)

Instantiate `SqsConfig` with a hand-built gap record + hand-built library
`SqsProperties` (listener tuning + observation flag) + `NOOP` registry (the
`SqsAsyncClient` itself is auto-configured and is **not** built here):

1. `should_applyApiCallTimeout_when_customizerRuns` (apply
   `sqsApiTimeoutCustomizer` to a builder; assert
   `overrideConfiguration().apiCallTimeout()` is `PT1.5S`)
2. `should_buildFactoryWithOrderedOnSuccess_when_propertiesAreValid` (inspect
   `SqsContainerOptions`: mode `ON_SUCCESS`, ordering `ORDERED`, gap
   interval `PT3S`/threshold `10`, listener concurrency/poll/timeouts from
   `SqsProperties`)
3. `should_wireBackpressureAndCallbackAndExecutor_when_factoryIsBuilt`
   (`ThroughputBackPressureHandler`, logging callback, virtual-thread executor
   non-null)
4. `should_useNoopRegistry_when_libraryObservationIsDisabled` (both branches of
   the `isObservationEnabled` ternary — kills mutants and guards the library
   default-`false` trap)

Migration coverage (existing suites, updated): `S3Config` override both-ways
tests now run against `spring.cloud.aws.s3.endpoint` (set vs absent) instead
of `aws.s3.endpoint`; empty-string → `null` binding contract moves to the
library keys.

### 6.3 Messaging unit — callback / converter / listener (no network)

1. `should_logWithoutPayload_when_ackSucceeds` (callback completes, no throw)
2. `should_logErrorWithoutPayload_when_ackFails`
3. `should_deserialiseS3Event_when_bodyIsS3NotificationJson`
   (`JacksonJsonMessageConverter` + fixture `s3-notification.json` with
   `brands%2Fimages%2F<32-hex>` key; assert bucket + encoded key)
4. `should_completeWithoutThrow_when_listenerReceivesEvent` (seam listener)

### 6.4 Integration — `SqsContainerIntegrationTests` (LocalStack SQS + S3)

`@SpringBootTest` with `spring.cloud.aws.sqs.listener.auto-startup: false`
(container created-but-stopped, started manually), `AWS_*=test` env creds.
Mirrors the `EndpointIntegrationTest` pattern; surefire excludes
`integration/**`, failsafe includes it:

1. `should_injectAsyncClientsAndContainer_when_contextStarts`
2. `should_deliverS3EventToListener_when_objectIsUploaded` — `putObject` to
   `develop-assets/brands/images/<32-hex>` (or rely on bucket notification),
   poll with Awaitility until the test listener latch fires; assert the
   received `S3Event` bucket + decoded key.
3. `should_startEmpty_when_queueHasNoMessages` (container starts, no poison).

### 6.5 Regression

`EndpointIntegrationTest` and every suite extending it stays green; `mvn verify`
green with LocalStack up (surefire + failsafe + jacoco ≥ 90% on new classes).

---

## 7. Definition of Done

1. BOM + 3 dependencies added and pinned; `mvn verify` run **before** code to
   flush out `duplicate-finder` conflicts.
2. `AwsSqsProperties` (4-key gap record) + `SqsConfig` (customizer + factory +
   container reading `SqsProperties`) + callback + executor + seam listener created; `AwsS3Properties`
   shrunk and `S3Config` migrated to `spring.cloud.aws.*`; no manual
   `S3AsyncClient`/`SqsAsyncClient` builders; `mvn spotless:apply` clean.
3. `spring.cloud.aws.*` + slim `aws.*` (4 keys) blocks in both `application.yml`
   files; test block uses global `endpoint: http://localhost:4566`,
   `auto-startup: false` + fast batching.
4. Ack mode `ON_SUCCESS` + ordering `ORDERED` + gap interval `3 s` + threshold
   `10` proven by tests; endpoint override proven both ways **through the
   library keys** (service vs absent, global fallback); listener tuning proven
   sourced from `SqsProperties`, not duplicated.
5. `SqsAsyncClient` 1.5 s API timeout proven via the customizer test (6.2.1).
6. Observability proven: container built with the registry when the **library**
   `spring.cloud.aws.sqs.observation-enabled` is `true`, `NOOP` when `false`
   (test both branches — library default is `false`).
7. LocalStack round trip green (`putObject` → `S3Event` in listener).
8. App boots with LocalStack **stopped** (no fail-fast on bean creation).
9. No credentials/keys/endpoints hardcoded in `src/main`; no payload/URL/key
   in any log (grep-verified).
10. `README.md` + `docs/backend-standards.md` updated (Messaging/SQS
    convention: container, `S3Event`, ordering, observability, LocalStack).
11. Branch `feat/KAN-16-spring-cloud-aws`; conventional commits; code review
    before merge.

---

## 8. Non-functional requirements & risks

### 8.1 Security

- Credentials come from the SDK default chain because
  `spring.cloud.aws.credentials.*` is **left unset**; production resolves IAM
  role/IRSA. Never commit static/test keys, never `StaticCredentialsProvider`
  in `src/main`.
- Never log message bodies, keys, presigned URLs, queue URLs with account IDs,
  or `ex.getMessage()` content that embeds key material.
- SQS payload is **untrusted**: the seam listener does no DB access; KAN-13
  must validate every key through `BlobType` (prefix match) before any lookup.
- Endpoints come from `spring.cloud.aws.*`/env only, never from a request.
- The SQS queue policy currently allows `AWS: "*"` (`localstack-resources.yml`
  `SQSQueuePolicy`) — acceptable locally, **must be scoped before production**
  (separate infra ticket, already flagged in KAN-13).

### 8.2 Performance

- Both async clients are **singletons** (thread-safe, pooled Netty event loop);
  per-request construction is a severe anti-pattern.
- `pollTimeout: 20 s` long-poll keeps empty-queue cost near zero; virtual
  threads make `maxConcurrentMessages: 10` cheap (no platform-thread sizing
  math). Throughput backpressure adapts poll rate instead of fixed-delay
  hammering (compare KAN-13's `PT5S` fixed delay).
- Batch ack (`3 s` / `10` msgs) bounds `DeleteMessage` calls to ~1 per batch.
- `apiCallTimeout: 1.5 s` bounds a wedged SQS call; redrive + DLQ absorb the
  failure (queue `VisibilityTimeout: 300 s`, `maxReceiveCount: 5` already
  provisioned).

### 8.3 Build-gate risks (this pom is strict)

- **`duplicate-finder`** is the likeliest breakage (Spring Cloud AWS brings
  Jackson/Netty/Micrometer artefacts overlapping the SDK). Justify any new
  `<ignoredDependencies>` entry in the PR; never widen silently.
- **Boot 4.1 API drift**: `SqsMessageListenerContainerFactory` builder and
  `SqsContainerOptions` setters moved between 3.x → 4.x. The option *set* in
  §5.4 is normative; adapt fluent spelling to `4.1.0` javadoc.
- `pitest` targets the customizer lambda and the `observationEnabled` ternary
  (both branches covered by 6.2.1 + factory tests); the deleted manual
  `hasEndpointOverride()` branches disappear with the migration.
- `spotless` (google-java-format + license header) runs at `validate`.
- **Auto-config clash risk**: if a manual `S3AsyncClient`/`SqsAsyncClient`
  `@Bean` survives next to the starters, the context may wire the wrong bean
  or fail on duplicates. The DoD requires zero manual client builders — grep
  for `S3AsyncClient.builder()` / `SqsAsyncClient.builder()` outside tests.

### 8.4 Developer experience

- One env var covers both services out of the box
  (`AWS_ENDPOINT_URL_SQS`/`AWS_ENDPOINT_URL_S3` fall back to global
  `AWS_ENDPOINT_URL`), so existing `docker compose` + `EndpointIntegrationTest`
  muscle memory keeps working with **fewer** variables than before.
- **Resolved (was open): region/endpoint ownership.** `AwsSqsProperties` carries
  no region/endpoint; `SqsConfig` carries no `@Value("${aws.region}")`. The
  single source of truth is `spring.cloud.aws.*` (§3.1) — no DRY violation to
  decide at implementation time.

### 8.5 Observability

- Container observation is gated by the **library**
  `spring.cloud.aws.sqs.observation-enabled` (default `false` — our YAML opts
  in with `true`); the custom factory mirrors the auto-config gating
  (`SqsAutoConfiguration`: registry wired only when the flag is `true`).
  `management.endpoints.web.exposure.include` already covers `metrics`/`health`.
  Add listener timers/traces to dashboards in the follow-up that owns them
  (KAN-13); this ticket proves the registry is wired, not the dashboard.

---

## 9. Critical assumptions to validate

1. **KAN-13 is superseded by this ticket.** KAN-13 §8.1 assumed *raw
   `SqsClient` + `@Scheduled` poller instead of Spring Cloud AWS*. KAN-16
   reverses that: the poller must **not** be implemented; KAN-13's
   `BrandImageConfirmService` + `BlobUploadEvent` design is unchanged, but its
   `S3EventParser`/`SqsAssetEventsPoller` sections are replaced by the
   container + `AssetEventsListener` seam. Confirm with the team and update
   KAN-13's refinement note.
2. **Ordering cost.** `ORDERED` acknowledgement serialises ack batches per
   queue partition for correctness (at-least-once + idempotent consumer
   assumed). If throughput ever needs `PARALLEL`, it is a one-line option
   change — confirm `ORDERED` is deliberate, not copy-paste.
3. **Batching defaults.** `3 s` / `10` msgs bound redelivery latency after a
   crash to ~3 s. Confirm acceptable for "image becomes visible" UX.
4. **No `SqsTemplate` in this slice.** Producers are not needed (S3 fan-out
   produces); if tests need to send messages, use `SqsAsyncClient` directly —
   do not add `SqsTemplate` speculatively.
5. **Mode/ordering hardcoded.** Per ticket text they are fixed enums; confirm
   the team does not need them as properties in this slice.
6. **`develop-assets` vs `develop-assets-events-queue` naming.** Queue name
   default matches `localstack-resources.yml` (`develop-` + `assets-events-queue`);
   bucket default `develop-assets` matches `AssetsS3Bucket`. Confirm no rename
   is planned before merging (a rename breaks both blocks + the CF template).
7. **Single region/endpoint.** One region and one endpoint for all SQS access;
   multi-account needs a client factory, not a singleton.
8. **Library-first migration is accepted.** The team agrees `aws.region`,
   `aws.s3.endpoint`, `aws.s3.path-style-access`, `aws.sqs.endpoint`,
   `aws.sqs.max-concurrent-messages`, `aws.sqs.max-messages-per-poll`,
   `aws.sqs.poll-timeout`, `aws.sqs.max-delay-between-polls`,
   `aws.sqs.polling-enabled` and `aws.sqs.observation-enabled` are deleted as
   sources of truth (env vars stay mapped in YAML, §3.3), and that
   `S3Config`/`AwsS3Properties` are refactored inside this ticket rather than
   in a separate migration story. If the team prefers a strangler approach
   (aliases first, deletion later), record it here before implementation —
   the file as written does the clean cut.
9. **Ack-batching has no library property (verified).** `SqsProperties.Listener`
   models only concurrency/poll/backoff/auto-startup; acknowledgement interval
   and threshold live in `SqsContainerOptions` code-only, so the 4-key
   `aws.sqs.*` gap record is not duplicative — it will survive future library
   upgrades unless the starters add these fields.

---

## 10. Out of scope (explicit follow-ups)

| Follow-up | Rationale |
|-----------|-----------|
| KAN-13 rework onto the container (`confirmUploaded` in the listener) | Consumer logic, not foundation |
| `SqsTemplate` producer bean | No producer use case in this slice |
| SQS Actuator health indicator | Needs readiness/liveness policy (same argument as KAN-14 §7.5) |
| DLQ CloudWatch alarm on poison count | Observability follow-up |
| Production bucket notification rule + queue-policy scoping | Infra tickets (local CF template is local-only) |
| Strain/product confirm consumers | One story each, reusing this container pattern |
| PENDING orphan sweeper | Now unblocked; needs retention sign-off |

---

## 11. Success metrics

| Metric | Target |
|--------|--------|
| Coverage of new `config` + `messaging/sqs` classes | ≥ 90% lines/branches; 0 surviving pitest mutants |
| `putObject` → `S3Event` in listener (LocalStack) | Green and deterministic |
| Hardcoded credentials / endpoints in `src/main` | 0 |
| Tuning knobs hardcoded in `@Bean` methods | 0 (all from properties) |
| AWS/Spring-Cloud imports outside `infrastructure/config`, `infrastructure/messaging/sqs`, storage adapter | 0 |
| Startup failures with SQS unreachable | 0 |
| Follow-up stories unblocked | ≥ 2 (KAN-13 rework, strain/product consumers) |

---

## 12. Suggested task breakdown (baby steps, TDD)

1. Add BOM + 3 dependencies; run `mvn verify` **first** (duplicate-finder risk up front).
2. Create slim 4-key `AwsSqsProperties`; gap binding + validation tests (6.1). *(red → green)*
3. Migrate `AwsS3Properties` + `S3Config` to `spring.cloud.aws.*` (Option A/B in §5.3); update existing override tests to the library keys.
4. Add `SqsAsyncClient` timeout customizer; customizer test (6.2.1).
5. Add logging callback + virtual-thread executor; unit tests (6.3.1–6.3.2).
6. Add factory reading listener tuning + observation from `SqsProperties` (§5.4); options tests incl. both observation branches (6.2.2–6.2.4).
7. Add `JacksonJsonMessageConverter` + `AssetEventsListener` seam; converter test with fixture (6.3.3–6.3.4).
8. Wire container bean with `@ConditionalOnProperty`; `application.yml` (main §3.3 + test §3.4).
9. Integration round trip against LocalStack (6.4); fix the `wait-for-it` port if still `5432`.
10. Docs (`README`, `backend-standards` Messaging/SQS convention) + OpenSpec spec + review.

---

## 13. Proposed OpenSpec requirement (`openspec/specs/spring-cloud-aws-foundation/spec.md`)

### Requirement: Provide Spring Cloud AWS async messaging foundation

The system SHALL expose the starter auto-configured singleton `S3AsyncClient`
bean and singleton `SqsAsyncClient` bean, both configured from
`spring.cloud.aws.region.static` with endpoint overrides from
`spring.cloud.aws.s3.endpoint` / `spring.cloud.aws.sqs.endpoint` (falling back
to `spring.cloud.aws.endpoint`), credentials from the SDK default chain, and
the SQS client SHALL enforce a 1.5-second API call timeout via an
`AwsClientCustomizer`. The system SHALL expose
a singleton `SqsMessageListenerContainer<S3Event>` listening to the configured
assets-events queue with `ON_SUCCESS` acknowledgement, `ORDERED` ordering, and
interval/threshold/concurrency/polling all sourced from `aws.sqs.*` properties.
The container SHALL use a logging acknowledgement callback, a throughput
backpressure handler, a virtual-threads task executor, and an enabled Micrometer
observation registry. The container SHALL source concurrency, poll size, poll
timeout, backoff, auto-startup and the observation toggle from the library
`spring.cloud.aws.sqs.listener.*` / `spring.cloud.aws.sqs.observation-enabled`
(via injected `SqsProperties`) and ack-batching, queue name and client timeout
from the 4-key `aws.sqs.*` gap record. Bean creation SHALL NOT perform network calls.

#### Scenario: Async clients available for injection

- **WHEN** the application context starts
- **THEN** exactly one `S3AsyncClient` bean AND exactly one `SqsAsyncClient` bean SHALL be available for constructor injection

#### Scenario: Endpoint overrides applied for local testing

- **WHEN** `spring.cloud.aws.s3.endpoint` / `spring.cloud.aws.sqs.endpoint` (or global `spring.cloud.aws.endpoint`) are set to non-empty URIs
- **THEN** the corresponding async client SHALL target that endpoint

#### Scenario: Default AWS endpoints in production

- **WHEN** the endpoint properties are absent or empty
- **THEN** the clients SHALL resolve the standard AWS endpoints for the configured region

#### Scenario: Ordered batch acknowledgement of asset events

- **WHEN** `S3Event` messages arrive on the assets-events queue
- **THEN** the container SHALL acknowledge them in `ON_SUCCESS` + `ORDERED` mode with the configured interval and threshold

#### Scenario: Tuning fully externalised

- **WHEN** an operator sets any `spring.cloud.aws.sqs.listener.*`, `spring.cloud.aws.sqs.observation-enabled` or gap `aws.sqs.*` property
- **THEN** the container SHALL honour it without a code change

#### Scenario: Startup without a reachable endpoint

- **WHEN** the application starts while SQS/S3 endpoints are unreachable
- **THEN** the context SHALL start successfully AND failures SHALL surface only on first use / poll retry
