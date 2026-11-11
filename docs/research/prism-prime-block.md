# PRISM / Prime-block Research Contract

## 1. Scope and gate decision

This document is the B12 research-only contract for the Prime-block mechanics used by the
WSPM_PreTree baseline. It specifies representation, support extraction, and joins; it does not
add production code or authorize a Bitmap/ID-list substitute.

The research gate is **PASS** and research question R3 is **RESOLVED**. The original PRISM
journal paper gives exact definitions and theorems for all five mandatory primitives: sequence
blocks, position blocks, support extraction, itemset joins, and sequence joins. B13 and B14 may
therefore be implemented from this contract, subject to the implementation decisions explicitly
classified below.

## 2. Sources

### 2.1 WSPM_PreTree baseline

- Thi-Thiet Pham, Thuy-Duong Vu, Tai-Du Nguyen, Bao Huynh, Trang Van,
  *Mining Weighted Sequential Patterns Based on Prefix-Tree and Prism Encoding*,
  *Vietnam Journal of Computer Science* 10(3), 357-372, 2023.
- DOI: <https://doi.org/10.1142/S219688882350001X>
- Repository copy:
  `docs/Mining_weighted_sequential_patterns_based_on_prefix_tree_and_prism_encoding_2023.pdf`
- Relevant material: the Prime-block support definition and example around journal pages
  363-364; WSPM_PreTree algorithm and extension procedures around pages 365-366.
- Dependency statement: candidate representation and support calculation use PRISM [7], and
  construction of sequence identifiers is delegated to [7]. WSPM [7] is the PRISM paper below.

### 2.2 Original PRISM source

- Karam Gouda, Mosab Hassaan, Mohammed J. Zaki,
  *PRISM: An Effective Approach for Frequent Sequence Mining via Prime-block Encoding*,
  *Journal of Computer and System Sciences* 76(1), 88-102, 2010.
- DOI: <https://doi.org/10.1016/j.jcss.2009.05.008>
- Repository copy:
  `docs/Prism_An effective approach for frequent sequence mining prime-block encoding.pdf`
- It is reference [7] in the WSPM paper, confirmed by title and authors rather than citation
  number alone.
- A shorter 2007 ICDM paper exists, but the 2010 JCSS journal article cited by WSPM is the
  authoritative version for this contract.
- Relevant material: Sec. 2.1 (prime factors/generators), Sec. 2.2 (primal block encoding),
  Sec. 3.2 (sequence and position encoding), Theorems 3.1-3.3 (support and joins), and Sec. 3.4
  (ranked lookup tables).
- Availability: **AVAILABLE AND SUFFICIENT** for the mathematical representation and joins.

The journal article uses the spelling **primal block encoding** in its definitions. WSPM uses
**prime-block encoding**. This contract treats them as terminology for the same referenced
mechanism; no source establishes two different representations.

One typesetting inconsistency is preserved explicitly: immediately after Theorem 3.2, the journal
example prints `gcd_XY={30,1}` even though `gcd(2,2)=2`; the preceding calculation, decoded SID
vector `11101000`, and final `S_XY={30,2}` all require `{30,2}`. This contract uses the arithmetic
result `{30,2}` and classifies the isolated `{30,1}` as a source typo, not an alternative rule.

### 2.3 Author implementation (corroborating source)

- Author repository: <https://github.com/zakimjz/PRISM>
- The repository README identifies both the 2010 journal article and the 2007 conference paper.
- `sequence.cc` declares eight primes, 256-entry support/rank structures, and a `256 x 256`
  `unsigned char` GCD matrix. It corroborates the journal paper's rank-based implementation.
- The code is supplementary evidence for implementation representation, not a replacement for
  Theorems 3.1-3.3.

## 3. Symbol table and terminology

