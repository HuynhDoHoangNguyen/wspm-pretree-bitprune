# Phase B Final WSPM_PreTree Baseline Audit

Audit date: 2026-10-16

Task: B21

Decision: **BASELINE READY WITH DOCUMENTED LIMITATIONS**

## 1. Executive Summary

The audited WSPM_PreTree implementation is a credible Phase B reference baseline for the tested,
strictly-positive-threshold scope. Direct source review, the complete Table 1 oracle, an independent
test-only brute-force oracle, 146 deterministic differential comparisons, and three clean Maven
lifecycles found no CRITICAL or HIGH correctness defect. The default standalone execution also
reproduced all nine Table 1 patterns and their exact supports.

This is not a claim of correctness for every possible database or a complete reproduction of every
statement in the paper. R1 (sequence-weight example inconsistency), R6 (zero minimum support), and
R7 (transaction-to-sequence preprocessing) remain open. Recursive DFS can exhaust the Java stack on
an extreme accepted chain. Most importantly, the reviewed implementation and tests are currently
untracked in Git, so a fresh checkout of the current commit cannot reproduce this audit. The code is
algorithmically ready for reviewer acceptance, but the reviewed artifacts must be staged and
committed before Phase C work is based on repository history.

## 2. Scope

The audit directly reviewed:

- `AGENTS.md`, `PHASE_B_WSPM_PRETREE.md`, `wspm-pretree-spec.md`, and
  `prism-prime-block.md`;
- the local WSPM_PreTree 2023 and PRISM papers;
- domain objects, SPMF and FIMI parsers, explicit format dispatch, item statistics, weights, and
  minimum-support conversion;
- Prime-block encoding, sequence/position blocks, I-Join, S-Join, and strict-after behavior;
- F1, candidate generation, DFS miner, Prefix Tree, immutable result metadata, and statistics;
- Table 1, Prime-block boundary tests, brute-force oracle, differential tests, and standalone Main;
- build environment and Git worktree state.

No production semantics were changed. No Bitmap, CMAP, Lazy Pruning, WSPM_BitPrune, benchmark
framework, web/API/database code, or iterative DFS refactor was introduced.

## 3. Environment

| Property | Audited value |
|---|---|
| Date/time zone | 2026-10-16, Asia/Bangkok |
| OS | Microsoft Windows 11 Home Single Language, 10.0.26200, 64-bit |
| JDK | Oracle Java 21.0.12.1 LTS, HotSpot 64-bit |
| Maven | Apache Maven 3.9.16 |
| Source/target | Java 21 |
| Locale/encoding | `en_US`, UTF-8 |
| Test framework | JUnit Jupiter 5.12.2 |
| Surefire | 3.5.3 |
| Runtime dependencies | Java Core only; JUnit is test-scoped |

Audited source and fixture fingerprints:

| Artifact | Bytes | SHA-256 |
|---|---:|---|
| WSPM_PreTree 2023 PDF | 913125 | `B1F019867FD61E21F4E951F464A5CBE79B3917E09CF99DD3B67C8AB18C119C41` |
| PRISM PDF | 717697 | `EBABC7B9AC95605F9D7C30A894B0426D8A7586728B0F9C88D5972C9F5BA81298` |
| `table1.spmf` | 273 | `F7E409F982EE42E0E34C7B940496074750AD8AEB44F191397EFEDDD0653AE8F5` |

## 4. Source Fidelity Matrix

