# KAN-14 S3 Client Beans — Archive Report

- **Change:** `KAN-14-s3-client-beans` — Reusable AWS S3 foundation (injectable `S3Client` + `S3Presigner` beans, LocalStack-overridable endpoint)
- **Schema:** `story-sdd`
- **Branch:** `feat/KAN-14-s3-client-beans-backend`
- **Archived on:** 2026-09-20
- **Archived to:** `openspec/archive/2026-09-20-KAN-14-s3-client-beans/`

## Summary

The KAN-14 S3 Client Beans change was archived. The full SDD pipeline completed with the following results:

- **Proposal phase:** Scope is the reusable AWS S3 foundation unblocking `product_images` work (deferred by KAN-8); non-goals explicitly named (domain port + adapter, upload endpoint, SQS consumer, health indicator, bucket hardening, Testcontainers module, async client).
- **Spec phase:** 1 requirement, 8 scenarios (S1 beans for injection, S2 override applied, S3 default AWS endpoint, S4 region, S5 default-chain credentials, S6 round-trip, S7 presigned URL, S8 fail-lazy startup), all concrete and testable.
- **Design phase:** 7 decisions (D1–D7) fully documented with alternatives considered and trade-offs named, plus mermaid architecture diagram, migration plan, and risk mitigations.
- **Tasks phase:** 31 checkboxes across sections 0–7, all complete (31/31 [x]).
- **Apply phase:** All 7 phases delivered per `apply-progress.md` — setup + branch, BOM-first build-gate flush (no duplicate-finder conflict), `AwsS3Properties` record, `s3Client()` both override directions, `s3Presigner()` with path-style service configuration, yml wiring + env vars, 4/4 LocalStack integration tests, infra/docs/gates. Two documented deviations: (a) unit presign test uses `aws.accessKeyId` system properties instead of `@SetEnvironmentVariable` because surefire lacks the `--add-opens` flag; (b) `S3ConfigIntegrationTests` mirrors (not extends) `EndpointIntegrationTest` annotations because the base class is package-private. Final `mvn verify`: 66 unit + 63 integration, BUILD SUCCESS; JaCoCo 100% on both new classes.
- **Verify phase:** `success`, zero CRITICAL. Notes carried: W1 equivalent (NonVoidMethodCall) mutants on builder-chain calls — SDK defaults apply when removed, `mutationThreshold=0` gate passes; W2 pre-existing init-script exit 127; W3 tmp-file audit note; S1 surefire add-opens idiom (unit tests correctly use `System.setProperty`, `@SetEnvironmentVariable` confined to failsafe).
- **Code Review phase:** `PASS WITH GAPS` (status success) — 5 minors + 1 suggestion, all non-blocking (S8 automated pin missing, `System.setProperty` global-state idiom, unconditional `@AfterEach` cleanup, fully-qualified presign type, `bucket` without `@NotBlank`, `wait-for-it -t 10` suggestion). No code changes required before archive.

The change adds a new **`aws-s3-integration`** capability. Because it is a NEW capability with no existing spec behavior changes, its delta spec was sync-promoted into the main OpenSpec spec store as canonical content, exactly as precedent at KAN-6 and KAN-8.

## Decisions (final state)

- **D1** Placement `infrastructure/config/`, no new layers — `AwsS3Properties` + `S3Config` next to `JpaConfig`; no port/adapter (YAGNI, no consumer yet).
- **D2** `AwsS3Properties` immutable record with null-safe `hasEndpointOverride()`; registered via `@EnableConfigurationProperties` on `S3Config`; empty-string → `null` URI binding relied upon deliberately and pinned by test.
- **D3** Two singleton beans, `Region.of(...)` + `DefaultCredentialsProvider.create()` per bean, conditional override; **D3a** presigner path-style via `S3Configuration.pathStyleAccessEnabled` (no `forcePathStyle` on presigner); **D3b** no hardcoded credentials; **D3c** Spring-inferred `close()` destroy method, no override; **D3d** fail-lazy offline builders; **D3e** single `INFO` log of override endpoint only.
- **D4** Env-driven `application.yml` (main empty-endpoint ⇒ real AWS; test LocalStack `http://localhost:4566` path-style); `AWS_ENDPOINT_URL_S3` name shared with SDK/CLI; `bucket`/`presign-ttl` declared with no consumer to stabilise config surface.
- **D5** BOM `2.46.7` + `s3` artifact only; strict gates all green.
- **D6** `wait-for-it localstack:5432` → `localstack:4566` flake fix; README + backend-standards docs updated.
- **D7** TDD strategy: unit both override directions (kills `NEGATE_CONDITIONALS`), presigned path-prefix unit test, LocalStack integration (inject, listBuckets, round-trip, credential-free presigned fetch), regression green.

## Spec Sync

The delta spec `specs/aws-s3-integration/spec.md` (wrapper `## ADDED Requirements`) was applied to the main specs as a new canonical capability:

- **New canonical spec:** `openspec/specs/aws-s3-integration/spec.md`
  - Form: canonical `## Requirements` (delta `## ADDED Requirements` wrapper stripped per OpenSpec convention).
  - Content: 1 requirement / 8 scenarios, preserved verbatim from the delta body.
  - Purpose section: retained verbatim from delta, as specified in the schema for new capabilities.
  - No existing specs for other capabilities were modified (`brands-management`, `products-management` untouched).

## Specs Synced

| Domain | Action | Details |
|--------|--------|---------|
| aws-s3-integration | Created | 1 requirement / 8 scenarios added to canonical spec |

