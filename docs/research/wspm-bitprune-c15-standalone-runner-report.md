# WSPM_BitPrune C15 Standalone Runner & Six-Scope Orchestration Report

**Document ID:** `WSPM-BITPRUNE-C15-REPORT`  
**Task:** C15 — Standalone Java Main & Configuration Closure  
**Parent Phase:** Phase C — WSPM_BitPrune Implementation & Correctness  
**Status:** ACCEPTED  
**Date:** 2026-10-31  

---

## 1. Executive Status

- Subtasks **C15.0, C15.1, C15.1-R1, C15.2, C15.3, C15.3-R1, and C15.4** have successfully completed independent research reviews and are accepted by the Repository Owner.
- Parent task **C15 (Standalone Java Main & Configuration)** satisfies all functional, architectural, timing, console, and testing requirements and is formally transitioned to **`ACCEPTED`**.
- Standalone execution orchestration provides empirical runtime verification for the three previously pending execution scopes:
  - **Scope 1 (Dataset I/O)**
  - **Scope 5 (Output I/O)**
  - **Scope 6 (Total Execution)**
- Parent task **C13 (Time/Memory Instrumentation Parity)** is now supported by complete runtime evidence across all six standardized measurement scopes and is formally transitioned from **`PARTIAL`** to **`ACCEPTED`**.
- Task **C16 (Final Regression & Phase C Acceptance)** remains **`NOT STARTED`**.
- **Phase C remains IN PROGRESS** pending the C16 final audit and Phase C formal closure contract. No premature claim of Phase C completion is made.

---

## 2. Scope and Decomposition

Task C15 decomposed the standalone execution subsystem into cohesive, non-regressive increments:

1. **C15.0 — Metadata Exposure Contract:**
   Integrated run metadata propagation through `BitPruneRunMetadata` and `MeasuredBitPruneRun.metadata()`, exposing distinct item counts, exact `MinimumSupportMode`, relative fraction, effective threshold, sequence resolution count, and `globalMeanWeight` calculated during algorithm preprocessing without re-scanning or re-parsing the database.

2. **C15.1 & C15.1-R1 — Immutable Run Configuration Model:**
   Implemented `BitPruneRunConfiguration` encapsulating 8 run properties with decoupled validation: in-memory range checks (`relative ∈ [0.0, 1.0]`, `absolute >= 0.0`, finite checks) occur at instantiation, while filesystem and path collision validations occur strictly prior to execution. Relative fractions are converted to `MinimumSupport.percentage` using `BigDecimal.movePointRight(2)` to eliminate floating-point multiplication artifacts.

3. **C15.2 — Standalone Runner & Six-Scope Orchestration:**
   Implemented `MainWspmBitPrune.execute(BitPruneRunConfiguration)` returning immutable `BitPruneExecutionRun`. Orchestrates all six standardized measurement scopes:
   - Early filesystem and path collision validation outside total execution;
   - Single caller-owned `ExecutionMemorySampler` lifecycle per execution;
   - Scope 1 Dataset I/O measurement;
   - Scopes 2–4 delegated to `BitPruneMiner.mineMeasured(...)`;
   - In-memory `BitPruneOutputProjection` generated exactly once inside total execution;
   - Scope 5 Output I/O measured exclusively around physical disk write (`BitPruneOutputWriter`);
   - Scope 6 Total Execution encompassing scopes 1, 4, 5 and coordination overhead, closing before sampler shutdown;
   - Zero console formatting or printing inside total execution.

4. **C15.3 & C15.3-R1 — Console Formatting & Eight-Combination Flag Matrix:**
   Implemented deterministic four-section console reporting via `MainWspmBitPrune.execute(configuration, output)` and pure formatting method `summaryLines(configuration, run)`. Reuses pre-computed projection lines for pattern printing. Verified the complete 8-combination flag matrix (`printPatterns`, `printStatistics`, `exportPatterns`) against state-free reference executions with zero state leaks or result mutations.

5. **C15.4 — Executable Main, End-to-End Integration, Lifecycle Matrix & Table 1 Oracle:**
   Equipped `MainWspmBitPrune` with 8 editable source constants and executable `main(String[] args)` delegating directly to the C15.3 console overload without CLI parsing or terminal prompts. Verified complete Table 1 oracle reproduction, differential equality against the Phase B `WspmPreTreeMiner` baseline, controlled FIMI format routing, and exception lifecycle cleanup across malformed parser input, R6 zero-threshold guard rejection, and writer I/O failures.

