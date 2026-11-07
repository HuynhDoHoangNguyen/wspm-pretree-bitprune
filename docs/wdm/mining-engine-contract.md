# WDM-05.1 — Engine public boundary

Date: 2026-11-07 (Asia/Bangkok). Status: IMPLEMENTED / TECHNICAL PASS /
OWNER ACCEPTED (2026-11-07). Owner authorized the reviewed implementation
plan. WDM-05.2 production adapter/dispatcher was outside WDM-05.1; its subsequent
implementation and verification are recorded in the WDM-05.2 section below.

## Scope and authority

This is a backend Java application contract, not an HTTP API, JPA model, standalone
runner or new mining algorithm. It reuses the accepted core APIs and contracts:
[PreTree](../research/wspm-pretree-spec.md),
[BitPrune semantics](../research/wspm-bitprune-c01-1-semantic-contract.md),
[statistics/architecture](../research/wspm-bitprune-c02-architecture-contract.md)
and [configured requests](mining-request-contract.md).

Production types live in `backend/.../services/run/engine/`: `MiningAlgorithm`,
`MiningEngine`, `MiningEngineRequest`, `MiningEngineResult`. Small result-specific
types are nested in the result. WDM-05.1 introduced no implementation or Spring
bean. WDM-05.2 supplies `CoreMiningEngineAdapter`; existing endpoints still do not
invoke this interface.

## Ownership and execution

`MiningEngineResult execute(MiningEngineRequest request)` is synchronous. A caller
receives either one completed result or an exception, never an accepted/partial
result. Scheduling, admission, run IDs and lifecycle persistence remain WDM-05.3–05.5.

The application caller owns authentication, authorization, fresh conversion
publication verification through `ConversionReadService.verified`, parsing its
SPMF bytes, provenance and later result handoff. A parsed model alone is not proof
of authorization or artifact integrity. The eventual run workflow must bind the
publication identity/hashes and the parsed input to the same run; this interface
does not accept a path, HTTP request, entity, token or fabricated dataset count.

WDM-05.2 adapter obligations:

1. Reject null input. Resolve `request.minimumSupport()` against the actual
   `request.database().size()` using `MinimumSupportResolver` and invoke the
   existing `RunConfigurationValidityGate` **before** calling either miner.
2. Dispatch only the exact `MiningAlgorithm` requested. Use the public Java API
   below, never `Main`, console scraping or a child JVM.
3. Create a fresh `ExecutionMemorySampler` per invocation and close it with
   try-with-resources on both success and failure. Keep per-call mutable state
   separate. Do not assert thread safety merely from interface immutability.
4. Project the complete engine result, retaining exact supports and iteration
   order. Check resolved metadata against preflight (mode, configured-value bits,
   relative fraction, sequence count and effective-threshold bits), and return the
   requested algorithm. No recomputed or rounded threshold may replace it.
5. Propagate errors without manufacturing success or an empty fallback map.
   Do not duplicate logging across layers. The future run service owns safe
   transition/failure logs and public failure mapping.

No cancellation, forced timeout, retry, idempotency, queue capacity or crash
recovery guarantee is introduced here. `mean_w NOT NULL` before a QUEUED insert
and transactional admission remain explicit WDM-05.3–05.5 design obligations;
this task neither invents a placeholder nor moves mining into the HTTP controller.

## Input

`MiningEngineRequest(algorithm, database, minimumSupport)` contains immutable core
`SequenceDatabase` and `MinimumSupport` values, and a typed two-value algorithm.
Its constructor checks non-null components and non-empty database only.
Construction does **not** approve execution: zero/signed-zero/configured percent
underflow can still be represented and must be rejected by adapter preflight.

PERCENT remains a percentage in [0,100], not a client fraction. ABSOLUTE remains
in the existing weighted-threshold unit. Preserve configured and resolved values,
unrounded core conversion, Java double behavior, SPMF itemset boundaries and
`support * MeanW >= effectiveThreshold`. Positive tiny ABSOLUTE values remain
eligible; a supported configuration producing zero accepted patterns succeeds.
No epsilon, integer rounding, clamping or alternate comparator is authorized.

