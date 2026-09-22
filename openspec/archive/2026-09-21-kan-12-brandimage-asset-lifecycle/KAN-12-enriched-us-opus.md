# KAN-12 — Enriched User Story: `BrandImage` persistence + shared `AssetStatus` lifecycle enum

> **Jira note**: no Jira MCP server is configured in this workspace (`mcpServers` is empty for this
> project), so the ticket could not be read or updated automatically. This document is the
> `[enhanced]` section: paste it into KAN-12 below the `[original]` text and move the ticket from
> *To refine* to *Pending refinement validation* manually.

## Executive Summary

KAN-12 is the **persistence half of the asset pipeline** whose storage half landed in KAN-10
(`BlobStorage` port + `S3BlobStorageAdapter`). Today a caller can mint a presigned `PUT` target for
`BlobType.BRAND_IMAGE` and upload bytes to `brands/images/<32 hex>`, but **nothing records that the
blob exists**: `brand_images` is a bare `(id, brand_id, image_url)` table with no entity, no
repository and no lifecycle.

This story delivers a **foundation slice** (no HTTP endpoint, no application service):

1. `AssetStatus` — a shared domain enum (`PENDING`, `UPLOADED`, `DELETED`) that owns its legal
   transitions and is designed for reuse by `brand_videos`, `strain_images`, `product_images` and
   `dispensary_images`.
2. `BrandImage` — a JPA entity mapped to `brand_images`, extending `BaseEntity`, holding the
   **canonical blob key** (not a URL) and its `AssetStatus`.
3. `BrandImageRepository` + a Flyway migration that adds `status`, the audit columns, the
   `CHECK` constraint and the supporting indexes.

**Why the status field matters beyond this table**: a presigned upload is a two-phase commit across
two systems (S3 and Postgres) with no shared transaction. `PENDING` is the intent record written
before the client uploads; `UPLOADED` is the confirmation; `DELETED` is the tombstone that lets blob
removal be deferred and retried. Without it, every asset story re-invents orphan handling — exactly
the risk KAN-10 logged as open assumptions **8.3** (who calls `remove`, and when) and **8.4** (orphan
blobs have no reclamation).

Scope decisions confirmed with the requester:

| Decision | Choice |
|---|---|
| Slice size | **Foundation only** — entity + enum + migration + repository + tests. No endpoint, no service. |
| Enum | **`AssetStatus`** in `domain/models`, `@Enumerated(EnumType.STRING)` + DB `CHECK` constraint. |
| Delete model | **Status-only**: `DELETED` is the single delete marker. **No** `@SQLDelete` / `@SQLRestriction`. |
| Row pointer | **Canonical blob key** (`brands/images/<32 hex>`), column renamed `image_url` → `image_key`. |

---

## 1. User story

> *As a **backend developer building the brand-asset upload flow**,
> I want **`brand_images` rows to be persistable as a typed `BrandImage` entity carrying a shared
> `AssetStatus` (`PENDING` / `UPLOADED` / `DELETED`)**,
> so that **an uploaded brand image can be tracked from intent to confirmation to removal, and the
> same lifecycle can be reused verbatim by strain, product and video assets without redesign**.*

### Business value

- **Unblocks four queued asset stories** (`brand_images`, `brand_videos`, `strain_images`,
  `product_images`). KAN-10 delivered the storage port with zero consumers; this is its first
  consumer and the template for the rest.
- **Makes orphan blobs detectable and therefore billable-cost-controllable**: `PENDING` rows older
  than `aws.s3.presign-ttl` are provably abandoned uploads; without the field, the only way to find
  an orphan is a full bucket-vs-DB diff.
- **Prevents broken image links**: with a `DELETED` tombstone, the DB row can be retired first and
  the blob removed later (KAN-10 assumption 8.8 ordering), and the removal can be retried after a
  failure instead of being lost.
- **Amortises the design once**: the enum plus its transition rules is written and tested here and
  reused by ≥4 tables, so the marginal cost of the next asset story drops to entity + migration.

### Acceptance criteria

1. `AssetStatus` exists in `com.example.demo.domain.models` with exactly three constants in the
   order `PENDING`, `UPLOADED`, `DELETED`, and is usable by any resource (no brand-specific types in
   its API).
2. `AssetStatus` exposes its legal transitions: `PENDING → UPLOADED`, `PENDING → DELETED`,
   `UPLOADED → DELETED`. Every other transition — including any transition out of `DELETED` and any
   self-transition — is rejected.
