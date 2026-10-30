# C12 Research Report — Mining Statistics & Accounting Invariants

**Repository:** `wspm-pretree-bitprune`  
**Task:** C12 — Mining Statistics & Accounting Invariants  
**Scope:** C12.1 – C12.4  
**Branch:** `feature/wspm-bitprune`  
**HEAD:** `c7e90039be63d0db2e5fdb8a1f036e0b2ee16686`  
**Status:** IN REVIEW  
**Operational Scope:** positive-effective-threshold operational scope  
**Research Issue Tracker:** R6 (`Zero Minimum Support Semantics`) remains OPEN; floating-point Java `double` comparator safety remains unresolved / unchanged.

---

## 1. Executive Summary

Task C12 establishes a rigorous, mathematically verified candidate accounting and statistics subsystem for the `WSPM_BitPrune` sequential pattern mining algorithm.

The objectives achieved in C12 are:
1. **Immutable Statistics Model (`BitPruneStatistics`)**: Represents a completed mining run via 12 primary non-negative `long` counters and 1 derived property (`rejectedExtensions`), enforcing 4 structural conservation invariants at construction time; `Math.addExact` detects overflow and throws `ArithmeticException` instead of silently wrapping.
2. **Encapsulated First-Failure Accumulator (`BitPruneStatisticsAccumulator`)**: Package-private mutable accumulator residing strictly in `vn.edu.wspm.bitprune.mining`, instantiated per mining run on the invocation stack. It routes every candidate extension to exactly one terminal category upon the first pruning failure.
3. **Miner Integration (`BitPruneMiner`) & Minimal Result Carrier (`BitPruneMiningResult`)**: Integrates statistics tracking into the candidate generation and multi-tier pruning lifecycle of `BitPruneMiner` (`public final class`) without modifying candidate generation or pruning semantics, preserving source-compatible `mineInto(...)` signatures, and returning frozen Prefix Trees alongside immutable support maps in `BitPruneMiningResult` (`public final class`).
4. **Exact Counter Oracle Evidence**: Validates exact full-vector counts for all 12 primary counters across controlled edge cases, single-outcome witnesses, and a comprehensive mixed four-outcome execution, eliminating weak inequalities.
5. **C11 Non-Regression**: Proves that statistics collection introduces zero behavioral alterations to pattern identities, exact integer supports, or tree structures.

---

## 2. Implementation Inventory

The C12 subsystem consists of the following components:

| Component | Package / Path | Visibility | Role & Mutability |
|---|---|---|---|
| `BitPruneStatistics` | `vn.edu.wspm.bitprune.statistics` | `public record` | Immutable statistics carrier holding the 12 primary counters; constructor enforces non-negativity and invariants. |
| `BitPruneStatisticsAccumulator` | `vn.edu.wspm.bitprune.mining` | `class` (package-private) | Mutable accumulator for candidate lifecycle events during a single mining run; checks invariants on `finish()`. |
| `BitPruneMiner` | `vn.edu.wspm.bitprune.mining` | `public final class` | Mining engine instrumented with per-invocation accumulator; produces `BitPruneMiningResult`. |
| `BitPruneMiningResult` | `vn.edu.wspm.bitprune.result` | `public final class` | Immutable completed-run carrier encapsulating `PrefixTree prefixTree`, immutable `List<Pattern> acceptedPatterns`, immutable snapshot `Map<Pattern, Integer> supports`, and `BitPruneStatistics statistics`. |
| `BitPruneStatisticsTest` | `vn.edu.wspm.bitprune.statistics` | `class` (test) | Unit tests verifying model invariants, negative validation, and `Math.addExact` overflow safety (34 tests). |
| `BitPruneStatisticsAccumulatorTest` | `vn.edu.wspm.bitprune.mining` | `class` (test) | Unit tests verifying accumulator lifecycle, first-failure terminal routing, and double-finish prevention (39 tests). |
| `BitPruneMiningResultTest` | `vn.edu.wspm.bitprune.result` | `class` (test) | Unit tests verifying result carrier immutability, null rejection, and tree-support structural consistency (14 tests). |
| `BitPruneMiningStatisticsIntegrationTest` | `vn.edu.wspm.bitprune.mining` | `class` (test) | Integration tests executing end-to-end mining runs with exact 12-counter vector assertions against hand-audited oracles (23 tests). |