## Public engine APIs and projection

| Engine | Invocation | Metadata | Statistics | Complete algorithm timing |
|---|---|---|---|---|
| PreTree | `WspmPreTreeMiner.mineMeasured(database, configuration, sampler, ResultRetentionPolicy.SUPPORT_ONLY)` | `result.minimumSupport()`, `distinctItemCount()`, `globalMeanWeight()` | `result.statistics()` | `metrics.algorithmNanos()` |
| BitPrune | `BitPruneMiner.mineMeasured(database, configuration, sampler)` | `metadata.minimumSupport()`, `distinctItemCount()`, `globalMeanWeight()` | `result.statistics()` | `metrics.completeAlgorithmNanos()` |

Both expose `result.supports()`. PreTree SUPPORT_ONLY changes post-mining retention,
not Prime-block traversal/support calculation. Default FULL_ENCODING is reserved
for existing research/audit uses and compatibility comparison in tests.

## Completed result invariants

`MiningEngineResult` contains algorithm, complete `Map<Pattern,Integer>`, resolved
minimum support, distinct-item count, global MeanW, typed original statistics and
normalized algorithm metrics. This is not directly serialized to clients.

- Copy supports into an insertion-ordered map and expose an unmodifiable view of
  that private copy. Preserve the engine's deterministic iteration order; do not
  force the two engines to share one traversal order.
- Keep `Pattern` structural identity, including I/S distinctions. Reject null
  entries, root/empty pattern, zero/negative support and support above sequence count.
- Require positive sequence count, effective threshold, distinct-item count and
  finite positive MeanW. The statistics variant must match algorithm, sequence
  count and map size. Empty map plus zero pattern count is valid.
- Preserve the original immutable `WspmMiningStatistics` or `BitPruneStatistics`
  through sealed variants. Common candidate count is respectively
  `candidateCount` or `generatedExtensions`; F1 is excluded by the accepted core
  definitions. BitPrune tier counters remain available only in its original record.
- No PrefixTree, encoding, occurrence index, bitmap, sampler or mutable worker
  state crosses this result boundary.

Constructor checks establish internal consistency, not truth/completeness against
the input database. They cannot detect a consistently truncated map/statistics or
a fabricated support within range. Real adapter full-map differential tests in
WDM-05.2 and the Web gate in WDM-05.10 remain required.

## Measurement and result lifetime

Nested `Metrics` retains immutable preprocessing/DFS `PhaseExecutionMetrics`,
complete-algorithm nanoseconds, heap-after-mining bytes and observed algorithm
peak heap bytes. It validates nonnegative/containing timing and heap observations.
Projection copies existing observations without recomputation. The PreTree legacy
statistics `elapsedNanos` is retained only as part of the original statistics and
must not replace complete-algorithm time.

Algorithm metrics exclude dataset/artifact I/O, parsing, queue wait, adapter result
projection/copying, persistence and output I/O. The six-scope application capture
and conversion to HTTP/database milliseconds belong to WDM-05.6 and must define
their own boundaries; this carrier does not fabricate zero for absent scopes.

Heap is JVM-wide used heap sampled by the core sampler (default 5 ms interval),
not process RSS, physical RAM or memory attributable exclusively to this run.
Other backend requests/runs can affect it. No benchmark or performance claim is
made by these tests or the Web UI.

The caller owns the returned result until handoff completes and must release its
reference afterwards. No singleton map cache, unbounded run-result registry or
automatic persistence is provided. Durable pattern storage/browsing is WDM-06;
the intermediate execution-to-handoff lifecycle must be designed in WDM-05.2–05.5.

## Verification

New tests are plain in-process Java/JUnit tests, not HTTP, Spring wiring, executor,
database or browser integration. `MiningCoreBoundaryCompatibilityTest` calls the
real core APIs and explicitly projects their outputs in test code. That code is a
compatibility probe, not an implemented production adapter.

