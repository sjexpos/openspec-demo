# Verification Report — KAN-8 Create Product

- Date: 2026-09-10
- Change: `KAN-8-create-product`
- Branch: `feat/KAN-8-create-product`
- Agent: `opsx-verify`
- Mode: full independent verification. Every claim below was re-derived from a fresh run or from
  reading the actual code/git history. Prior reports (`apply-progress.md`, the two mandatory Step
  N+1 / N+2 reports) were read as *claims to be checked*, not as evidence.
- Schema: `openspec/schemas/story-sdd/schema.yaml` has **no distinct `verify` artifact block**; the
  closest analog is the `apply` block's tracking philosophy. Report path/name follows the KAN-6
  archive precedent (`openspec/archive/2026-08-05-KAN-6-brands-crud/reports/2026-08-05-verify-report.md`).

## Housekeeping performed before verification

`git status --porcelain` showed exactly one pending change (`proposal.md`, the orchestrator-written
item 10 addendum scoping the SpotBugs exception). Committed as `2c1d649`
(`docs(KAN-8): scope the SpotBugs exception in proposal.md open items`). Nothing else was committed.
Working tree is clean at the time of writing (no production source was mutated by this verification).

## Completeness

| Artifact | Expected | Found | Status |
|---|---|---|---|
| `proposal.md` | Why / What Changes / Capabilities / Impact + open items | Present, 10 open items carried | PASS |
| `specs/products-management/spec.md` | 8 requirements, 18 scenarios, `## Purpose` | 8 requirements, 18 scenarios (2+3+3+2+2+2+1+3), Purpose present | PASS |
| `design.md` | Decisions D1–D15, risks, migration plan | Present, D1–D15 all documented with alternatives | PASS |
| `tasks.md` | All checkboxes cleared | 104 `- [x]`, **0** `- [ ]` | PASS |
| `apply-progress.md` | History of all 5 apply batches | **Batches 1–4 only; no Batch 5 entry** | **FAIL** (see W1) |
| Step N+1 report | `reports/YYYY-MM-DD-step-N+1-...md` | `2026-09-10-step-N+1-unit-test-and-db-verification.md` | PASS |
| Step N+2 report | `reports/YYYY-MM-DD-step-N+2-...md` | `2026-09-10-step-N+2-manual-curl-verification.md` | PASS |

Note: the orchestrator brief cited "108 checkboxes"; the actual count is 104, all checked. Not a defect.

## Build, tests and coverage evidence (all re-run by this agent)

| Command | Result | Claimed | Verdict |
|---|---|---|---|
| `mvn -o -Dspotbugs.skip=true test` | `Tests run: 50, Failures: 0, Errors: 0, Skipped: 0` / `BUILD SUCCESS` | 50 unit | **matches** |
| `mvn -o -Dspotbugs.skip=true verify` | Surefire 50 + Failsafe `Tests run: 58, Failures: 0, Errors: 0` / exit 0 | 58 integration | **matches** |
| Total | **108 green (50 unit + 58 integration)** | 108 | **matches** |
| `mvn -o clean compile` | `BUILD SUCCESS`, 70 classes | clean build | matches |
| `mvn -o spotbugs:check` (feature branch, populated `target/classes`) | `Total bugs: 9` / `BUILD FAILURE` | 9 pre-existing | **matches** |
| `mvn -o spotbugs:check` (**`main`**, populated `target/classes`) | `Total bugs: 9` / `BUILD FAILURE` | "same 9 already fail on main" | **matches — confirmed not a regression** |

Per-class Failsafe breakdown observed: `ProductEndpointsTests` 19, `BrandControllerEndpointsTests` 15,
`ProductRepositoryTests` 6, `BrandRepositoryTests` 6, `ProductReferenceDataRepositoryTests` 4,
`ActuatorEndpointsTests` 4, `StrainRepositoryTests` 2, `DispensaryEndpointsTests` 1,
`DispensaryRepositoryTests` 1 = 58.

Database state re-checked by this agent *after* its own full `verify` run:
all ten tracked tables at 0 rows; `flyway_schema_history` a single row, version `0.1.0`, `success = t`;
`\d products` shows only `products_pkey` — **no index on `ocpc`**, confirming zero schema drift and
confirming the race window described in R4 is genuinely un-mitigated at the DB level.

