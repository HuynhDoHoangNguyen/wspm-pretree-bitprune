---
name: mining-correctness
description: Implement, review, or validate correctness-sensitive mining code in mining-core, including WSPM_PreTree, PRISM Prime-block, WSPM_BitPrune, support/weight thresholds, candidate generation, pruning, and exact temporal verification. Use when mining outputs or proof obligations may change, not for ordinary application-layer consumption of unchanged results.
---

# Mining Correctness

Prove behavior before optimizing it. Follow `AGENTS.md` and use `wspm-research-guard` to select the governing contracts before changing or judging mining semantics.

## Bound the review

1. Name the primitive or phase being changed: parsing/model, threshold resolution, baseline generation, Prime-block join, bitmap/CMAP bound, exact verifier, DFS integration, output projection, or measurement.
2. Open the changed production file, direct callers/callees needed to trace that phase, and its focused tests. Discover filenames first; do not read entire packages.
3. State the invariant and identify whether any rejection is exact, necessary-condition, or upper-bound pruning.
4. Identify the earliest place a false negative, wrong support, lost itemset boundary, unstable order, or numeric mismatch could occur.

## Required comparison contract

For identical input, weights, and resolved threshold, compare:

```text
canonical pattern identity -> exact support
```

Also compare total pattern count. Equal counts alone are insufficient. Keep I-Extension and S-Extension distinct, retain deterministic iteration/output, and use the frozen Java `double` baseline predicate unless an explicit research decision authorizes another comparator.

## Verification ladder

Choose the smallest applicable checks, then broaden in this order:

1. Unit test for the changed primitive and every boundary/negative case.
2. Regression test for the specific defect or false-negative risk.
3. Table 1 or paper fixture when canonical behavior is involved.
4. Full-map differential test against frozen WSPM_PreTree.
5. Independent brute-force oracle on tiny controlled databases.
6. Fixed-seed randomized or bounded-exhaustive differential tests for pruning, candidate generation, joins, or temporal verification.
7. Full `mining-core` test suite.

Useful existing test areas include:

- baseline and oracle comparisons: `mining-core/src/test/java/vn/edu/wspm/mining/`;
- Prime-block joins/encoding: `mining-core/src/test/java/vn/edu/wspm/prism/`;
- bitmap, CMAP, index, verifier, miner, result, statistics, and measurement: matching packages under `mining-core/src/test/java/vn/edu/wspm/bitprune/`.

For a BitPrune pruning review, at minimum inspect the semantic and pruning-safety contracts, the exact changed pruning implementation, its focused unit/differential tests, and the three-oracle/differential tests relevant to the affected phase. Add the architecture/statistics contract only if APIs, counters, timing, or lifecycle change.

## Commands

Run focused tests with Maven's `-Dtest=<ClassName>` from the reactor or module as appropriate, then run:

```powershell
mvn -pl mining-core test
```

Do not start or continue performance benchmarking after a correctness disagreement.

## Disagreement protocol

If baseline, BitPrune, and the brute-force oracle differ:

1. find the first differing canonical pattern;
2. trace its generation, pruning decision, and exact verification;
3. preserve the failing input as a small regression fixture;
4. restore full-map equivalence before any performance claim.

Report commands executed, results, source/inference classification, unsupported acceptance criteria as `NOT VERIFIED`, and unresolved R6 or numeric-equivalence limits using the task-report format in `AGENTS.md`.