| Behavior/decision | Classification | Audit conclusion |
|---|---|---|
| Support counts containing SIDs, not occurrences/embeddings | SOURCE-CONFIRMED | Implemented and tested. |
| `W(i) = occurrenceCount(i) / totalDatabaseLength` | SOURCE-CONFIRMED | Implemented without rounding. |
| `MaxW`, `MinW`, `MeanW = (MaxW + MinW) / 2` | SOURCE-CONFIRMED | Implemented and Table 1 tested. |
| Acceptance uses `support(P) * MeanW >= effectiveMinSup` | SOURCE-CONFIRMED | Inclusive comparison implemented. |
| Prefix Tree with I-/S-Extension and DFS | SOURCE-CONFIRMED | Structural boundaries and extension types preserved. |
| Prime-block factor encoding, GCD joins, strict-after mask | SOURCE-CONFIRMED | Implemented with full aligned block vectors. |
| Eight positions/SIDs per Prime-block | SOURCE-CONFIRMED | Fixed eight-prime codec; cross-block cases tested. |
| Relative percentage conversion `(p / 100) * N` | STRONGLY-SUPPORTED INFERENCE | Explicit, unrounded conversion reproduces Table 1. |
| Main fraction `0.14` maps to API percentage `14.0` | IMPLEMENTATION POLICY | Explicit decimal shift; does not change the B7 API. |
| WSPM `pattern + singleton` operands for PRISM joins | REASONABLE-INFERENCE | Differentially validated, but not stated as a WSPM theorem. |
| Canonical numeric item ordering and deterministic traversal | IMPLEMENTATION POLICY | Prevents duplicates and stabilizes output. |
| FIMI line maps to one sequence containing one itemset | IMPLEMENTATION POLICY | Avoids inventing temporal order; paper preprocessing remains unknown. |
| Standalone Main, export format, and statistics | IMPLEMENTATION POLICY | Observation/orchestration only; not paper algorithm steps. |
| Paper sequence-weight worked value | UNSPECIFIED / UNRESOLVED | Formula and worked prose conflict; tracked as R1. |
| Meaning of zero minimum support | UNSPECIFIED / UNRESOLVED | Literal inclusive comparison can accept zero-support candidates; R6. |
| Pumsb/T40I10D100K preprocessing used by authors | UNSPECIFIED / UNRESOLVED | Current one-itemset policy is not claimed as paper reproduction; R7. |

The primary algorithm sources are *Mining Weighted Sequential Patterns Based on Prefix-Tree and
Prism Encoding* (DOI `10.1142/S219688882350001X`) and *Prism: An effective approach for frequent
sequence mining via prime-block encoding* (DOI `10.1016/j.jcss.2009.05.008`). Tests validate the
implementation interpretation; they do not upgrade an inference or policy to SOURCE-CONFIRMED.

## 5. Component Audit

### Domain and input

`Itemset` stores a canonical ascending set and rejects duplicates. `Sequence` preserves ordered
itemsets, so `<(a)(b)>` and `<(ab)>` remain distinct. `Pattern` extensions preserve I/S boundaries and
structural identity. Database SIDs are unique; parsers assign contiguous one-based SIDs, and the
Prime-block builder additionally requires the contiguous coordinate system it encodes.

`SpmfSequenceParser` honors `-1` itemset and `-2` sequence terminators. `FimiTransactionParser`
maps each nonblank transaction line to exactly one itemset. `SequenceDatabaseParser` requires an
explicit `SPMF_SEQUENTIAL` or `FIMI_TRANSACTION` choice and does not auto-detect. Invalid tokens,
terminators, duplicates, and malformed input have focused tests.

### Weights and minimum support

Item occurrences use the full database item-occurrence length as denominator. Empty database
statistics are represented without fabricating a MeanW. Relative input is converted explicitly;
no integer ceiling, floor, epsilon, or display rounding changes candidate acceptance. The default
Main mapping is `0.14 -> 14.0% -> 0.9800000000000001` in the runtime `double` representation for
seven sequences. R6 limits the supported experimental scope to a strictly positive effective
threshold.

### Prime-block encoding

The codec uses the eight source primes, checked legal factors/products, aligned multi-block vectors,
and explicit empty padding. Sequence support is factor cardinality over sequence blocks. I-Join is
blockwise intersection/GCD. S-Join retains right endpoints strictly later than a valid left endpoint
and reconstructs the exact candidate SID filter after temporal verification.

Tests cover SID 8-to-9, position 8-to-9, positions beyond 16, equal/earlier/later endpoints,
multi-block padding, empty blocks, incompatible vectors, chained Table 1 joins, and a SID-prefilter
false positive. The baseline contains no Bitmap substitute.

### Candidate Engine, DFS, tree, and result

The engine generates deterministic canonical I-Extensions followed by S-Extensions, including
repeated items across itemsets. Each `CandidateResult` retains pattern, encoding, supporting SIDs,
support, weighted acceptance, and extension type; rejected candidates remain observable but are not
inserted or expanded. Candidate identities are unique for the audited cases.

The miner creates accepted F1 children under an empty root and recursively expands only accepted
nodes. Node levels, parent links, extension types, and immutable metadata agree with pattern
structure. Results expose a frozen/read-only tree and immutable maps/lists. The maximum input
sequence occurrence length bounds accepted-pattern length, but recursion depth can still be unsafe
for an extreme chain.

