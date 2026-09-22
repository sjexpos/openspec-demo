# Verification Report — kan-12-brandimage-asset-lifecycle

**Change**: `kan-12-brandimage-asset-lifecycle` (branch `feat/kan-12-brandimage-asset-lifecycle`)
**Mode**: verify (pre-archive, domain-only slice; no production code written by verifier)
**Date**: 2026-09-21
**Verifier inputs**: `specs/asset-lifecycle/spec.md`, `specs/brands-management/spec.md`,
`specs/blob-storage/spec.md`, `tasks.md`, `design.md`, `apply-progress.md`,
`reports/2026-09-21-step-8-unit-test-and-db-verification.md`,
`reports/2026-09-21-step-9-manual-curl-verification.md`, context `tmp/KAN-12-enriched-us-opus.md` §7 (DoD).

## 1. Completeness (tasks.md vs. implementation)

| Task | Claimed | Verifier finding |
|---|---|---|
| 0.1 Feature branch | done | Confirmed: current branch is `feat/kan-12-brandimage-asset-lifecycle`. |
| 1.1/1.2 `AssetStatus` + `AssetStatusTests` (17 tests) | done | `src/main/.../domain/models/AssetStatus.java` exists; `AssetStatusTests.java` exists; re-run 17/17 green. |
| 2.1/2.2 `BlobType.isKeyOf` + extended `BlobTypeTests` | done | `BlobType.java:53-61` adds `isKeyOf`; `isCanonicalKey` delegates (`BlobType.java:68-73`); pre-existing cases untouched in diff (add-only); re-run 49/49 green. |
| 3.1 Migration V0.1.1 | done | `flyway/release_0.1/V0.1.1__brand_images_asset_lifecycle.sql` (24 lines): rename, `status varchar(16)` NOT NULL with backfill default dropped, CHECK over exactly the three names, 6 audit columns, 3 indexes. |
| 3.2 Migration proven by suite | done | Integration run applies V0.1.1 on the test database (11/11 green); WARN-level constraint logs observed are the expected negative-path proofs. |
| 4.1/4.2 `BrandImage` + `BrandImageTests` (22 tests) | done | `src/main/.../domain/models/brand/BrandImage.java` exists as specified; re-run 22/22 green. |
| 5.1/5.2 `BrandImageRepository` + `BrandImageRepositoryTests` (11 tests) | done | `src/main/.../domain/repositories/BrandImageRepository.java` has exactly the three derived queries, no `@Query`, no delete methods; re-run 11/11 green. |
| 6.1 Mapping-vs-schema parity | done with documented deviation | See W1: single drift (`brand_images.id` serial vs bigint) proven pre-existing and table-generic; all V0.1.1 columns match. |
| 6.2 Existing-test review | done | Full unit scope re-run by verifier: 179/179 green. Pre-existing `isCanonicalKey` cases untouched and passing. |
| 7.1/7.2/7.3 Docs + specs | done | `docs/data-model.md` Brand Images section + ER block use `image_key`/`status`/tombstone note; `docs/backend-standards.md` registers STRING+varchar+CHECK; delta specs match shipped behavior, no drift found. |
| 8.1–8.4 Unit + DB verification report | done | Step-8 report exists; verifier independently re-ran targeted + full unit suites (see §2). |
| 9.1 No-endpoint curl scope | done | No `presentation/` changes (`git status` confirms); step-9 report exists; endpoint suites green per step-8 (80-test integration scope). |
| 10.1 Docs consistency pass | done | Verified: no `brand_images`-scoped `image_url` remains; remaining `image_url` mentions belong to other tables (brands/categories/products/reviews). English-only in new artifacts. |

## 2. Build / tests / coverage evidence (verifier-executed)

