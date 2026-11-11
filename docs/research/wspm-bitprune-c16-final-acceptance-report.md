# C16 Final Regression & Phase C Acceptance Audit

**Repository:** `wspm-pretree-bitprune`  
**Task:** C16 — Final Regression & Phase C Acceptance  
**Audit date:** 2026-10-31 (Asia/Bangkok)  
**Branch:** `feature/wspm-bitprune`  
**C15 closure checkpoint HEAD:** `51cf01c288dc438f1f13246c2bb3315df14d508f`  
**Decision:** `PASS — IN REVIEW`

---

## 1. Executive Decision

The C16 audit passes within the accepted positive-effective-threshold operational scope. Fresh repository evidence confirms the frozen WSPM_PreTree algorithm, WSPM_BitPrune, and the independent brute-force oracle produce identical complete `Map<Pattern, Integer>` results throughout the exercised Table 1, controlled, bounded-exhaustive, and fixed-seed randomized catalogues. No missing pattern, unexpected pattern, support mismatch, unexplained mismatch, or known false-negative pruning was reported.

The decision is deliberately limited:

- **C16 audit result: PASS — IN REVIEW**
- **Phase C: IN PROGRESS — awaiting Independent Research Review and Repository Owner acceptance**
- **Phase D: NOT STARTED**

This report does not mark C16 `ACCEPTED` or Phase C `COMPLETED`.

## 2. Repository Checkpoint

The audit started from:

```text
Branch: feature/wspm-bitprune
HEAD: 51cf01c288dc438f1f13246c2bb3315df14d508f
Commit: 51cf01c feat(bitprune): complete C13-C15 execution and measurement pipeline
Index: clean
```

`src/main/java/vn/edu/wspm/experiments/MainWspmPreTree.java` appeared as a working-tree modification. The Repository Owner explicitly identified it as an intentional pre-existing edit and authorized C16 to proceed while preserving it. Its working-tree SHA-256 was `66200B07EEDB73DE6208972D2DDBFA01A60D6C0D544C74A96B63F36F6EB8A52D`, exactly matching the hash accepted by C15. C16 did not modify, stage, reset, or commit that file.

## 3. Environment and Reproducibility

```text
Java: Oracle JDK 21.0.12.1 LTS, HotSpot 64-Bit Server VM
Maven: Apache Maven 3.9.16
OS: Windows 11, amd64
Locale / platform encoding: en_US / UTF-8
```

The authoritative full-regression command was `mvn -q test`. Focused suites used `mvn -q clean -Dtest=... test` so Surefire XML counts were isolated rather than contaminated by reports from earlier runs.

## 4. Accepted Phase C Task Inventory

Roadmap tasks C00 through C15 are recorded as `ACCEPTED`. Their accepted scope covers:

- C00–C02: baseline handoff, mathematical contract, pruning proofs, architecture and API boundaries;
- C03–C05: packed SID bitmap, vertical temporal index, CMAP-I and CMAP-S;
- C06–C08: baseline-relative bitmap bound and exact I-/S-extension verification;
- C09–C10: deterministic candidate generation, Prefix Tree integration, evaluator, DFS and public preprocessing boundary;
- C11: three-oracle complete-map differential correctness for positive effective thresholds;
- C12: statistics accounting and first-failure routing;
- C13: six-scope measurement protocol;
- C14: final-result retention and deterministic output;
- C15: noninteractive standalone runner and execution orchestration.

C16 is moved only to `IN REVIEW` by this audit.

## 5. Frozen Baseline Protection

Source inspection confirms the baseline continues to use `PatternEncoding`, `PrimalBlockCodec`, and `PrimeBlockJoins.iJoin/sJoin` through `CandidateEngine`. It has not been replaced by a Bitmap or simple ID-list support engine. The baseline retains:

- Prime-block encoding and sequence/position rank semantics;
- exact distinct-SID support;
- separate I-Extension and S-Extension joins;
- itemset boundaries and repeated-item semantics;
- deterministic Prefix Tree DFS;
- inclusive weighted acceptance through the frozen `ResolvedMinimumSupport.meetsThreshold` predicate.

The focused baseline suite passed 157/157 tests across 11 Surefire suites, including Table 1, baseline miner, brute-force oracle, candidate engine, Prime-block codec, join, and join-differential tests.

## 6. Full Regression Evidence

Command:

```text
mvn -q test
```

Authoritative aggregation from `target/surefire-reports/TEST-*.xml`:

| Suites | Tests | Failures | Errors | Skipped | Exit code |
|---:|---:|---:|---:|---:|---:|
| 98 | 1,179 | 0 | 0 | 0 | 0 |

The count exactly matches the C15 historical reference. There was no test-count decrease and no newly skipped test.

