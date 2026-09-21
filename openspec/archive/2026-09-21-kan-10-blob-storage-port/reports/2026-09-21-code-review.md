# Code Review Report — kan-10-blob-storage-port

- Change: `kan-10-blob-storage-port` (Blob Storage Port + S3 Adapter)
- Date: 2026-09-21. Reviewer: `code-review` sub-agent (adversarial red-team pass).
- Schema: `story-sdd` (`openspec/config.yaml`). Position: apply → verify → **code-review** → archive.
- Verdict: **PASS WITH GAPS** (no Blocker, no Major; 4 Minor + 2 Questions — none block archive).
- Per AGENTS.md §6: findings are reported, NOT fixed inline.

## Adversarial review

**Scope**: `kan-10-blob-storage-port` (uncommitted KAN-10 work on `main`: 5 new main classes + `AwsS3Properties` hardening + `spotbugs-exclude.xml` + tests + `README.md` / `docs/backend-standards.md`).

**Sources**:

- `openspec/changes/kan-10-blob-storage-port/proposal.md`
- `openspec/changes/kan-10-blob-storage-port/design.md`
- `openspec/changes/kan-10-blob-storage-port/tasks.md`
- `openspec/changes/kan-10-blob-storage-port/specs/blob-storage/spec.md`
- `openspec/changes/kan-10-blob-storage-port/specs/aws-s3-integration/spec.md`
- `openspec/changes/kan-10-blob-storage-port/verify-report.md`
- Diff surface: `git diff` (modified: `README.md`, `docs/backend-standards.md`, `spotbugs-exclude.xml`, `AwsS3Properties.java`, `AwsS3PropertiesTests.java`) + untracked (`domain/models/BlobType.java`, `domain/models/BlobUploadTarget.java`, `domain/repositories/BlobStorage.java`, `domain/repositories/BlobStorageException.java`, `infrastructure/adapters/storage/S3BlobStorageAdapter.java`, unit + integration tests)
- Implementation files read in full: `BlobType.java`, `BlobUploadTarget.java`, `BlobStorage.java`, `BlobStorageException.java`, `S3BlobStorageAdapter.java`, `AwsS3Properties.java` (diff), `spotbugs-exclude.xml`

### Spec and task alignment

- All `blob-storage` scenarios map to implementation + passing tests per verify-report §3 (six-prefix mapping, canonical-key shape, issuance, uniqueness, credential-free PUT, null/type guards, empty/null/non-canonical `remove` guards, 1000-chunking, `errors()` → `failedKeys`, no vendor leakage, URL confidentiality, scope exclusions). No spec-vs-code mismatch found.
- `aws-s3-integration` reconciliation wording (beans consumed unchanged) matches code: `S3Client`/`S3Presigner` construction untouched; adapter only injects the KAN-14 beans.
- Tasks 32/33 done; 10.2 optional TTL-expiry skipped per explicit task optionality — accepted, low residual risk (TTL wiring still pinned by `signatureDuration` captor test + LocalStack PUT test).
- Pre-declared deviations (`@Valid` cascade, `S3Error` type name) assessed JUSTIFIED; verifier accepted and this review concurs.
- DDD layering holds: AWS imports only in `infrastructure/config/S3Config.java` + `infrastructure/adapters/storage/S3BlobStorageAdapter.java` (grep confirmed); domain has zero AWS imports; port exposes exactly 2 operations (ISP); `HttpMethod` in domain is locked decision D4, not a violation.

### Findings

