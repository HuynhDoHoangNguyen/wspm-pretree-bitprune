# Phase B WSPM_PreTree Reproducibility Manifest

**Manifest Date:** 2026-10-17  
**Repository:** `wspm-pretree-bitprune`  
**Current Phase:** Phase B — Baseline Freeze (Task B24)  
**Proposed Commit Message:** `feat(wspm): finalize and freeze Phase B baseline`  

---

## 1. Executive Summary & Checkpoint Scope

This manifest defines the complete scientific reproducibility specification for the validated reference baseline of **WSPM_PreTree** before initiating Phase C / Phase D (**WSPM_BitPrune**). It records the exact toolchain, build requirements, dataset hashes, measurement protocols, algorithm invariants, test proofs, and research issues.

---

## 2. Required Execution Environment

- **Operating System:** Platform independent Java Core (Audited on Microsoft Windows 11 Home Single Language, build `10.0.26200`, 64-bit).
- **Java Runtime:** Oracle Java JDK `21.0.12.1` LTS (HotSpot 64-Bit Server VM).
- **Build System:** Apache Maven `3.9.16`.
- **Compiler Configuration:** Java source `21`, Java target `21`, UTF-8 file encoding.
- **Dependencies:** Pure standard Java runtime. All external libraries (`junit-jupiter-api`, `junit-jupiter-engine` `5.12.2`) are strictly test-scoped in `pom.xml`. Zero third-party runtime frameworks (no Spring Boot, no databases, no external math libraries).

---

## 3. Dataset Provenance, Formats & Fingerprints

### 3.1 Committed Test Fixtures
These deterministic fixtures are committed to Git under `src/test/resources/` and are available in every clean clone:

| Relative Path | Format | Lines | Bytes | SHA-256 Digest | Role in Reproduction |
|---|---|---:|---:|---|---|
| `src/test/resources/datasets/wspm/table1.spmf` | `SPMF_SEQUENTIAL` | 7 | 273 | `F7E409F982EE42E0E34C7B940496074750AD8AEB44F191397EFEDDD0653AE8F5` | Paper 2023 Table 1 oracle & default standalone input |
| `src/test/resources/spmf/single-sequence.txt` | `SPMF_SEQUENTIAL` | 1 | 12 | `5214A320728C925A2648A46AC7A16DC048BADF96B8B9DD370A9A0A9E2AE81F48` | Minimal parser edge fixture |

### 3.2 External Research Datasets
These datasets are ignored by Git (`.gitignore`: `src/main/resources/datasets/*.txt`) to prevent inflating repository history (~32.5 MB) pending licensing and author preprocessing confirmation:

| Local Relative Path | Format | Sequences | Total Occurrences | Local SHA-256 Digest | Origin & Provenance |
|---|---|---:|---:|---|---|
| `src/main/resources/datasets/SIGN.txt` | `SPMF_SEQUENTIAL` | 730 | 37,958 | `41F2250001AC5E6DCA40C3966F74D036CC4E982F270DCBCD55968CA21B9023DD` | Normalized CRLF-equivalent of public SPMF `SIGN.txt` |
| `src/main/resources/datasets/pumsb.txt` | `FIMI_TRANSACTION` | 49,046 | 3,629,404 | `33B8E1B577C2325052E5B2EB5E6B3E6DA8AC46C1237559A16B61ACE6F06A2D74` | Confirmed deterministic +1 item-ID shift of FIMI mirror `pumsb.dat` |
| `src/main/resources/datasets/T40I10D100K.txt` | `FIMI_TRANSACTION` | 100,000 | 1,000,000 | `CAA0C26C7494D3BAF93A8627E19225F275463D51F14E08886B435B5FA73D967D` | Mirrored FIMI transaction database |

---

## 4. Authoritative Research References