---

## 3. Standalone Configuration Contract

[`MainWspmBitPrune`](../../src/main/java/vn/edu/wspm/bitprune/experiments/MainWspmBitPrune.java) exposes 8 top-level source-configured constants matching the Phase B baseline pattern:

```java
// Edit these constants to configure a reproducible non-interactive run.
private static final Path DATASET_PATH =
        Path.of("src/test/resources/datasets/wspm/table1.spmf");

private static final SequenceDatabaseFormat INPUT_FORMAT =
        SequenceDatabaseFormat.SPMF_SEQUENTIAL;

private static final boolean MIN_SUP_IS_RELATIVE = true;

private static final double MIN_SUP = 0.14;

private static final boolean PRINT_PATTERNS = false;

private static final boolean PRINT_STATISTICS = true;

private static final boolean EXPORT_PATTERNS = false;

private static final Path OUTPUT_PATH =
        Path.of("output/wspm-bitprune-patterns.txt");
```

### Architectural Constraints
- **No Interactive Input:** Does not instantiate `Scanner` or read from `System.in`.
- **No Command-Line Parser:** `main(String[] args)` ignores command-line arguments and relies exclusively on deterministic source constants.
- **No Retention Switch:** WSPM_BitPrune exclusively uses the Owner-approved normative `FINAL_SCIENTIFIC_RESULT` model. No retention policy constant or parameter is exposed.
- **Demonstration Path vs Production Datasets:** The default dataset path targets `src/test/resources/datasets/wspm/table1.spmf` for immediate zero-configuration verification out of the box. Users configure `DATASET_PATH` to point to experimental sequence databases (e.g., SIGN, Snake, Pumsb) for large-scale runs.
- **Safe Defaults:** Output file export (`EXPORT_PATTERNS`) and pattern console logging (`PRINT_PATTERNS`) are disabled by default to prevent unintentional filesystem writes and terminal pollution.

---

## 4. Six-Scope Measurement Reconciliation

| Scope | Boundary | Owner / API | Runtime Evidence | Status |
|---|---|---|---|:---:|
| **Scope 1 — Dataset I/O** | Surrounds `SequenceDatabaseParser.parse(path, format)` | `MainWspmBitPrune.execute` | `MainWspmBitPruneTimingTest`, `MainWspmBitPruneIntegrationTest` | SATISFIED |
| **Scope 2 — Preprocessing** | Database statistics, MeanW, F1, SID mapping, vertical occurrence index, singleton bitmaps, CMAP tables, DFS prepared boundary | `BitPruneMiner.mineMeasured` | `BitPrunePhaseBoundaryTest`, `BitPruneMeasurementIntegrationTest` | SATISFIED |
| **Scope 3 — DFS Mining** | Candidate generation, Tier 1 CMAP filter, Tier 2 Bitmap bound, Tier 3 Exact verification, PrefixTree integration | `BitPruneMiner.mineMeasured` | `BitPruneMeasurementIntegrationTest`, `BitPrunePhaseBoundaryTest` | SATISFIED |
| **Scope 4 — Complete Algorithm** | Encompasses Scope 2 + Scope 3 + tree freezing + `BitPruneMiningResult` construction | `BitPruneMiner.mineMeasured` | `MeasuredBitPruneRunTest`, `BitPrunePhaseBoundaryTest` | SATISFIED |
| **Scope 5 — Output I/O** | Surrounds physical disk writing via `BitPruneOutputWriter.write(path, projection)` | `MainWspmBitPrune.execute` | `MainWspmBitPruneTimingTest`, `MainWspmBitPruneConsoleTest` | SATISFIED |
| **Scope 6 — Total Execution** | Covers Scope 1 + Scope 4 + Scope 5 + projection creation + runner orchestration overhead; stops before sampler shutdown | `MainWspmBitPrune.execute` | `MainWspmBitPruneTimingTest`, `MainWspmBitPruneIntegrationTest` | SATISFIED |

### Containment and Invariant Verification
1. **Algorithm Containment:**
   $$\text{Complete Algorithm Time} \ge \text{Preprocessing Time} + \text{DFS Mining Time}$$
   Verified across all tests: Complete Algorithm includes final Prefix Tree freezing and result object assembly.
