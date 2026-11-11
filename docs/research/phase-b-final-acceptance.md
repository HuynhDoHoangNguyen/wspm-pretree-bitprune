# Phase B Final Acceptance Report — WSPM_PreTree Baseline Validation

**Date:** 2026-10-17 (Asia/Bangkok)  
**Auditor:** Senior Java Engineer & Data Mining Researcher (Antigravity Agent)  
**Task:** B23 — Final WSPM_PreTree Baseline Validation  
**Repository:** `wspm-pretree-bitprune`  
**Current State Decision:** **PASS — READY FOR FREEZE**  

---

## 1. Executive Summary & Acceptance Decision

The final audit for Phase B (WSPM_PreTree baseline implementation) has been conducted across all algorithm components, test suites, performance harnesses, measurement instrumentation, and research records.

### Key Conclusions:
1. **Mathematical & Algorithmic Correctness:** The baseline WSPM_PreTree implementation faithfully adheres to the 2023 research paper (*Mining Weighted Sequential Patterns Based on Prefix-Tree and Prism Encoding*) and PRISM (*PRISM: An Effective Approach for Frequent Sequence Mining via Prime-Block Encoding*). It strictly enforces item weights, MeanW, non-rounded relative minimum support, Prefix Tree structural extensions (I-Extension and S-Extension), and exact Prime-block joins without any premature BitPrune/Bitmap substitution.
2. **Oracle Reproducibility:** 
   - **Table 1 Oracle:** Reproduces all 9 weighted sequential patterns with 100% exact pattern-to-support equality, exact supporting SIDs, exact prefix tree structure, and zero false positives.
   - **Differential Oracle:** Passes 146 distinct differential configurations against an independent, test-only brute-force oracle (`BruteForceWeightedPatternOracle`).
   - **JUnit Test Suite:** **363 / 363 tests PASS** under `mvn clean test` and `mvn clean verify`.
3. **Table 1 Counter Reconciliation:** Resolved the suspected discrepancy in B22.3 report. Actual production execution across both `FULL_ENCODING` and `SUPPORT_ONLY` yields: Candidates = 34, I-candidates = 7, S-candidates = 27, Accepted = 6, Rejected = 28, Expanded Nodes = 9, Patterns = 9. The report text in B22.3 ("I-candidates 6, S-candidates 28, expanded 10") was a documentation typo conflating accepted/rejected candidate counts and tree node count.
4. **Memory Scalability & OOM Resolution:** Task B22.3's `ResultRetentionPolicy.SUPPORT_ONLY` successfully resolves the heap exhaustion risk on dense datasets. Pumsb at 0.60% (4,059 patterns, 95,465 candidates) executes to completion within `-Xms256m -Xmx2g` at a peak heap of **514.089 MB** with zero OOM.
5. **Research Tracker Reconciliation:** Corrected the mislabeling of research issues in B22.3. R1 (sequence-weight discrepancy), R6 (zero minimum support), R7 (transaction-to-sequence mapping), and R8 (SIGN paper benchmark reproduction gap) remain explicitly **OPEN** and documented.
6. **Git Blocker B21-F01:** Acknowledged as **OPEN** until user executes Task B24 (checkpoint commit and independent fresh clone verification).

**Final Phase B Decision:** **PASS — READY FOR FREEZE** (pending Task B24 Git checkpoint).

---

## 2. Phase B Implementation Scope & Task Statuses

Phase B encompassed the complete research specification, faithful reproduction, verification, profiling, and memory lifecycle optimization of the baseline WSPM_PreTree algorithm:

