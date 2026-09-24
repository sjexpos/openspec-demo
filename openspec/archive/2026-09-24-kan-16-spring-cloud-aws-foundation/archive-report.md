# kan-16-spring-cloud-aws-foundation — Archive Report

- **Change:** `kan-16-spring-cloud-aws-foundation` — Spring Cloud AWS async messaging foundation (starter-owned `S3AsyncClient` + `SqsAsyncClient`, one ordered `SqsMessageListenerContainer<S3Event>` for asset events, S3 config migration onto `spring.cloud.aws.*`)
- **Schema:** `story-sdd` (per `openspec/config.yaml`)
- **Branch:** work held uncommitted in dirty tree on `main` (desired shape: `feat/KAN-16-spring-cloud-aws` with conventional commits — see Commit Boundary)
- **Archived on:** 2026-09-24
- **Archived to:** `openspec/archive/2026-09-24-kan-16-spring-cloud-aws-foundation/`

## Summary

The kan-16-spring-cloud-aws-foundation change was archived. The full SDD pipeline completed:

- **Proposal phase:** adopt the managed Spring Cloud AWS async foundation before the first consumer; KAN-13 hand-rolled `@Scheduled` poller explicitly **superseded and NOT implemented**; out of scope: business handling of `S3Event`, REST, Flyway, `SqsTemplate`, DLQ alarms, prod queue-policy scoping.
- **Spec phase:** 1 new capability (`spring-cloud-aws-foundation`, 4 requirements / 11 scenarios) + 1 modified capability (`aws-s3-integration`, 1 requirement / 8 scenarios). Pre-archive touch-up applied: converter requirement names the custom `S3EventMessageConverter` (canonical Lambda serialization) instead of `JacksonJsonMessageConverter` (does not exist in 4.x).
- **Design phase:** 10 decisions (D1–D10) with alternatives, trade-offs, risks R1–R8, mermaid context/component/sequence diagrams, normative container option set with adaptive 4.1.x spelling.
- **Tasks phase:** 27/27 checkboxes complete (`[x]`), 0 remaining.
- **Apply phase:** all phases 1–5 delivered per `apply-progress.md`; final `mvn verify` GREEN (213 surefire + 90 failsafe, incl. `EndpointIntegrationTest` suites and S3 migration suites); 11 justified deviations, all intent-preserving.
- **Verify phase:** `PASS WITH WARNINGS` — 209 unit tests re-run green, spotless clean, DoD greps clean; warnings W1 (DoD wording) + W2 (CI full-verify gate).
- **Code Review phase:** `PASS WITH GAPS` — 4 minors + 1 process gate + 1 confirm-only question, all non-blocking. Pre-archive touch-up applied: task 5.2 DoD item (f) reworded to "no vendor import outside `infrastructure/` (none in domain/application)".

## Decisions (final state — version pins recorded per tasks 1.1)

- **BOM `io.awspring.cloud:spring-cloud-aws-dependencies` = 4.1.1** (newer 4.1.x than the 4.1.0 named at refinement; verified on Maven Central 2026-09-24, latest in the 4.1.x line; SDK BOM stays first in `dependencyManagement`).
- **`com.amazonaws:aws-lambda-java-events` = 3.16.0** (pinned exactly as specified; provides the `S3Event` type).
- **Added (justified, pinned): `com.amazonaws:aws-lambda-java-serialization` = 1.2.0** (canonical `S3Event` JSON mapping, same code path as the Lambda runtime — plain Jackson 2/3 cannot bind `S3Event`, spike-proven) and **`software.amazon.awssdk:aws-crt-client`** (BOM-managed; enables the auto-configured CRT-based `S3AsyncClient` — the plain starter only provides sync `S3Client` + `S3Presigner`).
- **S3Config migration: Option A (delegate to starters)** — `S3Config` is registration-only; `S3Client`/`S3Presigner`/`S3AsyncClient` come from starter auto-configuration; `AwsS3Properties` slimmed to `bucket` + `presignTtl`; `S3ConfigTests` retired with reason recorded.