**Coverage: no enforced gate exists.** Independently confirmed against `pom.xml`: the
`jacoco-maven-plugin` binds only `prepare-agent` (`jacoco-initialize`) and `prepare-agent-integration`
(`jacoco-initialize-integration`). There is no `jacoco:check` execution anywhere. The two
`<goal>check</goal>` occurrences (lines 364, 384) belong to `duplicate-finder` and `spotbugs`.
Task 13.2's honest correction of its own premise is accurate; see W3.

## Spec compliance matrix — 8 requirements

Cross-checked by reading `ProductServiceImpl.java`, `ProductController.java`, `ProductApi.java`,
`CreateProductRequest.java`, `CreateProductResponse.java`, `GlobalExceptionHandler.java`, `Product.java`.

| Requirement | Implementation evidence | Status |
|---|---|---|
| R1 Create product via `POST /api/products` | `ProductApi`: `@RequestMapping("/api/products")`, `@PostMapping`, `@ResponseStatus(CREATED)`, returns `DataResponse<CreateProductResponse>`. All 17 accepted fields present in `CreateProductRequest`; the 11 required ones carry `@NotBlank`/`@NotNull`. **No audit/lifecycle field exists in either DTO** (grep for `createdAt|createdBy|updatedAt|deletedAt|modifiedAt` returns nothing) — the strongest form of "SHALL NOT accept audit metadata". `created_at` set via `BaseEntity`, asserted non-null in DB by the endpoint test. | PASS |
| R2 Validation of the payload | Full D8 constraint set verified present: `@NotBlank`+`@Size(max=64)` on `ocpc`; `@NotBlank`+`@Size(max=255)` on `title`; `@NotNull @Positive` on all 7 ids and on `formatValue`/`contentValue`; `@PositiveOrZero` on `thc`/`cbd`; nothing on `description` and the 3 flags. `@Valid` on the API parameter. Field names surface via `MethodArgumentNotValidException` → `FieldError(field, message)`. | PASS (implementation) / **PARTIAL (test coverage — see W2)** |
| R3 Resolution of references | Seven `resolveOrNotFound(...)` calls in request declaration order; each throws `NotFoundException("<Reference> not found with ID: <id>")`. `Brand` and `Strain` carry `@SQLRestriction("deleted_at IS NULL")`, so soft-deleted rows resolve as absent. Lookup entities correctly carry no soft-delete (their tables have no `deleted_at`), matching the spec's per-reference-type wording. Distinct names `FormatUnit`/`ContentUnit` correctly disambiguate the two `Unit` references. | PASS |
| R4 OCPC uniqueness among non-deleted | `ProductRepository.existsByOcpc(String)` + `@SQLRestriction` on `Product` → guard 1 throws `ConflictException("Product already exists with OCPC: …")`. Reuse-after-soft-delete pinned at repository level *and* end-to-end. | PASS (single-threaded); racy under concurrency — disclosed, see Risk (b) |
| R5 Taxonomy coherence | Guard 3: `!Objects.equals(subcategory.getCategory().getId(), category.getId())` → `ConflictException("Subcategory <id> does not belong to category <id>")`. Reads the LAZY proxy id without initialising it. | PASS |
| R6 Optional product attributes | `ProductServiceImpl` copies every optional value verbatim into `Product.builder()` — no `orElse`, no `Boolean.TRUE.equals`, no defaulting anywhere. Response booleans stay nullable (`Boolean`, not `boolean`), so unset stays distinguishable from `false`. Endpoint test asserts the five columns are `NULL` in the database, not `false`/`0`. | PASS |
| R7 Atomicity | Exactly one `@Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)`, on `ProductServiceImpl.create`. All three guards precede the single `save`, so deterministic rejections never issue an INSERT. `ProductEndpointsTests.shouldLeaveProductCountUnchanged_when_requestIsRejected` asserts `SELECT count(*)` before/after five distinct rejection paths (400 empty body, 400 blank field, 404, 409 dup, 409 taxonomy). | PASS |
| R8 Standard envelopes / no internal leakage | `ErrorResponse(timestamp, status, path, errors)` asserted field-by-field. All product-path messages are fixed text plus client-supplied identifiers. Endpoint test asserts the body contains no `insert into`, `fk_products_` or `Exception`. | PASS with caveat (see S2 — the untouched `DataIntegrityViolationException` handler still echoes `ex.getMessage()`) |

## Scenario traceability — 18 scenarios, independently traced

