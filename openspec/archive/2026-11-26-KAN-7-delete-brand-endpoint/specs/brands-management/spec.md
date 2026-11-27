## ADDED Requirements

### Requirement: Delete brand via DELETE /api/brands/{brandId}

The system SHALL expose `DELETE /api/brands/{brandId}` to soft-delete a brand and SHALL return HTTP `200 OK` with a removal confirmation wrapped in the standard `DataResponse` envelope when successful. Deletion MUST be logical only: the system SHALL set the brand's soft-delete timestamp and MUST NOT physically remove the underlying row; the brand SHALL thereafter be excluded from every brand query, consistent with the soft-delete semantics of this capability. The system SHALL resolve the brand before any other check: a brand that does not exist, or that was already soft-deleted, SHALL be answered with HTTP `404 Not Found` and the standard error response, and MUST NOT be reported as a conflict. A brand that is still referenced by at least one non-deleted product SHALL NOT be deletable; the system SHALL reject the request with HTTP `409 Conflict` and a descriptive error message naming the reason. Status-code decision (traceability): the brand-in-use case is specified as `409 Conflict` — not the `400 Bad Request` loosely worded in the source ticket — because the project's declared API standard (`docs/backend-standards.md`) reserves `400` for malformed input and assigns business-state conflicts to `409`; the ticket wording is superseded by that standard. A brand identifier that is not a number SHALL be rejected with HTTP `400 Bad Request` by the existing type-mismatch handling, before any brand lookup runs. A rejected delete MUST leave the catalog unchanged: no brand SHALL be soft-deleted when the request fails.

#### Scenario: Successful soft delete of an existing brand

- **WHEN** a client sends `DELETE /api/brands/{brandId}` for an existing brand that is not deleted and is not referenced by any non-deleted product
- **THEN** the system SHALL return HTTP `200 OK` with the standard `DataResponse` envelope confirming the removal
- **AND** the system SHALL mark the brand as soft-deleted without physically removing its row from the `brands` table
- **AND** subsequent `GET /api/brands` and `GET /api/brands/{brandId}` requests SHALL exclude the brand

#### Scenario: Delete brand with non-existent id

- **WHEN** a client sends `DELETE /api/brands/{brandId}` for an id that does not exist
- **THEN** the system SHALL return HTTP `404 Not Found` with the standard error response
- **AND** the system SHALL NOT soft-delete any brand

#### Scenario: Delete an already soft-deleted brand

- **WHEN** a client sends `DELETE /api/brands/{brandId}` for a brand that has already been soft-deleted
- **THEN** the system SHALL treat the brand as not found and return HTTP `404 Not Found` with the standard error response
- **AND** the system SHALL NOT report a conflict even if the brand is still referenced by non-deleted products, because the brand is resolved before any other check
- **AND** the system SHALL NOT apply any further change to the brand

#### Scenario: Delete a brand still referenced by a non-deleted product

- **WHEN** a client sends `DELETE /api/brands/{brandId}` for an existing non-deleted brand that is referenced by at least one non-deleted product
- **THEN** the system SHALL reject the request with HTTP `409 Conflict` and the standard error response carrying a descriptive message stating that the brand cannot be deleted because it is used by products
- **AND** the system SHALL NOT soft-delete the brand

#### Scenario: Delete a brand referenced only by soft-deleted products

- **WHEN** a client sends `DELETE /api/brands/{brandId}` for an existing non-deleted brand whose only referencing products are themselves soft-deleted
- **THEN** the system SHALL NOT reject the request as a conflict
- **AND** the system SHALL return HTTP `200 OK` and soft-delete the brand

#### Scenario: Delete brand with a non-numeric identifier

- **WHEN** a client sends `DELETE /api/brands/{brandId}` with a brandId that is not a number
- **THEN** the system SHALL return HTTP `400 Bad Request` via the existing type-mismatch handling
- **AND** the system SHALL perform no brand lookup and SHALL NOT soft-delete any brand

#### Scenario: A rejected delete leaves the catalog unchanged

- **WHEN** a `DELETE /api/brands/{brandId}` request is rejected for any reason (`400`, `404`, or `409`)
- **THEN** the catalog SHALL remain exactly as it was before the request: no brand is soft-deleted and no brand data is altered
