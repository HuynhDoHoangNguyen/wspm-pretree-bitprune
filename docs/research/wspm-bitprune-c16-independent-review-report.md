# C16 Independent Research Review & Phase C Closure Report

**Repository:** `wspm-pretree-bitprune`  
**Review Target:** Task C16 — Final Regression & Phase C Acceptance Audit  
**Auditor:** Independent Research Reviewer, Data Mining Research Auditor & Senior Java Reviewer  
**Audit Date:** 2026-10-31 (Asia/Bangkok)  
**Branch:** `feature/wspm-bitprune`  
**HEAD Checkpoint:** `51cf01c288dc438f1f13246c2bb3315df14d508f`  
**Executive Review Decision:** **`PASS — ACCEPTED`**  
**Phase C Status:** **`COMPLETED`**  
**Phase D Status:** **`NOT STARTED`**

---

## 1. Executive Summary & Review Scope

An exhaustive, independent verification and audit has been conducted on the C16 acceptance evidence, the WSPM_BitPrune algorithmic implementation, the frozen WSPM_PreTree baseline, the independent brute-force oracle, the measurement and accounting subsystems, the standalone runner, and all research documentation.

This review was conducted strictly adhering to `AGENTS.md` and the Phase C roadmap specifications. Prior results were not taken on faith; every reported test count, Surefire XML report, hash manifest, counter value, and four-way complete pattern/support mapping was directly and independently re-evaluated in the live repository environment.

### Final Review Dispositions:
- **Task C16:** **`ACCEPTED`** (Full regression, baseline protection, differential correctness, statistics accounting, measurement protocol, retention, output, and standalone execution verified).
- **Phase C (WSPM_BitPrune Research & Implementation):** **`COMPLETED`** (All roadmap tasks C00 through C16 are formally ACCEPTED).
- **Phase D (Comparative Benchmarking & Evaluation):** **`NOT STARTED`** (Strict stop condition enforced; no benchmark or comparative claim authorized).

---

## 2. Repository Checkpoint & Integrity Audit

The review commenced and executed against the authorized repository state:

```text
Branch: feature/wspm-bitprune
HEAD Commit: 51cf01c288dc438f1f13246c2bb3315df14d508f
Commit Message: feat(bitprune): complete C13-C15 execution and measurement pipeline
Toolchain: Oracle JDK 21.0.12.1 LTS (build 21.0.12.1+1-LTS-4, 64-Bit Server VM)
Build System: Apache Maven 3.9.16
OS / Encoding: Windows 11 (amd64) / UTF-8
```

### Pre-Existing Working Tree Exception
The pre-existing modification at `src/main/java/vn/edu/wspm/experiments/MainWspmPreTree.java` was audited:
- Working-tree SHA-256: `66200B07EEDB73DE6208972D2DDBFA01A60D6C0D544C74A96B63F36F6EB8A52D` (exact match to input checkpoint).
- Diff inspection: Contains owner-directed runner constants only (SIGN.txt path and minsup settings).
- Algorithm classes: Zero baseline mining classes were modified.
- Preservation: The file was not reset, checked out, deleted, staged, or committed.

### Input Artifact Hash Verification
Fresh SHA-256 digests computed directly on the filesystem match reported values:
- `PHASE_C_WSPM_BitPrune.md`: `FD644704BC33C0C5E3DD762E683940641DBC8E9F056A2F622167A5C329CA7B6A`
- `docs/research/wspm-bitprune-c16-final-acceptance-report.md`: `A110F0E9D778FA40C81E1816FE6C396CDDFB1916E8FB4B5CBFC199BEF5CEABCD`
- `docs/research/wspm-bitprune-c15-standalone-runner-report.md`: `054347B6FA282EE23BBFC7F27C649C98D5613E0879E16AB3D1D6F38998A11105`
- `docs/research/phase-b-reproducibility.md`: `3A65CE534C5451F916CB95CB6D3FD26316261DBDA82DEA9B199D4FC0113C7FDC`
- `src/main/java/vn/edu/wspm/bitprune/mining/BitPruneMiner.java`: `849BDBB947A64F1420EC913E90C5B882E84BF2348497ABC6EAA003B0C14B85E0`
- `src/main/java/vn/edu/wspm/bitprune/experiments/MainWspmBitPrune.java`: `5A0D831E0D7742CC2F6315222B38234BA6BA8E08E91B53EC5A7D48ED1DD1AED8`
- `src/test/resources/datasets/wspm/table1.spmf`: `F7E409F982EE42E0E34C7B940496074750AD8AEB44F191397EFEDDD0653AE8F5`