| Severity | Area | Finding | Evidence | Suggested fix (code / spec / tests) |
|----------|------|---------|----------|--------------------------------------|
| Minor | SpotBugs gate (WARNING-1 carried) | `spotbugs-exclude.xml` suppresses `EI_EXPOSE_REP2` for the whole `infrastructure.adapters.storage.*` package to silence the false positive on `BlobStorageException.getFailedKeys()` (which returns an immutable `Set.copyOf` snapshot). A future genuinely-mutable getter in that package would also be silenced. | `spotbugs-exclude.xml:38-41` (`<Package name="~com\.example\.demo\.infrastructure\.adapters\.storage.*"/>` + `EI_EXPOSE_REP2`); `BlobStorageException.java:47-49` (getter returns immutable `failedKeys`) | Follow-up (spec-update-first per AGENTS.md §6, NOT inline): narrow the `<Match>` to the specific class (`BlobStorageException` / `S3BlobStorageAdapter`) and add a justification comment in the XML. Docs/spec change, no behavior change. |
| Minor | `remove` chunk fail-fast (SUGGESTION-1 carried) | On `errors()` non-empty in chunk *k* of *n*, `remove` throws immediately; chunks *k+1…n* are never attempted and their keys are absent from `failedKeys`. Correct (fail-fast, idempotent-retry-safe) but the caller contract is implicit: a retry must resubmit the full set, not just `failedKeys`. | `S3BlobStorageAdapter.java:116-121` (per-chunk `failedKeys` collect + immediate throw inside the `for` loop) | Follow-up docs/spec: one Javadoc sentence on `BlobStorage.remove` (e.g. "On partial failure remaining chunks are not attempted; retry with the full key set") + matching spec scenario line. Spec-update-first if pursued. |
| Minor | Error-translation boundary is `SdkException`-only | Non-`SdkException` runtime failures inside the `try` blocks escape raw instead of as `BlobStorageException`: `presigned.url()` returning null → NPE at `presigned.url().toURI()`; `response.errors()` returning null → NPE at `response.errors().stream()`; `properties.s3()` returning null → NPE at `properties.s3().bucket()`/`presignTtl()`. SDK contract says these return non-null lists/objects, and Spring binding makes null `s3` unlikely, so probability is low — but the port promise "all store-side failures surface as `BlobStorageException`" is then technically wider than the `catch (SdkException)` enforcement. | `S3BlobStorageAdapter.java:71-87` (`catch (SdkException \| URISyntaxException)` only); `S3BlobStorageAdapter.java:100-125` (`catch (SdkException)` only; `response.errors().stream()` dereferenced without null-guard) | Follow-up (optional hardening, spec-update-first): either widen the `remove`/`createUploadTarget` catch to translate unexpected `RuntimeException` into `BlobStorageException` with cause preserved, or record an explicit decision that only `SdkException` is translated and anything else is a programming error. Tests: unit test with `s3Client` throwing `IllegalStateException` asserting translation policy. |
| Minor | Partial-failure message scales with batch size | `new BlobStorageException("Could not remove blobs: " + failedKeys, ...)` interpolates the full failed-key list (up to 1000 keys ≈ ~50 KB) into the exception message, which then flows to `GlobalExceptionHandler.handleGeneric` logging and the warn log `log.warn("Failed to remove blobs: {}", failedKeys)`. Keys are opaque and safe to log, but a large partial failure produces a very large log line / message. | `S3BlobStorageAdapter.java:118-120` | Follow-up (optional): cap the message to count + first-N keys (full set stays in `getFailedKeys()`), e.g. `"Could not remove blobs: " + failedKeys.size() + " failed"`. Spec/tests update first; low priority. |
| Question | `Assert.isTrue` message construction in `remove` validation loop | `keys.forEach(key -> Assert.isTrue(..., "Key is not a managed blob key: " + key))` builds the message eagerly for every key (including valid ones) and interpolates caller-influenced key material into an `IllegalArgumentException` message. Key material here is safe (opaque, non-URL, already validated-or-rejected), so this is not a leak — but confirm the team is comfortable with invalid keys echoed in the 500-path message. | `S3BlobStorageAdapter.java:96-98` | Question for author/archiver: keep as-is (correlation-friendly) or truncate/omit the key in the message. No action required for archive. |
| Question | `properties.s3()` null-tolerance | The adapter dereferences `this.properties.s3().bucket()` / `.presignTtl()` without a null-guard; `AwsS3Properties.hasEndpointOverride()` is null-tolerant but the adapter is not. If the `aws.s3` section were absent, the failure is a raw NPE instead of a binding/validation failure. `AwsS3PropertiesTests.should_reportNoOverride_when_s3SectionIsAbsent` suggests absent-`s3` is a tolerated configuration. | `S3BlobStorageAdapter.java:70,78`; `AwsS3Properties.java` (`S3 s3` without `@NotNull`) | Question for author: is absent-`s3` a supported runtime shape (then adapter should fail fast with `IllegalStateException`→`BlobStorageException`), or is it test-only and production always binds `s3`? If the former, add `@NotNull` on the `s3` component or an explicit guard. Follow-up only. |