Verified by reading the named test bodies, not by trusting the tasks.md table. Every test below is in
the 108 that passed in this agent's own runs.

| # | Scenario | Owning test(s) verified by reading | Status |
|---|---|---|---|
| 1 | Valid product creation | `ProductServiceTests.should_persistProductOnce_when_commandIsValid`; `ProductEndpointsTests.shouldReturn201AndPersistProduct_when_requestIsValid` (asserts count+1 and non-null `created_at` by native query) | PASS |
| 2 | Created product echoes submitted attributes | `ProductControllerTests.create_shouldReturn201_when_validRequest` (fixture entity independent of request) + structural: `ProductController.toCreateProductResponse` reads all 18 values off the persisted `Product`, never off `request` | PASS |
| 3 | Missing or blank required field | `ProductControllerTests.create_shouldReturn400_when_missingTitle` / `_when_blankOcpc`; `ProductEndpointsTests.shouldReturn400_when_requiredFieldIsBlank` (title `"   "` → field `title`) | PASS |
| 4 | Required field present but invalid | `create_shouldReturn400_when_formatValueIsNotPositive` (0), `create_shouldReturn400_when_thcIsNegative` (-1). **`@Size(max=64)`/`@Size(max=255)`, `contentValue=0`, `brandId=0`, negative `cbd` have no test anywhere.** | **PARTIAL (W2)** |
| 5 | Empty request body | `ProductControllerTests.create_shouldReturn400WithStaticMessage_when_bodyIsEmpty`; `ProductEndpointsTests.shouldReturn400_when_bodyIsEmpty`; `GlobalExceptionHandlerTest.handleMessageNotReadable_shouldReturn400WithStaticMessage`; live curl N+2.6 | PASS |
| 6 | Unknown collection/category/subcategory/unit | 4 `ProductServiceTests` unresolved-reference tests + `ProductEndpointsTests` `shouldReturn404_when_{collection,category,subcategory,formatUnit,contentUnit}IsUnknown` — each asserts the exact message *and* unchanged row count | PASS |
| 7 | Unknown brand or strain | `should_throwNotFound_when_brandDoesNotExist` / `_strainDoesNotExist`; `ProductEndpointsTests.shouldReturn404_when_brandIsUnknown` / `_strainIsUnknown` | PASS |
| 8 | Soft-deleted brand or strain | `StrainRepositoryTests.should_returnEmpty_when_strainIsSoftDeleted`; `ProductEndpointsTests.shouldReturn404_when_brandIsSoftDeleted` / `_strainIsSoftDeleted` (real soft-deleted fixtures); live curl N+2.4 | PASS |
| 9 | Duplicated OCPC | `should_throwConflict_when_ocpcAlreadyUsedByLiveProduct`; `ProductRepositoryTests` `existsByOcpc` true/false; `ProductEndpointsTests.shouldReturn409_when_ocpcIsAlreadyUsed` (asserts no second row) | PASS |
| 10 | OCPC of a soft-deleted product is reusable | `ProductRepositoryTests.should_returnFalse_when_onlyOcpcHolderIsSoftDeleted` (with `flush()`+`clear()`); `ProductEndpointsTests.shouldReturn201_when_ocpcHolderIsSoftDeleted` | PASS |
| 11 | Subcategory not belonging to category | `should_throwConflict_when_subcategoryBelongsToAnotherCategory`; `ProductEndpointsTests.shouldReturn409_when_subcategoryBelongsToAnotherCategory` (two real category/subcategory pairs) | PASS |
| 12 | Subcategory belonging to category | `should_continueCreation_when_subcategoryBelongsToSuppliedCategory`; endpoint happy path | PASS |
| 13 | Optional attributes omitted | `should_persistNulls_when_optionalAttributesAreOmitted`; `ProductEndpointsTests.shouldPersistNullOptionalAttributes_when_theyAreOmitted` (asserts all five columns `NULL` via native query) | PASS |
| 14 | Optional flags supplied explicitly | `should_persistFalse_when_flagsAreExplicitlyFalse`; `shouldPersistSuppliedFlags_when_theyAreExplicit` (asserts `false`, not `NULL`) | PASS |
| 15 | Rejected request persists nothing | `ProductEndpointsTests.shouldLeaveProductCountUnchanged_when_requestIsRejected` (5 paths, count before/after each). The `500` branch named in the scenario is covered only at unit level (`should_propagateException_when_saveFails`), never end-to-end — acceptable, since forcing a real 500 requires an artificial fault. | PASS |
| 16 | Validation failure reports field-level errors | Controller tests assert `$.errors[0].field` = the offending property; endpoint test asserts field `title` | PASS |
| 17 | Not-found and conflict report a descriptive message | Endpoint tests assert exact messages per reference; `shouldReportDescriptiveMessageWithoutInternalDetails_when_requestFails` asserts `timestamp`/`status`/`path`/`errors` | PASS |
| 18 | Error responses do not leak internal details | `shouldReportDescriptiveMessageWithoutInternalDetails_when_requestFails` asserts absence of `insert into`, `fk_products_`, `Exception`; live curl N+2.6 inspected three 400 bodies. Only the 404 path is scanned — see S2. | PASS with caveat |

