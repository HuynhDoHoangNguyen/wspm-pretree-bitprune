# WDM-04.1 — Mining run request contract

Date: 2026-11-06 (Asia/Bangkok).

Implementation scope: configured request input, strict JSON parsing and validation.
This contract does not establish a production run endpoint, resource eligibility,
threshold resolution, persistence or mining execution. Owner accepted the plan
and authorized implementation; owner accepted WDM-04.1 in chat on 2026-11-06.

The opening scope is WDM-04.1's historical boundary. Later sections record the
accepted WDM-04 configuration pipeline and WDM-05.3 conditional creation API;
default production creation fails closed; opt-in real admission is documented in
the WDM-05.4 section below, with current DB lifecycle/recovery in WDM-05.5.

## Request

```json
{
  "snapshotId": "550e8400-e29b-41d4-a716-446655440000",
  "algorithm": "WSPM_BITPRUNE",
  "minsupMode": "PERCENT",
  "minsupValue": 10.0
}
```

All four fields are required and non-null; no other fields are accepted.

| Field | JSON type | Constraint |
|---|---|---|
| `snapshotId` | string | Exact lower-case `8-4-4-4-12` hexadecimal UUID format used by existing snapshot reads; no trimming, case conversion or UUID version restriction |
| `algorithm` | string | Exactly `WSPM_PRETREE` or `WSPM_BITPRUNE` |
| `minsupMode` | string | Exactly `PERCENT` or `ABSOLUTE` |
| `minsupValue` | number | Finite Java `double`; `PERCENT`: `[0,100]`; `ABSOLUTE`: `>= 0` |

`PERCENT` value `10` means ten percent, not the fraction `0.10`. An absolute
threshold can be fractional and is in the units of the existing weighted
comparison `support(P) * MeanW >= effectiveThreshold`; it is not necessarily an
integer support count. `minWeight` is not a configurable acceptance input.
MeanW and item weights belong to the unchanged mining implementation.

The legacy `minSup` and `minWeight` fields are rejected, even alongside a valid
new request. Client-supplied resolved thresholds, counts, paths, weights and run
state are also unknown fields. There are no aliases or default algorithm/mode.

## Parsing and validation boundary

`RunRequestReader.read(InputStream)` uses its own strict Jackson reader. It
rejects duplicate keys, trailing JSON values, malformed JSON and non-standard
numeric tokens such as `NaN`/`Infinity`. Unrelated global mapper options cannot
relax this boundary. Root values must be JSON objects; text fields must be JSON
strings, and `minsupValue` must be a JSON number. No string/boolean coercion is
performed. The projected DTO is checked using Jakarta Bean Validation.

`RunCreateRequest` also carries constraints for direct Java callers using a
`Validator`. Record construction alone does not run Bean Validation. Future
service callers must validate their inputs; they must not bypass validation by
constructing the record directly. The custom class constraint reports invalid
support values against the `minsupValue` field.

The number is converted once to the existing Java `double` representation.
Normal binary floating-point conversion applies: for example `1e-999` becomes
`0.0`; `-0.0` is allowed as zero. Values overflowing to infinity are rejected.
There is no scaling, clamping, integer rounding, epsilon or alternative numeric
comparator. The parsed configured value is not a lossless copy of the JSON number
lexeme and is not an effective threshold.

## Zero and later-task obligations

Zero is representable configuration input, as in the existing core
`MinimumSupport`. Successful request validation does **not** approve mining
execution at zero or prove that a positive input resolves to a positive effective
threshold. Positive percentage inputs can also underflow during later resolution.
R6 remains OPEN.

| Task | Remaining responsibility |
|---|---|
| WDM-04.2 | Reuse `MinimumSupportResolver`, preserving configured/resolved values and existing unrounded conversion |
| WDM-04.3 | Dataset preview and threshold information |
| WDM-04.4 | Reject configurations outside the accepted operational scope, including zero-effective-threshold cases, before execution |
| WDM-04.5 | Admin form |
| WDM-05.3 and execution tasks | Authenticate/authorize before reading, enforce transport policy, verify snapshot existence/state/artifacts, persist and execute runs |

The accepted WDM API convention distinguishes invalid requests (400) from
well-formed unsupported mining configurations (planned 422). This task implements
the 400 request boundary only. It does not add the future unsupported-configuration
error code or a runnable route.

## Error contract

| Condition | HTTP | Code | Detail |
|---|---|---|---|
| Broken JSON, duplicate keys, trailing JSON, non-standard numeric tokens | 400 | `MALFORMED_REQUEST` | No parser exception or raw input |
| Missing/null/wrong-type fields, invalid root shape, unknown fields | 400 | `VALIDATION_FAILED` | Safe field detail; unknown fields use `body` |
| Invalid UUID, enum or numeric range/finite constraint | 400 | `VALIDATION_FAILED` | Safe field detail |

Reader exceptions use the existing `ApplicationException`/`ErrorCode` types.
The existing global handler and request-ID filter produce the HTTP envelope:

```json
{
  "status": 400,
  "code": "VALIDATION_FAILED",
  "message": "Request validation failed.",
  "path": "/api/test-run-contract",
  "requestId": "<matches X-Request-Id>",
  "details": [
    {"field": "minsupValue", "code": "INVALID_VALUE", "message": "Supply a valid value."}
  ]
}
```

The example path is test-only. No production endpoint is provided by WDM-04.1.
Error responses use `Cache-Control: no-store`. DTO constraint messages, submitted
values and parser exceptions are not exposed. DTO violations are deduplicated
and ordered by field; shape checks reject the first encountered invalid shape.

## Research fidelity

- **Accepted-contract confirmed:** two existing core modes, finite non-negative
  configured values, inclusive percent upper bound, fractional absolute values;
  unchanged weighted predicate and open R6. See `MinimumSupport`,
  `MinimumSupportResolver`, `ResolvedMinimumSupport` and the
  [BitPrune semantic contract](../research/wspm-bitprune-c01-1-semantic-contract.md).
- **Implementation inference:** four-field wire naming, closed JSON projection,
  UUID format reuse and safe error details. These are application choices from
  the approved WDM-04.1 plan, not claims made by the mining papers.
- **Existing research inference retained:** percent conversion in the resolver
  is the accepted B7 engineering/research inference; this task neither implements
  another conversion nor upgrades it to an explicit paper formula.
- **Unresolved:** R6 and exact-real comparator equivalence remain unchanged.

## Verification and acceptance

Focused verification: **109 tests PASS**, zero failures/errors/skips.

```powershell
mvn -pl backend -am test '-Dtest=RunCreateRequestValidationTest,RunRequestReaderTest,RunRequestContractWebTest' '-Dsurefire.failIfNoSpecifiedTests=false'
```

This runs 48 DTO validations, 57 reader cases and 4 standalone MVC boundary
tests. The MVC tests use the real reader, validator, global error handler and
request-ID filter with a test-only controller. They are not Auth/DB integration
tests and do not verify a production run API.

Selected reactor regression: **mining-core 1182/1182 PASS; backend 736/736 PASS**,
zero failures/errors/skips. Native Maven exit code 0, BUILD SUCCESS.

```powershell
mvn -pl backend -am test '-Dtest=*,!BackendApplicationTests' '-Dsurefire.failIfNoSpecifiedTests=false'
```

Retained evidence: `.agent-workspace/phase-wdm/WDM-04.1-regression.log`.
The 109 new focused cases are included in the backend regression count, not
additional tests on top of 736. Regression includes real Auth/MySQL isolated
integration tests and the existing WDM-03.8 round-trip gate; these do not establish
a production run-creation endpoint. `git diff --check` passed and contract links
were checked against existing files.

The default-environment `BackendApplicationTests` is excluded from selected
regression because it targets the configured development database; it remains
NOT VERIFIED. No development database access is needed for this task.

| AC | Status | Evidence |
|---|---|---|
| AC-01 | PASS | Reader test verifies exact four-component record, valid JSON parsing and legacy-field rejection |
| AC-02 | PASS | DTO parameterized cases and reader required/null/type cases |
| AC-03 | PASS | DTO numeric tests: both modes, zero, negative zero, fractional values, inclusive 100 and next representable value, non-finite values; reader overflow rejection |
| AC-04 | PASS | Reader malformed/shape/type/unknown-field/duplicate/trailing JSON cases |
| AC-05 | PASS | Reader compares raw `double` bits with standard configured-number parsing for both modes and algorithms |
| AC-06 | PASS | Four standalone MVC tests with actual reader/validator/handler/filter; safe details, matching request ID and no-store |
| AC-07 | PASS | Source/dependency review: no resolver/miner/repository calls or production route; zero eligibility deferred above |
| AC-08 | PASS | Focused 109 PASS; selected regression 1182 core + 736 backend PASS; final diff/file audit including ignored files |

## File scope and acceptance state

Seven new source/test/document files and four modified files:
the run DTO, its two validation types, WDM reader, three focused test classes,
this contract, the documentation map and ignored WDM checklist. `.gitignore`
also adds the exact exception needed to version this contract; this necessary
scope expansion was disclosed during implementation. One ignored
regression log is retained as task evidence. No deletions, source moves, schema,
dependency, entity, response DTO, frontend or mining-core changes are authorized
by this task. Pre-existing staged `frontend/.env` is preserved.

WDM-04.1 owner acceptance is recorded on 2026-11-06; its checklist is checked.
The acceptance update changes only this contract and the ignored checklist,
retains all recorded evidence and does not rerun tests. Owner requested
WDM-04.2 planning only; no WDM-04.2 implementation or Git stage/commit/push.
Implementation status: **IMPLEMENTED / TECHNICAL PASS / OWNER ACCEPTED**.
No disposable task files or pending cleanup; the regression log is retained
verification evidence. Production run API, browser configuration E2E and default
application-context test remain NOT VERIFIED by this task.

## WDM-04.2 — Threshold resolver integration (2026-11-07)

Owner authorized implementation after accepting WDM-04.1 and reviewing the
WDM-04.2 plan. This section supersedes the earlier planning-only checkpoint for
WDM-04.2; WDM-04.1 acceptance and evidence above remain unchanged.

### Internal boundary

`RunThresholdResolutionService.resolve(RunCreateRequest request, long
numberOfSequences)` revalidates the complete DTO using the existing Jakarta
constraints, maps the exact mode to core `MinimumSupportMode`, constructs core
`MinimumSupport` with the unchanged configured `double`, and delegates directly
to the existing `MinimumSupportResolver`. It returns core
`ResolvedMinimumSupport` without copying or recomputing any resolved value.
Both algorithm selections use the same resolution path.

The caller supplies an internal sequence count from the dataset boundary and
must bind it to the request's snapshot. The service does not establish that
binding or look up data itself. Counts must be in `[0, Integer.MAX_VALUE]`, the
range supported by core. The bound check precedes `Math.toIntExact`; no narrowing
wraparound or truncation is allowed. Clients still cannot supply a count in the
four-field request. The returned count is a core `int`.

| Input/state | Behavior |
|---|---|
| Null request | `ApplicationException`, `VALIDATION_FAILED`, safe `request` detail |
| Invalid DTO from direct Java caller | `ApplicationException`, `VALIDATION_FAILED`, safe sorted/deduplicated field details; no raw constraint message |
| Negative or above-int internal count | `IllegalArgumentException`, fixed safe message |
| PERCENT with count zero | Core's `IllegalArgumentException` is preserved; no fallback threshold |
| ABSOLUTE with count zero | Core resolves the threshold; no mining eligibility claim |
| Zero, negative zero, positive-percent underflow | Core result is preserved, including signed-zero bits; WDM-04.4 must gate execution |

No HTTP route is added. The internal count/empty-database exceptions are not a
new public HTTP error contract: WDM-04.3/04.4 callers must translate dataset and
supported-configuration outcomes before exposing a production API. No generic
exception catch is added; expected validation paths and resolution diagnostics
use DEBUG logs with safe identifiers, never full request/dataset payloads.

There is no backend percent formula, relative-fraction calculation, integer
rounding, MeanW calculation, epsilon, alternate comparator, snapshot lookup,
artifact access, run persistence or miner invocation. `MinimumSupportResolver`
and the mining-core source/tests are unchanged.

### Verification and AC

Focused run: **8 core resolver tests and 291 backend tests PASS**, zero
failures/errors/skips, native Maven exit 0. Backend counts include **182 new
WDM-04.2 cases** (97 service, 85 reader-to-service contract) and 109 unchanged
WDM-04.1 cases. These are in-process tests using the real validator/reader/core
resolver, not database integration or browser E2E tests.

```powershell
mvn -pl backend -am test '-Dtest=RunThresholdResolutionServiceTest,RunThresholdResolutionContractTest,MinimumSupportResolverTest,RunCreateRequestValidationTest,RunRequestReaderTest,RunRequestContractWebTest' '-Dsurefire.failIfNoSpecifiedTests=false'
```

Selected regression: **mining-core 1182/1182 and backend 918/918 PASS**, zero
failures/errors/skips, native Maven exit 0, BUILD SUCCESS. Retained evidence:
`.agent-workspace/phase-wdm/WDM-04.2-regression.log`. The 182 new cases are
included in the backend total, not additional to it. The selected command is the
same reactor command documented for WDM-04.1 above. Default-environment
`BackendApplicationTests` remains excluded/NOT VERIFIED, avoiding the configured
development database. Production preview/run API, execution gate and browser E2E
are outside this task and remain NOT VERIFIED.

| AC | Check | Current evidence |
|---|---|---|
| 04.2-AC01 | Delegate both modes to existing resolver; no copied formula | Source review and actual-core service tests PASS |
| 04.2-AC02 | Preserve mode, configured value, count, relative fraction and threshold | Raw-double-bit comparison of every numeric result field against core, both algorithms PASS |
| 04.2-AC03 | Percent/absolute/fractional/unrounded behavior | Table 1's 14%/7 threshold, inclusive 100%, unscaled absolute and multiple sizes PASS |
| 04.2-AC04 | Preserve empty/zero/signed-zero/underflow semantics | Empty-percent rejection matches core; empty-absolute/zero and subnormal cases PASS |
| 04.2-AC05 | Revalidate direct callers; safe count conversion | Invalid/null DTO, sorted safe details, negative/count-overflow and int-max cases PASS |
| 04.2-AC06 | Reader -> DTO -> service -> core | 85 contract cases PASS; client counts and legacy inputs rejected |
| 04.2-AC07 | No DB/file/miner/MeanW/execution gate | Source/dependency scope review PASS; later callers own snapshot-count binding |
| 04.2-AC08 | Focused/regression and scope audit | PASS: focused 8 core/291 backend; selected regression 1182 core/918 backend; diff and file inventory verified, including ignored evidence |

### Fidelity, scope and acceptance

- **Accepted-contract confirmed:** core mode/value/count/error behavior and the
  unchanged Java-double weighted predicate. No pruning or pattern generation is
  changed, and no new pattern-output or performance claim is made.
- **Implementation inference:** backend adapter signature, checked long-to-int
  boundary, direct-caller revalidation and safe diagnostics from the approved plan.
- **Existing research inference retained:** B7 percent conversion is performed
  only by core; it is not reclassified as explicitly stated in the paper.
- **Unresolved:** R6 and exact-real equivalence remain OPEN/unresolved.

Task file inventory: three new files (service and two test classes), two modified
files (this contract and the ignored WDM checklist), one retained ignored
regression evidence log. Earlier WDM-04.1 changes, staged `frontend/.env` and
prior evidence are preserved. No disposable task files or proposed cleanup,
deletions/moves, dependency/schema/frontend/core changes or Git stage/commit/push.
Implementation status: **IMPLEMENTED / TECHNICAL PASS / OWNER ACCEPTED**.
All 04.2-AC01--08 PASS; `git diff --check` PASS. Owner accepted WDM-04.2 in
chat on 2026-11-07; checkbox is checked. This acceptance update changes only
this contract and the ignored checklist, retains test evidence and does not
rerun tests. Owner requested WDM-04.3 planning only; its implementation is
NOT STARTED. Proposed preview reads the existing verified conversion bundle,
binds its statistics/count/hashes to resolver input and exposes an informational
preview without run creation or an execution-eligibility decision. Detailed
WDM-04.3 plan supplied in chat; no Git stage/commit/push.

## WDM-04.3 — Dataset preview (2026-11-07)

Owner authorized the reviewed preview plan. This section supersedes WDM-04.3's
planning-only checkpoint above. Prior WDM-04.1/04.2 acceptance and evidence remain.

### Public API

`POST /api/v1/admin/mining-runs/preview`, consuming/producing `application/json`,
accepts exactly the existing four-field request. Shared ADMIN filters execute
before MVC transport checks, strict `RunRequestReader` parsing and resource access.
No query parameters are accepted. The endpoint returns 200 with the existing
`ApiResponse<T>` success envelope, a matching `X-Request-Id`, `Cache-Control:
no-store` and `X-Content-Type-Options: nosniff`.

| Response field | Meaning |
|---|---|
| `snapshotId`, `algorithm` | Verified publication identity and selected algorithm |
| `artifactVersion` | Existing verified conversion artifact contract version |
| `sequenceSha256`, `mappingSha256` | Hashes from the same verified publication |
| `statistics` | Nine existing conversion statistics: sequence count, single/multi-itemset sequence counts, itemset count, distinct-item count, occurrences, minimum/maximum/average sequence length |
| `minimumSupport.mode` | PERCENT or ABSOLUTE |
| `minimumSupport.configuredValue` | Configured Java-double value, without scaling or integer rounding |
| `minimumSupport.relativeFraction` | Resolver result; explicit JSON null for ABSOLUTE |
| `minimumSupport.effectiveThreshold` | Resolver result, without a new numeric comparator |
| `executionEligibility` | Always `NOT_EVALUATED` |

Count/minimum/maximum statistics are decimal strings; average length and support
values are JSON numbers. Public DTOs own nested response types. Projection is a
closed whitelist: no storage references, attempt ID, validation hash/report, raw
rows or artifact contents. The mapper never performs I/O. MeanW, weighted support,
candidate/pattern counts and future run IDs are not computed or returned.

### Verified input and consistency

`MiningPreviewService` revalidates direct Java callers before touching data,
then calls `ConversionReadService.verified(snapshotId)` once. From that single
bundle it takes the immutable publication, statistics/count and artifact hashes.
It checks the publication identity matches the request and count is positive
and within core's int range before invoking `RunThresholdResolutionService`.
It never requests metadata-only status as proof of integrity or reopens a path
after verification. The shared verifier retains its existing raw/file/hash,
parser/statistics, validation binding, fresh DB read, duration/capacity checks.

Preview describes the verified publication at the time of the read; it does not
reserve a future run. Later execution must perform its own required state and
artifact checks. No snapshot/count data is accepted from the client beyond the
requested snapshot identity.

| Failure | HTTP/code behavior |
|---|---|
| Missing identity / non-ADMIN | 401/403 from shared Auth/WDM filters, before malformed-body handling |
| Malformed JSON / invalid fields, unknown query/legacy/count input | 400 using existing MALFORMED_REQUEST / VALIDATION_FAILED |
| Unsupported media / method | 415 / 405 from existing MVC/error boundary |
| Missing snapshot | Existing 404 RESOURCE_NOT_FOUND |
| Unpublished conversion, inconsistent metadata/binding or verified count | Existing 409 INVALID_RESOURCE_STATE |
| File read/hash/shape failure or verifier capacity unavailable | Preserve verifier classification, including 503 SERVICE_UNAVAILABLE |

Current accepted conversion artifacts are non-empty. Zero or above-int dataset
counts in a supposedly verified publication are metadata inconsistencies, not
an opportunity to invent a replacement threshold. By contrast, a valid dataset
with configured zero or positive-percent underflow produces an informational
200 preview with the actual resolved threshold and `NOT_EVALUATED`. No 422
execution gate or execution authorization is added; WDM-04.4 still owns that work.

Preview creates no run and writes no snapshot/mapping/artifact. Normal shared
Auth session behavior is retained. Successful reads and validation diagnostics
use safe DEBUG logs; verifier failures use their existing safe logging boundary.
No migration, mining algorithm, resolver, weight formula or comparator changes.

### Verification and acceptance criteria

Focused command (native Maven exit 0, BUILD SUCCESS):

```powershell
mvn -pl backend -am test '-Dtest=MiningPreviewServiceTest,MiningPreviewResponseContractTest,MiningPreviewControllerTest,MiningPreviewIntegrationTest' '-Dsurefire.failIfNoSpecifiedTests=false'
```

**21/21 PASS**, zero failures/errors/skips: 14 service cases (mocked verifier,
real threshold service/core), one mapper/JSON contract test, five standalone
MVC tests (test authorizer, actual strict reader) and one real shared Auth/HTTP/
MySQL/files/verifier/resolver integration scenario. The integration case checks
32 successful previews (including repeat calls), both algorithms and modes,
independent fixture statistics, hashes, configured/resolved values, zero and
underflow. It checks anonymous/customer-before-malformed-body rejection,
missing/pending snapshots, tampered raw/SPMF/mapping files, altered count and
RUNNING/FAILED conversion metadata. Test-owned mutations are restored in finally
blocks. Snapshot rows, global item mapping and file-hash inventory are unchanged
after ordinary previews; MINING_RUN remains empty. The synthetic fixture and
independent expected statistics are reused from WDM-03.8.

Selected reactor regression: **mining-core 1182/1182, backend 939/939 PASS**,
zero failures/errors/skips, native Maven exit 0, BUILD SUCCESS. The 21 new cases
are included in the backend total, not additional to it. Final task inventory
and `git diff --check` PASS; mining-core has no source changes.
Retained evidence: `.agent-workspace/phase-wdm/WDM-04.3-regression.log`.
Selected command is the same `-Dtest=*,!BackendApplicationTests` reactor command
recorded above. Default development-DB `BackendApplicationTests` is excluded and
remains NOT VERIFIED. No real browser/configuration UI or production performance
claim is established by these tests.

| AC | Current evidence |
|---|---|
| 04.3-AC01 | PASS: MVC filter ordering and real Auth anonymous/customer denial before malformed input |
| 04.3-AC02 | PASS: actual four-field reader; query/legacy/client-count/duplicate/trailing JSON rejected before service |
| 04.3-AC03 | PASS: one verified bundle, identity check and spy count assertion; real fixture hashes/statistics bound to selected snapshot |
| 04.3-AC04 | PASS: two algorithms/two modes against actual core; numeric bit comparisons in service tests and HTTP result comparison |
| 04.3-AC05 | PASS: exact response field sets, nine statistics and precise decimal strings beyond JS safe-integer range; no private references |
| 04.3-AC06 | PASS: real missing/pending snapshot, raw/sequence/mapping tamper and count/state inconsistency rejections |
| 04.3-AC07 | PASS: configured zero/signed-zero/underflow cases remain informational NOT_EVALUATED |
| 04.3-AC08 | PASS: repeated response consistency, complete snapshot row/global mapping/file hashes preserved; no mining run |
| 04.3-AC09 | PASS: actual request-ID/envelope/no-store checks plus MVC method/media/query errors |
| 04.3-AC10 | PASS: focused 21/21; selected regression 1182 core/939 backend; diff and 11-file inventory (including ignored checklist/evidence) verified |

### Research fidelity and file scope

