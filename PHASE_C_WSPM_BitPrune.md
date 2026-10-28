# PHASE C — WSPM_BitPrune
## Research Instructions, Implementation Rules & Task Checklist

**Project:** `wspm-pretree-bitprune`  
**Phase:** C — WSPM_BitPrune Research & Implementation  
**Language:** Java 21  
**Build:** Apache Maven  
**Testing:** JUnit 5  
**Status:** IN PROGRESS — C00 ACCEPTED, C01 ACCEPTED, C02 ACCEPTED, C03 ACCEPTED, C04 ACCEPTED, C05 ACCEPTED (C05.1–C05.3 ACCEPTED), C06–C16 NOT STARTED

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

**Status:** NOT STARTED

Checklist:

- [ ] Build candidate Bitmap from safe SID constraints.
- [ ] Compute cardinality-based support bound.
- [ ] Compute weighted upper bound.
- [ ] Apply strict `< effectiveMinSup` rejection.
- [ ] Test equality with threshold.
- [ ] Test empty candidate Bitmap.
- [ ] Test false-positive Bitmap candidates.
- [ ] Compare bounds against exact brute-force support.
- [ ] Document why no valid candidate is falsely rejected.

**Deliverable:** Weighted upper-bound filtering component.

**Acceptance:** Exhaustive small-input checks confirm upper-bound safety.

### C07 — Exact I-Extension Temporal Verification

**Status:** NOT STARTED

Checklist:

- [ ] Define I-extension positional join contract.
- [ ] Require matching itemset positions.
- [ ] Preserve prefix embeddings.
- [ ] Prevent duplicate SID support counting.
- [ ] Test same-itemset matches.
- [ ] Test same-SID but different-itemset nonmatches.
- [ ] Test multiple prefix embeddings.
- [ ] Compare exact supports against independent oracle.

**Deliverable:** Exact I-extension verification component.

**Acceptance:** All I-extension support results match the oracle.

### C08 — Exact S-Extension Temporal Verification

**Status:** NOT STARTED

Checklist:

- [ ] Define S-extension temporal join contract.
- [ ] Require strictly increasing itemset positions.
- [ ] Handle repeated items.
- [ ] Handle multiple prefix embeddings.
- [ ] Reject reverse-order matches.
- [ ] Reject same-itemset-only matches when sequence extension is required.
- [ ] Count distinct supporting SIDs.
- [ ] Compare exact support against independent oracle.

**Deliverable:** Exact S-extension verification component.

**Acceptance:** All S-extension support results match the oracle.

## GATE 2 — ENGINE INTEGRATION AND CORRECTNESS

### C09 — Candidate Generation & Prefix Tree Integration

**Status:** NOT STARTED

Checklist:

- [ ] Define candidate generation based on F1.
- [ ] Apply canonical I-extension ordering.
- [ ] Apply S-extension semantics.
- [ ] Preserve itemset boundaries.
- [ ] Prevent duplicate pattern identities.
- [ ] Integrate with Prefix Tree structure.
- [ ] Check parent-child pattern relationships.
- [ ] Test candidate enumeration independently.
- [ ] Compare with baseline candidate structure where appropriate.

**Deliverable:** Candidate generation and Prefix Tree integration.

**Acceptance:** Search space generation is deterministic and complete.

### C10 — Three-tier Lazy Pruning & DFS Miner

**Status:** NOT STARTED

Checklist:

- [ ] Integrate CMAP filtering.
- [ ] Integrate Bitmap upper-bound filtering.
- [ ] Integrate exact temporal verification.
- [ ] Implement DFS traversal.
- [ ] Preserve accepted pattern/support semantics.
- [ ] Distinguish candidate pruning from subtree pruning.
- [ ] Avoid redundant exact joins for safely rejected candidates.
- [ ] Test pruning-disabled and pruning-enabled equivalence where feasible.
- [ ] Test absence of false pruning.
- [ ] Test DFS completion on controlled datasets.

**Deliverable:** Initial end-to-end WSPM_BitPrune miner.

**Acceptance:** Complete output matches the independent reference oracle on controlled inputs.

### C11 — Full Differential Correctness & Regression

**Status:** NOT STARTED

Checklist:

- [ ] Verify the complete Table 1 nine-pattern oracle.
- [ ] Compare exact pattern identities.
- [ ] Compare exact integer supports.
- [ ] Test I-extension and S-extension patterns.
- [ ] Test repeated items.
- [ ] Test multiple embeddings.
- [ ] Test sparse and dense databases.
- [ ] Test threshold equality.
- [ ] Test zero-threshold policy where resolved.
- [ ] Test CMAP false pruning.
- [ ] Test Bitmap false pruning.
- [ ] Compare against frozen WSPM_PreTree.
- [ ] Compare against independent brute-force oracle.
- [ ] Investigate and document every mismatch.

