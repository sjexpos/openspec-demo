# Code Review Report — kan-12-brandimage-asset-lifecycle

**Change**: `kan-12-brandimage-asset-lifecycle` (branch `feat/kan-12-brandimage-asset-lifecycle`)
**Date**: 2026-09-21
**Reviewer role**: `code-review` phase sub-agent (review only, no code changed)
**Mode**: adversarial red-team pass over the diff vs specs/design/verify artifacts
**Skills applied**: `code-auditing`, `adversarial-review`, `solid-principles`, `dry-principle`, `java-jpa-hibernate`

**Diff scope reviewed**:
- New: `src/main/java/com/example/demo/domain/models/AssetStatus.java`,
  `src/main/java/com/example/demo/domain/models/brand/BrandImage.java`,
  `src/main/java/com/example/demo/domain/repositories/BrandImageRepository.java`,
  `flyway/release_0.1/V0.1.1__brand_images_asset_lifecycle.sql`,
  tests `AssetStatusTests` (17), `BrandImageTests` (22), `BrandImageRepositoryTests` (11)
- Modified: `domain/models/BlobType.java` (`isKeyOf` extraction + delegation),
  `BlobTypeTests` (add-only extension), `docs/data-model.md`, `docs/backend-standards.md`
- Spec/design/verify sources: `proposal.md`, `specs/*/spec.md` (asset-lifecycle, brands-management,
  blob-storage), `design.md` (D1–D8), `tasks.md`, `verify-report.md` (verdict PASS WITH WARNINGS,
  179/179 unit green, Spotless/SpotBugs/Modernizer pass)

## Adversarial review

**Scope**: `kan-12-brandimage-asset-lifecycle` (domain-only slice, no endpoint/service/DTO)
**Sources**: `openspec/changes/kan-12-brandimage-asset-lifecycle/proposal.md`,
`openspec/changes/kan-12-brandimage-asset-lifecycle/specs/{asset-lifecycle,brands-management,blob-storage}/spec.md`,
`openspec/changes/kan-12-brandimage-asset-lifecycle/design.md`,
`openspec/changes/kan-12-brandimage-asset-lifecycle/tasks.md`,
`openspec/changes/kan-12-brandimage-asset-lifecycle/verify-report.md`,
diff `feat/kan-12-brandimage-asset-lifecycle` vs main (`git status`/`git diff HEAD`)

### Spec and task alignment

- `AssetStatus` implements exactly the specified contract: three constants in order
  (`AssetStatus.java:27-30`), exhaustive `switch` with no `default` (`:41-45`), null → false,
  `isTerminal` = DELETED only, `isVisible` = UPLOADED only. Zero framework imports. PASS.
- `BrandImage` matches design D3: `brand_images` table, `BaseEntity` audit base, LAZY `@ManyToOne`
  (`BrandImage.java:65-66`), immutable unique key (`updatable=false, unique=true`, `AccessLevel.NONE`
  setter, `:69-71`), STRING-mapped status with no public setter (`:73-76`), `pending()` factory
  rejecting transient brand and non-`BRAND_IMAGE` keys (`:82-90`), guarded `markUploaded`/`markDeleted`
  (`:93-108`). No `@SQLDelete`/`@SQLRestriction`, no inverse collection on `Brand`, no URL field. PASS.
- `BrandImageRepository` exposes exactly the three specified derived queries, no `@Query`, no added
  delete methods (`BrandImageRepository.java:34-38`). PASS with tracked caveat W2.
- `BlobType.isKeyOf` is a behaviour-preserving extraction: same `prefix` + `RANDOM_PART` constants,
  null-safe, `startsWith` guard makes the `substring` structurally safe (no underflow possible),
  global `isCanonicalKey` delegates via `anyMatch(isKeyOf)` (`BlobType.java:53-73`). Pre-existing
  `isCanonicalKey` cases untouched in diff. PASS.
