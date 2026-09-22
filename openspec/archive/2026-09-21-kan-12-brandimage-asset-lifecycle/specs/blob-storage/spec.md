## MODIFIED Requirements

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