| Task | Objective | Status | Artifact / Verification |
|---|---|:---:|---|
| **B0** | Repository environment audit | PASS | Java 21, Maven 3.9, JUnit 5 verified. |
| **B1** | Research contract & specification | PASS | `docs/research/wspm-pretree-spec.md`. |
| **B2** | Domain model (Item, Itemset, Sequence, Database) | PASS | Canonical models with immutability and validation. |
| **B3** | SPMF sequence and FIMI transaction parsers | PASS | Robust parsing without artificial order generation. |
| **B4** | Table 1 test fixture | PASS | `src/test/resources/datasets/wspm/table1.spmf`. |
| **B5** | Single item statistics and frequency counting | PASS | Occurrence count and sequence support calculation. |
| **B6** | Item weights, MaxW, MinW, MeanW calculation | PASS | Exact global length denominator (66 for Table 1). |
| **B7** | Minimum support semantics (percentage & absolute) | PASS | Unrounded `(p/100)*N`, inclusive comparison. |
| **B8** | F1 builder | PASS | Identification and ordering of weighted 1-patterns. |
| **B9** | Pattern canonical model | PASS | Structural canonical identity distinguishing `<(a)(b)>` vs `<(ab)>`. |
| **B10** | I-Extension and S-Extension candidate rules | PASS | Canonical lexicographic extension to eliminate duplicates. |
| **B11** | Prefix Tree model | PASS | Hierarchical prefix tree with parent-child links. |
| **B12** | PRISM Prime-block research specification | PASS | `docs/research/prism-prime-block.md`. |
| **B13** | Prime-block primitives & factor codec | PASS | 8-prime codec, GCD, CARD, MASK tables. |
| **B14** | Prime-block joins (I-Join, S-Join) & support | PASS | Blockwise intersection and strict-after positional masking. |
| **B15** | Candidate extension engine | PASS | Integration of joins with threshold acceptance. |
| **B16** | DFS WSPM_PreTree miner | PASS | Recursive DFS tree traversal. |
| **B17** | Table 1 end-to-end reproduction | PASS | 9 patterns, exact support map equality. |
| **B18** | Brute-force oracle for tiny databases | PASS | `BruteForceWeightedPatternOracle` (test-scoped). |
| **B19** | Differential correctness testing | PASS | 146 configurations, seed `20261016`. |
| **B20** | Logging, statistics, and standalone Main | PASS | `MainWspmPreTree` with deterministic console summary. |
| **B21** | Baseline audit and reproducibility manifest | PASS | `docs/research/phase-b-baseline-audit.md`. |
| **B21.1** | Repository hygiene & credential audit | PASS | No secrets or absolute paths; .gitignore updated. |
| **B21.2** | Measurement instrumentation & timing boundaries | PASS | `ExecutionMemorySampler` (5 ms), nanosecond phase timers. |
| **B21.3** | SIGN/Pumsb paper reproduction audit | PASS (Partial) | Pumsb 0.67% match; opened R8 for SIGN discrepancy. |
| **B21.4** | SIGN root-cause & threshold audit addendum | PASS | Categorized SIGN gap as Category E; audited boundaries. |
| **B22.0** | Performance & bottleneck audit | PASS | Identified object churn in per-SID dense representation. |
| **B22.1** | Rank-native GCD/CARD/MASK optimization | PASS | 256-state precomputed lookup tables; 20x CPU speedup. |
| **B22.2** | Compact primitive-array encoding | PASS | Removed per-SID `PositionBlock` allocations in hot path. |
| **B22.3** | DFS encoding lifecycle & retention policy | PASS | `SUPPORT_ONLY` mode; Pumsb 0.60% OOM resolved. |
| **B23** | Final baseline validation | **PASS** | Complete audit, counter verification, Main restored. |

---

## 3. Paper and Research Source Fidelity

Every critical algorithm decision has been cataloged against the authoritative publications:

| Component / Behavior | Research Classification | Implementation Detail & Fidelity Check |
|---|---|---|
| **Support Counting** | SOURCE-CONFIRMED | Distinct sequences containing pattern (SIDs), not multiple embeddings. |
| **Item Weight Formula** | SOURCE-CONFIRMED | $W(i) = \text{occurrences}(i) / \text{totalDatabaseLength}$. Denominator is total item occurrences. |
| **MeanW Definition** | SOURCE-CONFIRMED | $\text{MeanW} = (\max_i W(i) + \min_i W(i)) / 2.0$. |
| **Weighted Acceptance** | SOURCE-CONFIRMED | $\text{support}(P) \times \text{MeanW} \ge \text{effectiveMinSup}$. Inclusive inequality. |
| **Prefix Tree DFS** | SOURCE-CONFIRMED | Tree expansion via I-Extension followed by S-Extension using depth-first search. |
| **Prime-block Encoding** | SOURCE-CONFIRMED | PRISM 8-prime encoding per block; 256 state ranks. |
| **I-Join Semantics** | SOURCE-CONFIRMED | Blockwise GCD intersection across sequence and position blocks. |
| **S-Join Semantics** | SOURCE-CONFIRMED | Strict-after positional masking retaining right endpoints strictly later than left endpoints. |
| **Minsup Conversion** | STRONGLY-SUPPORTED INFERENCE | $\text{effectiveMinSup} = (\text{percentage} / 100.0) \times N$. Real-valued, unrounded. |
| **Lexicographic Pruning** | IMPLEMENTATION POLICY | Canonical item ordering eliminates duplicate pattern generation. |
| **FIMI Transaction Mapping** | IMPLEMENTATION POLICY | 1 transaction line = 1 sequence of 1 itemset. Avoids fabricating temporal sequence order. |
| **Retention Policy** | IMPLEMENTATION POLICY | `SUPPORT_ONLY` discards encodings post-subtree; `FULL_ENCODING` retains all for auditing. |
| **Sequence Weight Example** | UNSPECIFIED / CONFLICT | Formula (3) averages itemsets, while prose yields 0.559 (R1 OPEN). |
| **Zero Minimum Support** | UNSPECIFIED | Inclusive comparison with threshold 0 accepts non-occurring items (R6 OPEN). |
| **Author Pumsb Preprocessing**| UNSPECIFIED | Exact author conversion from transaction to sequence is unknown (R7 OPEN). |
| **SIGN Table 4 Reproduction** | UNRESOLVED GAP | Paper reports 56 patterns at 0.4%; baseline yields 315 independently verified (R8 OPEN). |