| Symbol | PRISM meaning | Java-friendly term | Source |
|---|---|---|---|
| `G = {p1,...,pg}` | Ordered base set of distinct primes | generator set | PRISM Sec. 2.1-2.2 |
| `P(G)` | Power set of `G` | all factor subsets | PRISM Sec. 2.1 |
| `x in product(P(G))` | Square-free product of a subset of `G` | encoded block value | PRISM Sec. 2.1 |
| `||x||G` | Number of factors from `G` in `x` | factor-cardinality | PRISM Sec. 2.1 |
| `B` | A binary vector split into `g`-bit blocks | logical bit vector | PRISM Sec. 2.2 |
| `Bi` | The `i`th consecutive `g`-bit block of `B` | logical block | PRISM Sec. 2.2 |
| `nu(Bi,G)` | Product of `pj` for every set bit `Bi[j]` | encoded block | PRISM Sec. 2.2 |
| `nu(B,G)` | Ordered list of encodings of all blocks | block encoding | PRISM Sec. 2.2 |
| `nu^-1` | Inverse from factors to the corresponding bits | decoded vector | PRISM Sec. 2.2 |
| `E(X)=(S_X,P_X)` | Encoding of pattern `X` | pattern encoding | PRISM Sec. 3.2 |
| `S_X` | Encoded blocks over database sequence identifiers | sequence blocks | PRISM Sec. 3.2 |
| `P_X` | Encoded occurrence-ending positions, grouped by SID | position blocks | PRISM Sec. 3.2 |
| `P_X^i` | Position-block vector for pattern `X` in SID `i` | positions for one SID | PRISM Sec. 3.2 |
| `f_A` | First set-bit position in vector `A` | first occurrence end | PRISM Sec. 2.2 |
| `(A)^>` | Zeros through `f_A`, then ones after `f_A` | strict-after mask | PRISM Sec. 2.2 |
| `gcd` | Blockwise GCD, equivalent to bit intersection | block intersection | PRISM Sec. 2.2 |
| `rank(x)` | Decimal value of factor indicator, leftmost bit least significant | block rank | PRISM Sec. 3.4 |

The paper's mask glyph is typeset as a triangular/arrow-like superscript. This document writes
it as `^>` to emphasize the strict `position > f_A` rule.

## 4. Prime assignment

Prime assignment is to **bit offsets inside a fixed-width block**, not to items, whole sequences,
item occurrences, or global positions directly.

For ordered `G = {p1,...,pg}`, bit `j` of every block maps to `pj`. A 1-bit contributes that
prime once; a 0-bit contributes multiplicative identity 1. The same `G` is reused in every block.
Therefore:

- SID `i` maps to block `floor((i-1)/g)` and offset `(i-1) mod g` in a sequence vector;
- itemset position `t` within one sequence maps analogously in its position vector;
- item identity does not select a prime; an item selects which occurrence vector is encoded;
- every prime is distinct and every encoded block is square-free;
- the source orders `G` increasingly and numbers SIDs/positions from 1.

Example from PRISM: with `G={2,3,5,7}`, block `1001` encodes `2*7=14`, while `0000`
encodes `1`. Vector `100111100100` encodes as `{14,30,3}`.

Classification: **SOURCE-CONFIRMED** (PRISM Sec. 2.1-2.2).

## 5. Sequence blocks

For a pattern `X`, construct a global SID bit vector whose bit `i` is 1 exactly when database
sequence `i` contains `X`. Pad its right end with zeros until its length is a multiple of `g`,
split it into consecutive `g`-bit blocks, and encode each block with `nu`. The ordered result is
`S_X`.

Each prime factor in a sequence block therefore denotes one supporting SID at that block offset.
A pattern exists in SID `i` iff that SID's bit in `nu^-1(S_X)` is set. Sequence blocks are global
for the pattern, not one value per occurrence.

In the PRISM example, `a` occurs in SIDs 1, 2, 3, and 5. With `g=4`, the padded SID vector is
`11101000`, so `S_a={30,2}`.

Classification: **SOURCE-CONFIRMED** (PRISM Sec. 3.2).

## 6. Position blocks

For each SID `i`, `P_X^i` encodes the itemset indexes at which an occurrence/embedding of `X`
ends. For a singleton item these are exactly the itemset indexes containing that item. For a
derived sequential pattern, PRISM explicitly retains positions of the pattern's last item.

Positions are **itemset indexes**, not offsets among flattened items. They are 1-based in the
paper. Multiple occurrences set multiple bits in the same per-SID vector. Each per-SID vector is
right-padded to a multiple of `g`, split in order, and encoded blockwise.

The two extension relations are represented as follows:

- same itemset: elementwise `gcd(P_X^i,P_Y^i)` retains only equal set-bit positions;
- later itemset: `gcd((P_X^i)^>,P_Y^i)` retains positions of `Y` strictly greater than the first
  ending position of `X`.