- Migration V0.1.1 performs exactly the designed statements: rename `image_url` → `image_key`,
  `status varchar(16) NOT NULL` with backfill default then `DROP DEFAULT`, CHECK over exactly the
  three names, six `BaseEntity` audit columns, three indexes (unique `image_key`, composite
  `(brand_id, status)`, partial `PENDING`) (`V0.1.1:6-24`). Safe on this table: V0.1.0 seeds no
  `brand_images` rows and no `src/` reference exists, so rename + non-concurrent index builds on an
  empty table are safe. PASS with tracked caveat W1 (pre-existing `id` drift, untouched by V0.1.1).
- Security posture holds: no presigned URL column/field anywhere in new artifacts (only javadoc
  mentions of "never a presigned URL", which is the prohibition statement itself); traversal,
  truncated, uppercase, blank, and cross-type keys rejected by `isKeyOf` regex + asserted in tests;
  DB CHECK/unique/FK/NOT NULL backstops proven by native-query repository tests. PASS.
- Test quality: naming `should_*_when_*` + AAA followed throughout; 3x4 transition matrix incl. null
  asserted 100%; DB-level proofs (native string/CHECK/unique/FK/tombstone) present. One weak test
  found (M1, tautological brand assertion). English-only holds in all new artifacts (ASCII-verified);
  remaining `image_url` mentions in `data-model.md` belong to other tables (dispensaries, strains,
  products, etc.); remaining Spanish ER labels are pre-existing and out of scope. No
  presentation/service/DTO changes (`git status` confirms).

### Findings

| Severity | Area | Finding | Evidence | Suggested fix (code / spec / tests) |
|----------|------|---------|----------|--------------------------------------|
| WARNING (tracked, pre-existing) | Migration / validate parity | W1 — `ddl-auto: validate` drift on `brand_images.id` (SERIAL vs bigint) is pre-existing and table-generic, not introduced by V0.1.1 which does not touch `id` | `flyway/release_0.1/V0.1.0__*.sql:104-108` (`SERIAL` PK) vs `BrandImage.java:61-63` (`Long`/`IDENTITY`); corroborated by verify-report pristine-tree run | No code change in this slice; resolve codebase-wide (align V0.1.0 PK types or document the convention) before it blocks a future `validate` gate |
| WARNING (design-accepted) | DDD / deletion invariant | W2 — `JpaRepository.delete*()` remains technically reachable on `BrandImageRepository`; the "never physically delete" invariant rests on spec + review + zero callers | `BrandImageRepository.java:32` extends `JpaRepository`; grep confirms no delete methods added and no callers | Track with follow-up F6 (narrowed `Repository` interface); no action before archive |
| WARNING (pre-existing pattern) | Cascade semantics | W3 — Brand soft-delete does not cascade to images; `Brand` carries `@SQLDelete`/`@SQLRestriction` while `BrandImage` has neither, so a lazy proxy over a soft-deleted brand can misbehave at the future read/delete endpoint | `Brand.java:47-48`; no cascade in `BrandImage.java:65-67`; design §9.4 defers by design | Product decision required before the delete-endpoint story; no action before archive |
| Minor | Test quality | M1 — Tautological brand assertion in `BrandImageTests`: `assertThat(image.getBrand()).isSameAs(image.getBrand())` compares the getter to itself and always passes, so the brand association is never actually asserted at unit level | `BrandImageTests.java:50` | Tests: assert against the fixture, e.g. `assertThat(image.getBrand()).isSameAs(brand)` with a named `persistedBrand()` local; FK coverage in `BrandImageRepositoryTests` already backstops persistence, so this is a unit-test precision fix, post-archive eligible |
| Minor | Robustness | M2 — `transitionTo` dereferences `this.status` without a null guard, so a `BrandImage` assembled via the Lombok `@Builder` backdoor (no status) throws `NullPointerException` instead of the documented `IllegalStateException` on `markUploaded`/`markDeleted` | `BrandImage.java:102-108` (`this.status.canTransitionTo(target)`); builder path exists at `:57`; null-status persistence is DB-rejected (`BrandImageRepositoryTests.should_rejectWrite_when_statusIsNull`) but the in-memory path is unguarded | Code (post-archive, with F6/S1): null-guard in `transitionTo` or remove `@Builder`/restrict construction to `pending()`; explicitly a follow-up, not an archive blocker |
| Suggestion | API surface | S1 — Class-level `@Setter` leaves `setBrand`/`setId` public, so the brand association and identifier are reassignable in memory even though the key/status backdoors are closed (`AccessLevel.NONE`) and the key column is `updatable=false` at the DB level | `BrandImage.java:53` (`@Setter`) vs `:69-76` (guarded fields) | Code (post-archive, with F6): narrow to field-level setters or `AccessLevel.NONE` on `brand`/`id`; production path unaffected today |
| Suggestion | JPA equality | S2 — `@EqualsAndHashCode(callSuper=true)` with `@Include` on `id` only follows the existing `Brand` convention, but `BaseEntity` defines no equality, so two transient (null-`id`) instances compare equal — the standard JPA/Lombok pitfall, consistent codebase-wide | `BrandImage.java:54-63`, `Brand.java:43-54`, `BaseEntity.java` (no equals) | Docs/code (post-archive): adopt an explicit equality policy or document the convention; no behavioural evidence of harm in this slice |
| Suggestion | Docs hygiene | S3 — Pre-existing Spanish ER relationship labels violate the English-only rule but exist on the pristine tree and are untouched by this diff | `docs/data-model.md:789,796,811,824` (`tiene_imagenes`) | Docs sweep outside this change; out of scope |

