# Code review: kan-16-spring-cloud-aws-foundation

- Change: `kan-16-spring-cloud-aws-foundation`
- Date: 2026-09-24
- Reviewer: code-review sub-agent
- Schema: `story-sdd` (per openspec/config.yaml)
- Verdict: **PASS WITH GAPS**

## Skill resolution

| Skill | Status | Application to this review |
|---|---|---|
| code-auditing | loaded | 6-phase lens: discovery (diff stat + untracked files), file-by-file analysis of `SqsConfig` / `AwsSqsProperties` / messaging classes / S3 migration, best-practice check, pattern detection, report |
| adversarial-review | loaded | Refute-don't-rubber-stamp pass per acceptance criterion; spec-vs-code mismatches as first-class findings; severity classification; verdict |
| solid-principles | loaded | SRP/OCP/LSP/ISP/DIP checklist on new classes |
| dry-principle | loaded | Single-source-of-truth check per knob; Rule-of-Three on the 4-key gap record |

## Scope

- Code changes: working tree vs HEAD — modified `pom.xml`, `spotbugs-exclude.xml`, `S3BlobStorageAdapter.java`, `AwsS3Properties.java`, `S3Config.java`, main+test `application.yml`, related tests; untracked `AwsSqsProperties.java`, `SqsConfig.java`, `infrastructure/messaging/sqs/*` (3 classes), unit tests (`AwsSqsPropertiesTests`, `SqsConfigTests`, `SqsPropertiesBindingTests`, messaging `sqs/*Tests`), integration tests (`SqsContainerIntegrationTests`, `SqsFailLazyIntegrationTests`), fixture `sqs/s3-notification.json`.
- Specs: `openspec/changes/kan-16-spring-cloud-aws-foundation/specs/spring-cloud-aws-foundation/spec.md`, `specs/aws-s3-integration/spec.md`.
- Design: `openspec/changes/kan-16-spring-cloud-aws-foundation/design.md`.
- Verify report: `verify-report.md` (PASS WITH WARNINGS, 209 unit tests green, DoD greps clean, W1 + W2, S1 + S2).
- Apply progress: `apply-progress.md` (11 justified deviations).

## Spec and task alignment

