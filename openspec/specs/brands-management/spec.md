# brands-management

## Requirements

### Requirement: Brand entity mapping and soft-delete semantics

The system SHALL map the `Brand` entity to the existing `brands` table and the `BrandType` lookup entity to the existing `brand_types` table using JPA, following the existing DDD layered pattern. The `Brand` entity SHALL extend `BaseEntity`, which provides the `deletedAt` soft-delete field. The system MUST never physically delete brand records; soft-deletion MUST be represented solely by setting the `deletedAt` value. All brand queries exposed through the API MUST filter out records whose `deletedAt` is not null.

#### Scenario: Soft-deleted brand is excluded from queries

- **WHEN** a brand record has its `deletedAt` value set
- **THEN** it MUST be excluded from every list and get-by-id operation performed through the API

#### Scenario: Brand records are never physically removed

- **WHEN** a brand is soft-deleted
- **THEN** the underlying row MUST remain present in the `brands` table and MUST NOT be removed from the database

### Requirement: Create brand via POST /api/brands

The system SHALL expose `POST /api/brands` to create a brand and SHALL return HTTP `201 Created` wrapped in the standard `DataResponse` when successful. The creation request SHALL accept `name`, `description`, `email`, `stateLicense`, `brandTypeName`, `logoImageUrl`, `instagramUrl`, `twitterUrl`, `facebookUrl`, `websiteUrl`, `adminId`, and `enabled`. The system SHALL require `name`, `description`, `email`, `stateLicense`, `brandTypeName`, `logoImageUrl`, and `adminId` to be present and non-empty, and SHALL require `email` to match a valid email format. The response SHALL include the created brand along with its generated identifier.

#### Scenario: Valid brand creation

- **WHEN** a client sends a valid `POST /api/brands` request containing all required fields
- **THEN** the system SHALL create the brand
- **AND** return HTTP `201 Created` with the created brand data in a `DataResponse`

#### Scenario: Missing required field on creation

- **WHEN** a client sends `POST /api/brands` with a required field missing or blank
- **THEN** the system SHALL reject the request with HTTP `400 Bad Request` and a standard validation error response

#### Scenario: Invalid email format on creation

- **WHEN** a client sends `POST /api/brands` with an `email` that does not match a valid email format
- **THEN** the system SHALL reject the request with HTTP `400 Bad Request` and a validation error for the `email` field

### Requirement: Get all brands via GET /api/brands

The system SHALL expose `GET /api/brands` to list all non-deleted brands and return HTTP `200 OK` with the list of brands in the standard `DataResponse`. The response SHALL contain a compact view of each brand.

#### Scenario: Get all brands returns only non-deleted brands

- **WHEN** a client sends a `GET /api/brands` request
- **THEN** the system SHALL return HTTP `200 OK` with the list of all brands that are not deleted

#### Scenario: Get all brands returns no deleted brands

- **WHEN** a client sends a `GET /api/brands` request and one or more brands are soft-deleted
- **THEN** the system SHALL return a response that excludes every soft-deleted brand

### Requirement: Get brand by id via GET /api/brands/{brandId}

The system SHALL expose `GET /api/brands/{brandId}` to retrieve a single non-deleted brand and return HTTP `200 OK` with the brand in the standard `DataResponse`. If no non-deleted brand exists for the given identifier, the system SHALL return HTTP `404 Not Found`.

#### Scenario: Get brand by existing id

- **WHEN** a client sends `GET /api/brands/{brandId}` for an existing non-deleted brand
- **THEN** the system SHALL return HTTP `200 OK` with that brand's data in the standard `DataResponse`

#### Scenario: Get brand with non-existent id

- **WHEN** a client sends `GET /api/brands/{brandId}` for an id that does not exist
- **THEN** the system SHALL return HTTP `404 Not Found` with the standard error response

#### Scenario: Get soft-deleted brand

- **WHEN** a client sends `GET /api/brands/{brandId}` for a brand that has been soft-deleted
- **THEN** the system SHALL treat the brand as not found
- **AND** the system SHALL return HTTP `404 Not Found` with the standard error response

### Requirement: Update brand via PATCH /api/brands/{brandId}

The system SHALL expose `PATCH /api/brands/{brandId}` to partially or fully update a non-deleted brand and return HTTP `200 OK` with the updated brand in the standard `DataResponse`. The update request SHALL accept any subset of the creatable fields, and the system SHALL change only the fields that are provided in the request, leaving all other fields unchanged. If no non-deleted brand exists for the given identifier, the system SHALL return HTTP `404 Not Found`. Validation rules SHALL apply to any provided field.

#### Scenario: Partial update of existing brand

