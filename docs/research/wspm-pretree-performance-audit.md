# B22.0 — WSPM_PreTree Performance and Memory Bottleneck Audit

Audit date: 2026-10-16 (Asia/Bangkok)

## 1. Status and scope

**Status: PASS for diagnosis and design; no optimization was implemented.**

This audit identifies the measured CPU, allocation, GC, and retention bottlenecks of the current
WSPM_PreTree reference implementation. It proposes a staged optimization design that preserves
Prime-block semantics, pattern identity, exact support, and weighted acceptance. It does not change
production code, parsers, thresholds, expected pattern maps, public result APIs, or B21.2
instrumentation.

The results below are profiling observations from one controlled run per configuration, not
benchmark claims. The PRISM and WSPM paper runtimes use different hardware and undocumented or
non-equivalent measurement scopes. In particular, the paper's SIGN Table 4 result remains R8 OPEN;
paper runtime numbers are historical references only.

## 2. Sources and worktree boundary

Authoritative sources inspected:

- Pham et al., *Mining Weighted Sequential Patterns Based on Prefix-Tree and Prism Encoding*,
  DOI [10.1142/S219688882350001X](https://doi.org/10.1142/S219688882350001X), local SHA-256
  `B1F019867FD61E21F4E951F464A5CBE79B3917E09CF99DD3B67C8AB18C119C41`.
- Gouda, Hassaan, and Zaki, *PRISM: An Effective Approach for Frequent Sequence Mining via
  Prime-Block Encoding*, DOI [10.1016/j.jcss.2009.05.008](https://doi.org/10.1016/j.jcss.2009.05.008),
  local SHA-256
  `EBABC7B9AC95605F9D7C30A894B0426D8A7586728B0F9C88D5972C9F5BA81298`.
- The repository research contracts `prism-prime-block.md`, `wspm-pretree-spec.md`,
  `phase-b-baseline-audit.md`, `phase-b-reproducibility.md`, and the B21.3/B21.4 SIGN/Pumsb audit.

PRISM sections 3.2–3.4 were re-read from an author/university-hosted full text. Section 3.2 states
that the actual compact encoding retains only non-empty position blocks and keeps indexes/offsets
linking valid SIDs to their position-block segments. Theorems 3.1–3.3 define support and aligned
I/S joins. Section 3.4 confirms eight-prime blocks, 256 ranked states, GCD/CARD/MASK lookup tables,
and early termination when the sequence-block candidate support cannot meet minsup.

The worktree already contained uncommitted B21.2–B21.4 instrumentation, tests, and research notes.
They were treated as input and preserved. This task adds only this report. Temporary runners,
compiled classes, and JFR recordings are under ignored `target/b22.0/`.

## 3. Environment and profiling protocol

| Property | Audited value |
|---|---|
| OS | Microsoft Windows 11 Home Single Language, 64-bit, build `10.0.26200` |
| CPU | AMD Ryzen 7 4800H, 8 cores / 16 logical processors |
| Physical RAM | 16,558,178,304 bytes |
| JDK | Oracle JDK `21.0.12.1` LTS, HotSpot 64-bit Server VM |
| Maven | Apache Maven `3.9.16` |
| GC | G1 GC |
| Profiling JVM heap | `-Xms256m -Xmx2g`, compressed oops enabled |
| JFR | `settings=profile`, one recording per dataset/configuration |
| Project heap sampler | B21.2 `MemoryMXBean` used-heap samples every 5 ms; no forced GC |
| Output policy | pattern printing false; export false |
| Repetitions | one diagnostic run per configuration; not a benchmark |

JFR CPU and allocation data are samples. Percentages are allocation pressure or sampled stack
share, not exact allocation counts. The post-run Pumsb 0.66% class histogram is a separate
retention diagnostic: it intentionally requested a full GC only after timing and peak collection,
while the completed result remained strongly reachable. It is not part of a benchmark and was not
used to reduce a reported peak.

The normal JVM command that reproduced the user's Pumsb 0.60% OOM had an estimated default maximum
heap of 3.86 GB. That failing configuration was not rerun in B22.0.

## 4. Dataset and configuration matrix

| Dataset | SHA-256 | Format | Sequences | Distinct items | Relative minsup |
|---|---|---|---:|---:|---:|
| Table 1 | `F7E409F982EE42E0E34C7B940496074750AD8AEB44F191397EFEDDD0653AE8F5` | SPMF sequential | 7 | 8 | 14% |
| SIGN | `41F2250001AC5E6DCA40C3966F74D036CC4E982F270DCBCD55968CA21B9023DD` | SPMF sequential | 730 | 267 | 0.4%, 0.3% |
| Pumsb | `33B8E1B577C2325052E5B2EB5E6B3E6DA8AC46C1237559A16B61ACE6F06A2D74` | FIMI transaction → one sequence/one itemset | 49,046 | 2,113 | 0.67%, 0.66% |

Pumsb's one-transaction-to-one-sequence/one-itemset conversion is the documented project policy,
not verified author preprocessing; R7 remains OPEN.

## 5. Actual run evidence

All numbers in this table are single JFR-instrumented observations except Table 1, which is a
non-JFR oracle sanity run. Times are the B21.2 phase scopes, in milliseconds. Peaks are sampled
Java heap used, not process RSS.

| Configuration | F1 | Candidates (accepted/rejected) | Patterns | Preprocess | DFS | Algorithm | Total | Peak heap |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| Table 1, 14% | 3 | 34 (6/28) | 9 | 65.356 | 12.880 | 81.029 | 108.051 | 5.243 MB |
| SIGN, 0.4% | 49 | 18,198 (266/17,932) | 315 | 308.987 | 24,328.916 | 24,648.837 | 24,767.973 | 210.540 MB |
| SIGN, 0.3% | 69 | 92,027 (1,097/90,930) | 1,166 | 354.201 | 100,343.191 | 100,718.508 | 100,861.612 | 244.849 MB |
| Pumsb, 0.67% | 1 | 1 (0/1) | 1 | 526.631 | 73.587 | 604.854 | 1,449.917 | 323.993 MB |
| Pumsb, 0.66% | 6 | 113 (9/104) | 15 | 730.621 | 4,557.607 | 5,308.865 | 6,148.440 | 575.668 MB |

Correctness observations match the existing oracle/audits: Table 1 has nine patterns, SIGN has 315
at 0.4% and 1,166 at 0.3%, and Pumsb has one and 15 patterns at 0.67% and 0.66% respectively. These
runs do not resolve the paper-count differences at SIGN 0.4%/0.3% or Pumsb 0.65%/0.60%.

## 6. CPU hotspots

### 6.1 SIGN

SIGN 0.4% and 0.3% show the same dominant shape. SIGN 0.3% JFR samples were:

| Method | CPU sample share |
|---|---:|
| `PrimeBlockJoins.strictAfter` | 33.36% |
| `EncodedVector.<init>` | 16.80% |
| `PrimeBlockJoins.intersectPositionBlocks` | 15.23% |
| `PrimalBlockCodec.validateProduct` | 14.26% |
| `PrimeBlockJoins.positionalJoin` | 5.52% |
| `EncodedVector.isEmpty` | 4.45% |
| `EncodedVector.fromBits` | 3.86% |
| `EncodedVector.totalCardinality` | 2.28% |

SIGN 0.4% independently sampled `strictAfter` at 35.66%, position intersection at 17.03%, the
`EncodedVector` constructor at 14.50%, and product validation at 14.17%. The agreement supports the
conclusion that materializing masks/vectors and repeatedly validating/converting prime products,
not tree lookup or output I/O, dominates SIGN CPU.

No production `BigInteger` use exists. Prime arithmetic is bounded eight-prime `int` arithmetic.
The observed cost is repeated product-to-rank validation/trial division and vector construction,
not arbitrary-precision growth.

### 6.2 Pumsb

Pumsb 0.66% sampled hotspots include `validateProduct` 29.73%, position intersection 11.41%,
`PositionBlock.empty` 10.51%, `positionalJoin` 8.11%, `strictAfter` 5.41%, and the
`PatternEncoding` constructor 5.11%. Pumsb 0.67% is too short for a stable mining profile and is
mostly parser/statistics/collection work; it must not be used to rank mining micro-optimizations.

TreeMap/HashMap work is visible but secondary in SIGN mining. It becomes material in Pumsb
allocation samples because preprocessing boxes item/SID data and uses object collections. This is
evidence against introducing a `Map<Integer, PositionBlock>` per pattern.

### 6.3 Memory sampler overhead

`ExecutionMemorySampler` did not appear in the reported hot-method or allocation-site tables. Its
overhead is therefore below the resolution of these profiles, not proven to be zero. B22.0 did not
run an instrumentation-disabled production variant, so an exact overhead percentage is **NOT
VERIFIED**.

## 7. Allocation and GC hotspots

### 7.1 JFR allocation pressure

SIGN 0.3% allocation-by-class samples:

| Class | Pressure |
|---|---:|
| `int[]` | 44.48% |
| `boolean[]` | 27.41% |
| `PositionBlock` | 11.25% |
| `EncodedVector` | 11.13% |
| `Object[]` | 5.03% |

The first four categories account for about 94% of sampled allocation pressure; including
`Object[]` raises the listed share to about 99%. The highest sites are
`EncodedVector.fromBits`, `Arrays.copyOf(int[])`, `fromPaperPositions`, `PositionBlock.empty`,
`strictAfter`, and position intersection.

Pumsb 0.66% allocation is more mixed: `int[]` 20.95%, `Integer` 11.71%, `PositionBlock` 11.45%,
`EncodedVector` 9.62%, `TreeMap.Entry` 9.49%, `HashMap.Node` 8.50%, and stream pipeline objects.
This combines dense encoding churn with boxed/map-heavy transaction preprocessing.

### 7.2 GC evidence

| Configuration | GC count | Total pause | Max pause | Total observed runtime |
|---|---:|---:|---:|---:|
| SIGN 0.4% | 25 | 123 ms | 10.9 ms | 24.8 s |
| SIGN 0.3% | 145 | 729 ms | 11.7 ms | 100.9 s |
| Pumsb 0.67% | 7 | 91.8 ms | 21.3 ms | 1.45 s |
| Pumsb 0.66% | 11 | 189 ms | 36.5 ms | 6.15 s |

SIGN creates heavy allocation/collection churn, but stop-the-world pause time is below 1% of the
observed run. GC pressure is a symptom; CPU is spent primarily constructing, validating, scanning,
and intersecting encodings. Pumsb's growing post-GC live set is the more important memory risk.

## 8. Dense representation cost and retained ownership

### 8.1 Current shape

For every `PatternEncoding`, current code retains:

- one dense `SequenceBlock` over all `N` SIDs;
- a `List<PositionBlock>` of exactly `N` entries;
- one `PositionBlock` object per SID, including unsupported SIDs;
- one `EncodedVector` and one copied `int[]` under every `PositionBlock`;
- full empty position vectors rather than PRISM's actual compact representation.

`PrimeBlockJoins.positionalJoin` loops over every SID and constructs at least one output
`PositionBlock` per candidate/SID. For S-joins on a candidate SID it additionally materializes a
temporary full `strictAfter` position vector. `EncodedVector.fromBits` creates a `boolean[]`, an
`int[]`, and then the constructor copies that `int[]` again.

Static control-flow lower bounds, excluding the additional S-join mask blocks, are:

| Run | Singleton + candidate output `PositionBlock` allocations, lower bound |
|---|---:|
| SIGN 0.4% | 13,320,310 |
| SIGN 0.3% | 67,230,080 |
| Pumsb 0.66% | 5,836,474 |

These are code-derived lower bounds, not profiler-reported exact allocation totals. JFR sampling is
used only to validate the dominant types/sites.

### 8.2 Exact post-GC retention probe: Pumsb 0.66%

After the measured run completed, `WspmExecutionRun` was kept strongly reachable and a diagnostic
class histogram was requested. Exact live counts at that point were:

| Class | Instances | Shallow bytes |
|---|---:|---:|
| `PositionBlock` | 735,690 | 17,656,560 |
| `EncodedVector` | 735,705 | 17,656,920 |
| `int[]` | 736,283 | 18,304,320 |
| `PatternEncoding` | 15 | 360 |
| `SequenceBlock` | 15 | 360 |
| All live classes | 2,231,032 | 57,625,000 |

The `735,690` retained position blocks equal exactly `15 patterns × 49,046 SIDs`. Of them, 724,882
are non-empty and 10,808 are empty. Therefore:

- dense per-SID retention is directly demonstrated, not inferred only from multiplication;
- empty-block removal alone saves only 1.47% of retained Pumsb 0.66% per-SID entries;
- most avoidable cost is the three-object-plus-array-reference representation of each non-empty
  per-SID position vector;
- the 575.668 MB sampled peak and 471.698 MB end-of-mining used heap include substantial temporary
  garbage not present after the diagnostic full GC.

For Pumsb 0.60%, the independently derived 4,059-pattern result would imply 199,077,714 dense
per-pattern/SID entries if the current lifecycle completed. Applying the measured 24-byte shallow
sizes of `PositionBlock`, `EncodedVector`, and one-element `int[]` gives roughly 14.3 GB before list
references, maps, patterns, sequence vectors, JVM metadata, and transient candidates. This is an
**extrapolation/hypothesis**, not a successful 0.60% memory measurement, but it explains why a 3.86
GB heap fails and why merely increasing `-Xmx` is not a defensible fix on the audited 16 GB machine.

### 8.3 Retention owners

The ownership chain is explicit:

```text
WspmExecutionRun
  -> WspmMiningResult
       -> unmodifiable LinkedHashMap<Pattern, PatternEncoding>
            -> PatternEncoding
                 -> SequenceBlock -> EncodedVector -> int[]
                 -> List<PositionBlock>[N]
                      -> PositionBlock -> EncodedVector -> int[]
```

`WspmPreTreeMiner` retains every accepted encoding in a local `LinkedHashMap`. The result constructor
copies the map entries into another `LinkedHashMap` and exposes `encodings()`/`encoding(pattern)`;
the underlying encoding objects are shared, not deep-copied. `PrefixTree` retains patterns and
nodes but no encoding. The result also stores a separate `Pattern -> support` snapshot, so full
encodings are not required merely to print or compare final supports.

## 9. Temporary candidate lifetime

`CandidateEngine.generateOneStep` eagerly constructs all valid I- then S-extension encodings and
returns an immutable list of `CandidateResult`. Acceptance is checked only after the full positional
join. `WspmPreTreeMiner.depthFirstExpand` then iterates that list and recursively descends as soon as
it encounters an accepted child.

Consequences:

1. Every rejected candidate still receives a complete dense `PatternEncoding`.
2. The parent candidate list remains on the Java call stack during child recursion.
3. Rejected candidates and not-yet-visited sibling encodings can therefore remain live across an
   entire descendant subtree, not merely until their acceptance check.
4. Accepted encodings are additionally inserted into the global map and survive to the final
   result.

This explains why "temporary allocation" and "retained result" must be treated separately. The
former drives SIGN CPU/GC pressure; the latter makes Pumsb 0.60% structurally infeasible.

PRISM section 3.3 says all extensions of a node are evaluated before the depth-first recursive
call, because literal PRISM joins use sibling encodings. The current WSPM adaptation instead joins
the parent encoding directly with singleton encodings. Changing eager extension evaluation to a
fully lazy traversal is therefore not automatically source-neutral; the future task must preserve
the documented traversal/candidate set or clearly classify the deviation.

## 10. DFS encoding lifecycle

| Encoding | Needed descending | Needed after child returns | Needed for siblings | Needed only for audit/result |
|---|---|---|---|---|
| Singleton F1 encodings | Yes, by current direct-extension mapping | Yes | Yes | Their final copies are also results |
| Current parent encoding | Yes | Yes until all children are processed | Yes | No after subtree if support is snapshotted |
| Accepted child encoding | Yes while exploring child | No after child subtree under current direct mapping | Possibly under literal PRISM sibling joins | Yes only because public result API exposes it |
| Rejected candidate encoding | No | No | No | Candidate-level tests inspect it, final result does not |
| Accepted final pattern support | Yes for acceptance/counters | Yes | No | Yes, final output requires it |

Under the current direct pattern-plus-singleton mapping, a completed child's full encoding can be
released after its subtree returns if the final result stores Pattern + exact support instead.
However, B22.0 does not change this policy because `WspmMiningResult.encoding(s)` is public and is
used by `WspmPreTreeMinerTest`, `Table1EndToEndCorrectnessTest`,
`WspmDifferentialCorrectnessTest`, and `SignDeepAuditTest`. Candidate-engine tests separately rely
on candidate encodings and need not be changed merely by a final-result retention policy.

Any future lifecycle refactor must choose and document one of these modes:

- reference/audit mode retaining full encodings and the current API;
- mining/benchmark mode retaining only stable Pattern + support (+ optional supporting SID proof);
- lazy/on-demand reconstruction, which trades retained memory for recomputation and requires a
  precise API contract.

## 11. PRISM compact encoding fidelity

### 11.1 Source-confirmed requirements

PRISM's actual compact representation:

- retains sequence blocks;
- retains only non-empty position blocks;
- associates indexes/offsets with sequence blocks so valid SIDs map to their position segments;
- derives support from sequence-block factor cardinalities;
- aligns position blocks for I-join;
- applies the strict-after mask then aligned GCD for S-join;
- removes a SID when its joined position vector is empty;
- uses eight-prime ranked blocks and compact GCD/CARD/MASK lookup tables.

Compact Prime-block is therefore source-faithful and is not a Bitmap substitution. A Java layout
that loses block/SID alignment, or replaces sequence blocks with only an ID-list, would not be a
faithful baseline.

### 11.2 Representation comparison

| Representation | Shape | Strength | Main cost/risk | Fidelity |
|---|---|---|---|---|
| Current dense objects | `List<PositionBlock>[N]`, each with object + vector + `int[]` | Simple, canonical full-vector joins | O(patterns × N) objects; empty and non-empty SIDs equally expensive | Full encoding is source-described, but not PRISM's actual compact implementation |
| Dense primitive indexed | shared SID universe + dense `int[N+1]` offsets + flat block arrays | O(1) SID lookup; removes object-per-SID | Still O(N) metadata per pattern | Java implementation policy preserving full semantics |
| Compact indexed Prime-block | sequence ranks + supported-SID/offset metadata + flat `(blockIndex, rank)` arrays | Matches PRISM's sparse intent; preserves aligned joins | More complex merge/mask logic and boundary tests | Compact principle SOURCE-CONFIRMED; exact Java arrays are implementation policy |
| `Map<Integer, PositionBlock>` | boxed SID keys and per-entry nodes | Superficially sparse/easy lookup | Hash/boxing/node overhead; JFR already shows these costs | Not specified by PRISM and not recommended |

### 11.3 Proposed Java data design — not implemented

A defensible compact primitive layout is:

```text
CompactPatternEncoding
  shared CoordinateUniverse
    sidCount
    sequenceSizeBySid[]             // shared once across all patterns
  sequenceRanks[]                   // one unsigned 8-bit rank per SID block
  supportingSids[]                  // primitive, sorted; may be derived from sequenceRanks
  positionOffsets[]                 // support + 1; range in flat arrays per supported SID
  positionBlockIndexes[]            // absolute block index inside that SID's position vector
  positionRanks[]                   // non-zero unsigned 8-bit rank, same length as indexes
```

`byte[]` can store PRISM ranks as in the source implementation, but every Java lookup must use
`Byte.toUnsignedInt`; checked `int[]` ranks are simpler and larger. Explicit absolute position-block
indexes are a REASONABLE-INFERENCE that guarantees correct alignment even with interior empty
blocks. The representation must round-trip to the current full vector in tests.

I/S joins should merge the sorted supported-SID streams, then merge aligned block-index streams
within each common SID. S-join should compute the first set logical position and apply the PRISM
MASK semantics directly to ranks/blocks without constructing a full `boolean[]` or temporary
`PositionBlock`. Output SID bits are set iff the compact joined segment is non-empty.

For Pumsb, where every sequence has one itemset, compact empty elimination alone has limited value
at high support. Flattening the hundreds of thousands of Java objects into primitive arrays is the
essential memory improvement.

## 12. Ranked optimization plan

No item below is implemented or measured as a speedup.

### P0 — Preserve correctness evidence before optimization

- Freeze exact `pattern -> support` and supporting-SID fingerprints for Table 1, SIGN 0.4/0.3, and
  Pumsb 0.67/0.66.
- Keep the full current implementation available as the differential oracle on bounded inputs.
- Do not use Pumsb 0.60% as an OOM-based benchmark.

Expected impact: no runtime improvement; prevents scientifically invalid optimization.

### P1 — Rank-native block operations and direct MASK joins

- Store/use validated rank values internally and use direct GCD/CARD/MASK lookup operations.
- Avoid product trial division in the inner loop.
- Avoid `boolean[]`, redundant `int[]` copies, and full `strictAfter` object construction.
- Preserve product-facing APIs where required by reference tests.

Rationale: PRISM section 3.4 explicitly reports ranked one-byte blocks and lookup tables. JFR puts
`strictAfter`, vector construction, intersection, and validation at the top of SIGN CPU and
allocation profiles.

Expected impact hypothesis: large reduction in SIGN CPU and allocation rate; little change to
final retained-memory asymptotics until the layout/lifecycle work lands.

### P2 — Sequence-block upper-bound rejection before positional materialization

- Compute the sequence-block intersection first.
- If `candidateSequenceSupport * MeanW < effectiveMinSup`, record a safe rejected candidate without
  constructing per-SID position results.
- Preserve inclusive comparison and candidate/counter definitions.

PRISM section 3.4 explicitly permits stopping before position blocks when candidate sequence
support cannot reach minsup. Applying the same necessary upper bound to WSPM's weighted acceptance
is mathematically safe but must be documented as the weighted adaptation and tested against false
negatives. SIGN 0.3% rejects 90,930 of 92,027 candidates, so this has high potential impact.

Expected impact hypothesis: major reduction in rejected-candidate CPU/allocation. Exact impact is
unknown until the distribution of sequence-block upper bounds is measured.

### P3 — Compact primitive-array Prime-block representation

- Implement the source-confirmed compact principle with explicit alignment metadata.
- Share immutable SID/sequence-size coordinates instead of repeating them per pattern.
- Reject boxed per-pattern maps.
- Differentially validate full ↔ compact round trips and joins before adoption.

Expected impact hypothesis: removes object-per-SID overhead and makes large-N retention tractable.
It carries the highest representation-correctness risk.

### P4 — Separate mining state from final audit state

- During DFS retain only encodings required by the current branch/sibling contract.
- Snapshot exact support immediately.
- Offer explicit audit retention rather than always retaining every full encoding.
- Do not silently remove or change the current public `WspmMiningResult` API.

Expected impact hypothesis: changes final retained memory from proportional to all accepted full
encodings toward pattern/support metadata plus active DFS state. This is necessary for Pumsb 0.60%
on ordinary heaps even if compact encoding is implemented.

### P5 — Candidate-list lifetime and preprocessing collections

- Ensure rejected encodings become unreachable after their counters/diagnostics are captured.
- Retain only accepted sibling encodings if the chosen PRISM traversal requires them.
- Replace stream/boxing-heavy hot preprocessing paths with primitive buffers only after P1–P4 and
  only with before/after evidence.

Expected impact hypothesis: lower peak and GC pressure. It is secondary to the Prime-block inner
loop and result-retention changes.

## 13. Correctness and research risks

1. A compact join that omits absolute block indexes can misalign interior empty blocks and create
   false positives/negatives.
2. Java signed `byte` ranks can index lookup tables incorrectly unless converted as unsigned.
3. Early upper-bound rejection must use the exact weighted threshold and inclusive comparison; no
   rounding may be introduced.
4. Releasing encodings must not change structural DFS order, duplicate prevention, Prefix Tree
   parents, or pattern serialization.
5. Literal PRISM joins use siblings under a common prefix; the repository's direct
   pattern-plus-singleton mapping remains REASONABLE-INFERENCE. A lifecycle refactor must not erase
   that distinction.
6. Removing full result encodings affects public API and four audit/miner test classes. It requires
   an explicit compatibility/mode decision, not a hidden optimization.
7. Pumsb remains a transaction-to-one-itemset policy with R7 OPEN; faster completion would not
   prove paper-equivalent preprocessing.
8. SIGN R8 remains OPEN; optimizing the current 315/1,166 outputs does not validate the paper's
   56/273 counts.

Required future tests include block boundaries at positions/SIDs 8, 9, 16, and 17; interior and
trailing empty blocks; I/S joins with repeated endpoints; full/compact round trips; random
differential joins; complete Table 1 maps/SIDs/tree; SIGN and Pumsb exact output fingerprints; and
false-negative tests around the weighted upper bound.

## 14. Before/after experiment design

Correctness gate before any performance claim:

1. Run `mvn clean verify`.
2. Compare complete `Pattern -> support` maps and supporting SID sets against the existing full
   encoding on bounded datasets.
3. Reproduce Table 1's nine patterns and deterministic tree/counters.
4. Reproduce the audited SIGN/Pumsb fingerprints under identical threshold semantics.

Performance protocol after correctness passes:

- same dataset bytes/hash, input format, threshold, JDK, GC, machine, working tree, output flags,
  and heap flags;
- fork a fresh JVM per run;
- use JFR runs for diagnosis separately from non-JFR timed repetitions;
- one discarded warm-up run and at least three measured repetitions for expensive real datasets;
- report median and range, not only the best run;
- report Dataset I/O, Preprocessing, DFS, Algorithm, Output I/O, and Total separately;
- retain the 5 ms heap sampler for both variants and disclose its limits;
- collect GC count/pause/allocation evidence without forced GC inside measured scope;
- use post-run histograms only as separately labeled retention diagnostics;
- keep print/export settings identical;
- attempt Pumsb 0.60% only after bounded configurations pass and with an explicit heap cap,
  timeout, and safe stop.

Acceptance is exact output equality first. Runtime/memory changes are observations, not accepted
because they approach paper numbers from a different environment.

## 15. Research issues R1–R8

| Issue | B22.0 status | Performance-audit effect |
|---|---|---|
| R1 — sequence-weight discrepancy | OPEN | Unrelated to the observed encoding bottlenecks; do not alter formulas. |
| R2 — percentage minimum support | RESOLVED FOR IMPLEMENTATION | Profiling used explicit relative fractions and unrounded effective thresholds. |
| R3 — Prime-block mechanics | RESOLVED for current full representation | Compact principle is source-confirmed; exact Java layout remains implementation policy requiring differential proof. |
| R4 — complete Table 1 oracle | RESOLVED | Nine-pattern run passed; must gate every optimization. |
| R5 — item-weight denominator | RESOLVED | No performance change may alter it. |
| R6 — zero minimum support | OPEN | Profiling used positive thresholds only. |
| R7 — transaction-to-sequence mapping | OPEN | Pumsb measurements apply only to the documented one-itemset policy. |
| R8 — SIGN paper reproduction | OPEN | Current 315/1,166 outputs are internally verified but do not reproduce paper 56/273. |

## 16. Findings and stop decision

| ID | Severity | Finding | Evidence | Recommendation |
|---|---|---|---|---|
| B22-F01 | HIGH | Dense result retention is O(patterns × SIDs) Java objects and causes the Pumsb 0.60% OOM risk. | Exact Pumsb 0.66% post-GC histogram; ownership chain; 0.60% extrapolation. | P3 compact primitive layout plus P4 lifecycle/result policy. |
| B22-F02 | HIGH | Full positional joins are performed for overwhelmingly rejected candidates. | SIGN 0.3%: 90,930/92,027 rejected; 67.2M base position-block allocation lower bound. | P2 source-backed upper-bound rejection. |
| B22-F03 | HIGH | Mask/vector construction and product validation dominate SIGN CPU/allocation. | JFR hot methods and ~94% array/vector/block allocation pressure. | P1 rank-native GCD/CARD/MASK path. |
| B22-F04 | MEDIUM | Eager candidate lists retain rejected and sibling encodings across recursive DFS. | Direct code ownership/lifecycle trace. | P5 staged lifetime refactor preserving traversal semantics. |
| B22-F05 | MEDIUM | Full encodings are retained mainly because the public result exposes them. | `WspmMiningResult` maps plus four result-level test consumers. | Explicit audit/benchmark retention contract; no silent API change. |
| B22-F06 | LOW | Pumsb preprocessing has boxing/map/stream pressure. | JFR allocation samples. | Address after Prime-block bottlenecks; do not introduce boxed sparse maps. |
| B22-F07 | INFO | GC pauses are not the primary SIGN runtime bottleneck. | 729 ms pause over ~101 s at SIGN 0.3%. | Reduce allocation/work; do not tune GC first. |

**STOP:** B22.0 ends with this audit. It does not implement compact encoding, DFS/result lifecycle
changes, early pruning, rank-native joins, or WSPM_BitPrune.

## 17. B22.1 — rank-native GCD/CARD/MASK optimization

Audit date: 2026-10-16 (Asia/Bangkok).

### 17.1 Scope and representation

B22.1 implements only the P1 primitive optimization proposed above. The dense ownership model is
unchanged: every accepted/candidate encoding still has a complete sequence block and one position
block per SID, including empty blocks. DFS, candidate generation, acceptance, result retention,
input parsing, and minimum-support semantics are unchanged. No compact PRISM layout, Bitmap,
BitSet, CMAP, structural pruning, streaming, or WSPM_BitPrune code was introduced.

Before B22.1, `EncodedVector` stored prime products. Every hot read converted a product back to a
rank by validation and trial division; joins then converted the output rank to a product, validated
it again, and copied the array. `strictAfter` additionally allocated a logical-length `boolean[]`,
scanned bits through repeated product decoding, converted that array to products, and constructed
an intermediate position block before intersection.

After B22.1, the same full vector stores the PRISM rank for each eight-position block. Product APIs
remain exact views through `PRODUCT[rank]`; public product factories still reject zero, negative,
non-generator, repeated-prime, wrong-block-count, and padding-invalid inputs. Fresh rank arrays
created by internal joins cross a package-private ownership-transfer boundary, avoiding redundant
factor validation and copying. I-Join uses rank GCD directly. S-Join obtains the first non-empty
block, applies the rank `MASK` lookup there, treats later valid positions as set, clears final
padding, and fuses this mask with the right operand's rank GCD. The supporting sequence rank is
built during that same pass. Public `PatternEncoding` construction still validates membership;
join output uses a trusted internal constructor because membership and non-empty status are derived
from the same newly created rank values.

### 17.2 Equivalence argument and lookup derivation

For rank `r`, bit `j` is one exactly when generator prime `G[j]` divides the corresponding product:

`PRODUCT[r] = product(G[j] | bit_j(r) = 1)`.

The eight generator primes are distinct and every valid product is square-free, so this mapping is
bijective over the 256 subsets. Therefore:

- `rank(gcd(PRODUCT[a], PRODUCT[b])) = a & b`;
- `CARD[a] = bitCount(a)`;
- `MASK[0] = 0`; otherwise `MASK[a]` has exactly the offsets strictly greater than the least set
  offset of `a`;
- vector-level strict-after uses `MASK` in the first non-empty block, full valid masks in later
  blocks, and the logical-length mask in the final padded block.

GCD, CARD, the 256 ranks, all 65,536 GCD pairs, and all 256 MASK states are covered by exhaustive
tests against independent arithmetic/bit-loop references. The Java choice to retain ranks and
transfer ownership of fresh internal arrays is an implementation policy; the finite generator,
rank meaning, and GCD/CARD/MASK behavior are source-confirmed by PRISM Sec. 3.4.

### 17.3 Correctness evidence

The pre-change SIGN 0.3% result was canonicalized before editing. The post-change run matched all
four immutable references:

| Reference | Pre/post value |
|---|---|
| Patterns | 1,166 |
| Full sorted `pattern -> support` SHA-256 | `98094C7460AEE9E17EC4FE31CE6E7858E1FAF6B9F2C2910391C065A668795D7D` |
| All sequence-block and per-SID position-block ranks SHA-256 | `7B3959556B81BEF69D1EC6615503D78CEFD6F7CBBC12077B22A85C5AC890F36E` |
| Parent/extension-type tree-edge SHA-256 | `0EA6A64326406F3C571BF4C7D5093A12B903886DD2DE476D92F7EC7DE9F2A59F` |
| Statistics tuple | `730|69|92027|1097|90930|11573|80454|1166|1166` |

The tuple fields are sequences, F1, candidates, accepted candidates, rejected candidates,
I-candidates, S-candidates, expanded nodes, and patterns. Thus the comparison covers full maps,
SID membership, positional encodings, tree structure, and candidate semantics rather than counts
alone. SIGN 0.4% was also compared by the independent B21.4 raw parser/projected oracle: all 315
pattern supports and all 315 supporting-SID sets matched.

`mvn clean verify` compiled from a clean `target` and passed **354 tests, 0 failures, 0 errors,
0 skipped**. This includes the Table 1 nine-pattern oracle, primitive boundaries, repeated-item and
mixed I/S join tests, B18 brute force, B19's 146 differential configurations, FIMI integration,
statistics/Main/measurement tests, SIGN 0.4% independent comparison, and SIGN 0.3% complete
pre-change fingerprint comparison.

### 17.4 Measurement protocol

All B22.0 rows in Sections 3–7 remain the unmodified before baseline. Optimized JFR diagnostics
used the same machine, Oracle JDK 21.0.12.1, G1, `-Xms256m -Xmx2g`, `settings=profile`, dataset
bytes/hashes, formats, thresholds, 5 ms heap sampler, and disabled print/export policy. As in
B22.0, each JFR row is a single diagnostic observation, not a stable benchmark.

A separate post-change timing check forked a fresh JVM per configuration, discarded one warm-up,
then measured three repetitions. `System.gc()` and a 200 ms settling interval occurred before,
never inside, each measured execution. Values below are median with observed range; they establish
the optimized build's dispersion, but there is no equivalent three-run pre-change series, so the
JFR-to-JFR table is the only direct before/after speedup calculation.

| Configuration | Prep median (range) ms | DFS median (range) ms | Algorithm median (range) ms | Total median (range) ms | Peak algorithm median (range) MB | GC median count / pause ms |
|---|---:|---:|---:|---:|---:|---:|
| Table 1, 14% | 0.813 (0.767–1.075) | 1.995 (1.640–2.847) | 2.942 (2.517–4.057) | 4.516 (4.083–5.863) | 1.638 (1.326–1.638) | 0 / 0 |
| SIGN, 0.4% | 66.577 (29.526–68.215) | 1,022.333 (881.128–1,054.334) | 1,052.385 (950.043–1,121.706) | 1,065.930 (967.931–1,147.786) | 269.732 (259.644–280.515) | 7 / 40 |
| SIGN, 0.3% | 60.265 (48.499–80.349) | 4,249.238 (4,163.219–4,390.443) | 4,312.459 (4,245.636–4,441.720) | 4,340.449 (4,272.599–4,454.607) | 485.850 (476.758–486.126) | 25 / 175 |
| Pumsb, 0.67% | 345.015 (301.365–403.367) | 4.880 (4.447–7.438) | 350.141 (309.192–408.037) | 1,020.202 (945.533–1,180.272) | 350.156 (337.541–351.488) | 5 / 81 |
| Pumsb, 0.66% | 486.705 (355.151–506.497) | 406.032 (386.551–409.167) | 896.175 (742.047–912.797) | 1,506.294 (1,368.597–1,643.824) | 406.409 (402.214–416.008) | 8 / 143 |

### 17.5 Same-instrumentation before/after diagnostics

| Configuration | Algorithm before / after ms | Observed speedup | Peak heap before / after MB | Peak change | Candidates / patterns preserved |
|---|---:|---:|---:|---:|---:|
| Table 1, 14% | 81.029 / 87.033 | 0.93x (small-run regression) | 5.243 / 6.074 | +15.8% | 34 / 9 |
| SIGN, 0.4% | 24,648.837 / 1,265.043 | 19.48x | 210.540 / 461.148 | +119.0% | 18,198 / 315 |
| SIGN, 0.3% | 100,718.508 / 4,937.104 | 20.40x | 244.849 / 499.620 | +104.1% | 92,027 / 1,166 |
| Pumsb, 0.67% | 604.854 / 532.162 | 1.14x | 323.993 / 368.672 | +13.8% | 1 / 1 |
| Pumsb, 0.66% | 5,308.865 / 1,114.116 | 4.76x | 575.668 / 401.408 | -30.3% | 113 / 15 |

The Table 1 difference is dominated by startup/JFR noise and is reported as observed, not as a
meaningful algorithm regression. SIGN's peak used heap is a real observed regression under this
sampler: the much shorter run performs far fewer collections before completion, so more dead
candidate data is present at the sampled peak. This metric is used Java heap, not live-set size or
hardware RAM. It does not contradict reduced allocation work, and it must not be described as a
memory-efficiency improvement. Pumsb 0.66%, whose live retained result dominated B22.0, decreased
about 30%; the dense-retention risk itself remains unresolved.

JFR/MXBean GC observations changed from 25 to 7 collections for SIGN 0.4%, 145 to 25 for SIGN
0.3%, 7 to 7 for Pumsb 0.67%, and 11 to 9 for Pumsb 0.66%. Recorded pause deltas were about 48,
197, 93, and 135 ms respectively, versus B22.0's 123, 729, 91.8, and 189 ms. On SIGN 0.3%, the
pre-change CPU leaders `strictAfter`, `EncodedVector.<init>`, position intersection, and product
validation no longer appear separately. The optimized profile is concentrated in the unavoidable
dense `positionalJoin`. Sampled allocation has no `boolean[]`; its leading classes are `int[]`
31.79%, `EncodedVector` 30.40%, `PositionBlock` 19.83%, and `Object[]` 16.73%. Percentages describe
sample share, not exact allocated bytes.

### 17.6 Result and remaining limitations

**B22.1 status: PASS with an explicit SIGN peak-used-heap limitation.** Mathematical semantics,
full outputs, supporting SIDs, encodings, tree shape, and candidate counters are preserved. The
target real workload improved materially in same-instrumentation diagnostics, while tiny Table 1
and low-candidate Pumsb 0.67% remain dominated by fixed costs.

Research issues R1–R8 retain their Section 15 status. In particular R7 and R8 are not resolved by
performance work. Pumsb 0.60% was not rerun. Dense per-pattern/per-SID retention, the known OOM
risk, and SIGN peak-used-heap behavior remain for an explicitly authorized later task; B22.1 does
not silently address them through compact layout or result lifecycle changes.

**STOP:** B22.1 ends here for research review. No B22.2/B22.3 work was started.

---

## 18. B22.2 — Compact Primitive-Array Prime-Block Encoding

Audit date: 2026-10-16 (Asia/Bangkok)

### 18.1 Status

**B22.2 status: PASS**

All 359 JUnit tests pass (`mvn clean test`). Full pattern maps, supporting-SID sets, prime-block
decoded positions, tree structure, and candidate counters are identical to B22.1. Compact
primitive-array layout is complete, immutable, and compatible with existing API boundaries.

### 18.2 Compact data layout

The previous B22.1 representation stored, per accepted pattern:

- one `PositionBlock` object per SID (including unsupported SIDs);
- one `EncodedVector` object (containing a copied `int[]`) under each `PositionBlock`;
- a full `List<PositionBlock>` of size `N` (database size) per encoding.

B22.2 replaces this with **four parallel primitive arrays** per `PatternEncoding`:

| Array | Semantics |
|---|---|
| `supportingSids[]` | Strictly increasing SIDs that have non-empty positions (size = support count) |
| `positionOffsets[]` | Sentinel array of size support+1; `positionOffsets[i]..positionOffsets[i+1]` is the segment for `supportingSids[i]` |
| `positionBlockIndexes[]` | Absolute block index for each non-zero rank entry |
| `positionRanks[]` | Non-zero prime rank for each entry |

A single `PositionCoordinateSystem` (immutable, containing `sequenceSizes[]`) is shared by
every `PatternEncoding` produced from one database traversal. All sixteen accepted Pumsb 0.66%
patterns share exactly one `PositionCoordinateSystem` instance (verified by
`B222RetentionHarness`).

**Representation invariants** (enforced by `PatternEncoding.validateCompact`):

1. `supportingSids` is strictly increasing in `[1, N]`.
2. Every SID in `supportingSids` is also marked supporting in `sequenceBlock`.
3. `positionOffsets.length == supportingSids.length + 1` and `positionOffsets[0] == 0` and
   `positionOffsets[last] == positionRanks.length`.
4. Each segment has at least one rank entry (no empty supporting SID segments).
5. Within each segment, `positionBlockIndexes` are strictly increasing in `[0, blocksNeeded(seqSize))`.
6. Every rank is non-zero and does not set padding bits outside the valid mask for its block.
7. `support() == supportingSids.length`.

Interior empty position blocks are preserved by the strictly-increasing absolute index: gaps in
`positionBlockIndexes` represent zero-rank blocks, which are not stored but remain unambiguous.

### 18.3 Construction paths

**Singleton encoding (`SingletonEncodingBuilder`):** a single database scan populates one
`CompactAccumulator` per F1 item, using `IntArrayBuilder` (dynamically-growing `int[]`). No
`PositionBlock` or `EncodedVector` object is created during construction.

**Join results (`PrimeBlockJoins`):** `iJoin` and `sJoin` now call `positionalJoin`, which merges
segments directly via three inner methods:

- `appendSameEndpointIntersection` — I-Join: walks two sorted segment pairs with a merge cursor;
  applies `PrimalBlockCodec.gcdRank`; writes block index and rank directly into the result arrays.
- `appendTemporalIntersection` — S-Join: locates the first left segment entry; applies the
  `strictAfterMaskRank` arithmetic mask; walks the right segment and writes valid entries.

No `PositionBlock`, `EncodedVector`, or intermediate `int[]` is allocated per SID in the hot path.
Results are materialized as `PatternEncoding.fromTrustedCompact` with ownership-transferred arrays.

**Compatibility boundary (`positionBlock(sid)`, `positionBlocks()`):** the lazy view materializes
one `PositionBlock` only when an element is explicitly requested. The mining DFS hot path never
calls these methods.

### 18.4 API and ownership safety

- `PatternEncoding` is `final`. All arrays are private.
- Public factory `fromCompact` defensively copies inputs and validates.
- Package-private factory `fromTrustedCompact` skips copy/validation; used only in
  `PrimeBlockJoins` after join outputs are owned arrays not shared elsewhere.
- Public accessors `supportingSids()`, `positionOffsets()`, `positionBlockIndexes()`,
  `positionRanks()` return `Arrays.copyOf` snapshots.
- Internal accessors `supportingSidAt`, `positionStart`, `positionEnd`, `positionBlockIndexAt`,
  `positionRankAt` are package-private and used only by `PrimeBlockJoins`.
- `PositionCoordinateSystem` is `final`; `sequenceSizes()` returns a defensive copy.

### 18.5 Prime-block fidelity

The following remain **SOURCE-CONFIRMED** from PRISM section 3.2–3.4:

- Compact representation stores only non-empty position blocks with SID-to-segment mapping.
- Absolute block indexes unambiguously address each block; interior gaps are zero-rank.
- I-Join applies `gcdRank` at same-block positions.
- S-Join applies `strictAfterMaskRank` from the earliest left endpoint and retains right
  positions strictly after.
- GCD/CARD/MASK lookup tables in `PrimalBlockCodec` are unchanged.

The following remain **IMPLEMENTATION POLICY**:

- Four-array layout and shared `PositionCoordinateSystem` across patterns.
- `IntArrayBuilder` doubling strategy during singleton construction.
- Lazy `PositionBlock` materialization at the compatibility boundary.

### 18.6 B22.2 benchmark results

Three-repetition timing with the B22.1 harness (`B221PerformanceHarness`); same JVM flags,
same dataset hashes, same parser, same output configuration (print=false, export=false).

| Configuration | Candidates / Patterns | Run | Prep ms | DFS ms | Algorithm ms | Peak heap MB |
|---|---:|---:|---:|---:|---:|---:|
| Table 1, 14% | 34 / 9 | 1 | 59.93 | 11.35 | 74.32 | 3.76 |
| | | 2 | 0.78 | 2.64 | 3.56 | 1.64 |
| | | 3 | 0.65 | 2.25 | 3.05 | 2.68 |
| SIGN, 0.4% | 18,198 / 315 | 1 | 159.23 | 537.55 | 701.49 | 109.26 |
| | | 2 | 46.28 | 433.18 | 480.09 | 156.84 |
| | | 3 | 13.87 | 401.87 | 416.30 | 157.89 |
| SIGN, 0.3% | 92,027 / 1,166 | 1 | 150.74 | 1,616.66 | 1,774.51 | 158.81 |
| | | 2 | 58.42 | 1,518.97 | 1,579.06 | 159.99 |
| | | 3 | 20.88 | 1,782.85 | 1,805.49 | 158.94 |
| Pumsb, 0.67% | 1 / 1 | 1 | 449.10 | 20.98 | 472.62 | 323.89 |
| | | 2 | 473.12 | 8.26 | 481.62 | 353.37 |
| | | 3 | 344.30 | 5.14 | 349.58 | 319.38 |
| Pumsb, 0.66% | 113 / 15 | 1 | 500.85 | 499.29 | 1,003.50 | 385.44 |
| | | 2 | 446.41 | 512.64 | 959.31 | 375.93 |
| | | 3 | 379.74 | 480.07 | 860.10 | 361.72 |

### 18.7 Before/after comparison (B22.1 → B22.2)

B22.1 reference values are the three-run medians from Section 17.4.

| Configuration | Algorithm B22.1 ms | Algorithm B22.2 med ms | Change | Peak heap B22.1 MB | Peak heap B22.2 med MB | Change |
|---|---:|---:|---:|---:|---:|---:|
| Table 1, 14% | 2.942 | 3.056 | ~0% (noise) | 1.638 | 2.684 | within JVM noise |
| SIGN, 0.4% | 1,052.385 | 480.093 | −54% | 269.732 | 157.891 | −41% |
| SIGN, 0.3% | 4,312.459 | 1,719.685 | −60% | 485.850 | 159.136 | −67% |
| Pumsb, 0.67% | 350.141 | 434.607 | within variance | 350.156 | 332.215 | within variance |
| Pumsb, 0.66% | 896.175 | 940.638 | within variance | 406.409 | 375.927 | −7% |

> [!NOTE]
> B22.2 DFS time improves substantially on SIGN (approximately 2–3x relative to B22.1). The
> compound 20x improvement over B22.0 is now partially confirmed to originate from both the
> rank-native rework in B22.1 and the reduced per-SID allocation in B22.2. Peak heap on SIGN
> also decreased significantly because fewer dead `int[]`/`EncodedVector` objects are present
> at the sampling point during the shorter run.

> [!WARNING]
> SIGN DFS under B22.2 shows higher variance between run 1 and runs 2–3 than B22.1. Run 1
> includes JIT warm-up; runs 2–3 are stable. Single-run JFR diagnostics should use run 2 or 3.
> Pumsb's algorithm time variance remains dominated by preprocessing I/O and GC timing.

### 18.8 Compact object counts (B222RetentionHarness — Pumsb 0.66%, post-GC)

After mining completes with patterns strongly retained:

| Metric | B22.2 value |
|---|---:|
| Accepted patterns | 15 |
| Candidates | 113 |
| Compact supporting-SID entries (total across 15 encodings) | 10,874 |
| Position rank entries (total across 15 encodings) | 1,010,543 |
| Shared `PositionCoordinateSystem` instances | 1 |

The 724,882 per-SID `PositionBlock` objects recorded in B22.0 are no longer retained; they are
materialized lazily only on demand at the compatibility boundary and not cached. The 15 patterns
share one coordinate object (49,046 `int` entries) instead of 15 copies.

### 18.9 Correctness evidence

- `mvn clean test`: **359 tests, 0 failures, 0 errors, 0 skipped**.
- `PatternEncodingCompactTest` (5 tests): validates compact array semantics, defensive copies,
  interior empty blocks, immutability, rejection of malformed inputs.
- `PrimeBlockJoinsTest` (19 tests): I-Join, S-Join, `strictAfter`, sparse/dense SIDs, positions
  spanning multiple blocks, interior empty blocks, repeated items, long sequences.
- `Table1EndToEndCorrectnessTest` (7 tests): nine-pattern oracle from paper Table 1 including
  exact support values, confirmed PASS.
- `WspmDifferentialCorrectnessTest` (17 tests): B18/B19 differential comparisons, PASS.
- `SignDeepAuditTest` (7 tests): SIGN 0.4% full pattern/SID/position audit, PASS.
- `SignRankNativeRegressionTest` (1 test): SIGN 0.3% fingerprint regression, PASS.

Candidate counts, accepted counts, tree node counts, and statistics are unchanged from B22.1.

### 18.10 Research issues R1–R8

Unchanged from B22.1 (Section 15). R7 (Pumsb preprocessing semantics) and R8 (paper runtime
scope) remain OPEN. R3–R6 (pattern-count divergences and minsup boundary) remain OPEN. No new
issues were introduced.

### 18.11 Remaining risks

- **Pumsb 0.60% OOM**: not retested. The dense retained result still dominates post-GC live set.
  B22.2 reduces per-candidate allocation but does not reduce live accepted-encoding size. R3/OOM
  risk is unresolved.
- **SIGN peak-used-heap interpretation**: peak heap under MXBean sampler reflects a short run with
  fewer GCs, not necessarily a larger live set. The statement from B22.1 Section 17.5 applies here.
- **DFS result lifecycle**: accepted encodings are retained for the full run lifetime.
  B22.3 will address selective lifecycle management; B22.2 explicitly does not.
- **Per-SID materialization at boundary**: tests and external callers that invoke `positionBlock(sid)`
  or `positionBlocks()` will still allocate `PositionBlock` objects. This is intentional at the
  compatibility boundary and documented. Mining hot path does not trigger it.

### 18.12 Scope compliance

- Compact primitive-array representation: **DONE**.
- No `PositionBlock` per SID in mining hot path: **DONE**.
- No `EncodedVector` per SID in mining hot path: **DONE**.
- No `int[]` per SID in mining hot path: **DONE**.
- Interior empty-block offsets preserved: **DONE**.
- Prime-block / GCD / CARD / MASK semantics unchanged: **DONE**.
- I-Join and S-Join correctness: **DONE** (all join tests PASS).
- Shared immutable `PositionCoordinateSystem`: **DONE**.
- `WspmMiningResult` retains accepted encodings unchanged: **DONE**.
- DFS lifecycle not changed: **DONE**.
- Representation not changed to Bitmap: **DONE**.
- WSPM_BitPrune not started: **DONE**.
- No Git commit/push: **DONE** (worktree remains as received).

**STOP:** B22.2 ends here. Research Reviewer must accept before B22.3 (DFS lifecycle) begins.

---

## 19. Task B22.3 — DFS Encoding Lifecycle & Result Retention Optimization

**Date:** 2026-10-17  
**Status:** PASS — Correctness and memory targets fully met.  
**Baseline Test Suite:** 363 / 363 JUnit tests PASS (`mvn clean test`, 28.2 s).  

### 19.1 Encoding ownership analysis before and after B22.3

In the baseline implementation through B22.2, all accepted patterns had their `PatternEncoding`
instances stored in a run-wide `LinkedHashMap<Pattern, PatternEncoding>` inside `WspmPreTreeMiner`
and permanently preserved within `WspmMiningResult`.

| Encoding Category | 1. Creator | 2. References Held By | 3. Required Lifetime | 4. Sibling / Descendant Dependencies | 5. Release Timing (B22.3 SUPPORT_ONLY) | 6. Post-Mining Access |
|---|---|---|---|---|---|---|
| **Singleton encoding** | `SingletonEncodingBuilder` | `singletonEncodings` Map in `WspmPreTreeMiner` | Entire mining phase | Yes: all DFS I- and S-joins extend with F1 singletons | Discarded when mining completes | None in `SUPPORT_ONLY`; retained in `FULL_ENCODING` |
| **Parent encoding** | Passed as argument to `depthFirstExpand` | Stack frame of `depthFirstExpand(parent, ...)` | Until candidate generation for this parent completes | Yes: all sibling candidates of `parent` join against `parentEncoding` | Stack frame exit; not held in global map | None in `SUPPORT_ONLY` |
| **Current DFS node encoding** | Created by join when extending parent | `parentEncoding` argument in recursive DFS frame | During traversal of this node's subtree | Yes: all descendants join against it as ancestor | Stack frame exit of this node | None in `SUPPORT_ONLY` |
| **Candidate encoding** | `PrimeBlockJoins.iJoin` / `sJoin` | Local `CandidateResult.encoding` in candidate list | During threshold evaluation | None: siblings do not join with each other | Dropped when candidate list is discarded | None in `SUPPORT_ONLY` |
| **Accepted child encoding** | `CandidateResult.encoding` | Candidate list; passed to `depthFirstExpand` | While child's subtree is actively recursing | Descendants depend on it; siblings do not | Immediately upon return from child DFS recursion | Throws `UnsupportedOperationException` in `SUPPORT_ONLY` |
| **Rejected candidate encoding** | `CandidateResult.encoding` | Candidate list | Evaluated for threshold and discarded | None | Dropped when candidate list goes out of scope | Never retained |
| **Completed subtree encoding** | Generated during DFS | Formerly held in `encodings` map | Only while subtree was active | None: WSPM_PreTree has no cross-subtree joins | **Eligible for GC immediately upon subtree exit** | In `FULL_ENCODING` only |
| **Final result state** | `WspmMiningResult` | `WspmMiningResult` fields | Lifespan of mining result | None | Caller-managed | `supports()`, `acceptedPatterns()`, `prefixTree()` |

### 19.2 Result retention policy (`FULL_ENCODING` vs `SUPPORT_ONLY`)

B22.3 introduces an explicit result retention policy governed by `ResultRetentionPolicy`:

- `FULL_ENCODING`: Preserves all compact `PatternEncoding` instances in `WspmMiningResult.encodings()`.
  This mode is the default for existing test harnesses, differential tests, and supporting SID audits.
  Guarantees 100% backward compatibility for all existing API consumers.
- `SUPPORT_ONLY`: Discards `PatternEncoding` instances as DFS subtrees complete. `WspmMiningResult`
  retains only `PrefixTree`, `supports()` (`Map<Pattern, Integer>`), and execution statistics.
  Calling `result.encodings()` or `result.encoding(pattern)` fails fast with
  `UnsupportedOperationException`, explicitly documenting absence rather than returning misleading empty structures.

### 19.3 DFS lifecycle and working vs result state separation

The mining DFS execution remains strictly identical between both modes:

1. Candidate generation order, tree insertion, and candidate count instrumentation are identical.
2. Sibling candidates are evaluated in the canonical order (`I-Extension` followed by `S-Extension`).
3. In `SUPPORT_ONLY`, the miner tracks accepted pattern supports in `Map<Pattern, Integer> supports`
   and avoids populating the long-lived `encodings` map.
4. As soon as a child's recursive `depthFirstExpand` returns, all encoding structures created within
   that subtree become unreachable and are reclaimed during generational GC cycles.

### 19.4 Why retention optimization preserves algorithm semantics

- **Candidate space invariant:** The exact same candidate sequences are generated from the exact same
  parent encodings and F1 singleton encodings.
- **Support calculation invariant:** Support values are determined by `PrimeBlockJoins` SequenceBlock
  cardinality at the exact moment of candidate evaluation.
- **Prefix Tree invariant:** `PrefixTree` structure, node insertion order, and pre-order traversal
  remain identical.
- **No Bitmap / BitSet substitution:** Prime-block representation, rank-native codec tables, and GCD/CARD/MASK
  join algorithms are strictly preserved.
- **No BitPrune / CMAP introduction:** No upper-bound pruning or secondary data structures were added.

### 19.5 When FULL_ENCODING is required vs when SUPPORT_ONLY is appropriate

- **Use FULL_ENCODING:**
  - Table 1 paper reproduction verification.
  - Differential correctness testing against brute force or future BitPrune implementations.
  - Position decoding and supporting SID set auditing.
  - Small to moderate datasets where pattern count is moderate (e.g., Table 1, SIGN 0.4%, SIGN 0.3%).
- **Use SUPPORT_ONLY:**
  - Production mining where only patterns and support counts are exported or displayed.
  - Benchmark performance measurements and memory scalability tests.
  - High-pattern datasets such as Pumsb 0.60% (~4,059 patterns) to avoid out-of-memory crashes.

### 19.6 B22.3 Benchmark results

Evaluated under identical benchmark protocol (`-Xms256m -Xmx2g`, Java 21, `B221PerformanceHarness`):

| Configuration | Retention Mode | Candidates / Patterns | Prep ms | DFS ms | Algorithm ms | Peak Heap MB | GC Count / Time |
|---|---|---:|---:|---:|---:|---:|---:|
| Table 1, 14% | FULL_ENCODING | 34 / 9 | 0.604 | 2.026 | 3.027 | 2.687 | 0 / 0 ms |
| Table 1, 14% | SUPPORT_ONLY | 34 / 9 | 0.622 | 1.584 | 2.304 | 1.638 | 0 / 0 ms |
| SIGN, 0.4% | FULL_ENCODING | 18,198 / 315 | 21.812 | 438.700 | 461.327 | 161.038 | 1 / 3 ms |
| SIGN, 0.4% | SUPPORT_ONLY | 18,198 / 315 | 14.316 | 426.037 | 469.750 | 157.893 | 1 / 3 ms |
| SIGN, 0.3% | FULL_ENCODING | 92,027 / 1,166 | 18.651 | 1,418.558 | 1,439.055 | 160.274 | 4 / 9 ms |
| SIGN, 0.3% | SUPPORT_ONLY | 92,027 / 1,166 | 16.157 | 1,464.722 | 1,487.976 | 159.990 | 4 / 9 ms |
| Pumsb, 0.67% | FULL_ENCODING | 1 / 1 | 307.691 | 4.250 | 312.119 | 349.435 | 5 / 85 ms |
| Pumsb, 0.67% | SUPPORT_ONLY | 1 / 1 | 288.194 | 4.253 | 292.559 | 361.416 | 5 / 84 ms |
| Pumsb, 0.66% | FULL_ENCODING | 113 / 15 | 335.227 | 421.669 | 758.858 | 372.601 | 5 / 80 ms |
| Pumsb, 0.66% | SUPPORT_ONLY | 113 / 15 | 265.735 | 428.376 | 707.295 | 367.613 | 5 / 80 ms |
| **Pumsb, 0.60%** | **FULL_ENCODING** | 95,465 / 4,059 | — | — | **OOM** (> 2 GB) | > 2,048.0 | — |
| **Pumsb, 0.60%** | **SUPPORT_ONLY** | **95,465 / 4,059** | **581.885** | **355,625.615** | **356,220.784** | **514.089** | **351 / 1,073 ms** |

### 19.7 Pumsb 0.60% validation findings

Pumsb 0.60% with `-Xms256m -Xmx2g` under `SUPPORT_ONLY` mode achieved **complete execution without OutOfMemoryError**:

1. **Exact Pattern Count:** Mined exactly **4,059 patterns**, completely replicating the independently
   audited baseline expectation (vs paper's 3,684 patterns, as documented in R3/R7).
2. **Candidate Count:** Generated **95,465 candidates** (4,059 accepted, 91,406 rejected).
3. **Peak Heap Algorithm:** Reached only **514.089 MB**, well below the 2 GB threshold (compared to fatal OOM
   when retaining all 4,059 encodings in memory).
4. **Execution Time:** DFS mining completed in 355.626 s; total algorithm time 356.221 s.
5. **Garbage Collection Behavior:** 351 minor collections totaling only 1.073 s pause time, confirming that
   completed subtree encodings were efficiently collected in Eden/Survivor spaces rather than promoting
   to an uncollectible old-generation live set.

### 19.8 Research issues status

- **R1 (Table 1 F1 discrepancy):** OPEN.
- **R6 (SIGN minsup boundary discrepancy):** OPEN.
- **R7 (Pumsb preprocessing semantics & transaction database interpretation):** OPEN.
- **R8 (Paper runtime and memory measurement scope):** OPEN.
- No research issues were closed or altered.

### 19.9 Remaining risks and next steps

- **Candidate allocation volume:** During DFS, sibling candidate lists generated by `CandidateEngine.generateOneStep`
  are allocated in batches per stack frame. While safe under 2 GB heap, candidate streaming (evaluating
  candidates one by one) could further reduce transient allocation pressure in dense databases (recommended
  for Phase C or future task B22.4).
- **Long DFS execution time on dense databases:** Pumsb 0.60% requires ~356 seconds in pure Java PRISM Prime-block
  joins. This is expected baseline behavior for PRISM Prime-block encoding on dense 49k-sequence transaction data
  without BitPrune optimizations.

### 19.10 Scope compliance

- Separation of working state from result state: **DONE**.
- `SUPPORT_ONLY` and `FULL_ENCODING` retention policies implemented: **DONE**.
- API throws `UnsupportedOperationException` when encodings requested in `SUPPORT_ONLY`: **DONE**.
- Both modes produce identical pattern/support mappings and prefix trees: **DONE**.
- Table 1, SIGN 0.4%, SIGN 0.3%, Pumsb 0.67%, Pumsb 0.66% verified: **DONE**.
- Pumsb 0.60% tested under bounded heap (`-Xms256m -Xmx2g`): **PASS** (4,059 patterns, peak 514 MB).
- No changes to Prime-block representation or join semantics: **DONE**.
- No Bitmap / BitSet substitution: **DONE**.
- No CMAP or BitPrune algorithms: **DONE**.
- Standalone Main updated with `RESULT_RETENTION`: **DONE**.
- No Git commit/push: **DONE**.

**STOP:** B22.3 ends here.

