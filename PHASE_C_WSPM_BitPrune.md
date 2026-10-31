# PHASE C — WSPM_BitPrune
## Research Instructions, Implementation Rules & Task Checklist

**Project:** `wspm-pretree-bitprune`  
**Phase:** C — WSPM_BitPrune Research & Implementation  
**Language:** Java 21  
**Build:** Apache Maven  
**Testing:** JUnit 5  
**Status:** COMPLETED — C00–C16 ACCEPTED; PHASE C CLOSED; PHASE D NOT STARTED

---

# 1. PROJECT ROLE AND RESPONSIBILITY

You are an AI Coding Agent working inside the repository `wspm-pretree-bitprune`.

Your responsibility in Phase C is to support the research and implementation of:

**WSPM_BitPrune — Weighted Sequential Pattern Mining using Bitmap, CMAP and Multi-tier Lazy Pruning.**

You must act as:

- Senior Java 21 Developer.
- Data Mining Research Engineer.
- Algorithm Correctness Engineer.
- Scientific Reproducibility Assistant.

**Research correctness has higher priority than implementation speed.**

The Research Reviewer is responsible for accepting each task. You must not assume that implementing code or obtaining a green build automatically means a task has passed.

# 2. PHASE C SCOPE

## 2.1. In scope

- Shared mathematical semantics with WSPM_PreTree.
- SID Bitmap representation.
- Vertical occurrence and temporal position indexes.
- CMAP for I-extension and S-extension.
- Prefix Tree candidate generation.
- Bitmap cardinality upper bounds.
- Multi-tier Lazy Pruning.
- Exact temporal support verification.
- DFS mining.
- Mining statistics.
- Memory and time instrumentation.
- Standalone Java Main.
- Independent correctness tests.
- Full differential testing against the baseline.

## 2.2. Out of scope

Do not implement:

- Spring Boot.
- REST API.
- Database.
- Frontend.
- Product recommendation system.
- Full-scale comparative performance experiments belonging to Phase D.

Do not rewrite, replace, or optimize the frozen WSPM_PreTree baseline.

# 3. AUTHORITATIVE RESEARCH SOURCES

Before making algorithmic decisions, inspect the following sources.

1. `Mining_weighted_sequential_patterns_based_on_prefix_tree_and_prism_encoding_2023.pdf`
2. `WSPM_BitPrune_HoiThaoQuocGia_V1_2026_05_20.docx`
3. `CNTT-KLCN256_thuật toán WSPM_PreTree_Đã sửa.docx`
4. `AGENTS.md`
5. `README.md`
6. Existing Phase B research specifications, audit reports and correctness manifests.
7. B23 final acceptance report.
8. B24 Git freeze and fresh-checkout verification evidence.
9. Original PRISM research documentation when relevant.
10. Existing Java source code and JUnit tests.

For each significant algorithm decision, classify its basis:

**SOURCE-CONFIRMED:** Explicitly supported by a research source.

**IMPLEMENTATION POLICY / INFERENCE:** A design choice or interpretation made by the implementation team.

**UNRESOLVED:** Insufficient evidence to decide safely.

Never claim a source confirms an implementation detail that the source does not specify.

Never treat benchmark numbers in the 2026 manuscript as independently verified results.

# 4. PHASE B BASELINE PROTECTION

Phase B has already implemented WSPM_PreTree.

Do not restart Phase A or Phase B.

Do not initialize a new repository.

Do not recreate the baseline implementation.

## 4.1. Baseline Git reference

The Phase B freeze commit SHA reported in the repository archive inspection is:

`502e1ddb61d37751c2c5f606fbd30e3b90aefb6c`

This SHA must be independently verified against the actual local Git repository.

Do not assume that:

- The current HEAD points to this SHA.
- The worktree is clean.
- The B24 fresh checkout has passed.
- The local repository matches the archive exactly.

C00 must confirm these conditions with real evidence.

## 4.2. Forbidden Git operations

Unless explicitly authorized:

- Do not execute `git reset --hard`.
- Do not execute destructive `git clean`.
- Do not force push.
- Do not push to GitHub.
- Do not rewrite history.
- Do not modify `.gitignore`.
- Do not force-add ignored external datasets.
- Do not alter the frozen baseline implementation.

Before making changes, inspect `git status`, branch name and HEAD.

## 4.3. Baseline invariants

WSPM_PreTree retains:

- PRISM Prime-block Encoding.
- Prefix Tree.
- I-extension and S-extension.
- DFS mining.
- Rank-native GCD/CARD/MASK.
- Exact supporting SID/position semantics.
- Existing FULL_ENCODING and SUPPORT_ONLY retention policies.

BitPrune must use its own Bitmap/Vertical ID-list engine.

BitPrune must not depend on internal Prime-block implementation classes.

# 5. MATHEMATICAL CONTRACT

## 5.1. Item weight

For an item `i`:

`w(i) = occurrenceCount(i) / totalDatabaseItemOccurrences`

`occurrenceCount(i)` counts actual item occurrences across the entire sequence database.

Do not confuse occurrence count with sequence support.

## 5.2. Global MeanW

`MeanW = (MaxW + MinW) / 2`

This is a database-global constant, calculated from item weights.

Do not introduce a pattern-specific weight formula without explicit research authorization.

## 5.3. Exact support

`support(P)` is the number of distinct sequences containing pattern `P` as a structural subsequence.

A sequence contributes at most one unit of support, regardless of how many embeddings are found.

Pattern matching must preserve:

- Itemset boundaries.
- Temporal itemset ordering.
- Repeated items across different itemsets.
- Canonical item ordering within itemsets.
- Structural subsequence semantics.

## 5.4. Weighted support

`WSUP(P) = support(P) × MeanW`

Acceptance:

`WSUP(P) >= effectiveMinSup`

Do not replace the acceptance condition with an approximate test.

## 5.5. Minimum support

Relative minimum support:

`effectiveMinSup = relativeFraction × numberOfSequences`

Examples:

- `0.14` relative fraction means 14%.
- `0.004` relative fraction means 0.4%.

Percentage input must have explicit conversion semantics.

Absolute and relative weighted thresholds must not be mixed.

Do not round the threshold without a mathematically justified policy.

## 5.6. Zero-threshold behavior

Research issue R6 remains OPEN.

Before implementing zero-threshold behavior, determine whether the algorithm is intended to enumerate only database-supported patterns or all possible patterns under a bounded search space.

Never assume that ordinary support pruning remains valid when the threshold is zero.

# 6. BITPRUNE CORRECTNESS INVARIANTS

## 6.1. Bitmap representation

The initial Bitmap represents the set of sequence identifiers containing an item.

A candidate Bitmap is a necessary-condition representation, not necessarily an exact temporal match representation.

For every valid candidate `P`:

`ActualSupportingSID(P) ⊆ CandidateBitmapSID(P)`

Therefore:

`support(P) <= cardinality(candidateBitmap(P))`

An implementation may use a tighter bound only after proving that the tighter representation still contains every true supporting SID.

## 6.2. Weighted upper bound

With nonnegative `MeanW`:

`upperBoundWSUP(P) = cardinality(candidateBitmap(P)) × MeanW`

Safe candidate pruning requires:

`upperBoundWSUP(P) < effectiveMinSup`

Do not prune at equality.

## 6.3. CMAP safety

CMAP is a necessary-condition filter.

It must not reject a candidate whose extension relation can exist in the database.

Study separately:

- I-extension: item co-occurrence within the same itemset.
- S-extension: ordered occurrence across different itemsets.
- Ordered item pairs.
- Repeated item occurrences.
- SID boundaries.
- Itemset boundaries.
- Canonical item ordering.

Separate CMAP-I and CMAP-S structures may be chosen as an implementation policy.

Do not silently use a generic unordered co-occurrence test for temporal extensions.

## 6.4. Exact temporal verification

Bitmap intersection does not establish temporal ordering.

Exact verification must inspect structural positions or equivalent information.

For I-extension, the extended item must belong to the matching last itemset.

For S-extension, the extended item must occur in a strictly later itemset.

Multiple embeddings must not inflate sequence support.

## 6.5. DFS pruning safety

Distinguish:

1. Candidate rejection.
2. Candidate subtree elimination.

Subtree pruning requires a separate proof that all valid descendants fail the acceptance condition.

Under the baseline mathematical policy, MeanW is a nonnegative constant. This allows mathematical investigation of support antimonotonicity under valid subsequence extension.

However, do not reuse an unsafe branch-pruning decision merely because a candidate-level condition is safe.

For every pruning rule document:

- Predicate.
- Preconditions.
- Proof or counterexample analysis.
- Whether it rejects one candidate or an entire subtree.
- Boundary cases.
- Correctness tests.

# 7. TASK EXECUTION POLICY

All Phase C tasks use identifiers `C00`, `C01`, `C02`, etc.

Each task should represent approximately 30–90 minutes of focused human engineering work.

Large tasks must be split into smaller subtasks before implementation.

Codex must never start a task without a specific authorized task prompt.

After completing an authorized task:

1. Stop implementation.
2. Run required tests.
3. Perform self-review.
4. Submit Task Result.
5. Wait for Research Reviewer acceptance.

Task Result acceptance statuses:

- PASS.
- PARTIAL.
- BLOCKED.
- FAIL.

Only the Research Reviewer can authorize moving to the next task.

**A task marked PASS in Codex's own report is not automatically accepted.**

# 8. PHASE C TASK ROADMAP & CHECKLIST

The following roadmap is a proposed implementation sequence. It may be refined when research or code evidence requires splitting a task.

A checkbox may be marked complete only when the Research Reviewer has formally accepted the task.

## GATE 0 — BASELINE HANDOFF AND RESEARCH DESIGN

### C00 — Phase C Baseline Handoff & Research Readiness Audit

**Status:** ACCEPTED

**Goal:** Establish the verified baseline and research starting point.

Checklist:

- [x] Verify the actual Git branch and HEAD SHA.
- [x] Verify Phase B freeze commit SHA.
- [x] Inspect the actual worktree state.
- [x] Verify B23 acceptance evidence.
- [x] Verify B24 fresh-checkout evidence (qualified: clean-checkout execution remains REPORTED).
- [x] Identify all source modules and test packages.
- [x] Confirm Java 21, Maven and JUnit configuration.
- [x] Identify reusable shared domain and measurement components.
- [x] Identify protected baseline classes and packages.
- [x] Locate research specifications and reproducibility manifests.
- [x] Review R1–R8 without changing their status.
- [x] Document conflicts between source documents and implementation.
- [x] Prepare a research-readiness audit report.
- [x] Submit Task Result with evidence.

**Deliverable:** Baseline Handoff & Research Readiness Audit.

**Acceptance:** Research Reviewer: PASS (Freeze SHA verified; 363 tests PASS; B24 fresh-checkout log qualified as REPORTED; BLK-C00-01 resolved by confirming PHASE_C_WSPM_BitPrune.md as official Phase C instruction document).

**STOP:** Do not start C01.

### C01 — Source Fidelity & Mathematical Safety Design

**Status:** ACCEPTED

**Role:** Parent task for C01.1 and C01.2. The parent remains incomplete until both subtasks are formally accepted by the Research Reviewer.

**Goal:** Formalize BitPrune's source-derived mathematical behavior and establish reviewable pruning-safety proofs before implementation.

Checklist:

- [x] C01.1 is formally accepted by the Research Reviewer.
- [x] C01.2 is formally accepted by the Research Reviewer.
- [x] The two accepted deliverables are mutually consistent.
- [x] All source claims, implementation inferences and unresolved decisions are explicitly classified.
- [x] No pruning rule is approved without its C01.2 proof obligations and false-negative analysis.

**Deliverable:** The accepted C01.1 Semantic Contract together with the accepted C01.2 Pruning Safety Proofs & Test Design.

**Acceptance:** C01 may be marked `ACCEPTED` only after both C01.1 and C01.2 are individually marked `ACCEPTED` by the Research Reviewer. Completion of either subtask alone is insufficient.

**Reviewed Result:** Research Reviewer: `ACCEPTED` after integrated consistency review of the accepted C01.1 semantic contract and C01.2 pruning-safety deliverable. R6 zero-threshold semantics and the Java `double` comparator policy remain explicitly unresolved and are not treated as proved implementation behavior.

**STOP:** No Bitmap/CMAP implementation in this task.

#### C01.1 — Source Fidelity & Semantic Contract

**Status:** ACCEPTED

**Goal:** Establish the authoritative, unit-consistent semantic contract that C01.2 must use, without performing pruning-safety proofs or test design.

Checklist:

- [x] Extract relevant claims from the 2023 paper, 2026 manuscript, thesis proposal, Phase B specifications and frozen baseline behavior.
- [x] Link every material claim to an exact document section/page or source class/method.
- [x] Classify every material decision as `SOURCE-CONFIRMED`, `IMPLEMENTATION POLICY / INFERENCE` or `UNRESOLVED`.
- [x] Define patterns as ordered itemsets and preserve the distinction between `<(a)(b)>` and `<(ab)>`.
- [x] Define subsequence embeddings and exact support as the count of distinct supporting SIDs, not the number of embeddings.
- [x] Define I-Extension, S-Extension, canonical item ordering, repeated-item semantics and multiple-embedding semantics.
- [x] Reconcile absolute support, relative support, percentage input, normalized support and effective minimum support units.
- [x] Record Item Weight, MaxW, MinW, global MeanW and weighted acceptance semantics without changing the frozen baseline.
- [x] State the global-MeanW downward-closure proposition with all required assumptions and source conflicts; defer its formal proof to C01.2.
- [x] Define the intended semantic contracts for SID candidate Bitmap, CMAP-I and CMAP-S as necessary-condition filters; defer safety proofs to C01.2.
- [x] State the exact temporal verification contract and identify why earliest/latest endpoint compression is not assumed safe.
- [x] Preserve R1–R8 statuses and keep R6 zero-threshold semantics `OPEN`.
- [x] Produce the C01.2 proof-obligation handoff without proving, testing or implementing the pruning rules.

**Deliverable:** `docs/research/wspm-bitprune-c01-1-semantic-contract.md`.

**Acceptance:** The semantic contract is source-traceable, internally unit-consistent, preserves baseline definitions, identifies all unresolved decisions and provides an unambiguous proof-obligation handoff to C01.2.

**Reviewed Result:** Research Reviewer: `ACCEPTED` after remediation rounds C01.1-R1, C01.1-R2 and final acceptance round C01.1-R3. No further C01.1 changes are authorized unless a new defect is demonstrated.

**STOP:** Do not perform C01.2 proofs, construct its final test suite, implement code or update R1–R8.

#### C01.2 — Pruning Safety Proofs & Test Design

**Status:** ACCEPTED

**Prerequisite:** C01.1 must be formally `ACCEPTED` before C01.2 may be authorized.

**Goal:** Discharge the mathematical safety obligations established by the accepted C01.1 semantic contract and design tests targeting false-negative risk.

Checklist:

- [x] Prove support anti-monotonicity under valid I-/S-Extension.
- [x] Prove or refute global-MeanW WSUP downward closure under all stated assumptions, without generalizing to pattern-dependent weights.
- [x] Prove the SID candidate-Bitmap support upper bound.
- [x] Prove weighted upper-bound pruning safety for non-negative global MeanW.
- [x] Prove CMAP-I and CMAP-S are safe necessary-condition filters under their accepted definitions.
- [x] Distinguish candidate pruning from subtree pruning and prove every permitted subtree-pruning transition.
- [x] Analyze strict threshold inequality, exact equality and floating-point/unit boundaries.
- [x] Analyze zero threshold while preserving R6 as `OPEN`; do not invent a policy.
- [x] Specify exact temporal support verification for repeated items and multiple embeddings.
- [x] Analyze earliest/latest endpoint counterexamples and any proposed endpoint-compression preconditions.
- [x] Construct counterexamples for unsafe pruning rules and invalid generalizations.
- [x] Design small deterministic test vectors with explicit expected pattern-to-support mappings and pruning outcomes.
- [x] Produce the final pruning-safety audit without implementing Bitmap, CMAP or the miner.

**Deliverable:** `docs/research/wspm-bitprune-c01-2-pruning-safety.md`.

**Acceptance:** Every proposed pruning layer has a formally reviewable proof or is explicitly rejected/unresolved; test specifications cover equality, repeated items, multiple embeddings, temporal false positives and zero-threshold risk.

**Reviewed Result:** Research Reviewer: `ACCEPTED` after remediation rounds C01.2-R1 and C01.2-R2. OBL-01 through OBL-08 retain their accepted names and classifications; mathematical approval on real numbers does not constitute approval of a Java `double` comparator policy.

**STOP:** Do not implement C02 or any BitPrune component.

### C02 — Module Boundaries & API/Statistics Contract

**Status:** ACCEPTED

**Goal:** Design the BitPrune module without damaging the baseline.

Checklist:

- [x] Review reusable domain models.
- [x] Review parser and weight/minsup APIs.
- [x] Review Prefix Tree integration options.
- [x] Define Bitmap and positional-index interfaces.
- [x] Define BitPrune mining result contract.
- [x] Define candidate accounting states.
- [x] Define output and retention compatibility.
- [x] Review isolation from PRISM internals.
- [x] Document architecture tradeoffs.

**Deliverable:** `docs/research/wspm-bitprune-c02-architecture-contract.md` — BitPrune Architecture & Interface Specification.

**Acceptance:** Architecture is reviewable, compatible and sufficiently detailed for individual implementation tasks.

**Reviewed Result:** Research Reviewer: `ACCEPTED` after the final acceptance review and remediation rounds C02-R1, C02-R2 and C02-R2.1. R6 remains `OPEN`; the Java `double` comparator remains `UNRESOLVED / IMPLEMENTATION POLICY REQUIRED`, so C06 is not ready for implementation.

**STOP:** C02 is closed. Do not implement C03 or any mining component automatically.

## GATE 1 — BITPRUNE BUILDING BLOCKS

### C03 — SID Bitmap Representation & Bounds

**Status:** ACCEPTED

**Owner Acceptance:** Repository Owner accepted both execution units after their respective Research Reviewer `PASS` results and accepted parent C03.

- **C03.1 — SID Mapping & Packed Bitmap Core:** accepted evidence records 30 focused tests and 393 full-regression tests passing, protected-file SHA-256 values matching, and no PRISM dependency.
- **C03.2 — Candidate Semantics & Differential Evidence:** accepted evidence records 8 task tests, 38 focused tests, and 401 full-regression tests passing; a fixed-seed (`20261028L`) randomized differential run of 1,200 cases; an 11-pattern inclusion/upper-bound catalogue; CE-03 with candidate cardinality 1 and exact temporal support 0; and protected-file SHA-256 values matching.
- **Fresh verification for this acceptance update on 2026-10-28:** a local rerun confirmed 38 focused tests and 401 full-regression tests passing. The SHA-256 statements above remain acceptance-report evidence and were not newly generated by this roadmap update.

Checklist:

- [x] Implement SID Bitmap representation.
- [x] Define SID-to-bit mapping.
- [x] Implement safe Bitmap intersection.
- [x] Implement cardinality counting.
- [x] Define empty Bitmap behavior.
- [x] Test noncontiguous SID cases.
- [x] Test duplicate occurrences within a SID.
- [x] Test candidate Bitmap upper-bound inclusion.
- [x] Avoid unnecessary object allocations where practical.

**Deliverable:** Bitmap component and focused unit tests.

**Acceptance:** Bitmap operations preserve correct SID semantics.

### C04 — Vertical Occurrence / Temporal Position Index

**Status:** ACCEPTED