- **WHEN** a client sends a `PATCH /api/brands/{brandId}` request providing only a subset of fields for an existing non-deleted brand
- **THEN** the system SHALL update only the provided fields
- **AND** the system SHALL return HTTP `200 OK` with the updated brand while leaving all non-provided fields unchanged

#### Scenario: Update brand with non-existent id

- **WHEN** a client sends `PATCH /api/brands/{brandId}` for an id that does not exist
- **THEN** the system SHALL return HTTP `404 Not Found` with the standard error response

#### Scenario: Update soft-deleted brand

- **WHEN** a client sends `PATCH /api/brands/{brandId}` for a brand that has been soft-deleted
- **THEN** the system SHALL treat the brand as not found
- **AND** the system SHALL return HTTP `404 Not Found` with the standard error response

#### Scenario: Update brand with invalid provided value

- **WHEN** a client sends `PATCH /api/brands/{brandId}` providing a value that violates the field validation rules
- **THEN** the system SHALL reject the request with HTTP `400 Bad Request` and the standard validation error

### Requirement: BrandType lookup resolution on create and update

The system SHALL resolve the `brandTypeName` supplied on brand create and update operations against the `brand_types` lookup table by name and associate the resolved `BrandType` entity with the brand. If the provided `brandTypeName` does not match any existing `BrandType`, the system SHALL reject the operation with the standard not-found error response.

#### Scenario: Resolve existing brand type during create

- **WHEN** a client creates a brand with a `brandTypeName` that matches an existing `BrandType`
- **THEN** the system SHALL associate the brand with that resolved `BrandType`

#### Scenario: Resolve existing brand type during update

- **WHEN** a client updates a brand providing a `brandTypeName` that matches an existing `BrandType`
- **THEN** the system SHALL re-associate the brand with that resolved `BrandType`

#### Scenario: Invalid brand type name during create

- **WHEN** a client creates a brand with a `brandTypeName` that does not match any existing `BrandType`
- **THEN** the system SHALL reject the operation with a `404 Not Found` error response and MUST NOT create the brand

#### Scenario: Invalid brand type name during update

- **WHEN** a client updates a brand with a `brandTypeName` that does not match any existing `BrandType`
- **THEN** the system SHALL reject the operation with a `404 Not Found` error and MUST NOT change the brand

### Requirement: Standard error response shape

The system SHALL return all error responses for the brand endpoints in the shape produced by the existing `GlobalExceptionHandler` and `ErrorResponse` contract. Validation failures on create or update SHALL return HTTP `400 Bad Request` and MUST produce error responses identifying the failing fields. Missing or soft-deleted brands identified by id SHALL return HTTP `404 Not Found`, and the system SHALL include the detail in the standard error response. Upstream blob-store failures on brand image upload-target creation SHALL return HTTP `502 Bad Gateway` with the standard error response and a fixed client-facing message that SHALL NOT echo the upstream error text, URL, or signature material.

#### Scenario: Validation failure returns field errors

- **WHEN** the system rejects a brand create or update request due to validation failure
- **THEN** the system SHALL return HTTP `400 Bad Request` with a standard error response listing the offending fields

#### Scenario: Not found returns standard error response

- **WHEN** the system cannot resolve a brand because it does not exist or is deleted
- **THEN** the system SHALL return HTTP `404 Not Found` with the standard error response and include a descriptive message

#### Scenario: Blob store failure returns fixed-message 502

- **WHEN** the blob store fails while issuing a brand image upload target
- **THEN** the system SHALL return HTTP `502 Bad Gateway` with the standard error response
- **AND** the response message SHALL be a fixed string that contains no upload URL, signature material, or upstream error text

### Requirement: Brand image persistence and lifecycle

The system SHALL map brand images to the existing `brand_images` table as a first-class entity that extends the shared audit base type and references its brand. Each brand image SHALL store the canonical blob key issued by the blob-storage port for the `BRAND_IMAGE` blob type, SHALL NOT store a presigned URL, and SHALL carry a shared asset lifecycle state. A brand image SHALL be created only in the `PENDING` state. Its blob key SHALL be immutable and unique across all brand images. The system MUST NOT physically delete brand image rows: removal SHALL be represented solely by the `DELETED` lifecycle state.

#### Scenario: Brand image recorded before upload
- **WHEN** a brand image is created for a canonical `BRAND_IMAGE` key
- **THEN** the system SHALL persist it in the `PENDING` state with its audit timestamps populated

#### Scenario: Non-canonical or foreign key rejected
- **WHEN** a brand image is created with a key that is not canonical or that belongs to another blob type
- **THEN** the system SHALL reject the creation

#### Scenario: Duplicated blob key rejected
- **WHEN** a brand image is created with a key already recorded for another brand image
- **THEN** the database SHALL reject the write