- **Accepted-contract confirmed:** existing verified conversion statistics and
  resolver/Java-double behavior; unchanged weighted acceptance and open R6.
- **Implementation inference:** POST preview route, public projection,
  `NOT_EVALUATED` marker and verified-publication count binding, as approved in plan.
- **Existing research inference retained:** percent conversion remains B7, delegated
  to core; this task does not claim it is an explicit formula from the paper.
- **Unresolved:** R6 and exact-real comparator equivalence remain unchanged.

Eight new files: MiningPreviewController, MiningPreviewService,
MiningPreviewResponse, MiningPreviewResponseMapper and their four planned test
classes. Two modified files: this contract and ignored WDM checklist. One ignored
regression log is retained verification evidence. No disposable files or pending
cleanup, deletions/moves, runtime schema/dependency/frontend/mining-core changes,
Git stage/commit/push or WDM-04.4 implementation. All earlier dirty work,
staged frontend/.env and research/task evidence are preserved.

All 04.3-AC01--10 PASS.
Status: **IMPLEMENTED / TECHNICAL PASS / OWNER ACCEPTED**.
Owner accepted WDM-04.3 in chat on 2026-11-07; its checkbox is checked.
This acceptance update changes only this contract and ignored checklist,
preserves all verification evidence and does not rerun tests. Owner requested
WDM-04.4 planning only; implementation NOT STARTED. The proposed plan preserves
informational preview, adds a reusable gate over actual core resolution and a
separate ADMIN configuration-validation endpoint, with 422 rejection for zero
effective thresholds including underflow. Detailed file/AC plan supplied in chat.

## WDM-04.4 — Configuration validity gate (2026-11-07)

Owner approved the plan and authorized implementation. This section supersedes
the planning-only WDM-04.4 checkpoint above. Prior accepted request, resolver and
informational preview contracts remain unchanged.

### Operational policy and internal gate

`RunConfigurationValidityGate.requireSupported(ResolvedMinimumSupport)` checks
the actual Java-double effective threshold, not just the configured value. A
finite positive threshold returns the identical core object. Zero and negative
zero, including a positive configured percentage that resolves to zero through
underflow, are rejected with `UNSUPPORTED_MINING_CONFIGURATION` / HTTP 422.
There is no epsilon, integer rounding, minimum positive floor or arbitrary cap
relative to sequence count. Fractional ABSOLUTE, `Double.MIN_VALUE` in ABSOLUTE
mode and finite `Double.MAX_VALUE` thresholds are not rejected just for size.

The gate requires a non-empty verified input. Null resolution, non-positive
sequence count, non-finite/negative effective thresholds violate the internal
contract and fail as internal exceptions, not user-scope 422 responses. HTTP
orchestration first handles invalid snapshot identity/statistics/count as 409;
it does not forward invalid metadata to the gate or broadly catch exceptions.

The uniform Web policy for both algorithms follows the accepted positive-
effective-threshold operational scope. It does not redefine baseline zero
enumeration, alter core acceptance or close R6. Source context: the two C16
acceptance/review reports, the BitPrune semantic contract, current core resolver
and BitPrune's operational zero guard. No mining-core source/test is changed.

### Public validation API

`POST /api/v1/admin/mining-runs/configuration-validation` consumes/produces JSON.
It reuses the existing strict four-field reader and shared ADMIN filters. No
query parameter or client-supplied count/threshold/proof is accepted. Order:

1. Shared authentication and ADMIN authorization.
2. MVC transport checks, query rejection and strict request validation.
3. Fresh `ConversionReadService.verified(snapshotId)` call.
4. Bind requested identity and positive/int-range count to that single bundle.
5. Existing threshold resolution service.
6. Operational validity gate.
7. Closed public response mapping.

Direct Java callers are also Bean-validated before resource access. Resource and
integrity errors retain existing classifications even when configured support is
zero: missing snapshot 404, pending/inconsistent publication 409, and file-read/
hash/shape/capacity failures retain verifier errors including 503. Bad JSON or
field constraints remain 400; unsupported method/media remain 405/415. Internal
exceptions are sanitized by the existing global handler as 500, not reclassified
as 422. No handler or informational preview implementation is modified.

Successful response: 200 `ApiResponse<RunConfigurationValidationResponse>`, with
`snapshotId`, `algorithm`, `artifactVersion`, `sequenceSha256`, `mappingSha256`,
decimal-string `sequenceCount`, nested `minimumSupport` (mode, configuredValue,
nullable relativeFraction, effectiveThreshold) and `configurationStatus` equal
to `SUPPORTED`. Public response owns its nested type and exposes no path,
attempt/validation reference, report, run ID or execution authorization field.

`SUPPORTED` means the verified configuration passes this policy at this read;
it does not reserve or create a run, attest future files, guarantee successful
mining or promise runtime resources. WDM-05.3/execution integration must reverify
state and call the gate again; client-supplied prior validation is not authority.

Zero-scope rejection uses the existing error envelope, matching request ID and
no-store, with status/code 422 / `UNSUPPORTED_MINING_CONFIGURATION`, message
`Mining configuration is outside the supported scope.`, and one safe detail:

```json
{
  "field": "minsupValue",
  "code": "ZERO_EFFECTIVE_THRESHOLD",
  "message": "Effective minimum support must be positive while R6 remains open."
}
```

Successful responses also use nosniff. No raw input or internal exception text is
exposed. Logs are DEBUG diagnostics with validated identifiers; there is no new
persistence, conversion retry, run creation or mining execution.

The existing preview API continues to return 200 with `NOT_EVALUATED` for zero
and underflow. Validation of the same verified configuration returns 422. This
intentional distinction is covered by real HTTP tests.

### Verification and evidence

Focused components: 10 gate tests, 12 service cases (mocked verifier, actual
resolver/gate), 8 controller/projection tests, and 1 real shared Auth/HTTP/MySQL/
artifact integration scenario. The unchanged 21 WDM-04.3 cases were also run.
Gate tests compare object identity and raw bits, accept tiny/large finite values,
and reject signed zero, underflow and malformed internal resolutions. Service
tests assert verifier -> resolver -> gate ordering and failure precedence.

The integration scenario exercises 28 algorithm/mode/value combinations against
both preview and validation (14 supported, 14 zero rejections), repeated successful
validation and ABSOLUTE Double.MAX_VALUE. It covers real Auth-before-malformed-
body rejection, missing/pending metadata and tampered raw/SPMF/mapping artifacts
even with configured zero, plus inconsistent counts and RUNNING/FAILED metadata.
Complete snapshot row/global mapping/file hashes are preserved and MINING_RUN
remains empty. Test-owned tampering is restored in finally blocks.

Retained checkpoints:
- `WDM-04.4-focused.log`: initial compile-only failure from a new test assertion
  retaining the old `preview` method name; fixed to `validate` (not test evidence).
- `WDM-04.4-focused-final.log`: 52 cases run; 51 PASS and one Mockito re-stubbing
  error in a new controller test, fixed using doThrow (not a green build).
- `WDM-04.4-controller-final.log`: corrected controller/projection suite, 8/8 PASS,
  native Maven exit 0.
- `WDM-04.4-regression.log`: final selected reactor regression **mining-core
  1182/1182, backend 970/970 PASS**, zero failures/errors/skips, native Maven
  exit 0, BUILD SUCCESS. All 31 new cases and 21 unchanged preview cases pass
  in this single final run; they are included in the backend total.

These logs live under ignored `.agent-workspace/phase-wdm/`. The checkpoint
failures are test authoring/setup issues, not baseline/BitPrune/oracle output
disagreements. No benchmark was performed. The selected regression command is
the existing `-Dtest=*,!BackendApplicationTests` reactor command above. The
default development-DB context test remains excluded/NOT VERIFIED; no development
database access, browser UI/configuration E2E or production benchmark is claimed.

| AC | Current evidence |
|---|---|
| 04.4-AC01 | PASS: actual resolved signed-zero/underflow gate tests and real 422 responses |
| 04.4-AC02 | PASS: identical positive core object, raw-bit check, smallest/largest positive and fractional ABSOLUTE; no epsilon/cap |
| 04.4-AC03 | PASS: both algorithms and modes use same resolver/gate policy |
| 04.4-AC04 | PASS: mocked ordering assertions plus real Auth and resource-before-zero-gate outcomes |
| 04.4-AC05 | PASS: one verified bundle supplies identity/count/hashes; real converted fixture/core comparison |
| 04.4-AC06 | PASS: safe 422 field/reason/message, matching ID/no-store; preserved resource/media/method errors and sanitized internal 500 |
| 04.4-AC07 | PASS: closed mapper/JSON schema, decimal-string count, nullable ABSOLUTE fraction and SUPPORTED marker without run authorization |
| 04.4-AC08 | PASS: unchanged preview tests and real preview 200 versus validation 422 for the same configuration |
| 04.4-AC09 | PASS: repeated responses and unchanged business row/mapping/files, no run; later execution must recheck |
| 04.4-AC10 | PASS: final selected regression 1182 core/970 backend including all new/preview cases; diff and 16-file inventory including ignored evidence verified |

### Fidelity, scope and status

- **Accepted-contract confirmed:** positive-effective-threshold scope, core
  Java-double numeric semantics and existing verifier guarantees.
- **Implementation inference:** uniform Web gate, separate validation endpoint,
  public status and safe error reason, as approved by the owner.
- **Existing research inference retained:** B7 conversion stays in the resolver;
  it is not a new explicit-paper formula claim.
- **Unresolved:** zero enumeration R6 and exact-real equivalence remain unchanged.

Nine new files: gate, validation service/controller/response/mapper and four
planned test classes. Three modified files: ErrorCode, this contract and ignored
WDM checklist. Four ignored logs above are retained evidence/checkpoints. No
disposable helpers, pending cleanup, deletion/move, baseline/resolver/preview/
schema/frontend/dependency change or Git stage/commit/push. Earlier dirty work,
staged frontend/.env and prior evidence are preserved. No WDM-04.5 work started.

All 04.4-AC01--10 PASS; final `git diff --check` PASS. Mining-core has no source
changes. Owner accepted WDM-04.4 in chat on 2026-11-07; checkbox is checked.
Status: **IMPLEMENTED / TECHNICAL PASS / OWNER ACCEPTED**.
This acceptance update changes only this contract and the ignored checklist,
preserves all test/checkpoint evidence and does not rerun tests. Owner requested
the WDM-04.5 implementation plan; no frontend implementation is started here.
Proposed form uses existing mining-runs route and verified preview/validation
APIs, with snapshot-ID/detail-link selection because no snapshot-list API exists.
Auth/form-bound request cancellation and stale result suppression are required;
threshold formulas and gate remain on backend. Detailed file/AC plan supplied
in chat; full real configuration/browser E2E remains WDM-04.6.

## WDM-04.5 — Admin mining configuration form (2026-11-07)

Owner accepted the detailed plan and authorized implementation. This section
supersedes the WDM-04.5 planning-only checkpoint above. Backend request,
resolution, preview and validity contracts remain unchanged.

### User flow

Existing `/admin/mining-runs` now renders a Mantine configuration form with
snapshot ID, WSPM_PRETREE/WSPM_BITPRUNE, PERCENT/ABSOLUTE and configured value.
The algorithm starts visibly at WSPM_PRETREE, mode at PERCENT and value empty.
There is no snapshot-list endpoint: selection uses a canonical ID or the new
link on a SUCCEEDED/COMPUTED snapshot detail conversion tab. The link prefills
`?snapshotId=<UUID>` only. Duplicate query IDs yield an empty field; invalid IDs
cannot be submitted. Opening/remounting/prefilling the page triggers no mining
POST. Existing Admin route/menu/guards and the page's navigation heading remain.

Explicit **Xem trước dữ liệu** sends the four-field POST preview request.
Explicit **Kiểm tra cấu hình** independently sends configuration-validation.
Enter/form submission chooses preview only. Empty/whitespace, non-decimal,
non-finite, negative and out-of-range percent inputs show local field errors.
The input remains text while editing; standard JavaScript-double conversion is
used only for request construction. Decimal/scientific notation is supported;
zero and underflow-to-zero remain representable configured input. No integer
rounding, clamping, epsilon, minsup scaling, MeanW calculation or client-side
execution gate is introduced. Neither input nor payload includes minWeight.

Preview displays all nine server statistics and configured/relative/effective
values, with the unevaluated explanation. Successful validation displays the
supported-scope explanation, never a run ID or execution authorization. Counts
remain decimal strings, including values beyond JS safe-integer precision;
small thresholds use String(number), not fixed-decimal display that hides them
as zero. ABSOLUTE's relative fraction displays as not applicable. A known
422/UNSUPPORTED_MINING_CONFIGURATION + minsupValue/ZERO_EFFECTIVE_THRESHOLD
detail is translated to a safe explanation. Other field/resource/Auth/network/
response failures use safe UI messages/request IDs, never raw server text.

### Transport, binding and stale-result protection

The new mining client uses the existing wdmApi/Auth adapter/base URL with
expected-status-200 requests, existing success/error envelopes, matching request
IDs and per-request decoder callbacks. Payload construction selects only four
fields. It does not accept client count, state, path or weights. Endpoint
decoders whitelist public fields and check request snapshot/algorithm/mode/value
binding, current artifact version, canonical hashes/counts, finite number fields
and literal NOT_EVALUATED/SUPPORTED statuses. Canonical ID length, hash length
and count whitespace checks also reject trailing line terminators. The decoder
checks wire constraints, not mining formulas or the backend positive-threshold
execution policy. Unrelated conversion/shared client code is not modified.

The dedicated hook binds results to raw form revision, verified Auth identity/
roles/status/forbidden state and access-token change. This binding exists only
in memory, is never logged/persisted/exposed as a public value, and carries no
authority to the server. Input handlers invalidate synchronously before editing.
Old responses must match the current binding and a non-aborted controller before
they can update state. Input/auth/unmount changes abort active work; ref-based
locking prevents double submission before React commits loading state. Failed
operations clear supported results, and retries require an explicit user action.
There is no automatic effect-driven POST, polling, recovery POST or localStorage
configuration/validation cache. Shared Auth request cancellation is retained.

When preview and validation coexist, server snapshot/hash/version/count/support
fields must agree. A mismatch clears both results and requests an explicit fresh
check; no threshold is recomputed to reconcile the responses. Starting a fresh
preview also removes prior SUPPORTED status. No run-start control or execution
request is added. Backend still revalidates everything authoritatively.

### Verification and acceptance criteria

Focused confirmed suite: **67/67 PASS** across the new API (31), form/hook/result
(32) and page (4) tests, native exit 0. One additional API canonical-terminator
case was added during final self-review and is included in the final regression.
The snapshot detail link adds one test to the existing suite; navigation
expectations are updated for the now-functional mining page, still requiring no
business fetch on navigation.

Initial test checkpoints were corrected without weakening production contracts:
the new API fixture originally used `OK` instead of the required exact success
message; jsdom required the same ResizeObserver stub used by existing Mantine
tests; Select options select on mouseDown, so the test was corrected to send the
actual event. Checkpoint logs are preserved. There was no mining-output mismatch.

The first complete frontend run passed **390 tests / 2 existing live opt-in
skips**. Final regression after canonical-boundary tightening: **391 PASS / 2
existing live opt-in skips**, 24 suites PASS / 2 skipped, native exit 0. This
includes all new cases and snapshot/navigation/Auth regression, not real-server
E2E. Final typecheck and scoped lint exit 0 with no errors/warnings; production
build exit 0 with the existing unrelated warnings noted below.

Production output uses the task-owned ignored
`.agent-workspace/phase-wdm/WDM-04.5-build/` via CRA BUILD_PATH, preserving any
pre-existing frontend/build output. This output is retained as the reviewable
build artifact, not deployed. Existing unrelated hook/non-null-assertion and
outdated Browserslist warnings remain; there is no dependency update.

Evidence under `.agent-workspace/phase-wdm/`: WDM-04.5-{focused,focused-final,
form-final,focused-confirmed,typecheck,typecheck-final,lint,lint-final,regression,
build,typecheck-confirmed,lint-confirmed,regression-final,build-final}.log.
Final diff and task-file inventory are verified. No new backend/mining test run
is required for this frontend slice; previous backend acceptance evidence is
retained, not presented as a fresh task run. Actual browser/API/DB configuration
E2E, visual review in a real browser and run execution remain NOT VERIFIED and
are not claimed by module tests; the full configuration gate belongs to WDM-04.6.

| AC | Current evidence |
|---|---|
| 04.5-AC01 | PASS: existing Admin route, untrusted prefill/duplicate IDs, no POST on mount/navigation |
| 04.5-AC02 | PASS: canonical ID, empty/non-decimal/non-finite/range tests, both algorithm/mode selections, fractional/scientific input and exact four-field payload |
| 04.5-AC03 | PASS: real shared transport with mocked fetch verifies POST paths/payload and expected 200 |
| 04.5-AC04 | PASS: malformed shape/ID/mode/value/hash/count/version/status and unknown validation status rejected; request ID retained |
| 04.5-AC05 | PASS: precise count and tiny-number projection/display; nine server statistics; source review finds no mining formula/gate |
| 04.5-AC06 | PASS: preview unevaluated versus SUPPORTED status, safe whitelisted 422 explanation, no run controls |
| 04.5-AC07 | PASS: edit/late response and logout/user/role/token/forbidden binding changes; hash mismatch clears confirmation |
| 04.5-AC08 | PASS: ref lock/double click, aborted old/unmounted work, no automatic replay and explicit retry |
| 04.5-AC09 | PASS: loading/error displays, labelled inputs and associated validation text, status/alert roles and form-submit behavior in component tests |
| 04.5-AC10 | PASS: snapshot detail link/prefill, query change resets form and existing navigation stays free of business requests |
| 04.5-AC11 | PASS: final frontend 391 PASS / 2 existing live skips, typecheck/scoped lint/build exit 0, source/evidence inventory and diff checks PASS |

### Research fidelity, scope and status

- **Accepted-contract confirmed:** backend wire fields, units and operational
  gate status/error behavior from WDM-04.1--04.4; all calculations remain server-side.
- **Implementation inference:** form layout, ID/detail-link selection, safe
  localized labels and Auth/form-bound state machine, per the owner-approved plan.
- **Unresolved:** R6 and exact-real equivalence remain unchanged; B7 remains the
  accepted research inference implemented by core, not a new formula in TypeScript.

Eight new files (mining API/decoder/API tests, hook/form/result and two UI test
files), eight modified files (API index, mining page, snapshot detail/test,
navigation test, frontend README, this contract and ignored checklist). Logs and
the isolated build artifact above are retained; no disposable helper/report is
created. No deletions/moves, backend/schema/core/dependency/shared Auth changes,
Git stage/commit/push or WDM-04.6 implementation. All prior dirty work, staged
frontend/.env and earlier task/research evidence are preserved.

All 04.5-AC01--11 PASS within the frontend/module scope above. Owner accepted
WDM-04.5 in chat on 2026-11-07; checkbox is checked. WDM-04.6 real configuration
E2E remains NOT VERIFIED.
Status: **IMPLEMENTED / TECHNICAL PASS / OWNER ACCEPTED**.
This acceptance update changes only this contract and ignored checklist, retains
all previous evidence and does not rerun tests. Owner requested the WDM-04.6
implementation plan; no E2E implementation/run is started here. Proposed cases
extend the existing real Dataset browser runner with a separate configuration
case module, an explicit task-build path option and test-only Java core-resolution
verification. Earlier Dataset gates, isolated resource ownership and safe
evidence rules remain. Detailed file/AC plan supplied in chat; no WDM-05 work.

## WDM-04.6 — Real configuration E2E (2026-11-07)

Owner approved the plan and authorized implementation. This section supersedes
WDM-04.6's planning-only/NOT VERIFIED checkpoint above for the exercised
configuration flow. Actual run creation/execution remains WDM-05. No production
frontend/backend/core implementation was changed to make this gate pass.

### Runner and ownership

Dataset mode retains all 17 existing cases and invokes eight new groups in
`configuration-cases.mjs`. It serves the actual production build over HTTPS to
Edge with real shared Auth, a fresh labeled MySQL 8.4 container and private
temporary storage. Snapshots originate from real UI upload/validation/conversion;
SQL only seeds synthetic accounts/catalog or performs controlled integrity tests.
There is no Auth harness endpoint, production hook or fabricated success response.

WDM_E2E_BUILD_DIR selects a canonical workspace-contained build directory;
frontend/build remains the default. This task serves the new ignored
`.agent-workspace/phase-wdm/WDM-04.6-build`, preserving older outputs.
PREPARATION.json records Node/Java/Git HEAD and served index/manifest/backend JAR
hashes. Git HEAD is provenance, not a claim that the pre-existing dirty tree is
identical to HEAD. The UI and backend were freshly prepared from the working
tree. Package with skipTests is preparation only, not backend test evidence.

The runner keeps its ten-minute suite deadline and per-operation limits. Every
SQL call checks container ID/label and absence of host bind mounts. Cleanup
checks the unique OS-temp parent/prefix and closes only owned browser/processes/
container/temp files. Both runs reported successful cleanup. No development DB,
existing service/storage/build or older evidence was cleaned. Dataset mode does
not claim a fresh CRA port-8080 proxy check or full separate Auth harness run.

### Verification bridge and cases

`java/MiningThresholdVerifier.java` is a test-only JDK 21 source launcher. It
parses SPMF actually downloaded through the browser, checks count against the
independent fixture expectation (3), and compares captured mode/configured value/
count/relative fraction/effective threshold with MinimumSupportResolver using
raw double bits. It verifies the core JAR on its classpath is byte-identical to
the nested core JAR inside the running backend package. This is a bridge to the
actual backend core, not a JavaScript formula, independent mining oracle or
exact-real proof.

The final run verified **57 captured tuples**, **28 algorithm/mode/input matrix
entries**, plus ABSOLUTE Double.MAX_VALUE. Both algorithms/modes, fractional
values, 100%, zero/-0.0 and input/resolution underflow are exercised. Preview
stays 200/NOT_EVALUATED for zero; validation rejects with 422. ABSOLUTE
Double.MIN_VALUE stays positive and passes. Two negative controls change a
captured threshold and expected count: both exit 1 after valid tuples exit 0.

Browser assertions cover exact four-field bodies, verified hashes, all nine
fixture statistics and displayed support values/status. Prefill/reload and
local-invalid-input POST counts are zero. Real API negatives cover 400/401/403/
404/405/409/415/422/503, preserving resource/integrity errors for configured zero.
Double-click sends one POST; dropping a real successful response does not replay
it, while explicit retry raises the count to two. Held real responses are
released after edit/snapshot change/navigation/logout, without stale SUPPORTED.
Logout's held configuration POST count remains one. Complete selected snapshot
row, global mapping and file-hash inventory stay unchanged after normal reads;
MINING_RUN stays empty. Test-owned tampering is restored in finally blocks.

Final desktop/mobile screenshots were inspected. Mobile 390x844 has no document
horizontal overflow. This is visual inspection of exercised screens, not broad
cross-browser/accessibility acceptance.

### Executed evidence

- `WDM-04.6-package.log` and `WDM-04.6-build.log`: native exit 0, preparation only;
  build keeps existing unrelated warnings. No dependency update/npm-ci cleanup.
