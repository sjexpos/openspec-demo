## ADDED Requirements

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
- **THEN** the system SHALL return `400 Bad Request` via the existing type-mismatch handler
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

## MODIFIED Requirements

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