### Key Architectural Boundaries
- **No Global Mutable State**: No static or shared counters exist. Each invocation of `BitPruneMiner.mine(...)` or `mineIntoResult(...)` allocates an isolated `BitPruneStatisticsAccumulator`.
- **Package Encapsulation**: `BitPruneStatisticsAccumulator` is package-private to `vn.edu.wspm.bitprune.mining`. Outside callers only receive immutable `BitPruneStatistics`.
- **Completed Run Snapshot**: A `BitPruneStatistics` instance is minted only upon successful, normal completion of a mining run via `accumulator.finish(...)`. Even if an exception prevents completed result return, no partial or completed statistics result is published to the caller through the mining API.

---

## 3. Counter Contract & Definitions

The accounting model defines exactly 12 primary counters and 1 derived metric:

| Index | Counter Name | Type | Event Definition | Exclusions | Increment Location | Phase | Evidence Test |
|:---:|---|---|---|---|---|:---:|---|
| 1 | `sequenceCount` | Primary | Number of sequence records $|D|$ in the database (not inferred from max SID) | Empty lines / comments | Constructor: `new BitPruneStatisticsAccumulator(sequenceCount, f1Count)` | Preprocessing | `testEmptyDatabasePositiveThreshold`, `testMixedOutcomesControlledExecution` |
| 2 | `f1Count` | Primary | Number of accepted weighted singleton patterns inserted directly under the root satisfying $\text{singletonSupport} \times \text{globalMeanWeight} \ge \text{effectiveThreshold}$ | Non-frequent items ($\text{singletonSupport} \times \text{globalMeanWeight} < \text{effectiveThreshold}$) | Constructor: `new BitPruneStatisticsAccumulator(sequenceCount, f1Count)` | Preprocessing | `testF1EmptyDatabase`, `testF1OnlyAllExtensionsPruned`, `testMixedOutcomesControlledExecution` |
| 3 | `generatedExtensions` | Primary | Structural candidate extensions actually emitted during DFS (excludes F1) | F1 items; ungenerated candidates | `BitPruneMiner.dfs(...)` via `recordGeneratedIExtension()` / `recordGeneratedSExtension()` | DFS Mining | `testF1PatternsDoNotCountAsGeneratedExtensions`, `testMixedOutcomesControlledExecution` |
| 4 | `generatedIExtensions` | Primary | Itemset extension (I-Extension) candidates generated | S-Extensions; F1 items | `BitPruneMiner.dfs(...)` via `recordGeneratedIExtension()` | DFS Mining | `testGeneratedIExtensionsRecorded`, `testMixedOutcomesControlledExecution` |
| 5 | `generatedSExtensions` | Primary | Sequence extension (S-Extension) candidates generated | I-Extensions; F1 items | `BitPruneMiner.dfs(...)` via `recordGeneratedSExtension()` | DFS Mining | `testGeneratedSExtensionsRecorded`, `testMixedOutcomesControlledExecution` |
| 6 | `cmapRejected` | Primary | Terminal outcome when necessary CMAP relation is absent | Candidates passing CMAP | `BitPruneMiner.dfs(...)` switch (`CMAP_REJECTED`) via `recordCmapRejected()` | DFS Mining | `testCmapRejectedRecorded`, `testMixedOutcomesControlledExecution` |
| 7 | `bitmapRejected` | Primary | Terminal outcome when weighted bitmap upper bound fails threshold: $\text{candidateBitmap.cardinality()} \times \text{globalMeanWeight} < \text{effectiveThreshold}$ | Candidates pruned by CMAP; candidates passing Bitmap | `BitPruneMiner.dfs(...)` switch (`BITMAP_REJECTED`) via `recordBitmapRejected()` | DFS Mining | `testBitmapRejectedRecorded`, `testMixedOutcomesControlledExecution` |
| 8 | `exactRejected` | Primary | Candidates completing exact temporal verification but failing threshold: $\text{exactSupport} \times \text{globalMeanWeight} < \text{effectiveThreshold}$ | Candidates pruned by CMAP or Bitmap; accepted candidates | `BitPruneMiner.dfs(...)` switch (`EXACT_REJECTED`) via `recordExactRejected()` | DFS Mining | `testExactRejectedRecorded`, `testMixedOutcomesControlledExecution` |
| 9 | `acceptedExtensions` | Primary | Candidates completing exact verification and accepted as weighted patterns: $\text{exactSupport} \times \text{globalMeanWeight} \ge \text{effectiveThreshold}$ | F1 patterns; rejected candidates | `BitPruneMiner.dfs(...)` switch (`ACCEPTED`) via `recordAcceptedExtension()` | DFS Mining | `testAcceptedExtensionsRecorded`, `testMixedOutcomesControlledExecution` |
| 10 | `expandedNodes` | Primary | Accepted patterns whose candidate extensions are explored in DFS (including leaf nodes that produce no accepted extensions) | Unaccepted candidates; unexpanded root | `BitPruneMiner.dfs(...)` entry via `recordExpandedNode()` | DFS Mining | `testAcceptedLeafCountsAsExpandedNode`, `testMixedOutcomesControlledExecution` |
| 11 | `patternCount` | Primary | Total weighted sequential patterns mined ($= \text{f1Count} + \text{acceptedExtensions}$) | Rejected candidates; intermediate non-patterns | `accumulator.finish((long) supportMap.size())` post-traversal | Summary | `testTable1PaperDatasetStatistics`, `testMixedOutcomesControlledExecution` |
| 12 | `exactVerifications` | Primary | Number of candidates completing exact temporal support verification | Candidates pruned by CMAP or Bitmap | Internal to `recordExactRejected()` and `recordAcceptedExtension()` upon completed exact verification | DFS Mining | `testExactRejectedRecorded`, `testAcceptedExtensionsRecorded`, `testMixedOutcomesControlledExecution` |
| 13 | `rejectedExtensions` | Derived | Total rejected extensions ($= \text{cmapRejected} + \text{bitmapRejected} + \text{exactRejected}$) | Accepted candidates; F1 items | Derived method in `BitPruneStatistics` (no increment location) | Post-run | `BitPruneStatisticsTest`, `assertExactStatistics` |