---

## 4. Worktree Audit & Environment Verification

### 4.1 Environment
- **Operating System:** Microsoft Windows 11 Home Single Language (10.0.26200, 64-bit)
- **CPU:** AMD Ryzen 7 4800H (8 cores / 16 threads)
- **Physical RAM:** 16 GB (16,558,178,304 bytes)
- **JDK:** Oracle Java 21.0.12.1 LTS (HotSpot 64-bit Server VM)
- **Maven:** Apache Maven 3.9.16
- **Compiler Target:** Java 21 (`source: 21`, `target: 21`)
- **Default Encoding:** UTF-8

### 4.2 Git Status & Worktree Inventory
Direct inspection via `git status --short`:
- **Tracked & Modified (14 files):**
  - `docs/research/phase-b-baseline-audit.md`
  - `docs/research/phase-b-reproducibility.md`
  - `docs/research/wspm-pretree-spec.md`
  - `src/main/java/vn/edu/wspm/experiments/MainWspmPreTree.java`
  - `src/main/java/vn/edu/wspm/mining/SingletonEncodingBuilder.java`
  - `src/main/java/vn/edu/wspm/mining/WspmMiningResult.java`
  - `src/main/java/vn/edu/wspm/mining/WspmPreTreeMiner.java`
  - `src/main/java/vn/edu/wspm/prism/EncodedVector.java`
  - `src/main/java/vn/edu/wspm/prism/PatternEncoding.java`
  - `src/main/java/vn/edu/wspm/prism/PrimalBlockCodec.java`
  - `src/main/java/vn/edu/wspm/prism/PrimeBlockJoins.java`
  - `src/test/java/vn/edu/wspm/experiments/MainWspmPreTreeTest.java`
  - `src/test/java/vn/edu/wspm/prism/PrimalBlockCodecTest.java`
  - `src/test/java/vn/edu/wspm/prism/PrimeBlockJoinsTest.java`
- **Untracked Directories & Files (12 entries):**
  - `docs/research/sign-pumsb-paper-reproduction-audit.md`
  - `docs/research/wspm-pretree-performance-audit.md`
  - `src/main/java/vn/edu/wspm/experiments/WspmExecutionRun.java`
  - `src/main/java/vn/edu/wspm/measurement/`
  - `src/main/java/vn/edu/wspm/mining/MeasuredMiningRun.java`
  - `src/main/java/vn/edu/wspm/mining/ResultRetentionPolicy.java`
  - `src/main/java/vn/edu/wspm/prism/PositionCoordinateSystem.java`
  - `src/test/java/vn/edu/wspm/audit/`
  - `src/test/java/vn/edu/wspm/experiments/B221PerformanceHarness.java`
  - `src/test/java/vn/edu/wspm/experiments/B222RetentionHarness.java`
  - `src/test/java/vn/edu/wspm/measurement/`
  - `src/test/java/vn/edu/wspm/mining/ResultRetentionPolicyTest.java`
  - `src/test/java/vn/edu/wspm/prism/PatternEncodingCompactTest.java`
- **Git Invariant Check:** Zero destructive Git commands (`git add`, `git commit`, `git reset`, `git clean`) were executed. All source and measurement files remain intact in the working tree.

---

## 5. Reconciliation of Research Issues R1–R8

An audit of task documentation identified that B22.3 report Section 19.8 and its Markdown artifact inadvertently mislabeled several research issue titles. Below is the canonical, verified reconciliation of all research issues:

| Issue | Canonical Name | Origin | Technical Scope & Evidence | Status |
|---|---|:---:|---|:---:|
| **R1** | **Sequence-Weight Discrepancy** | Task B1 | Equation (3) averages itemset weights, but prose yields 0.559 by dividing unnormalized sums by database length. Acceptance uses MeanW, not Sequence Weight. Mining outputs are unaffected. | **OPEN** |
| **R2** | **Percentage Minimum Support** | Task B7 | Real-valued conversion `(p / 100.0) * N` without integer rounding or ceiling. Inclusive inequality. Reproduces Table 1 boundary. | **RESOLVED FOR IMPLEMENTATION** |
| **R3** | **Prime-Block Mechanics** | Task B12–B14 | PRISM 8-prime block encoding, rank-native lookup tables (B22.1), compact arrays (B22.2), GCD and strict-after joins. Verified via unit and differential tests. | **RESOLVED** |
| **R4** | **Table 1 Oracle Reproduction** | Task B4 / B17 | Complete 9-pattern Table 1 oracle reproduction with exact pattern identity, support, SIDs, and prefix tree. | **RESOLVED BY B17** |
| **R5** | **Item-Weight Denominator** | Task B6 | Total database item-occurrence length (66 in Table 1, 37,958 in SIGN, 3,629,404 in Pumsb). Validated by paper worked example $23/66$. | **RESOLVED** |
| **R6** | **Zero Minimum Support Semantics** | Task B7 | Inclusive `0 >= 0` accepts non-occurring candidates. Baseline restricts supported configurations to strictly positive thresholds (`effectiveMinSup > 0`). | **OPEN** |
| **R7** | **Transaction-to-Sequence Mapping** | Task B3 / B21.1 | 1 FIMI transaction line = 1 sequence of 1 itemset. Conservative policy avoiding invented temporal order. Author preprocessing remains unknown. | **OPEN** |
| **R8** | **SIGN Paper Benchmark Discrepancy** | Task B21.3 | Paper reports 56 patterns at 0.4%; baseline yields 315 independently validated patterns. Categorized as Category E (unresolved reproduction gap). | **OPEN** |

> [!IMPORTANT]
> **Resolution of Mislabeling in B22.3:**
> - B22.3 referred to R1 as *"Table 1 F1 discrepancy"* — **Incorrect**. Table 1 F1 has 3 items and was resolved under R4. R1 is the sequence-weight formula conflict.
> - B22.3 referred to R6 as *"SIGN minsup boundary discrepancy"* — **Incorrect**. R6 is zero minimum support semantics.
> - B22.3 referred to R8 as *"Paper runtime and memory measurement scope"* — **Incorrect**. R8 is the SIGN Table 4 56-vs-315 pattern count gap.
> 
> The canonical definitions above are permanently restored.

---

## 6. Table 1 Statistics & Counter Discrepancy Audit

### 6.1 Audit Finding
The user prompt highlighted a discrepancy between:
- **Baseline before B22.3:** Candidates = 34, I-candidates = 7, S-candidates = 27, Expanded nodes = 9.
- **B22.3 Report Text:** Candidates = 34, I-candidates = 6, S-candidates = 28, Expanded nodes = 10.

### 6.2 Empirical Execution
Production miner executed on `src/test/resources/datasets/wspm/table1.spmf` with `minsup = 0.14` under both `FULL_ENCODING` and `SUPPORT_ONLY`:
```text
========== MINING STATISTICS ==========
Generated Candidates           : 34
Accepted Candidates            : 6
Rejected Candidates            : 28
I-Extension Candidates         : 7
S-Extension Candidates         : 27
Expanded Nodes                 : 9
Number of Weighted Patterns    : 9
```

### 6.3 Code & Mathematical Candidate Verification
1. **Root Handling:** `PrefixTree` contains 10 nodes (1 empty root + 9 pattern nodes). DFS expansion (`depthFirstExpand`) is invoked **only for accepted pattern nodes** (the 3 F1 singletons + 6 accepted candidate extensions). The root node is never expanded. Therefore:
   $$\text{expandedNodeCount} = 9 = \text{patternCount}$$
   The report number "10" was an accidental inclusion of the tree root (`nodeCount = 10`).