2. **Total Execution Containment:**
   $$\text{Total Execution Time} \ge \text{Dataset I/O Time} + \text{Complete Algorithm Time} + \text{Output I/O Time}$$
   Verified across all flag combinations and execution runs.
3. **Peak Heap Coverage:**
   The observed peak heap of Total Execution covers (is greater than or equal to every measured child-scope peak) the observed peak heaps of Dataset I/O, Preprocessing, DFS Mining, and Output I/O:
   $$\text{Peak Heap}_{\text{Total}} \ge \max\left(\text{Peak Heap}_{\text{DatasetIO}}, \text{Peak Heap}_{\text{Algorithm}}, \text{Peak Heap}_{\text{OutputIO}}\right)$$
4. **Console Exclusion:**
   Console formatting and printing take place strictly via `execute(configuration, PrintStream)` after `execute(configuration)` returns, after the sampler has closed, and totally outside `totalExecutionNanos`.

---

## 5. C13 Reconciliation

| C13 Requirement | Previous Status | C15 Evidence | Reconciled Status |
|---|:---:|---|:---:|
| Reuse Phase B measurement protocol | SATISFIED | Sampler checkpoints, phase boundaries, metric records | SATISFIED |
| Measure Dataset I/O Time (Scope 1) | PENDING | Timed around `SequenceDatabaseParser.parse` | SATISFIED |
| Measure Preprocessing Time (Scope 2) | SATISFIED | Implemented in `BitPruneMiner.mineMeasured` | SATISFIED |
| Measure DFS Mining Time (Scope 3) | SATISFIED | Implemented in `BitPruneMiner.mineMeasured` | SATISFIED |
| Measure Complete Algorithm Time (Scope 4) | SATISFIED | Implemented in `BitPruneMiner.mineMeasured` | SATISFIED |
| Measure Output I/O Time (Scope 5) | PENDING | Timed strictly around `BitPruneOutputWriter.write` | SATISFIED |
| Measure Total Execution Time (Scope 6) | PENDING | Timed inside runner try-with-resources | SATISFIED |
| Report time in milliseconds | PENDING | Console converts nanos via `BigDecimal.movePointLeft(6)` to 5 decimals | SATISFIED |
| Report memory in decimal MB | PENDING | Console formats `bytes / 1_000_000.0` with 3 decimals (SI standard) | SATISFIED |
| Sample heap using `MemoryMXBean` | SATISFIED | `ExecutionMemorySampler` background sampling thread | SATISFIED |
| Report sampling interval | PENDING | Exposes sampler interval (5 ms) in carrier and console | SATISFIED |
| Distinguish sampled peak from absolute peak | PENDING | Explicit disclaimer in memory console output and research reports | SATISFIED |
| Prevent phase-scope overlap or mislabeling | SATISFIED | Explicit nested timing boundaries and containment invariants | SATISFIED |
| Test measurement lifecycle cleanup | SATISFIED | Guaranteed close and thread join in try-with-resources | SATISFIED |
| Document GC and sampler limitations | SATISFIED | Sampled JVM heap limitation, no System.gc(), sampler close/join, no immediate reclamation claim | SATISFIED |
| Console exclusion (additional measurement requirement) | PENDING | Overload design executes console strictly after sampler shutdown | SATISFIED |

### Scientific Limitations of Measurement
- **Observed Sampled Heap vs Absolute RAM:** Memory metrics reflect periodic samples of Java heap usage obtained from `MemoryMXBean.getHeapMemoryUsage().getUsed()`. They do not represent resident set size (RSS), unmanaged native memory, JVM process overhead, or absolute hardware peak allocation.
- **Fair Comparisons:** Runtime duration numbers are valid for scientific auditing and relative comparisons under identical execution environments; no claim of general performance superiority is asserted without benchmark protocols.

**Conclusion:** All 15 roadmap C13 checklist items are fulfilled, together with the additional console-exclusion measurement requirement. Parent task **C13 is formally transitioned to `ACCEPTED`**.

---

## 6. Result, Output and Retention