**Deliverable:** Differential Correctness Report.

**Acceptance:** Identical `Map<Pattern, Integer>` outputs for all specified correctness configurations.

**STOP:** Do not advance to performance benchmarking while any unexplained mismatch exists.

## GATE 3 — INSTRUMENTATION, OUTPUT AND ACCEPTANCE

### C12 — Mining Statistics & Accounting Invariants

**Status:** NOT STARTED

Checklist:

- [ ] Define generated candidate counter.
- [ ] Define CMAP-pruned counter.
- [ ] Define Bitmap-bound-pruned counter.
- [ ] Define exact-support-rejected counter.
- [ ] Define accepted-extension counter.
- [ ] Define F1 pattern count.
- [ ] Define total weighted pattern count.
- [ ] Prevent counter overlap.
- [ ] Test counter accounting invariants.
- [ ] Match reported counts to verified events.

**Deliverable:** Mining statistics with counter tests.

**Acceptance:** Every candidate has exactly one final accounting outcome.

### C13 — Time/Memory Instrumentation Parity

**Status:** NOT STARTED

Checklist:

- [ ] Reuse the established Phase B measurement protocol.
- [ ] Measure Dataset I/O Time.
- [ ] Measure Preprocessing Time.
- [ ] Measure DFS Mining Time.
- [ ] Measure Algorithm Time.
- [ ] Measure Output I/O Time.
- [ ] Measure Total Execution Time.
- [ ] Report time in milliseconds.
- [ ] Report memory in MB (`bytes / 1_000_000.0`).
- [ ] Sample heap using Java MemoryMXBean.
- [ ] Report sampling interval.
- [ ] Distinguish sampled peak from absolute peak.
- [ ] Prevent phase-scope overlap or mislabeling.
- [ ] Test measurement lifecycle cleanup.
- [ ] Document GC and sampler limitations.

**Deliverable:** Comparable measurement instrumentation.

**Acceptance:** Both algorithms can be measured under the same documented protocol.

### C14 — Result Retention & Stable Output

**Status:** NOT STARTED

Checklist:

- [ ] Evaluate FULL_ENCODING equivalent behavior.
- [ ] Evaluate SUPPORT_ONLY result retention.
- [ ] Preserve Pattern → Support results.
- [ ] Avoid retaining unnecessary temporal encodings in benchmark mode.
- [ ] Ensure deterministic result iteration/export.
- [ ] Test result equality across supported retention policies.
- [ ] Document memory implications.

**Deliverable:** Result retention and deterministic output behavior.

**Acceptance:** Retention policy does not change mining correctness.

### C15 — Standalone Java Main & Configuration

**Status:** NOT STARTED

Checklist:

- [ ] Create or integrate standalone BitPrune Main.
- [ ] Support dataset path configuration.
- [ ] Support dataset format configuration.
- [ ] Support clear minimum-support semantics.
- [ ] Support `PRINT_PATTERNS`.
- [ ] Support `EXPORT_PATTERNS`.
- [ ] Support `PRINT_STATISTICS`.
- [ ] Support result retention configuration where applicable.
- [ ] Print INPUT CONFIGURATION.
- [ ] Print MINING STATISTICS.
- [ ] Print MEMORY STATISTICS (MB).
- [ ] Print TIME STATISTICS (ms).
- [ ] Run directly in VS Code.
- [ ] Do not use Scanner.
- [ ] Do not require interactive terminal input.

**Deliverable:** Standalone executable BitPrune experiment entry point.

**Acceptance:** Reproducible noninteractive mining run.

### C16 — Final Regression & Phase C Acceptance

**Status:** NOT STARTED

Checklist:

- [ ] Execute full Maven regression.
- [ ] Recheck the frozen baseline correctness tests.
- [ ] Execute full BitPrune correctness tests.
- [ ] Execute baseline-versus-BitPrune differential tests.
- [ ] Verify Table 1.
- [ ] Confirm pruning safety evidence.
- [ ] Confirm measurement semantics.
- [ ] Confirm output and retention behavior.
- [ ] Confirm no forbidden baseline modifications.
- [ ] Review Git diff and ignored dataset policy.
- [ ] Document known limitations.
- [ ] Preserve statuses R1–R8 unless separately authorized.
- [ ] Create Phase C acceptance report.
- [ ] Establish readiness for Phase D.

**Deliverable:** Phase C Final Acceptance Report.