Synthetic SPMF fixture (three sequences, two items, seven occurrences):

```text
1 2 -1 1 -1 -2
1 -1 2 -1 -2
1 2 -1 -2
```

Direct enumeration yields seven non-empty structural patterns: `<(1)>:3`,
`<(2)>:3`, `<(1 2)>:2`, `<(1)(1)>:1`, `<(1)(2)>:1`, `<(2)(1)>:1`,
`<(1 2)(1)>:1`. Core MeanW is 0.5. This hand-enumerated tiny-fixture expectation
is not a new paper reproduction or independent brute-force implementation.

Compatibility cases cover ABSOLUTE 0.1, Double.MIN_VALUE and Double.MAX_VALUE,
PERCENT 14 and 100; compare full maps to FULL_ENCODING baseline and both engines,
counts, exact configured/resolved bits, metadata and typed counters. High
thresholds produce valid empty results. Preflight probes cover 0/-0 for both modes
and positive PERCENT underflow. They demonstrate the required existing gate,
not production interception before dispatch (that remains WDM-05.2).

Contract cases cover defensive copying, map order and temporal identity, mutation
through entries, invalid supports/root/counts, metadata mismatch, null components,
unsupported completed threshold and metric containment.

Focused command (from repository root):

```powershell
mvn -pl backend -am test '-Dtest=MiningEngineResultContractTest,MiningCoreBoundaryCompatibilityTest,ResultRetentionPolicyTest,BitPruneTable1ThreeOracleDifferentialTest,RunConfigurationValidityGateTest' '-Dsurefire.failIfNoSpecifiedTests=false'
```

PASS: mining-core 11/11 and backend 26/26, no failures/errors/skips, native Maven
exit 0. Backend includes all 16 new cases (6 carrier/request contract and 10
parameterized compatibility/preflight cases) and 10 existing gate cases. The 11
core cases include existing retention and Table 1 three-oracle full-map checks.
Evidence: `.agent-workspace/phase-wdm/WDM-05.1-focused.log`. Existing Mockito/JVM
dynamic-agent warnings appear on stderr; the Maven result is BUILD SUCCESS.

Selected regression command:

```powershell
mvn -pl backend -am test '-Dtest=*,!BackendApplicationTests,!OrderExportAvailabilityTest' '-Dsurefire.failIfNoSpecifiedTests=false'
```

This explicitly excludes the default-context `BackendApplicationTests` and the
separate `OrderExportAvailabilityTest`; neither is claimed as verified by this
task. Existing selected MySQL integration tests use isolated Testcontainers.
New boundary cases themselves are in-process compatibility tests, not database
tests. No development database or browser gate is needed for this boundary.
Regression evidence: `.agent-workspace/phase-wdm/WDM-05.1-regression.log`.

Final selected regression: **mining-core 1182/1182, backend 985/985 PASS**, zero
failures/errors/skips, native Maven exit 0 and BUILD SUCCESS. New cases are
included in the 985 backend total, not additional to it. Existing Mockito agent
and isolated-context connection-pool shutdown warnings do not change this result.
No mining-output disagreement or performance measurement occurred.

Acceptance matrix: all 05.1-AC01–08 **PASS for this Java boundary scope**.

| AC | Evidence |
|---|---|
| 05.1-AC01 | Java 21 compilation; interface, request, immutable result and contract tests |
| 05.1-AC02 | Explicit measured API/retention table and real compatibility probes |
| 05.1-AC03 | Native resolver/gate preflight, signed-zero/underflow rejection, raw-bit configured/resolved comparisons |
| 05.1-AC04 | Complete seven-pattern expected map, two-engine/full-encoding comparison, I/S identity and immutable snapshot tests |
| 05.1-AC05 | Typed original statistics, normalized native observations, units/scopes and containment tests |
| 05.1-AC06 | Five supported real-core matrix entries plus five unsupported preflight entries; no production adapter claim |
| 05.1-AC07 | Documented sampler ownership, error propagation, result handoff and deferred cancellation/recovery |
| 05.1-AC08 | File-scope audit: no core, migration/entity, endpoint, executor or frontend change |