**Owner Acceptance:** Repository Owner accepted both execution units after their respective Research Reviewer `PASS` results and accepted parent C04.

- **C04.1 — `VerticalOccurrenceIndex`:** accepted evidence records 25 focused tests and 426 full-regression tests passing, exact agreement with an independent direct-scan oracle, preservation of actual SID and 1-based repeated-occurrence positions, mapping consistency by record order, defensive copying, immutable representation, no PRISM dependency and no scope violation.
- **C04.2 — `PatternEndpoints` / All-Endpoints Value Object:** accepted evidence records 37 focused tests, 62 index-package tests and 463 full-regression tests passing; preservation of every supplied endpoint; CE-01 `{1, 2}` and CE-02 `{1, 3}` without earliest-only/latest-only compression; immutable value semantics and defensive copying; no temporal join or exact verifier outside scope; and protected-file integrity reported as conforming.
- The counts above are historical acceptance evidence reported and adjudicated by the Research Reviewer and Repository Owner; they are not presented as newly generated acceptance evidence by this roadmap update. A separate local consistency rerun during this update passed 62 index-package tests and 463 full-regression tests.

Checklist:

- [x] Represent itemset positions by SID.
- [x] Preserve complete itemset boundaries.
- [x] Preserve repeated occurrences across itemsets.
- [x] Keep occurrences ordered.
- [x] Test sparse and dense sequences.
- [x] Test repeated-item sequences.
- [x] Validate against source sequence data.
- [x] Define immutable/shared index metadata where appropriate.

**Deliverable:** Vertical occurrence index with tests.

**Acceptance:** Indexed positions exactly represent the original sequence database.

### C05 — CMAP I/S Construction & Tests

**Status:** ACCEPTED

**C05.1 Owner Acceptance:** Repository Owner accepted C05.1 — CMAP-I Construction based on the submitted Task Result `PASS` and preliminary review. Accepted reported evidence records 24 focused tests and 487 full-regression tests passing, agreement with an independent direct-scan oracle for 10/10 canonical pairs, preservation of the caller-supplied F1 universe, fail-fast invalid queries, valid querying of F1 items without database occurrences, protected SHA-256 agreement for 35/35 paths, and no reported PRISM dependency or scope violation. These are Owner-accepted reported results; this roadmap update performed only a consistency audit and does not claim a new independent source-level verification.

**C05.2 Owner Acceptance:** Repository Owner accepted C05.2 — CMAP-S Construction after independent Research Review `PASS`. Fresh review evidence records 31 focused tests and 518 full-regression tests passing, exact agreement with an independent nested direct-scan oracle for 25/25 ordered pairs, correct directed/self-pair semantics, no same-itemset or cross-SID leakage, preservation of the caller-supplied F1 universe, no PRISM dependency, and matching protected integrity across 38 audited paths. The review reported no blocking finding; its integer-ID upper-bound strengthening note was non-blocking.

**C05.3 Owner Acceptance:** Repository Owner accepted C05.3 — Integrated Differential Safety following Research Review `PASS` and artifact reconciliation. Target artifact `src/test/java/vn/edu/wspm/bitprune/cmap/CmapDifferentialTest.java` is reconciled and verified at SHA-256 `B673898A028062EEA1734090F27EBBE25E094458C62CD1F2FA19EE9AB59A2D49`. Accepted historical review evidence records 12 focused tests, 67 CMAP package tests, and 530 full-regression tests passing; fixed-seed (`20261028L`) randomized differential verification across exactly 1,200 trials; 4,399 CMAP-I comparisons and 11,581 CMAP-S comparisons with 0 false positives and 0 false negatives; and 111/111 protected file manifest agreement. Fresh verification confirmed 12/12 focused, 67/67 package, and 530/530 full-regression tests passing with 0 protected mismatches across 111 paths and the target artifact hash unchanged before, during, and after execution.

- **C05.1 — CMAP-I Construction:** `ACCEPTED`.
- **C05.2 — CMAP-S Construction:** `ACCEPTED`.
- **C05.3 — Integrated Differential Safety:** `ACCEPTED`.
- Parent C05 is fully `ACCEPTED`; all checklist items have supporting implementation and differential test evidence.

Checklist:

- [x] Implement CMAP-I.
- [x] Implement CMAP-S.
- [x] Preserve ordered pair semantics where relevant.
- [x] Respect canonical I-extension ordering.
- [x] Handle repeated items across different itemsets.
- [x] Avoid cross-SID relationships.
- [x] Test true and false co-occurrence cases.
- [x] Compare against independent direct sequence scans.
- [x] Test CMAP false-negative prevention.

**Deliverable:** CMAP component and differential tests.

**Acceptance:** CMAP never rejects a genuinely possible extension relation under its defined contract.

### C06 — Bitmap Candidate Upper-Bound Filter

**Status:** ACCEPTED

**C06.1 Owner Acceptance:** Repository Owner accepted C06.1 — Bitmap Upper-Bound Filter Core after Independent Research Review `PASS`. Fresh accepted evidence records 25 focused tests and 555 full-regression tests passing. The accepted production artifact `src/main/java/vn/edu/wspm/bitprune/verification/BitmapUpperBoundFilter.java` has SHA-256 `CC599317460CED2DC0D6A67094544FE038CB2559E60C47D8FCDEE16F9DDE68C2`; the accepted focused test artifact `src/test/java/vn/edu/wspm/bitprune/verification/BitmapUpperBoundFilterTest.java` has SHA-256 `43DDA203926B032C1446832B80C1E0F8956394623D8F8629D273BD668D030BB1`. The filter delegates to the frozen baseline `ResolvedMinimumSupport.meetsThreshold` predicate under the explicitly limited `BASELINE_RELATIVE_MONOTONE_UPPER_BOUND` policy, is a no-op when the effective threshold is zero, and does not claim exact-real comparator safety. Its deterministic helper is classified as a baseline-relative conformance oracle, not an independent structural oracle.

**C06.2 Owner Acceptance:** Repository Owner accepted C06.2 — Exhaustive & Structural Differential Safety Evidence after Independent Research Review `PASS` and remediation of exact-rational provenance/oracle classification. Fresh accepted evidence records 12 focused differential tests, 37 combined C06 tests and 567 full-regression tests passing; fixed seed `20261029L`; exactly 1,200 randomized trials; 45,200 structural configurations; 20,000 filter-prune decisions; 11,300 threshold-zero no-op checks; 0 threshold-zero prune decisions; and 0 frozen-baseline false negatives. The exact-rational audit preserves the deterministic `14% × 25` classification `BASELINE_PRUNE_EXACT_KEEP`. The accepted artifact `src/test/java/vn/edu/wspm/bitprune/verification/BitmapUpperBoundFilterDifferentialTest.java` has SHA-256 `0C9FA4124BA147FB6AB92CCBCEFD8CF9B3B25A26D4D281D7AEA8B1EB30450894` after all validation commands.

- **C06.1 — Bitmap Upper-Bound Filter Core:** `ACCEPTED`.
- **C06.2 — Exhaustive & Structural Differential Safety Evidence:** `ACCEPTED`.
- Parent C06 is fully `ACCEPTED`; C06.1 and C06.2 have both passed Independent Research Review and Owner acceptance.

Checklist:

- [x] Build candidate Bitmap from independently computed safe SID-presence constraints for structural differential verification.
- [x] Compute cardinality-based support bound.
- [x] Compute weighted upper bound through the frozen baseline predicate.
- [x] Apply strict baseline-relative rejection equivalent to `< effectiveMinSup` under the accepted Java `double` operational policy.
- [x] Test equality with threshold.
- [x] Test empty candidate Bitmap.
- [x] Test deterministic false-positive/loose-bound Bitmap fixtures and validate them with the accepted C06.2 independent structural direct-scan oracle.
- [x] Compare bounds against independent direct-scan exact support over deterministic and fixed-seed tiny databases.
- [x] Document why no valid candidate is falsely rejected relative to the frozen baseline predicate, while preserving the exact-real counterexample and limitation.

**Deliverable:** Weighted upper-bound filtering component.

**Acceptance:** Exhaustive small-input and independent structural direct-scan checks confirm upper-bound safety relative to the frozen baseline Java `double` predicate; exact-real equivalence is not claimed.

### C07 — Exact I-Extension Temporal Verification

**Status:** ACCEPTED

**Owner Acceptance:** Repository Owner accepted parent C07 after C07.1 and C07.2 each passed Independent Research Review.

- **C07.1 — Exact I-Extension Verifier Core:** accepted evidence records 22 focused tests, 84 related tests and 589 full-regression tests passing on the final reviewed production artifacts. The implementation preserves canonical I-extension ordering, same-itemset positional matching, All-Endpoints state, distinct actual-SID support, multiple embeddings and isolation from PRISM internals.
- **C07.2 — Exhaustive & Randomized Differential Evidence:** accepted evidence records 4,096 endpoint-mask comparisons, a 2,800-sequence exhaustive structural catalogue over 121 canonical parent/extension pairs, 12 named boundary/counterexample cases and 1,200 fixed-seed randomized trials (`20261030L`), with zero differential mismatches. The final remediation corrected noncontiguous-SID coverage, reproducible mismatch diagnostics and zero-support evidence classification. Validation passed 15 focused, 37 combined C07, 99 related and 604 full-regression tests.
- The accepted C07.2 final artifact SHA-256 is `1276741B76016BE9E376689A26BED12D01AE730D9532A966DC3C0E032E93F29B`. C07 acceptance does not authorize C08 automatically.

Checklist:

- [x] Define I-extension positional join contract.
- [x] Require matching itemset positions.
- [x] Preserve prefix embeddings.
- [x] Prevent duplicate SID support counting.
- [x] Test same-itemset matches.
- [x] Test same-SID but different-itemset nonmatches.
- [x] Test multiple prefix embeddings.
- [x] Compare exact supports against independent oracle.

**Deliverable:** Exact I-extension verification component.