## API deltas (4.1.1 spelling — option set normative, spelling adaptive, R2)

Recorded for KAN-13 consumer breadcrumbs (verify-report S2):

- `AcknowledgementMode` lives in `...sqs.listener.acknowledgement.handler` (not `...acknowledgement`).
- Backpressure: `backPressureHandlerFactory(adaptiveThroughputBackPressureHandler())` + explicit `backPressureMode(AUTO)`; the plain `throughputBackPressureHandler()` is rejected at runtime by `StandardSqsMessageSource` (requires `BatchAware...`).
- Executor: options carry `componentsTaskExecutor` + `acknowledgementResultTaskExecutor` (no singular `taskExecutor`); both wired to the single virtual-threads bean via `core.task.support.TaskExecutorAdapter`. No `ThreadPoolTaskExecutor` introduced.
- Converter: custom `S3EventMessageConverter extends SqsMessagingMessageConverter` with Lambda-serializer payload mapping (spike-proven necessity).
- Observation: no factory-builder method in 4.1.1; the registry/NOOP ternary lives inside `configure(options -> ...)`; both branches tested.
- Container: `setPayloadDeserializationType(S3Event.class)` is REQUIRED (lambda listener generic erasure → `ClassCastException` without it, proven by round-trip failure).
- Test YAML corrected to `max-messages-per-poll: 2` (library validation requires per-poll ≤ concurrent-messages); callback logs the exception TYPE, not `toString()` (strictly stronger key-material constraint).

## Spec Sync

| Domain | Action | Details |
|--------|--------|---------|
| spring-cloud-aws-foundation | Created | 4 requirements / 11 scenarios added to canonical spec (new capability: async clients, managed container, tuning + observability, fail-lazy + round trip + scope) |
| aws-s3-integration | Updated | 1 requirement / 8 scenarios replaced: source of truth migrated from `aws.region` / `aws.s3.*` to `spring.cloud.aws.*`; slim `bucket` + `presign-ttl` properties; starter-delegated `S3Config` |

Canonical form: delta wrappers (`## ADDED Requirements` / `## MODIFIED Requirements`) stripped to `## Requirements` per OpenSpec convention (archive copies retain the delta wrappers verbatim). No other specs modified (`asset-lifecycle`, `blob-storage`, `brands-management`, `products-management` untouched).

## Archive Contents

Preserved verbatim from `openspec/changes/kan-16-spring-cloud-aws-foundation/`:

- `proposal.md` ✅
- `design.md` ✅
- `tasks.md` ✅ (27/27 checkboxes complete)
- `apply-progress.md` ✅ (phases 1–5, version decisions, 11 deviations, coverage, no-commit boundary noted)
- `specs/spring-cloud-aws-foundation/spec.md` ✅ (delta retained; canonical spec created separately)
- `specs/aws-s3-integration/spec.md` ✅ (delta retained; canonical spec updated separately)
- `reports/` ✅
  - `2026-09-24-verify-report.md` (PASS WITH WARNINGS: 209 unit tests, DoD greps clean, W1 + W2, S1 + S2)
  - `2026-09-24-code-review.md` (PASS WITH GAPS: 4 minors + W2 CI gate + 1 question)

## Source of Truth Updated

- `openspec/specs/spring-cloud-aws-foundation/spec.md` — new canonical capability (source of truth for KAN-13 consumer work).
- `openspec/specs/aws-s3-integration/spec.md` — migrated S3 configuration contract (source of truth, supersedes the KAN-14 `aws.*`-keyed contract).

Technical documentation per `docs/documentation-standards.md` was finalized inside the change itself (no further doc action at archive): `README.md` (Spring Cloud AWS + LocalStack SQS workflow) and `docs/backend-standards.md` (Messaging/SQS convention). `docs/data-model.md` unaffected (no entities, no migrations).