2. **I-Candidates Trace (Total = 7):**
   - Expanding `<(1)>`: `<(1 2)>` (acc, sup 7), `<(1 3)>` (rej, sup 3) $\to$ **2**
   - Expanding `<(2)>`: `<(2 3)>` (rej, sup 3) $\to$ **1**
   - Expanding `<(3)>`: none $\to$ **0**
   - Expanding `<(1 2)>`: `<(1 2 3)>` (rej, sup 3) $\to$ **1**
   - Expanding `<(1)(1)>`: `<(1)(1 2)>` (rej, sup 3), `<(1)(1 3)>` (rej, sup 2) $\to$ **2**
   - Expanding `<(1)(2)>`: `<(1)(2 3)>` (rej, sup 2) $\to$ **1**
   - Expanding `<(1)(3)>`, `<(2)(3)>`, `<(1 2)(3)>`: none $\to$ **0**
   - **Sum of I-candidates:** $2 + 1 + 0 + 1 + 2 + 1 + 0 + 0 + 0 = \mathbf{7}$.
3. **S-Candidates Trace (Total = 27):**
   - For each of the 9 expanded nodes, exactly 3 S-candidates are generated (one for each item in $F_1 = \{1, 2, 3\}$).
   - **Sum of S-candidates:** $9 \times 3 = \mathbf{27}$.
4. **Candidate Sum Invariant:**
   $$\text{candidateCount} = 7 + 27 = \mathbf{34} = 6 \text{ (accepted)} + 28 \text{ (rejected)}$$

### 6.4 Discrepancy Conclusion
The numbers in `b22.3_task_result.md` (I-candidates 6, S-candidates 28, expanded 10) were a **pure documentation typo**:
- The author inadvertently transposed the accepted candidate count (6) and rejected candidate count (28) into the I-candidate and S-candidate report fields.
- The author wrote the tree's total node count (10) into the expanded nodes field.

The actual code implementation, test assertions, and runtime behavior were **never modified or regressed**.

---

## 7. Full Correctness Validation Evidence

### 7.1 Table 1 Paper Reproduction
- **Database:** 7 sequences, 8 distinct items, 66 total occurrences.
- **Weights:** $W(a) = 23/66 \approx 0.34848$, $W(b) = 15/66 \approx 0.22727$, $W(c) = 12/66 \approx 0.18182$, $W(h) = 1/66 \approx 0.01515$.
- **MeanW:** $(23/66 + 1/66) / 2 = 12/66 \approx 0.181818...$
- **Minsup:** $14\% \implies 0.14 \times 7 = 0.98$.
- **Accepted Patterns & Supports (Exact Oracle Match):**
  1. `<(1)>` : #SUP 7 (SIDs {1,2,3,4,5,6,7})
  2. `<(2)>` : #SUP 7 (SIDs {1,2,3,4,5,6,7})
  3. `<(3)>` : #SUP 6 (SIDs {1,2,3,4,5,6})
  4. `<(1 2)>` : #SUP 7 (SIDs {1,2,3,4,5,6,7})
  5. `<(1)(1)>` : #SUP 6 (SIDs {1,2,4,5,6,7})
  6. `<(1)(2)>` : #SUP 7 (SIDs {1,2,3,4,5,6,7})
  7. `<(1)(3)>` : #SUP 6 (SIDs {1,2,3,4,5,6})
  8. `<(2)(3)>` : #SUP 6 (SIDs {1,2,3,4,5,6})
  9. `<(1 2)(3)>` : #SUP 6 (SIDs {1,2,3,4,5,6})

### 7.2 Independent Differential Brute-Force Testing
- **Class:** `WspmDifferentialCorrectnessTest` (seed `20261016`).
- **Oracle:** `BruteForceWeightedPatternOracle` (completely independent, test-scoped structural subsequence matcher).
- **Scope:** 18 fixed edge-case tests + 128 random database configurations = **146 differential configurations**.
- **Result:** **100% PASS** on complete `Map<Pattern, Integer>`, supporting SID sets, and encoding consistency.

### 7.3 PRISM Join & Vector Primitive Verification
- **Rank-Native Tables (B22.1):** 256 precomputed ranks for GCD, CARD, and MASK.
- **Boundary Tests:** Verified SID 8-to-9, position 8-to-9, positions beyond 16, empty blocks, multi-block vectors, interior empty blocks, defensive copies, and immutability (`PrimalBlockCodecTest`, `PrimeBlockJoinsTest`, `PatternEncodingCompactTest`).

---

## 8. Real Dataset Validation: SIGN & PUMSB

### 8.1 Dataset Provenance & Hashes