> [!NOTE]
> F1 patterns (frequent 1-item sequences) are discovered during initial database projection and are not candidate extensions generated in DFS. Thus, `generatedExtensions` strictly measures extensions generated from prefix patterns of length $\ge 1$.

---

## 4. Candidate Lifecycle & First-Failure Routing

Every candidate generated during DFS undergoes Multi-tier Lazy Pruning and is routed into exactly one terminal outcome category upon the first failure encounter:

```text
Prefix Pattern P (expandedNodes recorded via recordExpandedNode())
    │
    ├── Emit I-Extension ──► recordGeneratedIExtension() (generatedIExtensions++, generatedExtensions++)
    └── Emit S-Extension ──► recordGeneratedSExtension() (generatedSExtensions++, generatedExtensions++)
             │
             ▼
      Tier 1: CMAP Filter (Necessary Co-occurrence Relation)
             │
             ├── Required CMAP relation absent ──► recordCmapRejected() (cmapRejected++)
             │                                     └── TERMINAL: no PrefixTree insertion, no support insertion, no recursion
             └── Required CMAP relation present
                     │
                     ▼
              Tier 2: Bitmap Bound Filter (Numeric Upper Bound)
                     │
                     ├── candidateBitmap.cardinality() * globalMeanWeight < effectiveThreshold
                     │       └── recordBitmapRejected() (bitmapRejected++)
                     │           └── TERMINAL: no PrefixTree insertion, no support insertion, no recursion
                     └── candidateBitmap.cardinality() * globalMeanWeight >= effectiveThreshold
                             │
                             ▼
                      Tier 3: Exact Temporal Support Verification
                             │
                             ├── exactSupport * globalMeanWeight < effectiveThreshold
                             │       └── recordExactRejected() (exactRejected++, exactVerifications++)
                             │           └── TERMINAL: no PrefixTree insertion, no support insertion, no recursion
                             └── exactSupport * globalMeanWeight >= effectiveThreshold
                                     └── recordAcceptedExtension() (acceptedExtensions++, exactVerifications++)
                                         ├── Insert into PrefixTree
                                         ├── Insert into support map
                                         └── Recurse into dfs(...) (recordExpandedNode())
```

