# Verification Report — KAN-8 Create Product (verify pass 3)

- **Change**: `KAN-8-create-product`
- **Branch**: `feat/KAN-8-create-product` (HEAD `a11d407`, working tree clean)
- **Date**: 2026-09-10
- **Mode**: Full independent re-verification. Every conclusion below was re-derived from the
  actual code, tests, database and build output. Prior self-reports (`apply-progress.md`
  Batches 6-8, both `code-review-report*.md`, `verify-report`/`-2`) were read for context only
  and are **not** relied on as evidence.
- **Verdict**: **PASS WITH WARNINGS**

---

## 1. Completeness

| Item | Expected | Observed | Status |
|---|---|---|---|
| New `src/main` files | 19 | 19 (`git diff --name-only --diff-filter=A main...HEAD -- src/main`) | PASS |
| New `src/test` files | 8 | 8 (`--diff-filter=A ... -- src/test`) | PASS |
| Modified files (excl. `openspec/`) | 8 | 8 — `docs/backend-standards.md`, `docs/data-model.md`, `spotbugs-exclude.xml`, `GlobalExceptionHandler.java`, `DispensaryServiceTests.java`, `BrandControllerEndpointsTests.java`, `DispensaryEndpointsTests.java`, `GlobalExceptionHandlerTest.java` | PASS |
| Deleted files | 0 | 0 | PASS |
| `tasks.md` checkboxes | all `[x]` | 124 lines `[x]`, **0** `[ ]` | PASS |
| `tasks.md` distinct task ids | 108 original + 20 corrective | 128 ids across 124 lines (4 lines carry two ids each: `6.3/6.4`, `6.5/6.6`, `6.7/6.8`, `6.9/6.10`) | PASS |
| Original task count re-derived from git | 108 | `git show 8f02cd4:.../tasks.md \| grep -c "^- \[[x ]\]"` → **108**; after Batch 6 → 115; after Batch 8 → 124 | PASS |
| Corrective tasks present and checked | 3.10, 6.11-6.14, 8.15-8.17, 12.6-12.8, 13.8-13.13, 14.2-14.4 | all present, all `[x]` | PASS |
| Spec scenarios | 18 | `grep -c "^#### Scenario:"` → **18** | PASS |
| Reports directory | verify/code-review/step-N artifacts | 6 pre-existing + this one | PASS |

The earlier "7 vs 8 test files" contradiction is resolved: `design.md:526`, `tasks.md:12.5`,
`tasks.md:12.6` and `tasks.md:13.6` all now state **8** new test files, and that figure matches
the git-derived count exactly. No residual numeric contradiction was found on file counts.

---

## 2. Build, tests and coverage evidence

All commands run fresh in this pass from a clean tree.

| Command | Result |
|---|---|
| `mvn -o -Dspotbugs.skip=true test` | `BUILD SUCCESS` — **Tests run: 53, Failures: 0, Errors: 0, Skipped: 0** |
| `mvn -o -Dspotbugs.skip=true verify` | `BUILD SUCCESS` — Surefire **53/53**, Failsafe **59/59** → **112 green** |

Per-class breakdown (from the fresh `verify` run):

| Class | Tests |
|---|---|
| `BrandServiceTests` | 12 |
| `DispensaryServiceTests` | 1 |
| `ProductServiceTests` | 16 |
| `ProductControllerTests` | 11 |
| `BrandControllerTests` | 9 |
| `GlobalExceptionHandlerTest` | 4 |
| **Surefire total** | **53** |
| `ActuatorEndpointsTests` | 4 |
| `DispensaryEndpointsTests` | 1 |
| `ProductEndpointsTests` | 20 |
| `BrandControllerEndpointsTests` | 15 |
| `ProductReferenceDataRepositoryTests` | 4 |
| `ProductRepositoryTests` | 6 |
| `StrainRepositoryTests` | 2 |
| `DispensaryRepositoryTests` | 1 |
| `BrandRepositoryTests` | 6 |
| **Failsafe total** | **59** |

