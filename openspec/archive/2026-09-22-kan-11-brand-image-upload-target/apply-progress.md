# Apply progress — kan-11-brand-image-upload-target

Schema `story-sdd` | Branch `feat/kan-11-brand-image-upload-target` | 28/28 tasks complete.
No commits made (orchestrator automatic-chain rule); all work in the working tree.

## Completed

- [x] T1.1 Branch created from main (`git checkout -b feat/kan-11-brand-image-upload-target`).
  Push of the tracking branch left to the orchestrator (no commits per chain rule).
- [x] T1.2 `BrandImageService` interface created (`BlobUploadTarget createUploadTarget(Long brandId)`);
  `mvn compile` green. No impl, no record, no new package (D2/D4/D5).
- [x] T2.1 `ServiceTest` mock bag extended (`BrandImageRepository`, `BlobStorage`).
- [x] T2.2–T2.9 `BrandImageServiceTests` (10 tests): PENDING captor, exact port-target return,
  404 missing + soft-deleted (`Brand not found with ID: {id}`), `never()` presign on 404,
  `never()` save + propagation on presign failure, `BRAND_IMAGE` captor, `ListAppender` no-URL
  pin, D8 non-canonical-key `IllegalArgumentException` pin. RED watched (test-compile failure on
  missing `BrandImageServiceImpl`) before GREEN (`BrandImageServiceImpl` per enriched §5.4).
- [x] T2.10 Regression: `BrandServiceTests` (12) + `ProductServiceTests` (17) +
  `DispensaryServiceTests` (1) green, unchanged.
- [x] T3.1 `GlobalExceptionHandlerTest` 502 case (RED watched: 500 via `handleGeneric`) +
  `BlobStorageException → 502` fixed-message handler (`Blob storage is currently unavailable`,
  `log.error` keeps stack trace). 5/5 handler tests green.
- [x] T4.1 `BrandImageControllerTests` (7 tests, RED watched: missing controller):
  201 exact JSON (3 keys, `PUT`, string `expiresAt`), D2 absence guard
  (`id`/`brandId`/`imageKey`/`status`), `no-store`, 404/400/502/405.
  GREEN: `BrandImageApi` (`/api/brands/{brandId}/images`, `@Tag("Brand Images")`, no `@RequestBody`),
  `CreateBrandImageResponse` (3 `String`s, placeholder OpenAPI example),
  `BrandImageController` (service → DTO via `url().toString()` / `method().name()` /
  `expiresAt().toString()`, `Cache-Control: no-store`, never touches entity, no `Location`,
  never logs URL).
- [x] T4.2 Exact-JSON traps verified; full controller package green
  (`BrandControllerTests` 9, `ProductControllerTests` 13 unaffected).
- [x] T5.1–T5.5 `BrandImageEndpointsTests` (6 tests, real Postgres + LocalStack):
  201 + one `PENDING` row (native query), credential-free `HttpClient PUT` → `headObject` at the
  row-read key, twice → distinct URLs/keys/rows, 404 soft-deleted + 404 missing with row counts,
  native `SELECT *` free of `X-Amz-Signature`/`http`. FK cleanup order
  (`brand_images` before `brands`) respected; key read-back only via
  `findByBrandIdAndStatus(..., PENDING)`.
- [x] T5.6 Coverage: 100% lines/branches on `BrandImageServiceImpl`, `BrandImageController`,
  `CreateBrandImageResponse` (JaCoCo); new 502-handler case fully covered.
- [x] T6.1 Full regression: `mvn verify` → 197 unit + 86 integration tests, 0 failures,
  BUILD SUCCESS (one transient KAN-10 failure traced to curl-verification residue in the shared
  local bucket; residue removed, re-run fully green).
- [x] T7.1 `localstack-resources.yml` CORS narrowed (`PUT`, `content-type`, `MaxAge: 3000`,
  `*` local-only with comment); applied live to the local bucket via `put-bucket-cors` and
  confirmed with `get-bucket-cors`. Stack restart for file-driven pickup left to infra flow.
- [x] T8.1 curl report + [x] T8.2 real-Chrome preflight report (`STATUS:200` from
  `http://localhost:3000`), both in `reports/`.
- [x] T9.1 Spec deltas verified present and coherent (`brands-management` requirement +
  error-shape 502 extension, `blob-storage` scope-exclusion narrow fixing the shipped
  "no HTTP endpoint wrapping the port" contradiction against `openspec/specs/`).
- [x] T9.2 `docs/data-model.md` §9 write path + `docs/backend-standards.md` (asset-creation
  convention, 502 mapping, presigned-URL handling incl. 405 note, CORS-as-DoD rule). README has no
  endpoint list → N/A.
- [x] T9.3 Scope gate: diffs only in the 5+3 new files + 4 design-listed modified files (+
  T9.2 docs, T8 reports, `spotbugs-exclude.xml` services entry — see deviations). No migration,
  no new package, no untouched-list diffs.

## Deviations from design.md (3, all principled)

1. `HttpRequestMethodNotSupportedException → 405` handler added to `GlobalExceptionHandler`.
   Design assumed "Spring's default 405 covers AC13 with no code", but the shared
   `Exception → 500` catch-all swallows it (watched: PUT returned 500). Required for AC13.
2. `BrandImageServiceImpl` uses an explicit constructor instead of `@RequiredArgsConstructor`
   (matches `BrandServiceImpl`/`DispensaryServiceImpl` convention; Lombok variant trips
   `EI_EXPOSE_REP2` the same way).
3. `spotbugs-exclude.xml` gains `application.services.* / EI_EXPOSE_REP2`, mirroring the existing
   controllers/storage-adapter entries: constructor-injected collaborators are not exposure bugs
   (the `BlobStorage` port trips the heuristic; Spring-Data repos do not). Result:
   `mvn spotbugs:check` BUILD SUCCESS.

## Environment notes (for verify)

- Integration tests need `--add-opens java.base/java.lang=ALL-UNNAMED
  --add-opens java.base/java.util=ALL-UNNAMED` on this GraalVM JDK, else junit-pioneer
  `@SetEnvironmentVariable` fails for ALL suites (pre-existing, affects main too).
- Local `develop-assets` bucket did not exist in this LocalStack; created manually for the run.
- `mvn test`'s bound `run-spotbugs` was skipped per-run (`-Dspotbugs.skip=true`); standalone
  `mvn spotbugs:check` is green.
- Local verification residue fully cleaned (bucket empty, demo `brands`/`brand_images`/
  `brand_types` at 0 rows, app stopped).