**Acceptance:** Complete correctness evidence, clean scope, reproducible test commands and explicit research limitations.

**STOP:** Do not begin Phase D automatically.

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
| C05 | PARTIAL | Repository Owner: ACCEPTED C05.1 and C05.2 | C05.1 CMAP-I: 24 focused / 487 full PASS and 10/10 oracle pairs (Owner-accepted reported evidence). C05.2 CMAP-S: fresh Research Review confirmed 31 focused / 518 full PASS, 25/25 ordered oracle pairs and 38 protected paths; C05.3 remains NOT STARTED |
| C06 | NOT STARTED | — | Bitmap upper bound |
| C07 | NOT STARTED | — | Exact I-extension |
| C08 | NOT STARTED | — | Exact S-extension |
| C09 | NOT STARTED | — | Candidate generation |
| C10 | NOT STARTED | — | Lazy pruning and DFS |
| C11 | NOT STARTED | — | Differential correctness |
| C12 | NOT STARTED | — | Statistics |
| C13 | NOT STARTED | — | Measurement |
| C14 | NOT STARTED | — | Retention/output |
| C15 | NOT STARTED | — | Standalone Main |
| C16 | NOT STARTED | — | Final acceptance |

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

- [ ] Phase B freeze reference verified.
- [ ] Research-source audit complete.
- [ ] Mathematical pruning safety established.
- [x] Bitmap representation correct.
- [x] Vertical temporal index correct.
- [x] CMAP-I and CMAP-S correct.
- [ ] Weighted upper-bound pruning correct.
- [ ] Exact I-extension correct.
- [ ] Exact S-extension correct.
- [ ] Prefix Tree candidate generation correct.
- [ ] DFS mining correct and complete.
- [ ] Full differential correctness passed.
- [ ] No known false pruning.
- [ ] Statistics accounting verified.
- [ ] Time/memory measurement compatible with Phase B.
- [ ] Output and retention semantics verified.
- [ ] Standalone Java Main verified.
- [ ] Full regression completed.
- [ ] Baseline protected.
- [ ] Phase C Final Acceptance Report reviewed.

**Phase C is complete only when all mandatory acceptance conditions have supporting evidence and the Research Reviewer formally accepts the phase.**

---

# 17. CURRENT EXECUTION DIRECTIVE

Current authorized execution state:

**C00 — ACCEPTED | C01 — ACCEPTED | C01.1 — ACCEPTED | C01.2 — ACCEPTED | C02 — ACCEPTED | C03 — ACCEPTED (C03.1 AND C03.2 ACCEPTED) | C04 — ACCEPTED (C04.1 AND C04.2 ACCEPTED) | C05 — ACCEPTED (C05.1, C05.2 AND C05.3 ACCEPTED) | C06–C16 — NOT STARTED**

Codex must:

1. Acknowledge that C00, C01, C01.1, C01.2 and C02 have been formally reviewed and accepted by the Research Reviewer; C01.1-R3, C01.2-R2 and the C02 remediation rounds C02-R1, C02-R2 and C02-R2.1 led to their respective final acceptances.
2. Confirm that `PHASE_C_WSPM_BitPrune.md` is the official Phase C instruction document.
3. Preserve the accepted C01 and C02 deliverables without further modification unless a new defect is demonstrated.
4. Carry forward R6 as `Zero Minimum Support Semantics — OPEN`; no support-zero pruning policy is approved for an effective threshold of zero.
5. Carry forward the Java `double` comparator policy as `UNRESOLVED / IMPLEMENTATION POLICY REQUIRED`; proof over real numbers is not implementation-equivalence evidence.
6. C02 is closed and accepted. C03.1 and C03.2 are accepted by the Repository Owner after their respective Research Reviewer `PASS` results; parent C03 is `ACCEPTED`.
7. C04.1 and C04.2 are accepted by the Repository Owner after their respective Research Reviewer `PASS` results; parent C04 is `ACCEPTED`.
8. Preserve the accepted C03 implementation and tests unless a new correctness defect is demonstrated.
9. C05.1, C05.2 and C05.3 are accepted by the Repository Owner following their respective Research Review `PASS` results and verified artifact reconciliation; parent C05 is fully `ACCEPTED`. Preserve the accepted CMAP-I/CMAP-S APIs, semantics, builders and differential tests unless a new correctness defect is demonstrated.
10. C06 remains `NOT STARTED`. C06 planning and implementation require an explicit authorized task prompt from the Repository Owner; keep C06 through C16 in `NOT STARTED` until the Repository Owner explicitly authorizes the applicable task contract.

**Core principle: Scientific correctness first. Optimization second. Reproducibility always.**
