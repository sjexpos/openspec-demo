## Purpose

Provides a storage-agnostic blob upload and removal port for brand, strain, and product image and video assets, with a first AWS S3 implementation that issues presigned PUT targets and removes keys without leaking vendor details.

## Requirements

### Requirement: Blob type to key prefix mapping

The system SHALL support exactly six blob types — `BRAND_IMAGE`, `BRAND_VIDEO`, `STRAIN_IMAGE`, `STRAIN_VIDEO`, `PRODUCT_IMAGE`, and `PRODUCT_VIDEO` — each mapped to a distinct key prefix within the single bucket configured by `aws.s3.bucket`: `brands/images/`, `brands/videos/`, `strains/images/`, `strains/videos/`, `products/images/`, and `products/videos/` respectively.

A canonical blob key SHALL consist of a known prefix followed by exactly 32 lowercase hexadecimal characters (`[0-9a-f]{32}`), giving the overall shape `^(brands|strains|products)/(images|videos)/[0-9a-f]{32}$`. Keys SHALL be generated inside the port and SHALL never be supplied by the caller. The canonical-key predicate SHALL be derived from the same prefix definitions used to build keys, so adding a blob type does not duplicate the pattern.

The system SHALL additionally expose a per-blob-type canonical-key check that is true only when a key is canonical **and** carries that blob type's prefix, derived from the same prefix definitions as the global canonical-key predicate.

#### Scenario: Each blob type maps to its own distinct prefix

- **WHEN** an upload target is requested for each of the six blob types
- **THEN** each returned key SHALL carry the prefix of its own blob type
- **AND** all six prefixes SHALL be pairwise distinct

#### Scenario: Product keys preserve the object-created notification prefix

- **WHEN** an upload target is requested for `PRODUCT_IMAGE` or `PRODUCT_VIDEO`
- **THEN** the returned key SHALL start with `products/`
- **AND** existing `products/`-filtered object-created notifications SHALL continue to match product asset keys while brand and strain keys SHALL NOT match that filter

#### Scenario: Non-canonical keys are distinguishable from port-issued keys

- **WHEN** a key with an unknown prefix, a random part that is not exactly 32 lowercase hexadecimal characters, a null or blank value, or a path-traversal segment is evaluated
- **THEN** the system SHALL treat it as non-canonical and SHALL reject it as a managed blob key

#### Scenario: Key ownership is distinguishable per blob type
- **WHEN** a canonical key for one blob type is checked against a different blob type
- **THEN** the system SHALL report that the key does not belong to that blob type
- **AND** the global canonical-key predicate SHALL still report the key as canonical

### Requirement: Upload target issuance

The system SHALL expose an operation that issues an upload target for a given blob type. The target SHALL consist of a canonical key, a presigned URL for the configured `aws.s3.bucket`, the HTTP method `PUT`, and an expiry instant equal to the issue time plus `aws.s3.presign-ttl`. The URL SHALL grant credential-free upload access to exactly the returned key until it expires. Two consecutive calls for the same blob type SHALL return different keys and different URLs. A null blob type SHALL be rejected with `IllegalArgumentException`. The configured bucket name SHALL be required to be non-blank.

#### Scenario: Upload target issued for a blob type

- **WHEN** an upload target is requested for a supported blob type
- **THEN** the system SHALL return a key matching `^(brands|strains|products)/(images|videos)/[0-9a-f]{32}$` with the prefix of the requested blob type
- **AND** the system SHALL return the HTTP method `PUT`
- **AND** the system SHALL return a presigned URL that targets the configured bucket and the returned key and is valid for `aws.s3.presign-ttl`
- **AND** the system SHALL return an expiry instant equal to the issue time plus `aws.s3.presign-ttl`

#### Scenario: Consecutive upload targets are unique

- **WHEN** two upload targets are requested consecutively for the same blob type
- **THEN** the system SHALL return two different keys
- **AND** the system SHALL return two different URLs

#### Scenario: Credential-free upload through the issued URL

- **WHEN** a client performs `PUT` against the returned URL with no AWS credentials before the URL expires
- **THEN** the object SHALL be stored in the configured bucket at exactly the returned key

#### Scenario: Null blob type is rejected

