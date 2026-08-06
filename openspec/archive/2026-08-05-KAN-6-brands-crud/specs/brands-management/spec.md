## ADDED Requirements

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

The system SHALL return all error responses for the brand endpoints in the shape produced by the existing `GlobalExceptionHandler` and `ErrorResponse` contract. Validation failures on create or update SHALL return HTTP `400 Bad Request` and MUST produce error responses identifying the failing fields. Missing or soft-deleted brands identified by id SHALL return HTTP `404 Not Found`, and the system SHALL include the detail in the standard error response.

#### Scenario: Validation failure returns field errors

- **WHEN** the system rejects a brand create or update request due to validation failure
- **THEN** the system SHALL return HTTP `400 Bad Request` with a standard error response listing the offending fields

#### Scenario: Not found returns standard error response

- **WHEN** the system cannot resolve a brand because it does not exist or is deleted
- **THEN** the system SHALL return HTTP `404 Not Found` with the standard error response and include a descriptive message