| Check | Command / method | Result |
|---|---|---|
| Targeted unit | `mvn test -Dtest='AssetStatusTests,BlobTypeTests,BrandImageTests'` | 88/88 pass (17 + 49 + 22), 0 failures/errors/skipped, BUILD SUCCESS |
| Repository integration | `mvn test-compile failsafe:integration-test -Dit.test='BrandImageRepositoryTests'` | 11/11 pass, BUILD SUCCESS (expected CHECK/FK/unique/NOT NULL violations logged as WARN on negative paths) |
| Full unit scope | `mvn test` | 179/179 pass, 0 failures/errors/skipped, BUILD SUCCESS |
| Spotless | `mvn spotless:check` | BUILD SUCCESS |
| SpotBugs | `mvn spotbugs:check` | BugInstance size 0, BUILD SUCCESS |
| Modernizer + duplicate-finder | `mvn modernizer:modernizer duplicate-finder:check` | BUILD SUCCESS |
| JaCoCo (unit scope) | `mvn jacoco:report` | `AssetStatus`: 12/12 lines, 15/15 branches, 4/4 methods. `BlobType`: 20/20 lines, 6/6 branches. `BrandImage`: 13/13 hand-written lines covered; branch/method misses are Lombok-generated code (identical pattern on pre-existing `Brand.java`: 73 missed branches), i.e. a project-wide JaCoCo+Lombok artifact, not a test gap. Transition matrix 3x4 incl. null asserted 100% (`AssetStatusTests.transitionMatrix`, 12 cases + self-transition guard). |
| TDD red-first evidence | apply-progress + step-8 report | Each new suite first failed compilation with missing-type errors (`AssetStatus` / `isKeyOf` / `BrandImage` / `BrandImageRepository`), confirming tests guard behavior rather than passing vacuously. Accepted as recorded evidence (not re-executed destructively). |

## 3. Spec compliance matrix

### asset-lifecycle

| Requirement / scenario | Implementation | Test proof | Status |
|---|---|---|---|
| Exactly three states, shared reusable type, no resource-specific concept | `AssetStatus.java:27-30`, zero framework imports, no brand references | constants-in-order guard; literal-name persistence contract | PASS |
| `PENDING`/`UPLOADED`/`DELETED` semantics; only `UPLOADED` visible | `isVisible()` (`AssetStatus.java:54-56`) | `should_reportVisibleOnlyForUploaded_when_isVisibleIsChecked` | PASS (consumption by read endpoints deferred by design non-goal) |
| Exactly `PENDING→UPLOADED`, `PENDING→DELETED`, `UPLOADED→DELETED`; `DELETED` terminal; reject self/undefined | exhaustive `switch`, no `default` (`AssetStatus.java:41-45`); null→false | 12-case 3x4 matrix incl. null + self-transition rejection + `isTerminal` | PASS |
| Confirming upload → `UPLOADED` | `BrandImage.markUploaded()` (`BrandImage.java:93-95`) | unit + repository round-trip with `modifiedAt` update | PASS |
| Retiring → `DELETED` | `BrandImage.markDeleted()` (`BrandImage.java:98-100`) | pending→deleted and uploaded→deleted paths | PASS |
| Illegal transition rejected, state unchanged | `transitionTo` throws `IllegalStateException` (`BrandImage.java:102-108`) | double-confirm and post-DELETED tests assert status unchanged | PASS |
| State stored as text, never ordinal | `@Enumerated(EnumType.STRING)` (`BrandImage.java:74`), `varchar(16)` | native `SELECT status` returns `'PENDING'` | PASS |
| DB rejects unknown values; names are a persistence contract | `chk_brand_images_status` CHECK in V0.1.1 | native `INSERT 'ARCHIVED'` rejected; literal-name test (rename breaks test, not data) | PASS |

### brands-management