Full encoding retains empty position blocks. Compact encoding removes redundant empty blocks and
stores an index/offset connecting each valid SID to its position-block segment. A canonical B13
model must preserve enough block-index information to reconstruct the full vector; otherwise an
interior empty block could not be aligned during a join. Using the full encoding first is faithful
to the source and is the safest reference representation.

Classification: the position meaning and full/compact distinction are **SOURCE-CONFIRMED**;
requiring explicit absolute indexes in a custom compact Java layout is **REASONABLE-INFERENCE**.

## 7. Empty blocks, invalid matches, and padding

- Logical all-zero block `000...0` encodes as product value `1`.
- In rank representation, that same block has rank `0`.
- A per-SID join is a no-match when all its result position blocks are empty (paper notation
  `P_result^i = 1`). That SID's result sequence bit must be 0.
- A candidate has support zero and is globally absent when no SID has a non-empty result.
- Padding bits can never become occurrences; source vectors are right-padded with zeros.
- Blockwise operands must refer to the same SID range or the same position range. Missing trailing
  blocks are logically empty. Misaligned metadata is an invalid implementation state, not a
  legitimate no-match.
- Itemset join requires two different PRISM siblings. Sequence join permits the same sibling on
  both sides. WSPM's separate lexicographic rule additionally prevents duplicate I-Extensions.

## 8. Single-item encoding and support extraction

Consider PRISM's source database fragment for item `a`. In SID 1, `a` occurs at itemset positions
1, 4, and 6. With `G={2,3,5,7}`, the occurrence vector `100101` is padded to `10010100`, giving
`P_a^1={14,3}`. Across the database, `a` occurs in SIDs 1, 2, 3, and 5, so:

```text
SID bits: 11101000
S_a:      {30, 2}
support:  ||30||G + ||2||G = 3 + 1 = 4
```

In general, PRISM Theorem 3.1 states:

```text
support(X) = sum(||v||G for v in S_X)
```

This is the number of supporting database sequences. Several matching positions in one SID only
set that SID bit once, so they contribute exactly 1, not their occurrence count. The magnitude of
an encoded integer is irrelevant; only its distinct factors matter. In rank form, `||v||G` is the
population count of the rank or the `CARD[rank(v)]` lookup.

Classification: **SOURCE-CONFIRMED** (PRISM Theorem 3.1 and WSPM support example).

## 9. I-Extension / itemset join

### 9.1 Exact PRISM theorem

Let `X` and `Y` be different siblings with common prefix `P`, with encodings
`E(X)=(S_X,P_X)` and `E(Y)=(S_Y,P_Y)`.

1. Compute `gcd_XY = gcd(S_X,S_Y)` blockwise. Its set SID bits are only a necessary candidate
   set: both operands occur somewhere in those sequences.
2. For every candidate SID `i`, compute the blockwise aligned vector
   `P_XY^i = gcd(P_X^i,P_Y^i)`.
3. Remove empty output position blocks in compact form, but preserve alignment metadata.
4. Set result SID bit `B_XY[i]=1` iff `P_XY^i` has at least one non-empty block.
5. Encode `S_XY=nu(B_XY)` and return `E(XY)=(S_XY,P_XY)`.
6. Calculate support from `S_XY` using Theorem 3.1.

The positional test is equality of itemset index: both operand endings must occur in the same
itemset. There is no divisibility test beyond GCD/factor intersection.

### 9.2 WSPM direct-extension form

For WSPM candidate `Q = IExtend(P,x)`, the operationally equivalent position calculation is
`gcd(P_P^i,P_x^i)`: endpoints of `P` that share an itemset with singleton `x` become endpoints
of `Q`. WSPM's pseudocode asks to add a singleton from `F1` to the last itemset but does not spell
out these operands. Using `E(P)` and `E(x)` is therefore a **REASONABLE-INFERENCE** from PRISM's
position semantics, not the literal sibling precondition of Theorem 3.2. B14 must test it against
direct subsequence support and the sibling-join form.

Theorem 3.2 itself is **SOURCE-CONFIRMED**.

## 10. S-Extension / sequence join

### 10.1 Strict-after mask

For the complete per-SID logical vector `A`, let `f_A` be its first set bit. Define:

```text
(A)^>[j] = 0  when j <= f_A
(A)^>[j] = 1  when j >  f_A
```

The mask applies to the entire per-SID vector, across block boundaries. It is not independently
restarted in every block. If `A=001001100100`, then `(A)^>=000111111111` and, for `g=4`,
`nu((A)^>)={7,210,210}`.