## 7. Table 1 Evidence

Fresh focused baseline, three-oracle, integration, and standalone evidence establishes:

| Property | Verified value |
|---|---:|
| Sequences | 7 |
| Distinct items | 8 |
| Total item occurrences / database length | 66 |
| Item occurrence counts for items 1–8 | 23, 12, 11, 5, 4, 4, 6, 1 |
| Item weights | `23/66, 12/66, 11/66, 5/66, 4/66, 4/66, 6/66, 1/66` |
| MaxW | `23/66` |
| MinW | `1/66` |
| MeanW | `12/66` |
| Configured relative minimum support | `0.14` |
| Effective weighted threshold | `0.98` |
| F1 | `<(1)>:7`, `<(2)>:7`, `<(3)>:6` |
| Accepted patterns | 9 |

The complete accepted map is:

```text
<(1)>       -> 7
<(2)>       -> 7
<(3)>       -> 6
<(1 2)>     -> 7
<(1)(1)>    -> 6
<(1)(2)>    -> 7
<(1)(3)>    -> 6
<(2)(3)>    -> 6
<(1 2)(3)>  -> 6
```

The paper oracle, frozen WSPM_PreTree, independent `BruteForceWeightedPatternOracle`, and WSPM_BitPrune maps are equal. This is complete-map equality, not pattern-count-only evidence.

## 8. Three-Oracle Differential Evidence

The isolated differential command executed 130 tests across 11 suites with zero failures, errors, or skipped tests.

| Catalogue | Scope | Result |
|---|---|---|
| Table 1 | Paper, baseline, brute force, BitPrune complete maps | 9/9 exact patterns/supports |
| Controlled matrix | 9 database families, 13 configurations | 0 unexplained mismatches |
| Bounded exhaustive | 156 databases, 312 configurations, 936 pairwise map comparisons | 0 missing, unexpected, support, or unexplained mismatches |
| Fixed-seed randomized | seed `20261104L`, 1,200 trials, 3,600 pairwise comparisons | 0 missing, unexpected, support, or unexplained mismatches |

The randomized catalogue executed 604 absolute and 596 percentage configurations and observed 16,283 accepted patterns per oracle, including repeated-item and multiple-embedding cases.

## 9. Pruning Safety Reconciliation

| Layer | Classification | Accepted predicate and audit evidence |
|---|---|---|
| CMAP-I | Necessary-condition pruning | Reject only when required same-itemset co-occurrence is absent; seed `20261028L`, 4,399 comparisons, 0 false negatives |
| CMAP-S | Necessary-condition pruning | Reject only when required ordered cross-itemset relation is absent; 11,581 comparisons, 0 false negatives |
| Bitmap bound | Upper-bound pruning | Reject when candidate SID-cardinality weighted bound fails the frozen baseline Java `double` predicate; seed `20261029L`, 45,200 comparisons, 20,000 prune decisions, 0 baseline-relative false negatives |
| Exact temporal verification | Exact rejection | Distinct-SID structural verification after loose filters; exact I seed `20261030L` and exact S seed `20261031L`, each 1,200 randomized trials, 0 mismatches |

The evaluator uses first-failure routing: CMAP rejection terminates before Bitmap construction; Bitmap rejection terminates before exact verification; exact rejection occurs only after temporal verification; only accepted candidates are inserted and expanded. Bitmap and CMAP never substitute for exact temporal verification when order remains uncertain.

This evidence proves operational safety only relative to the frozen baseline Java `double` acceptance behavior over the exercised domains. It is not an exact-real arithmetic proof.

## 10. Candidate Generation and DFS Completeness

The focused component suite passed 462/462 tests across 25 suites. Candidate-generation evidence includes:

- seed `20261101L`: 1,500 candidate-engine randomized trials, 0 mismatches;
- 304 parents × 64 F1 presentations: 19,456 candidate comparisons, 0 mismatches;
- seed `20261102L`: 1,500 Prefix Tree integration trials, 0 mismatches;
- 9,540 bounded compatible Prefix Tree comparisons, 0 mismatches;
- seed `20261103L`: 1,500 evaluator trials, 0 mismatches;
- controlled public preprocessing: 73 expected and 73 actual patterns;
- prepared DFS boundary: 64 expected and 64 actual patterns.

I-Extension, S-Extension, canonical ordering, repeated items, accepted-child insertion, frozen-tree result assembly, and deterministic DFS traversal are covered.

## 11. Statistics Accounting

The statistics-focused command passed 110/110 tests across 4 suites. The result exposes 12 primary non-negative `long` counters and derived `rejectedExtensions`.

The verified conservation identities are:

```text
generatedExtensions = generatedIExtensions + generatedSExtensions
generatedExtensions = cmapRejected + bitmapRejected + exactRejected + acceptedExtensions
exactVerifications = exactRejected + acceptedExtensions
patternCount = f1Count + acceptedExtensions
rejectedExtensions = cmapRejected + bitmapRejected + exactRejected
```

The accumulator is package-private, invocation-local, rejects duplicate terminal routing, and publishes only an immutable completed snapshot. Controlled exact-vector tests cover each terminal outcome and a mixed four-outcome execution, preventing incompatible double-counting.

## 12. Six-Scope Measurement Reconciliation

The measurement/output/runner command passed 229/229 tests across 33 Surefire suites. All six scopes are present:

1. Dataset I/O.
2. Preprocessing.
3. DFS Mining.
4. Complete Algorithm.
5. Output I/O.
6. Total Execution.

Nanoseconds are stored internally; milliseconds are formatting-only. Memory is reported in decimal MB (`bytes / 1_000_000.0`) from `MemoryMXBean` sampled used heap at a reported 5 ms interval. No `System.gc()` dependency exists. Try-with-resources closes and joins the sampler on success and exception paths. Console formatting and printing occur after execution and outside Total Execution.

Sampled Java heap is not RSS, native-process memory, absolute allocation peak, or hardware RAM peak.

## 13. Output and Retention Verification

The single normative retention model remains `FINAL_SCIENTIFIC_RESULT`. `BitPruneMiningResult` retains:

- a frozen Prefix Tree;
- immutable accepted patterns in deterministic tree pre-order;
- an immutable exact pattern-to-support map;
- immutable statistics.

It does not retain candidate bitmaps, singleton bitmaps, CMAP tables, vertical occurrence indexes, endpoint states, verifier results, DFS frames, or samplers.

Projection/writer tests verify deterministic stable output, explicit UTF-8, LF delimiters, exactly one trailing LF for non-empty content, create/truncate behavior, parent-directory creation, zero-byte empty output, and raw `IOException` propagation. The full 8-combination matrix of `printPatterns`, `printStatistics`, and `exportPatterns` preserves identical mining results.

## 14. Standalone Runner Verification

Commands:

```text
mvn -q -DskipTests package
java -cp target/classes vn.edu.wspm.bitprune.experiments.MainWspmBitPrune
```

Both commands exited with code 0. The direct process:

- required no prompt, `Scanner`, or `System.in` input;
- used source-configured constants and the default Table 1 fixture;
- printed the four required configuration/statistics/memory/time sections;
- reported all six time/memory scopes;
- reported 9 weighted patterns with Table 1 counters `F1=3`, generated `34`, accepted extensions `6`, exact rejected `28`;
- created no output file while export was disabled;
- terminated normally, so no sampler thread remained alive in the process.

## 15. Git and Ignored Dataset Policy

`.gitignore` excludes Maven build outputs, compiled classes, IDE metadata, local documents, `data/raw/`, external `src/main/resources/datasets/*.txt`, and generated `results/*` while retaining research notes.

Audit commands found:

- no ignored file force-added to the index (`git ls-files -ci --exclude-standard` was empty);
- no tracked `target/`, generated output, IDE artifact, `data/raw/`, or external large dataset;
- expected ignored local entries only, including `.idea/`, source documents, `src/main/resources/`, and `target/`;
- no staged changes and no commit created by C16.

## 16. Source-Confirmed Behavior

- WSPM_PreTree uses Prefix Tree, Prime-block encoding, I-Extension, S-Extension, and distinct-sequence support.
- Item weight is occurrence count divided by total database item occurrences.
- global `MeanW = (MaxW + MinW) / 2`.
- weighted acceptance is inclusive: `support(P) × MeanW >= effectiveMinSup`.
- Bitmap and CMAP are filters; exact temporal verification is required when order is unresolved.
- Table 1 contains 7 sequences, 8 items, MeanW `12/66`, and the nine exact accepted pattern/support pairs listed above.

## 17. Implementation Inference

- Packed Java bitmap and SID normalization representation.
- Separate immutable CMAP-I and CMAP-S relation structures.
- Invocation-local first-failure statistics accumulator.
- `FINAL_SCIENTIFIC_RESULT` carrier decomposition and deterministic Prefix Tree pre-order projection.
- `MemoryMXBean` background sampling at 5 ms and immutable six-scope metric carriers.
- Source-constant standalone configuration with console rendering outside Total Execution.

These choices are repository-verified implementation policies; they are not attributed to research papers where the papers do not prescribe them.

## 18. Unspecified / Unresolved Issues

- Java `double` exact-real comparator equivalence remains unresolved.
- R1, R6, R7, and R8 remain open as recorded below.
- Zero effective minimum support is rejected operationally; enumeration semantics remain unspecified.
- Controlled FIMI routing is not natural sequential benchmark evidence.
- Direct truncate-and-write output is not atomic and provides no automatic rollback after partial I/O failure.
- Recursive DFS may exhaust the Java stack on an extreme accepted chain.

