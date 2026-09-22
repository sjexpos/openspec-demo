## MODIFIED Requirements

### Requirement: Scope exclusions

This capability SHALL NOT itself expose an HTTP endpoint, a persistence entity, a repository, a service, or a database migration; consuming capabilities MAY expose endpoints that orchestrate this port. This capability SHALL NOT enforce an upload size or content-type restriction, SHALL NOT provide orphan-blob reclamation, SHALL NOT provide a second storage implementation, and SHALL NOT introduce a new configuration property. All blobs SHALL live in the single bucket configured by `aws.s3.bucket` with validity governed by `aws.s3.presign-ttl`.

#### Scenario: Out-of-scope surfaces are absent

- **WHEN** the capability is delivered
- **THEN** the capability SHALL expose no HTTP endpoint of its own and no persistence entity for blobs
- **AND** the system SHALL enforce no upload size or content-type restriction
- **AND** the system SHALL provide no orphan-blob reclamation and no second storage implementation