### Lifecycle Guarantees & Accounting Semantics
1. **Single Emission**: A candidate is counted as generated exactly once when its structural extension is emitted.
2. **First-Failure Terminality**: Pruning evaluation terminates at the first failing tier; no subsequent tiers are evaluated for that candidate. Rejection at any tier is terminal for that candidate (meaning no PrefixTree insertion, no support insertion, and no DFS recursion).
3. **Partitioning**: Outcomes `cmapRejected`, `bitmapRejected`, `exactRejected`, and `acceptedExtensions` form a strict partition of `generatedExtensions`.
4. **Exact Support Verification Accounting**: An exact temporal verification is executed if and only if the candidate passes both Tier 1 (CMAP co-occurrence) and Tier 2 (Bitmap upper bound) filters. In the implementation, `exactVerifications` is not an uncoordinated preliminary counter; rather, `recordExactRejected()` and `recordAcceptedExtension()` in `BitPruneStatisticsAccumulator` simultaneously increment `exactVerifications` alongside the respective terminal outcome upon completion of Tier 3 verification. Consequently, the invariant $\text{exactVerifications} = \text{exactRejected} + \text{acceptedExtensions}$ holds by construction.

---

## 5. Mathematical Conservation Invariants

The `BitPruneStatistics` constructor and `BitPruneStatisticsAccumulator.finish(...)` strictly validate four fundamental mathematical conservation laws:

$$\begin{aligned}
\text{generatedExtensions} &= \text{generatedIExtensions} + \text{generatedSExtensions} \\
\text{generatedExtensions} &= \text{cmapRejected} + \text{bitmapRejected} + \text{exactRejected} + \text{acceptedExtensions} \\
\text{exactVerifications} &= \text{exactRejected} + \text{acceptedExtensions} \\
\text{patternCount} &= \text{f1Count} + \text{acceptedExtensions}
\end{aligned}$$

Additionally, the derived counter is defined as:
$$\text{rejectedExtensions} = \text{cmapRejected} + \text{bitmapRejected} + \text{exactRejected}$$

### Robustness & Safety Mechanisms
- **Non-Negativity**: Passing any negative value to a counter throws `IllegalArgumentException`.
- **Invariant Mismatch**: Any deviation in the 4 conservation equations throws `IllegalStateException`.
- **Overflow Detection**: Sums are computed using `Math.addExact(...)`. `Math.addExact` detects overflow and throws `ArithmeticException` instead of silently wrapping.

---

## 6. Exact Controlled Oracle Evidence

To eliminate weak inequality assertions, C12.3-R1 established exact 12-counter vector oracles independently derived from structural lifecycle tracing on controlled sequence databases:

| Test Case | Database Configuration & Threshold | `seq` | `f1` | `gen` | `genI` | `genS` | `cmap` | `bmp` | `exact` | `acc` | `exp` | `patt` | `ev` | `rej` |
|---|---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| `testEmptyDatabasePositiveThreshold` | Empty DB, $\text{minSup} = 1.0$ | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| `testEmptyDatabaseZeroThreshold` | Empty DB, $\text{minSup} = 0.0$ | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| `testF1EmptyDatabase` | $S_1: (1)$, $\text{minSup} = 2.0$ | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| `testF1OnlyAllExtensionsPruned` | $S_1: (1), S_2: (1)$, $\text{minSup} = 2.0$ | 2 | 1 | 1 | 0 | 1 | 1 | 0 | 0 | 0 | 1 | 1 | 0 | 1 |
| `testGeneratedIExtensionsRecorded` | $S_1: (1\ 2), S_2: (1\ 2)$, $\text{minSup} = 1.0$ | 2 | 2 | 7 | 1 | 6 | 6 | 0 | 0 | 1 | 3 | 3 | 1 | 6 |
| `testGeneratedSExtensionsRecorded` | $S_1: (1\ 2), S_2: (1\ 2)$, $\text{minSup} = 1.0$ | 2 | 2 | 7 | 1 | 6 | 6 | 0 | 0 | 1 | 3 | 3 | 1 | 6 |
| `testCmapRejectedRecorded` | $S_1: (1\ 2), S_2: (1\ 2)$, $\text{minSup} = 1.0$ | 2 | 2 | 7 | 1 | 6 | 6 | 0 | 0 | 1 | 3 | 3 | 1 | 6 |
| `testBitmapRejectedRecorded` | $S_1: (1)(2), S_2: (1), S_3: (2), S_4: (2)$, $\text{minSup} = 0.75$ | 4 | 2 | 5 | 1 | 4 | 4 | 1 | 0 | 0 | 2 | 2 | 0 | 5 |
| `testExactRejectedRecorded` | $S_1: (1)(2), S_2: (2)(1)$, $\text{minSup} = 2.0$ | 2 | 2 | 5 | 1 | 4 | 3 | 0 | 2 | 0 | 2 | 2 | 2 | 5 |
| `testAcceptedExtensionsRecorded` | $S_1: (1\ 2), S_2: (1\ 2)$, $\text{minSup} = 1.0$ | 2 | 2 | 7 | 1 | 6 | 6 | 0 | 0 | 1 | 3 | 3 | 1 | 6 |
| **`testMixedOutcomesControlledExecution`** | **$S_1: (1)(2)(3), S_2: (1)(2), S_3: (2)(1), S_4: (3)$, $\text{minSup} = \frac{10}{16}$** | **4** | **3** | **16** | **4** | **12** | **10** | **3** | **2** | **1** | **4** | **4** | **3** | **15** |
| `testAcceptedLeafCountsAsExpandedNode` | $S_1: (1\ 2), S_2: (1\ 2)$, $\text{minSup} = 1.0$ | 2 | 2 | 7 | 1 | 6 | 6 | 0 | 0 | 1 | 3 | 3 | 1 | 6 |
| `testPreparedPathMiningProducesIdenticalStatistics` | $S_1: (1), S_2: (1)$, $\text{minSup} = 2.0$ | 2 | 1 | 1 | 0 | 1 | 1 | 0 | 0 | 0 | 1 | 1 | 0 | 1 |
| `testF1PatternsDoNotCountAsGeneratedExtensions` | $S_1: (1)(2), S_2: (1)(2)$, $\text{minSup} = 2.0$ | 2 | 2 | 5 | 1 | 4 | 5 | 0 | 0 | 0 | 2 | 2 | 0 | 5 |

### Detailed Mixed Four-Outcome Derivation
The mixed four-outcome database exhibits all 4 pruning and acceptance outcomes simultaneously:
- **Items**: 1, 2, 3 with item occurrence counts 3, 3, 2; total occurrences = 8, $\text{globalMeanWeight} = \frac{5}{16}$, $\text{effectiveThreshold} = \frac{10}{16} = 0.625$. Required integer support $= 2$.
- **Frequent Items ($F_1$)**: items 1, 2, 3 have sequence supports 3, 3, 2 respectively $\implies \text{f1Count} = 3$.
- **From prefix $\langle(1)\rangle$ (expanded)**:
  - $\langle(1\ 2)\rangle, \langle(1\ 3)\rangle$: pruned by CMAP-I (2 CMAP rejected).
  - $\langle(1)(1)\rangle$: pruned by CMAP-S (1 CMAP rejected).
  - $\langle(1)(2)\rangle$: exact support 2, $\text{exactSupport} \times \text{globalMeanWeight} = 2 \times \frac{5}{16} = \frac{10}{16} \ge \text{effectiveThreshold}$, accepted! ($\text{acceptedExtensions} = 1$).
  - $\langle(1)(3)\rangle$: pruned by Bitmap bound: $\text{cardinality} \times \text{globalMeanWeight} = 1 \times \frac{5}{16} < \text{effectiveThreshold}$ (1 Bitmap rejected).