### Statistics

`WspmMiningStatistics` is an immutable per-run snapshot. Its mutable accumulator is local to one
`mine` call; no static/global counter participates in results. `elapsedNanos` covers mining work and
is observational, not benchmark evidence. Checked additions fail on counter overflow. Tests and
Table 1 execution establish:

```text
candidateCount = iCandidateCount + sCandidateCount
candidateCount = acceptedCandidateCount + rejectedCandidateCount
patternCount   = f1Count + acceptedCandidateCount
patternCount   = PrefixTree.nodeCount() - 1
```

## 6. Table 1 Evidence

Configuration and computed values:

```text
MIN_SUP             = 14%
sequence count      = 7
total database size = 66 item occurrences
MeanW               = 12/66 = 0.181818181818...
effective minSup    = 0.98 (runtime double: 0.9800000000000001)
F1                  = a:7, b:7, c:6
```

The production map equals the nine-entry paper/test oracle exactly:

| Pattern | Numeric representation | Support | Supporting SIDs |
|---|---|---:|---|
| `<(a)>` | `<(1)>` | 7 | `{1,2,3,4,5,6,7}` |
| `<(b)>` | `<(2)>` | 7 | `{1,2,3,4,5,6,7}` |
| `<(c)>` | `<(3)>` | 6 | `{1,2,3,4,5,6}` |
| `<(ab)>` | `<(1 2)>` | 7 | `{1,2,3,4,5,6,7}` |
| `<(a)(a)>` | `<(1)(1)>` | 6 | `{1,2,4,5,6,7}` |
| `<(a)(b)>` | `<(1)(2)>` | 7 | `{1,2,3,4,5,6,7}` |
| `<(a)(c)>` | `<(1)(3)>` | 6 | `{1,2,3,4,5,6}` |
| `<(b)(c)>` | `<(2)(3)>` | 6 | `{1,2,3,4,5,6}` |
| `<(ab)(c)>` | `<(1 2)(3)>` | 6 | `{1,2,3,4,5,6}` |

The Prefix Tree contains the empty root plus nine accepted nodes and three F1 root children, with
paper-consistent parent/extension relationships. `<(ac)>` has support 3, is rejected, and is absent
from the tree/result. No production Table 1 special case or hardcoded expected map was found.

## 7. Brute-force Oracle Independence

`BruteForceWeightedPatternOracle` exists only under `src/test`. It enumerates non-empty itemset
subsets and ordered, gapped structural subsequences, then independently scans each database sequence
with a direct matcher. A SID contributes at most one support count even when multiple embeddings
exist. It independently derives occurrence counts, weights, MeanW, threshold, and acceptance.

The oracle does not call the production miner, Candidate Engine, Prime-block encoding/joins,
production weight calculator, Prefix Tree, or production DFS. It deliberately fails fast above its
one-million structural-enumeration cap. It is complete only for tiny controlled databases with a
strictly positive effective threshold; rejecting zero threshold avoids pretending to enumerate an
unbounded absent-pattern space while R6 is open.

## 8. Differential Testing Evidence

`WspmDifferentialCorrectnessTest` uses deterministic seed `20261016` and compares complete
`Pattern -> support` maps plus supporting SIDs and encoding support:

- 14 fixed databases and 18 fixed threshold comparisons;
- 32 generated databases at four positive thresholds each, giving 128 random comparisons;
- 146 comparisons in total.

Coverage includes sparse/dense structures, repeated items, gaps, multiple embeddings, mixed I/S
extensions, missing F1 branches, threshold equality, empty input, Table 1, SID 8-to-9, position
8-to-9, and positions beyond 16. All comparisons passed. This is strong regression evidence, not a
general proof over every possible database.

## 9. FIMI Format and R7

FIMI parsing preserves item IDs, ignores blank lines, assigns contiguous SIDs, rejects malformed or
duplicate-containing transactions, and never modifies SPMF behavior. A line `1 15 18` becomes
`<(1 15 18)>`, not `<(1)(15)(18)>`; therefore the adapter does not create temporal support that the
transaction source lacks. A focused FIMI integration/differential fixture passes.

R7 remains open because the preprocessing used by the paper authors for Pumsb and T40I10D100K has
not been source-verified. No full-dataset benchmark was run or claimed. Results from the current
mapping must be labeled as the repository's one-itemset-per-transaction policy.

## 10. Standalone Main B20.1