- All `tasks.md` phases marked `[x]`; spot checks confirm the claimed artifacts exist (customizer, callback, executor, factory, ternary both branches, converter + poison test, seam, container, main+test YAML, round-trip + fail-lazy + empty-queue integration tests, README + backend-standards updates).
- Independently re-verified: `mvn -q spotless:check` EXIT 0; no `SqsAsyncClient.builder()` / `S3AsyncClient.builder()` / `DefaultCredentialsProvider.create()` / `StaticCredentialsProvider` / `SqsTemplate` in `src/main`; no vendor imports in `domain/` or `application/`; main-code diff touches infrastructure only; no new migration files; `spring.cloud.aws.credentials.*` absent from main YAML (only a comment noting intentional absence).
- Vendor imports in `src/main` are confined to `infrastructure/config`, `infrastructure/messaging/sqs`, and the pre-existing `infrastructure/adapters/storage/S3BlobStorageAdapter` (required SDK consumer; see W1).
- Logging audit: callback logs counts + `throwable.getClass().getName()` only (strictly stronger than the design sketch's `toString()`); listener logs `getRecords().size()` only; no payload/key/URL/account-ID logging.

## Findings

| Severity | Area | Finding | Evidence | Suggested fix (code / spec / tests) |
|----------|------|---------|----------|--------------------------------------|
| Minor | Spec drift | Spec `spring-cloud-aws-foundation` line 62 still names `JacksonJsonMessageConverter`, but the implementation (necessarily) uses a custom `S3EventMessageConverter` — `JacksonJsonMessageConverter` does not exist in 4.x and plain Jackson cannot bind `S3Event` (spike-proven). Intent preserved, poison path tested. | `spec.md:62` vs `SqsConfig.java:99` + `S3EventMessageConverter.java` | Spec: update the requirement text to name the custom converter + canonical Lambda serialization (one-line touch-up at archive). |
| Minor | DoD wording (W1, inherited) | Task 5.2 DoD item (f) — "no vendor import outside `infrastructure/config` + `infrastructure/messaging/sqs`" — is violated in letter by the pre-existing `S3BlobStorageAdapter` (`software.amazon.awssdk` imports), which MUST import the SDK to implement the storage port and whose operation the s3-integration spec requires. No NEW scattering; no domain/application leakage. | `S3BlobStorageAdapter.java:37-46` | Spec/docs: reword DoD to "no vendor import outside `infrastructure/` (none in domain/application)" at archive. |
| Minor | Robustness | `S3EventPayloadConverter.convertFromInternal` casts blindly to `byte[]` when the payload is not a `String`. A non-String/non-byte[] payload throws raw `ClassCastException` instead of `MessageConversionException`. Impact is contained (still an exception → ack withheld → redrive → DLQ, never swallowed or wrongly acked), but the poison-path contract deserves the uniform exception type. | `S3EventMessageConverter.java:57-60` | Code (follow-up): `instanceof` chain with `else throw new MessageConversionException(...)`; add a unit test for the third payload shape. |
| Minor | DIP (negligible) | `sqsListenerContainerFactory` instantiates `new S3EventMessageConverter()` inline rather than injecting it. Acceptable (stateless infra adapter, no swap seam, covered via factory tests) but noted for completeness. | `SqsConfig.java:99` | Code (optional): declare the converter as a `@Bean` and constructor-inject. Not required before archive. |
| Minor | Process (W2, inherited) | Failsafe/LocalStack round trip, fail-lazy boot, and full `mvn verify` accepted from apply-progress testimony (213 surefire + 90 failsafe green); not independently re-executed (no LocalStack/Postgres here). Unit scope (209 tests) + spotless + greps re-verified independently. | `verify-report.md` W2; `SqsContainerIntegrationTests.java`, `SqsFailLazyIntegrationTests.java` exist | CI must run full `mvn verify` with LocalStack up before merge (S1: regenerates the stale JaCoCo report too). |
| Question | Assumption | `applyContainerOptions` null-guards each `listener.*` knob but assumes `sqsProperties.getListener()` itself is non-null. True for the library default; a custom `SqsProperties` bean returning null would NPE at startup (fail-fast, not silent). | `SqsConfig.java:88,104-118` | Confirm by inspection that no custom `SqsProperties` bean exists (none found); no action unless the team wants a defensive `Assert.notNull`. |

### Assessed and accepted (no action)

- **W1/W2**: assessed above as Minor/process findings; neither blocks archiving. W1 is a wording issue, not a code issue; W2 is covered by CI gating.
- **Deviations 1–11** (apply-progress): all preserve spec intent — BOM `4.1.1` (tasks mandate re-verification), `aws-lambda-java-serialization` + `aws-crt-client` (proven necessity), custom converter, adaptive backpressure factory + `AUTO`, dual executors via `TaskExecutorAdapter`, observation ternary inside `configure`, `setPayloadDeserializationType` (correctness fix), test `max-messages-per-poll: 2` (library validation), exception-type logging (stronger), no `@ConditionalOnProperty` (design-compliant), `S3Config` 0% coverage (registration-only convention). Concur with verify-report ACCEPTED/COMPLIANT assessments.
- **TDD discipline**: `should_[expected]_when_[condition]` naming pervasive; RED-first discipline witnessed per apply-progress testimony; cannot independently re-witness RED post-hoc — accepted.
- **Option A migration intactness**: `S3Config` is registration-only; slim `AwsS3Properties(bucket, presignTtl)`; adapter updated to the slim record with no logic change; `S3ConfigTests` retired with reason recorded. Blob-storage regression covered by updated suites per apply testimony.
- **Backpressure AUTO + virtual threads + observability ternary + converter poison path + fail-lazy**: all match design D4–D6 intent modulo documented 4.1.1 API spelling; both ternary branches tested.

## Verdict

**PASS WITH GAPS** — no Blockers, no Majors. Four Minor findings (spec converter wording, DoD wording, byte[] cast hardening, inline converter instantiation) plus one process gate (CI full `mvn verify` with LocalStack) and one confirm-only Question. All are trackable follow-ups; none invalidates the foundation, the migration, or the security posture.

## Recommended next steps (before archive)

- [ ] Touch up `specs/spring-cloud-aws-foundation/spec.md` line 62 to name `S3EventMessageConverter` (canonical Lambda serialization) instead of `JacksonJsonMessageConverter`.
- [ ] Reword task 5.2 DoD item (f) to "no vendor import outside `infrastructure/` (none in domain/application)".
- [ ] Record the 4.1.1 API spelling deltas in the PR (verify-report S2) so KAN-13 consumers inherit the breadcrumbs.
- [ ] Ensure CI runs full `mvn verify` with LocalStack up before merge (covers W2 + regenerates JaCoCo per S1).
- [ ] Optional (may follow archive): harden the converter `byte[]` cast + unit test; consider a converter `@Bean` for DIP purity.
