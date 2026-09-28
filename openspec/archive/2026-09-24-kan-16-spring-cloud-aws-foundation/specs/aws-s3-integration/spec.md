## MODIFIED Requirements

### Requirement: Provide configurable S3 client beans

The system SHALL source S3 client configuration from the Spring Cloud AWS library keys as the single source of truth: region from `spring.cloud.aws.region.static` (default `us-east-1`), S3 endpoint override from `spring.cloud.aws.s3.endpoint` with fallback to the global `spring.cloud.aws.endpoint` (empty means real AWS), and path-style access from `spring.cloud.aws.s3.path-style-access-enabled` (default `false`).

`AwsS3Properties` SHALL keep only the domain concerns the library does not model: `aws.s3.bucket` (default `develop-assets`) and `aws.s3.presign-ttl` (a `Duration` defaulting to `PT15M`). The legacy `aws.region` / `aws.s3.endpoint` / `aws.s3.path-style-access` keys SHALL be removed as sources of truth; any temporary `aws.*` alias, if kept, SHALL resolve from the `spring.cloud.aws.*` values, never the other way round.

`S3Config` SHALL delegate client construction to the Spring Cloud AWS starters (which auto-configure `S3Client`, `S3AsyncClient`, and `S3Presigner` from the library keys), or at minimum re-source its beans from the library keys. Application code SHALL NOT call `Region.of(...)` on a custom region property and SHALL NOT call `DefaultCredentialsProvider.create()`; credentials SHALL resolve exclusively through the SDK default chain with `spring.cloud.aws.credentials.*` left unset, and no credentials SHALL be hardcoded in `src/main`. Bean creation SHALL NOT perform network calls.

The endpoint-override and default-AWS-resolution behavior SHALL be preserved, now driven by the library keys: a non-empty S3 override (service-level or global fallback) SHALL be applied to both the `S3Client` and the `S3Presigner` (path-style addressing applied to both), while absent or empty overrides SHALL resolve the standard AWS endpoint for the configured region.

Existing consumption of these beans by the `blob-storage` capability SHALL keep working without behavior change. This requirement SHALL NOT introduce a controller, health indicator, SQS consumer, bucket hardening, Testcontainers module, or manual async client builder.

#### Scenario: S3 configuration sourced from library keys

- **WHEN** the application context starts
- **THEN** region SHALL come from `spring.cloud.aws.region.static`
- **AND** the S3 endpoint override SHALL come from `spring.cloud.aws.s3.endpoint` with fallback to `spring.cloud.aws.endpoint`
- **AND** path-style access SHALL come from `spring.cloud.aws.s3.path-style-access-enabled`

#### Scenario: Slim properties keep bucket and TTL only

- **WHEN** the `aws.s3` properties bind
- **THEN** only `bucket` (default `develop-assets`) and `presign-ttl` (default `PT15M`) SHALL be bound
- **AND** no region, endpoint, or path-style field SHALL remain on `AwsS3Properties`

#### Scenario: Endpoint override applied for local testing

- **WHEN** the S3 endpoint override (service-level or global fallback) is set to a non-empty URI
- **THEN** both the `S3Client` and the `S3Presigner` SHALL target that endpoint
- **AND** both SHALL use path-style addressing when `spring.cloud.aws.s3.path-style-access-enabled` is `true`

#### Scenario: Default AWS endpoint in production

- **WHEN** the S3 endpoint override is absent or empty
- **THEN** both clients SHALL resolve the standard AWS endpoint for the configured region
- **AND** no endpoint override SHALL be applied

#### Scenario: Credentials resolved via default chain only

- **WHEN** the beans are built in any environment
- **THEN** credentials SHALL be resolved exclusively through the default AWS credentials provider chain
- **AND** no credentials SHALL be hardcoded in `src/main`

#### Scenario: Legacy keys are not a source of truth

- **WHEN** the configuration binds in any environment
- **THEN** `aws.region` / `aws.s3.endpoint` SHALL NOT drive client construction
- **AND** any temporary `aws.*` alias SHALL resolve from the `spring.cloud.aws.*` values

#### Scenario: Beans consumed by the blob-storage adapter without behavior change

- **WHEN** the `blob-storage` capability issues an upload target or removes keys
- **THEN** the operations SHALL be served through the S3 beans defined by this requirement
- **AND** region resolution, endpoint handling, and credential resolution SHALL behave as specified above

#### Scenario: Startup without a reachable S3 endpoint

- **WHEN** the application starts while the configured S3 endpoint is unreachable
- **THEN** the context SHALL start successfully
- **AND** the failure SHALL surface only on the first S3 operation