`vn.edu.wspm.experiments.MainWspmPreTree` has a standard `main(String[] args)` and source-level
constants for dataset, explicit format, relative/absolute minimum support, pattern/statistics flags,
export flag, and output path. It uses no `Scanner`, CLI requirement, framework, or algorithm switch.
It delegates parsing and mining to production components.

The actual command:

```text
java -cp target/classes vn.edu.wspm.experiments.MainWspmPreTree
```

read the Table 1 SPMF fixture, reported seven sequences, and printed the deterministic nine-pattern
map from Section 6. Default export is disabled, and `target/wspm-pretree-patterns.txt` did not exist
after execution. Tests additionally verify UTF-8 `#SUP` export with stable newlines/order, enabled
and disabled export, input/output path inequality, and clear missing-input failure. The default
relative repository path assumes the working directory is the repository root.

Future WSPM_BitPrune work should have a separate Main while sharing the same explicit format,
minimum-support meaning, and stable support output contract. B21 did not create that runner.

## 11. Mining Statistics

The real standalone Table 1 run produced:

```text
sequenceCount           = 7
f1Count                 = 3
candidateCount          = 34
acceptedCandidateCount  = 6
rejectedCandidateCount  = 28
iCandidateCount         = 7
sCandidateCount         = 27
expandedNodeCount       = 9
patternCount            = 9
```

All four counter invariants in Section 5 hold. Values are derived from execution and are not
hardcoded. The observed nanosecond duration is intentionally omitted from performance conclusions;
this task did not define warm-up, repetitions, or a fair cross-algorithm benchmark.

## 12. Reproducibility Results

| Command | Result |
|---|---|
| `mvn clean test` | BUILD SUCCESS; 338 tests, 0 failures, 0 errors, 0 skipped |
| `mvn test` | BUILD SUCCESS; 338 tests, 0 failures, 0 errors, 0 skipped |
| `mvn clean verify` | BUILD SUCCESS; 338 tests, 0 failures, 0 errors, 0 skipped; JAR built |
| standalone Main command | Exit 0; exact nine patterns/counters; no default export |
| `git diff --check` | Exit 0 for tracked changes |

The Table 1 fixture hash and random seed above make the small audited experiments repeatable on the
recorded toolchain. Explicit parser format and source-level Main configuration prevent interactive
configuration drift.

Git reproducibility is not yet satisfied. Before this report was created, only six files were
tracked; `.gitignore` and `pom.xml` were modified, and 78 reviewable files were untracked (74 under
`src`, two under `docs`, plus `AGENTS.md` and `PHASE_B_WSPM_PRETREE.md`). This report raises the final
untracked count to 79. `git diff --stat` consequently reported only two tracked files with 8
insertions and 1 deletion. `git diff --check` does not inspect untracked files. The research PDFs are
locally available but ignored; their hashes above identify the exact audited copies.

## 13. Research Issues R1-R7

| Issue | Status | Policy and impact |
|---|---|---|
| R1 - sequence-weight discrepancy | OPEN | Printed equations average itemset weights, while worked prose yields `0.559` from unnormalized sums. Candidate acceptance uses source-confirmed MeanW, not sequence weight, so current mining results are unaffected; claims about reproduced sequence-weight examples remain limited. Inspect cited source [20] or obtain author clarification. |
| R2 - percentage minimum support | RESOLVED FOR IMPLEMENTATION | `(percentage / 100) * N`, no rounding, inclusive comparison. Classified STRONGLY-SUPPORTED INFERENCE. |
| R3 - Prime-block mechanics | RESOLVED | PRISM encoding/join mechanics are documented and boundary/differential tested. Direct WSPM operand mapping remains REASONABLE-INFERENCE. |
| R4 - complete Table 1 oracle | RESOLVED BY B17 | Exact pattern, support, supporting SID, tree, and rejected-pattern checks pass. |
| R5 - item-weight denominator | RESOLVED | Total item-occurrence database length; Table 1 denominator is 66. |
| R6 - zero minimum support | OPEN | Literal inclusive comparison accepts structurally generated zero-support candidates. Positive-threshold results are unaffected. Use strictly positive thresholds until the paper/source defines whether zero support is admissible or configuration zero should be rejected. |
| R7 - transaction-to-sequence semantics | OPEN | One FIMI line maps to one sequence/one itemset. This is safe against invented temporal order but does not reproduce unknown paper preprocessing. Obtain the authors' dataset preparation procedure before paper benchmark claims. |