Final file/link/whitespace audit PASS, including new untracked files. Seven new
source/test/contract files; updated docs index, one exact .gitignore exception
needed to retain the new contract, and ignored WDM checklist. Two retained
evidence logs. No deletion/move, disposable helper, pending cleanup or Git
stage/commit/push. Maven generated outputs remain ignored. Concrete adapter,
production preflight interception, HTTP/executor/persistence/browser execution
remain NOT VERIFIED by WDM-05.1. Owner accepted WDM-05.1 in chat on 2026-11-07;
checkbox checked. This acceptance-only update does not rerun tests or modify
production code. Owner requested WDM-05.2 planning; implementation has not started.

## Research fidelity and task boundary

- **Accepted-contract confirmed:** unchanged core parser/models, measured APIs,
  statistics, retention semantics, configured/resolved units and comparator.
- **Engineering inference:** application interface, enum, immutable projection,
  consistency checks, fixture and caller/resource ownership rules.
- **Unresolved:** R6 and exact-real comparator equivalence remain open. No
  original-paper claim is newly established; no algorithm source change is made.

WDM-05.2 owns the concrete adapter, selection and production preflight tests.
WDM-05.3–05.5 own admission, persistence, state and recovery; 05.6 metrics capture;
05.7–05.9 HTTP/UI; 05.10 Web differential; WDM-06 durable pattern handoff.

## WDM-05.2 — Concrete engine adapter (2026-11-07)

Owner accepted WDM-05.1 and then approved the detailed WDM-05.2 plan and authorized
implementation. This section supersedes the planning-only checkpoint above for
WDM-05.2. WDM-05.1 owner acceptance and historical evidence remain unchanged.
Current status: IMPLEMENTED / TECHNICAL PASS / OWNER ACCEPTED (2026-11-07).

### Production path and resource ownership

`CoreMiningEngineAdapter` is the single `@Component` implementation of
`MiningEngine`. Its public `@Autowired` constructor uses the existing gate and
fresh-miner/sampler constructor references. A package-private constructor accepts
factories for dispatch/resource tests; it is not an endpoint or runtime test flag.
The resolver is stateless and reused; databases, miners, samplers and results are
invocation-local. The adapter performs no parsing, database/file I/O or scheduling.

Execution follows the accepted boundary contract:

1. Require non-null request, resolve against `database.size()` using the actual
   core resolver, and invoke `RunConfigurationValidityGate` before creating any
   miner or sampler. Zero/-0 and positive-percent underflow are rejected here.
2. Open a fresh sampler in try-with-resources and invoke only the selected engine
   factory. PreTree uses measured SUPPORT_ONLY; BitPrune uses its measured API.
3. Compare the returned resolution to preflight: mode, sequence count,
   configured-value bits, relative-fraction presence/bits and effective-threshold
   bits. A mismatch throws a safe `IllegalStateException`; no repair or fallback.
4. Project the entire support map, core metadata, original typed statistics and
   native observations into `MiningEngineResult`. Algorithm time uses the correct
   native field, not PreTree statistics' legacy elapsedNanos.
5. Close the sampler before returning. Any engine, metadata, projection or close
   failure prevents a successful return. Java primary/suppressed exception rules
   are preserved; there is no catch/retry or exception-to-empty-result conversion.

DEBUG logs contain algorithm, mode and sequence/pattern/candidate counts only,
without paths, dataset content, patterns, credentials or raw exception messages.
Completion logging happens after sampler close. The future run service remains
the owner of run-ID transition/error logging; the adapter does not duplicate it.

No result cache or mutable mining state is stored on the bean. Repeated-call
resource isolation is tested, but this is not a concurrency/capacity acceptance
or cancellation guarantee. Queuing, transport errors, persisted run failure and
restart recovery remain WDM-05.3–05.5.

