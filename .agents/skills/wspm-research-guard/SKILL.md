---
name: wspm-research-guard
description: Protect source fidelity and accepted research contracts for WSPM_PreTree, PRISM Prime-block, and WSPM_BitPrune. Use for algorithm changes or reviews, mining semantics, thresholds, datasets, pruning proofs, benchmarks, reproducibility, or claims about correctness and performance; exclude ordinary UI and CRUD work that cannot affect mining results.
---

# WSPM Research Guard

Treat `AGENTS.md` as the mandatory policy and `docs/README.md` as the documentation router. This skill adds a minimal source-selection and decision protocol; it does not weaken the frozen baseline or accepted Phase C scope.

## Select authoritative context

Read `AGENTS.md` and `docs/README.md`, then open only the route required by the task:

- WSPM_PreTree behavior: `docs/research/wspm-pretree-spec.md` and, when encoding or joins matter, `docs/research/prism-prime-block.md`.
- Baseline reproducibility or acceptance claim: add `docs/research/phase-b-reproducibility.md` and/or `docs/research/phase-b-final-acceptance.md`.
- WSPM_BitPrune semantics: `docs/research/wspm-bitprune-c01-1-semantic-contract.md`.
- BitPrune pruning, bitmap bounds, CMAP, or false-negative safety: add `docs/research/wspm-bitprune-c01-2-pruning-safety.md`.
- BitPrune architecture, statistics, or measurement boundaries: add `docs/research/wspm-bitprune-c02-architecture-contract.md`.
- Phase C accepted-scope or closure claim: add the two `docs/research/wspm-bitprune-c16-*.md` reports.

Open C11-C15 reports or `docs/archive/` only when tracing specific historical evidence, measurements, provenance, or a prior decision. Do not load locally acquired papers or proposals unless a material decision cannot be established by the accepted contracts or the task explicitly requires source comparison.

## Classify decisions

For every material algorithmic decision, record one classification:

- `SOURCE-CONFIRMED`: explicitly supported by an authoritative source.
- `REASONABLE-INFERENCE`: an engineering choice that preserves the mathematics; document it when reproducibility is affected.
- `UNSPECIFIED`: evidence is insufficient; stop rather than invent behavior.

Never upgrade an inference into a source claim. If a required local source is absent, identify it precisely.

## Guard invariants

- Preserve the frozen WSPM_PreTree support, weighting, Prime-block, extension, lexicographic, DFS, candidate-space, and numeric behavior unless an explicit baseline-change task supplies renewed proof obligations.
- Preserve BitPrune equivalence as the complete `pattern -> exact support` map plus total count under an identical supported configuration.
- Classify pruning as exact rejection, necessary-condition pruning, or upper-bound pruning. Bitmap and CMAP uncertainty must flow to exact temporal verification.
- Keep `<(a)(b)>` distinct from `<(ab)>`, preserve SPMF `-1`/`-2`, and do not invent order for transaction data.
- Keep configured, resolved, relative, absolute, percentage, and weighted thresholds distinct.
- Do not define zero-effective-threshold semantics while R6 remains open, or add epsilon/alternate numeric comparators without an explicit research decision and proof.
- Make performance claims only from equivalent, reproducible measurements with timing scopes identified separately.

## Stop conditions

Stop implementation or benchmarking when a formula/source is unavailable, baseline behavior conflicts with a source, Prime-block behavior is unresolved, dataset semantics are unknown, a pruning false-negative is possible, or baseline/BitPrune/oracle outputs disagree. Report `PRISM SOURCE REQUIRED` when the accepted PRISM contract cannot establish required behavior.

For implementation or review, pair this source-fidelity protocol with `mining-correctness` and finish using the report format required by `AGENTS.md`.