### 10.2 Exact PRISM theorem

Let `X` and `Y` be possibly identical siblings with common prefix `P`.

1. Compute candidate SIDs `gcd_X->Y = gcd(S_X,S_Y)` blockwise.
2. For every candidate SID `i`, find the first set bit of the complete `P_X^i` vector and form
   `(P_X^i)^>`.
3. Compute `P_X->Y^i = gcd((P_X^i)^>,P_Y^i)` blockwise.
4. Set `B_X->Y[i]=1` iff the output contains at least one non-empty position block.
5. Encode `S_X->Y=nu(B_X->Y)` and return `E(X->Y)=(S_X->Y,P_X->Y)`.
6. Calculate support from `S_X->Y`.

Why the first occurrence is sufficient: if a `Y` endpoint is after any endpoint of `X`, it is
necessarily after the earliest endpoint. The output retains all `Y` endpoints after that earliest
one, and these become the last-item positions for future joins. Same-position bits are cleared by
the `j <= f_A` branch; only a strictly later itemset matches.

For WSPM `Q = SExtend(P,x)`, use `X=P` and singleton `Y=x` under the same documented adaptation
classification as the direct I-Extension form.

Theorem 3.3 and the mask are **SOURCE-CONFIRMED**; the WSPM operand mapping is
**REASONABLE-INFERENCE**.

## 11. I-join versus S-join

| Aspect | I-join | S-join |
|---|---|---|
| Structural meaning | Add item to final itemset | Append a new singleton itemset |
| PRISM operands | Different siblings under one prefix | Possibly identical siblings under one prefix |
| SID prefilter | `gcd(S_X,S_Y)` | `gcd(S_X,S_Y)` |
| Position operation | `gcd(P_X^i,P_Y^i)` | `gcd((P_X^i)^>,P_Y^i)` |
| Required relation | Same itemset index | Right endpoint strictly after earliest left endpoint |
| Result positions | Common endpoint positions | Valid right/Y endpoint positions |
| SID accepted when | Any result block is non-empty | Any result block is non-empty |
| Support | Factor-cardinality sum of result sequence blocks | Same |
| Failure | No common endpoint in SID | No right endpoint after a left endpoint in SID |

## 12. Worked examples

### 12.1 PRISM source example

With `G={2,3,5,7}`, PRISM gives for SID 1:

```text
P_a^1 = {14,3}   -> bits 1001 0100 -> a at positions 1,4,6
P_b^1 = {210,2}  -> bits 1111 1000 -> b at positions 1,2,3,4,5
```

Itemset extension `<(ab)>`:

```text
gcd({14,3},{210,2}) = {14,1}
bits                         1001 0000
matching final positions     1,4
```

Sequence extension `<(a)(b)>`:

```text
(1001 0100)^>               = 0111 1111
nu(mask)                    = {105,210}
gcd({105,210},{210,2})      = {105,2}
bits                         0111 1000
matching final positions     2,3,4,5
```

Across all five source sequences, the itemset join retains SIDs 1,2,3,5 and gives sequence blocks
`{30,2}`, hence support 4. The sequence join drops SID 3 and gives `{6,2}`, hence support 3.

### 12.2 Table 1 manual cross-check

For WSPM Table 1, both `a` and `b` occur together in an itemset in every SID, so
`support(<(ab)>)=7`. Every SID also has a `b` in an itemset strictly after an itemset containing
`a`, so `support(<(a)(b)>)=7`. These checks demonstrate that the two joins can have equal support
while retaining different endpoint positions and different pattern identity.

For the paper's oracle `<(a)(bc)>`, the I-join for `bc` requires co-occurrence of `b` and `c`, and
the S-join requires that `bc` itemset to be after an `a` endpoint. The valid SIDs are exactly:

```text
SID 1: a at 1, bc at 2
SID 2: a at 1, bc at 3
SID 4: a at 3, bc at 4
```

Thus `support(<(a)(bc)>)=3`, matching the WSPM paper oracle. This is a manual derivation, not an
end-to-end miner verification.

## 13. PRISM mechanics versus WSPM additions