- Node --check for run.mjs/dataset-cases.mjs/configuration-cases.mjs: PASS.
  Java javac --release 21 smoke compilation: PASS; isolated WDM-04.6-javac kept.
- `WDM-04.6-core-focused.log`: **23/23 PASS**, 8 resolver + 15 parser tests, no
  failures/errors/skips, native Maven exit 0.
- First `WDM-04.6-browser.log`, run **WDM-01.8-BAkmdc**: 19 completed cases PASS;
  boundary assertion compared JS -0 with JSON 0. Harness expectation was fixed
  to compare the actual JSON wire contract (which normalizes -0 to 0). No backend
  or policy change and no mining/core-verifier disagreement. Failure retained.
- Final `WDM-04.6-browser-final.log`, run **WDM-01.8-PeDZwu**: **25/25 PASS**, no
  skips, native Node exit 0, successful owned-resource cleanup. Edge
  **156.0.4314.58**, Java **21.0.12.1**, Node **v22.23.4**, isolated MySQL **8.4**,
  real HTTPS/shared Auth.

Per-run evidence: `.agent-workspace/phase-wdm/WDM-04.6/`. RESULT.json contains
safe configuration/hash/count/request/verifier records; PREPARATION.json contains
artifact fingerprints. Final screenshots are configuration-supported-desktop.png
and configuration-supported-mobile.png in WDM-01.8-PeDZwu. No credentials/tokens/
browser storage/traces are saved in safe summaries. Downloaded SPMF, captured TSV
and TLS keys were removed with the verified run-owned temporary directory.

| AC | Evidence/result |
|---|---|
| 04.6-AC01 | PASS: current build/JAR fingerprints, versions, real Edge/HTTPS/Auth/isolated MySQL and native exit 0 |
| 04.6-AC02 | PASS: real upload -> validation -> conversion -> detail selection; no prefill/reload POST |
| 04.6-AC03 | PASS: exact transmitted four fields, both algorithms/modes |
| 04.6-AC04 | PASS: downloaded SPMF/fixture count, nine statistics, same-publication hashes, UI values; exact core JAR, 57 bit-checked tuples, two negative controls |
| 04.6-AC05 | PASS: preview 200/validation 422 zero/underflow; positive tiny/fractional/large ABSOLUTE and unrounded server values |
| 04.6-AC06 | PASS: actual local/API/Auth/resource/media/integrity negatives, UI errors and request-ID/no-store envelopes |
| 04.6-AC07 | PASS: one double-click POST, no replay after dropped response, explicit retry count two |
| 04.6-AC08 | PASS: edit/snapshot change/navigation/unmount/logout suppress held real responses and stale confirmation |
| 04.6-AC09 | PASS: unchanged business row/mapping/files and zero runs after normal actions/restored test mutations |
| 04.6-AC10 | PASS: 17 retained + 8 new groups, final 25/25; safe evidence and owned cleanup; syntax/diff/file audit |

### Research fidelity, scope and status

- **Accepted-contract confirmed:** parser/resolver/validity rules, units/counts
  and Java-double results. No PRISM, candidate/pruning/verification or weighted
  acceptance changes.
- **Implementation inference:** test module, build override, verifier bridge and
  race controls from the accepted plan. B7 remains the accepted core inference;
  it is not promoted to an explicit paper formula.
- **Unresolved:** R6 and exact-real comparator equivalence remain unchanged.

Two new files (configuration module and Java verifier), five modified (runner,
Dataset registration, E2E README, this contract, ignored checklist). Retained:
five top-level logs, task-owned build/javac output, two per-run evidence
directories including the failed checkpoint. No disposable helper/report,
pending cleanup, deletion/move, production/dependency/schema/core change or Git
stage/commit/push. Prior dirty work, staged frontend/.env and evidence preserved.
No WDM-05 work started.

All 04.6-AC01--10 PASS for the exercised Edge configuration gate. Other browser
engines, production deployment/capacity, Flyway upgrade and actual mining-run
creation/execution remain NOT VERIFIED. Status: **IMPLEMENTED / TECHNICAL PASS /
OWNER ACCEPTANCE PENDING**; checkbox awaits owner acceptance.

## WDM-05.3 — Run creation API (2026-11-07)

Owner accepted WDM-05.2, approved the reviewed WDM-05.3 plan and authorized
implementation. This section supersedes the earlier planning checkpoint for
05.3. Current status: IMPLEMENTED / TECHNICAL PASS / OWNER ACCEPTED (2026-11-07).
WDM-05.4 managed execution is not implemented here.

This section preserves WDM-05.3's accepted conditional/no-provider checkpoint.
Subsequent opt-in managed execution and its evidence are in WDM-05.4 below.

### HTTP and production admission boundary

`POST /api/v1/admin/mining-runs` uses shared ADMIN authorization before MVC,
`RunRequestReader`'s strict four-field JSON contract and no query parameters.
The new controller uses the current shared `ApiResponse` success envelope,
not the bare illustrative response in the historical WDM-00 convention.

There is no production `RunExecutionAdmission` provider in this task. After
eligible verified preparation, zero or ambiguous providers return **503 with
no MINING_RUN insertion**. No no-op provider, synchronous miner call, unbounded
thread or pretend execution acceptance is installed. Existing resource/gate
errors retain their precedence: malformed input 400, missing snapshot 404,
inconsistent state/publication 409, unsupported effective zero 422, unavailable
storage/preparation/admission 503; Auth denies with 401/403 before business reads.

Conditional 202 is returned only when the short creation transaction has committed
and an admission reservation has acknowledged activation and closed successfully:

```json
{
  "code": "SUCCESS",
  "message": "Request completed successfully.",
  "data": {
    "runId": "server-generated-uuid",
    "snapshotId": "verified-snapshot-uuid",
    "algorithm": "WSPM_BITPRUNE",
    "status": "QUEUED",
    "createdAt": "2026-11-07T05:00:00.123456Z"
  },
  "requestId": "server-generated-request-uuid"
}
```

Headers include relative `Location: /api/v1/admin/mining-runs/{runId}`,
`Cache-Control: no-store`, `X-Content-Type-Options: nosniff` and matching
`X-Request-Id`. IDs are strings. The five-field data record is a creation snapshot,
not a live status read: execution may advance after activation. Pattern counts,
paths and private publication metadata do not appear. Errors have no Location.
GET status/polling is still WDM-05.7, not implemented by this endpoint.

A lost response does not undo admission. No POST replay or idempotency guarantee
is introduced; deliberate repeated submissions can create separate run IDs.

### Verified input preparation

`RunPreparationService` validates direct callers, then uses
`ConversionReadService.verified` for actual file/hash/report integrity. It runs
`ConversionArtifactCodec.verify` on the bundle's exact SPMF/mapping bytes to obtain
the core database and all nine descriptive statistics, which must equal the
publication. This deliberately reuses the existing bounded parser/codec rather
than changing its accepted read contract; it performs an additional parse.

A fresh metadata read must decode to the same `ConversionArtifactResult`.
`PreparedRun.Binding` additionally captures source type/name and raw reference/hash
for locked comparison. The decoder validates frozen validation report/hash/raw
binding, policy, conversion success and artifact descriptors. No physical path is
exposed by the public DTO or internal carrier's redacted toString.

The actual database size goes to `RunThresholdResolutionService` and
`RunConfigurationValidityGate`; zero/signed-zero and percent underflow remain
unsupported while positive tiny ABSOLUTE remains supported. Original configured,
relative and effective values are not rounded or clamped. Core
`ItemStatisticsCalculator`/`ItemWeightCalculator` compute real MaxW/MinW/MeanW
before QUEUED; no copied formula, mining call or placeholder MeanW.

Preparation uses its own semaphore with the existing `wdm.validation.max-concurrent`
budget and elapsed checkpoints using `wdm.validation.max-duration-millis`.
Codec input/token/row bounds remain the accepted conversion limits. This bounds
active preparation, not executor concurrency, and is not a forced parser timeout
or cancellation promise. No new runtime setting/dependency was added. The carrier
retains the immutable parsed model, resolved config, weights and binding, not
source file byte arrays. Its lifetime ends at failed admission or passes to the
future bounded execution provider. This task adds no cache or result registry.

### Durable creation and timestamp mapping

`RunCreationRepository.create` is a separate proxied REQUIRES_NEW transaction with
a five-second timeout. It pessimistically locks the snapshot row, decodes current
publication metadata, and compares the entire expected publication plus raw/source
binding before INSERT. There is no filesystem, parsing, mining or provider call
inside the transaction. Multiple configurations/algorithms can reference one
snapshot; there is no one-run-per-snapshot constraint or deduplication promise.

`RunEntity.queued` sets the real snapshot ID, requested algorithm, QUEUED,
source_name display label, mode/configured/relative/effective values, MaxW, MinW,
MeanW and created time. It bypasses the old constructor that aliases snapshot ID
to run ID and writes LEGACY_COMPATIBILITY. Existing constructor/getter signatures
remain available for old consumers. No transient compatibility getter is used as
new metadata or filled with fabricated values.

Counts, metrics, started/finished time and error remain null at creation.
`legacy_metadata_json` stays null: V2 reserves it for V1 legacy provenance. New
run provenance is the verified snapshot FK/publication protected by existing
publication guards; no new per-run JSON or schema column is invented. A future
need for separate per-run artifact version history requires its own schema review.

`created_at` now maps as `Instant` (persistent field name `runTime` retained for
existing repository ordering), using the configured Hibernate/JDBC UTC policy.
Clock values are truncated to microseconds before INSERT for DATETIME(6).
`getCreatedAt()` returns Instant; compatibility `getRunTime()` returns the UTC
LocalDateTime view, and the legacy constructor interprets its LocalDateTime
argument as UTC. No historical row is rewritten and no claim is made that old
legacy timestamps with unknown original timezone have become verified.

The integration test exposed a real seven-hour drift with the old LocalDateTime
mapping on the current host. The Instant mapping fixes the new creation round-trip,
verified by independent JDBC UTC reads against the exact response timestamp.
This is an application timestamp correction, not a mining semantics change.

### Admission protocol and uncertain outcomes

`RunCreationService` has no outer transaction. It selects exactly one provider;
@Primary does not hide multiple registrations. The port is:

1. `reserve(runId, PreparedRun)`: provider reserves real bounded capacity without
   running work; it must be nonblocking/bounded.
2. Repository create returns only after its REQUIRES_NEW transaction commits.
3. `reservation.activate()`: provider acknowledges managed ownership after commit.
4. `reservation.close()`: idempotent/nonthrowing release of unactivated capacity,
   never cancellation of activated work. Caller returns accepted response afterwards.

Resource release is try-with-resources. Rollback releases the reservation without
activation; uncertain commit/activation/close failure never returns success,
never deletes the committed row, never retries and never blindly assigns FAILED.
Safe logs retain run/snapshot IDs and sanitized exception type/stack for later
reconciliation, without SQL text/paths/payloads. Known post-commit application
failures also log the run ID for reconciliation. Lifecycle/restart repair belongs
to WDM-05.5; this protocol does not make the DB and executor one atomic transaction.

WDM-05.4 must implement real reservation/activation with commit ordering, bounded
capacity, safe resource ownership and no execution before commit. Until then,
production 202/actual execution remain NOT VERIFIED and unavailable by design.

### Tests and retained checkpoints

Four new test classes separate evidence:

- `RunPreparationServiceTest`: real codec/parser/calculators/resolver/gate with
  mocked storage/publication. Weights match actual engine metadata; configured
  support, tiny-positive/zero/underflow, malformed bytes/statistics/publication
  changes, direct input validation and capacity release on failure are covered.
- `RunCreationServiceTest`: mocked repository/admission protocol, no-provider or
  ambiguous provider, reservation/commit/activation/release ordering, capacity,
  commit/activation/close failure, no retry/delete and safe error messages.
- `RunCreationControllerTest`: standalone MVC with test authorizer, real reader,
  shared error/request-ID handling; conditional 202/envelope/Location/UTC shape,
  input/query/media/method rejection, 401/403 ordering and error statuses.
- `RunCreationIntegrationTest`: real shared Auth/HTTP, V2 MySQL 8.4 schema,
  Hibernate validate, private temporary storage and actual upload → validation →
  conversion → preparation. Default context has no provider and proves 503/no
  insertion. A runtime-registered **test-only** provider checks independent JDBC
  visibility of committed rows before activate, without starting mining.

The integration's ten successful creation matrix entries cover both algorithms,
PERCENT 14/100 and ABSOLUTE 0.25/Double.MIN_VALUE/Double.MAX_VALUE. Readback checks
every configured/resolved numeric field by raw bits where applicable, actual
weights, FK/label/status, microsecond UTC time and null metrics/legacy JSON.
The snapshot remains unchanged and PATTERN stays empty. Additional groups cover
publication binding changed after preparation, real INSERT rollback and unknown
post-commit activation with retained QUEUED row/no 202. A test-owned temporary
CHECK constraint induces rollback; it and the temporary provider are removed in
finally blocks. No production migration/privilege is altered. Tampered test bytes
and source label are restored. SQL fixtures are limited to synthetic Auth/catalog
and controlled failure injection in the owned container.

Ten matrix entries and inner error assertions are not counted as extra JUnit
cases. Conditional 202 with test acknowledgement proves HTTP/persistence/protocol,
not actual executor execution, production capacity or completion.

Retained evidence under `.agent-workspace/phase-wdm/`:

- `WDM-05.3-service-focused.log`: 18/18 service cases PASS, native exit 0.
- `WDM-05.3-focused.log`: 23 cases, one context setup error; PER_CLASS caused
  Spring to query the mapped port before Testcontainers startup. Fixed the test
  lifecycle without a production change.
- `WDM-05.3-focused-final.log`: 26 cases, one timestamp round-trip failure and two
  harness errors (trigger privilege then leaked test provider). Timestamp mapping
  was corrected; rollback injection switched to a test-owned CHECK with protected
  cleanup. No privileges were elevated and no baseline/core output differed.
- `WDM-05.3-focused-corrected.log`: **26/26 PASS**, zero failures/errors/skips,
  native Maven exit 0. Includes 10 preparation, 8 creation-protocol, 4 MVC and 4
  real integration groups; startup uses current production source.

Corrected focused command:

```powershell
mvn -pl backend -am test '-Dtest=RunCreationControllerTest,RunCreationIntegrationTest,RunPreparationServiceTest,RunCreationServiceTest' '-Dsurefire.failIfNoSpecifiedTests=false'
```

Selected reactor regression:

```powershell
mvn -pl backend -am test '-Dtest=*,!BackendApplicationTests' '-Dsurefire.failIfNoSpecifiedTests=false'
```

Evidence: `WDM-05.3-regression.log`. Default environment BackendApplicationTests
is excluded/NOT VERIFIED. Existing selected integration tests use isolated
Testcontainers; Flyway upgrade/migration execution, development DB, browser and
real executor acceptance are not claimed.

Final selected regression: **mining-core 1182/1182 and backend 1042/1042 PASS**,
zero failures/errors/skips, native Maven exit 0 and BUILD SUCCESS. The 26 new
WDM-05.3 cases are included in 1042, not added on top. Existing core differential
and three-oracle tests run unchanged. Existing Mockito/JVM agent and isolated
pool-shutdown warnings remain; no performance claim or mining disagreement.

### Acceptance criteria — 05.3-AC01–10 PASS for approved conditional creation scope

| AC | Evidence |
|---|---|
| 05.3-AC01 | Actual shared Auth 401/403 before business parsing, strict reader, media/query and current envelope checks |
| 05.3-AC02 | Actual upload/validation/conversion/artifact preparation; corrupted artifact negative and immutable publication binding |
| 05.3-AC03 | Real core calculator/resolver/gate; engine MeanW comparison, zero/underflow rejection and tiny-positive preservation |
| 05.3-AC04 | Ten real V2 insert/readbacks across both algorithms/modes, exact doubles, FK/label/null fields and UTC microsecond timestamp |
| 05.3-AC05 | Locked fresh metadata decode and source/raw/publication comparison; after-preparation binding-change negative rolls back |
| 05.3-AC06 | Real transaction visibility at test activation, no active outer transaction, reserve/commit/activate/release order and failure controls |
| 05.3-AC07 | Production-context zero provider 503 with zero inserted runs; ambiguity and capacity rejection in service tests |
| 05.3-AC08 | 202 only after commit/acknowledgement; real INSERT rollback and post-commit unknown activation return 503 with no retry/delete/false terminal state |
| 05.3-AC09 | Focused 26/26 and selected core/backend 1182/1042 all PASS, retained failed checkpoints and native exit evidence |
| 05.3-AC10 | No mining-core/gate/resolver, runtime migration/config/dependency, Auth implementation or frontend changes |

Production 202 with real managed execution, GET polling, restart reconciliation,
browser E2E and Flyway upgrade remain **NOT VERIFIED**. Passing conditional
creation tests is not acceptance of those later-task behaviors.

Final file/whitespace/link audit PASS. Eleven new Java source/test files, seven
modified pre-existing files (RunEntity plus request/engine contracts, docs index,
README, ARCHITECTURE and ignored checklist), five retained evidence logs. README/
architecture/index updates document the new endpoint and fail-closed dependency;
the Instant mapping change is the tested correction described above. Existing
WDM-05.1/05.2 source/evidence and .gitignore work preserved. No deletion/move,
disposable helper, pending cleanup or Git stage/commit/push. Runtime test resources
are owned Testcontainers/temp/provider/constraint fixtures; no development DB or
owner storage was accessed. Owner accepted WDM-05.3 in chat on 2026-11-07;
checkbox checked. This acceptance-only update does not rerun tests or alter
production code. Owner requested WDM-05.4 planning; implementation has not started.

### Research fidelity

- Accepted-contract confirmed: unchanged parser/sequence semantics, core item
  statistics/weights, resolver/gate and exact mining predicates. Core algorithms
  are not called by the creation HTTP pipeline.
- Engineering inference: prepared-input binding, locked creation transaction,
  admission protocol, safe failure handling and UTC application timestamp mapping.
- Unresolved: R6 and exact-real comparator equivalence remain unchanged. Unknown
  legacy timezone and execution recovery are not silently defined as verified.

## WDM-05.4 — Managed execution (2026-11-07)

Owner accepted WDM-05.3, approved the detailed WDM-05.4 plan and authorized
implementation. This supersedes the earlier planning-only checkpoint for 05.4.
Status: IMPLEMENTED / TECHNICAL PASS / OWNER ACCEPTED (2026-11-07).

### Enablement, configuration and scope

`RunExecutionConfiguration` registers exactly one production
`ManagedRunExecutionAdmission` provider only when `wdm.execution.enabled=true`.
Disabled/missing flag registers no provider/limits bean and retains the accepted
503/no-insertion behavior. No real `.env`, owner service or deployment was changed.
The versioned application settings and `.env.example` defaults are:

| Environment variable | Property suffix under wdm.execution | Default |
|---|---|---|
| WDM_EXECUTION_ENABLED | enabled | false |
| WDM_EXECUTION_PARALLELISM | parallelism | 1 |
| WDM_EXECUTION_QUEUE_CAPACITY | queue-capacity | 1 |
| WDM_EXECUTION_SHUTDOWN_GRACE_MILLIS | shutdown-grace-millis | 30000 |

Enabled workers/queue must be positive, grace nonnegative, and their sum must
fit a Java int. Invalid enabled limits fail startup. Defaults are engineering
choices, not benchmark-derived capacity recommendations. The provider owns fixed
platform worker threads named `wspm-mining-*`, an ArrayBlockingQueue and
ThreadPoolExecutor.AbortPolicy. No @Async default executor, unbounded queue,
CallerRunsPolicy, process Main or HTTP-thread mining fallback is used.

HTTP still performs preparation, commit and admission synchronously; it does not
wait for mining. This task changes no request/response shape, Auth or mining
predicate. Internal states do not update MINING_RUN.status: DB rows stay QUEUED
with null start/finish/metrics, including after an internal completed/failed
outcome. WDM-05.5 owns those transitions/recovery, 05.6 application measurements,
05.7 polling, and WDM-06 durable pattern handoff. This is not a complete operational
workflow or production deployment acceptance.

### Reservation, activation and commit visibility

One short registry lock serializes capacity/state changes; no DB/file I/O,
preparation, engine call or completion callback runs under that lock.

- reserve rejects stopping/full/duplicate-ID registry entries with 503 and stores
  immutable PreparedRun ownership without submitting a task or occupying a worker.
- activate moves RESERVED to ACTIVATING, reads the committed run outside the
  registry lock, then submits under the lock with SUBMITTED/admitted ownership
  visible before the worker can start. It acknowledges only after execute accepts.
- Repeated activation of an admitted reservation is a no-op, including after its
  outcome is acknowledged. Concurrent activation attempts cannot double-submit.
- close removes an unadmitted RESERVED/ACTIVATING entry idempotently. If it races
  with verification, activation rechecks identity/state before submitting. An old
  generation's cleanup cannot remove a replacement reservation with the same ID.
  Closing an admitted reservation never cancels it.
- Executor rejection frees the unadmitted slot and propagates 503; no retry or
  inline execution. Shutdown/resource races can still reject after DB commit;
  the WDM-05.3 caller keeps the row and logs reconciliation context, never 202.

`RunExecutionVerificationRepository.requireCommitted` uses a fresh read-only
REQUIRES_NEW transaction. It requires a visible QUEUED row matching snapshot,
algorithm, source label, mode, configured/relative/effective values and all three
weights. Doubles use exact raw-bit checks; null/inconsistent fields are rejected.
No transaction covers mining or filesystem verification. This is not a durable
claim/lease or an exactly-once-across-restart guarantee; WDM-05.5 must integrate
durable lifecycle ownership before promising those behaviors.

### Worker revalidation and complete result ownership

A worker moves SUBMITTED to internal RUNNING, then `RunExecutionVerifier.refresh`
brackets a fresh `RunPreparationService.prepare` with committed-row checks.
Actual raw/SPMF/mapping integrity and the accepted codec/parser are rerun after
queue delay. Fresh publication/raw/source binding, support resolution and item
weights must match the reserved input. The engine receives the freshly verified
immutable database. Queue delay is not treated as evidence that files stayed valid.
All of this work is outside registry/creation locks. Preparation's independent
limits still apply; a verification dependency/budget failure is retained as failure,
not retried or treated as successful mining.

The worker invokes the unchanged MiningEngine adapter, which owns each miner and
sampler. Success retains the exact immutable MiningEngineResult/full support map;
RuntimeException failure retains only safe EXECUTION_FAILED outcome metadata and
logs sanitized context. No raw exception chain, paths or dataset/pattern payload
are stored in the failure outcome. Fatal JVM errors are outside normal completion
guarantees; a worker termination is logged without fabricating success/recovery.

One bounded registry counts RESERVED, ACTIVATING, SUBMITTED, RUNNING and terminal
outcomes together. Maximum entries = parallelism + queueCapacity (default two).
This is a count bound, not a byte/heap bound: individual inputs/results may be
large, and sampled heap remains JVM-wide. Original/fresh parsed models may coexist
temporarily during verification. No speed/memory/scalability claim is made.