- **Single Normative Model:** WSPM_BitPrune operates strictly under the `FINAL_SCIENTIFIC_RESULT` model. No runtime retention enum or configuration switch is provided.
- **Immutable Result Carrier:** [`BitPruneMiningResult`](../../src/main/java/vn/edu/wspm/bitprune/result/BitPruneMiningResult.java) retains:
  1. Frozen `PrefixTree`;
  2. Immutable list of accepted patterns in deterministic tree pre-order;
  3. Immutable `Map<Pattern, Integer>` mapping accepted patterns to exact supports;
  4. Immutable `BitPruneStatistics` candidate and pruning counters.
- **Transient Memory Isolation:** Bitmaps (`SidBitmap`), occurrence indices (`VerticalOccurrenceIndex`), endpoints, verifiers, and CMAP tables are scoped strictly to preprocessing and DFS traversal. When mining finishes and local references fall out of scope, transient structures become eligible for Garbage Collection.
- **Single Projection Generation:** [`BitPruneOutputProjection`](../../src/main/java/vn/edu/wspm/bitprune/result/BitPruneOutputProjection.java) is instantiated exactly once per successful run inside Total Execution, decoupling algorithm completion from physical disk writing.

---

## 7. Console and Flag Matrix

### Standardized Four-Section Output
[`MainWspmBitPrune.summaryLines`](../../src/main/java/vn/edu/wspm/bitprune/experiments/MainWspmBitPrune.java) formats locale-independent console lines across four distinct sections:
1. `========== INPUT CONFIGURATION ==========`
2. `========== MINING STATISTICS ==========`
3. `========== MEMORY STATISTICS (MB) ==========`
4. `========== TIME STATISTICS (ms) ==========`

### Eight-Combination Flag Matrix
Evaluated in [`MainWspmBitPruneConsoleTest`](../../src/test/java/vn/edu/wspm/bitprune/experiments/MainWspmBitPruneConsoleTest.java) across all $2^3 = 8$ combinations of `(printPatterns, printStatistics, exportPatterns)`:
- `false, false, false`: Completely silent console, no disk write, `outputIo` empty.
- `false, false, true`: Completely silent console, physical disk write, `outputIo` present.
- `false, true, false`: 4 statistics sections printed, no pattern lines, no disk write.
- `false, true, true`: 4 statistics sections printed, no pattern lines, physical disk write.
- `true, false, false`: Pattern header and projection lines printed, statistics omitted, no disk write.
- `true, false, true`: Pattern lines printed, statistics omitted, physical disk write.
- `true, true, false`: 4 statistics sections printed, followed by pattern lines, no disk write.
- `true, true, true`: 4 statistics sections printed, followed by pattern lines, physical disk write.

Under C15.3-R1, every flag combination was tested against an independent state-free reference execution, confirming that pattern identity and exact supports remain invariant across all output settings.

---

## 8. Table 1 End-to-End Oracle

Evaluated on `src/test/resources/datasets/wspm/table1.spmf` at relative minimum support `0.14`:

### Metadata Verification
- Sequence count: `7`
- Distinct item count: `8`
- Global MeanW: $\frac{12}{66} \approx 0.181818...$ (formatted as `0.18182`)
- Relative minsup: `0.14` (formatted as `0.14000`)
- Effective threshold: $7 \times 0.14 = 0.98$ (formatted as `0.98000`)
- Resolution sequence count: `7`
- Mode: `MinimumSupportMode.PERCENT`

### Exact Nine-Pattern Support Map
Matches the hard-coded independent oracle and frozen baseline:

| Pattern | Formatted Representation | Exact Support |
|---|---|:---:|
| $P_1$ | `<(1)>` | 7 |
| $P_2$ | `<(1 2)>` | 7 |
| $P_3$ | `<(1 2)(3)>` | 6 |
| $P_4$ | `<(1)(1)>` | 6 |
| $P_5$ | `<(1)(2)>` | 7 |
| $P_6$ | `<(1)(3)>` | 6 |
| $P_7$ | `<(2)>` | 7 |
| $P_8$ | `<(2)(3)>` | 6 |
| $P_9$ | `<(3)>` | 6 |

### Exact Projection Content
Deterministic pre-order output strictly using LF:
```text
<(1)> #SUP: 7
<(1 2)> #SUP: 7
<(1 2)(3)> #SUP: 6
<(1)(1)> #SUP: 6
<(1)(2)> #SUP: 7
<(1)(3)> #SUP: 6
<(2)> #SUP: 7
<(2)(3)> #SUP: 6
<(3)> #SUP: 6
```