---

## 3. Full Regression Evidence

The authoritative full regression command was executed:
```bash
mvn -q test
```

Direct XML parsing and aggregation across all Surefire report files in `target/surefire-reports/TEST-*.xml` yielded:

| Metric | Verified Value | Reported in C16 | Discrepancy |
|---|---:|---:|---:|
| Test Suites (XML reports) | 98 | 98 | 0 |
| Total Tests Run | 1,179 | 1,179 | 0 |
| Failures | 0 | 0 | 0 |
| Errors | 0 | 0 | 0 |
| Skipped | 0 | 0 | 0 |
| Exit Code | 0 | 0 | 0 |

No test-count regressions, newly skipped tests, or suppressed failures occurred.

---

## 4. Frozen Baseline Protection Evidence

The frozen Phase B baseline implementation was audited for research fidelity and non-modification:
1. **Algorithmic Substrate:** Source inspection confirms `WspmPreTreeMiner` executes candidate extensions strictly through `CandidateEngine`, which relies on `PatternEncoding`, `PrimalBlockCodec`, and PRISM joins `PrimeBlockJoins.iJoin` and `PrimeBlockJoins.sJoin`.
2. **No Substitution:** The baseline has NOT been substituted with Bitmaps, simple ID-lists, or positional bitmasks.
3. **Focused Baseline Tests:** Executed 157 focused baseline tests across 11 test suites:
   - `Table1FixtureTest` (6 tests)
   - `Table1EndToEndCorrectnessTest` (7 tests)
   - `WspmPreTreeMinerTest` (12 tests)
   - `WspmDifferentialCorrectnessTest` (17 tests)
   - `BruteForceWeightedPatternOracleTest` (17 tests)
   - `CandidateEngineTest` (9 tests)
   - `CandidateEngineDifferentialTest` (3 tests)
   - `PrimalBlockCodecTest` (60 tests)
   - `PrimeBlockJoinsTest` (19 tests)
   - `PrimeBlockJoinDifferentialTest` (4 tests)
   - `SingletonEncodingBuilderTest` (2 tests)
   - Result: **157 / 157 PASS (0 failures, 0 errors, 0 skipped)**.

---

## 5. Table 1 Four-Way Complete-Map Oracle Evidence

The Table 1 benchmark from Pham et al. (2023) was verified directly against the parsed dataset fixture `src/test/resources/datasets/wspm/table1.spmf`:
- Sequence count $|D|$: 7
- Distinct items: 8 (items 1 through 8)
- Occurrence counts: 23, 12, 11, 5, 4, 4, 6, 1 (total database length = 66)
- Weights: 23/66, 12/66, 11/66, 5/66, 4/66, 4/66, 6/66, 1/66
- $\text{MaxW} = 23/66$, $\text{MinW} = 1/66$, $\text{MeanW} = (23/66 + 1/66) / 2 = 12/66$
- Minimum support input: 0.14 (relative fraction, representing 14%)
- Effective threshold: $0.14 \times 7 = 0.98$
- Frequent 1-patterns (F1): $\langle(1)\rangle: 7$, $\langle(2)\rangle: 7$, $\langle(3)\rangle: 6$
- Total weighted sequential patterns: exactly 9

### Complete Mapping Equality
Four independent oracles were evaluated:
1. Published paper oracle (Pham et al., 2023, Table 1 / Figure 2)
2. Frozen WSPM_PreTree baseline (`WspmPreTreeMiner`)
3. Test-only brute-force oracle (`BruteForceWeightedPatternOracle`)
4. Proposed WSPM_BitPrune (`BitPruneMiner`)

The complete resulting map $\text{Map}\langle\text{Pattern}, \text{Integer}\rangle$ across all 4 oracles is strictly identical:

```text
<(1)>      -> 7
<(2)>      -> 7
<(3)>      -> 6
<(1 2)>    -> 7
<(1)(1)>   -> 6
<(1)(2)>   -> 7
<(1)(3)>   -> 6
<(2)(3)>   -> 6
<(1 2)(3)> -> 6
```

Pairwise map comparison:
- `Paper == Baseline`: 9/9 equal, 0 missing, 0 unexpected, 0 support mismatches
- `Paper == BruteForce`: 9/9 equal, 0 missing, 0 unexpected, 0 support mismatches
- `Paper == BitPrune`: 9/9 equal, 0 missing, 0 unexpected, 0 support mismatches
- `Baseline == BitPrune`: 9/9 equal, 0 missing, 0 unexpected, 0 support mismatches
- `BruteForce == BitPrune`: 9/9 equal, 0 missing, 0 unexpected, 0 support mismatches

Prefix tree integrity: `bitPruneTree.isFrozen() == true`, node count = 10 (root + 9 patterns), pre-order traversal deterministic across repeated runs.

---

## 6. Differential Correctness & Pruning Safety Evidence

The dedicated differential test suite (130 tests across 11 Surefire test classes) was executed in isolation:

| Test Class | Scope / Catalogue | Verified Counts | Mismatches / False Negatives |
|---|---|---|:---:|
| `CmapDifferentialTest` | Seed `20261028L`, 1,200 trials | 4,399 CMAP-I, 11,581 CMAP-S | 0 FP, 0 FN |
| `BitmapUpperBoundFilterDifferentialTest` | Seed `20261029L`, 1,200 trials | 45,200 comparisons, 20,000 prunes, 11,300 zero no-ops | 0 FN (baseline-relative) |
| `ExactTemporalVerifierIExtensionDifferentialTest` | Seed `20261030L`, 1,200 trials | 4,096 mask comps, 121 canonical pairs | 0 mismatches |
| `ExactTemporalVerifierSExtensionDifferentialTest` | Seed `20261031L`, 1,200 trials | 4,096 mask comps, 183 structural pairs | 0 mismatches |
| `BitPruneCandidateEngineDifferentialTest` | Seed `20261101L`, 1,500 trials | 304 parents × 64 F1 (19,456 comparisons) | 0 mismatches |
| `BitPruneCandidatePrefixTreeDifferentialTest` | Seed `20261102L`, 1,500 trials | 9,540 compatible tree comparisons | 0 mismatches |
| `BitPruneCandidateEvaluatorDifferentialTest` | Seed `20261103L`, 1,500 trials | 1,836 bounded exhaustive comparisons | 0 mismatches |
| `BitPruneControlledDifferentialCorrectnessTest` | 9 database families, 13 configs | 13 three-way complete maps | 0 unexplained mismatches |
| `BitPruneBoundedExhaustiveDifferentialTest` | 156 databases, 312 configs | 936 pairwise complete maps, 1,916 patterns | 0 missing, 0 unexpected, 0 support mismatches |
| `BitPruneFixedSeedRandomizedDifferentialTest` | Seed `20261104L`, 1,200 trials | 3,600 pairwise complete maps, 16,283 patterns (604 abs, 596 pct) | 0 missing, 0 unexpected, 0 support mismatches |
| `BitPruneTable1ThreeOracleDifferentialTest` | Table 1 paper fixture | 4-way complete map equality | 0 mismatches |

### Pruning Safety Classification:
1. **CMAP-I & CMAP-S (Tier 1):** Necessary-condition pruning. Rejects candidates only when required intra-itemset or ordered inter-itemset co-occurrence is impossible. Zero false negatives observed.
2. **Bitmap Cardinality Upper Bound (Tier 2):** Monotone upper-bound pruning. Proved operationally safe relative to the frozen baseline Java `double` acceptance predicate. Zero baseline-relative false negatives observed.
3. **Exact Temporal Verification (Tier 3):** Exact distinct-SID verification. Terminal outcomes (EXACT_REJECTED or ACCEPTED) are evaluated strictly from structural temporal endpoints.
4. **First-Failure Routing:** Evaluator routes candidates immediately upon the first failing tier without double-counting. Bitmap and CMAP never substitute for exact temporal verification when temporal order remains unresolved.