## 19. R1–R8 Status Matrix

| Issue | Status | C16 disposition |
|---|---|---|
| R1 — Sequence-Weight Discrepancy | OPEN | Preserved |
| R2 — Percentage Minimum Support | RESOLVED FOR IMPLEMENTATION | Preserved |
| R3 — Prime-Block Mechanics | RESOLVED | Preserved |
| R4 — Complete Table 1 Oracle | RESOLVED | Preserved and freshly verified |
| R5 — Item-Weight Denominator | RESOLVED | Preserved and freshly verified |
| R6 — Zero Minimum Support Semantics | OPEN | Preserved; excluded from acceptance scope |
| R7 — Transaction-to-Sequence Mapping | OPEN | Preserved; controlled FIMI routing only |
| R8 — SIGN Paper Benchmark Reproduction Discrepancy | OPEN | Preserved; no paper-equivalence claim |

## 20. Known Limitations and Non-Claims

This audit does not claim that BitPrune is universally correct over every unbounded database, that randomized testing is a formal proof, that Java `double` equals exact-real arithmetic, or that R6 is resolved. It makes no claim that BitPrune is faster, more memory efficient, more scalable, or eliminates overflow. No comparative Phase D benchmark was conducted.

## 21. Phase D Readiness Assessment

The repository has the required correctness, accounting, measurement, retention, output, and standalone evidence to be submitted for independent Phase C closure review. Phase D is not authorized by this report. Readiness becomes actionable only after an Independent Research Reviewer and Repository Owner accept C16 and formally close Phase C.

## 22. Artifact Hash Manifest

Before testing, SHA-256 hashes were captured for 171 tracked production, test, fixture, build, policy, and accepted-report artifacts. The same 171 paths were hashed after testing; mismatches were `0`.

| Artifact | SHA-256 |
|---|---|
| `PHASE_C_WSPM_BitPrune.md` before C16 update | `DCA60BA5CF6B7E008B4FBFC8F945D5767FFB2E906F133C77322974CFB71FCBC7` |
| `PHASE_C_WSPM_BitPrune.md` after C16 update | `FD644704BC33C0C5E3DD762E683940641DBC8E9F056A2F622167A5C329CA7B6A` |
| `docs/research/wspm-bitprune-c15-standalone-runner-report.md` | `054347B6FA282EE23BBFC7F27C649C98D5613E0879E16AB3D1D6F38998A11105` |
| `src/main/java/vn/edu/wspm/experiments/MainWspmPreTree.java` | `66200B07EEDB73DE6208972D2DDBFA01A60D6C0D544C74A96B63F36F6EB8A52D` |
| `src/main/java/vn/edu/wspm/bitprune/experiments/MainWspmBitPrune.java` | `5A0D831E0D7742CC2F6315222B38234BA6BA8E08E91B53EC5A7D48ED1DD1AED8` |
| `src/main/java/vn/edu/wspm/bitprune/mining/BitPruneMiner.java` | `849BDBB947A64F1420EC913E90C5B882E84BF2348497ABC6EAA003B0C14B85E0` |
| `src/test/resources/datasets/wspm/table1.spmf` | `F7E409F982EE42E0E34C7B940496074750AD8AEB44F191397EFEDDD0653AE8F5` |

The report's own final hash is recorded in the C16 handoff rather than embedded here, avoiding a self-referential digest.

## 23. Proposed Closure Decision

```text
C16 audit result: PASS — IN REVIEW
Phase C: IN PROGRESS — awaiting Independent Research Review and Repository Owner acceptance
Phase D: NOT STARTED
```

Recommended next action: **Independent Research Review — C16 Final Acceptance Report & Phase C Closure**.

**STOP:** Do not begin Phase D automatically.

---

## 24. Independent Research Review Disposition

The independent research review has been conducted and completed:
- **Reviewer:** Independent Research Reviewer, Data Mining Research Auditor & Senior Java Reviewer.
- **Review Date:** 2026-10-31 (Asia/Bangkok).
- **Executive Review Result:** **`PASS`**.
- **Deliverable:** [C16 Independent Research Review & Phase C Closure Report](wspm-bitprune-c16-independent-review-report.md).
- **Status Reconciliation:**
  - **Task C16:** **`ACCEPTED`**.
  - **Phase C:** **`COMPLETED`**.
  - **Phase D:** **`NOT STARTED`**.
- **Repository Owner Acceptance:** Formally provided to proceed with Phase C closure.
- **STOP:** Phase D must not be started automatically. No benchmark or comparative claim authorized.