### Baseline Differential Equality
Executed on the identical parsed database instance using [`WspmPreTreeMiner`](../../src/main/java/vn/edu/wspm/mining/WspmPreTreeMiner.java) with `ResultRetentionPolicy.SUPPORT_ONLY`:
$$\text{Baseline Supports} \equiv \text{BitPrune Supports}$$
- Total patterns: 9 in baseline, 9 in BitPrune.
- Missing patterns: 0.
- Unexpected patterns: 0.
- Support discrepancies: 0.

---

## 9. Exact Statistics Evidence

The 12 primary long counters and derived rejection counter produced by WSPM_BitPrune on Table 1 are:

| Statistic Field | Value | Meaning |
|---|:---:|---|
| `sequenceCount` | 7 | Number of sequences in the database |
| `f1Count` | 3 | Number of frequent singleton items (items 1, 2, 3) |
| `generatedExtensions` | 34 | Total structural extension candidates evaluated |
| `generatedIExtensions` | 7 | Total I-Extension candidates generated |
| `generatedSExtensions` | 27 | Total S-Extension candidates generated |
| `cmapRejected` | 0 | Candidates pruned by Tier 1 CMAP co-occurrence |
| `bitmapRejected` | 0 | Candidates pruned by Tier 2 Bitmap Upper Bound |
| `exactRejected` | 28 | Candidates rejected by Tier 3 Exact Support Verification |
| `acceptedExtensions` | 6 | Non-singleton extensions accepted into Prefix Tree |
| `rejectedExtensions` | 28 | Derived total rejections ($0 + 0 + 28 = 28$) |
| `expandedNodes` | 9 | Nodes visited during DFS tree expansion |
| `patternCount` | 9 | Final accepted weighted sequential patterns ($3 \text{ (F1)} + 6 = 9$) |
| `exactVerifications` | 34 | Total Tier 3 exact temporal verifications executed |

F1 patterns are singletons identified during preprocessing and are never counted as generated extensions, preserving C12 accounting integrity.

---

## 10. Input Format Evidence

- **SPMF Sequential:** Verified end-to-end on Table 1 (`SequenceDatabaseFormat.SPMF_SEQUENTIAL`). Sequence and itemset delimiters (`-1`, `-2`) are parsed into distinct itemsets and sequences without loss of temporal structure.
- **FIMI Transaction:** Verified on controlled transactional input (`sample.fimi`, 4 sequences/transactions, 4 distinct items, relative minsup 0.05). Verified parser routing, sequence count resolution, 6-scope timing, and output projection matching.
- **Scientific Boundary:** FIMI transaction parsing maps each transaction line to a single sequence containing exactly one itemset. This demonstrates format routing and integration consistency with `BitPruneMiner`; FIMI data is not interpreted as natural sequential data and is not used to support sequential pattern performance claims.

---

## 11. Exception Lifecycle Matrix

| Failure Scenario | Trigger Condition | Expected Exception | Propagated | Sampler Thread Closed | Filesystem State |
|---|---|---|:---:|:---:|---|
| **Malformed Parser Input** | Non-integer token (`"1 not_an_int -1 -2\n"`) | `IllegalArgumentException` (`SpmfParseException`) | Yes | Yes (0 threads leaked) | Unchanged |
| **R6 Zero-Threshold Guard** | Configured minsup `0.0` on Table 1 | `IllegalArgumentException` | Yes | Yes (0 threads leaked) | Output file not created |
| **Output Writer Failure** | Output path nested under a regular file | `IOException` | Yes | Yes (0 threads leaked) | Input dataset bit-identical |

### Lifecycle Implementation
- The sampler is constructed within a `try-with-resources` block inside `MainWspmBitPrune.execute(BitPruneRunConfiguration)`.
- Upon abnormal termination, `ExecutionMemorySampler.close()` is invoked deterministically, signaling the background sampling thread and joining it before the exception is propagated out of `execute`.
- Verified using active thread inspection: no orphan threads starting with `wspm-heap-sampler-` remain active.

---

## 12. Reproducible Execution

### Verification Commands
```bash
# 1. Full regression test suite
mvn -q test

# 2. Package executable jar
mvn -q -DskipTests package

# 3. Direct execution of standalone main
java -cp target/classes vn.edu.wspm.bitprune.experiments.MainWspmBitPrune
```