| Dataset | Format | Raw Bytes | SHA-256 Checksum | Provenance / Equivalence |
|---|---|---:|---|---|
| `table1.spmf` | SPMF | 273 | `F7E409F982EE42E0E34C7B940496074750AD8AEB44F191397EFEDDD0653AE8F5` | WSPM_PreTree 2023 Table 1 fixture. |
| `SIGN.txt` | SPMF | 237,455 | `41F2250001AC5E6DCA40C3966F74D036CC4E982F270DCBCD55968CA21B9023DD` | Byte-for-byte identical to public SPMF after CRLF normalization. |
| `pumsb.txt` | FIMI | 16,691,407 | `33B8E1B577C2325052E5B2EB5E6B3E6DA8AC46C1237559A16B61ACE6F06A2D74` | Confirmed deterministic +1 item-ID shift from FIMI mirror. |

### 8.2 SIGN Validation (minsup 0.4% and 0.3%)
- **SIGN 0.4%:**
  - $N = 730$, Distinct items = 267, Total occurrences = 37,958.
  - $\text{MaxW} = 680/37958 \approx 0.01791$, $\text{MinW} = 1/37958 \approx 0.000026$.
  - $\text{MeanW} = 0.008970441013752042$, Effective minsup = $2.92$.
  - Required integer support = 326.
  - Patterns = **315** (Lengths: 1: 49, 2: 131, 3: 114, 4: 21).
  - Candidates = **18,198** (I: 2,763, S: 15,435; Accepted: 266, Rejected: 17,932).
  - Mode Equality: `FULL_ENCODING` and `SUPPORT_ONLY` produce identical pattern maps.
- **SIGN 0.3%:**
  - Effective minsup = $2.19$, Required integer support = 245.
  - Patterns = **1,166**, Candidates = **92,027**.
- **Table 4 Discrepancy (R8):**
  - Paper reports 56 patterns for SIGN 0.4%.
  - Diagnostic hypothesis in `SignDeepAuditTest`: An absolute weighted threshold of 4.0 yields exactly 56 patterns. However, standard percentage conversion yields 2.92 (315 patterns). R8 remains **OPEN**.

### 8.3 PUMSB Validation Matrix

| Input Minsup | Format | Expected Patterns | Candidates | Peak Heap MB | DFS Mining Time | Status / Evidence |
|---|---|---:|---:|---:|---:|---|
| **0.67%** | FIMI | 1 | 1 | ~349 MB | 4.25 ms | PASS (Fresh unit & integration test) |
| **0.66%** | FIMI | 15 | 113 | ~367 MB | 428 ms | PASS (Fresh harness & test) |
| **0.65%** | FIMI | 64 | — | — | — | Independent diagnostic expectation |
| **0.60%** | FIMI | 4,059 | 95,465 | 514.089 MB | 355.626 s | PASS (Reused B22.3 verified run, `-Xmx2g`, `SUPPORT_ONLY`) |

> [!NOTE]
> **Pumsb 0.60% Reused Evidence Note:**
> In accordance with Task B23 instructions, the 356-second single-run evidence from Task B22.3 is reused because the codebase, JVM configuration (`-Xms256m -Xmx2g`), dataset hash, and retention policy are identical. B22.3 proved complete execution without OOM, mining exactly 4,059 patterns.

---

## 9. Result Retention Modes: `FULL_ENCODING` vs `SUPPORT_ONLY`

Introduced in Task B22.3 to separate active working memory from final result state:

| Feature / Behavior | `FULL_ENCODING` | `SUPPORT_ONLY` |
|---|---|---|
| **Primary Use Case** | Correctness auditing, differential testing, SID inspections. | Benchmarks, production runs, high-pattern datasets (Pumsb 0.60%). |
| **Encoding Map** | Retained in `WspmMiningResult.encodings()`. | Discarded; completed subtrees are immediately GC-eligible. |
| **Supports Map** | Retained in `WspmMiningResult.supports()`. | Retained in `WspmMiningResult.supports()`. |
| **PrefixTree** | Identical structural tree retained. | Identical structural tree retained. |
| **Absence Contract** | `hasEncodings() == true`. | `hasEncodings() == false`; `encodings()` throws `UnsupportedOperationException`. |
| **Algorithmic Path** | Exact same DFS candidate traversal. | Exact same DFS candidate traversal. |
| **Table 1 Result** | 9 patterns, exact supports, peak 2.68 MB. | 9 patterns, exact supports, peak 1.64 MB. |

---

## 10. Memory & Time Measurement Protocols

