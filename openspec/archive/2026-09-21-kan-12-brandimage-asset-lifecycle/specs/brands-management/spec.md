## ADDED Requirements

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