### Verification scope and evidence

Three new test classes distinguish their evidence:

- `CoreMiningEngineAdapterTest`: mocked miners/samplers with real resolver/gate.
  Tests exact selected calls and arguments, no resource creation on rejected
  preflight, native metrics/statistics projection, metadata drift, engine and
  projection failures, close failure, suppressed exceptions and factory failure.
  Its repeated-call test additionally uses real miners and spied real samplers
  with finally cleanup to check fresh resources and absence of result caching.
- `CoreMiningEngineAdapterCompatibilityTest`: actual production adapter and both
  actual miners, six supported configurations on the same three-sequence fixture
  documented above. Compares full maps to direct-core calls, frozen FULL_ENCODING
  baseline, explicit seven-pattern expectation, each engine's own iteration order,
  counts, metadata and counters. Includes ABSOLUTE 0.1/0.5/Double.MIN_VALUE/
  Double.MAX_VALUE and PERCENT 14/100; valid empty output is success.
- `CoreMiningEngineAdapterWiringTest`: small real Spring context registers adapter
  and gate, verifies one MiningEngine bean, executes both real algorithms and
  rejects zero configuration. It does not start Boot, Auth, HTTP or a database.

No timing equality assertion or performance claim is made. Forged metadata cases
are controlled mock negative tests, not actual core-output disagreements.

Initial focused checkpoint `WDM-05.2-focused.log`: 46 cases, 0 failures and 9
Mockito setup errors. BitPrune helper constructed a measured carrier that called
another mock while stubbing was unfinished. Carrier/statistics construction was
moved before stubbing; production adapter/core code did not change to fix this.
Real compatibility and wiring cases already passed. Failed evidence is retained.

Corrected focused command:

```powershell
mvn -pl backend -am test '-Dtest=CoreMiningEngineAdapterTest,CoreMiningEngineAdapterCompatibilityTest,CoreMiningEngineAdapterWiringTest,MiningEngineResultContractTest,MiningCoreBoundaryCompatibilityTest,RunConfigurationValidityGateTest,ResultRetentionPolicyTest,BitPruneTable1ThreeOracleDifferentialTest' '-Dsurefire.failIfNoSpecifiedTests=false'
```

`WDM-05.2-focused-final.log`: **core 11/11 and backend 56/56 PASS**, no
failures/errors/skips, native Maven exit 0. Backend includes 30 new WDM-05.2 cases
(23 orchestration/lifecycle cases, 6 real adapter matrix entries, 1 Spring wiring
case), 16 unchanged WDM-05.1 cases and 10 existing gate cases. Inner loops cover
both algorithms where noted; they are not counted as additional JUnit cases.

Reactor regression command:

```powershell
mvn -pl backend -am test '-Dtest=*,!BackendApplicationTests' '-Dsurefire.failIfNoSpecifiedTests=false'
```

Evidence: `.agent-workspace/phase-wdm/WDM-05.2-regression.log`. Only the default
environment `BackendApplicationTests` is excluded/NOT VERIFIED. Unlike the
WDM-05.1 selection, this run includes the existing isolated-MySQL
`OrderExportAvailabilityTest`. Backend/core tests neither prove Web execution
nor production deployment/capacity.

Final selected regression: **mining-core 1182/1182 and backend 1016/1016 PASS**,
zero failures/errors/skips, native Maven exit 0 and BUILD SUCCESS. The 30 new
WDM-05.2 cases are included in the backend total. Core three-oracle and differential
suites ran unchanged. Existing Mockito dynamic-agent and isolated pool-shutdown
warnings remain; no core-output disagreement or benchmark occurred.

### Acceptance criteria — all 05.2-AC01–10 PASS for adapter scope