- **WHEN** an upload target is requested with a null blob type
- **THEN** the system SHALL throw `IllegalArgumentException`
- **AND** the system SHALL perform no call to the object store

### Requirement: Validated idempotent batch removal

The system SHALL expose an operation that removes a set of blob keys. The operation SHALL delete every corresponding object from the configured bucket. Removal of a canonical key that has no stored object SHALL be a silent no-op, so removal is idempotent. Removal requested with an empty set SHALL be a no-op and SHALL perform no call to the object store. A null key set, or any key that is not canonical, SHALL cause the whole request to be rejected with `IllegalArgumentException` before any call to the object store, and no object SHALL be deleted in that case. Batches larger than the object store single-request limit SHALL be split into chunks of at most 1000 keys, and every listed object SHALL still be deleted.

#### Scenario: Blobs removed by key

- **WHEN** removal is requested with a set of keys previously issued by the port
- **THEN** the system SHALL delete every corresponding object from the configured bucket

#### Scenario: Removal of unknown canonical keys is idempotent

- **WHEN** removal is requested with a canonical key that has no stored object
- **THEN** the system SHALL complete successfully
- **AND** the system SHALL NOT report an error

#### Scenario: Empty removal request performs no store call

- **WHEN** removal is requested with an empty set of keys
- **THEN** the system SHALL delete nothing
- **AND** the system SHALL perform no call to the object store

#### Scenario: Null key set rejected before any store call

- **WHEN** removal is requested with a null key set
- **THEN** the system SHALL throw `IllegalArgumentException`
- **AND** the system SHALL perform no call to the object store

#### Scenario: Non-canonical key rejects the whole batch before any store call

- **WHEN** removal is requested with a set containing any key that does not match a managed prefix followed by a 32-character lowercase hexadecimal string
- **THEN** the system SHALL reject the whole request with `IllegalArgumentException`
- **AND** the system SHALL NOT delete any object
- **AND** the system SHALL perform no call to the object store

#### Scenario: Large removal batches are chunked

- **WHEN** removal is requested with more keys than the object store accepts in a single request
- **THEN** the system SHALL split the request into batches of at most 1000 keys
- **AND** the system SHALL delete every listed object

### Requirement: Failure reporting and presigned URL confidentiality

All store-side failures, including presigning failures and partial batch-delete failures, SHALL be reported as `BlobStorageException`. For a partial batch failure the exception SHALL carry the failed keys. No vendor-specific exception, return, or parameter type SHALL escape the port; vendor exceptions SHALL be retained as the cause only. Presigned URLs SHALL never be written to any log at any level, and SHALL never be placed in exception messages or metric tags. Issue and removal operations SHALL log only the blob type and key, and partial failures SHALL log the failed keys at warn level.

#### Scenario: Partial batch failure carries failed keys

- **WHEN** the object store reports per-key failures for part of a removal batch
- **THEN** the system SHALL raise `BlobStorageException` carrying the failed keys

#### Scenario: Storage failures are reported without vendor leakage

- **WHEN** the object store rejects an operation or fails partially
- **THEN** the system SHALL raise `BlobStorageException`
- **AND** no vendor-specific exception type SHALL escape the port

#### Scenario: Presigned URLs are never logged

- **WHEN** an upload target is issued or a removal is performed
- **THEN** no log record at any level SHALL contain the presigned URL or its signature material
- **AND** no exception message or metric tag SHALL contain the presigned URL

### Requirement: Scope exclusions

This capability SHALL NOT introduce an HTTP endpoint, a persistence entity, a repository, a service, a database migration, an upload size or content-type restriction, an orphan-blob reclamation process, a second storage implementation, or a new configuration property. All blobs SHALL live in the single bucket configured by `aws.s3.bucket` with validity governed by `aws.s3.presign-ttl`.

#### Scenario: Out-of-scope surfaces are absent

- **WHEN** the capability is delivered
- **THEN** the system SHALL expose no HTTP endpoint wrapping the port
- **AND** the system SHALL apply no database migration and provide no persistence entity for blobs
- **AND** the system SHALL enforce no upload size or content-type restriction
- **AND** the system SHALL provide no orphan-blob reclamation and no second storage implementation