The expected 53 + 59 = 112 is confirmed. Output is pristine apart from one pre-existing
`[WARNING] Parameter 'systemProperties' is deprecated` from the Surefire configuration (present
on `main`, unrelated to this change) and the intentional `GlobalExceptionHandler` ERROR log
emitted by `GlobalExceptionHandlerTest`'s deliberately-throwing controller.

**Coverage gate**: as `tasks.md` 13.2 already discloses honestly, `pom.xml` binds JaCoCo only to
`prepare-agent`/`prepare-agent-integration`; **no `jacoco:check` goal exists**, so no Maven goal
enforces a 90% threshold. Re-confirmed this pass — `pom.xml` is byte-identical to `main`
(`git diff --stat main...HEAD -- pom.xml` → empty), so the gate was neither added nor weakened by
this change. This is a pre-existing project condition, not a KAN-8 regression.

---

## 3. Field-by-field mapping audit (fresh, complete)

This slice contains exactly **three** mapping/construction sites. All were audited from scratch,
field by field, against the four type definitions — not just the fields named by prior passes.

### Type inventories

- `CreateProductRequest`: 17 fields
- `CreateProductCommand`: 17 components (same names, same order)
- `Product`: 17 mapped attributes + generated `id`
- `CreateProductResponse`: 18 components (`id` + the 17)

### Site 1 — `ProductController.create()` : request → command (`ProductController.java:45-64`)

Named-builder mapping. All 17 verified one-by-one:
`ocpc←getOcpc`, `title←getTitle`, `description←getDescription`, `collectionId←getCollectionId`,
`categoryId←getCategoryId`, `subcategoryId←getSubcategoryId`, `brandId←getBrandId`,
`strainId←getStrainId`, `formatValue←getFormatValue`, `formatUnitId←getFormatUnitId`,
`contentValue←getContentValue`, `contentUnitId←getContentUnitId`,
`isCoreProduct←getIsCoreProduct`, `approved←getApproved`, `thc←getThc`, `cbd←getCbd`,
`enabled←getEnabled`. **No unmapped, duplicated or crossed field.**

### Site 2 — `ProductController.toCreateProductResponse()` : Product → response (`ProductController.java:70-90`)