3. `BrandImage` is mapped to `brand_images`, extends `BaseEntity`, has a `LAZY @ManyToOne Brand`,
   a non-null immutable `imageKey` and a non-null `status`.
4. A `BrandImage` can only be created through a factory that sets `status = PENDING` and rejects any
   `imageKey` that is not a canonical `BRAND_IMAGE` key (`brands/images/` + 32 lowercase hex chars).
5. `status` is persisted as its **name** (`'PENDING'`), never as an ordinal; the DB rejects any other
   value via a `CHECK` constraint.
6. `image_key` is unique across the table; inserting a duplicate key fails at the DB level.
7. The Flyway migration applies cleanly on a database already at `V0.1.0`, and Hibernate
   (`ddl-auto: none`) validates against the resulting schema — no schema drift.
8. No code path physically deletes a `brand_images` row: removal is represented solely by
   `status = DELETED`.
9. `docs/data-model.md` (section *Brand Images* + the ER diagram) reflects the new columns.

---

## 2. Scope

### In scope
- `AssetStatus` enum with transition rules and unit tests.
- `BrandImage` entity + factory + guarded transitions.
- `BrandImageRepository` with the three query methods listed in §4.4.
- Flyway migration `V0.1.1` on `brand_images`.
- A small DRY refactor of `BlobType` to expose a per-type canonical-key check (§4.1).
- Docs (`docs/data-model.md`, `docs/backend-standards.md`) and OpenSpec specs.

### Out of scope (follow-up tickets, listed in §12)
- Any HTTP endpoint (`POST /api/brands/{brandId}/images`, list, delete).
- Any application service or `PATCH`/confirm flow that performs the `PENDING → UPLOADED` transition.
- Applying `AssetStatus` to `brand_videos`, `strain_images`, `product_images`, `dispensary_images`.
- The orphan-reclamation sweeper and the blob-removal job that consumes `DELETED` rows.
- Ordering / primary-image flags for multiple brand images.
- Authentication/authorization (still absent from the whole codebase).

---

## 3. Data model and migration

### 3.1 Target shape of `brand_images`

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | `SERIAL` | no | PK, unchanged |
| `brand_id` | `int` | no | FK → `brands(id)`, unchanged |
| `image_key` | `varchar` | no | **renamed** from `image_url`; canonical blob key, unique, immutable |
| `status` | `varchar(16)` | no | `AssetStatus` name; `CHECK` constrained |
| `created_at` | `timestamp` | no | **new**, `DEFAULT now()` (mirrors `brands`) |
| `created_by` | `varchar` | yes | **new** |
| `modified_at` | `timestamp` | yes | **new** |
| `modified_by` | `varchar` | yes | **new** |
| `deleted_at` | `timestamp` | yes | **new**, mapped by `BaseEntity` but **not** the delete marker (see §8.3) |
| `deleted_by` | `varchar` | yes | **new** |

### 3.2 Migration — `flyway/release_0.1/V0.1.1__brand_images_asset_lifecycle.sql`

Flyway locations are `filesystem:flyway/release*` (Maven plugin) and `classpath:flyway/release*`
(tests, `pom.xml` copies the `flyway/` folder as a test resource), so a new file inside
`flyway/release_0.1/` is picked up by both without configuration changes.

```sql
-- KAN-12: brand image asset lifecycle
ALTER TABLE "brand_images" RENAME COLUMN "image_url" TO "image_key";

ALTER TABLE "brand_images"
    ADD COLUMN "status"      varchar(16) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN "created_at"  timestamp   NOT NULL DEFAULT (now()),
    ADD COLUMN "created_by"  varchar,
    ADD COLUMN "modified_at" timestamp,
    ADD COLUMN "modified_by" varchar,
    ADD COLUMN "deleted_at"  timestamp,
    ADD COLUMN "deleted_by"  varchar;

-- The table is empty and the entity always sets the status explicitly: drop the default so a
-- forgotten assignment fails loudly instead of silently landing as PENDING.
ALTER TABLE "brand_images" ALTER COLUMN "status" DROP DEFAULT;

ALTER TABLE "brand_images"
    ADD CONSTRAINT ck_brand_images_status
        CHECK ("status" IN ('PENDING', 'UPLOADED', 'DELETED'));

CREATE UNIQUE INDEX ux_brand_images_image_key ON "brand_images" ("image_key");
CREATE INDEX ix_brand_images_brand_id_status ON "brand_images" ("brand_id", "status");
CREATE INDEX ix_brand_images_pending_created_at
    ON "brand_images" ("created_at") WHERE "status" = 'PENDING';
```