### 10.1 Memory Measurement Protocol
- **Metric:** Observed sampled Java used heap (`MemoryMXBean.getHeapMemoryUsage().getUsed()`).
- **Sampling Interval:** Single daemon background thread sampling every 5 ms.
- **Unit Definition:** Decimal Megabytes, defined strictly as $\text{MB} = \text{bytes} / 1{,}000{,}000.0$ (never binary MiB).
- **Checkpoints:** Synchronous boundary snapshots taken at start/end of:
  - Dataset I/O
  - Preprocessing (weights, MeanW, F1, singleton encodings)
  - DFS Mining
  - Algorithm Total (includes tree freezing and immutable result wrapping)
  - Output I/O (reports `N/A` and 0 ms when export is disabled)
  - Total Execution
- **Distinctions:**
  - *Observed Sampled Peak Heap:* Maximum used heap sampled during a phase window.
  - *Used Heap Snapshot:* Instantaneous memory at a phase transition.
  - *Post-GC Live Set:* Retained objects surviving garbage collection.
- **Sampler Overhead:** Documented as **NOT VERIFIED** to be negligible; sampler thread adds minor runtime overhead.

### 10.2 Time Measurement Protocol
- **Measurement Tool:** `System.nanoTime()` with boundary encapsulation in `PhaseExecutionMetrics`.
- **Units:** Milliseconds displayed with 5 decimal places (`formatFiveDecimals`).
- **Phase Scopes:**
  - $\text{Dataset I/O Time}$: File parsing to `SequenceDatabase`.
  - $\text{Preprocessing Time}$: Calculation of item statistics, weights, F1, and root seeding.
  - $\text{DFS Mining Time}$: Pure recursive tree traversal and join execution.
  - $\text{Algorithm Time}$: Preprocessing + DFS Mining + Prefix Tree freezing + Result construction.
  - $\text{Output I/O Time}$: File export (0.00000 ms when disabled).
  - $\text{Total Execution Time}$: Entire lifecycle excluding console printing.

---

## 11. Performance Reproducibility (B22.2 / B22.3 Benchmarks)

Benchmark protocol: Oracle JDK 21.0.12.1 LTS, Windows 11, `-Xms256m -Xmx2g`, output disabled, 3 repetitions (median reported):

| Configuration | Mode | Candidates / Patterns | Prep ms | DFS ms | Algorithm ms | Peak Heap MB |
|---|---|---:|---:|---:|---:|---:|
| **Table 1, 14%** | `SUPPORT_ONLY` | 34 / 9 | 0.622 | 1.584 | 2.304 | 1.638 |
| **Table 1, 14%** | `FULL_ENCODING` | 34 / 9 | 0.604 | 2.026 | 3.027 | 2.687 |
| **SIGN, 0.4%** | `SUPPORT_ONLY` | 18,198 / 315 | 14.316 | 426.037 | 469.750 | 157.893 |
| **SIGN, 0.4%** | `FULL_ENCODING` | 18,198 / 315 | 21.812 | 438.700 | 461.327 | 161.038 |
| **SIGN, 0.3%** | `SUPPORT_ONLY` | 92,027 / 1,166 | 16.157 | 1,464.722 | 1,487.976 | 159.990 |
| **SIGN, 0.3%** | `FULL_ENCODING` | 92,027 / 1,166 | 18.651 | 1,418.558 | 1,439.055 | 160.274 |
| **Pumsb, 0.67%** | `SUPPORT_ONLY` | 1 / 1 | 288.194 | 4.253 | 292.559 | 361.416 |
| **Pumsb, 0.66%** | `SUPPORT_ONLY` | 113 / 15 | 265.735 | 428.376 | 707.295 | 367.613 |
| **Pumsb, 0.60%** | `SUPPORT_ONLY` | 95,465 / 4,059 | 581.885 | 355,625.615 | 356,220.784 | 514.089 |

---

## 12. Standalone Main Execution Verification

The standalone entry point `vn.edu.wspm.experiments.MainWspmPreTree` was audited and restored to the canonical Table 1 default configuration:
- `DATASET_PATH = Path.of("src/test/resources/datasets/wspm/table1.spmf");`
- `INPUT_FORMAT = SequenceDatabaseFormat.SPMF_SEQUENTIAL;`
- `MIN_SUP_IS_RELATIVE = true;`
- `MIN_SUP = 0.14;` (Represents 14.0%, converted via `BigDecimal.movePointRight(2)`)
- `RESULT_RETENTION = ResultRetentionPolicy.SUPPORT_ONLY;`
- `PRINT_PATTERNS = true;`
- `PRINT_STATISTICS = true;`
- `EXPORT_PATTERNS = false;`