**Acceptance:** All I-extension support results match the oracle.

**Reviewed Result:** Research Reviewer: `ACCEPTED` after C07.1 core review and C07.2 exhaustive/randomized differential review with evidence-quality remediation. Repository Owner subsequently accepted parent C07. R6 and Java `double` comparator semantics remain outside C07 and unchanged.

**STOP:** C07 is closed. Do not implement C08 or any later task automatically.

### C08 — Exact S-Extension Temporal Verification

**Status:** ACCEPTED

**Owner Acceptance:** Repository Owner accepted parent C08 after C08.1 and C08.2 each passed Independent Research Review.

- **C08.1 — Exact S-Extension Verifier Core:** accepted evidence records 26 focused tests, 48 combined I-/S-extension tests and 630 full-regression tests passing on the final reconciled artifacts. The implementation preserves strict S-extension ordering, All-Endpoints state, repeated-item semantics, multiple embeddings, distinct actual-SID support and isolation from PRISM internals.
- **C08.2 — Exhaustive & Randomized Differential Evidence:** accepted evidence records 4,096 endpoint-mask comparisons, a 2,800-sequence exhaustive structural catalogue over 183 S-extension pairs and 1,200 fixed-seed randomized trials (`20261031L`), with zero differential mismatches. The final wording remediation correctly limits the claim to bounded exhaustive and empirical differential evidence; it is not presented as a formal proof over infinite sequence spaces. Validation passed 19 focused, 45 combined C08 and 649 full-regression tests.
- Final accepted SHA-256 values: production `ExactTemporalVerifier.java` — `D12D46A22E68F101DB1CB653520EFB95F98A4B728D448E22102B6AD463182885`; C08.1 test — `1EFCCFD5F4358EE64259E9094E5055EAF2A0D9AAA3D7CF2F760ABD568B14E23B`; C08.2 differential test — `67ED3F059C8A9E485BDABC955C58E417F2FD8B866A51430356CFAE1A0664C5AF`. C08 acceptance does not authorize C09 automatically.

Checklist:

- [x] Define S-extension temporal join contract.
- [x] Require strictly increasing itemset positions.
- [x] Handle repeated items.
- [x] Handle multiple prefix embeddings.
- [x] Reject reverse-order matches.
- [x] Reject same-itemset-only matches when sequence extension is required.
- [x] Count distinct supporting SIDs.
- [x] Compare exact support against independent oracle.

**Deliverable:** Exact S-extension verification component.

**Acceptance:** All S-extension support results match the oracle.

**Reviewed Result:** Independent Research Reviewer: `ACCEPTED` after C08.1 core review and C08.2 exhaustive/randomized differential review with research-claim wording remediation. Repository Owner subsequently accepted parent C08. R6 and Java `double` comparator semantics remain outside C08 and unchanged.

**STOP:** C08 is closed. Do not implement C09 or any later task automatically.

## GATE 2 — ENGINE INTEGRATION AND CORRECTNESS

### C09 — Candidate Generation & Prefix Tree Integration

**Status:** ACCEPTED

**Owner Acceptance:** Repository Owner accepted parent C09 after C09.1, C09.2 and C09.3 each passed Independent Research Review, including the C09.1-R1, C09.2-R1 and C09.3-R1/R2 evidence-integrity remediations.

- **C09.1 — Immutable Candidate Model & Deterministic Generator Core:** accepted structural generation preserves canonical I-extension semantics, S-extension semantics, itemset boundaries, uniqueness, deterministic I-before-S enumeration and input immutability without Prefix Tree mutation or PRISM coupling.
- **C09.2 — F1 Root Initialization & Accepted-child Prefix Tree Integration:** accepted integration keeps generation separate from retention, inserts only caller-accepted children, preserves immediate parent/edge identity, rejects invalid/frozen mutations and makes exact reinsertion idempotent.
- **C09.3 — Integrated Structural Differential Evidence:** accepted bounded evidence covers 64 ordered F1 presentations, 304 bounded parents, 9,540 compatible exhaustive comparisons and 1,500 fixed-seed randomized trials (`20261102L`), with zero mismatches. The final R2 remediation ensures non-null selection-mask diagnostics and derives expected selected-child ordering from the independent direct structural oracle. Final validation passed 67 focused and 708 full-regression tests.
- Final accepted SHA-256 values: `BitPruneCandidateEngine.java` — `8687AAD7FE0F8AC6AE16C0671F1727C03C7CB9B50977BA6CC46494DF1F5A50D8`; `BitPruneCandidateResult.java` — `FAD1978976BDF6EDF0305D46CE83DB239F8D775D262B7255FEB87A42CFE91A97`; `BitPruneCandidateEngineTest.java` — `5EFE72977E61A1DF5DE77F213B30EA02CAE32412D4AD68D13355F5F3CE2B8726`; `BitPruneCandidateEngineDifferentialTest.java` — `633D9964745C1EF92C09013389503834E10D8C510FE7E359F8591065733943F9`; `BitPrunePrefixTreeIntegrator.java` — `22AA996F926214D09FE580F5C9B1AEEDC759D67BE5AF3BA0F766FD1A7AB64A8D`; `BitPrunePrefixTreeIntegratorTest.java` — `960A76A7834754EDD1955B8EB959FED2888BF3985054CB5B268B809C82797D49`; `BitPruneCandidatePrefixTreeDifferentialTest.java` — `416B016B3618B9A1B96AA2B5C70681ECD9393534223DB0662E66A62F931369F1`.

Checklist:

- [x] Define candidate generation based on F1.
- [x] Apply canonical I-extension ordering.
- [x] Apply S-extension semantics.
- [x] Preserve itemset boundaries.
- [x] Prevent duplicate pattern identities.
- [x] Integrate with Prefix Tree structure.
- [x] Check parent-child pattern relationships.
- [x] Test candidate enumeration independently.
- [x] Compare with baseline candidate structure where appropriate.

**Deliverable:** Candidate generation and Prefix Tree integration.

**Acceptance:** Search space generation is deterministic and complete.

**Reviewed Result:** Independent Research Reviewer: `ACCEPTED` after C09.1 generator/model review, C09.2 integration review and C09.3 bounded exhaustive/randomized differential review with evidence-integrity remediation. Repository Owner subsequently accepted parent C09. The evidence is bounded and empirical, not a formal proof over the infinite pattern space.

**STOP:** C09 is closed. Do not implement C10 or any later task automatically. C10 requires a separate readiness audit and implementation contract authorized by the Repository Owner.

### C10 — Three-tier Lazy Pruning & DFS Miner

**Status:** ACCEPTED

**Accepted decomposition and evidence:**

- **C10.1 — Three-tier one-step candidate evaluator:** accepted immutable evaluation outcomes and ordered CMAP → Bitmap upper-bound → exact temporal verification, with deterministic boundary and validation tests.
- **C10.2 — Independent differential evaluator evidence:** accepted 1,836 bounded exhaustive comparisons and 1,500 fixed-seed randomized trials (`20261103L`), with zero mismatches across CMAP rejection, Bitmap rejection, exact rejection, acceptance, equality and threshold-zero no-prune boundaries. Final differential artifact SHA-256: `AB3999290DE078E2DA7B4C08D8666B808E35C40C6DF31C8422C91E48C768970D`.
- **C10.3 — Positive-threshold DFS orchestration:** accepted deterministic DFS, accepted-child-only Prefix Tree insertion, exact integer-support result map and full-bound controlled oracle evidence. The reviewed controlled evidence covered four databases and 16 configurations with 64 expected and 64 actual patterns, zero mismatches and maximum verified pattern length four. Final miner unit-test SHA-256: `25144D988EF37AABFF51FE77EED7156151AA20C4BE043046222F6F25B87EBE77`.
- **C10.4 — End-to-end preprocessing boundary:** accepted raw `SequenceDatabase`/`MinimumSupport` preprocessing through statistics, weights, F1, SID mapping, singleton Bitmap/endpoint state, occurrence index and CMAP before delegation to the accepted DFS overload. C10.4-R1 added deterministic fail-fast mismatch diagnostics. The independent raw-data oracle covered four databases and 16 configurations with 73 expected and 73 actual patterns, zero mismatches and maximum verified pattern length four. Final production SHA-256: `9F33F5D85C8FCAC95743A3BF34BB9AC2A5A5C992A8A1ECC9A961F02A1B751CC3`; final end-to-end test SHA-256: `A5D4543E17B9DA9D13100C2045496DB5CCE517DC930F8D1ACD9A4353C9752388`.
- Final independent acceptance rerun: 99 focused C10 tests and 807 full-regression tests passed with zero failures or errors. R6 remains `OPEN`: non-empty databases with effective threshold zero are rejected before mutation; C10 does not claim zero-threshold DFS semantics.

Checklist:

- [x] Integrate CMAP filtering.
- [x] Integrate Bitmap upper-bound filtering.
- [x] Integrate exact temporal verification.
- [x] Implement DFS traversal.
- [x] Preserve accepted pattern/support semantics.
- [x] Distinguish candidate pruning from subtree pruning.
- [x] Avoid redundant exact joins for safely rejected candidates.
- [x] Test pruning-disabled and pruning-enabled equivalence where feasible.
- [x] Test absence of false pruning.
- [x] Test DFS completion on controlled datasets.

**Deliverable:** Initial end-to-end WSPM_BitPrune miner.

**Acceptance:** Complete output matches the independent reference oracle on controlled inputs.

**Reviewed Result:** Independent Research Reviewer: `ACCEPTED` after C10.1 evaluator review, C10.2 bounded exhaustive/randomized differential review, C10.3 DFS/full-bound oracle review and C10.4 end-to-end preprocessing review with C10.4-R1 diagnostic remediation. Repository Owner subsequently accepted C10.4 and parent C10. Evidence is controlled and bounded; full cross-algorithm differential correctness remains the separate scope of C11.