## 14. Known Limitations

- Evidence covers Table 1 and bounded tiny differential databases, not every database or a formal
  proof.
- The brute-force oracle is intentionally resource-capped and excludes zero effective threshold.
- Recursive DFS may raise `StackOverflowError` for an extreme accepted chain; B21 did not perform
  the prohibited iterative refactor.
- Prime-block uses a fixed eight-prime block model; checked validation exposes invalid factors rather
  than silently changing baseline representation.
- Timing is per-run observation only, not a performance or memory-efficiency claim.
- Pumsb/T40I10D100K have not been validated as paper-equivalent sequential preprocessing or fully
  benchmarked.
- `MainWspmPreTree` uses repository-relative source constants; running from another working
  directory requires configuration editing.
- The pre-existing `vn.edu.wspm.Main` still prints `Hello World`; IDE users should run the explicit
  experiment Main.
- A clean clone of the current commit lacks most reviewed artifacts until the worktree is committed.

## 15. Audit Findings

| ID | Severity | Type | File/module | Evidence | Impact | Recommended action | Status |
|---|---|---|---|---|---|---|---|
| B21-F01 | HIGH | Reproducibility risk | Git worktree | Only 6 tracked files; 78 files were untracked before this report and 79 afterward, including nearly all implementation/tests | Current commit cannot reproduce the audited baseline | Review the intended file list, then stage and commit all approved baseline source, fixtures, tests, and research notes before Phase C | OPEN |
| B21-F02 | MEDIUM | Reproducibility/source provenance | `docs/` | Primary PDFs are local and ignored; exact copies identified only by audit hashes | Another researcher may not obtain the same source revision from Git | Preserve DOI/acquisition metadata and SHA-256 manifest; distribute PDFs only where licensing permits | OPEN |
| B21-F03 | MEDIUM | Source-fidelity ambiguity | Research spec, R1 | Paper sequence-weight formula conflicts with worked value | Limits claims about sequence-weight example, but not current MeanW acceptance path | Inspect source [20] or request author clarification | OPEN |
| B21-F04 | MEDIUM | Correctness scope ambiguity | Minimum support/miner, R6 | Inclusive `0 >= 0` can accept generated zero-support candidates | Zero-threshold output cannot be claimed paper-faithful | Keep experimental threshold positive; resolve policy in a separate reviewed task | OPEN |
| B21-F05 | MEDIUM | Dataset-semantics ambiguity | FIMI parser, R7 | Transaction lines contain no temporal order; author preprocessing unknown | Paper benchmark reproduction on transaction datasets is unverified | Obtain and document original preprocessing before benchmark claims | OPEN |
| B21-F06 | MEDIUM | Robustness/performance limitation | Recursive DFS miner | Search depth uses Java call stack | Extreme accepted chains may overflow stack | Retain as baseline limitation; consider iterative DFS only in a separately scoped task | OPEN |
| B21-F07 | LOW | Usability/documentation | `vn.edu.wspm.Main` | Pre-existing entry point still prints `Hello World` | A user may run the wrong class | Document/run `vn.edu.wspm.experiments.MainWspmPreTree`; clean up only in a separate task | OPEN |
| B21-F08 | INFO | Correctness evidence | Baseline/test suite | Table 1 exact map, 146 differential comparisons, 338 tests, three Maven lifecycles pass | Strong evidence within the declared scope | Preserve fixtures, seed, and exact-map assertions | RESOLVED |

No CRITICAL or HIGH correctness blocker was found. B21-F01 is HIGH because it prevents commit-level
reproduction, not because the reviewed in-worktree algorithm produced an incorrect result.

## 16. Final Recommendation

**BASELINE READY WITH DOCUMENTED LIMITATIONS.**

The baseline is ready to serve as the correctness reference for subsequent review on the exact
scope exercised here: explicit SPMF/FIMI semantics, strictly positive effective minimum support,
Table 1, and bounded tiny differential databases. R1, R6, and R7 remain open and constrain research
claims. This recommendation does not authorize a universal-correctness claim, paper-equivalent
transaction-dataset benchmark claim, or performance claim.

Before starting Phase C, the Research Reviewer should verify and commit the audited untracked
artifacts so that the accepted Phase B state is recoverable from Git. B21 itself stops here and does
not begin WSPM_BitPrune.