| Component | PRISM original | WSPM_PreTree |
|---|---|---|
| Vertical representation | `E(X)=(S_X,P_X)` primal blocks | Reuses Prime-block representation for candidates |
| Sequence block | SID containment bits encoded in prime blocks | Reuses it for candidate support |
| Position block | Per-SID last-item/end positions | Reuses join mechanics; details delegated to PRISM |
| I-join | Theorem 3.2 over different siblings | Adds eligible `F1` item to last itemset; invokes PRISM encoding |
| S-join | Theorem 3.3 over possibly same siblings | Appends eligible `F1` singleton; invokes PRISM encoding |
| Support | Count supporting SIDs via factor cardinalities | Same support, then applies weighted acceptance |
| Search structure | Sequence search/equivalence classes, DFS option | Explicit Prefix Tree with parent-child edges and DFS |
| Weight model | Not present | Item weights, `MaxW`, `MinW`, `MeanW` |
| Acceptance | `support >= minsup` frequent-pattern test | `support(P) * MeanW >= minsup` |

The representation and positional mechanics are PRISM. Prefix-tree storage and weighted filtering
are WSPM additions. R1 (`0.229` versus `0.559` sequence-weight example) remains open and is
unrelated to Prime-block mechanics.

## 14. Numeric and overflow considerations

### 14.1 Source-confirmed facts

- The mathematical definition uses products of distinct primes.
- PRISM's reported implementation fixes `G={2,3,5,7,11,13,17,19}` (`g=8`). The largest product
  is `9,699,690`, and there are 256 possible factor subsets.
- Rather than storing/processing arbitrary products for joins, the paper assigns each product a
  rank `0..255`. The leftmost bit is least significant, and ranked GCD is bit intersection.
- `GCD` is a `256 x 256` table, `CARD` stores factor-cardinality, and `MASK` stores mask results.
- The author C++ source uses `int` for prime-product construction/intermediates and
  `unsigned char` for ranks/tables.

Therefore the exact eight-prime configuration does not overflow a signed 32-bit `int` during
block-product construction. The paper does not specify behavior for a larger generator set and
does not use arbitrary-precision integers.

### 14.2 Java engineering analysis

- Java `byte` is signed (`-128..127`), unlike C++ `unsigned char`. Store a rank as `int 0..255`
  or explicitly convert with `Byte.toUnsignedInt`; silent signed indexing is invalid.
- If actual products are constructed, `Math.multiplyExact` should expose overflow. The product of
  the first 9 primes is `223,092,870` (fits `int`), while the first 10 is `6,469,693,230` (does
  not). The first 15 primes fit `long`; the first 16 do not.
- A fixed eight-prime ranked representation is the closest match to the reported PRISM
  implementation and avoids product growth in stored blocks. Calling rank operations “Bitmap
  WSPM_BitPrune” would be historically and algorithmically incorrect: ranks are explicitly part
  of PRISM's Prime-block optimization.
- B13 must document whether product values are retained for transparent reference tests or ranks
  are the canonical storage. It must not enlarge `g` silently.

## 15. Logical implementation contract for B13

B13 must implement primitives only, with the following information and invariants:

```text
Generator
  ordered distinct primes, default source configuration exactly 8 primes
  block width g
  product/rank conversion
  factor cardinality
  GCD/intersection and strict-after-mask lookup behavior

EncodedVector
  logical bit length before padding
  ordered fixed-width blocks
  canonical empty block (product 1 / rank 0)
  inverse/inspection operation for source-example tests

PatternEncoding E(X)
  ordered global sequence blocks S_X
  for each supporting SID, ordered position blocks P_X^i
  SID-to-position-segment metadata
  enough length/index metadata to reconstruct aligned full vectors
```

Required invariants:

1. The same ordered generator applies to every block in an encoding.
2. Logical lengths exclude padding; padding is always zero.
3. Position blocks use itemset indexes and remain scoped to one SID.
4. A SID bit is set iff its position vector contains a non-empty block.
5. Full vectors are canonical. Any compact form must round-trip to the same full vector.
6. Support from sequence blocks equals the number of represented supporting SIDs.
7. Rank values are validated in `0..255` for `g=8`; signed Java bytes cannot leak into indexes.
8. B13 tests reproduce `{14,30,3}`, empty code `1`, ranks, factor-cardinality, and the source
   single-item example.

Whether Java stores product values, ranks, or both is an explicit B13 engineering decision. The
source behavior is fixed; the storage API is not.

## 16. Join contract for B14