### Direct Process Evidence
- **Exit code:** `0`
- **Interactive input:** None required.
- **Pattern count:** `9`
- **Output formatting:** Emits all four standardized sections cleanly to standard output.
- **Export behavior:** No output file created under default settings (`EXPORT_PATTERNS = false`).
- **Pattern display:** Suppressed under default settings (`PRINT_PATTERNS = false`).

---

## 13. Test Evidence

The final accepted test suite comprises:

| Suite | Category / Scope | Tests Run | Failures | Errors | Skipped |
|---|---|:---:|:---:|:---:|:---:|
| `MainWspmBitPruneTimingTest` | C15.2 timing & six-scope boundaries | 14 | 0 | 0 | 0 |
| `MainWspmBitPruneConsoleTest` | C15.3 console & 8-combination flag matrix | 24 | 0 | 0 | 0 |
| `MainWspmBitPruneIntegrationTest` | C15.4 main, Table 1 oracle, lifecycle matrix | 15 | 0 | 0 | 0 |
| **C15 Combined Suite** | **All C15 tests** | **53** | **0** | **0** | **0** |
| `Table 1 Compatibility Suite` | Boundary, projection, and integration tests | 56 | 0 | 0 | 0 |
| **Repository Full Regression** | **Complete repository test suite** | **1,179** | **0** | **0** | **0** |

- **Surefire Reports:** 98 XML report files generated across all test suites.
- **Test Integrity:** 1,179 represents the total unique test executions in full regression. Suites are not summed additively where tests overlap.

---

## 14. Artifact Hash Manifest

Fresh SHA-256 hashes computed on working-tree artifacts at closure time:

> [!NOTE]
> The report's final SHA-256 is recorded externally in the closure review result to avoid a self-referential hash.

### Production Artifacts
| Artifact Path | SHA-256 Checksum |
|---|---|
| `src/main/java/vn/edu/wspm/bitprune/experiments/MainWspmBitPrune.java` | `5A0D831E0D7742CC2F6315222B38234BA6BA8E08E91B53EC5A7D48ED1DD1AED8` |
| `src/main/java/vn/edu/wspm/bitprune/experiments/BitPruneRunConfiguration.java` | `E54BC11EA77548B4249DCCC85DAD13CB187DFC370CAE1347F5163AA1223BAF48` |
| `src/main/java/vn/edu/wspm/bitprune/experiments/BitPruneExecutionRun.java` | `6C4F3F06B5F364CE9F26BD4623D1013D956CC6552938CCE0800A65E4A654C2F4` |
| `src/main/java/vn/edu/wspm/bitprune/mining/BitPruneRunMetadata.java` | `219FAE6CB63AB029C51CB11E4840A4EFA6253EE6DF843BAE85301A50295A35F7` |
| `src/main/java/vn/edu/wspm/bitprune/mining/MeasuredBitPruneRun.java` | `62FAD1A741502C242F0866F76673A406B75C2418E7BEBC46AD49EC1B2F4E0842` |
| `src/main/java/vn/edu/wspm/bitprune/mining/BitPruneMiner.java` | `849BDBB947A64F1420EC913E90C5B882E84BF2348497ABC6EAA003B0C14B85E0` |
| `src/main/java/vn/edu/wspm/bitprune/measurement/BitPruneAlgorithmMetrics.java` | `72D5BE3F543EBE0D6BBABEA109F46299BA6216954016CD95997B9CD401411D5D` |
| `src/main/java/vn/edu/wspm/bitprune/measurement/BitPruneExecutionMetrics.java` | `716F7CD44048EDB9991DD2B91EF47AAA441FE9563D84ED353294093E09642F8D` |
| `src/main/java/vn/edu/wspm/bitprune/result/BitPruneOutputProjection.java` | `A3B40329F7A80C6CDDD634F4EB82E17C82442BF9109470D793C70FDA06D17F6A` |
| `src/main/java/vn/edu/wspm/bitprune/io/BitPruneOutputWriter.java` | `92F328E24DB149002CD8FDB1B720B1744743DFF34C7C9B6AE645C37510D387DF` |