---

## 7. Statistics Accounting & Invariants Evidence

The statistics accounting subsystem (`BitPruneStatistics`, `BitPruneStatisticsAccumulator`, `BitPruneMiningResult`) was audited (110 tests across 4 suites PASS):
- Holds exactly 12 primary non-negative `long` counters:
  `sequenceCount`, `f1Count`, `generatedExtensions`, `generatedIExtensions`, `generatedSExtensions`, `cmapRejected`, `bitmapRejected`, `exactRejected`, `acceptedExtensions`, `expandedNodes`, `patternCount`, `exactVerifications`.
- Convenience derived statistic: `rejectedExtensions = cmapRejected + bitmapRejected + exactRejected`.
- Mathematical conservation identities enforced via `Math.addExact` at constructor boundary:
  1. $\text{generatedExtensions} = \text{generatedIExtensions} + \text{generatedSExtensions}$
  2. $\text{generatedExtensions} = \text{cmapRejected} + \text{bitmapRejected} + \text{exactRejected} + \text{acceptedExtensions}$
  3. $\text{exactVerifications} = \text{exactRejected} + \text{acceptedExtensions}$
  4. $\text{patternCount} = \text{f1Count} + \text{acceptedExtensions}$
- Controlled exact-vector test cases confirm each pruning tier increments only its mutually exclusive outcome counter.

---

## 8. Six-Scope Measurement Parity Evidence

All six standardized measurement scopes are verified operational:
1. **Scope 1 — Dataset I/O:** Measures parsing and database construction.
2. **Scope 2 — Preprocessing:** Measures item statistics, weights, F1, vertical index, and CMAP construction.
3. **Scope 3 — DFS Mining:** Measures recursive DFS expansion, candidate generation, pruning, and verification.
4. **Scope 4 — Complete Algorithm:** Measures Scope 2 + Scope 3 continuously.
5. **Scope 5 — Output I/O:** Measures disk serialization and pattern writing (0.0 ms when disabled).
6. **Scope 6 — Total Execution:** Measures Scope 1 + Scope 4 + Scope 5 continuously. Excludes console formatting and terminal printing.

### Instrumentation Rules:
- Timing is captured via `System.nanoTime()` and formatted in milliseconds.
- Memory is sampled via `MemoryMXBean.getHeapMemoryUsage().getUsed()` at 5 ms intervals and reported in decimal megabytes (`bytes / 1_000_000.0`).
- No `System.gc()` call is made.
- Sampler is managed via try-with-resources with guaranteed cleanup on normal and exceptional paths (0 leaked threads).
- Sampled Java heap is documented strictly as sampled JVM heap, not OS process RSS or hardware RAM peak.

---

## 9. Output, Retention, and Standalone Runner Evidence

### Result Retention & Output
- **Model:** Single Owner-approved `FINAL_SCIENTIFIC_RESULT` model in `BitPruneMiningResult`.
- **Retained State:** Frozen `PrefixTree`, immutable `acceptedPatterns` in deterministic pre-order, immutable exact `Map<Pattern, Integer>`, and immutable `BitPruneStatistics`.
- **Transient State Dropped:** Bitmaps, vertical index, endpoint states, CMAP tables, DFS stack frames, and samplers are unreferenced by the result carrier.
- **Writer:** `BitPruneOutputWriter` produces UTF-8, LF line delimiters, exactly one trailing LF, handles create/truncate, parent directory creation, and propagates raw `IOException`.
- **Flag Matrix:** The full $2^3 = 8$ flag combinations (`printPatterns`, `printStatistics`, `exportPatterns`) preserve identical mining results without side-effects.

### Standalone Runner
Executed packaged standalone runner:
```bash
mvn -q -DskipTests package
java -cp target/classes vn.edu.wspm.bitprune.experiments.MainWspmBitPrune
```
- Exit code: 0
- Noninteractive: No `Scanner`, no prompt, no `System.in`.
- Default dataset: Table 1 (`src/test/resources/datasets/wspm/table1.spmf`, minsup 0.14).
- Output: 4 formatted console sections (Configuration, Mining Statistics, Memory Statistics, Time Statistics).
- Export disabled: Verified `output/wspm-bitprune-patterns.txt` was NOT created.
- Thread safety: Sampler thread terminated cleanly before process exit.