**STOP:** C10 is closed. Do not implement C11 or any later task automatically. C11 requires a separate readiness audit and implementation contract authorized by the Repository Owner.

### C11 — Full Differential Correctness & Regression

**Status:** ACCEPTED — positive-effective-threshold operational scope; R6 zero-threshold semantics deferred

Checklist:

- [x] Verify the complete Table 1 nine-pattern oracle.
- [x] Compare exact pattern identities.
- [x] Compare exact integer supports.
- [x] Test I-extension and S-extension patterns.
- [x] Test repeated items.
- [x] Test multiple embeddings.
- [x] Test sparse and dense databases.
- [x] Test threshold equality.
- [ ] Test zero-threshold policy where resolved.
      DEFERRED / NOT APPLICABLE UNTIL R6 IS RESOLVED.
- [x] Test CMAP false pruning.
- [x] Test Bitmap false pruning.
- [x] Compare against frozen WSPM_PreTree.
- [x] Compare against independent brute-force oracle.
- [x] Investigate and document every mismatch.

**Deliverable:** [docs/research/wspm-bitprune-c11-differential-report.md](docs/research/wspm-bitprune-c11-differential-report.md).

**Acceptance:** Identical `Map<Pattern, Integer>` outputs for all specified correctness configurations.

**Reviewed Result:** Independent Research Reviewer: `PASS` across all units (C11.1 Table 1 oracle, C11.2 controlled boundary matrix, C11.3 bounded exhaustive catalogue, C11.4-R1 fixed-seed randomized differential suite). Repository Owner formally accepted C11.1–C11.4 and closed parent C11 for the positive-effective-threshold operational scope. Combined C11 test suite passed with 38/38 tests; full regression passed with 845/845 tests across 61 reports with zero failures, zero errors, and zero mismatches. R6 zero-minimum-support semantics remains OPEN and deferred. Exact-real floating-point safety remains unresolved.

**STOP:** C11 is closed. Do not begin C12, statistics instrumentation or performance work automatically. C12 requires a separate readiness review and Repository Owner authorization.

## GATE 3 — INSTRUMENTATION, OUTPUT AND ACCEPTANCE

### C12 — Mining Statistics & Accounting Invariants

**Status:** ACCEPTED

Checklist:

- [x] Define generated candidate counter.
- [x] Define CMAP-pruned counter.
- [x] Define Bitmap-bound-pruned counter.
- [x] Define exact-support-rejected counter.
- [x] Define accepted-extension counter.
- [x] Define F1 pattern count.
- [x] Define total weighted pattern count.
- [x] Prevent counter overlap.
- [x] Test counter accounting invariants.
- [x] Match reported counts to verified events.

**Deliverable:** [docs/research/wspm-bitprune-c12-statistics-accounting-report.md](docs/research/wspm-bitprune-c12-statistics-accounting-report.md).

**Acceptance:** Every candidate has exactly one final accounting outcome.

**Reviewed Result:** Independent Research Reviewer: `PASS` after C12.1 immutable statistics model review, C12.2/C12.2-R1 accumulator ownership and first-failure routing review, C12.3/C12.3-R1 miner integration and exact controlled-oracle review, and C12.4/C12.4-R1/C12.4-R2 evidence-fidelity remediation. Repository Owner formally accepted parent C12. The accepted evidence covers 12 primary `long` counters, derived `rejectedExtensions`, checked overflow behavior, mutually exclusive terminal outcomes, exact mixed-outcome accounting, immutable completed-run statistics, and source-compatible legacy mining APIs. Statistics-focused tests passed 110/110, miner regression passed 82/82, C11 differential tests passed 38/38 with zero mismatches, and full regression passed 955/955 across 65 reports. R6 zero-threshold semantics remains OPEN; Java `double` exact-real safety remains unresolved; C13 measurement is NOT STARTED.

**STOP:** C12 is closed. Preserve the accepted statistics implementation, tests and research report. Do not begin C13 automatically; C13 requires a separate readiness audit and Repository Owner authorization.

### C13 — Time/Memory Instrumentation Parity

**Status:** ACCEPTED

Checklist:

- [x] Reuse the established Phase B measurement protocol.
- [x] Measure Dataset I/O Time.
- [x] Measure Preprocessing Time.
- [x] Measure DFS Mining Time.
- [x] Measure Algorithm Time.
- [x] Measure Output I/O Time.
- [x] Measure Total Execution Time.
- [x] Report time in milliseconds.
- [x] Report memory in MB (`bytes / 1_000_000.0`).
- [x] Sample heap using Java MemoryMXBean.
- [x] Report sampling interval.
- [x] Distinguish sampled peak from absolute peak.
- [x] Prevent phase-scope overlap or mislabeling.
- [x] Test measurement lifecycle cleanup.
- [x] Document GC and sampler limitations.

**Deliverable:** Full six-scope measurement instrumentation integrated and accepted across C13 algorithm scopes (Scopes 2–4) and C15 execution scopes (Scopes 1, 5, 6). Deliverables: `BitPruneAlgorithmMetrics`, `BitPruneExecutionMetrics`, `ExecutionMemorySampler`, `PhaseExecutionMetrics`, and comprehensive report [docs/research/wspm-bitprune-c15-standalone-runner-report.md](docs/research/wspm-bitprune-c15-standalone-runner-report.md).

**Acceptance:** All six measurement scopes (Dataset I/O, Preprocessing, DFS Mining, Complete Algorithm, Output I/O, Total Execution) are verified with runtime evidence. Parent C13 is fully `ACCEPTED`.

**Reviewed Result:** Algorithm scopes 2–4 were established in C13.1–C13.4. Runtime orchestration for Scope 1 (Dataset I/O), Scope 5 (Output I/O), and Scope 6 (Total Execution) was completed in C15.2 and verified through C15.4 end-to-end integration tests. Containment invariants ($\text{Total} \ge \text{Dataset I/O} + \text{Algorithm} + \text{Output I/O}$ and $\text{Algorithm} \ge \text{Preprocessing} + \text{DFS}$) hold across all runs. Full regression passes 1,179/1,179 tests across 98 Surefire reports. Observed memory is strictly documented as periodic sampled JVM heap from `MemoryMXBean.getHeapMemoryUsage().getUsed()` (not RSS or absolute hardware peak). Console formatting and printing are strictly excluded from Total Execution time. R6 zero-threshold semantics remain OPEN and exact-real comparator safety remains UNRESOLVED (not part of measurement acceptance).

**CLOSURE:** C13 is fully closed and accepted. All six standardized measurement scopes are operational.

### C14 — Result Retention & Stable Output

**Status:** ACCEPTED

**Owner Acceptance:** Repository Owner accepted C14.1 (Stable Output Projection Model), C14.1-R1 (trailing-LF remediation), C14.2 (Stable Output Writer), C14.2-R1 (trailing-LF remediation), the C14 Retention Decision verification, and formally closed parent C14.

- **Normative Retention Model:** WSPM_BitPrune operates under a single Owner-approved `FINAL_SCIENTIFIC_RESULT` model (semantic policy, no synthetic enum). The immutable completed-run carrier `BitPruneMiningResult` preserves: (1) frozen `PrefixTree`, (2) immutable `acceptedPatterns` in deterministic tree pre-order, (3) immutable exact `Map<Pattern, Integer>` supports, and (4) immutable `BitPruneStatistics`.
- **Transient Memory Isolation:** Transient mining structures (`SidBitmap`, candidate bitmaps, singleton bitmaps, `PatternEndpoints`, `VerificationResult`, `VerticalOccurrenceIndex`, `CmapI`, `CmapS`, DFS recursive frames, and memory samplers) are not referenced by `BitPruneMiningResult` or `MeasuredBitPruneRun`. When mining completes and caller references are dropped, these structures become eligible for Garbage Collection. No immediate reclamation or unmeasured heap-reduction claim is made.
- **Rejected / Deferred APIs:** `BitPruneRetentionPolicy`, `FULL_ENCODING`, `SUPPORT_ONLY` enum, `BitPrunePatternState`, and Phase B `ResultRetentionPolicy` reuse are explicitly rejected. `FULL_AUDIT_STATE` is deferred to future research scope.
- **C14.1 — Stable Output Projection:** provides `BitPruneOutputEntry` and `BitPruneOutputProjection` in `vn.edu.wspm.bitprune.result`. In-memory projection transforms `BitPruneMiningResult` into deterministic SPMF-compatible lines (`<pattern.serialize()> #SUP: <support>\n`) strictly using LF (`\n`), no BOM, ending in exactly one trailing LF when non-empty, and producing zero bytes / empty string for empty results. 23 focused unit tests PASS.
- **C14.2 — Stable Output Writer:** provides `BitPruneOutputWriter` in `vn.edu.wspm.bitprune.io`. Stateless physical writer writes `BitPruneOutputProjection` directly to disk using standard UTF-8, create/truncate (`CREATE`, `TRUNCATE_EXISTING`, `WRITE`), automatic parent directory creation, zero-byte file creation on empty projections, resource lifecycle managed internally by Files.writeString, no retained file handles, and transparent IOException propagation. 20 focused unit tests PASS.
- **Test Evidence:** 43 output-subsystem focused tests (20 writer + 23 projection) PASS; 69 closure-compatibility tests (20 writer + 23 projection + 14 mining result + 12 measurement integration) PASS; 1,073 full-regression tests PASS across 73 Surefire reports with 0 failures, 0 errors, and 0 skipped.

Checklist:

- [x] Evaluate FULL_ENCODING equivalent behavior.
  EVALUATED — NOT IMPLEMENTED BY OWNER DECISION.
- [x] Evaluate SUPPORT_ONLY result retention.
  SATISFIED BY CURRENT SINGLE FINAL-RESULT MODEL.
- [x] Preserve Pattern → Support results.
  SATISFIED.
- [x] Avoid retaining unnecessary temporal encodings in benchmark mode.
  SATISFIED.