On success/failure the entry drops its PreparedRun reference. Outcomes retain
capacity until explicit successful consumer handoff acknowledgement. No timeout,
TTL, oldest-result eviction, discard consumer, restart trick or automatic ack is
installed to accept more work. Consequently, with no production consumer yet,
two completed outcomes fill the default provider even when workers are idle.
This deliberate backpressure preserves outputs for WDM-05.5/06.

Internal Java handoff APIs (no HTTP endpoint) are:

- outcome(runId): non-destructive optional terminal outcome.
- completion(runId): read-only CompletionStage for admitted work; observation
  never releases capacity. Consumer callbacks run outside the registry lock on
  the completing thread unless the consumer explicitly schedules them elsewhere.
- state/retainedCount: internal task visibility, not persisted run status.
- acknowledge(runId, token): removes only the current terminal entry with the
  exact generation token. Active/wrong/stale/repeated acknowledgements do not
  release another slot. The future consumer owns any references it retains after
  handoff; this provider does not bound arbitrary caller-held result references.

An Outcome redacts its result in toString. No automatic production consumer,
pattern DB writes or result browsing/export is provided. Completion store and queue
are in memory and do not survive process restart.

### Shutdown

Bean destruction stops new reservations, releases unadmitted entries and invokes
executor.shutdown to drain accepted jobs. It waits at most the configured grace
period, logs any still SUBMITTED/RUNNING run IDs and leaves running work truthful.
Repeated close is idempotent. Interrupted waits preserve the interrupt flag.
There is no shutdownNow, Thread.stop, successful-cancellation claim or hard miner
timeout. Workers may continue after grace while the process remains alive;
process termination can lose in-memory work/outcomes. This is explicitly not
restart recovery. Owned worker threads are non-daemon and are not confused with
another service's threads; tests release all latches before teardown.

### Verification and evidence

New test classes distinguish their evidence:

- ManagedRunExecutionAdmissionTest: real bounded pools, controlled/mock verifier
  and engine, latch-based reservation/capacity, queue, callback/result ownership,
  one/two-worker concurrency, executor rejection, activation/close/generation and
  activation/shutdown races, idempotent activation, safe failure and graceful drain.
- RunExecutionVerifierTest: actual comparison logic with mocked preparation/DB
  boundary; committed-read ordering, binding/numeric/weight drift and errors.
- RunExecutionConfigurationTest: small real Spring contexts, default/false disabled,
  one enabled provider, conservative defaults and invalid enabled limit startup.
- ManagedRunExecutionIntegrationTest: production enabled provider + real shared
  Auth/HTTP, isolated MySQL 8.4 V2 schema/Hibernate validate, real private artifacts
  from upload/validation/conversion and both actual core engines. A spy adds only
  latches before calling the real adapter; no fabricated successful result.

Integration proves real 202 while the engine is held, committed-row verification,
bounded queue/no-insert 503, result-retention backpressure, ownership ack/reuse,
valid empty result, and complete pattern/support maps/counts equal direct-core.
It confirms DB remains QUEUED/null timestamps and PATTERN empty. A second case
modifies only test-owned SPMF after the second job queues; its worker reverification
fails before engine invocation. The already-verified first job still mines its
immutable original input correctly. Bytes are restored in finally. A third case
proves absent/mismatched committed rows never execute and restores its controlled
one-ULP SQL mutation. No owner DB/storage or production configuration is used.

RunCreationIntegrationTest now explicitly sets execution.enabled=false, preserving
the accepted WDM-05.3 no-provider/controlled-provider evidence regardless of host
settings. No old assertions or accepted algorithm tests were removed.

Retained checkpoints under `.agent-workspace/phase-wdm/`:

- WDM-05.4-unit.log: initial 17/17 scheduler/verifier/config cases PASS, native exit 0.
- WDM-05.4-focused.log: core 11/11 and backend 24/24 PASS, no failures/errors/skips,
  native Maven exit 0. Includes 20 new cases at this checkpoint and four unchanged
  WDM-05.3 integration groups. Existing Table 1 three-oracle and retention tests run.
- WDM-05.4-unit-final.log: 19/19 PASS after adding two missing coverage cases for
  actual two-worker parallelism and shutdown during activation verification.
  Final new test count is 22 (12 scheduler, 4 verifier, 3 config, 3 integration).
  The helper ExecutionTestInputs is not a test case. No failure checkpoint occurred.

Focused command:

```powershell
mvn -pl backend -am test '-Dtest=ManagedRunExecutionAdmissionTest,RunExecutionVerifierTest,RunExecutionConfigurationTest,ManagedRunExecutionIntegrationTest,RunCreationIntegrationTest,ResultRetentionPolicyTest,BitPruneTable1ThreeOracleDifferentialTest' '-Dsurefire.failIfNoSpecifiedTests=false'
```

Expanded unit command:

```powershell
mvn -pl backend -am test '-Dtest=ManagedRunExecutionAdmissionTest,RunExecutionVerifierTest,RunExecutionConfigurationTest' '-Dsurefire.failIfNoSpecifiedTests=false'
```

Regression command:

```powershell
mvn -pl backend -am test '-Dtest=*,!BackendApplicationTests' '-Dsurefire.failIfNoSpecifiedTests=false'
```

Evidence: WDM-05.4-regression.log. Only default-environment BackendApplicationTests
is excluded/NOT VERIFIED. Counters from inner latch/config/assertion loops are not
counted as extra JUnit cases. Timeouts prevent hanging tests, not speed assertions.
Final selected regression: **mining-core 1182/1182 and backend 1064/1064 PASS**,
zero failures/errors/skips, native Maven exit 0 and BUILD SUCCESS. All 22 new cases
are included in 1064, not added on top. Existing core differential/three-oracle
suites run unchanged. Expected invalid-configuration/grace/failure-control logs
and existing Mockito/JVM-agent/pool-shutdown warnings do not change the result.
No production benchmark, data migration or mining-output disagreement occurred.

### Acceptance criteria — 05.4-AC01–10 PASS for approved managed-execution scope

| AC | Evidence |
|---|---|
| 05.4-AC01 | Real conditional Spring wiring, false/missing default, exactly one enabled provider and enabled-limit startup validation |
| 05.4-AC02 | Reserved work never starts; actual absent/one-ULP-mismatched committed-row negatives reject; creation/activation ordering remains verified |
| 05.4-AC03 | Enabled real HTTP returns 202 while actual engine is latch-held; named worker/no active DB transaction; rejection never uses caller thread |
| 05.4-AC04 | One/two-worker concurrency and bounded queue tests; reservations/completed outcomes count toward the same finite capacity |
| 05.4-AC05 | Concurrent/repeated activation, close during verification, generation reuse, executor rejection and shutdown race/drain tests |
| 05.4-AC06 | Real queued artifact tamper blocks second engine invocation; binding/config/weight drift and dependency-error controls |
| 05.4-AC07 | Non-destructive complete-result retention, token ack/reuse, wrong/active/repeated ack rejection, no TTL/discard consumer |
| 05.4-AC08 | Enabled production provider + both actual algorithms: complete maps/counts equal direct-core; legitimate empty result completes |
| 05.4-AC09 | Focused core/backend 11/24, expanded unit 19 and selected regression 1182/1064 PASS with native exit 0 |
| 05.4-AC10 | DB remains QUEUED/null lifecycle times/PATTERN empty; no lifecycle/recovery/durability or production-deployment acceptance claim |

Final source/whitespace/link/scope audit PASS. Ten new Java files (five production,
four test classes plus one reusable test fixture); eleven modified pre-existing
files (application.yaml, .env.example, explicit-disabled 05.3 integration test,
two admission/creation Javadocs and six docs/checklist). Four retained evidence
logs. Existing WDM-05.1–05.3 work, RunEntity changes, .gitignore work and evidence
preserved. No deletion/move, disposable helper, pending cleanup, dependency/core/
migration/frontend change or Git stage/commit/push. Owned test workers/latches,
containers/temp data/context resources were released through test teardown.
No real .env, owner service/DB/storage was modified or used for execution.

Default-environment startup test, browser E2E, live deployment/capacity, forced
cancellation, DB lifecycle/recovery, exactly-once-across-restart and durable
result persistence remain **NOT VERIFIED**. Owner accepted WDM-05.4 in chat on
2026-11-07; checkbox checked. This acceptance-only update does not rerun tests
or alter production code. Owner requested the WDM-05.5 plan; implementation has
not started.

### Research fidelity

- Accepted-contract confirmed: unchanged engine/adapter, exact support and pattern
  identity, configured/resolved threshold and weighted acceptance; normal sampler
  ownership/metrics stay inside the adapter.
- Engineering inference: opt-in execution settings, count bounds, reservation state
  machine, committed checks/reverification, internal completion retention/token ack
  and graceful shutdown. No source-confirmed performance claim is introduced.
- Unresolved: R6, exact-real comparator equivalence, DB lifecycle/recovery and
  durable result handoff remain unchanged. WDM-05.5 had not started at that checkpoint;
  the implementation below supersedes it.

## WDM-05.5 — Run state and failure handling (2026-11-07)

Owner approved implementation after the detailed plan, including the lease,
heartbeat and recovery settings. Technical verification is recorded below;
status is **TECHNICAL PASS / OWNER ACCEPTED (2026-11-07)**. Earlier 05.3/05.4
statements describe their historical scope; current enabled execution uses this
lifecycle. Default disabled execution still returns 503 without inserting a run.

### Schema and transaction boundary

Additive runtime migration `V3__mining_run_execution_ownership.sql` adds nullable
`execution_owner_id VARCHAR(40)`, `execution_attempt_id VARCHAR(40)` and
`execution_lease_until DATETIME(6)`, an all-null-or-all-present CHECK and index
`(status, execution_lease_until)`. V1/V2 are immutable. There is no backfill,
status rewrite or fabricated ownership for historical runs. SQL was reviewed
against creation, claim, heartbeat and recovery predicates before integration.

The managed reservation issues coordinator/attempt UUIDs. `createOwned` binds
these and the initial DB-clock lease inside the same short transaction as the
QUEUED row. Activation remains after commit. Production wiring always supplies
the lifecycle; package-private scheduler-only constructors and the optional
admission ownership default preserve existing isolated tests/legacy port fixtures.
They do not enable an unowned production executor.

`RunLifecycleRepository` uses separate REQUIRES_NEW transactions (5-second
transaction timeout). Claim is a single conditional QUEUED -> RUNNING update;
success requires owned, live RUNNING. Failure requires the observed owned, live
QUEUED/RUNNING state. Every write matches run ID, owner and attempt. Terminal
rows cannot be overwritten. No mining or artifact I/O runs inside lifecycle
transactions. Input publication/config/weight verification occurs after claim
and before invoking the unchanged adapter.

`started_at` uses GREATEST(created_at, UTC_TIMESTAMP(6)); `finished_at` uses
GREATEST(created_at, COALESCE(started_at,created_at), UTC_TIMESTAMP(6)). Thus a
failure before start leaves started_at null, terminal times are monotonic and
repeated terminal calls cannot rewrite them. Java mappings use Instant; lease
authority is the DB UTC clock.

### Ownership, recovery and failure semantics

| Setting | Default | Meaning |
|---|---|---|
| WDM_EXECUTION_LEASE_MILLIS | 60000 | Live ownership deadline, renewed while active |
| WDM_EXECUTION_HEARTBEAT_MILLIS | 10000 | Interval of a separate maintenance scheduler |
| WDM_EXECUTION_RECOVERY_BATCH | 100 | Maximum expired runs selected in one pass |

Startup validates positive limits and lease >= 3 * heartbeat. A lease is not a
mining runtime timeout. The scheduler renews admitted queued/running entries and
pending finalization; it cannot revive an expired lease. Recovery uses a bounded
ordered SELECT FOR UPDATE SKIP LOCKED and conditional updates. Only expired,
owned QUEUED/RUNNING rows become FAILED/EXECUTION_LEASE_EXPIRED. Legacy/unowned,
live and terminal rows are untouched. No new attempt or automatic engine retry
is created. An old worker may continue computing after lease loss, but its
terminal writes are fenced. Graceful shutdown drains workers up to the existing
grace; it never forces cancellation, and maintenance then stops.

Persisted diagnostics are fixed enum names: ADMISSION_FAILED,
CLAIM_NOT_CONFIRMED, INPUT_REVERIFICATION_FAILED, ENGINE_EXECUTION_FAILED and
EXECUTION_LEASE_EXPIRED. Raw exceptions, paths, SQL and payloads are not persisted.
Logs retain sanitized exception type/stack information rather than raw causes.

A zero-row claim rejects execution; it is never interpreted as permission to
replay an already RUNNING attempt. An uncertain claim exception permits that
original invocation only when fresh readback confirms matching live RUNNING.
Success/failure finalization is confirmed by fresh readback. Unknown DB outcomes
remain pending and maintenance retries DB operations only. Existing terminal
failure diagnostics are preserved rather than overwritten.

Internal computation state and durable lifecycle confirmation are separate.
`Outcome.lifecycleConfirmed` records confirmation; `completion()` is the first
immutable completion snapshot, while `outcome()` can later expose confirmed
finalization. A complete successful map survives pending finalization or lease
loss in the bounded registry. Acknowledgement rejects pending confirmation and
still requires the correct handoff token. No success/failure is automatically
acknowledged; committing SUCCEEDED alone never releases capacity or discards
results. There is no production handoff consumer, TTL or discard policy.

### Verification and acceptance

Focused evidence: `.agent-workspace/phase-wdm/WDM-05.5-focused.log`, 33 tests,
zero failures/errors/skips, BUILD SUCCESS/native exit 0. New tests cover eight
service cases, three managed-lifecycle cases, seven real-MySQL lifecycle cases
and one actual Flyway upgrade. Existing enabled HTTP integration still compares
both real engines' complete pattern/support maps with direct core execution;
it now also verifies durable success/failure timestamps/statuses.

The Flyway test migrates actual V1 -> V2, seeds all five old run statuses,
migrates V3 and validates history. V2 checksum and old values remain unchanged;
new ownership fields are null, the index exists and partial ownership is rejected.
Other full-schema integration fixtures apply V3 on their owned V2 definitions
for Hibernate validate; this fixture helper is not claimed as Flyway evidence.
Twelve such fixtures were adapted, including Auth/export/conversion tests,
without weakening their business assertions. Two partial catalog-only fixtures
do not need V3 and remain unchanged.

The initial compatibility checkpoint (`WDM-05.5-compat-unit.log`) had 27 tests,
two configuration-context errors: the mock persistence repository needed a mock
EntityManagerFactory for @PersistenceContext injection. The harness was fixed;
the focused rerun passed. This was not a production DB failure.

| Criterion | Evidence |
|---|---|
| 05.5-AC01 | Additive real Flyway V3, immutable V2 checksum and old rows preserved |
| 05.5-AC02 | Concurrent claim has exactly one winner; no zero-row claim replay |
| 05.5-AC03 | Owned CAS success/failure and immutable UTC lifecycle timestamps |
| 05.5-AC04 | Fixed safe diagnostics, queued/input/engine failure stage handling |
| 05.5-AC05 | Owner/attempt fencing, expired writes and lease revival rejected |
| 05.5-AC06 | Separate heartbeat, validated defaults and bounded recovery |
| 05.5-AC07 | Recovery skips live/unowned/legacy/terminal rows; no engine retry |
| 05.5-AC08 | Uncertain commit readback and DB-only finalization reconciliation |
| 05.5-AC09 | Complete map retained pending/expired; no premature or automatic ack |
| 05.5-AC10 | Focused integration plus module regression recorded below |

### Scope and limitations

Regression evidence: `.agent-workspace/phase-wdm/WDM-05.5-regression.log`:
mining-core **1182/1182**, backend **1083/1083**, zero failures/errors/skips,
**BUILD SUCCESS**. Command: `mvn -pl backend -am test
"-Dtest=*,!BackendApplicationTests" "-Dsurefire.failIfNoSpecifiedTests=false"`.
Only the default-owner-environment `BackendApplicationTests` is excluded;
isolated full-Boot/Hibernate/MySQL integration tests are included. AC01-10 are
PASS for the approved task scope. Default owner-environment startup remains
**NOT VERIFIED**. `git diff --check` and whitespace checks on new sources PASS;
Git's existing LF/CRLF conversion warnings do not represent whitespace failures.

Changes are confined to backend lifecycle/creation/verifier wiring, V3/entity
mapping, configuration example, focused tests/schema fixtures and documentation.
Task file accounting: 11 new files (six production/migration files and five test
files), 31 existing files updated (ten production/config, fourteen tests and
seven documentation/checklist files). Pre-existing untracked 05.1-05.4 files
remain pre-existing work, even when Git displays them as untracked.

New production files: `ExecutionOwnership`, `LifecycleLimits`, `RunFailureCode`,
`RunLifecycleService`, `RunLifecycleRepository` and the V3 SQL migration. New test
files: `RunLifecycleServiceTest`, `ManagedRunLifecycleTest`,
`RunLifecycleIntegrationTest`, `ExecutionOwnershipMigrationTest` and shared
`testsupport/ExecutionSchema`. Existing creation/admission, verifier, manager,
configuration and entity files are extended. Documentation updates cover root
README/ARCHITECTURE, docs map, data dictionary, both mining contracts and the
local WDM checklist. Three task test logs are retained as ignored evidence;
there is no pending disposable-file cleanup.

No mining-core, frontend, dependency, V1/V2 or real .env changes. No owner DB
migration, production service start, Git staging/commit/push or old-file deletion.
Migration deployment to the owner DB is **NOT VERIFIED**; isolated MySQL success
does not establish any other environment's schema state.

SUCCEEDED means mining completed, not durable pattern storage. RAM results do
not survive process restart. Historical SUCCEEDED rows are neither rerun nor
reclassified when their in-memory results are absent. Retained outcomes can
exhaust admission capacity until explicit handoff. Metrics capture (05.6),
polling API (05.7), durable results (06), hard cancellation and production
rollout remain outside this task. R6 and exact-real comparator equivalence stay
unresolved; no research acceptance or performance claim is changed.

Recommended next task: WDM-05.6 — Metrics Capture; do not begin automatically.

## WDM-05.6 — Proposed metrics capture plan (2026-11-07)

Owner accepted 05.5 and requested this plan. **PLANNING ONLY / IMPLEMENTATION
NOT STARTED at the planning checkpoint**. Implementation below supersedes it.
That planning turn updated only this contract and the local checklist;
no source/schema/config changes, tests, owner DB access or migration execution.
The 05.5 evidence above remains historical evidence, not tests rerun this turn.

### Proposed measurement contract

All internal durations remain nonnegative nanoseconds; persist milliseconds as
`nanos / 1_000_000` (floor), independently per field. A measured sub-millisecond
duration may be zero; absent scopes stay null. Do not sum rounded phase values
to reconstruct a total. Use a monotonic injectable ticker, never lifecycle
timestamps or wall-clock subtraction. No new dependencies or migration expected:
the existing V2 columns already hold the required nullable BIGINT values.

| Column | Proposed source/scope |
|---|---|
| dataset_io_ms | Sum of the three verified storage reads (raw CSV provenance, sequence and mapping) during worker-side refresh; includes each storage call's checksum/path/size checks, excludes surrounding DB reads and codec parsing |
| preprocessing_ms | Native core preprocessing elapsedNanos |
| dfs_mining_ms | Native core DFS elapsedNanos |
| complete_algorithm_ms | Native adapter completeAlgorithmNanos, including core finalization |
| output_io_ms | null: no output-file/pattern persistence stage exists yet |
| total_execution_ms | Worker interval immediately after confirmed claim and before refresh through successful engine return; includes revalidation, parsing, application preparation, adapter overhead/projection and core execution, excludes pre-admission preparation, queue wait, claim, terminal DB finalization/retries and future output |
| peak_sampled_jvm_heap_bytes | Native observedPeakHeapAlgorithmBytes: JVM-wide used heap sampled during the algorithm, not peak across the whole worker interval |
| pattern_count | Complete result patternCount, including legitimate zero |
| candidate_count | PreTree candidateCount; BitPrune generatedExtensions; preserve native F1 exclusion |

The total is an explicitly named worker-compute scope for this contract version,
not HTTP latency, standalone CLI total or a complete persistence workflow.
Dataset reads are verified input I/O, not physical disk latency. Core phase
timings are nested in complete-algorithm time, so the six scopes are not six
disjoint addends. Persisted heap cannot be described as precise RAM, RSS,
per-run allocation or worker-total peak. Keep heap sampling ownership in core;
do not introduce an extra sampler or relabel the algorithm peak as a total peak.

Only successful complete results publish this metric set. Failure/recovery paths
leave these fields null; partial failure metrics are outside this proposal.
Preserve legacy historical values; no backfill and no terminal-row rewrites.

### Implementation sequence and proposed files

1. Freeze these scopes, units, counter definitions and null semantics in both
   mining contracts/data dictionary. Add a small immutable capture/projection
   value under services/run/execution with overflow-safe validation, retaining
   raw nanoseconds until DB projection. Test rounding boundaries and native
   counter/heap mapping before integration.
2. Add a measured internal variant of ConversionReadService.verified, timing
   only existing raw.verifiedBytes and two storage.read calls; preserve all
   integrity checks, capacity budgets and existing HTTP DTOs/verified callers.
   Add a measured preparation/refresh envelope rather than mixing execution
   observations into immutable mining configuration. Propagate measured reads
   through RunPreparationService and RunExecutionVerifier for worker refresh
   only; do not count pre-admission reads or add duplicate I/O for timing.
3. ManagedRunExecutionAdmission captures total scope once around refresh+engine,
   builds the immutable capture, and retains it alongside the complete result.
   Outcome snapshots/reconciliation preserve that original capture; retries
   must not remeasure elapsed time or rerun mining. Adapt scheduler-only seams
   and fixtures deliberately; production must always provide a measured capture.
4. Extend RunEntity's missing phase mappings and typed metric getters. Extend
   RunLifecycleRepository success CAS to write status, finished_at and the whole
   metric set atomically in one owned live RUNNING update. Extend lifecycle
   readback to verify the entire expected persisted snapshot, not just SUCCEEDED.
   Unknown commit/read failure retains result+capture and rejects acknowledgement.
   Retry only the same DB finalization payload; zero-row/recovered/foreign-owner
   writes never publish metrics or mutate terminal rows.
5. Add focused unit, real MySQL CAS/rollback/readback and real-engine managed
   integration coverage. Then run the appropriate core/backend regression and
   finish documentation/checklist evidence with the required task report.

Expected production changes: ConversionReadService, RunPreparationService,
RunExecutionVerifier, ManagedRunExecutionAdmission, RunLifecycleService,
RunLifecycleRepository and RunEntity, plus small immutable capture/envelope
types and a testable ticker seam where needed. Expected tests: measurement
projection, measured reads/preparation, lifecycle reconciliation, managed
execution and real MySQL integration; update only directly affected fixtures.
Documentation: both mining contracts, data dictionary, architecture/docs map
where current behavior changes, and local checklist. Exact names/counts are
confirmed before implementation. No frontend/controller/API, core mining logic,
V1/V2/V3, real .env or owner environment edits are planned. Any unexpected schema
need requires a concrete revised proposal before expanding scope.

### Proposed acceptance criteria (all NOT VERIFIED until implementation)