---

## 10. Research Issue Tracker Reconciliation

The canonical research issue tracker statuses are strictly preserved:

| Issue | Status | Review Auditor Determination |
|---|---|---|
| **R1 — Sequence-Weight Discrepancy** | **OPEN** | Preserved. Baseline and BitPrune preserve sequence weight definitions. |
| **R2 — Percentage Minimum Support** | **RESOLVED FOR IMPLEMENTATION** | Preserved. Unrounded $(p/100) \times N$ conversion verified. |
| **R3 — Prime-Block Mechanics** | **RESOLVED** | Preserved. Frozen baseline PRISM joins verified intact. |
| **R4 — Complete Table 1 Oracle** | **RESOLVED** | Preserved. 9/9 complete-map four-way equality verified. |
| **R5 — Item-Weight Denominator** | **RESOLVED** | Preserved. Denominator = total database length (66 for Table 1). |
| **R6 — Zero Minimum Support Semantics** | **OPEN** | Preserved. Zero effective threshold remains rejected operationally; excluded from acceptance scope. |
| **R7 — Transaction-to-Sequence Mapping** | **OPEN** | Preserved. Controlled FIMI routing only; not natural sequential benchmark evidence. |
| **R8 — SIGN Paper Benchmark Reproduction Discrepancy** | **OPEN** | Preserved. 315 vs 56 paper discrepancy documented; no paper benchmark equivalence claimed. |
| **Floating-Point Comparator Safety** | **UNRESOLVED** | Preserved. Operational safety proved relative to baseline Java `double` only; no exact-real equivalence claimed; no epsilon added. |

---

## 11. Acceptance Checklist Reconciliation

### Task C16 Checklist:
- [x] Execute full Maven regression.
- [x] Recheck the frozen baseline correctness tests.
- [x] Execute full BitPrune correctness tests.
- [x] Execute baseline-versus-BitPrune differential tests.
- [x] Verify Table 1.
- [x] Confirm pruning safety evidence.
- [x] Confirm measurement semantics.
- [x] Confirm output and retention behavior.
- [x] Confirm no forbidden baseline modifications.
- [x] Review Git diff and ignored dataset policy.
- [x] Document known limitations.
- [x] Preserve statuses R1–R8 unless separately authorized.
- [x] Create Phase C acceptance report.
- [x] Establish readiness for Phase D.

### Final Phase C Acceptance Checklist:
- [x] Phase B freeze reference verified.
- [x] Research-source audit complete.
- [x] Mathematical pruning safety established.
- [x] Bitmap representation correct.
- [x] Vertical temporal index correct.
- [x] CMAP-I and CMAP-S correct.
- [x] Weighted upper-bound pruning correct.
- [x] Exact I-extension correct.
- [x] Exact S-extension correct.
- [x] Prefix Tree candidate generation correct.
- [x] DFS mining correct and complete on the accepted positive-threshold controlled scope.
- [x] Full differential correctness passed.
- [x] No known false pruning.
- [x] Statistics accounting verified.
- [x] Time/memory measurement compatible with Phase B.
- [x] Output and retention semantics verified.
- [x] Standalone Java Main verified.
- [x] Full regression completed.
- [x] Baseline protected.
- [x] Phase C Final Acceptance Report reviewed.

---

## 12. Phase C Closure Decision

The Independent Research Reviewer and Data Mining Research Auditor formally concludes:

1. **Task C16 is `ACCEPTED`.**
2. **Phase C is `COMPLETED`.**
3. **Repository Owner acceptance has been provided to proceed with closure.**
4. **Phase D remains `NOT STARTED`.**

### STOP Confirmation:
Phase D comparative benchmarking, performance claims, and paper reproduction experiments must **NOT** begin automatically. Phase D requires a dedicated benchmark protocol, environment standardization, and execution authorization.