- [x] Ensure deterministic result iteration/export.
  SATISFIED BY C14.1 AND C14.2.
- [x] Test result equality across supported retention policies.
  NOT APPLICABLE — ONLY ONE SUPPORTED RETENTION MODEL.
- [x] Document memory implications.
  SATISFIED QUALITATIVELY; NO QUANTIFIED MEMORY-SAVING CLAIM.

**Deliverable:** [docs/research/wspm-bitprune-c14-retention-output-report.md](docs/research/wspm-bitprune-c14-retention-output-report.md).

**Acceptance:** Result retention model and stable output writer preserve mining correctness, provide deterministic pre-order projection, and maintain 100% regression compatibility across all 1,073 tests.

**Reviewed Result:** Independent Research Reviewer: `PASS` across C14.1, C14.1-R1, C14.2, C14.2-R1; Repository Owner: `ACCEPTED` parent C14 and approved normative retention model. All 17 protected hashes match. (At C14 closure time, C13 remained `PARTIAL` with scopes 1, 5, 6 pending C15 runner orchestration, and C15/C16 remained `NOT STARTED`. Subsequent C15 closure accepted C13 and C15; C16 is now separately authorized and `IN REVIEW`.)

**STOP:** C14 is closed. Do not begin C15 automatically. C15 requires a separate readiness audit and implementation contract authorized by the Repository Owner.

### C15 — Standalone Java Main & Configuration

**Status:** ACCEPTED

**Owner Acceptance:** Repository Owner accepted C15.0 (Metadata Exposure), C15.1/C15.1-R1 (Immutable Run Configuration Model), C15.2 (Standalone Runner & Six-Scope Orchestration), C15.3/C15.3-R1 (Console Formatting & Flag Matrix), C15.4 (Executable Main, Table 1 Oracle & Lifecycle Evidence), and formally closed parent C15.

Checklist:

- [x] Create or integrate standalone BitPrune Main.
- [x] Support dataset path configuration.
- [x] Support dataset format configuration.
- [x] Support clear minimum-support semantics.
- [x] Support `PRINT_PATTERNS`.
- [x] Support `EXPORT_PATTERNS`.
- [x] Support `PRINT_STATISTICS`.
- [x] Support result retention configuration where applicable. (NOT APPLICABLE AS A SWITCH — single normative FINAL_SCIENTIFIC_RESULT model)
- [x] Print INPUT CONFIGURATION.
- [x] Print MINING STATISTICS.
- [x] Print MEMORY STATISTICS (MB).
- [x] Print TIME STATISTICS (ms).
- [x] Run directly in VS Code.
- [x] Do not use Scanner.
- [x] Do not require interactive terminal input.

**Deliverable:** Standalone executable BitPrune entry point in [`MainWspmBitPrune`](src/main/java/vn/edu/wspm/bitprune/experiments/MainWspmBitPrune.java) and research report [docs/research/wspm-bitprune-c15-standalone-runner-report.md](docs/research/wspm-bitprune-c15-standalone-runner-report.md).

**Acceptance:** Reproducible noninteractive mining run across all six scopes; complete Table 1 nine-pattern oracle match; baseline complete-map differential equality; full eight-combination flag matrix; exception lifecycle cleanup matrix.

**Reviewed Result:** Independent Research Reviewer: `PASS` across C15.0–C15.4; Repository Owner: `ACCEPTED` parent C15. 8 editable source-configured constants; `public static void main(String[] args)` delegating to C15.3 console overload; exact Table 1 oracle reproduction (7 sequences, 8 distinct items, MeanW 12/66, effective threshold 0.98, exactly 9 weighted sequential patterns with identical support mapping to frozen baseline `WspmPreTreeMiner`); full six-scope containment verified; complete 8-combination flag matrix tested against independent reference executions; exception lifecycle cleanup verified on malformed parser input, R6 zero-threshold rejection, and writer I/O failure (0 leaked sampler threads); direct Java process execution (`java -cp target/classes vn.edu.wspm.bitprune.experiments.MainWspmBitPrune`) exits with code 0 without prompts; 1,179/1,179 full regression PASS across 98 Surefire reports.

**Historical STOP (satisfied):** C15 closed without beginning C16 automatically. C16 was later authorized separately and is now `IN REVIEW`.

### C16 — Final Regression & Phase C Acceptance

**Status:** ACCEPTED

Checklist:

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

**Deliverable:** [C16 Final Regression & Phase C Acceptance Audit](docs/research/wspm-bitprune-c16-final-acceptance-report.md) and [C16 Independent Research Review & Phase C Closure Report](docs/research/wspm-bitprune-c16-independent-review-report.md).

**Acceptance:** Complete correctness evidence, clean scope, reproducible test commands and explicit research limitations.

**Reviewed Result:** Independent Research Reviewer: `PASS`; Repository Owner: `ACCEPTED` parent C16. Independent review directly verified: full regression 98 Surefire suites / 1,179 tests PASS (0 failures, 0 errors, 0 skipped); 157 focused baseline tests PASS across 11 suites preserving PRISM Prime-block joins; 130 differential tests PASS across 11 suites with 0 missing, unexpected, support, or unexplained mismatches across Table 1, controlled, bounded exhaustive, and fixed-seed randomized catalogues; Table 1 complete four-way map equality across published paper oracle, frozen baseline, brute-force oracle, and BitPrune; 110 statistics accounting tests PASS with 4 checked conservation invariants; all six measurement scopes verified with internal nanoseconds and 5 ms sampled heap MB; deterministic `FINAL_SCIENTIFIC_RESULT` retention and UTF-8 LF writer verified across full 8-combination flag matrix; standalone runner exits with code 0 without prompts; protected artifacts unchanged. C16 is formally ACCEPTED; Phase C is COMPLETED; Phase D remains NOT STARTED.

**STOP:** Do not begin Phase D automatically. Phase C is `COMPLETED`; Phase D remains `NOT STARTED`.

# 9. CORRECTNESS TEST CONTRACT

Correctness testing must compare complete results:

`Map<Pattern, Integer>`

The following is insufficient:

`numberOfPatterns(baseline) == numberOfPatterns(bitprune)`

For every discrepancy, report:

- Dataset and format.
- Minimum support configuration.
- Expected pattern/support.
- Actual pattern/support.
- Candidate generation trace.
- CMAP decision.
- Bitmap bound.
- Exact temporal support decision.

Do not change expected values merely to make a test pass.

Do not modify the independent oracle to imitate optimized algorithm behavior.

# 10. STATISTICS CONTRACT

The implementation should distinguish between F1 roots and generated extension candidates.

When mutually exclusive candidate outcome categories are defined:

`generatedExtensions = cmapPruned + bitmapPruned + exactRejected + acceptedExtensions`

This identity is valid only when every generated extension belongs to exactly one terminal category and there are no additional outcome categories.

Total accepted patterns:

`totalPatterns = acceptedF1 + acceptedExtensions`

Counter definitions must be documented before implementation.

Do not count an F1 root as an extension candidate unless explicitly stated.

Do not double-count candidates rejected by different pruning tiers.

# 11. MEASUREMENT AND BENCHMARK POLICY

Memory values must use MB, not MiB.

`memoryMB = bytes / 1_000_000.0`

The following values must remain distinguishable:

- Dataset I/O heap.
- Preprocessing heap.
- Mining heap.
- Algorithm peak heap.
- Total execution peak heap.
- Output I/O heap when applicable.

Do not add phase peak memory values together.

A sampled Java heap peak is not equivalent to operating-system process RSS.

Benchmark comparisons require equivalent:

- Dataset bytes and hashes.
- Input format.
- Minimum-support conversion.
- JDK/JVM.
- Machine and heap configuration.
- Result retention policy.
- Output policy.
- Warm-up policy.
- Repetition policy.
- Measurement scope.

Phase C establishes measurement correctness.

Comparative performance conclusions belong to Phase D.

# 12. RESEARCH ISSUES

Preserve the existing canonical Phase B research issue tracker.

- R1 — Sequence-Weight Discrepancy: OPEN.
- R2 — Percentage Minimum Support: RESOLVED FOR IMPLEMENTATION.
- R3 — Prime-Block Mechanics: RESOLVED.
- R4 — Complete Table 1 Oracle: RESOLVED.
- R5 — Item-Weight Denominator: RESOLVED.
- R6 — Zero Minimum Support Semantics: OPEN.
- R7 — Transaction-to-Sequence Mapping: OPEN.
- R8 — SIGN Paper Benchmark Reproduction Discrepancy: OPEN.

Do not rename or change the status of R1–R8 without review.

New Phase C issues may be assigned R9 onward if necessary.

# 13. TASK RESULT FORMAT

Each task must end with the following report.

## Task Result — Cxx

**Status:** PASS / PARTIAL / BLOCKED / FAIL

### 1. Objective
What this specific task was authorized to accomplish.

### 2. Implementation / Research Completed
What was actually completed.

### 3. Files Changed
List every added, edited or deleted file.

### 4. Tests Executed
Include exact commands and results, distinguishing PASS, FAIL and SKIP.

### 5. Research Fidelity

**SOURCE-CONFIRMED:**
- Evidence-supported decisions.

**IMPLEMENTATION POLICY / INFERENCE:**
- Explicitly chosen implementation details.

**UNRESOLVED:**
- Questions requiring additional evidence.

### 6. Mathematical Invariants
Describe how the task preserves its relevant correctness properties.

### 7. Acceptance Checklist
Mark each task-specific acceptance criterion as verified or not verified.

### 8. Risks / Deviations
Describe unresolved correctness or compatibility risks.

### 9. Git State
Report branch, HEAD SHA and affected files.

### 10. Recommended Next Step
Recommend only the next authorized review or task.

### 11. STOP Confirmation
Confirm that no subsequent task was started automatically.

# 14. PHASE C PROGRESS LOG

Update this table only after review.

