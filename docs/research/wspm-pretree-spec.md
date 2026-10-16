# WSPM_PreTree Research Contract

## 1. Purpose and status

This document is the research contract for Phase B of this repository. It defines what a faithful WSPM_PreTree baseline must preserve before implementation begins.

Every material statement is classified as one of:

- `SOURCE-CONFIRMED`: explicitly stated, formulated, tabulated, or shown by an example in an identified source.
- `REASONABLE-INFERENCE`: needed for implementation and supported by the sources, but not specified completely enough to call exact source behavior.
- `UNSPECIFIED`: the available source does not determine the behavior reliably.

This contract does not authorize an approximation to be presented as the original algorithm. In particular, it does not authorize replacing Prime-block Encoding with a bitmap, an ID-list, or a direct database scan.

## 2. Source metadata

### 2.1 Baseline source

- Title: *Mining Weighted Sequential Patterns Based on Prefix-Tree and Prism Encoding*
- Authors: Thi-Thiet Pham, Thuy-Duong Vu, Tai-Du Nguyen, Bao Huynh, Trang Van
- Journal: *Vietnam Journal of Computer Science*, volume 10, issue 3, pages 357–372
- Year: 2023
- DOI: [10.1142/S219688882350001X](https://doi.org/10.1142/S219688882350001X)
- Repository copy: `docs/Mining_weighted_sequential_patterns_based_on_prefix_tree_and_prism_encoding_2023.pdf`
- Role: primary authoritative source for the WSPM_PreTree baseline.

### 2.2 Prime-block dependency

- Title: *PRISM: An Effective Approach for Frequent Sequence Mining via Prime-block Encoding*
- Authors: Karam Gouda, Mosab Hassaan, Mohammed J. Zaki
- Journal: *Journal of Computer and System Sciences*, volume 76, issue 1, pages 88–102
- Year: 2010
- DOI: [10.1016/j.jcss.2009.05.008](https://doi.org/10.1016/j.jcss.2009.05.008)
- Repository copy: `docs/Prism_An effective approach for frequent sequence mining prime-block encoding.pdf`
- Role: authoritative dependency for Prime-block/Primal-block representation, support extraction, and join operations referenced by the WSPM_PreTree paper as reference [7].

The WSPM paper uses the term “prime-block encoding,” while the PRISM paper also uses “primal block encoding” in its technical definitions. Phase B must record the adopted terminology without treating differently named representations as different algorithms unless the sources establish a difference.

### 2.3 Scope source

- Document: `docs/CNTT-KLCN256_thuật toán WSPM_PreTree_Đã sửa.txt`
- Role: confirms that the thesis requires a Java 21 WSPM_PreTree baseline, correctness evaluation, and later comparison with WSPM-BitPrune.
- Phase constraint: this contract covers the baseline only. Spring Boot, database, API, UI, recommendation logic, and WSPM-BitPrune are outside Phase B.

## 3. Notation

- `I = {i1, i2, ..., im}`: the set of distinct items.
- An itemset is written `(abc)` for `{a, b, c}`.
- A sequence is written as an ordered list of itemsets, for example `<(a)(bc)>`.
- A singleton itemset may be abbreviated in paper examples, so `<a (bc)>` and `<(a)(bc)>` have the same itemset structure.
- `SD`: a sequence database.
- `SID`: a unique sequence identifier.
- `L(S)`: the length of sequence `S`.
- `T(i)`: the total number of appearances of item `i` in `SD`.
- `support(P)`: the number of database sequences containing pattern `P`.

Canonical serialization syntax for Java is not fixed by the paper. Choosing one deterministic syntax is an implementation decision for B9.

### 3.1 Internal item identifier policy — `REASONABLE-INFERENCE`

From B2.1 onward, production-domain item identity uses a primitive Java `int` inside the immutable `Item` value object.

- Negative identifiers are invalid. This prevents SPMF delimiters such as `-1` and `-2` from becoming domain items.
- Identifier `0` is allowed because the paper does not prohibit it. A parser may not reject it without a separately documented format rule.
- Itemsets use ascending numeric identifier order for deterministic canonical storage, so `2 < 10`.
- This storage order is not yet claimed to be the final WSPM_PreTree mining comparator; B9/B10 must validate candidate-order semantics.
- Paper labels `a, b, ..., h` are external presentation/test labels. A later Table 1 fixture may map them deterministically to integers, but that mapping belongs to the fixture layer and is not production item semantics.

Using an `int` avoids retaining a `String` representation for each numeric identifier and is expected to reduce identifier representation overhead. No measured memory or performance improvement is claimed without a benchmark.

## 4. Core definitions

### 4.1 Item — `SOURCE-CONFIRMED`

An item is one distinct member of `I`. Item identity is significant. The paper does not prescribe a Java type for an item.

### 4.2 Itemset — `SOURCE-CONFIRMED`

An itemset is a non-empty subset of `I`. Its size is the number of items it contains. An item may occur at most once in one itemset.

Itemsets do not encode temporal order among their members. A deterministic internal item order may be needed for enumeration and serialization, but that order must not turn an itemset into a sequence.

### 4.3 Sequence — `SOURCE-CONFIRMED`

A sequence is an ordered list of itemsets:

```text
S = <s1 s2 ... sn>
```

`s1` occurs before `s2`, which occurs before `s3`, and so on. The same item may appear in different itemsets of one sequence.

Consequently:

```text
<(a)(b)> != <(ab)>
```

The first pattern has two temporally ordered itemsets. The second has one itemset containing two items.

### 4.4 Sequence size — `SOURCE-CONFIRMED`

The size of a sequence is its number of itemsets.

### 4.5 Sequence length — `SOURCE-CONFIRMED`

The length of a sequence is the total number of item occurrences across all its itemsets:

```text
L(S) = sum(|sj|), for every itemset sj in S
```

A sequence of length `l` is an `l`-sequence. Length and size are not interchangeable.

### 4.6 Subsequence — `SOURCE-CONFIRMED`

For sequences:

```text
alpha = <a1 a2 ... an>
beta  = <b1 b2 ... bm>
```

`alpha` is a subsequence of `beta` if and only if there are indices:

```text
1 <= j1 < j2 < ... < jn <= m
```

such that:

```text
a1 subset-of b[j1]
a2 subset-of b[j2]
...
an subset-of b[jn]
```

This definition preserves both itemset membership and temporal order.

### 4.7 Sequence database — `SOURCE-CONFIRMED`

A sequence database is a set of sequence records. Each record is a tuple `<SID, S>` containing a unique sequence identifier and a sequence.

### 4.8 Support — `SOURCE-CONFIRMED`

The support of sequence/pattern `alpha` is the number of sequences in `SD` that contain `alpha` as a subsequence:

```text
support(alpha) = |{ S in SD : alpha is a subsequence of S }|
```

Support is counted at most once per database sequence. It is not the total number of occurrences. Multiple embeddings or repeated appearances inside the same sequence still contribute one to absolute support.

### 4.9 Sequential pattern — `SOURCE-CONFIRMED`

In the paper's ordinary sequential-pattern definition, `alpha` is a sequential pattern when:

```text
support(alpha) >= minsup
```

The unit of `minsup` must match the support representation used by that comparison. The paper later states `minsup` values as percentages, but does not fully specify the conversion in the pseudocode; see Section 7.

### 4.10 Prefix — `SOURCE-CONFIRMED` with unresolved notation

The paper defines a prefix through shared initial itemsets and uses examples such as the prefixes `<(a)>` and `<(a)(bc)>` of `<(a)(bc)(d)>`. Operationally, every child produced by a legal I-Extension or S-Extension retains its parent pattern as the search-tree prefix.

The typeset formal condition around the last itemset and the accompanying example are not sufficiently clear to define all edge cases for a canonical Java `isPrefixOf` operation. Exact treatment of an incomplete last-itemset prefix is therefore `UNSPECIFIED` until B9/B10 reconcile the definition with the source figures and PRISM search tree.

### 4.11 Prefix Tree — `SOURCE-CONFIRMED`

The Prefix Tree is a lexicographic search tree with:

- an empty/null sequence at the level-0 root;
- frequent/accepted 1-patterns at level 1;
- a `k`-pattern stored at a level-`k` node;
- direct parent-child relationships produced by adding one item through I-Extension or S-Extension;
- child sequences sharing their parent prefix.

Here, level follows pattern length in items, not sequence size in itemsets.

### 4.12 I-Extension / Itemset Extension — `SOURCE-CONFIRMED`

I-Extension adds one item to the final itemset. The sequence size does not change.

```text
<(a)> +I b = <(ab)>
```

For duplicate prevention, the added item must be lexicographically greater than the items already in the final itemset. The paper pseudocode expresses this as selecting `Pi` greater than the last item in the last itemset.

The concrete comparator for non-letter item identifiers is `UNSPECIFIED`; it must be made explicit and deterministic in B9/B10.

### 4.13 S-Extension / Sequence Extension — `SOURCE-CONFIRMED`

S-Extension appends one item as a new singleton itemset. Sequence size increases by one.

```text
<(a)> +S b = <(a)(b)>
```

The paper pseudocode considers each eligible 1-pattern in `F1` for sequence extension.

## 5. Weight definitions

### 5.1 Item weight — `SOURCE-CONFIRMED`

Let `T(i)` be the total number of appearances of item `i` in the database, counting repeated appearances across different itemsets. Let the total database length be:

```text
totalLength(SD) = sum(L(Sj)), for j = 1..|SD|
```

The item weight used by the paper and demonstrated by Table 2 is:

```text
W(i) = T(i) / totalLength(SD)
```

For Table 1, `totalLength(SD) = 11 + 10 + 8 + 9 + 12 + 7 + 9 = 66`, which is the denominator shown for every item in Table 2.

The implementation must compute both numerator and denominator from the parsed database. Table 2 values are test expectations, not production constants.

#### B6 Eq. (1) denominator decision — `SOURCE-CONFIRMED` with preserved inconsistency

The WSPM_PreTree paper's prose immediately below Eq. (1) describes `L(Sj)` as the length of a
sequence containing item `i`. Read literally, that wording suggests dividing by the sum of only the
lengths of sequences containing `i`. Its worked `W(a)` example cannot distinguish that interpretation
from a global denominator because `a` occurs in all seven Table 1 sequences.

Table 2 does distinguish the interpretations: every item `a` through `h` uses denominator `66`, even
when the item is absent from some sequences. The cited SPMW source, *Efficient Mining of Sequential
Patterns in a Sequence Database with Weight Constraint* (reference `[21]` in the repository's accepted
WSPM manuscript; referred to as Ref. 20 in the B6 task), defines `S1, ..., Sn` as all sequences in the
database and gives:

```text
W(i) = T(i) / sum(L(Sj)), for j = 1..n
```

Its worked example likewise sums the lengths of every database sequence. Therefore B6 adopts the
global database length as the production denominator because it matches both the authoritative Table 2
oracle and the cited source definition:

```text
W(i) = occurrenceCount(i) / totalDatabaseLength
```

The containing-sequence wording remains recorded as a source inconsistency. The B5
`sumOfContainingSequenceLengths` metric is retained as raw diagnostic data but is not the B6 item-weight
denominator.

#### B6 numeric policy — `REASONABLE-INFERENCE`

Production stores item weights as Java `double`, computed directly from the `long` numerator and
denominator without formatting or decimal rounding. MaxW and MinW are selected from computed weights,
and `MeanW = (MaxW + MinW) / 2`. Tests use an absolute tolerance of `1e-12`; three-decimal values in
the paper are display approximations only. An empty database has no MaxW, MinW, or MeanW and is
represented with empty optional values rather than fabricated zeros.

#### B5 statistics representation — `REASONABLE-INFERENCE`

The B5 Java statistics layer stores item identifiers and sequence support as `int`, while total
occurrence counts, sums of containing-sequence lengths, and total database length use `long`.
Results contain only items present in the database and are exposed in ascending numeric identifier
order; querying an absent valid identifier returns no result. Empty input produces zero database
metadata and an empty item-statistics map. These are deterministic engineering choices, not numeric
or API rules stated by the paper.

### 5.2 Itemset weight — `SOURCE-CONFIRMED`

For itemset `x = {i1, i2, ..., in}`:

```text
W(x) = (W(i1) + W(i2) + ... + W(in)) / n
```

It is the arithmetic mean of the weights of its items.

### 5.3 Sequence weight — `SOURCE-CONFIRMED`

For sequence `S = <x1, x2, ..., xm>`:

```text
W(S) = (W(x1) + W(x2) + ... + W(xm)) / m
```

It is the arithmetic mean of itemset weights. It is not necessarily the arithmetic mean of all item occurrences when itemset sizes differ.

### 5.4 Source inconsistency in the worked weight example — `UNSPECIFIED`

The formulas printed as Eq. (2) and Eq. (3) use the two arithmetic means above. With the Table 2 values, they imply approximately:

```text
W((abd)) = (0.348 + 0.181 + 0.075) / 3 ≈ 0.201
W((ac))  = (0.348 + 0.166) / 2 ≈ 0.257
W(<(abd)(ac)>) = (0.201 + 0.257) / 2 ≈ 0.229
```

However, the paper's worked prose reports `W(<(abd)(ac)>) ≈ 0.559`. That value is obtained by averaging the *unnormalized sums* of item weights inside the two itemsets:

```text
((0.348 + 0.181 + 0.075) + (0.348 + 0.166)) / 2 = 0.559
```

Therefore the displayed equations and the reported example result are inconsistent. This contract preserves both pieces of evidence and does not choose one silently. Before B6 implements itemset or sequence weight, it must inspect cited source [20] and/or obtain an authoritative clarification. Until then, the executable interpretation is `UNSPECIFIED`.

The WSPM_PreTree acceptance pseudocode uses `MeanW`, not `W(S)`, in its candidate condition. The implementation must not silently substitute sequence weight for `MeanW`.

### 5.5 MaxW — `SOURCE-CONFIRMED`

For the distinct items in `SD`:

```text
MaxW = max(W(i))
```

### 5.6 MinW — `SOURCE-CONFIRMED`

For the distinct items in `SD`:

```text
MinW = min(W(i))
```

### 5.7 MeanW — `SOURCE-CONFIRMED`

The paper defines:

```text
MeanW = (MaxW + MinW) / 2
```

This is the midpoint of the maximum and minimum item weights. It is not the average weight over all items.

## 6. Weighted sequential pattern condition

### 6.1 Paper condition — `SOURCE-CONFIRMED`

A sequence/pattern `P` is accepted as a weighted sequential pattern when:

```text
support(P) * MeanW >= minsup
```

WSPM_PreTree applies the same condition to F1 items and generated candidates before inserting them into the Prefix Tree.

This contract deliberately does not replace that expression with another weighted-support definition.

### 6.2 R2 — Percentage minsup semantics

**Status: `RESOLVED FOR IMPLEMENTATION AS STRONGLY SUPPORTED INFERENCE`.**

The paper defines `support(P)` as an absolute sequence count, labels example thresholds such as `minsup = 14%`, and compares `support(P) * MeanW` directly with `minsup`. It does not explicitly show the percentage-to-internal-threshold conversion. B7 therefore adopts the following unit-preserving contract:

```text
relativeMinSup = configuredMinSup / 100.0
effectiveMinSup = relativeMinSup * |SD|
support(P) * MeanW >= effectiveMinSup
```

The effective threshold remains a real number. It is not passed through `ceil`, `floor`, or `round` before the weighted comparison.

For Table 1:

```text
|SD| = 7
14% of 7 = 0.98
```

Using the unrounded effective numeric threshold `0.98` reproduces the reported F1 because the exact B6 `MeanW = 12/66` gives:

```text
7 * (12/66) = 1.272727... >= 0.98
6 * (12/66) = 1.090909... >= 0.98
5 * (12/66) = 0.909090... < 0.98
```

This yields the paper's `a:7, b:7, c:6` boundary without hard-coding item identities.

The conversion is not promoted to `SOURCE-CONFIRMED`, because the paper never states it explicitly. It is stronger than an arbitrary engineering choice: it is dimensionally consistent and reproduces the paper oracle.

### 6.3 B7 implementation policy

- Percentage configuration accepts finite values in `[0, 100]`. Zero is mathematically valid, although a positive threshold is normally more meaningful for experiments.
- Absolute configuration accepts finite values greater than or equal to zero and is not scaled by `|SD|`.
- Percentage resolution rejects `|SD| = 0`; silently deriving zero would make an empty mining run appear meaningful. Absolute resolution remains defined for an empty database because it requires no database-size conversion.
- The comparison is inclusive (`>=`) and uses raw `double` values without a production epsilon.
- Configuration, optional relative representation, sequence count, and effective threshold remain separately accessible for future audit logging.

## 7. Table 1 correctness oracle

### 7.1 Test fixture mapping — `REASONABLE-INFERENCE`

The Java test fixture maps the paper's presentation labels to numeric identifiers as follows:

```text
a=1, b=2, c=3, d=4, e=5, f=6, g=7, h=8
```

This mapping is a `TEST FIXTURE CONVENTION`. It is not a WSPM paper rule and does not define
production item semantics or the final mining comparator.

### 7.2 Database structure — `SOURCE-CONFIRMED`

```text
SID 1: <a (abc) (ac) (ad) (acf)>
SID 2: <(abd) c (bc) (ae) b c>
SID 3: <(ef) (ab) (df) c b>
SID 4: <e g (af) (abc) b c>
SID 5: <a (abg) (cdg) (eg) g (gh)>
SID 6: <a (abd) b (ac)>
SID 7: <a (ab) a a a a a a>
```

Singletons without parentheses still represent singleton itemsets.

### 7.3 Required checkpoints — `SOURCE-CONFIRMED`

```text
Sequences       = 7
Distinct items  = 8
Items           = a, b, c, d, e, f, g, h
Sequence lengths = 11, 10, 8, 9, 12, 7, 9
Total length    = 66
```

Occurrences of `a` per sequence:

```text
5, 2, 1, 2, 2, 3, 8
```

The paper's sanity-check pattern is:

```text
support(<(a)(bc)>) = 3
```

It occurs in SIDs 1, 2, and 4. This example also demonstrates that occurrence count and sequence support are different quantities.

## 8. Table 2 correctness oracle

These values are `SOURCE-CONFIRMED` expected results for Table 1:

```text
W(a) = 23/66 ≈ 0.348
W(b) = 12/66 ≈ 0.181
W(c) = 11/66 ≈ 0.166
W(d) =  5/66 ≈ 0.075
W(e) =  4/66 ≈ 0.060
W(f) =  4/66 ≈ 0.060
W(g) =  6/66 ≈ 0.090
W(h) =  1/66 ≈ 0.015
```

Consequently:

```text
MaxW  = W(a)
MinW  = W(h)
MeanW = (W(a) + W(h)) / 2 ≈ 0.181
```

Exact fractions should be retained as long as practical in tests. The paper's decimal values are truncated/rounded display values and must not be hard-coded into production logic or rounded early during acceptance decisions.

The concrete Java numeric representation and test tolerance remain `UNSPECIFIED` and must be decided explicitly in B6.

## 9. F1 correctness oracle

For the paper example stated as `minsup = 14%`, the expected F1 is `SOURCE-CONFIRMED`:

```text
<(a)> : 7
<(b)> : 7
<(c)> : 6
```

These entries and supports must be computed from Table 1 in B8. They must not be hard-coded by item name.

### 9.1 B8 F1 production contract

For every item present in the B5 statistics, B8 uses the already-computed values from B5–B7:

```text
support = sequenceSupport(item)
weight factor = global MeanW
threshold = effectiveMinSup
accept iff sequenceSupport(item) * global MeanW >= effectiveMinSup
```

F1 must not substitute `occurrenceCount` for support or the current item's `W(i)` for global `MeanW`. It performs no rounding and delegates the inclusive comparison to the B7 resolved-threshold contract.

The B8 entry stores only `itemId` and `support`; the general pattern identity and serialization contract remain deferred to B9. F1 entries are returned in ascending numeric item-ID order as a `REASONABLE-INFERENCE` / implementation policy for deterministic results. This ordering is not claimed to be the final mining comparator.

For consistent empty statistics and B6 weights, F1 is empty; no missing `MeanW` is replaced with zero. Mismatched sequence counts or item sets fail fast.

### 9.2 B9 Pattern production contract

The general `Pattern` value object represents only an ordered list of immutable, non-empty `Itemset` values. Its identity is the complete ordered itemset structure, so boundaries and temporal order are significant:

```text
<(1)(2)> != <(1 2)>
<(1)(2)> != <(2)(1)>
```

Support, weight, MeanW, encoding, tree relationships, counters, and pruning state are excluded from `Pattern` and therefore from equality and hashing. B8 support remains separate metadata.

The database `Sequence` type remains non-empty. B9 introduces `Pattern.empty()` with serialization `<>` solely as the structural representation needed for the future Prefix Tree root. This is a `REASONABLE-INFERENCE` / implementation policy based on the paper's empty level-0 root.

Pattern size and length retain the paper's definitions:

```text
size   = number of itemsets
length = total number of items across itemsets
```

Serialization is deterministic numeric text such as `<(1)(2 3)(4)>`. Pattern reuses the existing `Itemset` canonical numeric storage order and introduces no second comparator. Storage order alone did not establish mining semantics in B9; the B10 decision below separately adopts the same numeric order for I-Extension and records its inference status.

### 9.3 B10 structural extension contract

#### I-Extension — `SOURCE-CONFIRMED` structure and constraint

I-Extension adds one item to the final itemset. The number of itemsets does not change and pattern length increases by one. The source requires the extension item to be lexicographically greater than all items already in the final itemset; Figure 1 expresses the gate as:

```text
extensionItem > last item in last itemset
```

The strict gate prevents duplicate branches and also rejects an item already present in the final itemset. Invalid requests fail fast rather than returning the input unchanged.

#### I-Extension comparator realization — `IMPLEMENTATION POLICY / REASONABLE-INFERENCE`

The paper does not define a Java comparator or the mapping of arbitrary source labels to integer IDs. B10 realizes lexicographic order with `Item` natural order, namely ascending numeric internal item ID. This matches existing `Itemset` canonical storage, so `Pattern.lastItem()` is the maximum item under the implemented gate. The Table 1 fixture's `a -> 1, b -> 2, ...` mapping preserves paper-label order only because the test mapping was deliberately chosen that way; production does not depend on alphabetic labels.

#### S-Extension — `SOURCE-CONFIRMED`

S-Extension appends the item as a new singleton itemset. Pattern size and length each increase by one. An item may repeat across itemsets, so `S(<(1)>, 1) = <(1)(1)>` is legal.

#### Empty pattern behavior — `REASONABLE-INFERENCE / IMPLEMENTATION POLICY`

```text
S(<>, x) = <(x)>
I(<>, x) = invalid
```

S-Extension from the structural root creates a level-1 pattern. I-Extension is rejected because the empty pattern has no final itemset. Both operations return new immutable patterns and perform no support calculation, database access, candidate enumeration, tree mutation, or encoding.

### 9.4 B11 Prefix Tree structural contract

#### Root and level — `SOURCE-CONFIRMED`

The Prefix Tree has exactly one level-0 root represented in Java by `Pattern.empty()`. The root has no parent and is not a mined pattern. The paper places a k-pattern at level k; here k is pattern length in items, not the number of itemsets. Therefore both `<(1 2)>` and `<(1)(2)>` are level 2.

#### Node and edge identity — `SOURCE-CONFIRMED` structure

Each non-root node represents one structural `Pattern` and has one direct parent produced by adding exactly one item through either I-Extension or S-Extension. B11 stores the corresponding `ExtensionType` on the non-root node because the source explicitly distinguishes these edge-producing operations and later Prime-block joins are extension-specific. Support and all other mining metadata remain excluded.

B11 validates only an immediate B10 extension by reconstructing the declared I- or S-Extension. It deliberately does not expose a general `isPrefixOf` predicate, whose typeset edge cases remain unresolved.

#### Uniqueness and insertion — `IMPLEMENTATION POLICY`

A tree-wide `Pattern -> node` index enforces one logical node and one parent for every structural pattern. Re-inserting the identical parent/child/type edge is idempotent and returns the existing node. Attempts to attach the same pattern under another parent or edge type fail fast, as do missing parents, insertion of the root as a child, and skipped-level edges.

#### Child ordering and traversal — `IMPLEMENTATION POLICY`

Children are sorted by canonical Pattern serialization to provide stable lookup/debug output. A structural pre-order helper traverses only nodes already in the tree and includes the root. Neither ordering is presented as the paper's mining DFS order; B11 performs no candidate generation, support calculation, acceptance check, or recursion that expands the search space.

Child collections and traversal results are immutable snapshots. Prefix Tree nodes contain only Pattern, parent, edge type, and children; they contain no support, weight, MeanW, minsup, encoding, bitmap, counters, or pruning state.

The paper also states that the complete example result contains nine weighted sequential patterns. The text explicitly identifies these four children of `<(a)>`:

```text
<(a)(a)> : 6
<(a)(b)> : 7
<(a)(c)> : 6
<(ab)>   : 7
```

The complete exact set of nine `pattern -> support` pairs appears graphically in Figure 2 but is not fully enumerated in the paper's prose. Extraction and independent verification of every pair were therefore `NOT VERIFIED` in B1. B17 subsequently completed this verification; see Section 9.7.

### 9.5 B15 one-step Candidate Engine contract

`CandidateEngine` evaluates exactly one extension layer for a non-empty parent pattern. It does
not access or mutate `PrefixTree`, recurse, or scan the sequence database. Its inputs are the
parent `Pattern` and `PatternEncoding`, F1 metadata, matching singleton encodings, global `MeanW`,
and the existing B7 `ResolvedMinimumSupport`.

For each F1 item, B15 applies the following source-derived behavior:

- eligible I-Extensions are constructed by the existing `PatternExtensions.iExtend` and encoded
  by `PrimeBlockJoins.iJoin(parentEncoding, singletonEncoding)`;
- all S-Extensions are constructed by `PatternExtensions.sExtend` and encoded by
  `PrimeBlockJoins.sJoin(parentEncoding, singletonEncoding)`;
- support is read only from the post-join `candidateEncoding.sequenceBlock().support()`;
- weighted acceptance is delegated to `ResolvedMinimumSupport.meetsThreshold(support, MeanW)`,
  preserving the inclusive B7 comparison without another conversion or epsilon;
- structurally valid candidates remain in the result even when support is zero or weighted
  acceptance fails. `CandidateResult.accepted()` distinguishes candidates eligible for later B16
  insertion from rejected candidates.

I/S structure, use of Prime-block candidate support, and the global-MeanW condition are
**SOURCE-CONFIRMED**. Applying `encoding(parent)` and `encoding(singleton item)` directly as join
operands remains the documented **REASONABLE-INFERENCE** validated in B14 and again by B15
differential tests.

The API and ordering are **IMPLEMENTATION POLICY**: inputs are snapshotted and validated, duplicate
F1 item IDs are rejected, F1 support must equal singleton-encoding support, SID universes must
match, and output is immutable. Results are ordered as eligible I-Extensions by ascending numeric
item ID followed by S-Extensions in the same item order. This deterministic order is not presented
as the paper's DFS traversal order.

For Table 1 and parent `<(a)>`, B15 generates five structurally valid candidates. `<(ab)>:7`,
`<(a)(a)>:6`, `<(a)(b)>:7`, and `<(a)(c)>:6` pass the 14% weighted threshold. `<(ac)>:3` is
structurally valid but rejected. At B15 this focused one-step evidence did not implement B16 or
resolve the then-open B17/R4 full nine-pattern oracle.

### 9.6 B16 DFS miner integration

`WspmPreTreeMiner` now composes the verified B3-B15 production components. It calculates database
statistics and weights, resolves minimum support, builds F1, creates full singleton Prime-block
encodings, attaches F1 below the empty root, and performs depth-first expansion. Every expansion
is delegated to `CandidateEngine.generateOneStep`; only results whose existing B15
`accepted()` value is true are inserted and recursively expanded. The miner does not reproduce
I/S joins, weighted acceptance, or direct pattern-support scans.

Prefix-tree organization, recursive DFS, accepted-child expansion, I/S extension, Prime-block
support, and global-MeanW acceptance are **SOURCE-CONFIRMED**. Using the B14/B15 direct
`encoding(pattern) + encoding(singleton)` mapping remains **REASONABLE-INFERENCE**.

The following are **IMPLEMENTATION POLICY**:

- `SingletonEncodingBuilder` performs one preprocessing traversal and retains one full
  `PositionBlock` per SID for every F1 item. It validates the contiguous `1..N` SID coordinate
  system required by the current full PRISM representation.
- Support and encoding metadata are stored outside structural tree nodes in immutable maps keyed
  by `Pattern`. `WspmMiningResult` publishes deterministic structural pre-order and the completed
  `PrefixTree` is frozen before crossing the result boundary.
- Candidate order is inherited from B15 (I-Extensions by ascending item ID, then S-Extensions by
  ascending item ID). Recursion processes that list in order. This is deterministic but is not
  claimed as a paper-prescribed DFS order.
- The maximum total item-occurrence length of any input sequence is a data-derived structural
  stopping bound. A supported sequential pattern cannot be longer than its supporting sequence.
  The bound is relevant for an allowed zero threshold, where the inclusive comparison can mark
  zero-support candidates accepted; it prevents unbounded generation without imposing a
  user-selected pattern-length cutoff.
- An empty database with absolute minimum support follows B6: F1 and the result are empty and
  MeanW remains absent. Percentage support on an empty database continues to be rejected by B7;
  B16 does not invent a conversion.

B16 focused tests verify empty/no-F1 results, F1 initialization, I- and S-edges, multi-level DFS,
rejected-branch termination, repeated items in different itemsets, unique pattern identity,
support/encoding consistency, immutable result metadata, zero-threshold termination, deterministic
execution, and the known Table 1 pairs `<(a)>:7`, `<(b)>:7`, `<(c)>:6`, `<(ab)>:7`,
`<(a)(a)>:6`, `<(a)(b)>:7`, and `<(a)(c)>:6`. `<(ac)>:3` remains rejected. At B16 these focused
checks did not constitute B17 and did not yet resolve R4.

#### R6 — `minsup = 0` and zero-support result patterns

**Status: `OPEN; CURRENT BEHAVIOR CHARACTERIZED`.**

The configured domain currently permits both absolute `0` and percentage `0%`. On a non-empty
database both resolve to effective threshold `0`. The source-confirmed inclusive expression then
evaluates a zero-support candidate as:

```text
support(P) * MeanW >= effectiveMinSup
0          * MeanW >= 0
0                  >= 0    -> true
```

Consequently, B15 marks a structurally valid zero-support candidate accepted and B16 inserts it
into the result under the current literal-comparison policy. F1 remains different: it iterates only
items present in the database, so no absent singleton item is invented.

The available WSPM source does not explicitly discuss `minsup = 0`, whether a pattern absent from
every sequence should be reported, or how its unbounded theoretical candidate space should be
handled. Therefore, allowing a zero-support result pattern is **not SOURCE-CONFIRMED** as an
intended boundary behavior; it is the literal arithmetic consequence of the source-confirmed
comparison plus the accepted configuration domain. B16's data-derived maximum sequence-length
bound makes this policy finite. Tests now demonstrate that absolute `0` and percentage `0%`
include representative zero-support I/S candidates, while any positive threshold rejects the same
candidates.

Changing the policy to require `support(P) > 0`, or rejecting `minsup = 0`, would change accepted
output and must be a separate reviewed research decision. Until R6 is resolved, experiments should
use a strictly positive effective threshold and must not claim that zero-support output reflects an
explicit paper requirement.

### 9.7 B17 Table 1 end-to-end correctness and R4 resolution

**R4 status: `RESOLVED`.**

B17 extracted the complete Figure 2 oracle at paper page 367 and represents it under the B4
numeric-label test convention as follows:

| Paper pattern | Numeric test pattern | Support | Supporting SIDs |
|---|---|---:|---|
| `<(a)>` | `<(1)>` | 7 | `{1,2,3,4,5,6,7}` |
| `<(b)>` | `<(2)>` | 7 | `{1,2,3,4,5,6,7}` |
| `<(c)>` | `<(3)>` | 6 | `{1,2,3,4,5,6}` |
| `<(a)(a)>` | `<(1)(1)>` | 6 | `{1,2,4,5,6,7}` |
| `<(a)(b)>` | `<(1)(2)>` | 7 | `{1,2,3,4,5,6,7}` |
| `<(ab)>` | `<(1 2)>` | 7 | `{1,2,3,4,5,6,7}` |
| `<(a)(c)>` | `<(1)(3)>` | 6 | `{1,2,3,4,5,6}` |
| `<(b)(c)>` | `<(2)(3)>` | 6 | `{1,2,3,4,5,6}` |
| `<(ab)(c)>` | `<(1 2)(3)>` | 6 | `{1,2,3,4,5,6}` |

Three independent evidence paths agree exactly:

1. Figure 2 supplies the nine paper `pattern -> support` pairs and tree relationships.
2. The test-only ordered-subset matcher scans Table 1 structure directly without calling
   `PrimeBlockJoins`, `CandidateEngine`, `WspmPreTreeMiner`, or production support code. It
   reproduces every support and the SID sets above.
3. `WspmPreTreeMiner` produces exactly the same nine-entry map and the same supporting SID set for
   every pattern through Prime-block encodings. There are no missing patterns, unexpected patterns,
   support mismatches, or duplicates.

The known negative `<(ac)>` has independent structural support 3 at SIDs `{1,4,6}`. Its weighted
score is below effective minimum support `0.98`, so neither it nor a descendant rooted at that
rejected node appears in the production Prefix Tree. The tree contains the empty root plus nine
accepted nodes, exactly three F1 root children, and the Figure 2 parent/extension relationships.

The B17 oracle map and numeric label mapping exist only in test code. Production code contains no
Table 1 special case and required no correctness change. R1 remains `OPEN`; R2 remains
`RESOLVED FOR IMPLEMENTATION`; R3 and R5 remain `RESOLVED`; R6 remains `OPEN` and B17 does not
alter its zero-minimum-support behavior.

### 9.8 B18 test-only brute-force oracle

B18 adds `BruteForceWeightedPatternOracle` under `src/test/java` only. It is a project-built
correctness oracle, not an implementation described by WSPM or PRISM. For each database sequence,
it independently enumerates every choice that skips an itemset or retains one of that itemset's
non-empty subsets. The enumeration preserves temporal order, permits gaps, preserves repeated items
across different itemsets, and deduplicates the union by `Pattern` structural identity.

For every observed non-empty pattern, the oracle performs a direct greedy structural subsequence
scan over every `SequenceRecord`. A containing SID contributes exactly one support unit regardless
of the number of embeddings. The oracle independently counts item occurrences, divides by total
database length, derives `MaxW`, `MinW`, and `MeanW = (MaxW + MinW) / 2`, resolves percentage input
as `(percentage / 100.0) * |SD|`, and applies the inclusive
`support(P) * MeanW >= effectiveMinSup` comparison without rounding or epsilon.

The exhaustive algorithm is deliberately limited to tiny fixtures. Before enumeration, each
sequence is rejected if its item-occurrence length implies more than 1,000,000 non-empty structural
choices (`2^L - 1`). The union is also rejected before adding more than 1,000,000 unique patterns.
Both limits fail fast; the oracle never truncates and returns an incomplete map. Accepted entries
are sorted by stable `Pattern.serialize()` order and returned as an unmodifiable insertion-ordered
map.

An empty database returns an empty map without resolving a percentage threshold or fabricating
MeanW; this test-oracle early return does not change B7's production resolver contract. A non-empty
run requires a strictly positive effective threshold. Zero is rejected because enumeration of only
observed patterns cannot establish the complete zero-support pattern space while R6 remains open.

The expected-result path does not call `WspmPreTreeMiner`, `CandidateEngine`, `PatternExtensions`,
`PrimeBlockJoins`, `PatternEncoding`, `SingletonEncodingBuilder`, `PrefixTree`, production DFS,
production item-weight calculation, or production threshold acceptance. Reusing immutable domain
models and `MinimumSupport` input configuration does not share mining logic. B18 tests independently
cover itemset subsets, gapped and repeated subsequences, one-count-per-SID support, weighted boundary
acceptance, determinism, resource rejection, and exact nine-entry Table 1 equality. General
production-versus-oracle differential testing remains B19 and is not part of B18.

### 9.9 B19 differential correctness coverage

B19 compares two independent execution paths for every configured case:

```text
SequenceDatabase -> BruteForceWeightedPatternOracle -> expected Pattern/support map
SequenceDatabase -> WspmPreTreeMiner              -> actual Pattern/support map
```

The suite contains 14 fixed databases producing 18 fixed-threshold comparisons, plus 32 generated
databases run under four thresholds each, producing 128 random comparisons. In total, 146 complete
`Map<Pattern, Integer>` comparisons execute. Every comparison additionally checks that the result
encoding map has the same pattern set, that the accepted-pattern list has no duplicate, that global
MeanW and the effective threshold agree with the independent calculations, and that every accepted
pattern has exactly the same supporting SID list under direct structural matching and the production
`SequenceBlock`.

Fixed cases cover one and multiple sequences, sparse and dense occurrence structures, multi-item
itemsets, mixed I/S patterns, gaps, repeated items, multiple embeddings, partial SID support,
missing F1 items, rejected branches, an empty result under a positive threshold, inclusive threshold
equality, and the Table 1 nine-pattern regression. Prime-block boundaries include nine SIDs (two
sequence blocks), a valid endpoint at position 9 (two position blocks), and 17 itemset positions
(three position blocks). The 17-position database repeats one item so its 131,071 raw structural
choices remain within B18's published 1,000,000-choice cap.

Random generation uses 32 independently reproducible case seeds `20261016 + caseIndex`, where
`caseIndex` is `0..31`. Each database contains 1-5 sequences, a 1-4 item universe, 1-4 itemsets per
sequence, and 1-3 distinct canonical items per itemset. SIDs are contiguous from 1. Each case runs
with absolute thresholds `0.05`, `0.5`, and `10.0`, plus percentage threshold `25%`. The high
absolute threshold exercises empty-F1 results without invoking unresolved zero-threshold semantics.

Across fixed and random cases, the distinct configured thresholds are positive absolute values
`0.05`, `0.1`, `0.2`, `0.25`, `0.5`, `0.8`, `1.0`, `2.0`, and `10.0`, plus `14%` and `25%`.
Mismatch diagnostics contain case ID, seed, an SPMF-like database serialization, configured
minimum support, independent MeanW and effective threshold, result counts, missing patterns,
unexpected patterns, and per-pattern support differences. Oracle resource-limit failure is tested
as an explicit exception and is never converted into a passing comparison.

All 146 executed comparisons have exact map and supporting-SID equality: zero missing patterns,
zero unexpected patterns, and zero support mismatches. This finite evidence supports only the claim
that WSPM_PreTree and the independent oracle are equivalent on every executed B19 case; it is not a
general mathematical proof for all possible databases. Random generation, seed choice, case count,
threshold selection, diagnostics, and deterministic ordering are **IMPLEMENTATION POLICY**.
Percentage conversion remains **STRONGLY-SUPPORTED INFERENCE**, the direct WSPM-to-PRISM operand
mapping remains **REASONABLE-INFERENCE**, and R1/R6 remain open and unchanged.

### 9.10 B19.1 FIMI transaction-per-line adapter

B19.1 adds an independent `FimiTransactionParser` without changing `SpmfSequenceParser`. The FIMI
format's one-transaction-per-line representation and whitespace-separated item identifiers are
**SOURCE-CONFIRMED** format semantics. FIMI transactions contain no temporal ordering between their
items. The adapter therefore maps each non-blank transaction line to one `SequenceRecord` whose
`Sequence` contains exactly one `Itemset`:

```text
FIMI line:  1 15 18
Domain:     SID n -> <(1 15 18)>
```

This one-itemset sequence mapping is **IMPLEMENTATION POLICY**. It permits the existing baseline to
measure item co-occurrence through I-Extension while preventing the input adapter from inventing
S-Extension support such as `<(1)(15)>`. The adapter does not infer customer identifiers,
timestamps, transaction grouping, or purchase history. Such interpretations remain
**UNSPECIFIED** by transaction-per-line input and must not be introduced silently.

Non-blank lines receive consecutive one-based SIDs in input order. Items are retained under their
original non-negative Java `int` identifiers and are canonicalized by the existing `Itemset` model.
Duplicate items in one transaction are rejected rather than collapsed. Negative values (including
SPMF `-1`/`-2` delimiters), numeric overflow, and non-integer tokens fail with line/token diagnostics.
Blank lines are ignored; an empty or whitespace-only file produces an empty database. These
validation and empty-input choices are **IMPLEMENTATION POLICY**.

`SequenceDatabaseFormat` exposes the explicit choices `SPMF_SEQUENTIAL` and `FIMI_TRANSACTION`,
and the minimal `SequenceDatabaseParser` facade delegates to the selected independent parser. It
does not inspect content or file names to guess a format. Pumsb and T40I10D100K therefore require
the caller to select `FIMI_TRANSACTION`; no dataset-specific branch exists in the parser or miner.

The focused three-transaction fixture `1 2 3`, `1 2`, `1 3` confirms structural support 2 for
`<(1 2)>`, structural support 0 for `<(1)(2)>`, acceptance of the former at positive absolute
threshold `0.5`, rejection of the latter, and exact equality between the complete production and
B18 oracle maps. Existing SPMF/Table 1 tests continue to validate that sequential parsing semantics
are unchanged. R1-R6 remain unchanged.

### 9.11 B20 mining statistics and R7

`WspmMiningResult.statistics()` exposes one immutable `WspmMiningStatistics` snapshot for each
completed `WspmPreTreeMiner.mine(...)` call. Its fields are primitive values, and the mutable
accumulator is private to that single call. No counter is static, shared between runs, retained in
`PrefixTree`, or included in `Pattern` identity. B20 adds observation only: candidate generation,
acceptance, Prime-block joins, support, Prefix Tree insertion, and recursive DFS order are
unchanged.

The counters have these execution-based definitions:

| Metric | Definition |
|---|---|
| `sequenceCount` | Number of input `SequenceRecord` values in the mined database. |
| `f1Count` | Number of accepted one-patterns returned by `F1Builder` and attached below the root. |
| `candidateCount` | Number of actual `CandidateResult` values returned across all calls to `CandidateEngine.generateOneStep`. F1 and candidates prevented by canonical ordering are excluded. |
| `acceptedCandidateCount` | Returned post-F1 candidates whose existing `CandidateResult.accepted()` value is true. Each is inserted into the Prefix Tree. |
| `rejectedCandidateCount` | Returned post-F1 candidates whose existing `CandidateResult.accepted()` value is false. They are neither inserted nor recursively expanded. |
| `iCandidateCount` | Returned candidates classified as `I_EXTENSION`. |
| `sCandidateCount` | Returned candidates classified as `S_EXTENSION`. |
| `expandedNodeCount` | Non-root accepted nodes on which the recursive DFS expansion method is actually invoked. A terminal node stopped by the data-derived maximum-length guard is counted because DFS processes that node and executes the guard; the root and rejected candidates are not counted. |
| `patternCount` | Completed Prefix Tree node count excluding the root. |
| `elapsedNanos` | Monotonic elapsed duration observed with `System.nanoTime()`. |

The elapsed interval starts after public input null validation and immediately before in-memory
database statistics preprocessing. It ends after DFS completes and the Prefix Tree is frozen,
before constructing the immutable `WspmMiningResult` snapshot. It therefore includes preprocessing,
F1/singleton encoding construction, DFS mining, and tree freezing. It excludes dataset parsing/file
I/O, output/export I/O, and final result-snapshot construction. This is an observational duration,
not a scientific benchmark; tests require only that it is non-negative and never impose a runtime
limit.

Every immutable snapshot validates:

```text
candidateCount = iCandidateCount + sCandidateCount
candidateCount = acceptedCandidateCount + rejectedCandidateCount
patternCount   = f1Count + acceptedCandidateCount
```

`WspmMiningResult` additionally checks:

```text
sequenceCount = resolved minimum-support sequence count
f1Count       = root child count
patternCount  = accepted-pattern count = PrefixTree.nodeCount() - 1
```

All counts except `elapsedNanos` are deterministic for identical database/configuration input under
the existing deterministic baseline traversal. B20 tests cover empty input, a terminal singleton,
accepted and rejected candidates, mixed I/S generation, invariants, tree consistency, repeated and
independent mining runs, immutable primitive-only snapshots, Table 1, and the B19.1 FIMI fixture.
Table 1 at 14% produces 7 sequences, 3 F1 patterns, 34 generated candidates (7 I and 27 S), 6
accepted and 28 rejected post-F1 candidates, 9 DFS-expanded nodes, and 9 final patterns. Its complete
nine-entry `Pattern -> support` oracle remains unchanged.

Statistics describe only work visible at the current orchestration boundary. They do not count
canonical candidates that were never generated, internal arithmetic operations inside a join, JVM
memory, dataset/output I/O phases, or future BitPrune pruning categories. CMAP and Bitmap counters
are intentionally absent rather than reported as baseline zeros. Counter collection uses checked
integer addition so overflow fails visibly instead of wrapping.

#### R7 — transaction dataset to sequence semantics

**Status: `OPEN`.**

Pumsb and T40I10D100K are transaction datasets. B19.1 maps each transaction to one sequence
containing exactly one itemset. This prevents the parser from inventing temporal order and permits
only same-itemset co-occurrence through I-Extension. The specific experimental preprocessing used
by the WSPM_PreTree authors for these datasets has not been source-verified. Results produced under
the current mapping therefore must not be presented as a complete reproduction of the paper's
preprocessing. No new evidence in B20 closes this issue.

Current research-issue status after B20:

| Issue | Status |
|---|---|
| R1 — Sequence-weight discrepancy | OPEN |
| R2 — Percentage minimum support | RESOLVED FOR IMPLEMENTATION |
| R3 — Prime-block mechanics | RESOLVED |
| R4 — Full Table 1 oracle | RESOLVED BY B17 |
| R5 — Item-weight denominator | RESOLVED |
| R6 — Zero minimum support | OPEN |
| R7 — Transaction-to-sequence mapping | OPEN |

### 9.12 B20.1 standalone WSPM_PreTree runner

`vn.edu.wspm.experiments.MainWspmPreTree` is an **IMPLEMENTATION POLICY** entry point for running
the verified baseline directly from an IDE. It is not part of the WSPM paper algorithm. The class
keeps dataset path, explicit input format, minimum support, console flags, export flag, and output
path together as source constants. It neither reads interactive input nor selects another mining
algorithm. The default configuration uses the existing B17 fixture at
`src/test/resources/datasets/wspm/table1.spmf`, `SPMF_SEQUENTIAL`, relative `0.14`, console summary
and pattern printing enabled, and export disabled. The path is repository-relative and therefore
assumes the IDE working directory is the repository root; users running from another working
directory must edit the source constant.

The Main-facing relative convention is a fraction in `[0,1]`, whereas the B7 API accepts a
percentage in `[0,100]`. The adapter performs this explicit mapping:

```text
Main relative 0.14 -> MinimumSupport.percentage(14.0)
Main absolute 0.98 -> MinimumSupport.absolute(0.98)
```

The relative conversion uses a decimal point shift from `BigDecimal.valueOf(value)` rather than
binary `double * 100`. This prevents the source literal `0.14` from becoming the configured API
value `14.000000000000002`; it does not round the resolved B7 effective threshold or change B7
semantics. Relative values outside `[0,1]` are rejected. Absolute validation remains delegated to
the existing `MinimumSupport` API.

The execution flow is deliberately orchestration-only:

```text
hardcoded configuration
  -> validate input/output paths
  -> SequenceDatabaseParser with explicit SPMF or FIMI format
  -> WspmPreTreeMiner.mine
  -> WspmMiningResult
  -> optional console summary/pattern printing and UTF-8 export
```

Summary values are read directly from `WspmMiningStatistics` and the resolved minimum-support
metadata; Main does not reconstruct counters. Pattern output follows the immutable
`acceptedPatterns()` order and uses this stable line representation for both console and export:

```text
<serialized pattern> #SUP: <absolute support>
```

Export uses UTF-8 and `\n` line endings, creates missing parent directories, and truncates only the
explicit output path. An output path resolving to the input dataset is rejected before parsing or
writing. When export is disabled, the output path is not created or modified. A missing or
non-regular dataset fails with an explicit absolute-path diagnostic; the runner never downloads or
constructs replacement data.

B20.1 tests verify exact relative and absolute conversion, the complete B17 Table 1 support map,
the B19.1 FIMI one-itemset semantics, use of production statistics in the summary, one deterministic
line per accepted pattern, disabled/enabled export, input overwrite protection, and missing-input
behavior. The standalone default run produces the existing nine Table 1 patterns. R1 and R6 remain
`OPEN`; R7 remains `OPEN` because selecting `FIMI_TRANSACTION` does not establish the preprocessing
used by the paper authors for Pumsb or T40I10D100K.

## 10. Prefix Tree search contract

The baseline flow below is `SOURCE-CONFIRMED` at the algorithmic level:

1. Scan `SD`.
2. Calculate item statistics and item weights from the actual database.
3. Determine `MaxW`, `MinW`, and `MeanW`.
4. Build `F1` from single-item patterns satisfying the paper's acceptance condition.
5. Create the Prefix Tree with an empty root.
6. Add every accepted weighted sequential 1-pattern as a root child.
7. For each accepted node, generate legal I-Extensions and S-Extensions.
8. Traverse/extend the search tree depth-first.
9. Represent candidates and calculate support using Prime-block Encoding and its join operations from PRISM.
10. Insert a candidate as a child only when `support(candidate) * MeanW >= minsup`; recursively extend accepted children.
11. Return the Prefix Tree/result set of weighted sequential patterns.

The pseudocode imposes the I-Extension constraint:

```text
extensionItem > last item in the last itemset of P
```

S-Extension iterates eligible 1-patterns in `F1`. The source does not prescribe a stable output ordering or a concrete comparator for arbitrary Java item identifiers.

The baseline flow must not include:

- Bitmap representation;
- CMAP;
- Bitmap upper-bound pruning;
- Multi-tier Lazy Pruning;
- BitPrune-specific pruning;
- an ID-list or direct scan silently substituted for Prime-block support counting.

## 11. Prime-block dependency contract

### 11.1 What WSPM_PreTree confirms

The WSPM paper is `SOURCE-CONFIRMED` for these high-level facts:

- a pattern encoding has encoded sequence blocks and encoded position blocks;
- support can be derived from encoded sequence blocks;
- Prime-block Encoding represents candidates;
- Prime-block join operations calculate candidate support;
- I-Extension and S-Extension require their corresponding joins;
- construction and detailed joins are delegated to PRISM reference [7].

### 11.2 What must be established from PRISM before implementation

B12 must inspect and document, with examples and formulas:

- prime generator assignment and block capacity;
- mapping between bits/positions and prime factors;
- sequence-block representation;
- position-block representation;
- full versus compact encoding;
- construction of single-item encodings from SIDs and positions;
- factor-cardinality and support extraction;
- itemset-extension join;
- sequence-extension join, including the masking operator for temporal order;
- treatment of empty blocks and offsets;
- allowed sibling relationships used by joins;
- numeric growth, Java integer representation, and overflow behavior.

The PRISM paper available in the repository contains dedicated sections for primal block construction, support extraction, itemset joins, and sequence joins. B12 has now translated and validated those rules in the implementation-ready contract:

- [`prism-prime-block.md`](prism-prime-block.md)

That contract is authoritative for B13/B14 representation and join work. In particular, it records the exact blockwise GCD operations, the strict-after mask used by sequence joins, full/compact layout requirements, and numeric limits of the source's eight-prime rank representation.

### 11.3 Gate status

```text
WSPM PAPER ALONE: INSUFFICIENT
REQUIRED DEPENDENCY: PRISM SOURCE
PRISM PAPER IN REPOSITORY: PRESENT
B12 RESEARCH CONTRACT: COMPLETE
B13 AUTHORIZED: YES, AFTER USER APPROVAL
```

B12 found sufficient evidence in PRISM Theorems 3.1-3.3 and Sec. 2.2/3.2/3.4. The fallback stop condition below was therefore not triggered:

```text
PRISM SOURCE REQUIRED
```

and B13 must not implement an approximation.

### 11.4 B14 Prime-block join implementation status

B14 now provides an immutable full `PatternEncoding` and exact encoded I/S joins. The full
per-SID position layout retains empty blocks and validates `SID membership iff positions are
non-empty`. I-Join intersects aligned endpoint blocks. S-Join builds the PRISM strict-after mask
from the first endpoint over the complete logical position vector and intersects it with the right
endpoint blocks. Both operations rebuild the result `SequenceBlock` after positional verification,
and final support is always `result.sequenceBlock().support()`.

The PRISM mechanics are `SOURCE-CONFIRMED`. Applying those mechanics directly to
`encoding(pattern)` and `encoding(singleton extension item)` remains a `REASONABLE-INFERENCE` from
WSPM Figure 1 and PRISM endpoint semantics. B14 tests that mapping against an independent direct
structural oracle for I-Extension, S-Extension, repeated items, zero-support patterns, multiple
SIDs, cross-block positions, and the scoped Table 1 patterns. The oracle shares no production join,
mask, GCD, or support logic.

The Table 1 chained join for `<(a)(bc)>` yields exact supporting SIDs `{1,2,4}` and support 3.
This remains B14 join evidence separate from B17. Current issue status after B17: R1 remains open,
R2 remains resolved for implementation, R3 remains resolved, R4 is resolved by Section 9.7, R5
remains resolved, and R6 remains open.

## 12. Source classification table

| Topic | Classification | Evidence / Source | Implementation Consequence |
|---|---|---|---|
| Internal item identifier | REASONABLE-INFERENCE | SPMF datasets use numeric identifiers; the WSPM paper leaves the Java type unspecified | Use non-negative `int`; allow zero; reject negative delimiters as items. |
| Canonical item storage order | REASONABLE-INFERENCE | Deterministic storage is required; final mining comparator is not specified | Sort itemsets by ascending numeric ID, while deferring mining-order confirmation to B9/B10. |
| Sequence model | SOURCE-CONFIRMED | WSPM paper, Problem Definitions | Preserve ordered itemsets and repeated items across different itemsets. |
| Sequence size and length | SOURCE-CONFIRMED | WSPM paper, Problem Definitions | Store/test itemset count separately from total item count. |
| Subsequence | SOURCE-CONFIRMED | WSPM paper, Problem Definitions | Match itemsets by ordered embedding and subset inclusion. |
| Support | SOURCE-CONFIRMED | WSPM paper, Problem Definitions and Table 1 example | Count containing sequences, never total occurrences or embeddings. |
| Sequential pattern | SOURCE-CONFIRMED | WSPM paper, Problem Definitions | Ordinary condition is `support >= minsup`, subject to consistent units. |
| Item Weight | SOURCE-CONFIRMED | WSPM Eq. (1), worked `W(a)` example, Table 2 | Compute occurrence count divided by total database length. |
| Itemset Weight equation | SOURCE-CONFIRMED | WSPM Eq. (2) | Equation states the arithmetic mean of member item weights. |
| Sequence Weight equation | SOURCE-CONFIRMED | WSPM Eq. (3) | Equation states the arithmetic mean of itemset weights. |
| Itemset/sequence weight worked example | UNSPECIFIED | Reported `0.559` conflicts with Eq. (2)–(3), which produce approximately `0.229` | B6 must inspect source [20] or obtain clarification before implementing these two formulas. |
| MaxW / MinW | SOURCE-CONFIRMED | WSPM weight definitions | Compute extrema across distinct-item weights. |
| MeanW | SOURCE-CONFIRMED | WSPM weight definitions and Table 1 discussion | Compute `(MaxW + MinW) / 2`; do not average all item weights. |
| Weighted acceptance | SOURCE-CONFIRMED | WSPM definition and Figure 1 pseudocode | Use `support(P) * MeanW >= minsup`; do not substitute another score. |
| F1 | SOURCE-CONFIRMED | Figure 1 and Table 1 example | Compute accepted 1-patterns; Table 1 must yield `a:7, b:7, c:6`. |
| Prefix Tree | SOURCE-CONFIRMED | WSPM prefix-tree definition and Figure 1 | Empty root; children preserve prefix; level tracks pattern length. |
| Formal prefix edge cases | UNSPECIFIED | WSPM prefix wording/example are not sufficient for all partial-last-itemset cases | Resolve before exposing a general prefix predicate. |
| I-Extension | SOURCE-CONFIRMED | WSPM definitions and Figure 1 | Add to last itemset with lexicographic restriction. |
| S-Extension | SOURCE-CONFIRMED | WSPM definitions and Figure 1 | Append a new singleton itemset. |
| DFS | SOURCE-CONFIRMED | WSPM algorithm description and recursive pseudocode | Extend accepted children recursively; preserve complete search behavior. |
| Prime-block representation | SOURCE-CONFIRMED | PRISM Sec. 2.2 and 3.2; B12 contract | Ordered fixed-width prime-factor blocks encode SID and per-SID itemset-position vectors. |
| Prime-block support extraction | SOURCE-CONFIRMED | PRISM Theorem 3.1; B12 contract | Sum factor-cardinalities of result sequence blocks. |
| Prime-block joins | SOURCE-CONFIRMED | PRISM Theorems 3.2-3.3; B12 contract | I-join uses position GCD; S-join uses strict-after mask then position GCD. |
| `minsup` percentage conversion | STRONGLY-SUPPORTED INFERENCE | WSPM labels thresholds as percentages but omits conversion; `(p/100) * \|SD\|` preserves units and reproduces F1 | Retain the real threshold without integer rounding. |
| Table 1 effective threshold `0.98` | STRONGLY-SUPPORTED INFERENCE | `14% * 7` combined with exact `MeanW = 12/66` accepts supports 7 and 6 but rejects 5 | Mandatory B7 boundary oracle. |
| Numeric representation for weights | REASONABLE-INFERENCE | B6 engineering policy; paper provides real formulas and approximate displayed decimals | Use `double` without early rounding and test with absolute tolerance `1e-12`. |
| Numeric representation for Prime-block | SOURCE-CONFIRMED domain; Java storage UNRESOLVED | PRISM Sec. 3.4 and author source use eight primes and ranks `0..255` | B13 must choose a checked Java representation; do not enlarge block width silently. |
| Candidate ordering | UNSPECIFIED | Lexicographic restriction is stated; comparator/domain order is not | B9/B10 must define a deterministic order compatible with the source. |
| Output ordering | UNSPECIFIED | Paper shows a tree but specifies no serialization/order contract | Use deterministic ordering for reproducibility and differential tests. |
| Exact nine pattern/support pairs | SOURCE-CONFIRMED; B17 VERIFIED | Full set and tree are graphical in Figure 2; B17 direct structural oracle and production output agree exactly | Retain the nine-pair test oracle outside production and compare the complete map, not merely the count. |

## 13. Open Research Questions

### 13.1 Is the WSPM paper sufficient to reproduce Prime-block Encoding?

No. `SOURCE-CONFIRMED`: it explicitly delegates detailed construction and join operations to PRISM [7]. WSPM alone is insufficient for B13/B14.

### 13.2 Is the original PRISM paper required before B13?

Yes. B12 inspected the copy in `docs/` and completed the reviewed representation/join contract in [`prism-prime-block.md`](prism-prime-block.md). B13/B14 no longer need to infer the PRISM formulas.

### 13.3 How is `minsup = 14%` represented internally?

`RESOLVED FOR IMPLEMENTATION AS STRONGLY SUPPORTED INFERENCE`. Percentage input `p` is represented relatively as `p / 100.0`, then resolved to the real-valued effective threshold `(p / 100.0) * |SD|`. Absolute input is already in the effective weighted-comparison unit and is not scaled. Neither mode applies integer rounding. The source-confirmed comparison remains `support(P) * MeanW >= effectiveMinSup`.

### 13.4 Which numeric type should Prime-block use?

`RESOLVED AT SOURCE LEVEL; JAVA STORAGE DECISION DEFERRED TO B13`. The reported PRISM implementation fixes eight primes, whose maximum product is `9,699,690`, and represents the 256 factor subsets by ranks `0..255`. The author C++ source uses `unsigned char` ranks and `int` product intermediates. Java has no unsigned `byte`, so B13 must explicitly select a checked representation such as `int 0..255` or unsigned conversion. Enlarging the generator or silently switching representations is not authorized; any Java overflow must remain observable and receive a reproducing test.

### 13.5 Where is lexicographic ordering applied?

`SOURCE-CONFIRMED` for I-Extension: the new item is greater than the last item in the last itemset. `RESOLVED FOR IMPLEMENTATION` for the concrete Java comparator as ascending numeric internal item ID, classified as `IMPLEMENTATION POLICY / REASONABLE-INFERENCE`. Traversal order and exported result ordering remain `UNSPECIFIED` for later tasks.

### 13.6 Does the paper provide all nine exact pattern/support pairs?

`RESOLVED BY B17`. The paper states the count `9`, explicitly lists F1 and four children of
`<(a)>`, and depicts the complete tree in Figure 2. B17 extracted all nine graphical pairs and
verified every one against an independent ordered-subset matcher and the production miner. Exact
map equality, supporting SID equality, Prefix Tree relationships, and deterministic repetition all
pass. The complete evidence is recorded in Section 9.7 and the B17 integration test.

### 13.7 Are any important details only visible in figures or tables?

Yes:

- Figure 1 contains the operational pseudocode and extension loops.
- Figure 2 contains the complete example Prefix Tree graphically.
- Table 1 supplies exact itemset boundaries for the oracle database.
- Table 2 supplies exact fractions and displayed weight approximations.
- PRISM figures illustrate block layout, offsets, full/compact encodings, and join examples.

These visual sources must be reviewed during the relevant tasks; OCR text alone is not sufficient for implementation-critical symbols.

### 13.8 Is the paper's general prefix definition sufficient for a Java predicate?

`UNSPECIFIED`. The operational extension semantics are clear, but the typeset formal prefix condition and its example need reconciliation before implementing a reusable predicate.

### 13.9 Should displayed decimal weights be used in acceptance checks?

No implementation decision is authorized yet. Table 2 displays approximate decimals, while the fractions are exact. B6 must select a numerical policy that avoids early rounding and documents test tolerance.

### 13.10 Which itemset/sequence weight interpretation is authoritative?

`UNSPECIFIED`. Eq. (2)–(3) imply approximately `0.229` for the paper's `<(abd)(ac)>` example, while the prose reports `0.559`. B6 must inspect the cited weighted-mining source [20] and/or obtain author clarification. No implementation may silently change Eq. (2), Eq. (3), or the example to make them agree.

## 14. Implementation Contract for Phase B

All later Phase B tasks must comply with the following rules:

1. Preserve itemset boundaries.
2. Preserve temporal order between itemsets.
3. Keep `<(a)(b)>` distinct from `<(ab)>`.
4. Allow an item to recur across different itemsets while preventing duplicate membership within one itemset.
5. Count support per containing sequence, not per occurrence or embedding.
6. Compute occurrence counts, sequence lengths, and item weights from the actual dataset.
7. Compute `MaxW` and `MinW` from distinct-item weights.
8. Compute `MeanW = (MaxW + MinW) / 2`.
9. Do not implement itemset/sequence weight semantics until the Eq. (2)–(3) versus `0.559` conflict is resolved and documented.
10. Preserve the paper's `support(P) * MeanW >= minsup` condition.
11. Keep configured `minsup`, percentage/relative representation, absolute support, and effective internal threshold distinct and auditable.
12. Do not round weights or thresholds early in a way that changes acceptance.
13. Preserve I-Extension and S-Extension semantics and their itemset effects.
14. Apply a documented deterministic lexicographic rule to prevent duplicate I-Extensions.
15. Preserve Prefix Tree parent-child relationships and an empty root.
16. Preserve depth-first search behavior required by WSPM_PreTree.
17. Use actual PRISM Prime-block Encoding and join operations for the baseline after B12 passes.
18. Do not use Bitmap in the baseline.
19. Do not use CMAP in the baseline.
20. Do not use Multi-tier Lazy Pruning or another BitPrune pruning rule in the baseline.
21. Do not silently replace Prime-block Encoding with an ID-list, direct database scan, or approximation.
22. Reproduce Table 1 item statistics, Table 2 weights, MeanW, and F1 through computation.
23. Reproduce and compare every available final `pattern -> support` pair; matching only the count `9` is insufficient.
24. A brute-force implementation may be used only as an independent oracle on tiny datasets, never as the baseline implementation.
25. Keep output deterministic for exact differential comparison.
26. Document every implementation inference, numeric policy, source ambiguity, and deviation affecting reproducibility.

## 15. Phase gates established by this contract

- B2–B11 may proceed within their individual scopes because their domain/search semantics are sufficiently specified for isolated implementation and testing.
- B7 must resolve `minsup` units and conversion before those semantics are used as trusted mining behavior.
- B12 is a mandatory research gate for Prime-block Encoding.
- B13 and B14 must not begin until B12 demonstrates sufficient PRISM fidelity.
- B17 must not pass on pattern count alone.
- Performance benchmarking must not begin until baseline correctness is established.

## 16. B1 completion statement

This document records the current source-confirmed contract and exposes unresolved points rather than filling them with plausible implementation choices. It is a specification input for later tasks, not evidence that WSPM_PreTree has been implemented or validated.