Rationale per statement:

- **Rename, not a second column**: the value stored is a key, not a URL. Keeping the name `image_url`
  while storing `brands/images/9f2a…` would be a lie that every future reader pays for. Safe today:
  `brand_images` has **no seed rows** in `V0.1.0` and **no code reference** anywhere in `src/`.
- **`varchar(16)`** fits the longest constant (`UPLOADED`, 8 chars) with room for one future value;
  it also caps the column for the `CHECK`.
- **`CHECK` instead of a native Postgres `ENUM` type**: a shared `CREATE TYPE` would be DB-enforced
  across all asset tables, but `ALTER TYPE … ADD VALUE` cannot run in a transaction and values can
  never be removed. A `varchar` + `CHECK` is diffable, reversible and trivially extended.
- **`ux_brand_images_image_key`**: keys are 122-bit random and globally unique; a duplicate means a
  bug or a replay, and the DB must be the last line of defence.
- **`ix_brand_images_brand_id_status`**: Postgres does not index FKs automatically; every future
  "list the visible images of this brand" query filters on exactly this pair.
- **`ix_brand_images_pending_created_at` (partial)**: the orphan sweeper query
  (`status = 'PENDING' AND created_at < now() - ttl`) is the only scan that must stay cheap as the
  table grows; a partial index keeps it proportional to the orphan count, not the table size.

---

## 4. Files to create / modify

| # | File | Action | Layer |
|---|---|---|---|
| 1 | `domain/models/AssetStatus.java` | **Create** — shared lifecycle enum with transition rules | Domain |
| 2 | `domain/models/brand/BrandImage.java` | **Create** — `@Entity @Table(name = "brand_images")`, extends `BaseEntity` | Domain |
| 3 | `domain/repositories/BrandImageRepository.java` | **Create** — `JpaRepository<BrandImage, Long>` + 3 queries | Domain |
| 4 | `domain/models/BlobType.java` | **Modify** — extract per-type `isKeyOf(String)`; `isCanonicalKey` delegates to it (DRY, no behaviour change) | Domain |
| 5 | `flyway/release_0.1/V0.1.1__brand_images_asset_lifecycle.sql` | **Create** — §3.2 | DB |
| 6 | `test/.../domain/models/AssetStatusTests.java` | **Create** — §5.1 | Test |
| 7 | `test/.../domain/models/brand/BrandImageTests.java` | **Create** — §5.2 | Test |
| 8 | `test/.../domain/models/BlobTypeTests.java` | **Modify** — add `isKeyOf` cases | Test |
| 9 | `test/.../integration/repositories/BrandImageRepositoryTests.java` | **Create** — §5.3 | Test |
| 10 | `docs/data-model.md` | **Modify** — *Brand Images* section + `BRAND_IMAGES` ER block | Docs |
| 11 | `docs/backend-standards.md` | **Modify** — register the enum-persistence convention (§7 *Maintainability*) | Docs |
| 12 | `openspec/specs/asset-lifecycle/spec.md` | **Create** — new capability (§10) | OpenSpec |
| 13 | `openspec/specs/brands-management/spec.md` | **Modify** — add the brand-image persistence requirement (§10) | OpenSpec |
| 14 | `openspec/specs/blob-storage/spec.md` | **Modify** — per-blob-type canonical-key check (§10) | OpenSpec |

> **No service, controller, DTO or `GlobalExceptionHandler` change** — this slice stops at the domain
> boundary on purpose.

---

## 5. Implementation details

### 5.1 `AssetStatus` (shared, reusable)

```java
package com.example.demo.domain.models;

/**
 * Lifecycle of a persisted asset reference pointing at a blob in object storage. Shared by every
 * asset table (brand images and videos, strain images, product images, dispensary images): the enum
 * MUST stay free of resource-specific concepts.
 *
 * <p>PENDING  — the row was written before the client uploaded to its presigned target.
 * <p>UPLOADED — the upload was confirmed; the asset is the only state considered visible.
 * <p>DELETED  — tombstone; the blob may still exist and is removed asynchronously. Terminal.
 */
public enum AssetStatus {
  PENDING,
  UPLOADED,
  DELETED;

  /** True when this status may legally move to {@code target}. Self-transitions are rejected. */
  public boolean canTransitionTo(AssetStatus target) {
    if (target == null) {
      return false;
    }
    return switch (this) {
      case PENDING -> target == UPLOADED || target == DELETED;
      case UPLOADED -> target == DELETED;
      case DELETED -> false;
    };
  }

  /** True when no transition out of this status exists. */
  public boolean isTerminal() {
    return this == DELETED;
  }

  /** True when an asset in this status may be exposed to clients. */
  public boolean isVisible() {
    return this == UPLOADED;
  }
}
```