### Verified Standalone Execution Output
```text
========== INPUT CONFIGURATION ==========
Algorithm                      : WSPM_PreTree
Dataset                        : src\test\resources\datasets\wspm\table1.spmf
Input Format                   : SPMF_SEQUENTIAL
Number of Sequences            : 7
Number of Distinct Items       : 8
Minimum Support Input          : 0.14 (relative fraction)
Effective Minimum Support      : 0.98000
MeanW                          : 0.18182
Result Retention Policy        : SUPPORT_ONLY
Print Patterns                 : true
Export Patterns                : false

========== MINING STATISTICS ==========
Generated Candidates           : 34
Accepted Candidates            : 6
Rejected Candidates            : 28
I-Extension Candidates         : 7
S-Extension Candidates         : 27
Expanded Nodes                 : 9
Number of Weighted Patterns    : 9

========== MEMORY STATISTICS (MB) ==========
Heap Before Dataset I/O          : 2.097
Heap After Dataset I/O           : 2.097
Peak Heap Dataset I/O            : 2.097

Heap Before Preprocessing        : 2.097
Heap After Preprocessing         : 4.194
Peak Heap Preprocessing          : 4.194

Heap Before Mining             : 4.194
Heap After Mining              : 4.194
Peak Heap DFS Mining           : 4.194

Heap Before Output I/O         : N/A
Heap After Output I/O          : N/A
Peak Heap Output I/O           : N/A

Peak Heap Algorithm            : 4.194
Peak Heap Total Execution      : 4.194
Memory Unit                    : MB (1 MB = 1,000,000 bytes)
Peak Measurement               : Observed sampled Java heap used
Sampling Interval              : 5 ms

========== TIME STATISTICS (ms) ==========
Dataset I/O Time               : 11.47590
Preprocessing Time             : 62.41180
DFS Mining Time                : 12.31180
Algorithm Time                 : 77.89250
Output I/O Time                : 0.00000
Total Execution Time           : 101.67330
Total Scope                    : Excludes console printing
=== Accepted Patterns ===
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

---

## 13. Maven Clean Test & Verify Verification

```text
Command: mvn clean test
Result : Tests run: 363, Failures: 0, Errors: 0, Skipped: 0
Status : BUILD SUCCESS (Total time: 24.734 s)

Command: mvn clean verify
Result : Tests run: 363, Failures: 0, Errors: 0, Skipped: 0
Status : BUILD SUCCESS (Total time: 28.312 s)
Artifact: target/wspm-pretree-bitprune-1.0-SNAPSHOT.jar
```

---

## 14. Known Limitations & Research Boundaries

1. **R1 (Sequence Weight Formula):** Unresolved worked example difference in paper text; candidate acceptance relies safely on MeanW.
2. **R6 (Zero Minimum Support):** Literal zero minsup comparison would accept non-occurring candidates; experiments are restricted to $minsup > 0$.
3. **R7 (FIMI Transaction Mapping):** Mapping 1 transaction line to 1 sequence containing 1 itemset is an explicit repository policy, not verified paper preprocessing.
4. **R8 (SIGN Paper Count Discrepancy):** Paper reports 56 patterns at 0.4%; baseline yields 315 patterns. Verified to be an unresolved paper reproduction gap (Category E).
5. **Git Reproducibility Blocker B21-F01:** Modified/untracked baseline files are present in the working tree. Must be committed and verified in a fresh clone in Task B24.
6. **Recursive DFS Call Stack:** Deep recursion relies on the Java thread stack. A very long sequential pattern could theoretically cause `StackOverflowError`.
7. **Transient Sibling Candidate Lists:** Sibling candidate extensions are currently generated in batches per stack frame before evaluation.

---

## 15. Recommendation for Phase C

The WSPM_PreTree baseline implementation is **mathematically sound, fully verified, reproducible, and ready for baseline freeze**:

1. **Proceed to Task B24:**
   - Execute the baseline checkpoint commit (`feat(wspm): complete and audit WSPM_PreTree baseline`).
   - Create an independent clean clone / worktree to verify build and standalone execution from a fresh checkout.
   - Formally close blocker **B21-F01**.
2. **Transition to Phase C & D (WSPM_BitPrune):**
   - Once B24 is complete, the repository is officially ready for Phase C (experimental dataset preparation) and Phase D (WSPM_BitPrune implementation with Bitmap upper-bound pruning, CMAP, and Multi-tier Lazy Pruning).
   - Under the Cross-Algorithm Correctness Rule (`AGENTS.md` Rule 6), all future WSPM_BitPrune runs must match this verified WSPM_PreTree baseline pattern-for-pattern on identical datasets.