| AC | Evidence to assess |
|---|---|
| 05.2-AC01 | Small Spring context discovers exactly one usable MiningEngine implementation |
| 05.2-AC02 | Real resolver/gate reject zero/signed-zero/underflow before any resource factory call |
| 05.2-AC03 | Selected factory/call only; exact database/config/sampler arguments and SUPPORT_ONLY for PreTree |
| 05.2-AC04 | Six real adapter configurations: full maps/counts equal both direct-core results and the explicit tiny oracle |
| 05.2-AC05 | Raw-bit configured/relative/effective checks, actual count, metadata-drift negative controls for both branches |
| 05.2-AC06 | Original statistics retained; exact native timing/heap fields, including distinction from legacy elapsedNanos |
| 05.2-AC07 | Fresh-resource repeated-call test; close on success/engine/projection/metadata failure, suppressed close exception |
| 05.2-AC08 | Engine/close/factory failures propagate, no fallback result; production path has no catch/retry/cache |
| 05.2-AC09 | Focused/wiring/core compatibility verification and final selected reactor regression |
| 05.2-AC10 | Scope audit: no core/gate/resolver, schema/entity, HTTP/executor or frontend edits |

Final audit: new source/contract whitespace, local contract links and
`git diff --check` PASS. Four new Java files (one adapter, three test classes),
three updated documents (this contract, docs index and ignored checklist),
three retained task evidence logs including the failed focused checkpoint. No
deletion/move, disposable helper, pending cleanup or Git stage/commit/push.
All pre-existing WDM-05.1 work, .gitignore changes and evidence remain preserved;
Maven generated outputs remain ignored. Owner accepted WDM-05.2 in chat on
2026-11-07; checkbox checked. This acceptance-only update does not rerun tests or
modify production code. Owner requested the WDM-05.3 plan; implementation has
not started. Real run creation/execution admission remains later-task work.

### Research fidelity and boundaries

- Accepted-contract confirmed: unchanged core models/resolver/gate/miners,
  retention behavior, exact support, Java-double predicate and measurement scopes.
- Engineering inference: Spring adapter/factories, consistency checks and
  application result projection. No mining formula or source claim is introduced.
- Unresolved: R6 and exact-real comparator equivalence remain unchanged.

WDM-05.3 API/DB admission, WDM-05.4 managed executor, WDM-05.5 lifecycle/recovery,
WDM-05.6 application measurement and WDM-06 durable pattern persistence remain
unimplemented by WDM-05.2. Do not treat successful Java execution as a successful
Web run or persisted result. WDM-05.3 was not started at this checkpoint; its
subsequent creation/admission work is recorded in
[the run request contract](mining-request-contract.md).

WDM-05.4 subsequently invokes this same adapter from an opt-in bounded executor
with worker reverification and bounded in-memory completion handoff. It does not
change the synchronous engine interface, core result/metrics semantics or sampler
ownership. Managed execution evidence and the DB lifecycle/durability limitations
are recorded in the WDM-05.4 section of that request contract.

WDM-05.5 subsequently integrates owner/attempt/lease-fenced DB lifecycle outside
this adapter. No engine request, complete result, numeric predicate or mining
semantics change. Success finalization preserves the complete map in memory;
uncertain commits trigger DB-only reconciliation, never engine replay. See the
WDM-05.5 section of [the request contract](mining-request-contract.md).

WDM-05.6 subsequently captures worker input I/O and total scope outside this
adapter. RunMetricsCapture preserves native phase/complete nanoseconds, counts
and observedPeakHeapAlgorithmBytes, then independently floors durations to DB
milliseconds. Dataset I/O is the three verified storage calls during worker
refresh (including checksum checks, excluding DB/parsing). Total spans refresh
through engine return after claim, excluding queue/pre-admission/claim/terminal
DB retries. Output I/O stays null. The persisted heap is still algorithm-scoped
JVM-wide used heap sampling, not a worker-total or private-RAM observation.
Atomic fenced success+metrics and complete-payload readback retain the original
capture/result through uncertain commits. No native metrics/statistics or engine
API change, new sampler, mining semantic change or performance claim is made.