- **From prefix $\langle(1)(2)\rangle$ (expanded leaf)**:
  - $\langle(1)(2\ 3)\rangle$: pruned by CMAP-I (1 CMAP rejected).
  - $\langle(1)(2)(2)\rangle$: pruned by CMAP-S (1 CMAP rejected).
  - $\langle(1)(2)(1)\rangle$: passed CMAP & Bitmap, exact support = 0, $\text{exactSupport} \times \text{globalMeanWeight} < \text{effectiveThreshold}$ (1 Exact rejected).
  - $\langle(1)(2)(3)\rangle$: pruned by Bitmap bound: $\text{cardinality} \times \text{globalMeanWeight} = 1 \times \frac{5}{16} < \text{effectiveThreshold}$ (1 Bitmap rejected).
- **From prefix $\langle(2)\rangle$ (expanded)**:
  - $\langle(2\ 3)\rangle, \langle(2)(2)\rangle$: pruned by CMAP (2 CMAP rejected).
  - $\langle(2)(1)\rangle$: passed CMAP & Bitmap, exact support = 1, $\text{exactSupport} \times \text{globalMeanWeight} = 1 \times \frac{5}{16} < \text{effectiveThreshold}$ (1 Exact rejected).
  - $\langle(2)(3)\rangle$: pruned by Bitmap bound: $\text{cardinality} \times \text{globalMeanWeight} = 1 \times \frac{5}{16} < \text{effectiveThreshold}$ (1 Bitmap rejected).
- **From prefix $\langle(3)\rangle$ (expanded)**:
  - $\langle(3)(1)\rangle, \langle(3)(2)\rangle, \langle(3)(3)\rangle$: all pruned by CMAP-S (3 CMAP rejected).
- **Outcome Totals**:
  - `generatedExtensions`: $4\ (\text{I}) + 12\ (\text{S}) = 16$
  - `cmapRejected`: $2 + 1 + 1 + 1 + 2 + 3 = 10$
  - `bitmapRejected`: $1 + 1 + 1 = 3$
  - `exactRejected`: $1 + 1 = 2$
  - `acceptedExtensions`: $1$ ($\langle(1)(2)\rangle$)
  - `exactVerifications`: $2\ (\text{exactRejected}) + 1\ (\text{acceptedExtensions}) = 3$
  - `expandedNodes`: $4$ ($\langle(1)\rangle, \langle(1)(2)\rangle, \langle(2)\rangle, \langle(3)\rangle$)
  - `patternCount`: $3\ (F_1) + 1\ (\text{acceptedExtensions}) = 4$
  - `rejectedExtensions`: $10 + 3 + 2 = 15$

---

## 7. Result Carrier & Public API Compatibility

### Public Mining APIs
`BitPruneMiner` exposes two primary public entry points returning the immutable completed-run carrier:
```java
public BitPruneMiningResult mine(
        SequenceDatabase database,
        MinimumSupport minimumSupport)

public BitPruneMiningResult mineIntoResult(
        PrefixTree tree,
        SequenceDatabase database,
        MinimumSupport minimumSupport)
```

Additionally, internal DFS execution over precomputed structures is handled by the package-private method:
```java
BitPruneMiningResult minePreparedIntoResult(
        PrefixTree tree,
        List<WeightedOnePattern> f1,
        Map<Integer, SidBitmap> singletonBitmaps,
        Map<Integer, VerificationResult> singletonVerifications,
        CmapI cmapI,
        CmapS cmapS,
        VerticalOccurrenceIndex occurrenceIndex,
        double globalMeanWeight,
        ResolvedMinimumSupport minimumSupport)
```

### Legacy Signature Preservation
Source-compatible public signatures are strictly preserved:
```java
public Map<Pattern, Integer> mineInto(
        PrefixTree tree,
        SequenceDatabase database,
        MinimumSupport minimumSupport)

public Map<Pattern, Integer> mineInto(
        PrefixTree tree,
        List<WeightedOnePattern> f1,
        Map<Integer, SidBitmap> singletonBitmaps,
        Map<Integer, VerificationResult> singletonVerifications,
        CmapI cmapI,
        CmapS cmapS,
        VerticalOccurrenceIndex occurrenceIndex,
        double globalMeanWeight,
        ResolvedMinimumSupport minimumSupport)
```
These legacy methods internally execute `mineIntoResult(...)` or `minePreparedIntoResult(...)` and return the unmodifiable support map from the completed result. They do not populate an arbitrary caller-supplied target map. Source-compatible public signatures are preserved; no binary-compatibility claims are asserted in the absence of dedicated binary-compatibility tool evidence.