## Archive Contents

Preserved verbatim from `openspec/changes/KAN-14-s3-client-beans/`:

- `proposal.md` ✅
- `design.md` ✅
- `tasks.md` ✅ (31/31 checkboxes complete, sections 0–7)
- `apply-progress.md` ✅ (phases 0–7, both deviations documented, no-commit boundary noted)
- `specs/aws-s3-integration/spec.md` ✅ (delta spec retained in archive; canonical spec created separately in `openspec/specs/`)
- `reports/` ✅
  - `2026-09-20-code-review.md` (PASS WITH GAPS: 5 minors + 1 suggestion, non-blocking)

## Source of Truth Updated

The canonical spec store now reflects the new behavior:

- `openspec/specs/aws-s3-integration/spec.md` — new AWS S3 Integration capability (source of truth).

Technical documentation per `docs/documentation-standards.md` was updated inside the change itself (no further doc action at archive): `README.md` (`aws.*` table + LocalStack workflow), `docs/backend-standards.md` (Technology Stack registers S3 / AWS SDK v2). `docs/data-model.md` unaffected (no entities, no migrations).

## Verification and Code-Review History

### Verification (opsx-verify): `success`, zero CRITICAL

- **Tests:** 66 unit (surefire) + 63 integration (failsafe), `mvn verify` BUILD SUCCESS.
- **Spec compliance:** 8/8 scenarios traced (S1–S8); override-set AND override-absent tests kill all `NEGATE_CONDITIONALS` mutants on both `if (hasEndpointOverride())` branches.
- **Notes (non-blocking, carried as follow-ups):**
  - **W1** — surviving NonVoidMethodCall (equivalent) mutants on builder-chain calls; SDK defaults apply when a call is removed; `mutationThreshold=0` gate passes. Follow-up: targeted pitest `targetTests` scoping if the team wants a tighter mutation signal on the new classes.
  - **W2** — pre-existing init-script exit 127 (line-9 cleanup candidate; pre-existing, not introduced by this change).
  - **W3** — tmp-file audit note (informational).
  - **S1** — surefire add-opens idiom: unit tests correctly use `System.setProperty` (no add-opens needed under surefire); `@SetEnvironmentVariable` confined to failsafe integration tests. Follow-up: migrate to junit-pioneer `@SetSystemProperty`/system-stub only if parallel surefire execution is ever enabled.

### Code Review (opsx-code-review): `PASS WITH GAPS` (status success)

All findings Minor/Suggestion, non-blocking; no code changes required. Carried as follow-ups:

- **M1 (S8)** — no automated test pins fail-lazy boot with unreachable endpoint (manual step 7.3a only). Follow-up: unit test asserting both beans build while endpoint points at an unroutable port (e.g. `http://localhost:9`).
- **M2** — `S3ConfigTests` mutates global JVM state via `System.setProperty/clearProperty` (acceptable sequential; revisit under parallel surefire).
- **M3** — `@AfterEach cleanUp()` issues `deleteObject` even for non-writing tests; harmless (idempotent), optionally guard with a written-flag.
- **M4** — fully-qualified `GetObjectPresignRequest` inline; import nit for a follow-up.
- **M5** — `bucket` carries no `@NotBlank`; intentional (no consumer yet). Add `@NotBlank` when the first consumer story lands.
- **S1** — `wait-for-it -t 10` remains short for slow machines; consider `-t 30` if bootstrap flakes recur.

## Known Carried-Forward Risks and Limitations

1. **`bucket`/`presign-ttl` declared with no consumer** — stabilises config surface for follow-ups at the cost of temporarily unused config (D4 trade-off, accepted). First-consumer adoption story should add `@NotBlank bucket` validation and wire both properties.
2. **Fail-lazy boot** — app starts with S3 unreachable by design (S8); first-operation failures surface at runtime, not startup.
3. **~12–18 MB jar weight** for the `s3` artifact — accepted; slimming (`url-connection-client` over `netty-nio-client`) deferred.
4. **Bucket security hardening outstanding** — drop public-read, scope CORS (production blocker, separate story).
5. **No auth/health/SQS/async surface** — no health indicator, SQS consumer, or async client introduced (explicit non-goals).

## Follow-ups (not archive blockers)

1. S8 automated fail-lazy pin test (unroutable-port bean-build test).
2. `@AfterEach` conditional cleanup (written-flag guard).
3. Parallel-surefire credential idiom (`@SetSystemProperty`/system-stub migration, only if parallelism enabled).
4. Targeted pitest `targetTests` scoping for tighter mutation signal.
5. Init-script line-9 cleanup (pre-existing exit 127).
6. `bucket`/`presign-ttl` first-consumer adoption (+ `@NotBlank bucket`).

## Commit Boundary (manual step for the user)

No commits were made during the automatic chain (`feat/KAN-14-s3-client-beans-backend` carries all work uncommitted, per auto-chain rule). To finish:

1. Review: `git status --porcelain` and `git diff --stat`.
2. Commit (conventional commits) and push the branch.
3. Open the PR (code review already PASS WITH GAPS; attach verify + review reports).

## SDD Cycle Complete

The change has been fully planned, specified (1 requirement, 8 scenarios), designed (D1–D7), implemented and verified (129 tests green, 0 CRITICAL, strict gates pass), code-reviewed (PASS WITH GAPS, gaps non-blocking and tracked above), and archived. The main spec is promoted to `openspec/specs/aws-s3-integration/spec.md` as the source of truth going forward.

Ready for the next change.