**Positional** 18-argument record constructor — structurally the highest-risk site in the change
(and the subject of `tasks.md` 13.12's Question resolution). Argument-by-argument against
`CreateProductResponse`'s declaration order: `getId`, `getOcpc`, `getTitle`, `getDescription`,
`getCollection().getId()`, `getCategory().getId()`, `getSubcategory().getId()`,
`getBrand().getId()`, `getStrain().getId()`, `getFormatValue()`, `getFormatUnit().getId()`,
`getContentValue()`, `getContentUnit().getId()`, `getIsCoreProduct`, `getApproved`, `getThc`,
`getCbd`, `getEnabled`. **All 18 positions correct; arity matches; no field omitted.**

### Site 3 — `ProductServiceImpl.create()` : command + resolved refs → Product (`ProductServiceImpl.java:111-130`)

Named-builder mapping. All 17 verified: the 10 scalar fields map from their own command
component; the 7 object references map from their own `resolveOrNotFound` result, each resolved
from the matching command id (`formatUnit`←`formatUnitId`, `contentUnit`←`contentUnitId` — the
only same-typed reference pair, both resolved via `unitRepository`). **No mismapping.**

### Same-typed transposition-risk groups and their test discrimination

A transposition is only *possible* between fields of the same Java type (the compiler kills the
rest), and only *detectable* if some fixture gives the two fields different values.

| Type group | Members | Discriminating fixture values | Verdict |
|---|---|---|---|
| `Long` (response ctor) | `id`, `collectionId`, `categoryId`, `subcategoryId`, `brandId`, `strainId`, `formatUnitId`, `contentUnitId` | `ProductControllerTests`: `100, 1, 2, 3, 4, 5, 6, 7` — 8 distinct | COVERED |
| `String` | `ocpc`, `title`, `description` | `"OCPC-001"`, `"Product title"`, `"Description"` (controller); `"OCPC-VALID-001"`, `"Sour Diesel Gummies"`, `"A tasty gummy"` (endpoint) | COVERED |
| `Integer` | `formatValue`, `contentValue`, `thc`, `cbd` | controller `10, 20, 15, 5`; endpoint `10, 100, 15, 5` — 4 distinct in both | COVERED |
| `Boolean` | `isCoreProduct`, `approved`, `enabled` | 2 fixture combinations — see §4 | COVERED |
| `Unit` (service refs) | `formatUnit`, `contentUnit` | `ProductServiceTests` ids `60`/`61`; endpoint seeds two distinct `units` rows | COVERED |

**Conclusion for item 2 of the brief: no other unmapped, mismapped or untested field exists
beyond those already fixed.** Every field of every type is asserted at least once against a value
distinct from every same-typed sibling.

---

## 4. Boolean pairwise coverage — re-derived independently

Re-derived from the test source, not from any prior table. With 3 same-typed booleans and 2
possible values, the pigeonhole principle forces every single fixture to leave at least one pair
equal, so at least two fixture combinations are required.

### `ProductControllerTests`

| Fixture | Request side | Response side (`savedProduct`) | `(iC,ap)` | `(iC,en)` | `(ap,en)` |
|---|---|---|---|---|---|
| `create_shouldReturn201_when_validRequest` (`:162`) | `T, F, T` | `savedProduct(100L)` → `T, F, T` | **differ** | equal | **differ** |
| `create_shouldNotTransposeIsCoreProductAndEnabled_when_theirValuesDiffer` (`:226`) | `F, T, T` | `savedProductWithCoreDifferingFromEnabled(101L)` → `F, T, T` | **differ** | **differ** | equal |
| **Union** | | | **covered** | **covered** | **covered** |

Both fixtures assert on **both** directions: response via `jsonPath("$.data.isCoreProduct|approved|enabled")`
and request→command via `ArgumentCaptor<CreateProductCommand>` (`:199-219`, `:250-256`).

### `ProductEndpointsTests`

| Fixture | Submitted `iC, ap, en` | `(iC,ap)` | `(iC,en)` | `(ap,en)` |
|---|---|---|---|---|
| `should_persistSuppliedFlags_when_theyAreExplicit` (`OCPC-FLAGS-001`) | `T, F, T` | **differ** | equal | **differ** |
| `should_persistIsCoreProductAndEnabled_when_theirValuesDiffer` (`OCPC-FLAGS-002`) | `F, T, T` | **differ** | **differ** | equal |
| **Union** | | **covered** | **covered** | **covered** |

Both read `SELECT enabled, approved, is_core_product` back from the real PostgreSQL row and
assert each column individually.

**All 3 pairwise cases are genuinely covered in both files, on both mapping directions, and
end-to-end through the database.** Independently re-derived; matches the gatekeeper's claim.

---

## 5. Spec compliance matrix — 18 scenarios traced to real, passing, correctly-asserting tests

Each scenario was traced to a named test method that I read and confirmed exists, asserts the
scenario's normative outcome, and passes in this pass's run.

| # | Scenario | Owning test(s) | Status |
|---|---|---|---|
| 1 | Valid product creation | `ProductEndpointsTests.should_return201AndPersistProduct_when_requestIsValid` (201, `count+1`, `created_at` non-null); `ProductServiceTests.should_persistProductOnce_when_commandIsValid` | PASS |
| 2 | Created product echoes submitted attributes | `ProductControllerTests.create_shouldReturn201_when_validRequest` — all **18** `$.data.*` asserted | PASS |
| 3 | Missing or blank required field | `create_shouldReturn400_when_missingTitle`, `create_shouldReturn400_when_blankOcpc`, `ProductEndpointsTests.should_return400_when_requiredFieldIsBlank` | PASS |
| 4 | Required field present but invalid | `create_shouldReturn400_when_formatValueIsNotPositive`, `_thcIsNegative`, `_ocpcExceedsMaxLength`, `_titleExceedsMaxLength` | PASS (partial — see W2) |
| 5 | Empty request body | `create_shouldReturn400WithStaticMessage_when_bodyIsEmpty`, `ProductEndpointsTests.should_return400_when_bodyIsEmpty` | PASS |
| 6 | Unknown collection/category/subcategory/unit | `should_return404_when_{collection,category,subcategory,formatUnit,contentUnit}IsUnknown` + 5 service-level equivalents asserting the exact message | PASS |
| 7 | Unknown brand or strain | `should_return404_when_brandIsUnknown`, `_strainIsUnknown` + service equivalents | PASS |
| 8 | Soft-deleted brand or strain | `should_return404_when_brandIsSoftDeleted`, `_strainIsSoftDeleted` (real soft-deleted rows) | PASS |
| 9 | Duplicated OCPC | `should_return409_when_ocpcIsAlreadyUsed`; `should_throwConflict_when_ocpcAlreadyUsedByLiveProduct` | PASS |
| 10 | OCPC of soft-deleted product reusable | `should_return201_when_ocpcHolderIsSoftDeleted` (native `UPDATE ... deleted_at`) | PASS |
| 11 | Subcategory not belonging to category | `should_return409_when_subcategoryBelongsToAnotherCategory` (both layers) | PASS |
| 12 | Subcategory belonging to category | `should_continueCreation_when_subcategoryBelongsToSuppliedCategory` + happy path | PASS |
| 13 | Optional attributes omitted | `should_persistNullOptionalAttributes_when_theyAreOmitted` (5 columns `NULL`); `should_persistNulls_when_optionalAttributesAreOmitted` | PASS |
| 14 | Optional flags supplied explicitly | `should_persistSuppliedFlags_when_theyAreExplicit`, `should_persistIsCoreProductAndEnabled_when_theirValuesDiffer`, `should_persistFalse_when_flagsAreExplicitlyFalse` | PASS |
| 15 | Rejected request persists nothing | `should_leaveProductCountUnchanged_when_requestIsRejected` (5 rejection paths, count before/after each); 500 path at unit level via `should_propagateException_when_saveFails` | PASS |
| 16 | Validation failure reports field-level errors | `create_shouldReturn400_*` (4 tests asserting `$.errors[0].field`), `should_return400_when_requiredFieldIsBlank` | PASS |
| 17 | Not-found / conflict descriptive message | `create_shouldReturn404_when_serviceThrowsNotFound`, `_409_when_serviceThrowsConflict`, plus endpoint tests asserting exact messages | PASS |
| 18 | Error responses do not leak internal details | `should_reportDescriptiveMessageWithoutInternalDetails_when_requestFails` (asserts absence of `insert into`, `fk_products_`, `Exception`) | PASS |

**18/18 trace to genuinely passing, correctly-asserting tests.** Scenario 4 is the only one whose
coverage is narrower than the requirement text (W2).

---

## 6. Correctness

| Area | Finding | Status |
|---|---|---|
| Guard order (D1) | `existsByOcpc` → 7× `resolveOrNotFound` → taxonomy coherence, all before any write, inside one `@Transactional` | PASS |
| Guard-order pinning | `should_throwConflict_when_ocpcDuplicatedAndBrandUnknown` and `should_throwNotFound_when_taxonomyIncoherentAndContentUnitUnknown` pin precedence with deliberately ambiguous commands | PASS |
| Taxonomy check | Reads only the proxy id (`subcategory.getCategory().getId()`), no initialisation | PASS |
| Soft delete (D5/D7) | `@SQLDelete` + `@SQLRestriction("deleted_at IS NULL")` on `Product` and `Strain`; `existsByOcpc` inherits the restriction | PASS |
| Atomicity (D6) | `@Transactional(rollbackFor = Exception.class, propagation = REQUIRED)`; proven end-to-end by row-count invariance across all 5 rejection paths | PASS |
| D9 malformed body | Single additive `@ExceptionHandler(HttpMessageNotReadableException.class)` with a **static** message (never `ex.getMessage()`); cross-cutting regression pinned on `/api/brands` and `/api/dispensaries` | PASS |
| Error-message hygiene | Messages composed of fixed text + client-supplied id only; no driver/DB exception text echoed | PASS |
| Repository layer | `ProductRepository` contains only the derived `existsByOcpc` — no mapping/construction code to audit | PASS |
| `ProductApi` / `DataResponse` | No field mapping; `@Valid @RequestBody`, `@ResponseStatus(CREATED)` correct | PASS |

### Adversarial mutation testing (new this pass)

Every prior mutation experiment in this change targeted `ProductController`. **No prior pass had
mutation-tested `ProductServiceImpl`**, so I ran four fresh mutations there against the full
suite. Each was applied to a pristine file, run through `mvn -o -Dspotbugs.skip=true verify`, then
reverted and diff-confirmed byte-identical.

| # | Mutation in `ProductServiceImpl` | Unit suite | Full suite | Killed by |
|---|---|---|---|---|
| A | `isCoreProduct` ↔ `enabled` in `Product.builder()` | **53/53 green (blind)** | `BUILD FAILURE` | `ProductEndpointsTests.should_persistIsCoreProductAndEnabled_when_theirValuesDiffer:656` — `expected: <true> but was: <false>` |
| B | `formatValue` ↔ `contentValue` | **53/53 green (blind)** | `BUILD FAILURE` | `ProductEndpointsTests.should_return201AndPersistProduct_when_requestIsValid` |
| C | `formatUnitId` ↔ `contentUnitId` in reference resolution | 3 failures | `BUILD FAILURE` | `ProductServiceTests.should_persistProductOnce_when_commandIsValid`, `_formatUnitDoesNotExist`, `_contentUnitDoesNotExist` |
| D | `thc` ↔ `cbd` | 1 failure | `BUILD FAILURE` | `ProductServiceTests.should_persistProductOnce_when_commandIsValid` |

**All four mutants were killed by the suite.** Tree restored; `git status --porcelain` empty; final
`mvn -o -Dspotbugs.skip=true verify` re-confirmed `BUILD SUCCESS`, 53 + 59.

Mutations A and B are the substance of **S1** below: they prove the Batch 8 integration fixtures
are load-bearing for the *service* layer, not only for the controller — a dependency neither
`apply-progress.md` nor either code-review report records.

---

## 7. Design coherence

| Decision | Implementation | Status |
|---|---|---|
| D1 fixed guard order | `ProductServiceImpl.create` as documented | COHERENT |
| D2 object associations incl. cross-aggregate `Brand`/`Strain` | All 7 `@ManyToOne(fetch = LAZY)`, no `CascadeType`, no `orphanRemoval`, no inverse collections; deviation disclosed in `proposal.md` item 9 and `design.md` Risks | COHERENT (knowing DDD deviation) |
| D3 `CreateProductCommand` as `@Builder` record | 17-component record, no `jakarta.validation` / `io.swagger` imports — application layer stays clean of presentation types | COHERENT |
| D4 DRY `resolveOrNotFound` | One private generic helper replaces 7 `orElseThrow` chains; kept private (single consumer) | COHERENT |
| D5 minimal `Strain` | Minimal mapping with soft-delete filtering | COHERENT |
| D6 single transaction | `@Transactional(rollbackFor = Exception.class)` on `create` only | COHERENT |
| D7 OCPC semantics | `existsByOcpc` + `@SQLRestriction` → live-only uniqueness | COHERENT |
| D8 request DTO validation | `@NotBlank`+`@Size(64)` `ocpc`; `@NotBlank`+`@Size(255)` `title`; `@NotNull @Positive` on 7 ids + `formatValue`/`contentValue`; `@PositiveOrZero` `thc`/`cbd`; **nothing on `description`** and the 3 flags | COHERENT |
| D9 malformed body | Additive handler, static message | COHERENT |
| D10 flat-id response DTO | 18-component record, values read from the persisted entity, no audit fields | COHERENT |
| D11/D12 lookup entities | `Collection`, `Category`, `Subcategory`, `Unit` | COHERENT |
| D13 documentation | `docs/data-model.md`, `docs/backend-standards.md` updated | COHERENT |
| D14 aggregate root | `Product` is the single write entry point; service never calls a setter on a resolved reference | COHERENT |
| Ubiquitous language | `Product`, `Collection`, `Category`, `Subcategory`, `Strain`, `Unit`, `ocpc`, `isCoreProduct`, `approved`; no `Manager`/`Helper`/`Processor` smells | COHERENT |
| Anemic-model note | `Product` is a data-holding aggregate with invariants enforced in `ProductServiceImpl` — a knowing, project-consistent choice matching the `Brand`/`Dispensary` precedent | COHERENT (consistent with codebase) |

---

## 8. Scope discipline across Batches 6-8

| Guard | Evidence | Status |
|---|---|---|
| `pom.xml` untouched | `git diff --stat main...HEAD -- pom.xml` → empty | HELD |
| No `@Size` on `description` | `CreateProductRequest.java:51-52` — `@Schema` only; disclosed as `proposal.md` open item 11 | HELD |
| No authentication/authorization work | `grep -rni "SecurityFilterChain\|@PreAuthorize\|spring-boot-starter-security" src/ pom.xml` → no hits | HELD |
| No OCPC index | `SELECT indexname FROM pg_indexes WHERE tablename='products'` → only `products_pkey`; no new Flyway script | HELD |
| No `DataIntegrityViolationException` work | That handler pre-exists on `main`; the `GlobalExceptionHandler` diff is a **single** additive `HttpMessageNotReadableException` method (+9 lines, D9), no existing handler touched | HELD |
| Batch 8 test-only | `git show --stat a11d407` → `ProductControllerTests.java`, `ProductEndpointsTests.java`, `tasks.md`, `apply-progress.md` only | HELD |
| `ProductController.java` / `CreateProductResponse.java` untouched by Batch 8 | Neither appears in `a11d407`'s file list | HELD |
| Batch 7 test-only | `git show --stat 184cb9d` → the same two test files only | HELD |
| `spotbugs-exclude.xml` scoped by class | New `<Match>` names exactly `Product` and `Subcategory` (not by package), leaving the 9 pre-existing `Brand`/`Dispensary` findings unsuppressed | HELD |
| Code-review reports frozen | Neither `code-review-report.md` nor `-2.md` modified after their commits | HELD |

---

## 9. Database state and schema drift

| Check | Result |
|---|---|
| `products`, `strains`, `brands`, `brand_types`, `collections`, `categories`, `subcategories`, `units`, `strain_types`, `seed_companies`, `dispensaries`, `addresses` | **all 0 rows** |
| `license_statuses` | 4 rows — V0.1.0 migration seed (`flyway/release_0.1/V0.1.0__initialData_SM.sql:41`), i.e. baseline, **not** test residue |
| `flyway_schema_history` | 1 row: `0.1.0 / initialData SM / success=t` |
| Migration files on disk | exactly one: `flyway/release_0.1/V0.1.0__initialData_SM.sql` |
| `ddl-auto` | `none` (`src/main/resources/application.yml:28`) |
| Indexes on `products` | `products_pkey` only |

**No schema drift. Database clean** — including after this pass's four mutation runs plus three
full `verify` runs, confirming `ProductEndpointsTests`'s `@AfterEach` native-SQL cleanup is
order-independent and residue-free.

---

## 10. Issues

### CRITICAL

*None.*

### WARNING

**W1 — `tasks.md` task `8.17` is filed in the wrong section (NEW this pass).**
Task `8.17` sits at `tasks.md:104`, inside `## 6. Presentation: DTOs, API interface and controller`
(lines 92-104), immediately after `6.14`. Section `## 8. Endpoint integration tests` runs lines
113-130 and ends at `8.16`. CLAUDE.md §7 requires corrective tasks be added "not as *bugfixes* but
as part of the initial design, thus **in the proper section**." `apply-progress.md:1366` records
"new corrective tasks 6.14, 8.17, 14.4" without noting the misplacement, and no prior pass caught
it. This is the same *class* of artifact-hygiene defect as the "7 vs 8 test files" miscount fixed
in Batch 7 — documentation-only, zero code or test impact, but it means `tasks.md`'s section
structure no longer matches its own numbering. **Fix**: move the `8.17` line from `:104` to the end
of Section 8 (after `8.16` at `:130`).

**W2 — Spec scenario 4 remains partially covered (CARRIED FORWARD, independently re-confirmed).**
Requirement R2 enumerates six invalid-value families. Four have tests (`formatValue` non-positive,
`thc` negative, `ocpc` > 64, `title` > 255). Three do **not**: `@NotNull` on the seven reference
ids, non-positive `contentValue`, and negative `cbd`. Re-verified this pass:
`grep -rn "collectionId(null)\|brandId(null)\|contentValue(0)\|cbd(-" src/test/java` → **no hits**.
Code-review pass 2 explicitly adjudicated this at **Minor** (unlike `@Size`, which had zero
instances anywhere, `@NotNull` and `@Positive` each already have working asserted instances, so
only per-field application is unpinned). I concur with that severity and do not escalate. Not
blocking; carry into a follow-up.

**W3 — `mvn -o spotbugs:check` still fails on 9 pre-existing findings (CARRIED FORWARD, disclosed).**
Re-confirmed `Brand.java` and `Dispensary.java` are byte-identical to `main`
(`git diff --stat main...HEAD -- <both>` → empty), so those findings predate this change and are
out of its declared scope. All verification here used `-Dspotbugs.skip=true`. Fully disclosed in
`proposal.md` open item 10, `tasks.md` 13.3 and 14.1. Not a KAN-8 regression.

### SUGGESTION

**S1 — `ProductServiceTests` is structurally blind to boolean and Integer transpositions in
`ProductServiceImpl` (NEW this pass; not a defect, but a single-point-of-failure).**
`validCommandBuilder()` (`ProductServiceTests.java:94-113`) uses `isCoreProduct=TRUE,
approved=TRUE, enabled=TRUE` — all three equal, so it distinguishes **zero** of the three boolean
pairs. The other two boolean fixtures are all-`FALSE` and all-`null`, equally undiscriminating.
It also uses `formatValue=1, contentValue=1, cbd=1` — three same-typed `Integer` fields sharing
the value `1`. I proved the consequence: mutations **A** (`isCoreProduct`↔`enabled`) and **B**
(`formatValue`↔`contentValue`) in `ProductServiceImpl.create`'s builder each left the unit suite
at **53/53 green**, and were killed *only* by `ProductEndpointsTests`. The suite as a whole is
therefore sound and this is **not** a coverage gap today — but the entire defense for the service
layer's most transposition-prone mapping rests on two integration fixtures, with no unit-level
backstop. Cheap hardening (test-only, ~4 lines): give `validCommandBuilder()` distinct booleans
(e.g. `T/F/T`, plus one variant with `isCoreProduct != enabled`) and distinct
`formatValue`/`contentValue`/`cbd`.

**S2 — Stale planned-file counts survive in `apply-progress.md` batch narratives.**
Lines 154, 432 and 690 say "5 planned new test files" (the pre-correction `design.md` figure);
line 1056 says "all 8 new test files (per the twice-corrected...)". These are historical batch
snapshots, correct as of their own batch, and line 1056 records the correction — so this is not a
live contradiction. Still, a reader scanning the file sees both `5` and `8`. Optional: add a
one-line forward-reference at the first occurrence.

---

## 11. Verdict

**PASS WITH WARNINGS**

- Suite is fresh-green at the expected **53 unit + 59 integration = 112**.
- The complete field-by-field audit of all **three** mapping sites found **no** unmapped,
  mismapped or untested field beyond those already fixed.
- All **3** pairwise boolean cases are covered, independently re-derived, on both mapping
  directions and end-to-end through the real database.
- All **18** spec scenarios trace to genuinely passing, correctly-asserting tests.
- Scope discipline held across Batches 6-8 on every named guard.
- Database is clean with no schema drift.
- **Four new mutations** on the previously-unmutated `ProductServiceImpl` were all killed.
- `tasks.md` is fully checked and internally consistent on counts.

The three warnings are one **new documentation-placement defect (W1)** and two **previously
disclosed, independently re-confirmed, non-blocking carry-forwards (W2, W3)**. None affects
runtime behaviour, spec compliance or test validity.

Recommended before archive: fix **W1** (move `8.17` into Section 8) — a one-line move requiring no
code, test or re-verification beyond a `tasks.md` read. **S1** is worth taking in the same pass
while the context is fresh, but is genuinely optional.