### Result Accessors & Structural Guarantees
`BitPruneMiningResult` provides the following accessors:
- `public PrefixTree prefixTree()`: Returns the caller-supplied `PrefixTree` instance after freezing.
- `public List<Pattern> acceptedPatterns()`: Returns an unmodifiable pre-order list of accepted non-root patterns.
- `public Map<Pattern, Integer> supports()`: Returns an unmodifiable snapshot `LinkedHashMap` preserving discovery iteration order.
- `public int support(Pattern pattern)`: Retrieves exact support for an accepted pattern, throwing `IllegalArgumentException` if absent.
- `public BitPruneStatistics statistics()`: Returns the immutable completed-run statistics snapshot.

Structural result invariants enforced at construction:
- `statistics.patternCount() == supports.size()`.
- `statistics.patternCount() == prefixTree.nodeCount() - 1L`.
- `statistics.patternCount() == acceptedPatterns.size()`.
- `statistics.f1Count() == prefixTree.root().children().size()`.

---

## 8. Edge Cases, Failure Modes & Aborted Runs

1. **Empty Database with Absolute Threshold**:
   - Yields a valid `BitPruneMiningResult` with empty support map and all-zero statistics (`sequenceCount=0, f1Count=0, ...`).
2. **Empty Database with Percentage Threshold**:
   - Rejected at input conversion (`MinimumSupport.percentage` requires $|D| > 0$ to calculate absolute support; throws `IllegalArgumentException`).
3. **Non-empty Database with Zero Effective Threshold**:
   - Rejected by `BitPruneMiner` under the open research policy `R6` (`IllegalArgumentException`).
4. **F1-Empty Database**:
   - Completes normally with `sequenceCount = |D|` and all candidate/pattern counters equal to 0.
5. **Failure Modes & Aborted Runs**:
   - The accumulator is mutable local state instantiated within the invocation stack frame of the mining run.
   - The accumulator is never returned or exposed to the caller.
   - Completed statistics can only be observed by the caller via a successfully returned `BitPruneMiningResult`.
   - Even if an exception prevents completed result return, no partial or completed statistics result is published to the caller through the mining API.
   - In C12, no mid-DFS failure-injection test was constructed; the no-publication guarantee is an architectural source-structure inference reinforced by tested input-validation paths that reject invalid inputs prior to result publication.

---

## 9. C11 Non-Regression Verification

Statistics instrumentation does not alter pattern mining outcomes. Differential comparisons against C11 oracles confirm complete equivalence:

- **Complete-Map Equivalence**: Every pattern identity, pattern length, and exact integer support produced matches the C11 baseline and brute-force oracles.
- **Prefix Tree Structure**: Result integration verifies the frozen tree; `patternCount == supports.size() == prefixTree.nodeCount() - 1` and `f1Count == prefixTree.root().children().size()`. Existing miner/tree regression remains green, and no pattern/support/tree semantic regression was observed.
- **C11 Focused Suite**: 38/38 tests pass with 0 mismatches across:
  - Table 1 Three-Oracle Differential Test (7 tests, 0 mismatches)
  - Controlled Differential Correctness Test (16 tests, 0 mismatches)
  - Bounded Exhaustive Differential Test (11 tests, 312 configurations, 936 pairwise comparisons, 0 mismatches)
  - Fixed-Seed Randomized Differential Test (4 tests, 1,200 trials, 3,600 pairwise comparisons, 0 mismatches)

---

## 10. Test Verification Results

All test suites were executed on the current repository state:

| Test Suite / Scope | Command | Reports | Tests Run | Failures | Errors | Skipped | Mismatches | Result |
|---|---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| **Statistics-Focused** | `mvn -q -Dtest=BitPruneStatisticsTest,BitPruneStatisticsAccumulatorTest,BitPruneMiningResultTest,BitPruneMiningStatisticsIntegrationTest test` | 4 | 110 | 0 | 0 | 0 | 0 | **PASS** |
| **Miner Regression** | `mvn -q -Dtest=BitPruneCandidateEvaluatorTest,BitPruneMinerTest,BitPruneMinerEndToEndTest test` | 3 | 82 | 0 | 0 | 0 | 0 | **PASS** |
| **C11 Differential** | `mvn -q -Dtest=BitPruneTable1ThreeOracleDifferentialTest,BitPruneControlledDifferentialCorrectnessTest,BitPruneBoundedExhaustiveDifferentialTest,BitPruneFixedSeedRandomizedDifferentialTest test` | 4 | 38 | 0 | 0 | 0 | 0 | **PASS** |
| **Full Maven Regression** | `mvn -q test` | 65 | 955 | 0 | 0 | 0 | 0 | **PASS** |