```text
iJoin(left, right)
  preconditions:
    same generator, SID universe, and compatible per-SID position lengths
    literal PRISM enumeration: different siblings sharing one prefix
  algorithm:
    candidateSids = gcd(left.sequenceBlocks, right.sequenceBlocks)
    for each set candidate SID i:
      positions[i] = gcd(left.positions[i], right.positions[i])
      keep SID i iff positions[i] contains a non-empty block
    rebuild result sequence blocks from kept SIDs
  result:
    positions are same-itemset endpoints; support comes only from result sequence blocks
```

```text
sJoin(left, right)
  preconditions:
    same generator, SID universe, and compatible per-SID position coordinate system
    literal PRISM enumeration: possibly identical siblings sharing one prefix
  algorithm:
    candidateSids = gcd(left.sequenceBlocks, right.sequenceBlocks)
    for each set candidate SID i:
      find first set bit over the complete left position vector
      strictAfter = zeros through that bit, ones at every later valid itemset index
      positions[i] = gcd(strictAfter, right.positions[i])
      keep SID i iff positions[i] contains a non-empty block
    rebuild result sequence blocks from kept SIDs
  result:
    positions are right endpoints strictly later than a left endpoint;
    support comes only from result sequence blocks
```

B14 must test both literal PRISM sibling examples and WSPM's direct `pattern + singleton`
adaptation. An initial sequence-block intersection is only a necessary prefilter; it must never be
reported as final support before position verification.

### 16.1 B14 Java representation and validation decisions

B14 implements the source contract with these explicitly classified decisions:

- `PatternEncoding` stores one full `PositionBlock` for every SID in ascending SID order,
  including empty vectors. Retaining the full layout is **SOURCE-CONFIRMED**; the Java list shape
  and fail-fast validation are **IMPLEMENTATION POLICY**.
- Construction enforces the bidirectional invariant `SID bit set iff its PositionBlock is
  non-empty`. Rebuilding `SequenceBlock` only after positional verification is
  **SOURCE-CONFIRMED** by PRISM Theorems 3.2-3.3.
- `PrimeBlockJoins` first performs a blockwise encoded GCD of the two sequence vectors. It then
  performs the appropriate blockwise position GCD only for candidate SIDs. The prefilter is never
  used as final support.
- `strictAfter` finds the first set bit over the complete logical vector, not once per block, and
  sets only later valid positions. It cannot set padding bits because the mask length is exactly
  the sequence size. These semantics are **SOURCE-CONFIRMED**; constructing the mask through the
  checked full-vector factory is **IMPLEMENTATION POLICY**.
- Join methods validate equal SID universes and equal per-SID sequence sizes. The fixed B13 codec
  already guarantees the same eight-prime generator. Fail-fast exception behavior is
  **IMPLEMENTATION POLICY**.
- Structural ancestry is not stored in `PatternEncoding`, so the join methods cannot themselves
  prove that literal PRISM operands are siblings. Enforcing legal pattern construction remains a
  caller responsibility for B15. This separation is **IMPLEMENTATION POLICY** and does not relax
  the PRISM/WSPM candidate-generation rules.

### 16.2 B14 differential validation

The WSPM direct-extension mapping `E(pattern) + E(singleton)` remains a
**REASONABLE-INFERENCE**, but B14 validates it against a test-only structural oracle that scans
ordered itemsets directly and does not call production GCD, mask, join, or support code. The tests
compare exact endpoint vectors, supporting SID sets, and support for both I-Extension and
S-Extension. They cover positive and zero support, multiple SIDs, repeated items across itemsets,
the 8-to-9 block boundary, and the Table 1 patterns `<(a)(a)>`, `<(a)(b)>`, `<(a)(c)>`, `<(ab)>`,
and `<(a)(bc)>`. The last pattern retains exactly SIDs `{1,2,4}` with support 3.

This evidence validates the mapping for the covered B14 cases; it does not promote the mapping to
`SOURCE-CONFIRMED` and is not the complete B17 nine-pattern oracle.

### 16.3 B15 one-step integration

B15 reuses the B14 joins without modifying their mechanics. `CandidateEngine` maps a legal
structural extension made by `PatternExtensions` to the corresponding operation:

```text
I-extension -> iJoin(parentEncoding, singletonEncoding)
S-extension -> sJoin(parentEncoding, singletonEncoding)
```

