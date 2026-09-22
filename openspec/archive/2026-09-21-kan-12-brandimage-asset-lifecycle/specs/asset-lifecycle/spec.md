## Purpose

Provides the shared lifecycle of any persisted asset reference pointing at a blob in object storage, so brand, strain, product and dispensary assets track intent, confirmation and removal identically.

## ADDED Requirements

### Requirement: Shared asset lifecycle states

The system SHALL define exactly three asset lifecycle states — `PENDING`, `UPLOADED` and `DELETED` — in a single shared domain type reusable by every asset resource. `PENDING` SHALL mean the asset reference was recorded before its blob upload was confirmed. `UPLOADED` SHALL mean the upload was confirmed and SHALL be the only state considered visible to clients. `DELETED` SHALL mean the asset reference is retired while its blob may still exist. The shared type SHALL NOT contain any resource-specific concept.

#### Scenario: The lifecycle is reused by a new asset resource
- **WHEN** a new asset resource adopts the lifecycle
- **THEN** it SHALL reuse the shared states and transition rules without modifying them
- **AND** only an entity mapping and a database migration SHALL be required

#### Scenario: Only uploaded assets are visible
- **WHEN** an asset is in `PENDING` or `DELETED`
- **THEN** the system SHALL NOT consider it visible to clients

### Requirement: Legal asset lifecycle transitions

The system SHALL permit exactly the transitions `PENDING → UPLOADED`, `PENDING → DELETED` and `UPLOADED → DELETED`. `DELETED` SHALL be terminal. The system SHALL reject every other transition, including any self-transition and any transition to an undefined target.

#### Scenario: Confirming an upload
- **WHEN** a `PENDING` asset's upload is confirmed
- **THEN** the system SHALL move it to `UPLOADED`

#### Scenario: Retiring an asset
- **WHEN** an asset in `PENDING` or `UPLOADED` is retired
- **THEN** the system SHALL move it to `DELETED`

#### Scenario: Illegal transition is rejected
- **WHEN** a transition out of `DELETED`, a self-transition, or a transition to an undefined target is attempted
- **THEN** the system SHALL reject it and SHALL leave the current state unchanged

### Requirement: Lifecycle persistence format

The system SHALL persist the lifecycle state as its textual name and SHALL NOT persist it as an ordinal position. The database SHALL reject any value outside the three defined names. The state names SHALL be treated as a persistence contract: renaming a state SHALL require a data migration.

#### Scenario: State stored as text
- **WHEN** an asset row is read directly from the database
- **THEN** its lifecycle column SHALL contain the state name

#### Scenario: Unknown state rejected by the database
- **WHEN** a row is written with a lifecycle value outside the three defined names
- **THEN** the database SHALL reject the write