| ID | Required evidence |
|---|---|
| 05.6-AC01 | Explicit six-scope contract, heap scope, units and absent-vs-zero semantics |
| 05.6-AC02 | Native timing/heap projection retained exactly before conversion; original result/map unchanged |
| 05.6-AC03 | Candidate mapping correct for both algorithms; zero-pattern run persists genuine zero |
| 05.6-AC04 | Deterministic conversion tests at 0, sub-ms, exact-ms and large long values; negatives/inconsistent captures rejected |
| 05.6-AC05 | Worker-only verified-read measurements exclude DB/parsing/pre-admission work; no extra reads; integrity negative controls still reject |
| 05.6-AC06 | Fake-ticker tests prove total boundaries and exclusion of queue/claim/finalization/retry delay |
| 05.6-AC07 | MySQL success status+metrics commit atomically; rollback cannot leave partial metrics; expired/foreign/terminal writes rejected |
| 05.6-AC08 | Lost commit acknowledgement and read failure retain original result/capture; full readback confirms; retries do not remeasure/rerun/auto-ack |
| 05.6-AC09 | Both real engines' managed runs preserve complete pattern/support maps and persist correctly mapped counts/timings/heap; failure/recovery/legacy values preserved |
| 05.6-AC10 | Focused and core/backend regression PASS, scope/diff checks and limitations reported separately from owner deployment |

This is engineering consumption of accepted measurements, not a change to
algorithm definitions or a performance benchmark. No speed/memory improvement
claim is authorized by this task. R6 and exact-real numeric questions remain
unchanged. Recommended task after implementation/acceptance: WDM-05.7 — Run
Status API. Durable patterns and automatic handoff remain WDM-06 work.

## WDM-05.6 — Implementation and verification (2026-11-07)

Owner authorized implementation, then accepted 05.6 and requested the 05.7 plan
while final verification was in progress. This acceptance does not replace the
technical checks reported here. Final status: **TECHNICAL PASS / OWNER ACCEPTED**.
Implementation follows the preceding scopes
without a new migration, dependency, public API or core change.

RunMetricsCapture is immutable and validates nonnegative counts/heap/durations,
phase containment and dataset-I/O + complete-algorithm containment in total,
using subtraction to avoid overflow. It retains raw nanos and independently
floors to millis. Its nullable Persisted view supports exact comparison with
historical/unmeasured database rows. Only a validated capture can enter the
success repository method; a missing payload cannot complete production success.

ConversionReadService.verifiedMeasured times the three existing raw/sequence/
mapping verified storage calls using a supplied monotonic ticker. DB access,
codec parsing and result copying remain outside dataset-I/O timing. Old read
callers/HTTP DTOs remain compatible. MeasuredRunPreparation carries I/O through
measured preparation and refresh; the worker's original configuration/binding,
threshold/weight gates and before/after ownership checks remain intact. No
extra storage read, new algorithm sampler or benchmark is introduced.

Managed execution measures total immediately after confirmed claim through
engine return. The same ticker is passed into input timing. Outcome now retains
the capture alongside the complete result. Successful computation is retained
even if capture validation/finalization cannot be confirmed; such an outcome
cannot be acknowledged. Maintenance retries the exact original capture only,
never reconstructs missing measurements or repeats mining. Scheduler-only
package-local tests still have no durable metrics lifecycle.

RunLifecycleRepository writes all counts, input/core/total timings and algorithm
sampled heap with status/finished_at in one conditional UPDATE. output_io_ms is
null. Full-payload readback plus matching ownership, SUCCEEDED and no error is
required for confirmation. Terminal, foreign and expired attempts cannot publish
or replace metrics. Failed/recovered new rows remain unmeasured; historical
values are not backfilled or rewritten. RunEntity adds five phase mappings and
typed metric getters on existing V2 columns, retaining compatibility accessors.

### Evidence checkpoints

All logs are retained under `.agent-workspace/phase-wdm/`:

- WDM-05.6-unit-initial.log: 37/37 existing unit compatibility cases PASS,
  native exit 0 / BUILD SUCCESS.
- WDM-05.6-focused.log: 55 tests, zero assertion failures and one fixture error.
  MySQL denied trigger creation because binary logging required extra privilege.
  No finalization statement was attempted in that failed rollback case.
- The owned lifecycle test container was configured with
  `--log-bin-trust-function-creators=1`; no owner MySQL setting was changed.
- WDM-05.6-focused-final.log: 55/55 PASS, zero failures/errors/skips, native
  exit 0 / BUILD SUCCESS. Includes 17 added cases across projection, verified
  read timing, preparation, refresh, manager, reconciliation and MySQL rollback.
  Real shared-Auth/HTTP runs use both actual engines with full pattern/support
  comparison and exact metric-payload assertions; genuine zero patterns and
  failed input metrics are checked. A controlled stale worker retains its map
  and capture after another coordinator's recovery, without publishing success.
- WDM-05.6-regression.log: mining-core 1182/1182 and backend 1100/1100 PASS,
  zero failures/errors/skips, native exit 0 / BUILD SUCCESS. Command:
  `mvn -pl backend -am test "-Dtest=*,!BackendApplicationTests" "-Dsurefire.failIfNoSpecifiedTests=false"`.
  Only the default-owner-environment BackendApplicationTests is excluded;
  isolated full-Boot/Hibernate/MySQL integration suites are included.

### Acceptance evidence

The implementation supersedes the planning table's NOT VERIFIED checkpoint.

| ID | Implementation evidence |
|---|---|
| 05.6-AC01 | Explicit six-scope, algorithm-heap, null/zero and unit contract above |
| 05.6-AC02 | RunMetricsCaptureTest compares raw native phase/complete/heap values for both engines; managed HTTP compares full support maps |
| 05.6-AC03 | Both native candidate variants checked; real empty HTTP run persists pattern count zero |
| 05.6-AC04 | Six conversion boundary cases including Long.MAX_VALUE; subtraction-based containment rejects negative/overflowing combinations |
| 05.6-AC05 | ConversionReadMetricsTest shares a deterministic dependency clock: only 41 ns of reads counted against 12041 ns including DB/parsing; exact call counts and invalid/changed-publication controls |
| 05.6-AC06 | Managed fake-clock test excludes pre-worker/claim/finalization time and confirms unchanged total on reconciliation |
| 05.6-AC07 | Real MySQL trigger rejection rolls back status/finished_at/all metrics; terminal/foreign/expired CAS negatives pass |
| 05.6-AC08 | Lost commit/read failure and missing/mismatched metric readback stay pending; same capture retained across retry, one engine call, no automatic ack |
| 05.6-AC09 | Both real engines' full maps + nine persisted fields checked; failure nulls and recovered stale-worker map/capture retention verified; no SQL backfill path |
| 05.6-AC10 | Focused 55 + core/backend regression 1182/1100 PASS; preservation and whitespace checks PASS |

AC01-10 PASS for this engineering scope. Owner deployment/startup remains
NOT VERIFIED; technical checks do not establish production readiness.

### Scope and research fidelity

New files: services/run/MeasuredRunPreparation.java,
services/run/execution/RunMetricsCapture.java, tests/execution/RunMetricsCaptureTest.java
and tests/dataset/conversion/ConversionReadMetricsTest.java. Existing production
changes: ConversionReadService, RunPreparationService, RunExecutionVerifier,
ManagedRunExecutionAdmission, RunLifecycleService, RunLifecycleRepository and
RunEntity. Existing tests updated: RunPreparationServiceTest,
RunExecutionVerifierTest, ManagedRunLifecycleTest, RunLifecycleServiceTest,
RunLifecycleIntegrationTest and ManagedRunExecutionIntegrationTest.
Seven documentation/checklist files are updated for behavior, acceptance and
the requested next plan. Four ignored test logs are retained as evidence. No
source/evidence files are deleted and no disposable helper requires cleanup.
Final accounting: four new source/test files and 20 existing files changed.
A 60-file scoped SHA-256 baseline confirms exactly those 20 expected changes;
the other 40 files remain unchanged, including V1/V2/V3 and real .env.
`git diff --check` and new-source whitespace checks PASS. Existing unrelated
dirty/untracked changes are preserved; they are not counted as newly created
by this task merely because Git shows them untracked.

Accepted-contract confirmed: native core measurements/counters and complete
patterns/supports remain unchanged. Engineering inference: worker scope,
verified-input timing, floor conversion and atomic publication follow the
owner-approved plan. No performance claim is made. R6 and exact-real comparator
equivalence remain unresolved. Default execution remains false. No owner DB
access/migration, service start, real .env edit or Git stage/commit/push.
Owner environment deployment/startup remains NOT VERIFIED. Durable patterns,
automatic handoff and restart survival of the in-memory map remain future work.

## WDM-05.7 — Proposed Run Status API plan (2026-11-07)

Owner requested planning after accepting 05.6. **PLANNING ONLY / IMPLEMENTATION
NOT STARTED**. No API implementation is authorized by this plan-only request.

This is the historical planning checkpoint; owner subsequently approved
implementation and explicitly requested running V3. Current work/evidence are
recorded in the WDM-05.7 implementation section below.

### Endpoint and access

Add GET `/api/v1/admin/mining-runs/{runId}`, the Location URL returned by creation.
It uses shared ADMIN Bearer authorization before ID/request-shape checks. No
query parameters or request body. Return the standard ApiResponse envelope,
requestId, application/json, Cache-Control:no-store and nosniff. Existing run
states (including FAILED) return 200; absent run 404, invalid UUID 400,
unauthenticated 401, non-admin 403 and unavailable DB 503. Unsupported media/
method follows the existing API contract. Do not echo untrusted input in errors.
The read endpoint is not conditional on execution.enabled: old runs remain
readable with execution disabled or no in-memory entry.

### Proposed response model

A dedicated RunStatusResponse (not legacy RunResponse) exposes:

- runId, snapshotId, algorithm and persisted status;
- configuration: minsupMode, configured value, relative fraction, effective
  threshold, MaxW/MinW/MeanW, preserving absent historical values;
- UTC Instant createdAt/startedAt/finishedAt, with null for missing timestamps;
- persisted patternCount/candidateCount and nullable metric fields for the six
  timing scopes and sampled heap;
- safe failure object {code,message} only for FAILED, using fixed public text
  and allowlisted 05.5 enum codes; unknown/legacy error text maps to RUN_FAILED;
- measurement descriptors: MILLIS/BYTES and current contract/scopes only when
  ownership exists and all required 05.6 measurement fields are populated.
  Historical/unmeasured rows have unknown measurement contract, not guessed
  05.6 provenance. Raw observed values remain distinguishable from missing data.

All BIGINT count/time/byte fields serialize as decimal strings to preserve
precision for JavaScript consumers. Null stays null; zero serializes as "0".
Do not expose execution owner/attempt/lease, internal acknowledgement tokens,
raw error_message, paths, SQL, legacy_metadata_json, private artifact references,
patterns or occurrence structures. Ownership may inform internal provenance
classification but never appears in the public DTO.

QUEUED/RUNNING remain the DB state until finalization/recovery commits; do not
derive an alternate public state from the executor. LEGACY_UNVERIFIED is explicit.
SUCCEEDED reports completed mining/metrics; it makes no pattern-durability claim.
No result-availability boolean is inferred from status or in-memory presence;
a future WDM-06 result endpoint must establish its own persistence evidence.

### Implementation sequence

1. Freeze response fields/null/precision/error/scope semantics and fixtures.
2. Add RunStatusRepository: one coherent SELECT/projection in a short read-only
   transaction, with all needed columns from MINING_RUN. Avoid snapshot/file
   joins, scans, N+1 and exposing a live JPA entity. A concurrent finalization
   yields a coherent pre-commit or post-commit row, not mixed field reads.
3. Add RunStatusService and explicit response mapper: validate canonical UUID,
   map allowlisted status/failure safely, preserve nullable values and read
   while execution is disabled. Unexpected corrupt state fails closed using
   the existing safe error contract. Log lookup failures safely without payloads.
4. Add RunStatusController with existing RequestChecks and Auth filter; preserve
   POST creation. No dependency on engine/admission, artifact storage or mutation
   repository. A GET never renews, recovers, acknowledges, reruns or changes DB.
5. Unit/controller tests, real Auth/MySQL integration, final diff/module
   regression and contract/checklist evidence. No new migration, core/frontend,
   .env, polling UI, list/search/pagination, SSE, cancellation or retry endpoint.

Expected new files: RunStatusController, RunStatusResponse, RunStatusService,
RunStatusRepository and a response mapper/projection as needed; focused tests
for mapping/service/controller and real integration. Existing Auth route-policy
tests and contracts/docs change only as required. Legacy RunResponse/RunRepository
are not silently repurposed because their fields do not match this API contract.

### Acceptance criteria (all NOT VERIFIED until implementation)

| ID | Required evidence |
|---|---|
| 05.7-AC01 | GET resolves the creation Location with standard envelope/UTC/security/cache headers |
| 05.7-AC02 | Real shared Auth: anonymous 401, non-admin 403, ADMIN allowed before resource checks |
| 05.7-AC03 | Valid missing UUID 404, malformed UUID/query/body 400, DB failure safe 503 |
| 05.7-AC04 | All five statuses, nullable timestamps/config and native threshold values map faithfully |
| 05.7-AC05 | All persisted metrics/counts retain null vs zero and exact decimal-string precision |
| 05.7-AC06 | Fixed safe failure messages; sensitive/unknown legacy diagnostics and ownership never leak |
| 05.7-AC07 | Enabled real run polled through active/terminal states; returned final fields match persisted 05.6 metrics |
| 05.7-AC08 | Disabled execution and no retained outcome still permit read; legacy/unmeasured scope remains unknown |
| 05.7-AC09 | Repeated polling leaves status/timestamps/metrics/lease and registry capacity unchanged; no engine/storage/ack calls |
| 05.7-AC10 | Focused Auth/MySQL and relevant regression PASS; no schema/mining changes or production-deployment claims |

Recommended next after implementing and accepting 05.7: WDM-05.8 — Admin
Execution UI. This turn does not implement 05.7 or start 05.8.

## WDM-05.7 — Implementation and V3 verification (2026-11-07)

Owner approved the plan and authorized implementation plus running migration V3.
Final status: **TECHNICAL PASS / OWNER ACCEPTED (2026-11-07)**. Implementation
and verification are complete. Owner subsequently requested the 05.8 plan below;
this acceptance update does not rerun tests or apply V3 again.

### Public read contract

GET `/api/v1/admin/mining-runs/{runId}` resolves the creation Location through
RunStatusController -> RunStatusService -> RunStatusRepository, with explicit
RunStatusResponseMapper projection. Shared WdmRequestFilter/ADMIN Bearer auth
runs before query/body/UUID checks. There is no execution.enabled condition,
engine/admission dependency, storage access, lease renewal, recovery or ack.
Queries/body are rejected; canonical lowercase UUIDs identify runs. Existing
resources including FAILED/LEGACY_UNVERIFIED return 200, missing IDs 404,
invalid input 400, missing identity 401, non-admin 403, unavailable DB 503.
Existing safe transport/media/method error behavior remains intact.

Responses use ApiResponse, requestId, application/json, Cache-Control:no-store
and nosniff. RunStatusResponse has exactly these top-level fields: runId,
snapshotId, algorithm, status, configuration, createdAt, startedAt, finishedAt,
patternCount, candidateCount, metrics, measurement and failure.

configuration has minsupMode/configuredValue/relativeFraction/effectiveThreshold/
maxW/minW/meanW. metrics has datasetIoMs/preprocessingMs/dfsMiningMs/
completeAlgorithmMs/outputIoMs/totalExecutionMs/peakSampledJvmHeapBytes.
All BIGINT observations are decimal strings (zero "0", absent null), including
unsigned values up to 18446744073709551615. SQL CAST(... AS CHAR) avoids an
intermediate signed-Long extraction by Hibernate. A single PK SELECT obtains
all public values plus internal diagnostic and boolean ownership completeness;
no live JPA entity escapes the short REQUIRES_NEW read-only transaction.

SQL DATE_FORMAT projects UTC DATETIME wall values as ISO strings before Instant
parsing, preventing JDBC/JVM default timezone shifts. Public instants preserve
microseconds; missing lifecycle timestamps stay null. Configuration doubles are
copied without resolver/formula changes. The legacy RunResponse/RunRepository
are not repurposed.

measurement always states MILLISECONDS/BYTES. Its contractVersion and three scope
descriptors are populated only for owned rows with every required measured
count/timing/heap field present. Historical/unmeasured rows retain unknown scope.
Current descriptors are WDM-05.6, WORKER_VERIFIED_RAW_SEQUENCE_MAPPING_READS,
POST_CLAIM_REFRESH_THROUGH_ENGINE_RETURN and ALGORITHM_JVM_WIDE_USED_HEAP_SAMPLING.
No descriptor implies precise RAM, run-private heap or durable pattern storage.

failure is null outside FAILED. Known 05.5 failure enum names map to fixed public
messages; any null/unknown historical diagnostic maps to RUN_FAILED / "Run failed."
Raw error_message, private paths, SQL, legacy_metadata_json, execution owner/
attempt/lease, ack tokens and patterns never enter the response. Unknown status
fails closed as INVALID_RESOURCE_STATE. Polling observes DB state only; no
result-availability flag is invented from SUCCEEDED or registry membership.

### Owner migration V3 — actual local evidence

The explicit migration request was executed against the inspected existing
container wspm-mysql, MySQL 8.4.13, application user wspm_app, local target
127.0.0.1:3307/wspm_combo. Read-only preflight found V1/V2/V3 already SUCCESS,
with checksums -1801304247 / 727165286 / 1697070581 respectively and zero mining
runs. V3 already existed before this task's migrate call; its origin is not
attributed to this agent.

Actual Flyway 12.4.0 validate -> migrate(target=3) -> validate completed with
native exit 0. All three runtime migrations validated; migrate executed **zero**
new migrations because schema was already current at V3. No SQL was reapplied,
history repaired, baseline inserted or clean performed. Readback confirmed all
three nullable columns, idx_run_execution_lease(status,execution_lease_until),
enforced chk_run_execution_ownership and zero MINING_RUN rows. No application
service was started or owner business row changed.

Evidence: `.agent-workspace/phase-wdm/WDM-05.7-owner-v3.log`. A task-created
standalone verification source used existing local Flyway/JDBC jars, loaded
credentials without printing them, and was deleted after completion. The log
remains. This establishes this local schema's V3 state; it does not claim other
environments were migrated, or that API deployment/startup was verified there.

### Verification checkpoints

Ignored logs under `.agent-workspace/phase-wdm/` are retained, including failures:

- WDM-05.7-unit.log: 18 tests, 3 controller failures due to omitted explicit
  @PathVariable name under a build without retained parameter names. Fixed by
  declaring @PathVariable("runId").
- WDM-05.7-focused.log: 27 tests, two integration assertion failures and one
  controller-fixture restubbing error. Tests exposed native DATETIME timezone
  shift, an old customer token whose account now held ADMIN, and Mockito
  when(...).thenThrow restubbing behavior. Fixed UTC SQL projection, a separate
  CUSTOMER account, and doThrow-based fixture setup.
- WDM-05.7-focused-final.log: 27 tests, two assertion failures: stale compiled
  customer fixture and unsigned BIGINT native extraction. Corrected SQL CAST
  before numeric extraction; the fresh run includes the separate customer.
- WDM-05.7-focused-verified.log: 27 tests, one unsigned extraction failure;
  the corrected customer/UTC fixtures passed. The CAST correction had not been
  in that run's compiled classes; the subsequent fresh run compiles it.
- Subsequent focused/regression outcomes are recorded below on completion.

- WDM-05.7-focused-complete.log: **27/27 PASS**, zero failures/errors/skips,
  native exit 0 / BUILD SUCCESS, after a fresh compile of all corrections.
- WDM-05.7-regression.log: **mining-core 1182/1182, backend 1120/1120 PASS**,
  zero failures/errors/skips, native exit 0 / BUILD SUCCESS. Command:
  `mvn -pl backend -am test "-Dtest=*,!BackendApplicationTests" "-Dsurefire.failIfNoSpecifiedTests=false"`.
  Only default-owner-environment BackendApplicationTests is excluded; isolated
  real Boot/Auth/Hibernate/MySQL integration suites are included.

### Acceptance evidence

The earlier planning table's NOT VERIFIED state is superseded for verified
criteria below. Unit tests and real integration evidence are distinguished.

| ID | Evidence |
|---|---|
| 05.7-AC01 | MVC and real HTTP resolve the creation URL, standard envelope/requestId/UTC/no-store/nosniff |
| 05.7-AC02 | Actual shared Auth rejects anonymous and a separate CUSTOMER before malformed resource checks |
| 05.7-AC03 | Real malformed UUID/query and missing-resource cases; MVC body/media/method cases; mocked DB failure translated safely by service/controller |
| 05.7-AC04 | All five statuses in real MySQL/HTTP; microsecond UTC and absent timestamps; unit threshold/null mapping |
| 05.7-AC05 | Real MySQL unsigned maximum 18446744073709551615, >2^53 counts, zero/null and all real measured fields checked |
| 05.7-AC06 | All five allowlisted codes in unit projection; raw historical error sanitized in real HTTP, ownership/tokens absent |
| 05.7-AC07 | Enabled real-engine HTTP polls RUNNING/QUEUED/SUCCEEDED/FAILED; complete maps/native metric payload still match |
| 05.7-AC08 | Disabled execution has no admission provider yet can read historical rows; SUCCEEDED remains readable after registry ack |
| 05.7-AC09 | Three polls per synthetic status preserve full DB row; terminal real rows and retained capacity unchanged; one PK read/no mutable dependencies |
| 05.7-AC10 | Focused 27 + core/backend regression 1182/1120 PASS; preservation and whitespace checks PASS |

AC01-10 PASS for the approved scope. DB-unavailable HTTP behavior is service/MVC
evidence, not a claim that
the owner DB was deliberately stopped or fault-injected.

Unit/controller tests cover all five statuses, thresholds/nulls/zero, all five
safe failure codes, unknown diagnostics, unknown status, absent/invalid IDs,
safe service errors, exact JSON precision, media/method/auth/request shape and
envelope headers. Extended existing real Auth/MySQL fixtures cover disabled
execution/all statuses, full unsigned range and row preservation. Enabled real
engine HTTP fixtures poll RUNNING/QUEUED/SUCCEEDED/FAILED, compare every persisted
metric field, compare terminal rows/capacity before/after GET and read SUCCEEDED
after explicit registry handoff. Synthetic status/precision rows are test-only
protocol fixtures, not evidence of real mining output. Real-engine complete maps
remain independently compared with direct core runs.

### Scope, acceptance and limitations

Five production files are new: RunStatusController, RunStatusResponse,
RunStatusRepository, RunStatusResponseMapper and RunStatusService. Two new test
files are RunStatusControllerTest and RunStatusServiceTest. Two existing
integration fixtures are extended; existing source/lifecycle/core logic remains
unchanged. Root README/ARCHITECTURE, docs map, data dictionary, request contract
and local checklist are updated. No frontend, mining-core, dependency, .env or
V1/V2/V3 source changes; no Git staging/commit/push. Owner DB permission applies
to the requested V3 check/migrate only. No new migration or owner data seed.