The resulting `PatternEncoding` is retained in immutable candidate metadata. Candidate support is
read from its rebuilt result `SequenceBlock`; the engine never substitutes the initial SID
intersection, endpoint count, occurrence count, or a direct database scan. The mapping remains
**REASONABLE-INFERENCE**, while the join mechanics and support extraction remain
**SOURCE-CONFIRMED**.

B15 differential tests compare every selected one-step result with a direct ordered-subset oracle,
including exact endpoint vectors, supporting SIDs, support, and weighted acceptance. The
position-8 to position-9 S-Extension passes through the Candidate Engine with support 1. Table 1
focused results reproduce the four accepted children documented by WSPM and separately retain the
structurally valid but rejected `<(ac)>` candidate with support 3.

### 16.4 B16 DFS use of Prime-block metadata

B16 creates singleton `PatternEncoding` values during preprocessing and thereafter obtains every
non-singleton support from the post-join `SequenceBlock` returned by B15. The DFS orchestrator does
not scan the database for candidate support and does not use the initial SID intersection as final
support. Accepted pattern encodings are retained in an immutable `Pattern -> PatternEncoding`
result map, and each published support is derived from the corresponding encoding.

The singleton preprocessing scan and metadata-map API are **IMPLEMENTATION POLICY**. Reusing full
PRISM position vectors and extracting support from sequence blocks are **SOURCE-CONFIRMED**. The
direct WSPM operand mapping retains its existing **REASONABLE-INFERENCE** classification. B16 adds
no Bitmap, CMAP, BitPrune pruning, or alternate support representation.

## 17. Source fidelity matrix

| Question | Decision | Classification | Evidence |
|---|---|---|---|
| Prime assignment | Prime per bit offset; reused per block | SOURCE-CONFIRMED | PRISM Sec. 2.2 |
| Sequence block | Encoded SID containment vector | SOURCE-CONFIRMED | PRISM Sec. 3.2 |
| Position block | Encoded itemset-index endpoints per SID | SOURCE-CONFIRMED | PRISM Sec. 3.2-3.3 |
| Empty/no-match | Product 1, rank 0, no set bits | SOURCE-CONFIRMED | PRISM Sec. 2.2, Thms. 3.2-3.3 |
| Support extraction | Sum factor-cardinalities of sequence blocks | SOURCE-CONFIRMED | PRISM Thm. 3.1 |
| I-join | Blockwise GCD of position vectors | SOURCE-CONFIRMED | PRISM Thm. 3.2 |
| S-join | GCD of right positions with strict-after left mask | SOURCE-CONFIRMED | PRISM mask definition, Thm. 3.3 |
| Literal join operands | Siblings under a common prefix | SOURCE-CONFIRMED | PRISM Sec. 3.3 |
| WSPM `pattern + singleton` operands | Equivalent direct extension interpretation | REASONABLE-INFERENCE | WSPM Fig. 1 plus PRISM endpoint semantics |
| Full encoding | Retain all empty blocks | SOURCE-CONFIRMED | PRISM Sec. 3.2 |
| Compact Java block indexes | Must preserve reconstructable alignment | REASONABLE-INFERENCE | Required by source's aligned joins |
| Original block width | Eight primes in reported implementation | SOURCE-CONFIRMED | PRISM Sec. 3.4 and author source |
| Original numeric storage | Ranked blocks and lookup tables | SOURCE-CONFIRMED | PRISM Sec. 3.4 and author source |
| Java numeric type | Rank as checked `int` recommended | IMPLEMENTATION UNRESOLVED | Java differs from C++ unsigned char |
| Generalized overflow | Must be detected, not hidden | ENGINEERING ANALYSIS | Java integer limits and prime products |

## 18. Open questions and task boundary

The following do not block B13 primitives:

1. Choose the public Java representation: transparent product value, rank, or a value object that
   exposes both. The eight-prime domain and exact behavior are fixed.
2. Decide whether the first B13 version stores only full vectors or also a compact form. Full
   vectors are source-faithful and reduce join-alignment risk.
3. WSPM's direct `E(P)` plus `E(singleton)` operand mapping is not stated as a theorem in WSPM.
   B14 has validated it against an independent structural oracle for the scoped extension and
   Table 1 cases, but its classification remains **REASONABLE-INFERENCE**.
4. R1 remains open and unrelated.

No Prime-block production code, dependency, Bitmap, CMAP, or BitPrune logic was introduced in
B12.