| Task | Status | Reviewed Result | Notes |
|---|---|---|---|
| C00 | ACCEPTED | Research Reviewer: PASS | Freeze SHA verified; 363 tests PASS; B24 fresh-checkout log remains REPORTED |
| C01 | ACCEPTED | Research Reviewer: PASS | Integrated consistency review passed; C01.1 and C01.2 are both accepted |
| C01.1 | ACCEPTED | Research Reviewer: PASS | Source fidelity and semantic contract; C01.1-R3 led to final acceptance |
| C01.2 | ACCEPTED | Research Reviewer: PASS | Pruning proofs and test design; C01.2-R2 led to final acceptance |
| C02 | ACCEPTED | Research Reviewer: PASS | Architecture contract accepted after remediation rounds C02-R1, C02-R2 and C02-R2.1; deliverable: `docs/research/wspm-bitprune-c02-architecture-contract.md` |
| C03 | ACCEPTED | Research Reviewer: PASS for C03.1 and C03.2; Repository Owner: ACCEPTED parent C03 | C03.1: 30 focused / 393 full PASS (accepted evidence). C03.2: 8 task / 38 focused / 401 full PASS, 1,200 fixed-seed differential cases, 11-pattern catalogue, CE-03 verified (accepted evidence). Fresh final verification: 38 focused / 401 full PASS |
| C04 | ACCEPTED | Research Reviewer: PASS for C04.1 and C04.2; Repository Owner: ACCEPTED parent C04 | C04.1: 25 focused / 426 full PASS. C04.2: 37 focused / 62 index-package / 463 full PASS. Counts are accepted historical review evidence; separate roadmap-update consistency rerun: 62 index-package / 463 full PASS |
| C05 | ACCEPTED | Research Reviewer: PASS for C05.1, C05.2 and C05.3; Repository Owner: ACCEPTED parent C05 | C05.1 CMAP-I: 24 focused / 487 full PASS and 10/10 oracle pairs (Owner-accepted reported evidence). C05.2 CMAP-S: fresh Research Review confirmed 31 focused / 518 full PASS, 25/25 ordered oracle pairs and 38 protected paths. C05.3 integrated differential safety: 12 focused / 67 CMAP-package / 530 full-regression PASS; fixed seed `20261028L`, 1,200 trials, 4,399 CMAP-I and 11,581 CMAP-S comparisons, 0 false positives and 0 false negatives; target artifact SHA-256 `B673898A028062EEA1734090F27EBBE25E094458C62CD1F2FA19EE9AB59A2D49`. C05.1, C05.2, C05.3 and parent C05 are ACCEPTED |
| C06 | ACCEPTED | Independent Research Reviewer: PASS for C06.1 and C06.2; Repository Owner: ACCEPTED parent C06 | C06.1 filter core and C06.2 exhaustive/structural differential evidence are accepted; operational safety is baseline-relative, while R6 and exact-real equivalence remain unresolved |
| C06.1 | ACCEPTED | Independent Research Reviewer: PASS; Repository Owner: ACCEPTED | 25 focused / 555 full-regression PASS; production SHA-256 `CC599317460CED2DC0D6A67094544FE038CB2559E60C47D8FCDEE16F9DDE68C2`; test SHA-256 `43DDA203926B032C1446832B80C1E0F8956394623D8F8629D273BD668D030BB1`; baseline-relative policy only; R6 and exact-real safety remain unresolved |
| C06.2 | ACCEPTED | Independent Research Reviewer: PASS; Repository Owner: ACCEPTED | 12 focused / 37 combined C06 / 567 full-regression PASS; seed `20261029L`; 1,200 trials; 45,200 configurations; 20,000 prune decisions; 11,300 threshold-zero no-op checks; 0 threshold-zero prunes; 0 frozen-baseline false negatives; final artifact SHA-256 `0C9FA4124BA147FB6AB92CCBCEFD8CF9B3B25A26D4D281D7AEA8B1EB30450894` |
| C07 | ACCEPTED | Independent Research Reviewer: PASS for C07.1 and C07.2; Repository Owner: ACCEPTED parent C07 | C07.1 exact I-extension verifier core: 22 focused / 84 related / 589 full-regression PASS. C07.2 differential evidence: 4,096 endpoint-mask comparisons, 2,800 exhaustive sequences, 121 canonical pairs, 1,200 fixed-seed trials (`20261030L`), 0 mismatches, 15 focused / 37 combined C07 / 99 related / 604 full-regression PASS; final artifact SHA-256 `1276741B76016BE9E376689A26BED12D01AE730D9532A966DC3C0E032E93F29B` |
| C08 | ACCEPTED | Independent Research Reviewer: PASS for C08.1 and C08.2; Repository Owner: ACCEPTED parent C08 | C08.1 exact S-extension verifier core: 26 focused / 48 combined I-/S-extension / 630 full-regression PASS. C08.2 differential evidence: 4,096 endpoint-mask comparisons, 2,800 exhaustive sequences, 183 S-extension pairs, 1,200 fixed-seed trials (`20261031L`), 0 mismatches, 19 focused / 45 combined C08 / 649 full-regression PASS; final artifact SHA-256 `67ED3F059C8A9E485BDABC955C58E417F2FD8B866A51430356CFAE1A0664C5AF` |
| C09 | ACCEPTED | Independent Research Reviewer: PASS for C09.1, C09.2 and C09.3; Repository Owner: ACCEPTED parent C09 | Deterministic structural generation and accepted-child Prefix Tree integration; 9,540 bounded exhaustive comparisons, 1,500 fixed-seed trials (`20261102L`), 67 focused / 708 full-regression PASS; final C09.3 SHA-256 `416B016B3618B9A1B96AA2B5C70681ECD9393534223DB0662E66A62F931369F1` |
| C10 | ACCEPTED | Independent Research Reviewer: PASS for C10.1, C10.2, C10.3 and C10.4; Repository Owner: ACCEPTED parent C10 | Three-tier evaluator, deterministic positive-threshold DFS and end-to-end preprocessing accepted; 1,836 bounded exhaustive evaluator comparisons, 1,500 fixed-seed trials (`20261103L`), controlled full-map evidence of 64/64 patterns for prepared DFS and 73/73 patterns for the public preprocessing boundary, all with zero mismatches; 99 focused C10 / 807 full-regression PASS; final miner SHA-256 `9F33F5D85C8FCAC95743A3BF34BB9AC2A5A5C992A8A1ECC9A961F02A1B751CC3`; final end-to-end test SHA-256 `A5D4543E17B9DA9D13100C2045496DB5CCE517DC930F8D1ACD9A4353C9752388`; R6 remains OPEN |
| C11 | ACCEPTED | Independent Research Reviewer: PASS for C11.1–C11.4; Repository Owner: ACCEPTED parent C11 | Positive-threshold complete-map equality against Table 1, baseline and brute-force oracles; 38 focused C11 / 845 full-regression PASS; deliverable: `docs/research/wspm-bitprune-c11-differential-report.md`; R6 deferred |
| C12 | ACCEPTED | Independent Research Reviewer: PASS through C12.4-R2; Repository Owner: ACCEPTED parent C12 | 12 primary long counters; derived `rejectedExtensions`; first-failure routing; exact controlled oracles (23/23 integration tests); 110 statistics-focused / 82 miner / 38 C11 differential / 955 full-regression PASS; deliverable: [docs/research/wspm-bitprune-c12-statistics-accounting-report.md](docs/research/wspm-bitprune-c12-statistics-accounting-report.md); R6 and exact-real comparator safety remain unresolved |
| C13 | ACCEPTED | Independent Research Reviewer: PASS; Repository Owner: ACCEPTED parent C13 | All six measurement scopes reconciled via C15 standalone runner runtime evidence; Scopes 1, 5, 6 verified; 1,179/1,179 full-regression PASS across 98 Surefire reports; deliverable: [docs/research/wspm-bitprune-c15-standalone-runner-report.md](docs/research/wspm-bitprune-c15-standalone-runner-report.md) |
| C14 | ACCEPTED | Independent Research Reviewer: PASS for C14.1, C14.1-R1, C14.2 and C14.2-R1; Repository Owner: ACCEPTED parent C14 | Owner-approved single FINAL_SCIENTIFIC_RESULT model; immutable BitPruneMiningResult (frozen PrefixTree, deterministic tree pre-order acceptedPatterns, exact Map<Pattern, Integer>, BitPruneStatistics); transient bitwise/occurrence structures unreferenced by final carrier; C14.1 projection (23 tests), C14.2 writer (20 tests); 69 closure-compatibility / 1,073 full-regression PASS across 73 reports; deliverable: [docs/research/wspm-bitprune-c14-retention-output-report.md](docs/research/wspm-bitprune-c14-retention-output-report.md) |
| C15 | ACCEPTED | Independent Research Reviewer: PASS for C15.0–C15.4; Repository Owner: ACCEPTED parent C15 | 8 source constants, executable main, 6-scope runner orchestration, 4-section console, 8-combination flag matrix, Table 1 oracle reproduction, baseline differential equality, exception lifecycle matrix, direct process exit code 0; 53 combined C15 tests; 56 Table 1 compatibility tests; 1,179 full tests across 98 reports; deliverable: [docs/research/wspm-bitprune-c15-standalone-runner-report.md](docs/research/wspm-bitprune-c15-standalone-runner-report.md) |
| C16 | ACCEPTED | Independent Research Reviewer: PASS; Repository Owner: ACCEPTED parent C16 | [Final acceptance report](docs/research/wspm-bitprune-c16-final-acceptance-report.md), [Independent review report](docs/research/wspm-bitprune-c16-independent-review-report.md); 98 suites / 1,179 tests PASS, complete Table 1 three-oracle equality, 0 differential mismatches, protected artifacts unchanged, standalone exit 0; Phase C closed |

Allowed statuses:

- NOT STARTED
- AUTHORIZED
- IN REVIEW
- ACCEPTED
- PARTIAL
- BLOCKED
- FAIL