## Verification and Code-Review History

### Verification (opsx-verify): PASS WITH WARNINGS, zero CRITICAL

- **Tests:** 209 unit (surefire, re-executed) + 213 surefire / 90 failsafe accepted from apply-progress (LocalStack round trip, fail-lazy boot, full `mvn verify` — not re-runnable without LocalStack + Postgres here).
- **Spec compliance:** all `spring-cloud-aws-foundation` + `aws-s3-integration` scenarios traced PASS (matrix in verify-report).
- **Design coherence:** DDD Generic Subdomain + ACL seam, SRP/OCP/DIP, DRY — all PASS.
- **Deviations 1–11:** all ACCEPTED/COMPLIANT, intent preserved.

### Code Review (opsx-code-review): PASS WITH GAPS, no Blockers/Majors

- 4 minors + 1 process gate + 1 confirm-only question; concur with verify ACCEPTED assessments; TDD discipline accepted on testimony.

## Follow-ups (not archive blockers)

1. **Converter `byte[]` hardening** — `S3EventPayloadConverter.convertFromInternal` casts blindly to `byte[]` for non-`String` payloads; add an `instanceof` chain ending in `MessageConversionException` + a unit test for the third payload shape. Impact contained (still exception → ack withheld → redrive → DLQ).
2. **Inline converter `@Bean` (optional)** — `sqsListenerContainerFactory` instantiates `new S3EventMessageConverter()` inline; consider declaring it a `@Bean` and constructor-injecting for DIP purity. Not required.
3. **CI full `mvn verify` with LocalStack required before merge** (W2) — covers round trip, fail-lazy boot, regression, and regenerates the stale JaCoCo report (S1).
4. **KAN-13 rework onto this container** — `BrandImageConfirmService` consumes the `AssetEventsListener` seam (`BlobUploadEvent` derivation, URL-decoded key, `BlobType` prefix validation); hand-rolled poller stays superseded; **no poller** in KAN-13.
5. **4.1.1 API spelling breadcrumbs** — record the deltas above in the PR description (§API deltas).

## Commit Boundary (manual step for the user — orchestrator policy: NEVER partial-commit in auto chain)

No commits were made during the automatic chain; the tree is intentionally left dirty. Desired shape:

1. Review: `git status --porcelain` and `git diff --stat` (expect: modified `pom.xml`, `spotbugs-exclude.xml`, `S3BlobStorageAdapter.java`, `AwsS3Properties.java`, `S3Config.java`, main+test `application.yml`, related tests, `README.md`, `docs/backend-standards.md`, canonical specs; deleted `S3ConfigTests.java`; untracked `AwsSqsProperties.java`, `SqsConfig.java`, `infrastructure/messaging/`, SQS unit/integration tests, `sqs/s3-notification.json` fixture, `openspec/` archive + synced specs).
2. On branch `feat/KAN-16-spring-cloud-aws` (currently on `main` — create/switch first), commit with conventional commits (e.g. `feat(sqs): add Spring Cloud AWS async messaging foundation`, plus `docs:` / `specs:` as the team prefers), push, and open the PR.
3. Attach `verify-report.md` + `code-review.md`; ensure CI runs full `mvn verify` with LocalStack up (follow-up 3) before merge.

## Symlink / Hygiene Check (AGENTS.md §5)

- No broken symlinks found (`find . -xtype l` empty); no agent-path artifacts added — nothing to re-link. Canonical specs live in `openspec/specs/`; no duplicated canonical artifacts in agent-specific folders.

## SDD Cycle Complete

The change has been fully planned, specified (4 + 1 requirements, 11 + 8 scenarios), designed (D1–D10), implemented and verified (213 + 90 tests green, 0 CRITICAL, strict gates pass), code-reviewed (PASS WITH GAPS, gaps non-blocking and tracked above), and archived with the source of truth promoted to `openspec/specs/`.

Ready for the next change.