### Test Artifacts
| Artifact Path | SHA-256 Checksum |
|---|---|
| `src/test/java/vn/edu/wspm/bitprune/mining/BitPruneRunMetadataTest.java` | `A13068E6120E65D1D7A0144BE4C2B4913D3B53326FFBCA91B6864B588C16BC48` |
| `src/test/java/vn/edu/wspm/bitprune/experiments/BitPruneRunConfigurationTest.java` | `AB1F1126B786A5AB6BA5AF11E393BCFE63571029657537A3CE0E693F8A08E67D` |
| `src/test/java/vn/edu/wspm/bitprune/experiments/MainWspmBitPruneTimingTest.java` | `2EC7845E7B8C691EE4A356733E32BE5974573FE4E11B7A87514AFB0F15E437CF` |
| `src/test/java/vn/edu/wspm/bitprune/experiments/MainWspmBitPruneConsoleTest.java` | `8CC7EBA18A5AA5AA9B57E85295EB73D6CCC3D540F48C185216EAA895D4ECB62A` |
| `src/test/java/vn/edu/wspm/bitprune/experiments/MainWspmBitPruneIntegrationTest.java` | `ECDD5A8280BB207F6B11F79758816A8B5B4BBFB82AB6D364BB4E17E6D99514ED` |
| `src/test/java/vn/edu/wspm/bitprune/mining/BitPruneMeasurementIntegrationTest.java` | `5AECB2E6DAC1C6A6BB1ECC7F3A77DCE1DFCE3D493712E58C96072C7A761B1B1D` |
| `src/test/java/vn/edu/wspm/measurement/BitPruneExecutionMemoryLifecycleTest.java` | `66627727C41DFCCDF127E16716CD91440A2FD698F89FCEC8A406DC0A677AB525` |

### Frozen Baseline Reference
| Artifact Path | SHA-256 Checksum |
|---|---|
| `src/main/java/vn/edu/wspm/experiments/MainWspmPreTree.java` | `66200B07EEDB73DE6208972D2DDBFA01A60D6C0D544C74A96B63F36F6EB8A52D` |

---

## 15. Research Classification

### SOURCE-CONFIRMED
- Definitions and boundaries of the six standardized measurement scopes: Dataset I/O, Preprocessing, DFS Mining, Complete Algorithm, Output I/O, and Total Execution.
- WSPM weighted support condition: $WS(P) \ge \text{minsup}$.
- Table 1 values: 7 sequences, 8 distinct items, MeanW $\frac{12}{66}$, 9 accepted patterns at relative minsup 0.14.
- Exact support map and deterministic pre-order serialization format (`<pattern> #SUP: <support>\n`).

### IMPLEMENTATION INFERENCE
- Immutable carrier decomposition (`BitPruneExecutionRun`, `BitPruneRunConfiguration`, `BitPruneRunMetadata`, `BitPruneOutputProjection`).
- Direct source-configured constant layout in `MainWspmBitPrune` matching Phase B patterns.
- Decoupling of core execution from console rendering via overloaded `execute(...)` methods to exclude console formatting from Total Execution time.
- Conversion of relative support fractions to percentage via `BigDecimal.movePointRight(2)` to eliminate floating-point representation artifacts.

### UNSPECIFIED / UNRESOLVED
- **R6 Zero-Threshold Semantics:** R6 remains **`OPEN`**. Zero threshold execution is evaluated solely for fail-fast lifecycle testing; no mathematical support-zero pruning policy is approved.
- **Java `double` Exact-Real Comparator Safety:** Operational upper-bound pruning is verified safe relative to the frozen baseline Java `double` arithmetic; theoretical exact-real comparator equivalence remains **`UNRESOLVED`**.
- **Memory Metric Scope:** Observed heap memory reflects sampled used heap from `MemoryMXBean` and does not represent total resident memory (RSS).
- **No Performance Superiority Claim:** Execution time and memory observations validate protocol fidelity; no claim of general algorithmic speedup or memory superiority over baseline is asserted without formal benchmark protocols.
- **Controlled FIMI Dataset:** Transaction-to-sequence mapping is verified strictly for format routing consistency and is not presented as natural sequential benchmark evidence.

---

## 16. Closure Decision

1. **C13 Status:** **`ACCEPTED`** (reconciled via complete six-scope runtime evidence).
2. **C15 Status:** **`ACCEPTED`** (standalone runner, configuration model, console reporting, lifecycle matrix, and Table 1 oracle fully verified).
3. **C16 Status:** **`NOT STARTED`**.
4. **Phase C Status:** **`IN PROGRESS`** (Phase C remains in progress until C16 Final Regression & Acceptance audit is formally executed).

