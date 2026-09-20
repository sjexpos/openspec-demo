## Purpose

Provides reusable, environment-driven AWS S3 access for local development and production so follow-up asset stories can inject S3 clients with zero infrastructure risk.

## ADDED Requirements

### Requirement: Provide configurable S3 client beans

The system SHALL expose a singleton `S3Client` bean and a singleton `S3Presigner` bean in the Spring application context, both built with AWS SDK v2 and both configured from the `aws.region` and `aws.s3.*` properties.

The configuration contract SHALL be: `aws.region` defaults to `us-east-1`; `aws.s3.endpoint` is a `URI` that is empty by default where empty means real AWS; `aws.s3.path-style-access` defaults to `false`; `aws.s3.bucket` defaults to `develop-assets`; `aws.s3.presign-ttl` is a `Duration` that defaults to `PT15M`.

The system SHALL apply an `endpointOverride` to both beans only when `aws.s3.endpoint` is configured as a non-empty `URI`, and SHALL use default AWS endpoint resolution for the configured region otherwise. Path-style addressing applied to the `S3Client` SHALL also be applied to the `S3Presigner` via its service configuration.

The system SHALL resolve credentials exclusively through the default AWS credentials provider chain and SHALL NOT embed credentials in source code. Bean creation SHALL NOT perform network calls.

This requirement SHALL NOT introduce a domain port, storage adapter, controller, health indicator, SQS consumer, bucket hardening, Testcontainers module, or async client.

#### Scenario: Beans available for injection

- **WHEN** the application context starts
- **THEN** the system SHALL provide exactly one `S3Client` bean AND exactly one `S3Presigner` bean available for constructor injection

#### Scenario: Endpoint override applied for local testing

- **WHEN** `aws.s3.endpoint` is set to a non-empty URI
- **THEN** both the `S3Client` and the `S3Presigner` SHALL target that endpoint
- **AND** both SHALL use path-style addressing when `aws.s3.path-style-access` is `true`

#### Scenario: Default AWS endpoint in production

- **WHEN** `aws.s3.endpoint` is absent or empty
- **THEN** both clients SHALL resolve the standard AWS endpoint for the configured region
- **AND** no endpoint override SHALL be applied

#### Scenario: Region resolved from configuration

- **WHEN** `aws.region` is set to a region value
- **THEN** both clients SHALL use that region
- **AND** when `aws.region` is absent the system SHALL default to `us-east-1`

#### Scenario: Credentials resolved via default chain only

- **WHEN** the beans are built in any environment
- **THEN** credentials SHALL be resolved exclusively through the default AWS credentials provider chain
- **AND** no credentials SHALL be hardcoded in `src/main`

#### Scenario: Object round-trip against overridden endpoint

- **WHEN** an object is written with `putObject` and read back with `getObject` while an endpoint override is active
- **THEN** the read content SHALL match the written content

#### Scenario: Presigned URL targets the overridden endpoint

- **WHEN** a presigned GET URL is generated while an endpoint override is active
- **THEN** the URL SHALL point at the overridden host
- **AND** the URL SHALL grant credential-free read access to the object until it expires

#### Scenario: Startup without a reachable S3 endpoint

- **WHEN** the application starts while the configured S3 endpoint is unreachable
- **THEN** the context SHALL start successfully
- **AND** the failure SHALL surface only on the first S3 operation