| Requirement / scenario | Implementation | Test proof | Status |
|---|---|---|---|
| Entity on `brand_images`, extends audit base, LAZY brand ref, immutable unique key, non-null status | `BrandImage.java:50-76` (`updatable=false, unique=true`, `AccessLevel.NONE` setters, `length=16`) | creation/reload with audits | PASS |
| Created only as `PENDING` via factory; rejects non-canonical/foreign keys | `pending()` (`BrandImage.java:82-90`) | canonical creation; foreign-type, null/blank, malformed/traversal, null/transient-brand rejections | PASS |
| Duplicate key rejected at DB | unique index `uq_brand_images_image_key` | duplicate-write → `DataIntegrityViolationException` | PASS |
| Retired row kept, never physically removed | no `@SQLDelete`/`@SQLRestriction` on `BrandImage` (verified by grep + reflection guard); no delete methods on repository | tombstone native `COUNT(*)=1` proof | PASS |
| No presigned URL persisted | no URL field/column; `imageKey` only (grep: zero `imageUrl`/`urlFor` in new files) | structural (no URL column exists to test) | PASS |

### blob-storage (MODIFIED)

| Requirement / scenario | Implementation | Test proof | Status |
|---|---|---|---|
| Six types, distinct prefixes, canonical shape, single-source derivation (pre-existing) | unchanged constants/regex | pre-existing cases pass untouched (behaviour-preserving refactor proof) | PASS |
| Per-type `isKeyOf`: canonical AND own prefix, same prefix definitions | `BlobType.java:53-61`; global delegates (`:68-73`); null-safe | own-prefix ×6 types, cross-type rejection, null/blank/traversal rejection | PASS |
| Foreign-type key: not owned, still globally canonical | delegation via `anyMatch(isKeyOf)` | cross-type test asserts `isKeyOf==false` AND `isCanonicalKey==true` | PASS |

## 4. Correctness table (adversarial spot-checks)

| Probe | Result |
|---|---|
| Null/blank/traversal keys through `isKeyOf` and `pending()` | Rejected (`startsWith` fails on blank; regex rejects traversal/uppercase/truncated). |
| Transient brand (`id==null`) through `pending()` | Rejected with `IllegalArgumentException`. |
| Ghost brand id through repository | FK violation at flush (`DataIntegrityViolationException`). |
| Null status through builder path | NOT NULL violation at flush — DB is the backstop (see S1). |
| `DELETED` terminality end-to-end | Both `markUploaded` and `markDeleted` throw post-DELETED; row persists as tombstone. |
| Physical-delete paths | None on entity or repository interface (inherited `JpaRepository.delete()` caveat → W2). |
| Framework leakage into `AssetStatus` | None — zero imports; pure `java.lang` enum. |
| Presigned-URL persistence | No URL column/field in migration, entity, or repository. |

## 5. Design coherence (design.md D1–D8)

| Decision | Verdict |
|---|---|
| D1 Domain-only, entity owns invariants; ubiquitous language | Coherent: 3 new domain files + 1 domain refactor; no service/controller/DTO; method names match specs verbatim. |
| D2 Enum-owned transition table, exhaustive switch | Coherent: `switch` with no `default`; constant-count test makes growth a loud failure. |
| D3 Guarded entity, immutable key, PENDING-only factory, no `@SQLDelete` | Coherent, with accepted `@Builder` trade-off (see S1). |
| D4 Behaviour-preserving `isKeyOf` extraction | Coherent: single source of truth kept; existing tests untouched and green. |
| D5 Three derived queries, `JpaRepository` convention | Coherent; `delete()` reachability explicitly deferred to F6 (see W2). |
| D6 `varchar(16)`+CHECK, default dropped, 3 indexes | Coherent: migration matches statement-by-statement; `CHECK` enumerates exactly the three names. |
| D7 TDD red-green, contract-first matrix, DB-level proofs | Coherent: 17+49+22+11 tests as planned; native-query proofs present; naming `should_*_when_*` + AAA followed. |
| D8 NFRs (LAZY, no inverse collection, audit ownership, no URL logging) | Coherent: LAZY `@ManyToOne`, no `@OneToMany` added to `Brand`, audit columns via `BaseEntity`. |

## 6. Issues

### CRITICAL

None.

### WARNING