### What the adversarial pass tried to break (and could not)

- **URL / signature leakage**: all three log statements carry type/key/count/`failedKeys` only (`log.info(type, key)`, `log.warn(failedKeys)`, `log.info(count)`); `presigned.url()` flows only into the `BlobUploadTarget` return value; exception messages carry key/`failedKeys`, never URL. Grep confirms no `presigned.url` in any log/exception path. `ListAppender` test pins this. PASS.
- **Key-validation bypass**: `isCanonicalKey` uses full `RANDOM_PART.matcher(suffix).matches()` (no partial-match bug); null → false; blank/traversal/unknown-prefix/uppercase/short/long all fail prefix+suffix match by construction. Mixed valid+invalid `remove` set proves validate-all-before-delete-any via `verifyNoInteractions(s3Client)`. No bypass found.
- **Chunking boundary**: `from += 1000`, `subList(from, min(from+1000, size))`; 1000→1 call, 1001→2 calls (1000+1) pinned by tests. Arithmetic correct.
- **Error swallowing**: every `errors().key()` is collected per chunk; non-empty → warn + throw with immutable `Set.copyOf`. No ignored-`errors()` path remains.
- **AWS leakage**: no `software.amazon.awssdk` type in any port signature/return/param/throws; vendor exception retained as `cause` only, per design §5.4. PASS.
- **DDD / SOLID / DRY**: SRP/OCP/ISP/DIP all hold (verifier §6 concurred); prefixes defined once in `BlobType`; bucket/TTL read once from `AwsS3Properties`; adapter stateless (`final` deps, method-local `pending`) and singleton-safe.
- **English-only**: new main files ASCII-only; Javadoc/specs/tests English. PASS.

### Verdict

**PASS WITH GAPS** — implementation matches specs, design, and tasks; adversarial red-team pass found no Blocker or Major; the 4 Minors + 2 Questions above are tracked follow-ups that do not block archiving. Archiving is **advisable** in the current state.

### Recommended next steps (before archive)

- Keep all findings as tracker follow-ups; do NOT fix inline in this phase (AGENTS.md §6 spec-update-first).
- Suggested pre-archive follow-up tickets (no code): (1) narrow SpotBugs `EI_EXPOSE_REP2` match to the specific class + justification comment; (2) document fail-fast chunk-abort retry contract in `BlobStorage.remove` Javadoc + spec line; (3) decide error-translation policy for non-`SdkException` runtime failures; (4) optionally cap partial-failure message size.
- Archiver owns: branch `feat/KAN-10-blob-storage-port` + conventional commits (DoD §6.12 outstanding, expected at this stage), then `opsx-archive`.

## Return summary

- **Review**: adversarial red-team code review of `kan-10-blob-storage-port` (specs + design + full diff + new adapter/domain classes + tests + docs/quality-gate deltas).
- **Findings**: no Blocker, no Major; 4 Minor (SpotBugs exclusion scope, chunk fail-fast documentation, `SdkException`-only translation boundary, large partial-failure message) + 2 Questions (`Assert` message key echo, null-`s3` tolerance). Full detail in Findings table above.
- **Status**: `PASS WITH GAPS` → archive advisable; gaps tracked as follow-ups.
