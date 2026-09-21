## Why

Product-image work (`product_images`, deferred by KAN-8) is blocked on object storage, yet nothing in `src/main/java` touches AWS today while the surrounding runway (LocalStack container, `develop-assets` bucket bootstrap, commented-out AWS env vars in `EndpointIntegrationTest`) is already provisioned and unused. This change provides the reusable AWS S3 foundation — injectable `S3Client` and `S3Presigner` beans with a LocalStack-overridable endpoint — so follow-up feature stories carry zero infrastructure risk.

## What Changes

- Add AWS SDK v2 (`software.amazon.awssdk:bom` `2.46.7` via `<dependencyManagement>`, plus the `s3` artifact, which transitively provides `S3Presigner` and the sync HTTP client).
- Add typed configuration `AwsS3Properties` (`@ConfigurationProperties(prefix = "aws")` record: `region`, `s3.endpoint` as `URI`, `s3.path-style-access`, `s3.bucket`, `s3.presign-ttl`) with a `hasEndpointOverride()` guard.
- Add `S3Config` (`@Configuration`) exposing exactly one singleton `S3Client` bean and one singleton `S3Presigner` bean, both built with `Region.of(...)` + `DefaultCredentialsProvider` and an `endpointOverride` applied only when configured.
- Wire `aws:` blocks into `src/main/resources/application.yml` (env-driven, empty endpoint means real AWS) and `src/test/resources/application.yml` (LocalStack `http://localhost:4566`, path-style `true`).
- Fix the `docker-compose.yml` `wait-for-it` port bug (`localstack:5432` → `localstack:4566`).
- Document the `aws.*` properties and LocalStack workflow in `README.md` and register S3 / AWS SDK v2 in the `docs/backend-standards.md` Technology Stack section.

## Capabilities

### New Capabilities

- `aws-s3-integration`: configurable S3 client beans — singleton `S3Client` and `S3Presigner` built from `aws.region` / `aws.s3.*`, endpoint override when `aws.s3.endpoint` is set, default AWS resolution otherwise, credentials exclusively via the default provider chain, fail-lazy bean creation with no network calls at startup.

### Modified Capabilities

- None. No existing spec behavior changes.

## Impact

- **Code**: new `infrastructure/config/AwsS3Properties.java` and `infrastructure/config/S3Config.java`; no domain, application, or presentation layer changes.
- **Config**: `application.yml` (main + test); `EndpointIntegrationTest` AWS env-var block enabled with `test`/`test` credentials via `DefaultCredentialsProvider`.
- **Build**: new `<dependencyManagement>` BOM section in `pom.xml`; strict gates apply (`spotless`, SpotBugs, modernizer, enforcer, `duplicate-finder` with `failBuildInCaseOfConflict=true`, JaCoCo ≥ 90%, pitest).
- **Infra**: `docker-compose.yml` wait-for-it target corrected; no bucket policy, CORS, or queue changes.

## Decisions

- **AWS SDK v2 BOM `2.46.7` + `s3` artifact only** — no extra presigner/HTTP-client artifacts; version pinned in `<properties>`.
- **`AwsS3Properties` as a record with `hasEndpointOverride()`** — immutable (`URI`, `Duration`, `String` sidestep `EI_EXPOSE_REP`); registered via `@EnableConfigurationProperties` on `S3Config`, not a broad `@ConfigurationPropertiesScan`.
- **Path-style asymmetry handled explicitly** — `S3Client.forcePathStyle(...)` vs `S3Presigner` via `S3Configuration.builder().pathStyleAccessEnabled(...).build()` passed to `.serviceConfiguration(...)`; otherwise presigned URLs point at unresolvable virtual-host style (`develop-assets.localhost:4566`).
- **`DefaultCredentialsProvider` only, no hardcoded credentials** — production resolves IAM role/IRSA; tests supply `AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY=test` env vars.
- **Fail-lazy boot** — builders perform no network calls, so the app starts with LocalStack/AWS unreachable; failures surface on first S3 operation.
- **`AWS_ENDPOINT_URL_S3` env-var name reused** — same variable the SDK v2 and AWS CLI honour natively, shared with the `localstack-resources` bootstrap model.
- **Empty-string → `null` URI binding relied upon and tested** — `${AWS_ENDPOINT_URL_S3:}` binds as `null`, meaning real AWS; covered by an explicit binding test.
- **`bucket` and `presign-ttl` declared now with no consumer** — stabilises the config surface for follow-up stories (droppable if the team prefers zero unused config).
- **Docs updated in this change** — `README.md` (properties + LocalStack workflow) and `docs/backend-standards.md` (Technology Stack).

## Non-Goals

Explicitly out of scope (follow-up stories):

- `AssetStorage` domain port + `S3AssetStorageAdapter`.
- Product image upload endpoint (`product_images`).
- SQS consumer for `develop-products-assets-events-queue`.
- S3 Actuator health indicator (would couple `/health` to an external service without a readiness/liveness policy).
- Bucket security hardening (drop public-read, scope CORS) — production blocker, not local-dev work.
- LocalStack Testcontainers module — `mvn verify` stays developer-managed `docker compose up` consistent with Postgres-backed tests.
- `S3AsyncClient` / `S3TransferManager` — sync client only.

## Risks

- **Duplicate-finder conflict (`failBuildInCaseOfConflict=true`, `checkRuntimeClasspath=true`)** — top build risk; AWS SDK v2 modules are a known source of duplicate `META-INF`/overlapping classes. Mitigation: add the BOM first and run `mvn verify` before writing code; whitelist offending pairs in `<ignoredDependencies>`/`<ignoredResourcePatterns>` with PR justification.
- **Empty-string → `null` URI binding assumption** — if Spring ever binds `""` differently, the "empty means real AWS" contract breaks silently. Mitigation: explicit `ApplicationContextRunner` test asserting the binding.
- **Presigner path-style mistake** — `S3Presigner` has no `forcePathStyle`; using the wrong API builds fine but yields unresolvable presigned URLs, failing only at URL-fetch time. Mitigation: unit test asserting path prefix `/develop-assets/` plus an integration test fetching a presigned URL over plain HTTP.
- **Pitest `NEGATE_CONDITIONALS` on `if (hasEndpointOverride())`** — a single-direction test leaves a surviving mutant. Mitigation: tests for both override-set and override-absent directions.