Only `ACCEPTED` means the checklist for that task may be marked `[x]`.

# 15. STOP CONDITIONS

Stop immediately and report a blocker when:

- Baseline Git evidence contradicts the handoff.
- An algorithmic formula cannot be established.
- A pruning rule may produce false negatives.
- Candidate generation may omit valid patterns.
- The independent oracle and mining engine disagree.
- WSPM_PreTree and WSPM_BitPrune produce different pattern/support maps.
- A proposed change requires modifying the frozen baseline.
- Dataset format or sequence semantics are uncertain.
- Required acceptance tests cannot run.
- A performance claim lacks reproducible evidence.
- The authorized task scope has been exceeded.

Do not silently work around correctness failures.

# 16. FINAL PHASE C ACCEPTANCE CHECKLIST

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

**Phase C Closure Notice:** All mandatory acceptance criteria have been directly verified by the Independent Research Reviewer. With Repository Owner acceptance provided, Phase C is formally **COMPLETED** and closed. Phase D remains **NOT STARTED**.

---

# 17. CURRENT EXECUTION DIRECTIVE

Current authorized execution state:

**C00 — ACCEPTED | C01 — ACCEPTED | C01.1 — ACCEPTED | C01.2 — ACCEPTED | C02 — ACCEPTED | C03 — ACCEPTED (C03.1 AND C03.2 ACCEPTED) | C04 — ACCEPTED (C04.1 AND C04.2 ACCEPTED) | C05 — ACCEPTED (C05.1, C05.2 AND C05.3 ACCEPTED) | C06 — ACCEPTED (C06.1 AND C06.2 ACCEPTED) | C07 — ACCEPTED (C07.1 AND C07.2 ACCEPTED) | C08 — ACCEPTED (C08.1 AND C08.2 ACCEPTED) | C09 — ACCEPTED (C09.1, C09.2 AND C09.3 ACCEPTED) | C10 — ACCEPTED (C10.1, C10.2, C10.3 AND C10.4 ACCEPTED) | C11 — ACCEPTED (C11.1, C11.2, C11.3 AND C11.4 ACCEPTED) | C12 — ACCEPTED (C12.1 THROUGH C12.4-R2 ACCEPTED) | C13 — ACCEPTED | C14 — ACCEPTED (C14.1, C14.1-R1, C14.2 AND C14.2-R1 ACCEPTED) | C15 — ACCEPTED (C15.0 THROUGH C15.4 ACCEPTED) | C16 — ACCEPTED | PHASE C — COMPLETED | PHASE D — NOT STARTED**

Codex must:

1. Acknowledge that C00, C01, C01.1, C01.2 and C02 have been formally reviewed and accepted by the Research Reviewer; C01.1-R3, C01.2-R2 and the C02 remediation rounds C02-R1, C02-R2 and C02-R2.1 led to their respective final acceptances.
2. Confirm that `PHASE_C_WSPM_BitPrune.md` is the official Phase C instruction document.
3. Preserve the accepted C01 and C02 deliverables without further modification unless a new defect is demonstrated.
4. Carry forward R6 as `Zero Minimum Support Semantics — OPEN`; no support-zero pruning policy is approved for an effective threshold of zero.
5. Carry forward the accepted C06 operational policy as `BASELINE_RELATIVE_MONOTONE_UPPER_BOUND`: it is proved safe only relative to the frozen baseline Java `double` predicate and is not exact-real equivalence evidence. Exact-real comparator safety remains unresolved; no epsilon or alternate comparator is approved.
6. C02 is closed and accepted. C03.1 and C03.2 are accepted by the Repository Owner after their respective Research Reviewer `PASS` results; parent C03 is `ACCEPTED`.
7. C04.1 and C04.2 are accepted by the Repository Owner after their respective Research Reviewer `PASS` results; parent C04 is `ACCEPTED`.
8. Preserve the accepted C03 implementation and tests unless a new correctness defect is demonstrated.
9. C05.1, C05.2 and C05.3 are accepted by the Repository Owner following their respective Research Review `PASS` results and verified artifact reconciliation; parent C05 is fully `ACCEPTED`. Preserve the accepted CMAP-I/CMAP-S APIs, semantics, builders and differential tests unless a new correctness defect is demonstrated.
10. C06.1 and C06.2 are accepted by the Repository Owner after their respective Independent Research Review `PASS` results; parent C06 is fully `ACCEPTED`. Preserve the accepted filter behavior and differential evidence unless a new correctness defect is demonstrated.
11. C07.1 and C07.2 are accepted by the Repository Owner after their respective Independent Research Review `PASS` results; parent C07 is fully `ACCEPTED`. Preserve the accepted exact I-extension implementation and differential evidence unless a new correctness defect is demonstrated.
12. C08.1 and C08.2 are accepted by the Repository Owner after their respective Independent Research Review `PASS` results; parent C08 is fully `ACCEPTED`. Preserve the accepted exact S-extension implementation and bounded differential evidence unless a new correctness defect is demonstrated.
13. C09.1, C09.2 and C09.3 are accepted by the Repository Owner after their respective Independent Research Review `PASS` results and evidence-integrity remediations; parent C09 is fully `ACCEPTED`. Preserve the accepted candidate-generation and Prefix Tree integration artifacts unless a new correctness defect is demonstrated.
14. C10.1, C10.2, C10.3 and C10.4 are accepted by the Repository Owner after their respective Independent Research Review `PASS` results and evidence-integrity remediations; parent C10 is fully `ACCEPTED`. Preserve the accepted evaluator, DFS miner, preprocessing boundary and tests unless a new correctness defect is demonstrated. R6 remains `OPEN`, so C10 acceptance is limited to the accepted positive-threshold operational scope. Later directive items govern the current state of C11–C16.
15. C11.1, C11.2, C11.3 and C11.4-R1 are accepted by the Repository Owner after their respective Independent Research Review `PASS` results; parent C11 is fully `ACCEPTED` for the positive-effective-threshold operational scope. Complete-map equivalence is verified against the Table 1 oracle, the frozen WSPM_PreTree baseline, and the independent BruteForceWeightedPatternOracle across controlled, bounded exhaustive, and fixed-seed randomized catalogues with zero mismatches. R6 remains `OPEN`, and exact-real comparator safety remains unresolved.
16. C12.1 through C12.4-R2 are accepted by the Repository Owner after Independent Research Review and evidence-fidelity remediation; parent C12 is fully `ACCEPTED`. Complete accounting evidence and exact controlled oracles are compiled in `docs/research/wspm-bitprune-c12-statistics-accounting-report.md`. Preserve the accepted statistics model, package-private accumulator, miner integration, immutable result boundary, exact counter-oracle tests and report unless a new defect is demonstrated. R6 remains `OPEN`, and Java `double` exact-real comparator safety remains unresolved.
17. C13 (Time/Memory Instrumentation Parity) is fully accepted by the Repository Owner. Algorithm scopes 2–4 (preprocessing, DFS mining, complete algorithm) were established in C13.1–C13.4, and execution scopes 1, 5, 6 (Dataset I/O, Output I/O, Total Execution) were orchestrated and verified via C15 standalone runner evidence. All six standardized measurement scopes are operational. Memory metrics remain documented strictly as sampled Java heap from `MemoryMXBean` (not RSS or absolute hardware peak); console formatting is strictly excluded from Total Execution time.
18. C14 (including C14.1, C14.1-R1, C14.2, C14.2-R1) and the C14 Retention Decision have been reviewed and accepted by the Repository Owner; parent C14 is fully `ACCEPTED`. WSPM_BitPrune operates under the Owner-approved normative single `FINAL_SCIENTIFIC_RESULT` retention model: `BitPruneMiningResult` preserves frozen `PrefixTree`, immutable `acceptedPatterns` in deterministic tree pre-order, exact `Map<Pattern, Integer>` supports, and immutable `BitPruneStatistics`. No synthetic retention enum or redundant policy API is created; transient SID bitmaps, pattern endpoints, verifier results, vertical indices, and CMAP tables are not referenced by the result carrier and become eligible for GC once external execution references are dropped. C14.1 `BitPruneOutputProjection` and C14.2 `BitPruneOutputWriter` provide deterministic projection and stable disk writing with explicit UTF-8, LF line delimiters, create/truncate semantics, parent directory creation, zero-byte empty output, and raw `IOException` propagation. (At C14 closure time, C15 and C16 were `NOT STARTED`. C15 is now `ACCEPTED`; C16 is now `IN REVIEW`.)
19. C15 (including C15.0, C15.1, C15.1-R1, C15.2, C15.3, C15.3-R1, and C15.4) has been reviewed and accepted by the Repository Owner; parent C15 is fully `ACCEPTED`. `MainWspmBitPrune` provides source-configured noninteractive execution, executable `main(String[] args)`, six-scope measurement orchestration, deterministic four-section console reporting, 8-combination flag matrix verification, complete Table 1 oracle reproduction, differential equality against Phase B baseline `WspmPreTreeMiner`, and exception lifecycle cleanup across malformed parser input, R6 zero-threshold rejection, and writer I/O failure. Preserve the accepted C15.0–C15.4 implementation, tests, and research report `docs/research/wspm-bitprune-c15-standalone-runner-report.md`. C16 is now `IN REVIEW` under directive item 20.
20. C16 Final Regression & Phase C Acceptance has completed independent audit with result `PASS` from the Independent Research Reviewer, and Repository Owner acceptance has been provided. Deliverables are [docs/research/wspm-bitprune-c16-final-acceptance-report.md](docs/research/wspm-bitprune-c16-final-acceptance-report.md) and [docs/research/wspm-bitprune-c16-independent-review-report.md](docs/research/wspm-bitprune-c16-independent-review-report.md). C16 is `ACCEPTED`. Phase C is `COMPLETED`. Phase D remains `NOT STARTED`. Do not begin Phase D automatically.

**Core principle: Scientific correctness first. Optimization second. Reproducibility always.**