Design notes:
- The transition table lives **in the enum**, exactly as `BlobType` owns its key prefixes: one source
  of truth, reused for free by strain/product assets (DRY + the KAN-10 precedent in
  `docs/backend-standards.md` → *Storage Ports*).
- A Java 21 exhaustive `switch` expression (no `default`) makes adding a fourth constant a
  **compile error** rather than a silent fall-through.
- **Constant names are a persistence contract** (`EnumType.STRING`): renaming a constant is a data
  migration. This is asserted by a test (§5.1) and stated in the spec.
- `isVisible()` is deliberately included now so read endpoints across all asset stories express
  visibility identically instead of each hardcoding `status == UPLOADED`.

### 5.2 `BrandImage` entity

```java
package com.example.demo.domain.models.brand;

@Entity
@Table(name = "brand_images")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
public class BrandImage extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @EqualsAndHashCode.Include
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "brand_id", nullable = false)
  private Brand brand;

  @Setter(AccessLevel.NONE)
  @Column(name = "image_key", nullable = false, updatable = false, unique = true)
  private String imageKey;

  @Setter(AccessLevel.NONE)
  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 16)
  private AssetStatus status;

  /** Sole sanctioned creation path: a not-yet-uploaded image for a canonical BRAND_IMAGE key. */
  public static BrandImage pending(Brand brand, String imageKey) {
    if (brand == null || brand.getId() == null) {
      throw new IllegalArgumentException("brand must be a persisted brand");
    }
    if (!BlobType.BRAND_IMAGE.isKeyOf(imageKey)) {
      throw new IllegalArgumentException("imageKey is not a canonical BRAND_IMAGE key");
    }
    BrandImage image = new BrandImage();
    image.brand = brand;
    image.imageKey = imageKey;
    image.status = AssetStatus.PENDING;
    return image;
  }

  public void markUploaded() {
    transitionTo(AssetStatus.UPLOADED);
  }

  public void markDeleted() {
    transitionTo(AssetStatus.DELETED);
  }

  private void transitionTo(AssetStatus target) {
    if (!this.status.canTransitionTo(target)) {
      throw new IllegalStateException(
          "Illegal brand image transition: " + this.status + " -> " + target);
    }
    this.status = target;
  }
}
```

Design notes:
- **`@Setter(AccessLevel.NONE)` on `imageKey` and `status`** is the point of the whole entity: an
  open `setStatus` would let any caller write `PENDING → UPLOADED → PENDING` and defeat the enum's
  rules. State changes go through `markUploaded()` / `markDeleted()` only (DDD: the entity protects
  its own invariants).
- **No `@SQLDelete` / `@SQLRestriction`** — per the agreed delete model, `status = DELETED` is the
  single delete marker. Two competing "is it deleted?" flags would inevitably drift.
- **`updatable = false` on `image_key`** — the key is the identity of the blob; changing it would
  orphan the object silently.
- **`@Builder` is kept** for test arrangement (and Lombok consistency with `Brand`/`Product`) but the
  production path is `BrandImage.pending(...)`. Reviewer note: if the team prefers zero back doors,
  drop `@Builder`/`@AllArgsConstructor` and build fixtures through the factory + `markUploaded()`.
- The image validates its key against `BlobType.BRAND_IMAGE`, so a `STRAIN_IMAGE` key can never be
  filed under a brand — a class of bug the `varchar` column alone cannot prevent.

### 5.3 `BlobType` DRY refactor (behaviour-preserving)

`isCanonicalKey` already encodes "known prefix + 32 hex". `BrandImage` needs the same check scoped to
one type. Extract instead of copying the regex a second time:

```java
  /** True when {@code key} is canonical and owned by this blob type. */
  public boolean isKeyOf(String key) {
    return key != null
        && key.startsWith(this.prefix)
        && RANDOM_PART.matcher(key.substring(this.prefix.length())).matches();
  }

  public static boolean isCanonicalKey(String key) {
    if (key == null) {
      return false;
    }
    return Arrays.stream(values()).anyMatch(type -> type.isKeyOf(key));
  }
```

The existing `BlobTypeTests` must keep passing unchanged — that is the regression proof.

