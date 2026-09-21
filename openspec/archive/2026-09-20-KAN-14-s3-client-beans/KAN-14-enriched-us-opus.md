# KAN-14 — Enriched User Story: Provide `S3Client` and `S3Presigner` beans (AWS SDK v2)

> **Placeholder key**: `KAN-14`. No Jira MCP was connected in this session, so this story was produced
> locally (same as `KAN-8`). Replace the key if needed and paste the content into the ticket under the
> `[enhanced]` section, keeping the original text under `[original]`.

## Executive Summary

Add the **AWS S3 foundation** to the application: two Spring-managed singleton beans, `S3Client` (sync)
and `S3Presigner`, built with **AWS SDK v2**, configured through typed properties so the endpoint can be
pointed at **LocalStack** for local development and integration tests.

This is pure **infrastructure enablement**, not a feature. Nothing in `src/main/java` touches AWS today
(`grep -i aws src/main` → 0 hits, no `software.amazon.awssdk` dependency in `pom.xml`), yet the
surrounding infrastructure is already provisioned and waiting:

- `localstack-resources.yml` already creates the `develop-assets` S3 bucket (public-read, CORS `GET`/`PUT`,
  `s3:ObjectCreated:*` → `develop-products-assets-events-queue` SQS notification on the `products/` prefix).
- `docker-compose.yml` already runs `localstack/localstack:4.3.0` on `127.0.0.1:4566` with a CloudFormation
  bootstrap container.
- `EndpointIntegrationTest` already carries **commented-out** `@SetEnvironmentVariable` lines for
  `AWS_ENDPOINT`, `AWS_REGION`, `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`.
- `maven-failsafe-plugin` already declares the `--add-opens java.base/java.util=ALL-UNNAMED` argLine
  *"to allow to override env vars using JUnit pioneer extension"*, and `junit-pioneer` 2.2.0 is a test dependency.

In other words: **the runway was built for this story and never used.** This ticket closes that gap so
the next story (asset upload / presigned URL issuance for product images) can simply `@Autowired` the beans.

**Scope decision (confirmed with the requester): beans + configuration only.** No domain port, no storage
adapter, no health indicator, no controller. Those are explicit follow-ups (§9).

---

## 1. User story

> *As a **backend developer**,
> I want **`S3Client` and `S3Presigner` beans available in the Spring context, with an overridable AWS
> endpoint**,
> so that **I can inject them into any bean and run the whole suite against LocalStack without touching
> real AWS or changing code**.*

### Business value