- **W1 — `ddl-auto: validate` drift on `brand_images.id` (serial vs bigint).**
  Evidence: step-8 report §8.3; independently corroborated — `flyway/release_0.1/V0.1.0__*.sql`
  declares `SERIAL` PKs on every table (12+ occurrences) while entities use `Long`/`IDENTITY`
  (`Brand.java:52-54`; `BrandImage.java:61-63` follows the same convention), and V0.1.1 does not
  touch `id`. Proven pre-existing and table-generic via pristine-tree run (same failure on
  `brand_types.id`). All V0.1.1-added columns match exactly (additionally proven by native-query
  DB tests). Per task instruction: WARNING, not FAIL. No new drift from this change exists.
- **W2 — `JpaRepository.delete*()` technically reachable on `BrandImageRepository`.**
  Evidence: `BrandImageRepository.java:32` extends `JpaRepository`; no delete callers exist in this
  slice. Invariant "never physically delete" currently rests on spec + review (design D5, follow-up
  F6: narrowed `Repository` interface). Accepted by design; must stay tracked until F6.
- **W3 — Brand soft-delete does not cascade; `@SQLRestriction` proxy caveat.**
  Evidence: `Brand.java:47-48` (`@SQLDelete`/`@SQLRestriction`); no cascade added by design (§9.4).
  Needs a product decision before the delete-endpoint story. Pre-existing pattern, documented risk.

### SUGGESTION

- **S1 — `@Builder` permits in-memory construction outside the `pending()` factory**
  (e.g. born-`UPLOADED` or status-less transient, the latter proven DB-rejected by the null-status
  test). Evidence: `BrandImage.java:57` + `BrandImageRepositoryTests.should_rejectWrite_when_statusIsNull`.
  Design D3 explicitly accepted this for fixture arrangement/Lombok consistency with a reviewer
  option to drop it. Suggest tracking removal (or a factory-only guard) alongside F6.
- **S2 — Pre-existing Spanish ER labels** (`docs/data-model.md:789,796,811,824`
  `"tiene_imagenes"`) violate the English-only rule but exist on the pristine tree (verified via
  stash) — out of scope; suggest a docs sweep.
- **S3 — JaCoCo branch/method percentages are depressed by Lombok-generated code** (no
  generated-code exclusion configured; pre-existing `Brand.java` shows the same pattern).
  Hand-written line coverage on all new classes is 100%. Suggest adding the Lombok/JaCoCo filter
  so future gates measure hand-written code.
- **S4 — Cosmetic name drift vs. the `tmp/` sketch** (`chk_` vs `ck_`, `uq_` vs `ux_`, `ix_` vs
  `ux_/ix_` variants). `design.md` does not pin names; no action required. Noted for traceability.

## 7. DoD §7 (`tmp/KAN-12-enriched-us-opus.md`) assessment

1. `AssetStatus`/`BrandImage`/`BrandImageRepository` created; `BlobType` refactored without behaviour change — **met**.
2. V0.1.1 applies on V0.1.0 and fresh; `validate` clean — **met except known pre-existing `id` drift (W1)**.
3. All §6 tests pass; transition matrix 100%; hand-written line coverage 100% (≥90% target) — **met**.
4. No physical-delete path; no `@SQLDelete`/`@SQLRestriction` on `BrandImage` — **met** (W2 tracked).
5. Status as string with active CHECK — **met** (native proofs).
6. Docs updated (`data-model.md`, `backend-standards.md`) — **met**.
7. OpenSpec capability created/specs consistent — **met** (no spec edits needed; no drift found).
8. Spotless / SpotBugs (0) / Modernizer / duplicate-finder pass; license headers present; English-only new artifacts; DDD layering respected — **met**.
9. Branch/commits/Jira follow-ups — **process item for orchestrator** (branch correct; nothing committed per instructions; F1–F7 live in the enriched context, not yet Jira tickets — no MCP server configured).

## Verdict

**PASS WITH WARNINGS** — no CRITICAL issues. Implementation matches specs, design, and tasks;
all suites and quality gates green with independent verifier evidence. W1–W3 are documented,
 pre-existing or design-accepted risks that must travel with the change to code-review; S1–S4 are
non-blocking follow-ups. Archiving is advisable after code-review; no further verify cycle required
unless review introduces behavior changes.