### Verdict

**PASS WITH GAPS** — no Blocker, no Major, no CRITICAL. All spec requirements trace to implementation
with test proof; quality gates green per verify-report (179/179 unit, Spotless/SpotBugs/Modernizer,
100% hand-written line coverage on new classes). W1–W3 are pre-existing or design-accepted risks that
travel with the change; M1–M2 are non-blocking test/robustness gaps; S1–S3 are informational
follow-ups. Archiving is advisable; no further verify cycle required unless review feedback is
implemented as behaviour changes.

### Recommended next steps (before archive)

- Carry W1–W3 + S1 into the archive record as tracked follow-ups (F6 narrow-repository/builder work,
  brand-cascade product decision, codebase-wide SERIAL/bigint convention).
- Optionally fix M1 (one-line test assertion) in the next change; do not hold the archive for it.
- Run `opsx-archive` for `kan-12-brandimage-asset-lifecycle`.

## Review checklists applied (summary)

- **Code-auditing**: dead code (none — every new method/constant is exercised), smells (none beyond
  S1/S2 conventions), security (no secrets/URLs, input validation at domain + DB), performance (LAZY,
  indexed reads, no N+1 surface: no collections, single-row PK ops), error handling (factory
  `IllegalArgumentException`, transitions `IllegalStateException`, DB backstops for bypass paths).
- **SOLID**: SRP (transition table lives in the enum; entity guards its own invariants; repository is
  a pure port), OCP (closed 3-state contract with compile-error growth via exhaustive switch +
  constant-count test — deliberate and documented in D2), LSP/ISP (no inheritance beyond the
  `BaseEntity` convention; repository keeps project `JpaRepository` convention with F6 narrowing
  tracked), DIP (domain types have zero Spring/JPA leakage in `AssetStatus`; `BrandImage` depends on
  JPA abstractions only, consistent with `Brand`).
- **DRY**: `isKeyOf` extraction is genuine same-concept reuse (prefix + regex single source of truth),
  not coincidental similarity; the deferred migration-fragment extraction (F5) correctly waits for the
  second asset table per the Rule of Three.
- **JPA/Hibernate**: LAZY `@ManyToOne`, no inverse `@OneToMany` (no N+1/unbounded loads), `STRING`
  enum mapping + bounded `varchar` + CHECK (never ordinals/native enums), `updatable=false` immutable
  key, auditing via `BaseEntity` listener, `validate`-parity intent (modulo pre-existing W1 drift).