Final inventory: seven new source/test files and eight existing files updated;
seven ignored logs retained. A 69-file scoped SHA-256 baseline confirms exactly
eight expected modifications and 61 unchanged files, including .env, all three
migrations and previous lifecycle source. Diff/new-source whitespace checks
PASS. The only deleted item was the task-created disposable migration helper
(and its empty task directory); no pre-existing file was removed. No pending
disposable cleanup remains.

Accepted-contract
confirmed: stored thresholds and native measurements/maps are consumed unchanged.
Engineering inference: read projection, exact-string JSON and safe error mapping
follow the approved plan. R6 and exact-real comparator questions are unchanged.
API production rollout, browser UI, durable patterns and availability of restart-
surviving results remain NOT VERIFIED/outside this task. Recommended next task:
WDM-05.8 — Admin Execution UI; do not start automatically.

## WDM-05.8 — Proposed Admin Execution UI plan (2026-11-07)

Owner accepted 05.7 and requested planning. **PLANNING ONLY / IMPLEMENTATION
NOT STARTED at the planning checkpoint**. Implementation below supersedes it.
That planning turn updated only this contract and the local checklist;
no frontend/backend source, tests, dependencies, config or database changes.

### Existing slice and intended flow

The current /admin/mining-runs page has MiningConfigForm and its revision/Auth-
bound preview/validation hook. It does not create runs. Reuse that form, the
existing DataMiningGuard, shared Auth adapter, Mantine 4 and WDM API client.
The client uses /api (or the configured API root) with /v1/admin/... paths;
CRA proxies to 127.0.0.1:8080. No dependency upgrade or base-URL change is needed.

The intended UI flow is: choose snapshot + algorithm + minsup -> preview/check
configuration -> explicit "Tạo lần chạy" -> confirmed 202 -> navigate to
/admin/mining-runs/:runId -> GET status -> display active/terminal state. A
separate small open-by-ID action and copyable detail link allow revisiting a
known run. Refresh/direct navigation only issues GET. No run-list/history API
exists; do not fabricate a server-backed list or infer the newest run from time.

### Proposed steps

1. Add execution API/types/strict decoders, separate from configuration preview
   types. POST /v1/admin/mining-runs sends exactly MiningRequest's four fields;
   expected status is 202, with canonical runId, matching snapshotId/algorithm,
   creation status QUEUED and valid UTC createdAt. Do not interpret this snapshot
   as live execution state. GET /v1/admin/mining-runs/{runId} expects 200 and
   validates requested run identity, all five statuses, nullable config/time/
   count/metric fields, safe failure shape and measurement descriptors. Preserve
   BIGINT decimal strings through the full unsigned range; no Number conversion
   or arithmetic on counts. Keep timestamps as UTC strings so microseconds are
   not silently lost. Project allowlisted fields instead of propagating unknown
   server payloads into the UI. Reuse the current client/Auth error behavior.
2. Extend the configuration UI with an explicit create action. Enable only for
   the same four fields/revision/Auth identity whose validation is SUPPORTED.
   Editing snapshot/algorithm/mode/value or changing session/role invalidates
   that authorization-to-submit view. Backend revalidates at creation; the UI
   does not compute thresholds/weights or treat preview as execution permission.
   Use a synchronous in-flight ref plus visible loading/disabled controls so
   double clicks/Enter cannot create twice. Creation never occurs on mount,
   route query, validation success, focus or network reconnect. The button is
   an explicit user action; there is no automatic POST retry/replay.
3. Add the guarded detail route and a hook owned by runId + current Auth/session
   identity. A confirmed 202 navigates to a UI route constructed from validated
   runId, never an arbitrary Location URL. A fresh GET is authoritative. Ignore
   stale responses after run/account/role changes, cancel pending fetches and
   clean timers on unmount/logout. Route state may carry the accepted snapshot
   for a loading message, but it never replaces GET or triggers a POST. Do not
   store tokens, results, SUPPORTED markers or pending requests in localStorage.
4. Implement a serial, bounded GET polling loop. Proposed defaults are listed
   below. Poll only QUEUED/RUNNING; terminal and LEGACY_UNVERIFIED stop. Delay
   starts after the preceding GET finishes, so requests cannot overlap. Pause
   and abort reads when the document is hidden; visibility restoration may
   resume GET within the same remaining budget, never POST. Exhausted budget,
   GET timeout/network/malformed response/503 pauses automatic polling and offers
   explicit "Đọc lại" / "Tiếp tục theo dõi". A last confirmed snapshot remains
   visible with a stale/paused label and last-read time; errors do not invent a
   FAILED or SUCCEEDED state. 401/403 defer to shared Auth and clear protected
   stale data; 404 stops polling. Resume begins a new bounded GET session.
5. Render creation/detail states and safe Vietnamese messages. Show runId and
   copyable link, snapshot/algorithm/config, persisted status and UTC timestamps,
   last refresh state, plus a basic completion pattern-count summary. QUEUED and
   RUNNING have indeterminate indicators, not invented percentage/ETA. FAILED
   displays a Vietnamese mapping of the allowlisted code and requestId when
   present; unknown codes/diagnostics use a generic safe message. Distinguish
   legitimate zero patterns from missing counts. LEGACY_UNVERIFIED is explicitly
   historical/unverified and offers no rerun shortcut. SUCCEEDED says mining
   finished; do not imply durable patterns, recommendations or downloads exist.
   Detailed metric tables/unit presentation/comparison remain WDM-05.9.
6. Add API decoder/client, React hook/component/navigation and accessibility
   tests, then focused/full frontend tests, TypeScript and production build.
   Use fake timers/deferred responses for polling, timeouts and race tests.
   Browser smoke uses the actual UI with controlled HTTP fixtures and reports
   that scope explicitly. The complete two-engine browser/API/DB differential
   gate remains WDM-05.10; no owner data is seeded or execution flag changed
   merely to demonstrate the UI. Update maintained docs/checklist with evidence.

### Timing and uncertainty policy

| Setting | Proposal | Effect |
|---|---|---|
| Poll delay | 2000 ms after completed GET | Serial status reads while active |
| Automatic observation session | 120000 ms | Bounded UI watching; manual resume available |
| GET timeout | 15000 ms | Pause observation on unconfirmed read |
| POST timeout | 45000 ms | Bound client wait; abort does not cancel server work |

These UI limits are unrelated to the 60-second ownership lease and do not
change mining runtime, heartbeat or recovery. Stop means stop observing, not
stop/cancel the run. Timers are constants tested in the feature slice, not new
production backend settings.

Creation outcomes need separate states: ready, submitting, accepted and
creation-uncertain, plus known validation/auth errors. A timeout, lost network
response, aborted submitted request, malformed 202 or unexpected response can
leave a durable run without a confirmed client runId. A 503 also does not prove
no row exists because admission can fail after commit. Do not reuse conversion's
lost-POST recovery logic: conversion knows snapshotId, whereas run creation
allocates its runId on the server and exposes no requestId lookup/idempotency API.

When runId is confirmed, offer GET-only recovery at the known detail URL. When
it is unknown, display uncertainty and requestId (if available), preserve the
submitted configuration for operator review, and do not claim automatic recovery.
Creating again must be a distinct explicit "Tạo yêu cầu mới" action explaining
that the earlier request may already have created a run; revalidate its current
configuration before another POST. No hidden retry, GET list scan, history guess
or fabricated failure status. A UI AbortController cannot undo a server commit.

400/422 get field/configuration guidance, with the existing zero-effective-
threshold explanation retained; 401/403 use Auth handling; 404 names the resource
for the current operation; 409 advises checking current binding/state; 503 uses
a generic unavailable/admission message. The UI cannot distinguish disabled
execution, capacity exhaustion and transient dependencies from the shared 503
alone. Retained outcomes still occupy capacity until future durable handoff;
do not add an automatic acknowledge consumer or a user "free slot" action.

### Expected file scope

API: new mining-runs.ts / mining-run-response.ts (exact names confirmed before
implementation), index.ts exports and matching API tests. UI: MiningConfigForm,
MiningRunsPage and the smallest hook/callback integration needed for the explicit
create action; new create/status hooks, MiningRunDetailPage and small status/
error presentation components as needed. Routing: routes.tsx and related
navigation tests. Reuse ManagerPath.MINING_RUNS for the detail suffix.
Tests stay beside the feature; extend existing browser-smoke tooling only if
needed without dependency changes. Documentation: frontend README, request
contract, docs/architecture map where behavior changes, and local checklist.

Preserve existing snapshot-prefill/preview/validation behavior and Auth boundaries.
No backend/API/schema/migration/core, ranking/top-K, pattern list/download,
cancel/retry endpoint, persistent run history or dependency upgrade is planned.
Source file counts are determined from the concrete implementation, not invented
in this plan. No browser/API tests run during this planning-only turn.

### Acceptance criteria (all NOT VERIFIED until implementation)

| ID | Required evidence |
|---|---|
| 05.8-AC01 | Existing preview/validation/prefill still works; create requires current SUPPORTED binding and exactly four POST fields |
| 05.8-AC02 | One explicit create despite double click/Enter; no mount/query/focus/reconnect/retry POST; strict 202 decoder |
| 05.8-AC03 | Confirmed create opens guarded detail URL; direct link/refresh/open-by-ID performs GET only |
| 05.8-AC04 | Five statuses, UTC/null/zero/large-count fidelity, safe messages, empty-pattern success and no false pattern-durability claims |
| 05.8-AC05 | Fake timers prove serial 2s polling, budgets/timeouts, terminal stop, hidden-page pause and explicit GET resume |
| 05.8-AC06 | Unmount/run/session/role changes abort and suppress stale results; protected data cleared on Auth loss |
| 05.8-AC07 | Lost/malformed POST/503 preserves uncertainty, never replays, never guesses runId or definitive failure |
| 05.8-AC08 | GET errors preserve last confirmed snapshot with stale indication; 404/401/403 stop or delegate to Auth correctly |
| 05.8-AC09 | Keyboard labels/live status/accessibility and browser UI smoke with controlled HTTP fixtures PASS, clearly scoped as mocked HTTP |
| 05.8-AC10 | Focused/full frontend tests, TypeScript/build and scope checks PASS; actual two-engine browser/API/DB gate remains 05.10 |

Engineering inference: UI lifecycle, budgets and safe display implement the
accepted backend contract. Mining thresholds/weights/temporal semantics and
native measurements remain authoritative in backend/core; no performance or
research claim is introduced. Recommended next after 05.8 implementation and
acceptance: WDM-05.9 — Metrics UI. Do not start it automatically.

## WDM-05.8 — Implementation and verification (2026-11-07)

Owner approved the detailed file-scope plan and authorized implementation.
Final status: **TECHNICAL PASS / OWNER ACCEPTED (2026-11-07)**. Implementation
and verification are complete. Owner requested the WDM-05.9 plan below; this
acceptance/planning turn does not rerun tests or start implementation.

### Final behavior and boundaries

The existing configuration form retains snapshot prefill, preview and validation.
The explicit create button requires the current supported configuration and
Admin identity; edits invalidate old validation. A synchronous consumed/in-flight
guard prevents double click/Enter replay, and the form locks while submitting.
POST serializes exactly snapshotId/algorithm/minsupMode/minsupValue. The strict
202 decoder verifies QUEUED creation identity/time before navigating to the
same-origin guarded UI detail path; it never follows an arbitrary Location URL.

Run execution API/types/decoders are separate from preview types. GET verifies
requested run identity, both algorithms, all five statuses, UTC timestamps,
nullable config/count/metrics, known units/scopes and failure shape. BIGINT
decimal strings retain the full unsigned range without Number conversion.
Unexpected extra payloads/private fields are projected away, and unknown/raw
failure diagnostics are replaced with a safe Vietnamese message. UTC strings
retain up to six fractional digits; invalid calendar dates/whitespace are rejected.
LEGACY_V1 mode is accepted for historical V2-migrated rows after inspecting the
actual migration; no mining formula is implemented in TypeScript.

Creation hook states are ready/submitting/accepted/rejected/uncertain. A response
timeout, abort after sending, malformed 202, network loss, 409 or 503 cannot prove
no commit occurred. Submitted configuration/requestId remain visible and no POST
is replayed automatically. An explicit new-intent action warns of possible
duplicate creation and invalidates validation before another submit. The hook's
request-controller identity plus Auth binding prevents aborted old attempts
overwriting a newer request even after an identity ABA change. Single-flight is
per mounted UI intent, not server idempotency or exactly-once across browsers.

The detail route `/admin/mining-runs/:runId` and open-by-ID action only GET.
Refresh/direct navigation never creates work. No server list/history endpoint
is assumed and no pending marker/token/result is persisted to localStorage.
Status is the last confirmed backend response, not an inferred execution state.
SUCCEEDED includes legitimate zero patterns and explicitly does not assert
durable result/pattern browsing. Metrics detail is left to WDM-05.9.

Polling uses serial completion-triggered 2s timers, a 120s observation session,
15s bounded GET and 45s bounded POST. Abort settles the client wait even when
a transport ignores AbortSignal, while late completion cannot mutate state.
These limits do not cancel server work or modify lease/heartbeat/recovery.
Hidden pages abort reads and pause scheduling; visible pages may resume within
remaining budget, otherwise expose manual resume, including when the initial
read was never confirmed. Terminal/legacy states stop automatic polling.
Run/session/role changes and unmount abort/clear timers and suppress stale results.
GET failure pauses observation, preserving a labeled last-confirmed snapshot;
401/403 clear protected snapshots and defer to shared Auth. Manual resume is
GET-only and starts a fresh bounded observation session. Backend/client headers,
Auth callbacks and API-root/proxy configuration remain unchanged.

### Files and scope

New production frontend files:

- api/admin/data-mining/mining-runs.ts and mining-run-response.ts;
- pages/admin/data-mining/useMiningRunCreation.ts, useMiningRunStatus.ts,
  mining-run-lifecycle.ts, MiningRunCreationFeedback.tsx,
  MiningRunStatusPanel.tsx and MiningRunDetailPage.tsx.

New tests/fixtures: matching API decoder/client tests, create/status hook tests,
feedback/status/detail component tests and mining-run-test-data.ts. New retained
browser tooling: frontend/test/e2e/execution-ui-harness.tsx and execution-ui-smoke.mjs.
The small lifecycle helper centralizes bounded cancellation/timeouts/error text;
the synthetic protocol fixture centralizes test data. These are justified small
scope expansions supporting the approved hooks/tests, not temporary helpers.

Existing frontend updates: API index/index tests, MiningConfigForm/form tests,
MiningRunsPage/page tests, routes/navigation tests. Existing documentation:
frontend README, E2E README, architecture, docs map, this contract and checklist.
No backend/migration/core/.env/dependency edits or owner environment operations.
Old dirty/untracked WDM work remains preserved. No existing files are deleted.
Build output, screenshot/RESULT and check logs are retained under ignored
.agent-workspace/phase-wdm; they are review evidence, not production deployment.

### Verification checkpoints

- WDM-05.8-types-initial.log and WDM-05.8-types.log: tsc --noEmit PASS.
- WDM-05.8-focused-initial.log: 59 PASS/1 failed (four suites). The new API
  fixture used an invalid envelope success message; corrected to the accepted
  standard message. No production transport contract was weakened.
- WDM-05.8-hooks.log: 41/41 PASS across API/decoder/create/polling hook cases.
- WDM-05.8-focused.log: 121/121 PASS (11 suites).
- WDM-05.8-regression.log: 449 PASS, two live tests skipped by existing opt-in
  configuration (31 suites passed, two skipped). No real live endpoint claim.
- Initial browser log failed on an exact label selector excluding Mantine's
  required marker; the fixture selector was corrected. browser-final and
  browser-verified logs PASS six groups; the latter also checks 390px overflow.
- WDM-05.8-build.log initially failed on form indentation. Scoped eslint --fix
  and focused test lint corrections preserved business behavior; intermediate
  lint logs retain failures, including an inline helper's Windows encoding
  error corrected using explicit UTF-8. lint-complete.log PASS, zero warnings.
- WDM-05.8-focused-final.log: 121/121 PASS after style corrections. A final
  request-controller/identity ABA regression case was subsequently added.
- WDM-05.8-build-final.log PASS (inherited warnings outside task); final build/
  regression for the last controller guard are recorded below when complete.
- WDM-05.8-build-complete.log PASS on the final production code (inherited
  warnings only). WDM-05.8-aba.log PASS 8/8 including the new identity ABA case.
- WDM-05.8-regression-final.log stopped with React/JSDOM document.createEvent
  on a null document during flushPassiveEffects teardown while build was also
  running. No root-cause claim is made from that trace. A separate serial full
  run uses unchanged assertions and --testTimeout=30000.
- WDM-05.8-regression-verified.log: **450 PASS, 2 skipped**, 31 suites PASS,
  two live suites skipped, native exit 0. The skipped tests are opt-in live
  Auth/health checks, not real endpoints verified by this UI task.
- WDM-05.8-types-complete.log: final tsc --noEmit PASS/native exit 0.
- WDM-05.8-lint-last.log: final scoped ESLint PASS/native exit 0, no warnings.
- WDM-05.8-browser-complete.log: final six browser groups plus mobile overflow
  check PASS/native exit 0 on the same source. Artifacts remain reviewable.
- Final production build (WDM-05.8-build-complete.log) PASS/native exit 0 to
  task-owned WDM-05.8-build, preserving pre-existing build artifacts. Remaining
  warnings are inherited outside the task (including old hook deps/Browserslist).

Browser evidence uses actual routes/components/client/guard with fixture identity
in the real in-memory store and intercepted HTTP. No real backend/shared-Auth/
MySQL verification, owner accounts or production test hooks. Six groups cover
SUPPORTED create/double click, QUEUED/RUNNING/SUCCEEDED polling, refresh GET only,
GET error retaining snapshot, lost POST without replay and CUSTOMER route guard.
Screenshots and RESULT.json live under WDM-05.8-browser. Browser/server are closed
after the run; desktop/mobile screenshots were visually/sizing checked.

### Acceptance evidence

| ID | Evidence |
|---|---|
| 05.8-AC01 | Existing preview/validation/prefill tests and supported/edit-bound create form tests |
| 05.8-AC02 | Synchronous double-click/accepted-consumption tests, exactly-four-field API tests, strict 202 decoder and browser create |
| 05.8-AC03 | Guarded detail route, open-ID navigation, direct route/refresh browser assertions, no create call |
| 05.8-AC04 | Five statuses, microsecond UTC/null/zero/full unsigned counts, failure allowlist and empty-pattern display tests |
| 05.8-AC05 | Fake timers prove serial completion-based delay, timeout, observation budget/manual GET resume and hidden-page handling |
| 05.8-AC06 | Auth/session/route/unmount/controller identity tests suppress stale results and clear protected data |
| 05.8-AC07 | Lost/malformed POST/network/503 uncertainty tests, no automatic replay, explicit reset with revalidation |
| 05.8-AC08 | GET 503 preserves stale snapshot, 401/403 clear/delegate, 404 stops automatic polling |
| 05.8-AC09 | Accessible labeled form/buttons, live status/alerts, desktop/mobile mocked-HTTP browser smoke |
| 05.8-AC10 | Final full regression 450 PASS/2 live skipped; TypeScript, scoped lint, production build, diff and preservation checks PASS |

AC01-10 PASS for the approved UI/mocked-HTTP scope. No production deployment or
real two-engine browser/API/DB acceptance is inferred from these checks.

Final inventory: **18 new maintained source/test/tooling files, 14 existing files
updated** (eight frontend files, six documentation/checklist files). The two
small shared fixture/lifecycle modules and two browser tooling files account
for the concrete scope expansion from the initial filename proposal. A 63-file
scoped SHA-256 baseline confirms 13 expected modifications and 50 unchanged
files, including .env/package/package-lock and previous API/configuration code;
the additionally updated existing E2E README is separately accounted for.
No source files were deleted. Check logs, isolated build output, browser RESULT
and desktop/mobile screenshots are intentionally retained as evidence; no
disposable cleanup is pending. No Git stage/commit/push or owner environment
operation was performed.

Accepted-contract confirmed: backend thresholds/native observations are displayed
without recomputation, both engine IDs and supported request modes are preserved.
Engineering inference: UI lifecycle, budgets, safe presentation and test fixtures
follow the approved plan. R6 and exact-real questions remain unchanged; no speed,
memory improvement or research acceptance claim. Owner deployment and real
browser/API/DB execution remain NOT VERIFIED. Patterns remain in bounded RAM
until future handoff; UI cannot free backend capacity or recover an unknown
server-generated runId after a lost creation response. Recommended next:
WDM-05.9 — Metrics UI, not started automatically.

## WDM-05.9 — Proposed Metrics UI plan (2026-11-07)

Owner accepted WDM-05.8 and requested the next detailed plan. **PLANNING ONLY /
IMPLEMENTATION NOT STARTED at that planning checkpoint**. The implementation
below supersedes it. That turn modified only this contract and the
local checklist; no source, tests, dependency, schema or owner environment change.

### Objective and existing boundary

Add a read-only metrics section to /admin/mining-runs/:runId, consuming exactly
the same decoded MiningRunStatus report already owned by useMiningRunStatus.
The 05.8 decoder already retains all metrics, candidateCount and measurement
descriptors; no endpoint, new request, store or polling loop is required. The
backend WDM-05.6 measurement contract and native counters remain authoritative.

### Display contract

| API field | Label / interpretation for known WDM-05.6 scope | Unit |
|---|---|---|
| patternCount | Stored complete pattern count, including legitimate zero | count |
| candidateCount | PreTree candidateCount / BitPrune generatedExtensions, preserving native F1 exclusion | count |
| metrics.datasetIoMs | Three verified raw/sequence/mapping reads, including integrity checks; excludes DB/parsing | ms |
| metrics.preprocessingMs | Native core preprocessing | ms |
| metrics.dfsMiningMs | Native core DFS | ms |
| metrics.completeAlgorithmMs | Complete core algorithm including finalization | ms |
| metrics.outputIoMs | Output I/O; null with current contract means no output stage exists | ms |
| metrics.totalExecutionMs | Worker refresh through engine return after claim; excludes pre-admission, queue, claim and terminal DB finalization | ms |
| metrics.peakSampledJvmHeapBytes | JVM-wide used heap sampled during the algorithm | bytes |

Keep ms and bytes as primary exact units. Values remain canonical decimal strings
through presentation; any digit grouping uses string operations only, preserving
all digits up to 18446744073709551615. No Number conversion, implicit parseFloat,
BigInt literal, dependency upgrade or mandatory MB/MiB conversion. Test formatted
values against exact original digits; null is "Chưa có số đo", while "0" is a
real published zero. DB millis are independently floored from nanos: 0 ms does
not establish zero runtime. Do not invent decimals or restore discarded nanos.

Use three groups: counts, six timing rows and sampled heap. Show units in labels
or table headers, accessible scope explanations and overflow-safe responsive
layout. Counters are consolidated into the new panel; the status panel retains
the legitimate empty-pattern success message but removes its duplicate count row.
No timing chart/stacked sum: phases are nested in complete-algorithm/worker scopes.
Do not derive total from phases, subtract createdAt/finishedAt, calculate queue
time/percentages, compare candidate counts as equivalent work, or infer speedup.

### Availability and provenance states