### 5.4 `BrandImageRepository`

```java
@Repository
public interface BrandImageRepository extends JpaRepository<BrandImage, Long> {

  List<BrandImage> findByBrandIdAndStatus(Long brandId, AssetStatus status);

  Optional<BrandImage> findByImageKey(String imageKey);

  boolean existsByImageKey(String imageKey);
}
```

- `findByBrandIdAndStatus` resolves through the `brand.id` property path and is served by
  `ix_brand_images_brand_id_status`.
- `JpaRepository` is kept for consistency with `BrandRepository`/`ProductRepository`, which means
  `delete(...)` is technically reachable. The **invariant "never physically delete"** is therefore
  enforced by (a) the OpenSpec requirement, (b) review, and (c) the absence of any caller in this
  slice. Reviewer note: a narrower `Repository<BrandImage, Long>` interface (ISP) would make it
  structurally impossible; raised as follow-up F6 rather than diverging from project convention here.

---

## 6. Testing plan (TDD — failing test first)

Naming `should_[expected_behavior]_when_[condition]`, Arrange/Act/Assert, coverage ≥ 90%.

### 6.1 `domain/models/AssetStatusTests.java`
1. `should_exposeExactlyThreeStatuses_when_valuesAreInspected` — guards the enum against silent growth.
2. `should_keepConstantNames_when_persistedAsStrings` — asserts the literal names `PENDING`/`UPLOADED`/`DELETED` (the DB contract; a rename must break a test, not production data).
3. `should_allowUploadedAndDeleted_when_currentStatusIsPending` (parameterized).
4. `should_allowOnlyDeleted_when_currentStatusIsUploaded`.
5. `should_rejectEveryTransition_when_currentStatusIsDeleted`.
6. `should_rejectSelfTransition_when_targetEqualsCurrentStatus` (parameterized over all values).
7. `should_rejectTransition_when_targetIsNull`.
8. `should_reportTerminal_when_statusIsDeleted` / `should_reportNotTerminal_when_statusIsPendingOrUploaded`.
9. `should_reportVisible_when_statusIsUploaded` and not visible otherwise.

### 6.2 `domain/models/brand/BrandImageTests.java`
1. `should_createPendingImage_when_keyIsCanonicalBrandImageKey`.
2. `should_rejectCreation_when_keyBelongsToAnotherBlobType` (`strains/images/<32 hex>`).
3. `should_rejectCreation_when_keyIsNullBlankOrNonCanonical` (`@NullAndEmptySource` + bad randoms + path traversal `brands/images/../x`).
4. `should_rejectCreation_when_brandIsNullOrNotPersisted`.
5. `should_markUploaded_when_statusIsPending`.
6. `should_markDeleted_when_statusIsPending` and `..._when_statusIsUploaded`.
7. `should_throwIllegalState_when_markUploadedIsCalledOnDeletedImage`.
8. `should_throwIllegalState_when_markUploadedIsCalledTwice`.
9. `should_notExposeSetters_when_statusOrImageKeyAreInspected` — reflection assertion that no public `setStatus`/`setImageKey` exists (protects the invariant against a future Lombok edit).

### 6.3 `domain/models/BlobTypeTests.java` (extend)
1. `should_recogniseOwnKey_when_keyCarriesTheSamePrefix` (parameterized over all six types).
2. `should_rejectKey_when_keyBelongsToAnotherBlobType`.
3. Existing `isCanonicalKey` tests must pass untouched.

### 6.4 `integration/repositories/BrandImageRepositoryTests.java` (extends the `RepositoryTest` base, real Postgres + Flyway)
1. `should_persistPendingImage_when_imageIsSaved` — reload and assert `status = PENDING`, `createdAt` not null.
2. `should_storeStatusAsString_when_rowIsReadWithNativeQuery` — `SELECT status FROM brand_images` returns `'PENDING'`, proving `EnumType.STRING` (an ordinal mapping would return `0`).
3. `should_rejectRow_when_statusValueIsNotInTheCheckConstraint` — native `INSERT … 'ARCHIVED'` → `DataIntegrityViolationException`.
4. `should_rejectRow_when_imageKeyIsDuplicated` — unique index violation.
5. `should_rejectRow_when_brandIdDoesNotExist` — FK violation.
6. `should_rejectRow_when_statusIsNull` — `NOT NULL` violation (and no DB default silently rescuing it).
7. `should_returnOnlyUploadedImages_when_findByBrandIdAndStatusIsCalled` — fixture with one row per status.
8. `should_findImage_when_searchedByImageKey` / `should_returnEmpty_when_keyIsUnknown`.
9. `should_persistUploadedStatus_when_markUploadedIsFlushed` — transition survives a round trip and `modifiedAt` is updated by `BaseEntity`.
10. `should_keepRow_when_statusIsDeleted` — after `markDeleted()` + flush, the row is still present (proof that `DELETED` is a tombstone, not a removal).