The reference implementation is derived from:
1. **WSPM_PreTree (2023):** Thi-Thiet Pham, Thuy-Duong Vu, Tai-Du Nguyen, Bao Huynh, Trang Van, *Mining Weighted Sequential Patterns Based on Prefix-Tree and Prism Encoding*, Journal of Information & Knowledge Management, DOI [10.1142/S219688882350001X](https://doi.org/10.1142/S219688882350001X). Local SHA-256: `B1F019867FD61E21F4E951F464A5CBE79B3917E09CF99DD3B67C8AB18C119C41`.
2. **PRISM (2010):** Karam Gouda, Mosab Hassaan, Mohammed J. Zaki, *PRISM: An Effective Approach for Frequent Sequence Mining via Prime-block Encoding*, Journal of Computer and System Sciences, DOI [10.1016/j.jcss.2009.05.008](https://doi.org/10.1016/j.jcss.2009.05.008). Local SHA-256: `EBABC7B9AC95605F9D7C30A894B0426D8A7586728B0F9C88D5972C9F5BA81298`.

---

## 5. Build & Test Verification Procedure

### 5.1 Verification Commands
From the repository root:
```bash
mvn clean test
mvn clean verify
```

### 5.2 Test Execution Semantics (Committed vs External Datasets)
- **Full Environment (with local external datasets):**
  - **363 tests run, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS**.
  - All deep audit tests (`SignDeepAuditTest`: 7 tests, `SignRankNativeRegressionTest`: 1 test, `ResultRetentionPolicyTest`: 4 tests) execute assertions against `SIGN.txt` and `pumsb.txt`.
- **Fresh Checkout Environment (without external datasets):**
  - External datasets are not committed in Git due to `.gitignore`.
  - Tests touching external datasets (`SignDeepAuditTest`, `SignRankNativeRegressionTest`, and the 2 external methods in `ResultRetentionPolicyTest`) use JUnit 5 `Assumptions.assumeTrue(...)`.
  - **Expected in fresh checkout:** **353 tests PASS, 10 tests SKIPPED, 0 failures, 0 errors, BUILD SUCCESS** (to be empirically verified in the isolated fresh-checkout directory).
  - Production JAR packaged at `target/wspm-pretree-bitprune-1.0-SNAPSHOT.jar`.

### 5.3 Deterministic Differential Suite
- Seed: `20261016`.
- 18 fixed edge-case databases and 128 randomized differential test databases (146 configurations).
- Oracle: `BruteForceWeightedPatternOracle` validates complete `Map<Pattern, Integer>`, supporting SIDs, and prefix tree pre-order.

---

## 6. Standalone Execution Specification

### 6.1 Execution Command
```bash
java -cp target/classes vn.edu.wspm.experiments.MainWspmPreTree
```

### 6.2 Default Source Configuration
- `DATASET_PATH = Path.of("src/test/resources/datasets/wspm/table1.spmf");`
- `INPUT_FORMAT = SequenceDatabaseFormat.SPMF_SEQUENTIAL;`
- `MIN_SUP_IS_RELATIVE = true;`
- `MIN_SUP = 0.14;` (Represents 14.0% via `BigDecimal.movePointRight(2)`)
- `RESULT_RETENTION = ResultRetentionPolicy.SUPPORT_ONLY;`
- `PRINT_PATTERNS = true;`
- `PRINT_STATISTICS = true;`
- `EXPORT_PATTERNS = false;`

### 6.3 Expected Deterministic Outputs (Table 1, 14%)
- **Number of Sequences:** 7
- **Distinct Items:** 8
- **Effective Minimum Support:** 0.98000
- **MeanW:** 0.18182 (12/66)
- **Candidate Count:** 34 (Accepted: 6, Rejected: 28)
- **I-Extension Candidates:** 7
- **S-Extension Candidates:** 27
- **Expanded Nodes:** 9
- **Number of Weighted Patterns:** 9
- **Patterns & Supports:**
  - `<(1)>` #SUP: 7
  - `<(1 2)>` #SUP: 7
  - `<(1 2)(3)>` #SUP: 6
  - `<(1)(1)>` #SUP: 6
  - `<(1)(2)>` #SUP: 7
  - `<(1)(3)>` #SUP: 6
  - `<(2)>` #SUP: 7
  - `<(2)(3)>` #SUP: 6
  - `<(3)>` #SUP: 6

---

## 7. Performance & Memory Measurement Protocol

### 7.1 Memory Protocol
- **Heap Metric:** Sampled used heap bytes from `java.lang.management.MemoryMXBean.getHeapMemoryUsage().getUsed()`.
- **Sampling Interval:** 5 ms background daemon thread (`ExecutionMemorySampler`).
- **Reporting Unit:** Decimal Megabytes ($\text{MB} = \text{bytes} / 1{,}000{,}000.0$).
- **No Forced GC:** Sampler does not invoke `System.gc()`.
- **Phase Windows:** Dedicated synchronous checkpoints for Dataset I/O, Preprocessing, DFS Mining, and Output I/O.
- **Overhead:** Sampler introduces minor runtime overhead; not claimed as zero.

### 7.2 Time Protocol
- Measured via `System.nanoTime()`.
- Preprocessing ends after F1, singleton encodings, and maximum pattern length preparation.
- DFS ends after all F1 roots complete expansion.
- Algorithm Time includes result finalization and tree freezing, but excludes Dataset I/O and console output.
- Output I/O measures file writing only (0.00000 ms when disabled).

### 7.3 Result Retention Modes
- **`FULL_ENCODING`:** Retains compact `PatternEncoding` across all accepted patterns. Used for differential testing and SID auditing.
- **`SUPPORT_ONLY`:** Discards encodings as DFS subtrees complete. Retains only `supports()` and `PrefixTree`. Resolves Pumsb 0.60% heap exhaustion under `-Xmx2g` (peak 514 MB vs OOM).

---

## 8. Research Issues & Limitations Status (R1–R8)

| Issue | Name | Status | Limitation & Research Boundary |
|---|---|:---:|---|
| **R1** | Sequence-Weight Discrepancy | OPEN | Paper formula (3) averages itemsets; text yields 0.559. Acceptance uses MeanW; mining unaffected. |
| **R2** | Percentage Minimum Support | RESOLVED | `(p / 100.0) * N`, real-valued, unrounded, inclusive inequality. |
| **R3** | Prime-Block Mechanics | RESOLVED | PRISM 8-prime codec, rank-native 256-state tables, compact array representation, GCD and strict-after joins. |
| **R4** | Table 1 Oracle Reproduction | RESOLVED | 9 patterns, exact supports, SIDs, and prefix tree structure verified. |
| **R5** | Item-Weight Denominator | RESOLVED | Total database item-occurrence length ($23/66$ worked example). |
| **R6** | Zero Minimum Support Semantics | OPEN | Inclusive comparison accepts non-occurring items at 0. Experiments restricted to $minsup > 0$. |
| **R7** | Transaction-to-Sequence Mapping | OPEN | 1 FIMI transaction = 1 sequence of 1 itemset. Conservative policy; author preprocessing unknown. |
| **R8** | SIGN Paper Benchmark Discrepancy | OPEN | Paper Table 4 reports 56 patterns at 0.4%; baseline yields 315 independently validated patterns. Category E unresolved gap. |

---

## 9. Baseline Checkpoint File Inventory

### 9.1 Modified Files to Include (14 files)
1. `docs/research/phase-b-baseline-audit.md`
2. `docs/research/phase-b-reproducibility.md`
3. `docs/research/wspm-pretree-spec.md`
4. `src/main/java/vn/edu/wspm/experiments/MainWspmPreTree.java`
5. `src/main/java/vn/edu/wspm/mining/SingletonEncodingBuilder.java`
6. `src/main/java/vn/edu/wspm/mining/WspmMiningResult.java`
7. `src/main/java/vn/edu/wspm/mining/WspmPreTreeMiner.java`
8. `src/main/java/vn/edu/wspm/prism/EncodedVector.java`
9. `src/main/java/vn/edu/wspm/prism/PatternEncoding.java`
10. `src/main/java/vn/edu/wspm/prism/PrimalBlockCodec.java`
11. `src/main/java/vn/edu/wspm/prism/PrimeBlockJoins.java`
12. `src/test/java/vn/edu/wspm/experiments/MainWspmPreTreeTest.java`
13. `src/test/java/vn/edu/wspm/prism/PrimalBlockCodecTest.java`
14. `src/test/java/vn/edu/wspm/prism/PrimeBlockJoinsTest.java`

### 9.2 Untracked Files to Include (18 files)
1. `docs/research/phase-b-final-acceptance.md`
2. `docs/research/sign-pumsb-paper-reproduction-audit.md`
3. `docs/research/wspm-pretree-performance-audit.md`
4. `src/main/java/vn/edu/wspm/experiments/WspmExecutionRun.java`
5. `src/main/java/vn/edu/wspm/measurement/ExecutionMemorySampler.java`
6. `src/main/java/vn/edu/wspm/measurement/PhaseExecutionMetrics.java`
7. `src/main/java/vn/edu/wspm/measurement/WspmAlgorithmMetrics.java`
8. `src/main/java/vn/edu/wspm/measurement/WspmExecutionMetrics.java`
9. `src/main/java/vn/edu/wspm/mining/MeasuredMiningRun.java`
10. `src/main/java/vn/edu/wspm/mining/ResultRetentionPolicy.java`
11. `src/main/java/vn/edu/wspm/prism/PositionCoordinateSystem.java`
12. `src/test/java/vn/edu/wspm/audit/SignDeepAuditTest.java`
13. `src/test/java/vn/edu/wspm/audit/SignRankNativeRegressionTest.java`
14. `src/test/java/vn/edu/wspm/experiments/B221PerformanceHarness.java`
15. `src/test/java/vn/edu/wspm/experiments/B222RetentionHarness.java`
16. `src/test/java/vn/edu/wspm/measurement/ExecutionMemorySamplerTest.java`
17. `src/test/java/vn/edu/wspm/mining/ResultRetentionPolicyTest.java`
18. `src/test/java/vn/edu/wspm/prism/PatternEncodingCompactTest.java`

### 9.3 Explicitly Excluded Paths
- `target/` and build artifacts (`.class`, `.jar`).
- `src/main/resources/datasets/*.txt` (large experimental datasets excluded by `.gitignore`).
- `docs/*.pdf` (proprietary reference literature excluded by `.gitignore`).
- IDE and OS metadata (`.vscode/`, `.idea/`, `.DS_Store`).
- Temporary files and profiler outputs (`*.jfr`, `*.log`, `*.tmp`).