#### Scenario: Retired brand image keeps its row
- **WHEN** a brand image is moved to `DELETED`
- **THEN** the row SHALL remain present in `brand_images`
- **AND** the system SHALL NOT remove it physically

#### Scenario: No presigned URL is persisted
- **WHEN** brand image rows are inspected
- **THEN** they SHALL contain only the opaque blob key and SHALL NOT contain any presigned URL

### Requirement: Create brand image upload target

The system SHALL expose an operation that registers a new image for an existing brand and returns a short-lived, credential-free upload target for it. The operation SHALL resolve the brand before any other work and SHALL reject a missing or soft-deleted brand with `404 Not Found` without issuing any blob key. On success the system SHALL issue an upload target for the `BRAND_IMAGE` blob type, SHALL persist exactly one brand image in the `PENDING` state carrying the issued key, and SHALL return the upload target. The persisted brand image SHALL be a server-side record that is not observable in the response. The operation SHALL return `201 Created` carrying the response wrapped in the standard `DataResponse` with exactly three fields — the upload URL, the upload HTTP method, and the expiry instant — and SHALL NOT expose the brand image identifier, the brand identifier, the blob key, or the lifecycle state. The operation SHALL NOT accept a request body and SHALL NOT accept a caller-supplied blob key. The operation SHALL NOT be idempotent: each invocation SHALL produce a distinct key and a distinct brand image. The response SHALL be marked non-cacheable. The upload URL SHALL NOT be persisted and SHALL NOT appear in any log record, exception message, metric tag, or API example.

#### Scenario: Upload target issued for an existing brand

- **WHEN** an upload target is requested for an existing non-deleted brand via `POST /api/brands/{brandId}/images`
- **THEN** the system SHALL return `201 Created` with a `DataResponse` body containing exactly three fields: `uploadUrl`, the HTTP method `PUT` as `uploadMethod`, and the expiry instant as `expiresAt`
- **AND** the response SHALL NOT contain `id`, `brandId`, `imageKey`, or `status`
- **AND** the system SHALL persist exactly one brand image in the `PENDING` state with the port-issued key for that brand

#### Scenario: Issued target accepts a credential-free upload

- **WHEN** a client uploads content to the returned upload URL with no credentials before it expires
- **THEN** the object SHALL be stored in the configured bucket at exactly the key recorded on the persisted brand image row

#### Scenario: Each request yields a distinct image

- **WHEN** an upload target is requested twice in a row for the same brand
- **THEN** the system SHALL return two different upload URLs
- **AND** the system SHALL persist two distinct brand images with two different blob keys

#### Scenario: Missing or soft-deleted brand is rejected without issuing a key

- **WHEN** an upload target is requested for a brand that does not exist or has been soft-deleted
- **THEN** the system SHALL return `404 Not Found` with the standard error response and the message `Brand not found with ID: {brandId}`
- **AND** the system SHALL NOT issue a blob key
- **AND** the system SHALL NOT persist any brand image

#### Scenario: Non-numeric brand identifier is rejected without side effects

- **WHEN** an upload target is requested with a brand identifier that is not a number
- **THEN** the system SHALL return HTTP `400 Bad Request` via the existing type-mismatch handler
- **AND** the system SHALL perform no brand lookup, SHALL issue no blob key, and SHALL persist no brand image

#### Scenario: Blob store failure persists nothing and leaks nothing

- **WHEN** the blob store fails to issue an upload target
- **THEN** the system SHALL return `502 Bad Gateway` with the standard error response
- **AND** the system SHALL NOT persist any brand image
- **AND** the error response SHALL NOT contain any upload URL or signature material

#### Scenario: Persistence failure discloses no upload target

- **WHEN** the brand image row insert fails after the upload target was issued
- **THEN** the transaction SHALL roll back so no row persists
- **AND** the presigned upload URL SHALL never be returned to the client

#### Scenario: Upload URL is never retained and the response is non-cacheable

- **WHEN** an upload target has been issued
- **THEN** no persisted row, log record, exception message, or metric tag SHALL contain the upload URL or its query string
- **AND** the persisted row SHALL contain only the opaque blob key
- **AND** the success response SHALL carry `Cache-Control: no-store`

#### Scenario: Collection URI rejects PUT

- **WHEN** a client sends `PUT /api/brands/{brandId}/images`
- **THEN** the system SHALL return `405 Method Not Allowed`

#### Scenario: No request body or caller-supplied key is accepted

- **WHEN** an upload target is requested with a request body or a caller-supplied blob key
- **THEN** the system SHALL ignore the body and SHALL use only the port-issued key

#### Scenario: Browser uploads are permitted by the store

- **WHEN** a browser on an allowed application origin issues the cross-origin preflight for the returned upload URL
- **THEN** the object store SHALL permit the subsequent `PUT` from that origin