### 6.5 Migration verification
- The Flyway migration is exercised implicitly by every `@DataJpaTest` (test `application.yml` runs
  Flyway against `classpath:flyway/release*`), so a broken migration fails the whole repository suite.
- Add an explicit `should_matchEntityMapping_when_schemaIsValidated` check by running the repository
  suite with `spring.jpa.hibernate.ddl-auto=validate` (property override on the test class) to catch
  column-name drift between the entity and the migration.
- Manual gate before merge: `mvn flyway:info` shows `V0.1.1` pending, then `mvn flyway:migrate`
  applies it on a database already at `V0.1.0` (rename + add-column path, not a fresh create).

---

## 7. Definition of Done

1. `AssetStatus`, `BrandImage`, `BrandImageRepository` created; `BlobType` refactored with no
   behaviour change.
2. `V0.1.1__brand_images_asset_lifecycle.sql` applies cleanly on top of `V0.1.0` and on a fresh DB;
   Hibernate `validate` passes against the result.
3. All tests in §6 pass; `mvn verify` green; coverage ≥ 90% lines/branches on the new classes; 100%
   of the legal/illegal transition matrix covered.
4. No physical-delete path exists for `brand_images`; no `@SQLDelete`/`@SQLRestriction` on
   `BrandImage`.
5. `status` verified as a string in the database, not an ordinal, with the `CHECK` constraint active.
6. `docs/data-model.md` *Brand Images* section and ER diagram updated (`image_key`, `status`, audit
   columns); `docs/backend-standards.md` records the enum-persistence convention
   (`@Enumerated(EnumType.STRING)` + `varchar` + `CHECK`, never ordinals, never native PG enums).
7. OpenSpec: `asset-lifecycle` capability created; `brands-management` and `blob-storage` specs
   updated per §10.
8. Spotless, SpotBugs, Modernizer, duplicate-finder and the license-header check all pass;
   English-only artifacts; DDD layering respected (no framework leakage into `AssetStatus`).
9. Branch suffixed `-backend`, conventional commits, follow-up tickets in §12 created in Jira.

---

## 8. Non-functional requirements

### Security
- **No presigned URL is ever persisted.** Only the opaque key is stored: URLs are bearer
  capabilities (KAN-10 rule) and a stored URL would be a long-lived credential in the database.
- **Key provenance is validated in the domain** (`BlobType.BRAND_IMAGE.isKeyOf`), so caller-supplied
  strings can never introduce path traversal (`brands/images/../../etc`) or cross-resource keys.
- Audit columns are never accepted from outside: `BaseEntity` + `AuditingEntityListener` own them.
- `DELETED` is a tombstone, so asset removal is auditable rather than a vanished row.
- No new logging of keys beyond KAN-10's discipline; **never** log a presigned URL at any level.

### Performance
- Every association is `LAZY`; no inverse collection is added to `Brand` (avoids the N+1 and the
  unbounded-collection load that an `@OneToMany` on the aggregate would invite).
- Writes are single-row inserts/updates by PK. Reads are index-backed:
  `(brand_id, status)` for listings, unique `image_key` for lookups, partial `PENDING` index for the
  future sweeper. Target: p95 < 20 ms for repository operations.
- `varchar(16)` + `CHECK` costs one bounded comparison per write; negligible versus an FK lookup.

### Observability (prepared here, consumed by the follow-ups)
- The status field makes three operational metrics possible and they should be defined now:
  `brand_images.pending_older_than_ttl` (orphan candidates), `brand_images.deleted_awaiting_blob_removal`,
  and the `PENDING → UPLOADED` conversion rate.

### Maintainability / reuse
- Adding the lifecycle to `brand_videos`, `strain_images`, `product_images` or `dispensary_images`
  must require **only** an entity + a migration — no change to `AssetStatus`. If the second asset
  table needs a different set of states, this design is wrong and must be revisited before the third.
- The duplicated `(status, audit columns, CHECK, indexes)` migration block across asset tables is a
  known upcoming repetition: once the second asset table lands, extract a repeatable Flyway
  script or a shared `AssetEntity` mapped superclass (flagged, not built — see F5).