Scope explanations and algorithm-specific candidate semantics require the known
WDM-05.6 measurement contract/validated descriptors. With null/unknown contract,
retain stored numeric values/units but label scope as unverified; do not assign
the current worker/heap interpretation from status, algorithm or nonnull values.
For known WDM-05.6, null output I/O is explained as no output stage. Historical
null output is merely missing measurement, not proof export was disabled.

QUEUED/RUNNING with null values say complete measurements have not been published.
FAILED with null values says no complete metric set is available, not zero work.
SUCCEEDED without measured values still means completed mining with missing
stored measurements; do not fabricate zero or downgrade status. Legacy rows
show the unverified-scope notice even if values exist. A transient GET error
retains the same last-confirmed metrics under the existing stale/paused banner.
Auth/run changes clear both status and metrics through the existing hook.

The heap label must be "JVM heap lấy mẫu trong thuật toán (bytes)" with a concise
explanation: shared JVM used heap, affected by other activity, not RSS/physical
RAM, run-private allocation or a peak over the entire worker. SUCCEEDED and
counts remain metadata, not evidence that patterns were durably persisted.

### Concrete file scope

| File (relative to repository) | Proposed action/responsibility |
|---|---|
| frontend/src/pages/admin/data-mining/MiningRunMetricsPanel.tsx | NEW: read-only count/timing/heap groups, units, missing-value and scope notices |
| frontend/src/pages/admin/data-mining/MiningRunMetricsPanel.test.tsx | NEW: two algorithms, all statuses, measured/legacy/null/zero/large values, safe scope text/accessibility |
| frontend/src/pages/admin/data-mining/mining-run-metrics.ts | NEW: pure row metadata, scope selection and exact decimal-string formatting; no research calculations |
| frontend/src/pages/admin/data-mining/mining-run-metrics.test.ts | NEW: exact precision/grouping/zero/null/unit/scope projection tests |
| frontend/src/pages/admin/data-mining/MiningRunDetailPage.tsx | UPDATE: render panel from operation.report alongside status; no additional fetch |
| frontend/src/pages/admin/data-mining/MiningRunDetailPage.test.tsx | UPDATE: coherent status+metrics, stale GET snapshot and cleared data on route/Auth change |
| frontend/src/pages/admin/data-mining/MiningRunStatusPanel.tsx | UPDATE: remove duplicate pattern-count row, preserve success/zero/failed status messages |
| frontend/src/pages/admin/data-mining/MiningRunStatusPanel.test.tsx | UPDATE: preserve status behavior; precision/count assertions move to metrics panel |
| frontend/src/api/admin/data-mining/mining-run-test-data.ts | UPDATE: explicit measured/legacy/partial protocol fixtures with correct provenance |
| frontend/src/api/admin/data-mining/mining-run-response.test.ts | UPDATE: exact measured payload/provenance regression; production decoder unchanged unless a concrete contract defect is discovered |
| frontend/test/e2e/execution-ui-smoke.mjs | UPDATE: representative measured/legacy metrics, stale read retention, desktop/mobile exact-value display assertions/screenshots |
| frontend/test/e2e/README.md | UPDATE: scope and reproduction of added browser UI groups |
| frontend/README.md | UPDATE: units/scope/null/legacy behavior and usage |
| ARCHITECTURE.md | UPDATE: shared report ownership and presentation boundary |
| docs/README.md | UPDATE: documentation map/current UI status |
| docs/wdm/mining-request-contract.md | UPDATE: implementation/acceptance evidence after execution |
| .agent-workspace/phase-wdm/WDM_CHECKLIST.md | UPDATE: technical/owner acceptance states |

Expected four new maintained source/test files. Actual modified counts are audited
before implementation. Existing API client, production decoder/types, index,
create/status hooks, router, Auth, backend/V1-V3/core/.env/dependencies stay
unchanged by default. The existing browser harness should pick up the new panel
through actual routes without edits. Any unexpected schema/API need triggers a
concrete revised scope before work expands. No benchmark, run history/list,
pattern download or comparison dashboard is added.

### Execution sequence

1. Freeze labels/units/known-vs-unknown scope rules and explicit protocol fixtures.
2. Implement/test pure projection/formatting, then the metrics panel.
3. Integrate the single report in detail, consolidate counts and preserve 05.8
   create/polling/error behavior. Add component/API regression evidence.
4. Extend actual-UI/mocked-HTTP browser smoke for measured and historical values,
   zero/missing fields, stale GET and 390px layout. Do not seed real metrics or
   claim these protocol fixtures are actual engine results.
5. Focused tests -> scoped ESLint/TypeScript -> full frontend regression ->
   production build in an isolated task-owned output path. Run heavy test/build
   checks serially because 05.8 recorded one concurrent React/JSDOM teardown
   failure. Keep inherited warnings/opt-in live skips explicit; no unrelated fixes.
6. Final scope/preservation/diff review and required task report; retain evidence,
   do not start the next gate automatically.

### Acceptance criteria (all NOT VERIFIED until implementation)

| ID | Required evidence |
|---|---|
| 05.9-AC01 | All nine observations (two counts, six timings, one heap) map from existing report without new requests/recomputation |
| 05.9-AC02 | Exact decimal-string precision including >2^53/unsigned maximum; null distinct from real zero; ms/bytes explicit |
| 05.9-AC03 | Correct six-scope explanations; nested scopes never summed; total never derived from lifecycle timestamps |
| 05.9-AC04 | Sampled algorithm JVM-wide heap described accurately, with no private-RAM/RSS/worker-total claim |
| 05.9-AC05 | Native candidate labels/F1 exclusion only for verified current scope; no cross-engine equivalence/performance conclusion |
| 05.9-AC06 | Active/failed/succeeded/legacy missing measurements and unknown provenance displayed faithfully |
| 05.9-AC07 | Known absent output vs historical missing output differ correctly; no fake zero/decimals/durable-result claim |
| 05.9-AC08 | Same snapshot as status, stale GET keeps old measurements with existing banner, Auth/run clearing preserved, no duplicate polling |
| 05.9-AC09 | Accessible labels/scope help and desktop/mobile mocked-HTTP browser evidence for measured/legacy/zero/large values |
| 05.9-AC10 | Focused/full frontend, scoped lint, TypeScript/build and preservation checks PASS; real engine/browser/API/DB evidence remains 05.10 |

Accepted-contract confirmed: metric/candidate definitions are those already
accepted in 05.1/05.2/05.6/05.7. Engineering inference: layout, string formatting
and availability messages expose those observations without changing them.
R6 and exact-real comparator questions remain unchanged. Recommended next after
implementation and owner acceptance: WDM-05.10 — Engine E2E Gate, comparing full
pattern/support maps under the same supported configuration. It is not started
by this planning turn.

## WDM-05.9 — Implementation and verification (2026-11-07)

Owner approved the plan and authorized implementation. Final status:
**TECHNICAL PASS / OWNER ACCEPTED (2026-11-07)**. Implementation and verification
are complete. Owner requested the 05.10 plan below; no checks are rerun and no
gate implementation starts in this acceptance/planning turn.

### Implemented presentation contract

MiningRunMetricsPanel receives the same decoded operation.report as the status
panel. It owns no request, state store, timers, Auth behavior or measurement.
A pure mining-run-metrics helper supplies two count rows, six timing rows and
one sampled-heap row. The duplicate structured pattern-count row is removed
from status, while its legitimate zero-pattern success message is preserved.

All numeric observations remain decimal strings. The formatter inserts Vietnamese
digit-group dots using string replacement only; HTML data values retain exact
canonical digits. Full unsigned 18446744073709551615 and >2^53 values are tested.
Timing units stay ms, heap units bytes; null is "Chưa có số đo", never a fake
zero. Zero remains a published number, and the current contract explains that
floor conversion from nanos can produce 0 ms for a sub-millisecond observation.
No Number/parseFloat conversion, extra rounding, derived timing or benchmark
arithmetic is introduced.

Current explanations require contractVersion WDM-05.6 plus all accepted unit/
scope descriptors. Dataset I/O includes the three verified reads/integrity
checks, excludes DB/parsing; preprocessing/DFS/complete are native core scopes;
worker total excludes pre-admission/queue/claim/terminal DB finalization. Current
timings are explicitly nested and are not summed. Current candidate labels are
PreTree candidates or BitPrune generated extensions with native F1 exclusion;
the UI makes no cross-engine work/performance equivalence claim.

Known null output explains the absent output stage; historical null output
is merely missing. Legacy/unknown scope preserves raw values with a notice,
uses generic stored candidate/total/heap labels and does not infer algorithm-
heap/worker/native counter semantics from populated values or algorithm IDs.
Known heap is labeled algorithm sampled JVM-wide used heap with a shared-JVM
explanation, not RSS/physical RAM/private run allocation or whole-worker peak.

Active, failed, succeeded and legacy all-null states get distinct availability
messages without changing the run status. Stale GET errors keep the same status
and metric snapshot under the existing paused banner; Auth loss clears both.
Metrics do not imply durable patterns or recommendations. Region/group names,
heading hierarchy, definition lists and always-visible scope text support
accessible navigation, and the responsive grid wraps long exact values.

### Source and file scope

Four new files under frontend/src/pages/admin/data-mining:
MiningRunMetricsPanel.tsx, MiningRunMetricsPanel.test.tsx,
mining-run-metrics.ts and mining-run-metrics.test.ts.

Existing source/tests updated: MiningRunDetailPage/detail tests,
MiningRunStatusPanel/status tests, API mining-run-test-data.ts and
mining-run-response.test.ts. Production API types/decoder, client, create/status
hooks, routes and Auth are unchanged. Existing browser smoke adds --metrics mode
while preserving its original six groups/default path; metrics mode writes
separate WDM-05.9 evidence and adds four protocol-fixture groups. The harness
itself is unchanged. Six docs/checklist files describe behavior/evidence.

No backend, V1/V2/V3, mining-core, .env, package/dependency or owner environment
change is authorized or performed. No Git stage/commit/push or source deletion.
Build and browser artifacts/logs are retained under ignored .agent-workspace.
Synthetic protocol/precision fixtures are not real mining or benchmark evidence.

### Verification checkpoints

- WDM-05.9-focused.log: 61/61 PASS (five suites), zero skips, native exit 0.
- WDM-05.9-browser.log: ten actual-UI/fixture-identity/mocked-HTTP groups PASS,
  native exit 0, including prior execution flow, both measured algorithm IDs,
  legacy unknown scopes, missing vs measured zero and stale metrics. Desktop,
  mobile 390px and historical screenshots plus RESULT.json retained separately.
- WDM-05.9-lint.log: eleven test lint errors from direct DOM/container queries.
  Added accessible section/group names and replaced queries with roles/within;
  no lint rules or business assertions were disabled. WDM-05.9-lint-final.log
  PASS/native exit 0, zero warnings.
- WDM-05.9-types.log: tsc --noEmit PASS/native exit 0.
- WDM-05.9-regression.log: **481 PASS, 2 opt-in live skipped**, 33 suites PASS
  and two skipped, native exit 0. Command: `CI=true npm test -- --watchAll=false
  --runInBand --testTimeout=30000`. Live Auth/health tests are not claimed verified.
- WDM-05.9-build.log: isolated production build PASS/native exit 0 to
  .agent-workspace/phase-wdm/WDM-05.9-build. Warnings are inherited outside task,
  including old hook dependencies/Browserslist. Old build artifacts preserved.
- WDM-05.9-browser-final.log: final ten browser groups PASS/native exit 0,
  including 390px no-horizontal-overflow check, on the final accessible source.
  Heavy test/build checks ran serially.

The browser runs actual components/routes/shared client/guard with synthetic
identity and intercepted HTTP, not a real backend/Auth/MySQL gate. Each metrics
report is read once, prior GET-error behavior keeps the exact old values, and
desktop/mobile checks include grouped/raw full-precision values. Browser/server
are closed after execution. WDM-05.8 evidence/artifacts are preserved by metrics
mode's separate output path. Owner environment and real engine/browser/API/DB
execution remain NOT VERIFIED and are reserved for WDM-05.10.

### Acceptance evidence

| ID | Evidence |
|---|---|
| 05.9-AC01 | Immutable nine-row projection and same-report detail test; no new request/polling dependencies |
| 05.9-AC02 | Zero/null and digit-roundtrip cases through full unsigned max; DOM raw values and browser assertions |
| 05.9-AC03 | Native six-scope row metadata, explicit nesting explanation; no sums or timestamp arithmetic |
| 05.9-AC04 | Current algorithm JVM-wide sampled-heap explanation; generic legacy label; no private-RAM/worker peak inference |
| 05.9-AC05 | Both native candidate labels/F1 rule only under confirmed descriptors; historical generic label tests |
| 05.9-AC06 | Five-status missing-value tests and measured/legacy preservation in component/API/browser fixtures |
| 05.9-AC07 | Known absent output vs historical null vs measured zero tests; no fabricated nanos or result durability claim |
| 05.9-AC08 | One report/GET for both panels, failed GET retains values, identity loss clears both; hook source preserved |
| 05.9-AC09 | Named region/groups/headings/scope text; browser measured/legacy/390px sizing/screenshots |
| 05.9-AC10 | Focused 61/full frontend 481 PASS (2 live skipped); lint/type/build, preservation and whitespace PASS |

AC01-10 PASS for the approved UI/controlled-HTTP scope. Real engine/browser/API/
DB acceptance and owner rollout remain NOT VERIFIED.

Final accounting: four new maintained files and thirteen existing updates,
matching the proposed scope. A scoped SHA-256 baseline of 82 existing files
confirms thirteen expected changes and 69 unchanged files, including previous
API decoder/client/polling and .env/package/lock. Aggregate SHA-256 inventories
of the entire Git-visible backend and mining-core are unchanged, covering all
migrations and prior scientific/application source. Diff and new-source
whitespace checks PASS. No files were deleted and no disposable helper was
created; logs, isolated build output and browser screenshots/RESULT are retained
as evidence. No Git stage/commit/push or owner environment operation.

Accepted-contract confirmed: the existing API/native metric scopes/counters are
displayed unchanged. Engineering inference: grouping, layout, accessibility and
availability text follow the approved UI plan. R6 and exact-real questions stay
unresolved; no performance, memory improvement or renewed research acceptance
claim is made. Recommended next after acceptance: WDM-05.10 — Engine E2E Gate;
do not start it automatically.

## WDM-05.10 — Proposed Engine E2E Gate plan (2026-11-07)

Historical planning snapshot: owner accepted WDM-05.9 and initially requested
planning/checklist updates only. The subsequent "Thực hiện theo kế hoạch trên"
authorizes WDM-05.10 implementation. Execution evidence and deviations are
recorded below; the plan is retained to distinguish proposal from actual checks.

### Gate objective and evidence gap

Prove that real browser-created runs execute both unchanged algorithms under the
same supported configuration and preserve the entire canonical pattern -> exact
support map plus pattern count. Existing mocked-HTTP UI smoke cannot establish
that. Existing enabled HTTP integration already compares retained complete maps,
but it does not link those maps to actual browser interactions in the built UI.

Current GET exposes status/metadata/metrics, not patterns. Full results live in
ManagedRunExecutionAdmission's bounded JVM registry. A separate process cannot
read them through the public API. The gate therefore adds a test-only same-JVM
observer, never a production result endpoint or a fabricated pattern query.
Artifacts captured by this observer are test evidence, not WDM-06 persistence.

### Real isolated execution path

Use an actual CRA production build, shared Auth UI/session, HTTPS loopback proxy,
unchanged production BackendApplication/managed provider/core engines, disposable
MySQL 8.4, actual Flyway V1 -> V2 -> V3 and Hibernate validate. Execution=true
applies only to this test backend, with bounded parallelism=1/queue=1 and existing
lease/heartbeat defaults. No real .env, owner DB/service/port or named volume is
modified. Random test credentials/keys stay in run-owned temp resources and are
never retained in logs/browser storage snapshots or result manifests.

Reuse the small accepted CSV round-trip fixture and expected product layout:
backend/src/test/resources/wdm/csv-v1/round-trip.csv and round-trip-expected.json.
It contains both same-itemset p1,p2 and later-itemset p1 then p2 semantics. Seed
reference catalog/roles only inside the disposable DB. Acquire/upload, validate,
convert and mine through real flows, preserving the actual downloaded SPMF and
mapping hashes. Do not manually seed patterns or invent transaction ordering.

The old run.mjs infrastructure uses 47 V2 definitions and launches without an
execution observer. Create a dedicated engine runner instead of silently
changing the accepted Auth/dataset modes. Reuse bounded fixture/login helpers
where practical. The new runner must use actual migrations, not describe a
definitions-only bootstrap as Flyway evidence.

### Test-only observation and artifact identity

Add a test launcher/config outside the production component-scan package.
Explicitly register BackendApplication and test-only observer/control beans.
Stage only the required compiled E2E helper classes; do not place the entire
backend test classpath on the application scan path. Runtime application classes
and dependencies come from the exact packaged backend artifact (or verified
byte-identical extracted classes/libs), with recorded artifact hashes. Packaging
must exclude the test launcher/observer from the production JAR.

The Node runner publishes a bounded allowlist of canonical runIds captured from
actual backend POST responses into a run-owned filesystem inbox. The observer
uses the registry's non-destructive outcome/completion APIs in that JVM. For each
approved run it captures the original complete result, lifecycle confirmation,
native raw statistics/metrics, algorithm/resolved configuration and input binding
with runId/attempt correspondence. Confirm this against the DB row/ownership,
snapshot/publication and hashes. Never derive the map by running the engine again.
Do not log acknowledgement tokens, credentials or private paths.

Write full maps canonically using the accepted structural Pattern.serialize()
identity and exact integer supports, sorted for deterministic comparison. Reject
duplicate identities, truncated/incomplete artifacts and mismatched run binding.
Keep I/S distinctions and -1/-2 semantics; flattening itemsets is forbidden.
Hashes/counts supplement the retained full map, not replace it. Persist evidence
atomically before declaring a case complete, with bounded inbox/files/timeouts
appropriate to the tiny fixture. No network observer endpoint is added.

No automatic ack is introduced. Use a fresh isolated backend JVM for each batch
of at most two retained runs. Capture/check both outcomes before closing it;
terminal DB metadata can outlive the JVM, but no restart durability of RAM maps
is claimed. A third run after both slots are retained must demonstrate 503 with
no extra DB row. This avoids changing production handoff/capacity policy merely
to fit the test matrix.

For deterministic queue/tamper cases, a test-only bounded latch may hold an actual
MiningEngine call before delegating to the unchanged real implementation. It may
count calls but cannot replace requests/results/metrics. Release latches on all
paths. This instrumentation is explicit test evidence, not an uninstrumented
performance measurement; a backend failure must not be hidden by fake success.

### Comparison ladder and supported matrix

Each actual downloaded SPMF/mapping and captured numeric request is verified in
Java against the same core artifact used by the application. Preserve configured
versus resolved values and compare raw double bits, including actual JSON wire
normalization of -0. No JavaScript threshold/weight formula or epsilon substitute.

For every successful browser run compare:

1. Original retained browser-run complete map;
2. Direct frozen WSPM_PreTree on that exact parsed input/configuration;
3. Direct WSPM_BitPrune under the same input/configuration;
4. Existing independent BruteForceWeightedPatternOracle on this tiny database.

The independent oracle is currently package-private in core test sources. Add
a test-only verifier/bridge in the same package; do not copy its formulas or
change production APIs/visibility to expose it. Compile with existing test
sources and run against the exact core artifact, checking source/artifact hashes.
The bridge is never packaged in mining-core's production JAR.

Minimum positive matrix: both algorithms at ABSOLUTE 0.1, both at PERCENT 14,
and a pair at a verified positive threshold producing a legitimate empty map
(PERCENT 100 on the accepted tiny fixture, subject to direct/oracle confirmation
before the browser assertion). Pairwise comparisons require identical actual
wire configuration; do not assume different modes/values resolve equivalently.
At the low threshold explicitly verify representative I/S pattern distinctions.
If the existing fixture cannot satisfy an expected property, report it before
changing the fixture/expectation, rather than fabricating evidence.

### Negative and failure controls

- Preserve equal map size but alter one exact support in a copied evidence file:
  the comparator must fail. Also remove/replace a canonical pattern and alter a
  resolved threshold by one ULP in separate copies; all must be rejected.
- Zero/negative-zero/percentage-underflow remain operational rejections, not
  newly defined R6 mining semantics. UI validation blocks create; authenticated
  direct negative POST controls confirm 422/no new row/no engine call.
- Hold the first real engine call, admit a second queued run, tamper only the
  test-owned queued input artifact, release and prove safe FAILED before a second
  engine invocation. The first run still yields its original complete map;
  restore fixture bytes and prove before/after hashes for later cases.
- Drop/delay an actual backend creation response after commit, never invent a
  payload. The UI must enter uncertainty without replaying POST. The runner may
  privately retain the real server runId for observation, but the UI must not
  guess/discover it through unsupported APIs. Verify the actual retained result
  and DB outcome separately, with the original run identity.
- Verify real metrics UI/API/DB against that run's capture: exact counts, scoped
  units/provenance, independent floor conversions and safe diagnostics. Do not
  assert equality of timing/heap between separate executions or infer speedup.
- Polling remains non-destructive: row/capacity stable for terminal reads, no
  public result/ack endpoint, no manually seeded pattern records.

If baseline, BitPrune, retained browser result or independent oracle disagree,
stop the gate; retain the smallest failing input/config and first differing
canonical pattern/support with source/build hashes. Report FAIL/PARTIAL with
unverified criteria. Do not tune thresholds, add epsilon, replace the baseline,
silently weaken the comparison or proceed to performance claims/WDM-06.

### Proposed concrete file scope

| File / directory (relative to repo) | Proposed responsibility |
|---|---|
| frontend/test/e2e/engine-run.mjs | NEW dedicated real gate orchestration, isolated DB/Flyway/backend/HTTPS/build, batch lifecycle, safe cleanup and evidence |
| frontend/test/e2e/engine-cases.mjs | NEW real browser execution/metrics/negative cases; correlate actual responses with observed runIds |
| frontend/test/e2e/java/EngineRunVerifier.java | NEW source launcher comparing observed maps/metadata/DB capture with direct core results and artifact identity; invokes the existing oracle bridge |
| backend/src/test/java/vn/edu/wspm/e2e/EngineE2EApplication.java | NEW test-only launcher explicitly composing production app plus test config |
| backend/src/test/java/vn/edu/wspm/e2e/EngineE2ETestConfiguration.java | NEW bounded observer/control wiring, outside production scan |
| backend/src/test/java/vn/edu/wspm/e2e/EngineRunEvidenceObserver.java | NEW capture of original retained results and exact binding into run-owned evidence |
| backend/src/test/java/vn/edu/wspm/e2e/EngineRunEvidenceObserverTest.java | NEW identity/bounds/atomic evidence/completeness tests |
| backend/src/test/java/vn/edu/wspm/e2e/EngineExecutionGate.java | NEW transparent test-only latch/call counter for deterministic negative cases |
| mining-core/src/test/java/vn/edu/wspm/mining/EngineE2EOracleVerifier.java | NEW same-package bridge to the existing independent brute-force oracle, plus canonical comparison/negative controls |
| mining-core/src/test/java/vn/edu/wspm/mining/EngineE2EOracleVerifierTest.java | NEW support/identity/truncation/threshold corruption rejection tests, including equal-count differences |
| frontend/test/e2e/README.md | UPDATE exact commands, artifact/classpath identity, cases/limits and evidence reading |
| frontend/README.md, ARCHITECTURE.md, docs/README.md | UPDATE real-gate scope/status and reproduction links |
| docs/wdm/mining-request-contract.md, local WDM_CHECKLIST.md | UPDATE technical results, acceptance evidence and limitations |

