# WDM-04.1 — Mining run request contract

Date: 2026-11-06 (Asia/Bangkok).

Implementation scope: configured request input, strict JSON parsing and validation.
This contract does not establish a production run endpoint, resource eligibility,
threshold resolution, persistence or mining execution. Owner accepted the plan
and authorized implementation; owner accepted WDM-04.1 in chat on 2026-11-06.

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