---

## 9. Critical assumptions to validate

1. **Renaming `image_url` → `image_key` is safe.** Verified: `V0.1.0` contains no `INSERT` into
   `brand_images` and no `src/` reference exists. *Must be re-confirmed if any environment was seeded
   manually.* If not acceptable, fall back to keeping `image_url` and documenting that it stores a key.
2. **`brand_images` stores keys, not URLs, and the public URL is derived at read time** from
   `aws.s3.bucket` + endpoint/CDN. The read story must own that derivation — `BlobStorage` currently
   has **no** `urlFor(key)` operation, so the read endpoint will need one (follow-up F3).
3. **`deleted_at` / `deleted_by` columns exist only because `BaseEntity` maps them.** They are
   deliberately unused here (status is the delete marker) and `BaseEntity.@PreRemove` would only fire
   on a physical removal that must never happen. Confirm the team accepts the redundancy, or split
   `BaseEntity` into `AuditableEntity` + `SoftDeletableEntity` (follow-up F6).
4. **Brand soft-delete does not cascade to its images in this slice.** Soft-deleting a `Brand` leaves
   its images `UPLOADED`, and `Brand` has `@SQLRestriction`, so `brandImage.getBrand()` on an orphaned
   image will behave unexpectedly (lazy proxy over a filtered row). This is KAN-10 assumption 8.3
   resurfacing and needs a product decision before the delete endpoint story.
5. **Who writes `PENDING` and who confirms `UPLOADED`?** Two viable models: (a) client confirms via a
   `PATCH` call, (b) the S3 → SQS object-created notification confirms server-side. The notification
   queue currently filters on `products/` only, so brand images would need a new filter or a broader
   subscription. This choice drives the next story's shape and must be decided at refinement.
6. **No authentication exists in the codebase**, so nothing prevents an unauthenticated caller from
   later minting upload targets and rows. An auth story must precede production exposure (inherited
   from KAN-8/KAN-10).
7. **Three states are enough.** Deliberately excluded: `FAILED` (upload rejected/virus-scanned),
   `EXPIRED` (presign lapsed without upload — derivable from `PENDING` + `created_at`), and
   `REMOVED` (blob physically gone vs row tombstoned). If any of these is required by product within
   the next two asset stories, add it now: adding a state later means touching every asset table's
   `CHECK` constraint.
8. **`DELETED` vs blob removal are separate events.** The blob still exists after the tombstone;
   nothing in this slice calls `BlobStorage.remove`. Storage cost keeps accruing until F4 ships.
9. **No ordering/primary-image concept** for multiple brand images; the read story will return an
   arbitrary order unless a follow-up adds a position column.

---

## 10. Proposed OpenSpec artifacts

### 10.1 New capability — `openspec/specs/asset-lifecycle/spec.md`

## Purpose

Provides the shared lifecycle of any persisted asset reference pointing at a blob in object storage,
so brand, strain, product and dispensary assets track intent, confirmation and removal identically.

### Requirement: Shared asset lifecycle states

The system SHALL define exactly three asset lifecycle states — `PENDING`, `UPLOADED` and `DELETED` —
in a single shared domain type reusable by every asset resource. `PENDING` SHALL mean the asset
reference was recorded before its blob upload was confirmed. `UPLOADED` SHALL mean the upload was
confirmed and SHALL be the only state considered visible to clients. `DELETED` SHALL mean the asset
reference is retired while its blob may still exist. The shared type SHALL NOT contain any
resource-specific concept.

#### Scenario: The lifecycle is reused by a new asset resource
- **WHEN** a new asset resource adopts the lifecycle
- **THEN** it SHALL reuse the shared states and transition rules without modifying them
- **AND** only an entity mapping and a database migration SHALL be required

#### Scenario: Only uploaded assets are visible
- **WHEN** an asset is in `PENDING` or `DELETED`
- **THEN** the system SHALL NOT consider it visible to clients

### Requirement: Legal asset lifecycle transitions

The system SHALL permit exactly the transitions `PENDING → UPLOADED`, `PENDING → DELETED` and
`UPLOADED → DELETED`. `DELETED` SHALL be terminal. The system SHALL reject every other transition,
including any self-transition and any transition to an undefined target.

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

The system SHALL persist the lifecycle state as its textual name and SHALL NOT persist it as an
ordinal position. The database SHALL reject any value outside the three defined names. The state
names SHALL be treated as a persistence contract: renaming a state SHALL require a data migration.