---

## 11. Research Fidelity & Issue Tracking

### Source-Confirmed
- Multi-tier lazy pruning lifecycle (CMAP-I/CMAP-S necessary-condition relations, Bitmap weighted upper bound, exact temporal support verification, weighted pattern acceptance).
- DFS Prefix Tree candidate extension structure (distinguishing I-Extension vs S-Extension candidates).
- SPMF sequence database conventions and Table 1 dataset characteristics.

### Implementation Inference
- Modeling statistics as an immutable Java record (`BitPruneStatistics`) detecting arithmetic overflow with `Math.addExact`.
- Encapsulating mutable state within a package-private accumulator in `vn.edu.wspm.bitprune.mining`.
- Designing controlled minimal sequence databases to act as exact 12-counter vector oracles for test verification.

### Unresolved / Deferred Issues
- **R6 (`Zero Minimum Support Semantics`)**: Remains **OPEN**. No support-zero pruning policy is approved. Zero effective threshold mining is rejected.
- **Floating-point Comparator Safety**: Java `double` comparison behavior remains unchanged and baseline-relative.
- **C13 (`Time/Memory Instrumentation Parity`)**: Measurement instrumentation is **NOT IMPLEMENTED** in C12.
- **C14 (`Result Retention & Stable Output`)**: Configurable retention policies are **NOT IMPLEMENTED** in C12.
- **Performance Claims**: No comparative benchmark numbers or performance claims are made.

---

## 12. Artifact Integrity Manifest

Pre-execution SHA-256 hashes of C12 artifacts and roadmap source identity:

```text
8428D040A11555E7F5E578EE2DF66AC385319BA2717791CD0F6C4AD18FE2C65C  src/main/java/vn/edu/wspm/bitprune/statistics/BitPruneStatistics.java
00ED3E8B66F6C7AED75C978E0F184C977BC0CB9A71FD1C776CFF5E12FF80DAD3  src/main/java/vn/edu/wspm/bitprune/mining/BitPruneStatisticsAccumulator.java
38A575ECEB83E09618FEA5A5D25769E71599BA866A2C52B1F2B3FA245D4F7B77  src/main/java/vn/edu/wspm/bitprune/mining/BitPruneMiner.java
177004247777BF45E79969A5E1EDED6008649A0F11897A58B9E50B04B6086998  src/main/java/vn/edu/wspm/bitprune/result/BitPruneMiningResult.java
9E80C41704EE227BF4E21CAAE23E1F3A1D0E60F3894B7E4369CE8BF38BB782C5  src/test/java/vn/edu/wspm/bitprune/statistics/BitPruneStatisticsTest.java
809C394E2D21AE24E3373C410A3B3E6D096EE1E5A8897C7DB26710EB790DFF71  src/test/java/vn/edu/wspm/bitprune/mining/BitPruneStatisticsAccumulatorTest.java
EC1B83EE8A628EC9BA1EE16BDA2F80A5FE83138537480D77210E347248EB4451  src/test/java/vn/edu/wspm/bitprune/result/BitPruneMiningResultTest.java
E7D669A945563704DC29D5F54BDC287E4F517DB8C84A916CB091937D5204D189  src/test/java/vn/edu/wspm/bitprune/mining/BitPruneMiningStatisticsIntegrationTest.java
86859E5D2BEE4B301B2B73B7352C1618D605334365384D39A843BEC66D67FD06  PHASE_C_WSPM_BitPrune.md (pre-update identity)
```

---

## 13. Roadmap Status

C12 implementation and accounting evidence package are complete. The roadmap status is transitioned to **IN REVIEW** pending Independent Research Review and formal acceptance by the Repository Owner.
