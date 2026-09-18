# products-management

## Purpose

Owns the Product aggregate of the catalog: the invariants a product must satisfy to exist (unique commercial code, coherent taxonomy, resolvable references to collection, category, subcategory, brand, strain and measurement units) and the write API through which catalog products are created.

## Requirements

### Requirement: Create product via POST /api/products

The system SHALL expose `POST /api/products` to create a catalog product and SHALL return HTTP `201 Created` with the created product wrapped in the standard `DataResponse` envelope.

The request SHALL accept `ocpc`, `title`, `description`, `collectionId`, `categoryId`, `subcategoryId`, `brandId`, `strainId`, `formatValue`, `formatUnitId`, `contentValue`, `contentUnitId`, `isCoreProduct`, `approved`, `thc`, `cbd` and `enabled`.

The system SHALL require `ocpc`, `title`, `collectionId`, `categoryId`, `subcategoryId`, `brandId`, `strainId`, `formatValue`, `formatUnitId`, `contentValue` and `contentUnitId` to be present and non-blank. The system SHALL treat `description`, `isCoreProduct`, `approved`, `thc`, `cbd` and `enabled` as optional.

The request contract SHALL NOT accept audit or lifecycle metadata (creation, modification or deletion attributes); those values SHALL be assigned by the system and never taken from the client.

On success the system SHALL persist exactly one product, SHALL assign it a system-generated identifier, SHALL record its creation timestamp, and SHALL return the persisted attributes together with that identifier.

#### Scenario: Valid product creation

- **WHEN** a client sends a valid `POST /api/products` request containing all required fields with resolvable references
- **THEN** the system SHALL return HTTP `201 Created` with the created product and its generated identifier in the standard `DataResponse`
- **AND** exactly one product SHALL be persisted with its creation timestamp set

#### Scenario: Created product echoes the submitted attributes

- **WHEN** the system creates a product successfully
- **THEN** the response SHALL report, for every accepted attribute, the value that was actually persisted

### Requirement: Validation of the product creation payload

The system SHALL validate the product creation payload before applying any domain invariant and SHALL reject an invalid payload with HTTP `400 Bad Request` and the standard validation error response identifying each offending field by its request property name.

The system SHALL reject the request when any required field is absent, `null` or blank; when `ocpc` exceeds 64 characters; when `title` exceeds 255 characters; when any supplied identifier (`collectionId`, `categoryId`, `subcategoryId`, `brandId`, `strainId`, `formatUnitId`, `contentUnitId`) is not a positive value; when `formatValue` or `contentValue` is not a positive value; or when `thc` or `cbd` is negative.

A request rejected by validation SHALL NOT create any product.

#### Scenario: Missing or blank required field

- **WHEN** a client sends `POST /api/products` with a required field absent, `null` or blank
- **THEN** the system SHALL return HTTP `400 Bad Request` with the standard validation error response naming that field
- **AND** no product SHALL be created

#### Scenario: Required field present but invalid

- **WHEN** a client sends `POST /api/products` with a non-positive `formatValue`, `contentValue` or reference identifier, a negative `thc` or `cbd`, or an `ocpc` or `title` longer than its allowed length
- **THEN** the system SHALL return HTTP `400 Bad Request` with the standard validation error response naming each offending field
- **AND** no product SHALL be created

#### Scenario: Empty request body

- **WHEN** a client sends `POST /api/products` with an empty body
- **THEN** the system SHALL return HTTP `400 Bad Request` with the standard validation error response
- **AND** no product SHALL be created

### Requirement: Resolution of the references a product depends on

A product SHALL only exist while every entity it references can be resolved. The system SHALL resolve the supplied `collectionId`, `categoryId`, `subcategoryId`, `brandId`, `strainId`, `formatUnitId` and `contentUnitId`, and SHALL reject the creation with HTTP `404 Not Found` and a descriptive standard error response naming the offending reference when any of them cannot be resolved.

Resolvability is defined per reference type, according to which of them support soft-deletion:

- `collectionId`, `categoryId`, `subcategoryId`, `formatUnitId` and `contentUnitId` refer to reference data that has no deletion state; the only failure mode SHALL be that the referenced entity does not exist.
- `brandId` and `strainId` refer to soft-deletable entities; the failure modes SHALL be that the referenced entity does not exist **or** that it is soft-deleted. A soft-deleted brand or strain SHALL be treated exactly as if it did not exist.

A request rejected because a reference cannot be resolved SHALL NOT create any product.

#### Scenario: Unknown collection, category, subcategory or unit reference

- **WHEN** a client sends `POST /api/products` with a `collectionId`, `categoryId`, `subcategoryId`, `formatUnitId` or `contentUnitId` that does not exist
- **THEN** the system SHALL return HTTP `404 Not Found` with the standard error response naming the unresolved reference
- **AND** no product SHALL be created

#### Scenario: Unknown brand or strain reference

- **WHEN** a client sends `POST /api/products` with a `brandId` or `strainId` that does not exist
- **THEN** the system SHALL return HTTP `404 Not Found` with the standard error response naming the unresolved reference
- **AND** no product SHALL be created