**Result: 17 of 18 scenarios fully traced to passing tests that genuinely assert what the scenario
requires; 1 (scenario 4) partially traced.**

## Design coherence — key decisions verified in code

| Decision | Verified | Status |
|---|---|---|
| D1 guard order | `ProductServiceImpl.create` lines 80–132: `existsByOcpc` → 7× `resolveOrNotFound` in declaration order → `Objects.equals` taxonomy check → `save`. Pinned by two order tests (`should_throwConflict_when_ocpcDuplicatedAndBrandUnknown`, `should_throwNotFound_when_taxonomyIncoherentAndContentUnitUnknown`). | PASS |
| D2 reference style | All seven references are `@ManyToOne(fetch = FetchType.LAZY)` + `@JoinColumn(nullable = false)`. Guard rails hold: no `CascadeType` anywhere, no `orphanRemoval`, no inverse `@OneToMany` on `Brand`/`Strain`. `Product` never has a setter called on a resolved reference. | PASS |
| D3 command signature | `CreateProductCommand` record, 17 fields, in `application/services/model`; `ProductService` exposes exactly one `create(CreateProductCommand)`. | PASS |
| D4 DRY helper | Exactly one private generic `resolveOrNotFound(JpaRepository<T,Long>, Long, String)`, seven call sites, a single `orElseThrow` in the whole file. Messages match the `BrandServiceImpl` style. | PASS |
| D6 transaction boundary | Exactly one `@Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)`, on the impl method — not on the interface, not on the controller. | PASS |
| D9 exception handler | One additive `@ExceptionHandler(HttpMessageNotReadableException.class)` → 400, **static** `FieldError("general", "Malformed or missing request body")`, reusing the existing private `build(...)`. `ex.getMessage()` is not referenced. No other handler altered. Cross-cutting effect pinned for `brands` and `dispensaries`. | PASS |
| D13 docs fix | `docs/data-model.md:497` now reads "unique among non-deleted products (a soft-deleted product does not reserve its `ocpc`)"; `:498` adds "`subcategory_id` must belong to `category_id`"; JPA mapping notes added. `docs/backend-standards.md` gains an accurate "Malformed / Missing Request Body (`400`)" bullet naming KAN-8/D9. | PASS |
| D14 (bonus check) | `@EqualsAndHashCode(onlyExplicitlyIncluded = true)` with `@Include` on `id` only — no `callSuper`, so equality never touches the seven lazy associations. | PASS |

## Strict TDD forwarding — independently spot-checked, not assumed

Checked `git log` per file rather than trusting the `apply-progress.md` narrative.

Findings:

- **No commit in this change adds a test file ahead of its production code.** Test and production
  code always land in the *same* commit: `8f02cd4` carries `Product.java` **and**
  `ProductRepositoryTests.java`; `65ffce6`/`e5d5a13`/`f4459f2`/`a6425cc` each carry
  `ProductServiceImpl.java` **and** `ProductServiceTests.java`; `bac4527` carries
  `ProductController.java` **and** `ProductControllerTests.java`. Git history therefore **cannot
  independently corroborate** that RED was observed first — it is consistent with TDD but does not
  prove it. The commit spacing for the Section 5 guards (16:48:17 → 16:50:55 → 16:52:10 → 16:56:06,
  i.e. 75–158 s apart) is at least *consistent* with real per-guard RED→GREEN cycles.