Product images are the largest missing slice of the catalog domain: `docs/data-model.md` models
`product_images`, and `KAN-8` explicitly deferred it ("`product_images`, `product_uses` and
`product_available_states` are out of scope and become follow-up stories"). Every one of those stories is
blocked on object storage. Additionally, a presigner enables **direct browser-to-S3 uploads**, which keeps
large binaries off the JVM heap and out of the request path — a decision that is expensive to retrofit later.

Shipping this as a standalone, well-tested slice means the follow-up feature stories carry **zero
infrastructure risk**.

### Acceptance criteria

1. The Spring context exposes exactly one `S3Client` bean and one `S3Presigner` bean; a bean can declare
   `S3Client`/`S3Presigner` as a constructor dependency and the application starts.
2. When `aws.s3.endpoint` is **empty/absent**, both clients are built **without** `endpointOverride`, i.e.
   they resolve the real AWS endpoint for the configured region.
3. When `aws.s3.endpoint` is **set**, both clients apply `endpointOverride(...)` with that URI.
4. `aws.s3.path-style-access=true` forces path-style addressing on both clients (required by LocalStack,
   because virtual-host style would resolve `develop-assets.localhost`).
5. The region comes from `aws.region` and defaults to `us-east-1`, matching `docker-compose.yml` and
   `docker-compose-localstack-init.sh`.
6. Credentials are resolved by `DefaultCredentialsProvider` — **no credentials are hardcoded** anywhere in
   `src/main`.
7. An integration test performs a real round trip against LocalStack (`putObject` → `getObject`) and
   generates a presigned `GET` URL whose host matches the overridden endpoint.
8. The application still starts when LocalStack/AWS is unreachable — bean creation MUST NOT perform any
   network call (fail-lazy, not fail-fast). See §8.2.

---

## 2. Configuration contract

### 2.1 Properties

| Property | Type | Required | Default | Purpose |
|----------|------|----------|---------|---------|
| `aws.region` | `String` | ✅ | `us-east-1` | Region for both clients |
| `aws.s3.endpoint` | `URI` | ❌ | *(empty)* | Endpoint override. **Empty = real AWS.** |
| `aws.s3.path-style-access` | `boolean` | ❌ | `false` | Force path-style addressing (LocalStack needs `true`) |
| `aws.s3.bucket` | `String` | ❌ | `develop-assets` | Default assets bucket; consumed by follow-up stories |
| `aws.s3.presign-ttl` | `Duration` | ❌ | `PT15M` | Default presigned-URL lifetime; consumed by follow-up stories |

> `aws.s3.bucket` and `aws.s3.presign-ttl` have **no consumer in this story** — they are declared now so the
> config surface is stable. If the team prefers zero unused config, drop them and add them with their first
> consumer. Flagged in §8.1.

### 2.2 `src/main/resources/application.yml`

```yaml
aws:
  region: ${AWS_REGION:us-east-1}
  s3:
    endpoint: ${AWS_ENDPOINT_URL_S3:}        # empty => real AWS
    path-style-access: ${AWS_S3_PATH_STYLE_ACCESS:false}
    bucket: ${AWS_S3_BUCKET:develop-assets}
    presign-ttl: ${AWS_S3_PRESIGN_TTL:PT15M}
```

`AWS_ENDPOINT_URL_S3` is deliberately reused as the env-var name: it is the **same variable AWS SDK v2 and
the AWS CLI already honour natively**, so operators and the `localstack-resources` container share one
mental model.

### 2.3 `src/test/resources/application.yml` (append)

```yaml
aws:
  region: us-east-1
  s3:
    endpoint: http://localhost:4566
    path-style-access: true
    bucket: develop-assets
    presign-ttl: PT15M
```

Credentials for tests are supplied as **environment variables** so `DefaultCredentialsProvider` resolves
them — exactly what the commented block in `EndpointIntegrationTest` anticipated:

```java
@SetEnvironmentVariable(key = "AWS_ACCESS_KEY_ID", value = "test")
@SetEnvironmentVariable(key = "AWS_SECRET_ACCESS_KEY", value = "test")
```

---

## 3. Files to create / modify

| # | File | Action | Notes |
|---|------|--------|-------|
| 1 | `pom.xml` | **modify** | Add `<dependencyManagement>` with the AWS SDK BOM + `s3` dependency (§4.1) |
| 2 | `src/main/java/com/example/demo/infrastructure/config/AwsS3Properties.java` | **create** | `@ConfigurationProperties("aws")` record |
| 3 | `src/main/java/com/example/demo/infrastructure/config/S3Config.java` | **create** | `@Configuration` exposing both beans |
| 4 | `src/main/resources/application.yml` | **modify** | Add the `aws:` block (§2.2) |
| 5 | `src/test/resources/application.yml` | **modify** | Add the LocalStack `aws:` block (§2.3) |
| 6 | `src/test/java/com/example/demo/infrastructure/config/S3ConfigTests.java` | **create** | Unit tests, no network (§5.1) |
| 7 | `src/test/java/com/example/demo/integration/config/S3ConfigIntegrationTests.java` | **create** | Real LocalStack round trip (§5.2) |
| 8 | `src/test/java/com/example/demo/integration/endpoints/EndpointIntegrationTest.java` | **modify** | Un-comment / replace the AWS env-var block (§2.3) |
| 9 | `docker-compose.yml` | **modify** | Fix the `wait-for-it` port bug (§7.4) |
| 10 | `README.md` | **modify** | Document the `aws.*` properties and the LocalStack workflow |
| 11 | `docs/backend-standards.md` | **modify** | Add S3/AWS SDK v2 to the Technology Stack section |
| 12 | `spotbugs-exclude.xml` | **modify (only if needed)** | See §7.3 |

Package placement follows `docs/backend-standards.md`: SpringBoot setup lives in
`infrastructure/config/`, alongside `JpaConfig`, `JsonConfig` and `OpenApiConfig`.

---

## 4. Implementation details

### 4.1 `pom.xml`

The project has **no `<dependencyManagement>` section today** — it must be created:

```xml
<properties>
    ...
    <aws-sdk.version>2.46.7</aws-sdk.version>
</properties>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>software.amazon.awssdk</groupId>
            <artifactId>bom</artifactId>
            <version>${aws-sdk.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

```xml
<!-- AWS S3 (SDK v2) -->
<dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>s3</artifactId>
</dependency>
```

`software.amazon.awssdk:s3` transitively provides `S3Presigner` (`software.amazon.awssdk.services.s3.presigner`)
and the Apache-based sync HTTP client — **no extra artifact is required**.

> **Version note**: `2.46.7` is the latest BOM on Maven Central as of this refinement. Pin it in
> `<properties>` like every other version in this pom; never inherit a floating version.

### 4.2 `AwsS3Properties`

```java
@ConfigurationProperties(prefix = "aws")
@Validated
public record AwsS3Properties(@NotBlank String region, S3 s3) {

  public record S3(
      URI endpoint,
      boolean pathStyleAccess,
      String bucket,
      @NotNull Duration presignTtl) {}

  public boolean hasEndpointOverride() {
    return this.s3 != null && this.s3.endpoint() != null;
  }
}
```

- A **record** keeps it immutable and sidesteps the SpotBugs `EI_EXPOSE_REP` family (`URI`, `Duration`,
  `String` are all immutable).
- `spring-boot-configuration-processor` is **already** wired in the `maven-compiler-plugin`
  `annotationProcessorPaths`, so IDE auto-completion for `aws.*` comes for free.
- Register with `@EnableConfigurationProperties(AwsS3Properties.class)` on `S3Config` (preferred over
  `@ConfigurationPropertiesScan`, which would be a broader, unrelated change).

**Empty-string trap**: `AWS_ENDPOINT_URL_S3:` resolves to `""`, and Spring binds `""` to a `URI` as
**`null`**, not as an empty `URI`. That is the behaviour this design relies on — assert it explicitly in a
test (§5.1.5) rather than trusting it.

### 4.3 `S3Config`

```java
@Slf4j
@Configuration
@EnableConfigurationProperties(AwsS3Properties.class)
@RequiredArgsConstructor
public class S3Config {

  private final AwsS3Properties properties;

  @Bean
  public S3Client s3Client() {
    S3ClientBuilder builder =
        S3Client.builder()
            .region(Region.of(this.properties.region()))
            .credentialsProvider(DefaultCredentialsProvider.create());
    if (this.properties.hasEndpointOverride()) {
      log.info("Overriding S3 endpoint with {}", this.properties.s3().endpoint());
      builder
          .endpointOverride(this.properties.s3().endpoint())
          .forcePathStyle(this.properties.s3().pathStyleAccess());
    }
    return builder.build();
  }

  @Bean
  public S3Presigner s3Presigner() {
    S3Presigner.Builder builder =
        S3Presigner.builder()
            .region(Region.of(this.properties.region()))
            .credentialsProvider(DefaultCredentialsProvider.create());
    if (this.properties.hasEndpointOverride()) {
      builder
          .endpointOverride(this.properties.s3().endpoint())
          .serviceConfiguration(
              S3Configuration.builder()
                  .pathStyleAccessEnabled(this.properties.s3().pathStyleAccess())
                  .build());
    }
    return builder.build();
  }
}
```

**Non-obvious details the implementer must not miss:**

1. **`S3Presigner` has no `forcePathStyle(...)` method.** Path-style on the presigner is configured via
   `S3Configuration.builder().pathStyleAccessEnabled(true)` passed to `.serviceConfiguration(...)`. This
   asymmetry with `S3Client` is the single most common mistake in this integration and produces presigned
   URLs pointing at `http://develop-assets.localhost:4566/...`, which will not resolve.
2. **`DefaultCredentialsProvider.create()` is intentional, per instance.** Do not hardcode
   `StaticCredentialsProvider` with `test`/`test` — that would put fake credentials in production code and
   break IAM-role-based deployment (IRSA / instance profile). LocalStack accepts any credentials, and the
   test env vars supply them.
3. **Both beans implement `SdkAutoCloseable` (→ `AutoCloseable`).** Spring infers `close()` as the destroy
   method automatically, so shutdown is handled. Do **not** set `destroyMethod = ""`.
4. **Bean creation must stay offline.** `S3Client.builder().build()` does not contact AWS; keep it that way
   so the app boots even when LocalStack is down (AC #8).
5. **`Region.of(...)` over `Region.US_EAST_1`** so the value is genuinely driven by configuration.
6. `@Slf4j` per `docs/backend-standards.md`; log the override at `INFO` because "which endpoint am I hitting"
   is the number-one debugging question. **Never log credentials.**

---

## 5. Testing plan (TDD — write the failing test first)

Naming: `should_[expected_behavior]_when_[condition]`. Structure: Arrange/Act/Assert. Coverage ≥ 90%.

Recall the build split: **surefire excludes `com/example/demo/integration/**`**, **failsafe includes only
that package**. Placement therefore decides whether a test needs LocalStack running.

### 5.1 Unit tests — `infrastructure/config/S3ConfigTests.java` (no network, no Spring context)

Instantiate `S3Config` directly with a hand-built `AwsS3Properties`. Assert configuration via
`client.serviceClientConfiguration()`, which exposes `region()` and `endpointOverride()` without any call.

1. `should_buildS3Client_when_propertiesAreValid` — bean is non-null, `serviceClientConfiguration().region()` is `us-east-1`.
2. `should_applyEndpointOverride_when_endpointIsConfigured` — `serviceClientConfiguration().endpointOverride()` is present and equals the configured URI.
3. `should_notApplyEndpointOverride_when_endpointIsNull` — `endpointOverride()` is `Optional.empty()`. **Kills the `NEGATE_CONDITIONALS` mutant on the `if`.**
4. `should_buildS3Presigner_when_endpointIsConfigured` — presigner is non-null; a presigned `GetObjectRequest` URL host/port matches `localhost:4566` **and** the path starts with `/develop-assets/` (proving path-style). Presigning is a **local signing operation — no network call** — which makes this a genuine unit test.
5. `should_bindEndpointAsNull_when_propertyIsEmptyString` — via `ApplicationContextRunner` with
   `"aws.s3.endpoint="`; asserts the empty-string → `null` binding contract from §4.2.
6. `should_useConfiguredRegion_when_regionIsNotDefault` — e.g. `eu-west-1`.

### 5.2 Integration tests — `integration/config/S3ConfigIntegrationTests.java` (LocalStack required)

Extends/mirrors the `EndpointIntegrationTest` pattern; `@SpringBootTest` + `@SetEnvironmentVariable` creds.

1. `should_injectS3ClientAndPresigner_when_contextStarts` — both beans autowire successfully.
2. `should_listAssetsBucket_when_localstackIsRunning` — `listBuckets()` contains `develop-assets`, proving the CloudFormation bootstrap and the override agree.
3. `should_roundTripObject_when_putAndGetAreInvoked` — `putObject` to `products/kan-14-test.txt` then `getObject`; content matches. Clean up in `@AfterEach`.
4. `should_returnDownloadableUrl_when_objectIsPresigned` — presign a `GET`, fetch it over plain HTTP with no credentials, assert `200` and matching body. **This is the acceptance proof for the presigner**, since a misconfigured presigner still builds fine and only fails at URL-fetch time.

### 5.3 Context test

`EndpointIntegrationTest` (and therefore every endpoint suite extending it) must still start green — the
strongest regression signal that the new auto-configured beans do not destabilise the context.

---

## 6. Definition of Done

1. AWS SDK v2 BOM (`2.46.7`) + `s3` dependency added; `<dependencyManagement>` section introduced.
2. `AwsS3Properties` and `S3Config` created in `infrastructure/config/`, fully typed, `@Slf4j`, license header applied.
3. `aws:` blocks added to both `application.yml` files.
4. Endpoint override verified in both directions (set **and** unset) by tests.
5. `S3Presigner` path-style configured via `S3Configuration.serviceConfiguration` and proven by a real URL fetch.
6. `mvn verify` green with LocalStack up: surefire + failsafe + jacoco ≥ 90% on the new classes.
7. `mvn spotless:apply` clean; SpotBugs, modernizer, enforcer and **duplicate-finder** all pass (§7.3).
8. Application boots with LocalStack **stopped** (no fail-fast on bean creation).
9. No credentials, keys or endpoints hardcoded in `src/main`.
10. `README.md` + `docs/backend-standards.md` updated; `docker-compose.yml` `wait-for-it` port fixed.
11. Branch `feat/KAN-14-s3-client-beans-backend`; conventional commits; code review before merge.

---

## 7. Non-functional requirements

### 7.1 Security
- **No hardcoded credentials.** `DefaultCredentialsProvider` only; production resolves IAM role / IRSA.
- Do **not** log credentials, presigned URLs (they are bearer capabilities) or full ARNs at `INFO`.
- Presigned URLs MUST be short-lived; `PT15M` default, and consumers must never exceed 1 hour.
- The bucket is currently `AccessControl: PublicRead` with a `s3:GetObject` `Principal: "*"` policy and
  `AllowedOrigins: "*"` CORS. **Acceptable for LocalStack, unacceptable for production** — raise a separate
  infrastructure hardening ticket before any real deployment. A public-read bucket also makes presigned
  **GET** URLs pointless (though presigned **PUT** remains essential).
- `endpointOverride` is a redirection primitive: ensure the property cannot be set by untrusted input — it
  comes from `application.yml`/env only, never from a request.

### 7.2 Performance
- Both beans are **singletons**. `S3Client` is thread-safe and pools connections; creating one per request
  is a known severe anti-pattern (TLS handshake + credential resolution per call).
- Presigning is a **local HMAC operation**: no network, negligible latency.
- Expect ~12–18 MB of additional jar weight. If that matters, exclude `netty-nio-client` and use
  `url-connection-client` — optional, out of scope, note it in the PR.
- Region is resolved eagerly from config, avoiding the IMDS lookup that `DefaultAwsRegionProviderChain`
  would otherwise attempt (a classic multi-second startup stall outside EC2).

### 7.3 Build-gate risks (this project's pom is strict)
- **`duplicate-finder-maven-plugin` runs with `failBuildInCaseOfConflict=true` and
  `checkRuntimeClasspath=true`.** AWS SDK v2 modules are a well-known source of duplicate `META-INF`
  resources and overlapping classes. **This is the single most likely build breakage in this story.** If it
  trips, add the offending pair to `<ignoredDependencies>` / `<ignoredResourcePatterns>` — and justify it in
  the PR rather than silently widening the filter.
- `spotless` (google-java-format + license header) runs at `validate`: run `mvn spotless:apply` before committing.
- `pitest` runs on new code: the `if (hasEndpointOverride())` branch **requires both test 5.1.2 and 5.1.3**
  to survive mutation testing.
- SpotBugs may flag `EI_EXPOSE_REP2` on `S3Config`'s injected properties; the record is immutable, so prefer
  fixing the shape over adding an exclusion.

### 7.4 Developer experience (defect found during refinement)
`docker-compose.yml` waits on the **wrong port** before bootstrapping AWS resources:

```yaml
entrypoint: ["/mnt/wait-for-it.sh", "localstack:5432", "-t", "10", "--", "/mnt/docker-compose-localstack-init.sh"]
```

`5432` is PostgreSQL; the LocalStack gateway is `4566`. Because `wait-for-it.sh` is not `--strict`, it simply
times out after 10 s and runs the CloudFormation deploy anyway — so today it "works by accident" and is
**flaky on slow machines**, leaving the `develop-assets` bucket missing and §5.2 tests red for no obvious
reason. Change to `localstack:4566` as part of this story.

### 7.5 Observability
- `log.info("Overriding S3 endpoint with {}", endpoint)` at startup — makes "am I hitting AWS or LocalStack?"
  answerable from the first log line.
- Do not add an S3 Actuator health indicator here (deliberately out of scope): it would make `/health` depend
  on an external service and could flap readiness probes. Separate ticket, with `liveness`/`readiness` group
  placement decided explicitly.

---

## 8. Critical assumptions to validate

1. **Unused properties**: `aws.s3.bucket` and `aws.s3.presign-ttl` have no consumer in this story. Confirm the
   team wants the config surface staked out now, or drop them until their first consumer.
2. **Fail-lazy over fail-fast**: the app boots even if S3 is unreachable, and the first S3 call fails at
   runtime. The alternative (a startup `headBucket` probe) trades boot resilience for early detection.
   *Confirm with the requester — AC #8 assumes fail-lazy.*
3. **Sync client only**: `S3AsyncClient` / `S3TransferManager` are excluded. Large multipart uploads will need
   the async client later; adding it now is speculative.
4. **One region, one endpoint** for all S3 access. Multi-region or multi-account access needs a client factory,
   not a singleton.
5. **`us-east-1` default** is inferred from `docker-compose.yml` and the CLI init script. Confirm it matches
   the intended production region; a mismatch produces `PermanentRedirect` errors that are painful to debug.
6. **LocalStack over Testcontainers**: integration tests assume a developer-managed `docker compose up`, which
   is how the Postgres-backed tests already work. Consistent, but it does mean `mvn verify` is not
   self-contained. A `localstack` Testcontainers module would fix that — separate ticket.
7. **`AWS_ENDPOINT_URL_S3` naming**: reusing the SDK's own variable name is convenient but means a globally
   exported value silently reconfigures the app. Acceptable, and arguably desirable — confirm.

---

## 9. Out of scope (explicit follow-ups)

| Follow-up | Rationale |
|-----------|-----------|
| `AssetStorage` domain port + `S3AssetStorageAdapter` | Design the port against a real consumer, not in a vacuum |
| Product image upload endpoint (`product_images`) | The feature that motivates this story; deferred by `KAN-8` |
| SQS consumer for `develop-products-assets-events-queue` | The queue exists and nothing reads it |
| S3 Actuator health indicator | Needs a readiness/liveness policy decision (§7.5) |
| Bucket security hardening (drop public-read, scope CORS) | Blocker for production, not for local dev (§7.1) |
| LocalStack Testcontainers module | Makes `mvn verify` self-contained (§8.6) |

---

## 10. Proposed OpenSpec requirement (`openspec/specs/aws-s3-integration/spec.md`)

### Requirement: Provide configurable S3 client beans

The system SHALL expose a singleton `S3Client` bean and a singleton `S3Presigner` bean in the Spring
application context, both built with AWS SDK v2 and both configured from the `aws.region` and `aws.s3.*`
properties. The system SHALL apply an endpoint override to both beans when `aws.s3.endpoint` is configured,
and SHALL use the default AWS endpoint resolution when it is not. The system SHALL resolve credentials
exclusively through the default AWS credentials provider chain and SHALL NOT embed credentials in source
code. Bean creation SHALL NOT perform network calls.

#### Scenario: Beans available for injection
- **WHEN** the application context starts
- **THEN** the system SHALL provide exactly one `S3Client` bean **AND** exactly one `S3Presigner` bean available for constructor injection

#### Scenario: Endpoint override applied for local testing
- **WHEN** `aws.s3.endpoint` is set to a non-empty URI
- **THEN** both the `S3Client` and the `S3Presigner` SHALL target that endpoint **AND** SHALL use path-style addressing when `aws.s3.path-style-access` is `true`

#### Scenario: Default AWS endpoint in production
- **WHEN** `aws.s3.endpoint` is absent or empty
- **THEN** both clients SHALL resolve the standard AWS endpoint for the configured region **AND** no endpoint override SHALL be applied

#### Scenario: Presigned URL targets the overridden endpoint
- **WHEN** a presigned GET URL is generated while an endpoint override is active
- **THEN** the URL SHALL point at the overridden host **AND** SHALL grant credential-free read access to the object until it expires

#### Scenario: Startup without a reachable S3 endpoint
- **WHEN** the application starts while the configured S3 endpoint is unreachable
- **THEN** the context SHALL start successfully **AND** the failure SHALL surface only on the first S3 operation

---

## 11. Success metrics

| Metric | Target |
|--------|--------|
| Coverage of `S3Config` / `AwsS3Properties` | ≥ 90% branches/lines; 0 surviving pitest mutants |
| Code changes required to switch LocalStack ↔ AWS | **0** (configuration only) |
| Application startup overhead from the new beans | < 100 ms |
| Startup failures when S3 is unreachable | 0 |
| Hardcoded credentials in `src/main` | 0 |
| Follow-up stories unblocked | ≥ 3 (product images, SQS consumer, storage adapter) |

---

## 12. Suggested task breakdown (baby steps, TDD)

1. Add the AWS SDK BOM + `s3` dependency; run `mvn verify` **first** to flush out any duplicate-finder conflict before writing code. *(build-gate risk up front)*
2. Create `AwsS3Properties`; test binding, including the empty-string → `null` case (5.1.5). *(red → green)*
3. Create `S3Config.s3Client()` **without** the override branch; test region (5.1.1).
4. Add the override branch; tests 5.1.2 + 5.1.3. *(both mutants killed)*
5. Add `S3Presigner` with `S3Configuration` path-style; test 5.1.4.
6. Wire `application.yml` (main + test) and un-comment the AWS env vars in `EndpointIntegrationTest`.
7. Integration tests 5.2.1 → 5.2.4 against LocalStack.
8. Fix the `wait-for-it` port; update `README.md`, `docs/backend-standards.md` and the OpenSpec spec.