Exact helper splits are reviewed before implementation. Existing runner/case
helpers may get a minimal explicit reuse seam only if needed; preserve accepted
Auth/dataset/mock-smoke modes. No production frontend/backend/mining-core source,
new endpoint, algorithm/threshold/counter change, dependency upgrade, migration
edit or owner configuration is planned. Existing package scripts need not change:
the new runner can be invoked directly with Node.

### Execution sequence and resource ownership

1. Freeze binding/evidence schema, batch/matrix and comparison contracts; add
   focused observer/comparator negative tests before relying on a PASS.
2. Build actual frontend to a fresh WDM-05.10 output path; package/test-compile
   backend/core without clean. Record Git state/source hashes, JDK/Node/browser/
   MySQL versions and app/core/test-helper artifact identity. Packaging is only
   preparation, not test evidence. Verify helper classes absent from production.
3. Start new owned disposable MySQL with label/ID checks, actual V1-V3 Flyway
   validation and isolated storage/keys/ports. Start the real app with explicit
   test config and built UI proxy, never the mocked smoke harness.
4. Run bounded real browser batches and full-map/oracle comparisons, then negative
   controls/failure cases, recording exact requests safely and per-run evidence.
5. Run relevant core/backend differential/regression and frontend regression/
   type/lint/build checks serially; report actual executed categories/skips.
6. Confirm source/fixture preservation and close only owned browser/process/
   container resources. Retain hashes, canonical full maps, DB/HTTP summaries,
   case manifest, screenshots and failures under an ignored task-owned evidence
   directory. Never retain passwords, JWTs or auth storage dumps. Validate final
   absolute paths/ownership before deleting task-created disposable keys/temp;
   no pre-existing artifact, volume or process cleanup is authorized by default.

### Acceptance criteria (all NOT VERIFIED until implementation)

| ID | Required evidence |
|---|---|
| 05.10-AC01 | Isolated real MySQL/Flyway V1-V3/Hibernate + actual built UI/app/core artifacts with recorded identity |
| 05.10-AC02 | Real shared Auth/Admin browser acquisition/validation/conversion/create/status/metrics path, no fake success or Auth injection |
| 05.10-AC03 | Both algorithms/modes and legitimate-empty positive-threshold matrix with actual request/binding hashes and resolved double bits |
| 05.10-AC04 | Each retained browser-run full canonical pattern/support map and count equals direct PreTree/BitPrune and independent oracle |
| 05.10-AC05 | I/S structure/SPMF semantics preserved; support/pattern/ULP corruption controls rejected despite equal counts |
| 05.10-AC06 | UI/API/DB/capture counts/units/provenance agree; native metrics preserved without cross-run timing equality/performance inference |
| 05.10-AC07 | Operational zero/underflow rejection, bounded capacity/503 and no unintended row/engine invocation |
| 05.10-AC08 | Real queued-artifact tamper and lost backend response prove safe failure/uncertainty/no replay with original result binding retained |
| 05.10-AC09 | Test observer/bridge/control absent from production artifacts/routes; no auto-ack or fabricated durable pattern persistence |
| 05.10-AC10 | Focused/regression/evidence/preservation/owned-resource cleanup PASS, limitations explicit and owner environment untouched |

Accepted-contract confirmed: structural identity/exact support/positive-threshold
scope, native measurements and existing independent oracle. Engineering inference:
same-JVM test evidence channel, batch isolation and hash/negative-control design.
Unresolved: R6 and exact-real comparator equivalence stay open; this is not a
new Phase B/C acceptance or performance benchmark. Recommended next only after
gate PASS and owner acceptance: WDM-06.1 — Persistence Schema Audit, reusing the
existing accepted metadata/lifecycle schema rather than rewriting it.

## WDM-05.10 — Implementation and verification (2026-11-07)

The owner authorized implementation after the planning/checklist-only turn.
The dedicated real gate passed in
`.agent-workspace/phase-wdm/WDM-05.10-KaFJiD/`: `RESULT.json`,
`PREPARATION.json`, `FLYWAY.tsv`, full per-run maps/properties, verifier logs,
negative controls and desktop/mobile screenshots are retained. Owner acceptance
of this implementation remains pending; WDM-06.1 has not started.

### Actual implementation and necessary scope expansion

Implemented the ten proposed new test/tooling files and six documentation/
checklist updates. Backend production code, core production code, migration
files, dependency versions and owner configuration were preserved. Only the four
explicit E2E helper classes enter the test application's extra classpath; both
production JARs exclude observation/oracle helpers. The original result is
observed once without ack or re-mining; direct engines are separate comparators.

The real built application exposed a prior integration defect: `Admin.tsx`
allowed snapshot detail but omitted `/admin/mining-runs/:runId`, causing the
actual shell to render a legacy placeholder after successful creation. Scope
expanded by two files: admit that already-defined route in `Admin.tsx`, and add
an authenticated deep-route regression through the real `App` in `App.test.tsx`.
No new route/API/business feature was invented. Earlier mocked UI smoke mounted
the mining routes without that shell and could not establish this integration.
The browser runner brings hidden tabs forward for assertions, respecting the
existing intentional background polling pause.

### Executed evidence

Environment: Java 21.0.12.1, Node v22.23.4, Microsoft Edge 157.0.4341.42,
MySQL 8.4.13. Production frontend output:
`.agent-workspace/phase-wdm/WDM-05.10-build-20261107-160141`.
Backend JAR SHA-256:
`adb37cfc66535df46d731e76a92b922408ef6dc829d672662c1eaf7c0a890b65`;
core JAR SHA-256 (identical nested/application/direct verifier bytes):
`c72f509eecd93bd40e98cad4c8517196829a11a50361a8b2f120bd3f4351ad7f`.
Git HEAD/source/helper/build hashes are recorded independently in PREPARATION;
the existing dirty tree is not represented as a clean release commit.

Actual Flyway on an empty owned schema applied V1/V2/V3 successfully, with
checksums `-1801304247`, `727165286`, `1697070581`; no baseline-history shortcut
or definitions-only bootstrap was used. Hibernate validate passed each batch.

| Matrix | PreTree patterns / native candidates | BitPrune patterns / native extensions |
|---|---:|---:|
| ABSOLUTE 0.1 | 15 / 59 | 15 / 64 |
| PERCENT 14 | 3 / 7 | 3 / 7 |
| PERCENT 100 | 0 / 0 | 0 / 0 |

These six browser-created results, the original first run in the tamper case,
and the privately observed committed run after lost response all passed full
canonical map/count equality against both direct engines and the existing
independent oracle. Actual engine input structure matched downloaded SPMF;
publication raw/sequence/mapping hashes, run/snapshot/owner/attempt identity and
configured/resolved raw double bits were checked. DB/API/UI preserve native
counts, independent nanos-to-ms floors, null output scope and sampled heap.
Timing/heap were never compared for equality across separate executions.

Four copied corruption controls were rejected: wrong support at equal count,
replacement structural identity at equal count, deleted pattern and one-ULP
resolved threshold. Three full retained pairs each proved 503/no inserted row
on a third request. There is no automatic acknowledgement.

The held first engine call used already-loaded original input. After admitting
the second queued run, only the owned sequence artifact was altered; release
produced the exact first result and second FAILED/INPUT_REVERIFICATION_FAILED
with one engine entry total. The original artifact bytes/hash were restored.
Dropping the actual committed 202 produced uncertain UI, one POST/one inserted
row, no injected private run ID and a valid original retained result. Eight
authenticated zero/signed-zero/JSON-underflow/percentage-underflow controls
returned 422 with no new row or engine call; UI validation disabled creation.

Focused new Java checks: 3 core comparator/oracle tests and 3 backend observer/
delegate tests PASS. Full Maven reactor regression: **1185 core + 1124 backend**,
zero failures/errors/skips, BUILD SUCCESS. Actual App route regression: 5 PASS.
Production build PASS with existing unrelated lint warnings; strict CI build
failed on those warnings and they were deliberately preserved. Frontend full
regression/type/lint final results are recorded with the acceptance closeout.

### Acceptance closeout

| ID | Evidence / result |
|---|---|
| AC01 | PASS — actual build/JAR identity, MySQL/Flyway history and Hibernate startup logs |
| AC02 | PASS — real Auth, CSV file-picker upload/validation/conversion/download, browser create/detail/metrics |
| AC03 | PASS — both algorithms at all three supported matrix configurations, exact numeric metadata |
| AC04 | PASS — all eight original successful results equal full direct/oracle maps and counts |
| AC05 | PASS — actual SPMF structure and four corruption rejection controls |
| AC06 | PASS — original capture -> DB -> API -> UI, seven visible successful run panels; lost-response result privately verified |
| AC07 | PASS — zero/underflow controls and three retained-capacity refusals |
| AC08 | PASS — actual queued tamper and actual lost committed response, no replay |
| AC09 | PASS — helpers absent from production JARs, no observer route/ack/durable pattern claim |
| AC10 | PASS — full reactor/frontend regression, type/lint/build, source preservation and owned cleanup; owner acceptance pending |

Accepted-contract confirmed: complete structural identity/exact support,
positive-effective thresholds and native measurement definitions. Engineering
inference: test evidence transport, bounded latch and batch isolation. Unresolved:
R6, exact-real comparator equivalence, large datasets, other browsers and durable
pattern persistence. This gate establishes no performance improvement.

Initial failed attempts (storage-root working-directory guard; actual Admin
shell route defect) remain in their own evidence directories. The final runner
exited 0, verified source inventory preservation, gracefully closed six owned
backend JVM batches, closed browser/proxy, stopped its labelled disposable
container, and deleted only its newly-created private staging/TLS/storage after
absolute-path checks. No pre-existing file deletion or cleanup is pending.

Final frontend checks: **482 PASS, 2 opt-in live tests skipped** (33 suites
passed, 2 skipped), using CRA Jest `--watchAll=false --runInBand
--testTimeout=30000`; log `FRONTEND-REGRESSION.log` in the final evidence folder.
The first full invocation with default timeout terminated in React/JSDOM
teardown (`document.createEvent` after document disposal); the rerun with the
repository's previously used 30-second timeout completed successfully. No
production change or test removal was used to conceal that failed invocation.
`tsc --noEmit`, ESLint on `src/pages/Admin.tsx src/App.test.tsx`, Node syntax
checks for both new runners and scoped `git diff --check` also PASS.

Task file inventory: ten new test/tooling files listed in the plan; eight
modified files (`Admin.tsx`, `App.test.tsx`, the five tracked documentation
files and the local checklist). No source file was deleted. Earlier dirty
changes from WDM-05.1–05.9 remain outside this task's change inventory. Build
outputs and failed/final evidence are retained under ignored task-owned paths.
Recommended next, after owner acceptance only: **WDM-06.1 — Persistence Schema
Audit**. No WDM-06 implementation, commit or push was performed.

## Mining workspace UI and read APIs (owner-authorized follow-up, 2026-11-07)

Scope: discoverable snapshot/run history, current backend file locations,
instance-local execution capacity, and clearer configuration/results layout.
This follow-up does not implement WDM-06 pattern persistence, release retained
outcomes, change mining semantics, modify V1-V3 or change dependency versions.

### Read contracts

All endpoints below require existing ADMIN authorization, return the shared
success/error envelope and no-store headers. GET accepts no body; unknown or
duplicate query parameters are rejected before querying.

| Endpoint | Query / response |
|---|---|
| GET /api/v1/admin/dataset-snapshots | page (1-based, max 100000), size (default 20, max 100), search (literal substring, max 255), validationStatus, conversionStatus |
| GET /api/v1/admin/mining-runs | same pagination, search (Run ID literal substring), status, algorithm, snapshotId (canonical UUID) |
| GET /api/v1/admin/dataset-snapshots/{id}/storage | no query; current snapshot publication metadata, not an integrity check |
| GET /api/v1/admin/mining-execution/capacity | no query; current JVM registry/configuration only |

List response: items/page/size/total. Ordering is created_at DESC then primary
UUID DESC; counts stay canonical decimal strings, absent measurements stay
null. Total/list are read in one repeatable-read service transaction. This is
page-based browsing, not a frozen multi-request snapshot under concurrent
insertions. Lists read metadata only and never hash files or touch mining maps.
SQL values are bound; search treats SQL wildcard characters literally.

Storage returns sourceName, storage root, file kind/name/reference/absolutePath/
stored SHA-256 for raw.csv, sequences.spmf, mapping.json. Only exact snapshot-
bound raw references and canonical conversion-attempt paths are projected;
legacy/untrusted references are omitted. Paths are backend OS paths for Admin,
not browser paths or arbitrary filesystem read access. Missing physical files
cannot be inferred from this metadata-only endpoint. Existing verified download
and execution boundaries still check availability/integrity.

Storage explicitly declares CURRENT_SNAPSHOT_PUBLICATION and
METADATA_ONLY_NOT_INTEGRITY_VERIFIED. Run detail labels these as the current
source snapshot's files: historical publication identity is NOT VERIFIED by
this API. No run-publication hash/identity is fabricated or backfilled.

Capacity response: enabled, scope CURRENT_BACKEND_INSTANCE, observedAt, snapshot.
Live observedAt values accept ISO UTC Instant precision up to nine fractional
digits; persisted list DATETIME timestamps use up to six. The live timestamp
must not be rejected merely because it is more precise than a DB timestamp.
Disabled execution has snapshot=null, not fabricated free slots. Enabled snapshot
contains capacity/used/available/closing/observedAt and runId/state entries from
one synchronized read. It never exposes ownership or acknowledgement tokens,
input data or result maps. RESERVED/ACTIVATING entries are not presented as
confirmed persisted runs. COMPLETED/FAILED outcomes still occupy slots until
the existing explicit handoff contract releases them. This is not a DB row
quota, cluster capacity, reservation guarantee or a new release mechanism.

### UI behavior

Datasets is a newest-first library with URL-based filters/page, links to detail
and ready-snapshot configuration. Export/upload remain under the expandable
"Tạo dữ liệu mới" section. Mining-runs retains direct query-prefill and existing
creation semantics, adding create/history tabs, capacity (10s refresh while
visible) and a two-column configuration/output layout that stacks on mobile.
List links carry return context; browser navigation restores search/filter/page.

Selected dataset summary and expandable backend file panel explain input
location. Preview statistics use summary cards/details, backend thresholds
remain distinct and unrounded. Run detail groups status/configuration vs metrics,
links to source/history and labels current-file/historical limitations. Exact
metrics strings/nulls and original single status polling remain unchanged.
Auxiliary reads are session/token-bound and aborted on identity/key change or
unmount; failed reads clear their data rather than implying it is current.

### Verification and completion

Focused backend verification: 20 PASS, including actual MySQL 8.4/Flyway V1-V3
query/pagination/filter/path tests, controller authorization/query contracts and
read-only registry capacity assertions. Remaining regression/browser evidence
and final inventory are recorded at closeout below.

An additional controller regression proves explicit `snapshotId` path binding
without relying on compiler parameter metadata (5 controller tests in final
package). Real browser verification also exposed the nanosecond live-capacity
timestamp/decoder boundary; a focused regression now covers nine-digit Instant
fractions. Earlier failed attempts are retained, not presented as acceptance.

V2 legacy compatibility: metadata lists also include LEGACY_PATH snapshots and
explicit LEGACY_UNVERIFIED records with bounded printable non-UUID old IDs.
These records do not make an entire list fail decoding. Unverified statistics
remain null; unsupported snapshot-detail/re-execution links and non-UUID run
detail links are not invented. This does not widen existing operational UUID
endpoints or mark old publications as validated. A dedicated MySQL fixture and
frontend decoder/rendering regressions cover this boundary.

### Workspace closeout — technical PASS, owner acceptance pending

Final built UI: `.agent-workspace/phase-wdm/UI-workspace-build-20261107-183709`.
Final real browser evidence: `.agent-workspace/phase-wdm/WDM-05.10-4nVCHv/`:
eight browser groups PASS, eight original successful result maps verified,
source inventory preserved, production test-helper exclusions verified. Both
engines still yield 15/3/0 patterns for ABSOLUTE 0.1/PERCENT 14/PERCENT 100,
matching both direct miners and the independent oracle in full support maps.
This gate used execution=true only on its disposable backend, actual packaged
production classes, shared Auth/HTTPS and MySQL/Flyway V1-V3/Hibernate validate.

Full reactor evidence on the same unchanged backend production implementation:
`.agent-workspace/phase-wdm/WDM-05.10-U6YrLS/REGRESSION.log` — core **1185 PASS**,
backend **1133 PASS**, no failures/errors/skips. The subsequent legacy-only
MySQL test addition passed all five workspace integration tests in
`UI-workspace-legacy-backend.log`; no backend production code changed afterward.
The final browser pass deliberately did not repeat that entire reactor run.

Frontend full regression: **490 PASS, 2 opt-in live tests skipped**, 35 passed
suites (`UI-workspace-frontend-final.log`). TypeScript `tsc --noEmit`, scoped
ESLint and production build PASS. A final ES5-compatible spelling change in the
legacy ID validator passed the four decoder tests again before building
(`UI-workspace-decoder-final.log`). No target/dependency upgrade was used.
Build retains existing unrelated legacy lint/Browserslist warnings.

Earlier iterations caught and corrected hidden UTC fields, explicit storage
path binding and live Instant timestamp precision. One intermediate full
frontend invocation hit the existing Auth navigation test's own 15-second
timeout; its focused rerun passed and the final full 490-test run passed.
Those earlier failures/logs/builds are retained as evidence, not counted as PASS.

| Criterion | Result / evidence |
|---|---|
| Snapshot discovery | PASS — server list, literal search, URL-filtered refresh, direct detail/configuration links |
| Run discovery | PASS — newest-first real two-run history, stable SQL tie-break/pagination/filter tests, detail/source links |
| Backend file locations | PASS — current publication CSV/SPMF/mapping metadata, exact reference binding, Admin authorization and desktop/mobile evidence |
| Capacity | PASS — actual configured 2 used/0 available, exact occupied run IDs, coherent read-only snapshot, no release/ack/extra row |
| Presentation | PASS — create/history, grouped configuration/output/status/metrics, responsive local table scrolling and screenshots |
| Legacy records | PASS — V2 legacy metadata discoverable, null unverified stats, no fabricated executable routes |
| Safety/regression | PASS — original maps/metrics, zero/capacity/tamper/lost-response guards, tests/type/lint/build, unchanged mining/schema/dependencies |
| Resource ownership | PASS — own JVMs/browser/proxy/container closed, private temp staging/TLS/storage removed after path checks; owner environment untouched |
| Historical run publication | NOT VERIFIED — intentionally shown only as current source publication with explicit warning; no historical identity/persistence was fabricated |

Review screenshots in the final directory: `workspace-history-desktop.png`,
`workspace-history-mobile.png`, `workspace-storage-desktop.png`,
`workspace-storage-mobile.png`, `workspace-capacity-full.png`; each successful
run's batch also retains desktop/mobile metrics screenshots. PREPARATION.json
records actual build/JAR/helper/source/tool hashes, and RESULT.json records
the exact requests and case outcomes. No auth storage dumps or credentials are
retained. These are correctness/UI observations, not performance measurements.

File inventory for this follow-up: 11 new files (workspace controller/service,
two backend test classes, frontend workspace API/decoder + tests, read hook,
list/panels/selected-dataset components + panel tests); 18 modified files
(registry + registry tests, frontend API index + eight feature/style files,
engine browser cases + README, five documentation/checklist files). No source
deletions, migrations, package/dependency changes, commit or push. Earlier
dirty WDM-05 changes were preserved. Task-owned builds/failed/final evidence
remain intentionally; no pre-existing cleanup or deletion is pending.

Next: owner review of this UI/read-API follow-up, then WDM-06.1 Schema Audit
when separately authorized. WDM-06 persistence/handoff and exact historical
input binding remain outside this task.

### Admin step 5 / large-report list hotfix (2026-11-07)

Owner reported that Admin guide step 5 remained unavailable and that workspace
reads showed the generic "Chưa tải được thông tin" message across steps.
The guide now enables mining/create and run-history links at step 5, updates
the capability summary, and leaves pattern browsing/combo steps unavailable.

Live read-only diagnosis found MySQL error 1038 (Out of sort memory), with
snapshot report JSON up to 6,387,454 bytes and the existing 262,144-byte server
sort buffer. No server variables, schema, reports or business data were changed.
The old projection-before-ORDER-BY query was reproduced failing in a disposable
MySQL test with 1 MB JSON reports and a 32 KiB sort buffer. A first narrow-ID
attempt still failed when filtering on conversion JSON; the final fix also
isolates filtering from the outer sort via a materialized ID/date derived table.

Pagination now counts matching records, materializes/sorts only ID/date rows,
then retrieves at most one page of metadata without SQL sorting JSON and restores
the exact ID order in Java. All reads remain in the existing repeatable-read
transaction; filters, descending date/ID order and limits are preserved. Inner
LIMIT prevents derived-table merging as documented in the
[MySQL 8.4 optimizer manual](https://dev.mysql.com/doc/refman/8.4/en/derived-table-optimization.html).
This changes query shape, not the owner's sort-buffer setting or mining code.

Auxiliary read hooks retain safe WdmApiError status/requestId. The UI now
distinguishes HTTP 401/403/404, backend 5xx, invalid successful responses and
network failures; it never renders raw SQL/exception messages. Backend 500s
are no longer described as connection/permission problems.

Validation: 11 focused backend tests PASS (6 real-MySQL workspace integration,
5 controller), including the large-report regression with JSON status filter;
41 focused frontend tests PASS across 6 suites; TypeScript/scoped lint/build
PASS. Final package succeeded after the running-JAR lock was released.
Owner-database equivalent fixed pagination/projection read returned 20 metadata
rows without a write or settings change. Logs: UI-workspace-sort-repro.log,
UI-workspace-sort-fix-2.log, UI-workspace-read-fix-frontend.log,
UI-workspace-hotfix-package-final.log and UI-workspace-hotfix-build.log.
Final UI build: UI-workspace-hotfix-build-20261107-185227.

Runtime acceptance: NOT VERIFIED. Automatic approval review rejected both
backend stop/restart and a subsequent start attempt with "blocked by policy";
those blocked commands did not launch a backend. The old process subsequently
ceased outside the agent's executed commands. Owner chose "Tôi sẽ tự chạy sau"
and will start the documented local JAR manually. No further restart attempt
is authorized by that choice. No production-data test account/JWT was created.

Hotfix inventory: 11 existing files modified (guide + guide test; workspace
service + MySQL test; read hook; panels + panel tests; list; selected dataset;
this contract; local checklist). No source files created/deleted, mining,
migration/dependency changes, or pre-existing cleanup. Build/test logs retained.