- **Section 8 was explicitly not TDD, and the artifacts say so.** `6ab6474` adds
  `ProductEndpointsTests.java` alone with zero production changes, and tasks.md 8.3–8.12 record
  "Passed on first real run … no wiring gap found, so no fix was needed" for every test. This is
  legitimate wiring-level verification of already-built behaviour (tasks.md heads the section "TDD
  wiring level"), but 19 of the 108 tests are tests-after-implementation. Same for `c69a828`
  (Section 9's handler unit test, disclosed in tasks.md as "a disclosed no-op GREEN").
- **The three corrective fixes are all real and verifiable in history**, which materially raises my
  confidence in the artifact set:
  1. The retracted task-7.3 "git stash RED verification" claim: I confirmed the commit timing that
     triggered the retraction — `0649b9e` at 17:24:47 and `e0e238e` at 17:24:55, **8 seconds apart**,
     which indeed rules out a stash-based Maven cycle. The retraction is correct and the file now
     says so plainly.
  2. The self-caused `DispensaryServiceTests` regression: fixed in `00fc0cc`
     (`lazyInit` on the `@ComponentScan`), and `DispensaryServiceTests` passes in my own run.
  3. The understated SpotBugs count: corrected in-file, and my independent count matches the
     corrected figures exactly.
- I was **unable to re-execute** the task-7.3 handler-removal RED experiment myself (mutating
  production source was not permitted in this session). I verified it structurally instead: with the
  D9 handler absent, `@ExceptionHandler(Exception.class)` is the only remaining match for
  `HttpMessageNotReadableException`, and `GlobalExceptionHandlerTest.handleGeneric_shouldReturn500`
  confirms that catch-all yields `500` + `"An unexpected error occurred"`. The claimed RED state is
  therefore structurally sound, and both regression tests do assert `400` + the static message.

Overall: the TDD *narrative* is unusually well disclosed (every no-op GREEN and every collapsed RED
is named in-file), but it remains **narrative-backed rather than history-backed**. That is a process
observation, not a defect in the delivered code.

## Carried risks — independently assessed

**(a) The 9 pre-existing SpotBugs findings in `Brand`/`Dispensary` — CONFIRMED pre-existing and unrelated.**

- `git diff --stat main -- Brand.java Dispensary.java` is **empty**: both files are byte-for-byte
  identical to `main`.
- `mvn -o spotbugs:check` on the **feature branch** (against freshly compiled classes):
  `Total bugs: 9`, all `EI_EXPOSE_REP`/`EI_EXPOSE_REP2` in those two files only.
- `mvn -o spotbugs:check` on **`main`** (same procedure, after removing a bad first attempt via a
  `git worktree` that produced a *false* `BUILD SUCCESS` because the `git-commit-id` plugin fails in
  a worktree and no classes were ever compiled): `Total bugs: 9`, `BUILD FAILURE`.
- Conclusion: the SpotBugs gate was **already red on `main`** before this change. KAN-8 does not
  worsen it. The new `spotbugs-exclude.xml` block is correctly scoped by `<Class name>` to exactly
  `Product` and `Subcategory` (not by package), so it does not silently absorb the pre-existing nine.
- The claim in tasks.md 13.3, design.md's Risks section and proposal item 10 is **accurate**.

**(b) The OCPC uniqueness race — CONFIRMED genuinely disclosed, not silently ignored.**

Disclosed in four places: proposal open item 8; proposal Deferred ("partial unique index … separate
migration ticket"); design.md Risks (with the exact `CREATE UNIQUE INDEX CONCURRENTLY … WHERE
deleted_at IS NULL` remedy and an explicit instruction *not* to paper over it with a broader
isolation level, cross-referenced from D6); design.md Open Question 1 (scheduling only).
I independently confirmed the race is real and unmitigated at the DB level: `\d products` shows only
`products_pkey`, no unique or plain index on `ocpc`. Also confirmed the secondary consequence the
design flags — `existsByOcpc` is a full scan. Disclosure quality: good. See S1 for the one nuance.

## Issues

### CRITICAL

None. No functional defect, no spec violation, no failing test, no schema drift, no data-integrity
problem, and no regression against `main` was found.

### WARNING

**W1 — `apply-progress.md` is missing its Batch 5 entry, and two artifacts contain dangling
references to it.**
The file is titled "(Batch 1 of 5)" and contains headings for Batches 1–4 only (Sections 0–7).
**Sections 8–14 have no apply-progress record at all** — that is the largest and most
consequential chunk of the change: the 19 endpoint integration tests, both mandatory Step N+1/N+2
reports, the D13 documentation corrections, and the task-13.3 SpotBugs decision. Two artifacts
forward-reference a record that does not exist:
- `design.md` Risks: "*see apply-progress.md batch 5 for the full reasoning and the disclosed limits
  of what 'confirm the build passes with SpotBugs enabled' could mean here*";
- `spotbugs-exclude.xml`'s new comment: "*those predate KAN-8 and remain a separate, undecided
  follow-up — see apply-progress.md batch 5*".

Under this project's core OpenSpec principle (documentation is the source of truth) and CLAUDE.md §7,
this is a genuine traceability defect: the reasoning behind the single most contested decision in the
change (scoping the SpotBugs exclusion) is pointed at a document section that was never written.
The reasoning *does* survive in tasks.md 13.3 and design.md's Risks section, so nothing is
irrecoverably lost — but the cross-references are broken and Sections 8–14 have no batch narrative.
**Recommended fix before archive:** add the Batch 5 entry (or retarget both references to
tasks.md 13.3 / design.md Risks).

**W2 — Spec scenario 4 is only partially tested; the two `@Size` bounds have zero coverage anywhere.**
Requirement 2 normatively requires rejection "*when `ocpc` exceeds 64 characters; when `title`
exceeds 255 characters*", plus non-positive `contentValue`/reference ids and negative `cbd`.
The annotations are all correctly present, so the *behaviour* is implemented — but I searched the
entire test tree and found **no test that submits an over-long `ocpc` or `title`**, and none for
`contentValue = 0`, `brandId = 0`, or negative `cbd`. The existing disclosure (tasks.md 6.3/6.4 and
apply-progress batch 3 deviation 3) argues these "exercise the same validation annotations … no
untested *mechanism*". That reasoning holds for `@Positive` (pinned via `formatValue`) and
`@PositiveOrZero` (pinned via `thc`), but **not for `@Size`**, which is a distinct constraint with no
test instance at all. A wrong bound (e.g. `@Size(max = 6)`) or a missing `@Size` would pass the whole
108-test suite today. Cheap fix: two controller-unit tests asserting 400 for `"a".repeat(65)` /
`"a".repeat(256)`.

**W3 — The "90% coverage gate" asserted in tasks.md and design.md does not exist in this project.**
Independently confirmed against `pom.xml`: JaCoCo is bound only to `prepare-agent` and
`prepare-agent-integration`; there is no `jacoco:check` execution, so **no Maven goal enforces any
coverage threshold**. tasks.md 13.2 corrects this honestly and in detail, but the stale claim remains
uncorrected in three other places: tasks.md's working rules ("Coverage gate: 90% for branches,
functions, lines and statements (`mvn test`)"), design.md's Testing Plan ("90% coverage gate") and
design.md's Migration Plan ("`mvn test` green with the 90% gate"). Additionally, `jacoco.exec` and
`jacoco-it.exec` are never merged, so no single coverage figure exists for the change — the 100%
instruction-coverage figure cited in 13.2 is a Surefire-only snapshot. Either add a real
`jacoco:check` execution or correct design.md's two statements so the artifacts stop asserting a
gate that is not enforced.

**W4 — `mvn verify` does not pass on this branch without `-Dspotbugs.skip=true`.**
Because `spotbugs:check` is bound to `process-resources` and `failOnError` is `true`, the default
build is red (9 findings). I confirmed this is **equally true on `main`**, so it is a pre-existing
condition and not a regression — but it does mean the story's DoD item 9 ("code passes
Spotless/SpotBugs") is not literally satisfied on this branch, and any CI relying on a bare
`mvn verify` would fail. Fully and accurately disclosed in proposal item 10, tasks.md 13.3/14.1 and
design.md's Risks. Flagged here so the archive decision is made with eyes open, not so it blocks.
(`mvn -o -Dspotbugs.skip=true spotless:check` is clean.)

### SUGGESTION

**S1 — `spec.md` itself carries no caveat about the OCPC race.** Requirement 4 states the invariant
absolutely ("`ocpc` … SHALL be unique across all products that are not soft-deleted"), which the
implementation cannot guarantee under concurrent requests. The limitation is disclosed in
`proposal.md` and `design.md` but not in the behaviour contract that downstream consumers read.
Consider a one-line note in R4 (or accept that specs state intent and the deferred index closes it).

**S2 — The `DataIntegrityViolationException` handler still echoes `ex.getMessage()`**, which is in
direct tension with scenario 18. Correctly out of scope here (the file is otherwise untouched, all
deterministic product paths reject before `save`, and design.md's Risks documents both the
reachability argument and the tracked security follow-up). Note that scenario 18's leak scan only
covers the 404 path, so the one handler that *can* leak is the one never asserted against. Keep the
security follow-up visible after archive.

**S3 — Minor doc inconsistency:** tasks.md 8.14 records the OpenAPI check as
`curl -s localhost:8080/v3/api-docs`, while the Step N+2 report records
`curl -s localhost:8080/api-docs`. Cosmetic; harmless.

**S4 — TDD auditability.** Because every RED/GREEN pair was squashed into one commit, future
verification of TDD compliance is impossible from history alone. Committing the failing test
separately (even as a fixup later squashed) would make the project's core TDD principle
independently auditable rather than narrative-dependent.

**S5 — `ProductEndpointsTests.tearDown` uses unconditional `DELETE FROM <table>`** on nine tables
rather than deleting only its own seeded ids. Safe against the current all-zero test-database
baseline (verified again after my run), but it would silently wipe pre-existing rows if that database
were ever seeded. Consider scoping the cleanup to the ids created in `setUp`.

## Verdict

**PASS WITH WARNINGS**

The implementation genuinely satisfies the behaviour contract. All 108 tests (50 unit + 58
integration) pass in a fresh run performed by this agent, exactly matching the claimed counts. All 8
spec requirements are implemented as specified; 17 of 18 scenarios are fully traced to tests that
genuinely assert what the scenario requires. All seven checked design decisions (D1, D2, D3, D4, D6,
D9, D13) are truly reflected in the code, not merely claimed. Both carried risks are accurate: the 9
SpotBugs findings are provably pre-existing (confirmed red on `main` too, with the two files
byte-identical), and the OCPC race is genuinely and repeatedly disclosed. The database is restored to
its baseline and there is zero schema drift.

Nothing found is a blocker. The four warnings are artifact-hygiene and test-coverage gaps rather than
functional defects. **W1 (the missing Batch 5 apply-progress entry plus two dangling references to it)
should be fixed before `opsx-archive`**, since it is precisely the kind of documentation gap this
project's core principle exists to prevent. W2 and W3 are cheap to close and would be reasonable to
fold into the same pass; W3 in particular leaves two artifacts asserting an enforcement gate the
build does not have.

---

## Result contract

- **status**: `PASS WITH WARNINGS`
- **executive_summary**: Independently re-ran the full suite and reproduced the claimed 108 green
  tests exactly (50 Surefire + 58 Failsafe). All 8 spec requirements are genuinely implemented and
  17/18 scenarios trace to tests that really assert the scenario's content (scenario 4 is partial:
  the `@Size` bounds on `ocpc`/`title` are implemented but untested anywhere). D1, D2, D3, D4, D6, D9
  and D13 are all verifiably present in the code. Both carried risks check out: the 9 SpotBugs
  findings are provably pre-existing — `spotbugs:check` fails identically on `main`, and
  `Brand.java`/`Dispensary.java` are byte-identical to it — and the OCPC race is disclosed in four
  places with a named remedy. Database restored to its zero-row baseline, Flyway still at `0.1.0`, no
  index added to `ocpc`, no schema drift. No CRITICAL findings. Four warnings, the most substantive
  being that `apply-progress.md` never received its Batch 5 entry even though `design.md` and
  `spotbugs-exclude.xml` both forward-reference it, leaving Sections 8–14 without an apply record.
  On TDD: the artifacts' disclosures are unusually honest (all three prior corrective fixes are real
  and verifiable, including the 8-second commit gap that justified retracting the git-stash claim),
  but because every RED/GREEN pair was squashed into a single commit, TDD compliance is
  narrative-backed rather than history-backed, and Section 8's 19 endpoint tests were openly written
  after the implementation.
- **artifacts**:
  - `/Users/sjavierexposito/tmp/openspec-demo/openspec/changes/KAN-8-create-product/reports/2026-09-10-verify-report.md` (this report, created)
  - Commit `2c1d649` — `docs(KAN-8): scope the SpotBugs exception in proposal.md open items` (housekeeping)
  - Read and verified: `proposal.md`, `specs/products-management/spec.md`, `design.md`, `tasks.md`,
    `apply-progress.md`, both mandatory reports, `ProductServiceImpl.java`, `ProductController.java`,
    `ProductApi.java`, `CreateProductRequest.java`, `CreateProductResponse.java`, `Product.java`,
    `GlobalExceptionHandler.java`, `ProductServiceTests.java`, `ProductControllerTests.java`,
    `ProductEndpointsTests.java`, `GlobalExceptionHandlerTest.java`, `pom.xml`,
    `spotbugs-exclude.xml`, `docs/data-model.md`, `docs/backend-standards.md`
- **next_recommended**: `opsx-code-review`. Before `opsx-archive`, close **W1** (add the Batch 5
  apply-progress entry, or retarget the two dangling "batch 5" references in `design.md` and
  `spotbugs-exclude.xml`). Optionally fold in **W2** (two `@Size` boundary tests) and **W3** (correct
  design.md's two "90% gate" statements or add a real `jacoco:check`) in the same pass. Per
  CLAUDE.md §7, any such change is an artifact update first, then code.
- **risks**:
  - *Pre-existing, accepted*: `mvn verify` / `spotbugs:check` red on 9 `EI_EXPOSE_REP*` findings in
    `Brand`/`Dispensary` — verified identical on `main`; not introduced here, out of scope per the
    proposal's Impact boundary.
  - *Accepted, disclosed*: OCPC uniqueness is application-level and racy until the partial unique
    index lands; confirmed no index exists on `products.ocpc`. Blast radius is a duplicate row, not
    corruption.
  - *Artifact hygiene*: missing Batch 5 apply record + two dangling cross-references (W1).
  - *Test gap*: `@Size(max=64)`/`@Size(max=255)` bounds unexercised, so a wrong or missing bound would
    not be caught by the suite (W2).
  - *Process*: no enforced coverage gate despite three artifact statements asserting one (W3).
  - *Latent, tracked*: `DataIntegrityViolationException` handler echoes `ex.getMessage()`; unreachable
    on deterministic product paths but in tension with scenario 18 (S2).
  - *DDD*: D2 knowingly violates aggregate rule 3 (object references to `Brand`/`Strain`); structural
    guard rails (LAZY, no cascade, no orphanRemoval, no inverse collections) verified present, so the
    create transaction stays single-aggregate.
- **skill_resolution**:
  - **`test-driven-development`** — Loaded and applied as the audit lens, per the skill's "if you
    didn't watch the test fail, you don't know if it tests the right thing". Rather than accepting
    "RED confirmed" claims, I checked `git log` per file and found that no commit isolates a test
    ahead of its production code, so the skill's central guarantee is not independently verifiable
    from history (W-level process observation S4). I applied the skill's "Good Tests" criteria to the
    tests themselves and they hold up well: names follow `should_[behavior]_when_[condition]`,
    assertions are against real behaviour (native SQL row/column checks, real PostgreSQL, real
    `@RestControllerAdvice`) rather than against mocks, and edge cases are broadly covered — with the
    one genuine hole the skill's "edge cases and errors covered" checkbox exposes: the `@Size` bounds
    (W2). I also used the skill's standard to judge Section 8 honestly as verification-after-the-fact
    rather than TDD, which the artifacts themselves already state.
  - **`domain-driven-design`** — Loaded and applied to the model. `Product` is a proper aggregate
    root (identity-based equality via D14, soft-delete, single `save` per transaction); the four
    lookup entities are correctly modelled as reference data with no lifecycle; ubiquitous language is
    respected, including keeping the business term `Collection` despite the `java.util.Collection`
    clash (D11). The knowing violation of aggregate rule 3 (D2, object references to the `Brand` and
    `Strain` aggregates) is disclosed in the proposal, the design and `Product.java`'s own class
    comment, and I verified the compensating guard rails really exist in the mapping — so the
    consistency boundary is preserved behaviourally even though the mapping is not DDD-pure. The
    anemic-model trade-off (D15 — the taxonomy invariant lives in the service because
    `DomainException` sits in the application layer) is a real Clean Architecture constraint, honestly
    reasoned, with the correct follow-up recorded. DDD adherence assessed at roughly 7/10: strong
    aggregate/lookup separation and ubiquitous language, held back by the by-reference deviation and
    the invariant living outside the root — both deliberate, documented and with named follow-ups.