#### Scenario: State stored as text
- **WHEN** an asset row is read directly from the database
- **THEN** its lifecycle column SHALL contain the state name

#### Scenario: Unknown state rejected by the database
- **WHEN** a row is written with a lifecycle value outside the three defined names
- **THEN** the database SHALL reject the write

### 10.2 Modified capability — `brands-management` (new requirement)

### Requirement: Brand image persistence and lifecycle

The system SHALL map brand images to the existing `brand_images` table as a first-class entity that
extends the shared audit base type and references its brand. Each brand image SHALL store the
canonical blob key issued by the blob-storage port for the `BRAND_IMAGE` blob type, SHALL NOT store a
presigned URL, and SHALL carry a shared asset lifecycle state. A brand image SHALL be created only in
the `PENDING` state. Its blob key SHALL be immutable and unique across all brand images. The system
MUST NOT physically delete brand image rows: removal SHALL be represented solely by the `DELETED`
lifecycle state.

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

### 10.3 Modified capability — `blob-storage` (extended requirement)

Extend *Requirement: Blob type to key prefix mapping* with:

The system SHALL additionally expose a per-blob-type canonical-key check that is true only when a key
is canonical **and** carries that blob type's prefix, derived from the same prefix definitions as the
global canonical-key predicate.

#### Scenario: Key ownership is distinguishable per blob type
- **WHEN** a canonical key for one blob type is checked against a different blob type
- **THEN** the system SHALL report that the key does not belong to that blob type
- **AND** the global canonical-key predicate SHALL still report the key as canonical

---

## 11. Success metrics

| Metric | Target |
|---|---|
| Coverage of the new domain classes | ≥ 90% lines/branches |
| Transition matrix coverage (3 states × 4 targets incl. null) | 100% asserted |
| Schema drift incidents (`ddl-auto: validate`) | 0 |
| Repository operation latency p95 | < 20 ms |
| Extra code needed to reuse the lifecycle on the next asset table | entity + migration only, 0 changes to `AssetStatus` |
| Rows with an invalid lifecycle value in any environment | 0 (DB-enforced) |
| Presigned URLs persisted in the database | 0 |

---

## 12. Suggested task breakdown (baby steps, TDD)

1. `AssetStatusTests` (red) → `AssetStatus` with transitions, `isTerminal`, `isVisible` (green).
2. `BlobTypeTests` extension (red) → extract `BlobType.isKeyOf`, delegate `isCanonicalKey` (green;
   existing tests must stay untouched).
3. Flyway `V0.1.1` migration; run the existing repository suite to prove it applies.
4. `BrandImageTests` (red) → `BrandImage` entity, factory and guarded transitions (green).
5. `BrandImageRepositoryTests` (red) → `BrandImageRepository` (green), including the native-query
   string/`CHECK`/unique/FK assertions.
6. Hibernate `validate` run to confirm mapping ↔ schema parity.
7. Docs (`data-model.md`, `backend-standards.md`) and OpenSpec specs (§10).

## 13. Follow-up tickets to create

| Id | Ticket | Why it is not in KAN-12 |
|---|---|---|
| F1 | `POST /api/brands/{brandId}/images` — issue presigned target + persist `PENDING` row | Needs the API/auth decision and assumption 9.5 resolved |
| F2 | Confirm upload (`PATCH` vs S3→SQS notification) — `PENDING → UPLOADED` | Depends on the confirmation-model decision (9.5); SQS filter is `products/`-only today |
| F3 | Public URL derivation for stored keys (`BlobStorage.urlFor(key)` or CDN base property) | The port has no read-URL operation (9.2) |
| F4 | Blob-removal job consuming `DELETED` rows + orphan sweeper for stale `PENDING` rows | Closes KAN-10 assumptions 8.3/8.4/8.8; needs the partial index shipped here |
| F5 | Roll the lifecycle out to `brand_videos`, `strain_images`, `product_images`, `dispensary_images` (+ shared mapped superclass / repeatable migration if the block repeats) | Out of the agreed slice; DRY extraction decided at the second occurrence |
| F6 | Split `BaseEntity` into `AuditableEntity` + `SoftDeletableEntity`; consider a narrowed `Repository` for append-only tables | Cross-cutting refactor touching `Brand`, `Product`, `Dispensary` (9.3, §5.4) |
| F7 | Upload hardening (size/content-type policy) and bucket public-read/CORS hardening | Inherited open risks from KAN-10/KAN-14 |