#### Scenario: Soft-deleted brand or strain reference

- **WHEN** a client sends `POST /api/products` with a `brandId` or `strainId` that identifies a soft-deleted brand or strain
- **THEN** the system SHALL treat that reference as non-existent and return HTTP `404 Not Found` with the standard error response
- **AND** no product SHALL be created

### Requirement: OCPC uniqueness among non-deleted products

The `ocpc` SHALL identify a product commercially and SHALL be unique across all products that are not soft-deleted. The system SHALL reject a creation whose `ocpc` is already used by a non-deleted product with HTTP `409 Conflict` and a descriptive standard error response, and SHALL NOT create any product.

A soft-deleted product SHALL NOT reserve its `ocpc`: the same `ocpc` SHALL be accepted again once the product holding it has been soft-deleted.

#### Scenario: Duplicated OCPC

- **WHEN** a client sends `POST /api/products` with an `ocpc` already used by a non-deleted product
- **THEN** the system SHALL return HTTP `409 Conflict` with the standard error response
- **AND** no product SHALL be created

#### Scenario: OCPC of a soft-deleted product is reusable

- **WHEN** a client sends `POST /api/products` with an `ocpc` whose only previous holder is a soft-deleted product
- **THEN** the system SHALL accept the request and return HTTP `201 Created`

### Requirement: Taxonomy coherence between category and subcategory

A product's subcategory SHALL belong to the product's category. The system SHALL reject a creation whose referenced subcategory has a parent category different from the referenced `categoryId` with HTTP `409 Conflict` and a descriptive standard error response, and SHALL NOT create any product. No product SHALL ever be persisted with an inconsistent category/subcategory pair.

#### Scenario: Subcategory not belonging to the supplied category

- **WHEN** a client sends `POST /api/products` with a `subcategoryId` whose parent category differs from the supplied `categoryId`
- **THEN** the system SHALL return HTTP `409 Conflict` with the standard error response
- **AND** no product SHALL be created

#### Scenario: Subcategory belonging to the supplied category

- **WHEN** a client sends `POST /api/products` with a `subcategoryId` whose parent category is the supplied `categoryId`
- **THEN** the system SHALL accept the taxonomy pair and continue the creation

### Requirement: Optional product attributes

The system SHALL accept a product creation in which `description`, `isCoreProduct`, `approved`, `thc`, `cbd` or `enabled` are omitted, and SHALL persist each omitted attribute as unset (`NULL`) rather than substituting a value.

An unset `enabled` SHALL be interpreted as *not enabled*, an unset `approved` as *not approved*, and an unset `isCoreProduct` as *not a core product*. Unset `thc` and `cbd` SHALL mean *no potency value recorded*, which is distinct from a recorded value of zero.

#### Scenario: Optional attributes omitted

- **WHEN** a client sends `POST /api/products` omitting `enabled`, `approved`, `isCoreProduct`, `thc` and `cbd`
- **THEN** the system SHALL return HTTP `201 Created`
- **AND** the created product SHALL have those five attributes persisted as unset (`NULL`)

#### Scenario: Optional flags supplied explicitly

- **WHEN** a client sends `POST /api/products` supplying `enabled`, `approved` and `isCoreProduct` explicitly
- **THEN** the system SHALL persist and report exactly the supplied values

### Requirement: Atomicity of product creation

Product creation SHALL be atomic. A rejected `POST /api/products` request SHALL leave no trace in the catalog: no product row and no partially populated product SHALL remain, regardless of which condition caused the rejection (payload validation, unresolved reference, duplicated `ocpc`, incoherent taxonomy or an unexpected failure).

#### Scenario: Rejected request persists nothing

- **WHEN** a `POST /api/products` request is rejected with HTTP `400`, `404`, `409` or `500`
- **THEN** the number of stored products SHALL be identical to the number stored immediately before the request
- **AND** no partially created product SHALL be observable

### Requirement: Standard response envelopes for the product endpoints

The system SHALL wrap successful product responses in the project's standard `DataResponse` envelope and every error response in the project's standard error envelope, which reports the timestamp, the HTTP status, the request path and the list of errors. Validation failures SHALL report one error entry per offending field, keyed by the request property name. Not-found and conflict failures SHALL report a descriptive message. Error responses SHALL NOT expose SQL statements, database constraint names or stack traces.

#### Scenario: Validation failure reports field-level errors

- **WHEN** the system rejects a product creation because of payload validation
- **THEN** the system SHALL return HTTP `400 Bad Request` with the standard error envelope listing one entry per offending field, identified by its request property name

#### Scenario: Not-found and conflict failures report a descriptive message

- **WHEN** the system rejects a product creation because a reference cannot be resolved, because the `ocpc` is already taken or because the taxonomy is incoherent
- **THEN** the system SHALL return HTTP `404 Not Found` or HTTP `409 Conflict` accordingly, with the standard error envelope and a descriptive message

#### Scenario: Error responses do not leak internal details

- **WHEN** the system returns any error response for a product endpoint
- **THEN** the response SHALL NOT contain SQL statements, database constraint names or stack traces
