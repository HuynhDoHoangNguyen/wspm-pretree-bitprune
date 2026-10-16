# B21.3 — SIGN/PUMSB Paper Reproduction Audit

Audit date: 2026-10-16 (Asia/Bangkok)

## 1. Result and scope

**Status: PARTIAL.** Pumsb reproduces the paper's count of one weighted sequential pattern under
the repository's explicit FIMI-to-one-itemset policy. SIGN reproducibly produces 315 patterns, not
the paper's 56. Independent item statistics, threshold arithmetic, and direct structural support
checks agree with production. No production-support or acceptance defect was found in the audited
sample. The SIGN discrepancy is therefore classified **D — unresolved paper reproduction gap** and
research issue **R8** is opened.

This task did not change the miner, oracle, minimum support, parsers, or expected test results. It
did not benchmark time/memory and did not implement BitPrune.

## 2. Authoritative-source audit

The audited local paper is:

```text
docs/Mining_weighted_sequential_patterns_based_on_prefix_tree_and_prism_encoding_2023.pdf
SHA-256: B1F019867FD61E21F4E951F464A5CBE79B3917E09CF99DD3B67C8AB18C119C41
DOI: https://doi.org/10.1142/S219688882350001X
```

Page references below use the published pagination, 357–372. The accepted-manuscript page number
is included because the accessible manuscript numbers its first page as 1.

| Evidence | Location | Classification |
|---|---|---|
| Support is the number of database sequences containing a pattern. | p. 363 (manuscript p. 7), problem definitions | SOURCE-CONFIRMED |
| The item-weight worked example divides item occurrences by the total lengths of all seven Table 1 sequences. | p. 364 (manuscript p. 8), Eq. (1) and `W(a)=23/66` example | SOURCE-CONFIRMED for the worked example; see R5 for the nearby wording conflict |
| `MaxW=max(W(i))`, `MinW=min(W(i))`, and `MeanW=(MaxW+MinW)/2`. | p. 364 (manuscript p. 8) | SOURCE-CONFIRMED |
| A pattern is accepted when `support(P) * MeanW >= minsup`. | p. 364 (manuscript p. 8) | SOURCE-CONFIRMED |
| SIGN has 730 sequences, 267 items, and average sequence length 51. | Table 3, p. 367 (manuscript p. 11) | SOURCE-CONFIRMED |
| Pumsb has 49,046 sequences, 2,113 items, and average sequence length 74. | Table 3, p. 367 (manuscript p. 11) | SOURCE-CONFIRMED |
| SIGN at `minsup=0.4%`, displayed `MeanW=0.00897`, has 56 patterns. | Table 4, p. 367 (manuscript p. 11) | SOURCE-CONFIRMED report value |
| Pumsb at `minsup=0.67%`, displayed `MeanW=0.0067`, has one pattern. | Table 4, pp. 367–368 (manuscript pp. 11–12) | SOURCE-CONFIRMED report value |
| Experimental datasets were downloaded from `http://fimi.ua.ac.be/data/`. | Experimental-results prose, p. 367 (manuscript p. 11) | SOURCE-CONFIRMED author statement |
| Relative input `p%` is converted to `(p/100)*N`, without integer rounding, before the weighted comparison. | Not stated explicitly by the paper; established by project R2 and the Table 1 boundary | STRONGLY-SUPPORTED IMPLEMENTATION INFERENCE |
| How SIGN was acquired/converted, and how transactional Pumsb became a “sequence database.” | Not described by the paper | UNSPECIFIED |
| Whether Table 4 counts include F1, and the exact SIGN pattern identities/supports. | Not supplied by the paper | UNSPECIFIED |

The paper's online accepted manuscript corroborates these tables and formulas. The public SPMF
catalog describes SIGN as sign-language utterances with about 800 sequences, 267 items, and average
length 51.997, while the FIMI mirror identifies Pumsb as a transaction dataset prepared from PUMSB:

- https://www.researchgate.net/publication/367007762_Mining_Weighted_Sequential_Patterns_based_on_Prefix-tree_and_Prism_Encoding
- https://www.philippe-fournier-viger.com/spmf/datasets.php
- https://www.cs.rpi.edu/~zaki/Workshops/FIMI/data/

The paper's blanket FIMI download statement does not explain the SIGN SPMF conversion exposed by
the public SPMF catalog. Matching metadata alone is not proof that the author input bytes were the
same.

## 3. Dataset provenance and byte identity

### 3.1 Local files

| Dataset | Current path | Bytes | Local SHA-256 |
|---|---|---:|---|
| SIGN | `src/main/resources/datasets/SIGN.txt` | 237,455 | `41F2250001AC5E6DCA40C3966F74D036CC4E982F270DCBCD55968CA21B9023DD` |
| Pumsb | `src/main/resources/datasets/pumsb.txt` | 16,691,407 | `33B8E1B577C2325052E5B2EB5E6B3E6DA8AC46C1237559A16B61ACE6F06A2D74` |

Both files are intentionally ignored by Git. The repository contains no acquisition log proving
who downloaded or transformed them, so the acquisition event itself remains **UNSPECIFIED**.

### 3.2 Public-source comparison

**SIGN.** The current public SPMF file is
`https://www.philippe-fournier-viger.com/spmf/publicdatasets/SIGN.txt`. Its raw size/hash at audit
time were 236,727 bytes and
`3C93441FD0FA2B9EBB85B2EF606F1D435596AE8962DEF78ACA5AC27D83188981`.
The raw hash differs because the local file uses CRLF and omits the public file's final newline.
After converting CRLF to LF and removing only the terminal newline, the two contents are exactly
equal and have normalized SHA-256
`3991E7D62EF4C99701826E4FDC96EC946032E2A337C75F03B9BBF55C4B18E3C8`.
No item, delimiter, itemset, sequence, or order differs.

**Pumsb.** The FIMI mirror file is
`https://www.cs.rpi.edu/~zaki/Workshops/FIMI/data/pumsb.dat`. The downloaded gzip stream was
1,303,578 bytes with SHA-256
`4B1AB0B7F2E5844544A42B6A2F88A611A154DDD35EB0F45C21A9026403FD1F11`.
Its decompressed bytes were 16,689,761. A streaming comparison of all 49,046 rows and all
3,629,404 tokens found:

```text
localToken = publicFimiToken + 1
mismatching tokens = 0
```

Thus the local Pumsb is a deterministic one-based-ID transformation of the mirrored FIMI dataset,
plus text line-ending differences. The transformation changes pattern labels (`4438` in FIMI is
`4439` locally) but preserves item equality, transaction membership, support, weights, and pattern
counts. Its origin was not documented before this audit.

## 4. Dataset structural audit

| Metric | SIGN (`SPMF_SEQUENTIAL`) | Pumsb (`FIMI_TRANSACTION`) |
|---|---:|---:|
| Sequences/transactions parsed | 730 | 49,046 |
| Distinct items | 267 | 2,113 |
| Total item occurrences | 37,958 | 3,629,404 |
| Sequence length min / max / average | 18 / 94 / 51.9972602739726 | 74 / 74 / 74 |
| Itemset count min / max / average | 18 / 94 / 51.9972602739726 | 1 / 1 / 1 |
| Itemsets containing more than one item | 0 | 49,046 |
| Empty sequences | 0 | 0 |
| Duplicate item inside an itemset | 0 | 0 |
| Sequences containing a repeated item across itemsets | 0 | not applicable: one itemset only |
| Exact duplicate sequence/transaction rows beyond first occurrence | 0 | 572 |

SIGN is a sequence of singleton itemsets: each line has the form `x -1 y -1 ... -2`. Every item
appears at most once per sequence. Pumsb is delimiter-free FIMI transaction text; the explicit
adapter maps each nonblank line to one sequence containing one itemset. It does not invent an
ordering among transaction items.

No normalization, sorting, deduplication, or rewriting is performed by either parser. The only
confirmed pre-parser transformation is the Pumsb `+1` item-ID mapping already present in the local
file. Whether that mapping and the repository's one-itemset interpretation match the paper authors'
procedure remains **UNSPECIFIED**; R7 stays OPEN.

## 5. Independent weight and threshold audit

The audit routine read raw tokens directly. It did not call `ItemStatisticsCalculator`,
`ItemWeightCalculator`, `F1Builder`, a Prime-block class, or the miner. Consistent with resolved R5,
it calculated:

```text
W(i) = rawOccurrenceCount(i) / totalDatabaseItemOccurrences
MaxW = max_i W(i)
MinW = min_i W(i)
MeanW = (MaxW + MinW) / 2
effectiveMinSup = (configuredPercent / 100) * N
accept iff sequenceSupport(P) * MeanW >= effectiveMinSup
```

### 5.1 SIGN

```text
N                         = 730
configured minimum support= 0.4%
relative fraction         = 0.004
effective threshold       = 0.004 * 730 = 2.92 (no rounding)
total occurrences         = 37,958
MaxW                      = 680 / 37,958 = 0.017914537119974708 (item 1)
MinW                      =   1 / 37,958 = 0.00002634490752937457
MeanW                     = 0.008970441013752042
2.92 / MeanW              = 325.51353891336271
minimum integer support   = 326
325 * MeanW               = 2.915393329469414 (reject)
326 * MeanW               = 2.924363770483166 (accept)
```

The independent MeanW, effective threshold, integer boundary, and 49 accepted singleton IDs equal
production exactly. Using the misleading containing-sequence denominator instead would produce
`MeanW=0.017689627445725` and 91 singletons; that interpretation contradicts the paper's Table 1
worked denominator and project R5 and is not used.

### 5.2 Pumsb

```text
N                         = 49,046
configured minimum support= 0.67%
relative fraction         = 0.0067
effective threshold       = 0.0067 * 49,046 = 328.6082 (no rounding)
total occurrences         = 3,629,404
MaxW                      = 48,944 / 3,629,404 = 0.013485409725673967
MinW                      =      1 / 3,629,404 = 0.00000027552733176025594
MeanW                     = 0.006742842626502864
328.6082 / MeanW          = 48,734.371866903668
minimum integer support   = 48,735
48,734 * MeanW            = 328.6056925599906 (reject)
48,735 * MeanW            = 328.6124354026171 (accept)
```

The paper's `0.0067` is a displayed rounded MeanW. Treating it as the exact internal MeanW would
incorrectly require support 49,046 and reject the paper's sole pattern. The exact value computed
from the data reproduces one pattern, so no intermediate display rounding may be applied.

## 6. SIGN F1 diagnostic

Because no SIGN item repeats within a sequence, raw occurrence count equals sequence support for
every item. `weightedSupport` below means `sequenceSupport * global MeanW`; it is not the item's
individual weight. These are all 49 accepted F1 entries, in item-ID order.

| Item | Raw occurrences | Sequence support | Item weight | Weighted support | Accepted | Effective threshold |
|---:|---:|---:|---:|---:|:---:|---:|
| 1 | 680 | 680 | 0.017914537119974708 | 6.099899889351389 | yes | 2.92 |
| 2 | 363 | 363 | 0.009563201433162970 | 3.256270087991991 | yes | 2.92 |
| 4 | 384 | 384 | 0.010116444491279835 | 3.444649349280784 | yes | 2.92 |
| 5 | 352 | 352 | 0.009273407450339850 | 3.157595236840719 | yes | 2.92 |
| 7 | 336 | 336 | 0.008851888929869857 | 3.014068180620686 | yes | 2.92 |
| 8 | 638 | 638 | 0.016808051003740977 | 5.723141366773803 | yes | 2.92 |
| 9 | 590 | 590 | 0.015543495442330997 | 5.292560198113705 | yes | 2.92 |
| 10 | 507 | 507 | 0.013356868117392909 | 4.548013593972286 | yes | 2.92 |
| 11 | 455 | 455 | 0.011986932925865430 | 4.081550661257179 | yes | 2.92 |
| 12 | 353 | 353 | 0.009299752357869224 | 3.166565677854471 | yes | 2.92 |
| 13 | 476 | 476 | 0.012540175983982296 | 4.269929922545972 | yes | 2.92 |
| 14 | 420 | 420 | 0.011064861162337319 | 3.767585225775858 | yes | 2.92 |
| 15 | 394 | 394 | 0.010379893566573581 | 3.534353759418305 | yes | 2.92 |
| 16 | 358 | 358 | 0.009431476895516096 | 3.211417882923231 | yes | 2.92 |
| 17 | 652 | 652 | 0.017176879709152222 | 5.848727540966332 | yes | 2.92 |
| 18 | 552 | 552 | 0.014542388956214763 | 4.951683439591127 | yes | 2.92 |
| 19 | 423 | 423 | 0.011143895884925445 | 3.794496548817114 | yes | 2.92 |
| 20 | 428 | 428 | 0.011275620422572318 | 3.839348753885874 | yes | 2.92 |
| 22 | 474 | 474 | 0.012487486168923548 | 4.251989040518468 | yes | 2.92 |
| 23 | 442 | 442 | 0.011644449127983561 | 3.964934928078403 | yes | 2.92 |
| 24 | 357 | 357 | 0.009405131987986723 | 3.202447441909479 | yes | 2.92 |
| 26 | 601 | 601 | 0.015833289425154117 | 5.391235049264977 | yes | 2.92 |
| 27 | 513 | 513 | 0.013514937562569156 | 4.601836240054798 | yes | 2.92 |
| 28 | 425 | 425 | 0.011196585699984192 | 3.812437430844618 | yes | 2.92 |
| 29 | 369 | 369 | 0.009721270878339216 | 3.310092734074503 | yes | 2.92 |
| 30 | 344 | 344 | 0.009062648190104853 | 3.085831708730702 | yes | 2.92 |
| 31 | 417 | 417 | 0.010985826439749196 | 3.740673902734601 | yes | 2.92 |
| 32 | 371 | 371 | 0.009773960693397966 | 3.328033616102008 | yes | 2.92 |
| 35 | 595 | 595 | 0.015675219979977870 | 5.337412403182465 | yes | 2.92 |
| 36 | 528 | 528 | 0.013910111175509774 | 4.736392855261078 | yes | 2.92 |
| 37 | 330 | 330 | 0.008693819484693608 | 2.960245534538174 | yes | 2.92 |
| 38 | 336 | 336 | 0.008851888929869857 | 3.014068180620686 | yes | 2.92 |
| 39 | 340 | 340 | 0.008957268559987354 | 3.049949944675694 | yes | 2.92 |
| 41 | 366 | 366 | 0.009642236155751093 | 3.283181411033247 | yes | 2.92 |
| 45 | 329 | 329 | 0.008667474577164235 | 2.951275093524422 | yes | 2.92 |
| 53 | 330 | 330 | 0.008693819484693608 | 2.960245534538174 | yes | 2.92 |
| 78 | 382 | 382 | 0.010063754676221087 | 3.426708467253280 | yes | 2.92 |
| 79 | 346 | 346 | 0.009115338005163601 | 3.103772590758207 | yes | 2.92 |
| 110 | 376 | 376 | 0.009905685231044839 | 3.372885821170768 | yes | 2.92 |
| 113 | 341 | 341 | 0.008983613467516730 | 3.058920385689446 | yes | 2.92 |
| 117 | 575 | 575 | 0.015148321829390379 | 5.158003582907424 | yes | 2.92 |
| 118 | 439 | 439 | 0.011565414405395437 | 3.938023605037146 | yes | 2.92 |
| 133 | 338 | 338 | 0.008904578744928605 | 3.032009062648190 | yes | 2.92 |
| 139 | 329 | 329 | 0.008667474577164235 | 2.951275093524422 | yes | 2.92 |
| 143 | 626 | 626 | 0.016491912113388484 | 5.615496074608778 | yes | 2.92 |
| 144 | 440 | 440 | 0.011591759312924811 | 3.946994046050898 | yes | 2.92 |
| 245 | 549 | 549 | 0.014463354233626640 | 4.924772116549871 | yes | 2.92 |
| 246 | 420 | 420 | 0.011064861162337319 | 3.767585225775858 | yes | 2.92 |
| 253 | 664 | 664 | 0.017493018599504716 | 5.956372833131356 | yes | 2.92 |

Nearest rejected singleton boundary cases:

| Item | Occurrences/support | Item weight | Weighted support | Decision |
|---:|---:|---:|---:|:---:|
| 6 | 324 | 0.008535750039517362 | 2.906422888455662 | reject |
| 34 | 324 | 0.008535750039517362 | 2.906422888455662 | reject |
| 25 | 318 | 0.008377680594341115 | 2.852600242373149 | reject |
| 46 | 318 | 0.008377680594341115 | 2.852600242373149 | reject |
| 21 | 317 | 0.008351335686811739 | 2.843629801359398 | reject |

The paper does not publish its full SIGN F1. Therefore exact F1 equality with the paper is **NOT
VERIFIED**, even though the repository F1 is independently reproduced.

## 7. SIGN pattern-length and extension audit

Production reproducibly reports:

| Total item length | Itemset count | Pattern count |
|---:|---:|---:|
| 1 | 1 | 49 |
| 2 | 2 | 131 |
| 3 | 3 | 114 |
| 4 | 4 | 21 |
| **Total** |  | **315** |

`Pattern.length()` counts total items; `Pattern.size()` counts itemsets. They happen to be equal for
every accepted SIGN pattern because all source itemsets are singletons and all accepted extensions
are S-extensions. This dataset cannot reveal whether the paper grouped results by item length or
itemset count.

The run generated 18,198 candidates: 2,763 I-candidates and 15,435 S-candidates. It accepted 266
extensions and rejected 17,932 candidates. All 266 accepted extensions are S-extensions. Every
I-extension has support zero because no input itemset has more than one item. Every repeated-item
pattern has support zero because no sequence repeats an item. Consequently:

- accepted I-extension sample: **not present; structurally impossible in this file**;
- accepted repeated-item sample: **not present; structurally impossible in this file**;
- multiple-embedding sample: **not present; each item occurs at most once per sequence**;
- representative accepted S-extension: `<(1)(22)>`, support 326;
- representative length-3 pattern: `<(17)(144)(253)>`, support 326;
- representative length-4 pattern: `<(1)(143)(144)(253)>`, support 326;
- representative high-support length-3 pattern: `<(8)(143)(253)>`, support 479;
- representative zero-support I-extension: `<(1 8)>`;
- representative zero-support repeated pattern: `<(1)(1)>`.

The accepted patterns nearest the threshold have support 326 and weighted support
`2.924363770483166`, only `0.004363770483166` above 2.92.

As a non-causal diagnostic only, filtering the current 315 result patterns by support at least 445
or 446 leaves 56 patterns. That corresponds to a threshold near 0.547%, not the paper's reported
0.4%. This numerical coincidence is not evidence that the authors used that threshold, and the
implementation was not changed to exploit it.

## 8. Independent structural verification

The independent matcher parsed raw SPMF itemset boundaries into sets and used a direct ordered
subsequence dynamic program. It did not call production parsing, encodings, joins, candidate logic,
support, or oracle code. The SID hash is SHA-256 of the exact ascending comma-separated SID list.

| Pattern | Role | Production support | Structural support | Weighted support | SID hash | Result |
|---|---|---:|---:|---:|---|---|
| `<(37)>` | accepted singleton near threshold | 330 | 330 | 2.960245534538174 | `0888634EE285105F6453AA4F432858899A8C6F24E223D291996AB251365B21FA` | exact match |
| `<(1)(22)>` | accepted S-extension at boundary | 326 | 326 | 2.924363770483166 | `A72F3E6FC31A8B7361F39862C4F0136F83DF92F0DF9F286D954613DB45C4C3F5` | exact match |
| `<(8)(143)(253)>` | accepted length 3, higher support | 479 | 479 | 4.296841245587229 | `CD0804E62EEA9E61DF6C882BF92F5CEF55C9453C859EC3C9CF0B0B0B173961F1` | exact match |
| `<(17)(144)(253)>` | accepted length 3 at boundary | 326 | 326 | 2.924363770483166 | `D4B1B021F90DEA0229ADCD70A5C6C871B06D4A13658DCEE134C7BA5DE03AEED0` | exact match |
| `<(1)(143)(144)(253)>` | accepted length 4 at boundary | 326 | 326 | 2.924363770483166 | `A1CD938645C25304523C9237884E8F39A25B7A70B025BFD6B69F9CCA339C22D7` | exact match |
| `<(6)>` | rejected singleton nearest boundary | not in result | 324 | 2.906422888455662 | `026C73944C9A9FD369066135F623C8E74F9EB6AEA027E2B27EA74CC55E267E0C` | correctly rejected |
| `<(1 8)>` | diagnostic I-extension | not in result | 0 | 0 | empty-set hash | correctly absent |
| `<(1)(1)>` | diagnostic repeated item | not in result | 0 | 0 | empty-set hash | correctly absent |

The production and independent matchers produced identical complete SID lists for every row, not
only identical counts. The exact SID sets for the four non-singleton accepted samples are included
in Appendix A in lossless range notation. The maximum number of embeddings in any supporting SID
was one, as implied by the source data's unique-item-per-sequence property.

No mismatch was found, so the requested correctness-defect stop condition was not triggered.

## 9. Pumsb audit

The input format was explicitly `FIMI_TRANSACTION`. One transaction becomes one sequence with one
itemset. At 0.67%, production and the independent token scanner agree on:

```text
F1 count                = 1
accepted pattern count  = 1
pattern                 = <(4439)> (local +1 ID; FIMI source ID 4438)
support                  = 48,944
weighted support         = 330.0216895115562
effective threshold      = 328.6082
supporting-SID SHA-256   = 900D6EFC46B4B2112157D4A44BA0C6DDA2BEEE389570C6C4FD3F30A865ED2408
```

The exact supporting set is `{1..49046}` except these 102 SIDs:

```text
39,106,125,314,1792,1799,1800,1827,1828,2092,2555,2573,2742,2853,2957,
4026,5113,6161,6317,6957,6958,7123,7951,8009,8278,8527,8530,9265,10148,
10487,10807,11192,11268,12137,14220,15225,15447,16447,18227,18365,18407,
20028,22341,22372,22417,22758,23204,23353,23744,24656,25174,25502,25961,
26602,27225,27996,28004,28404,28433,29389,29502,30639,31008,31123,31447,
32905,33109,33151,33266,33713,33827,34614,34968,35293,35659,35761,35884,
36116,36169,36531,36903,37493,38073,38208,39071,39648,39898,39932,41323,
42499,44105,45030,45513,46253,47269,47310,47548,47873,48035,48114,48390,
49035
```

Count equality with Table 4 does not prove paper-equivalent preprocessing. In particular, the
paper does not say that it treated each transaction as one one-itemset sequence, and the local IDs
are shifted from the mirrored FIMI source. R7 therefore remains OPEN.

## 10. Root-cause classification

**Final classification: D — unresolved paper reproduction gap.**

Evidence against A (confirmed implementation defect):

- independent raw-token weights reproduce production MeanW and F1;
- the effective threshold and inclusive boundary are arithmetically consistent;
- direct structural support and complete supporting SID sets agree for all audited patterns;
- zero-support I/repeated candidates are correctly absent.

Evidence relevant to B (dataset/preprocessing mismatch):

- Pumsb has a confirmed, support-preserving `+1` ID transformation;
- local SIGN exactly matches the public SPMF content after newline normalization;
- the paper supplies only aggregate SIGN metadata and says datasets came from FIMI, while the
  public SIGN file is distributed by SPMF as a conversion of a sign-language source;
- no paper input hash or transformation recipe exists for byte-level comparison.

These facts are insufficient to classify the SIGN count gap as confirmed B. The known Pumsb ID
shift cannot explain SIGN, and matching SIGN to today's public SPMF file does not prove equality to
the authors' input.

Evidence against C (confirmed threshold interpretation mismatch):

- the paper states 0.4% but does not expose conversion arithmetic;
- the repository's relative conversion reproduces the Table 1 acceptance boundary;
- no source states an alternative SIGN conversion or rounding policy;
- the accidental 56-count cutoff near 0.547% is not source evidence.

The paper does not publish SIGN F1 or its 56 pattern/support pairs, so the first missing/excess
pattern relative to the paper cannot be identified. Author code, exact experiment inputs, complete
output, or a preprocessing/threshold clarification is required to resolve the gap.

## 11. Research issues R1–R8

| Issue | Status after B21.3 | Audit effect |
|---|---|---|
| R1 — sequence-weight discrepancy | OPEN | Unchanged; candidate acceptance uses MeanW, not the disputed sequence-weight example. |
| R2 — percentage minimum support | RESOLVED FOR IMPLEMENTATION | Independently applied as `(p/100)*N`, real-valued and unrounded. Paper remains non-explicit about conversion. |
| R3 — Prime-block mechanics | RESOLVED | No new contrary evidence; selected outputs agree with structural matching. |
| R4 — full Table 1 oracle | RESOLVED BY B17 | Unchanged. |
| R5 — item-weight denominator | RESOLVED | Global database length reproduces the source worked example and current diagnostics. |
| R6 — zero minimum support | OPEN | Unchanged; this audit uses strictly positive thresholds. |
| R7 — transaction-to-sequence mapping | OPEN | Retained. Pumsb count equality does not validate the one-itemset policy as author preprocessing. |
| **R8 — SIGN paper benchmark reproduction discrepancy** | **OPEN** | Paper reports 56 at 0.4%; current public-content-equivalent SIGN yields independently supported 315. Exact author input/output/preprocessing is unavailable. |

## 12. Unresolved questions and required evidence

1. What exact SIGN bytes and preprocessing script were used for Table 4?
2. Why does the paper say all inputs came from FIMI when the audited SIGN representation matches
   the public SPMF sequential file?
3. What was the precise percentage-to-threshold conversion and numeric precision in the author
   implementation?
4. Does Table 4's pattern count include singleton patterns?
5. What are the 56 SIGN `pattern -> support` pairs?
6. How were Pumsb transactions represented as sequences, and were item IDs shifted?

Until this evidence is obtained, the 315-pattern result must not be described as reproducing the
paper's SIGN benchmark, and the coincident Pumsb count must not be described as proof of equivalent
preprocessing.

## 13. Acceptance evidence

- [x] Read the 2023 WSPM_PreTree paper and existing research notes.
- [x] Confirmed Table 3 metadata and Table 4 SIGN/Pumsb rows by page/table.
- [x] Recorded local paths, sizes, raw SHA-256 hashes, structure, duplicates, and transformations.
- [x] Compared local SIGN content with the public SPMF file.
- [x] Compared every local Pumsb token with a FIMI mirror and proved the `+1` mapping.
- [x] Independently verified SIGN/Pumsb MeanW and effective thresholds.
- [x] Exported all 49 SIGN F1 diagnostics plus nearest rejected boundary items.
- [x] Reproduced SIGN length counts 49/131/114/21 = 315 and clarified length semantics.
- [x] Independently matched representative production supports and complete SID sets.
- [x] Verified Pumsb F1/result count, sole pattern, support, and SID set.
- [x] Kept R7 OPEN and opened R8.
- [ ] Reproduced the paper's SIGN count of 56 — **NOT VERIFIED; source evidence is insufficient**.
- [ ] Established paper-equivalent Pumsb preprocessing — **NOT VERIFIED; R7 remains OPEN**.

## 14. Verification executed

```text
mvn clean test
BUILD SUCCESS — 342 tests, 0 failures, 0 errors, 0 skipped

mvn clean verify
BUILD SUCCESS — 342 tests, 0 failures, 0 errors, 0 skipped; JAR built

Independent PowerShell raw-data audits
PASS — dataset statistics, normalized public-file comparison, Pumsb +1 token proof,
       weight/F1 calculations, direct structural support, and SID-set hashes

Focused production JShell runs against target/classes
PASS — SIGN 315 with 49/131/114/21 distribution; Pumsb one pattern;
       selected production SID hashes equal the independent matcher
```

No test source was added because this task found no production correctness defect and the full real
datasets are intentionally ignored/non-fixture research inputs. The commands above tested the
unchanged 342-test baseline after adding this report.

## 15. B21.4 deep root-cause and threshold audit addendum

Audit date: 2026-10-16 (Asia/Bangkok).

This addendum supersedes only B21.3's root-cause label. Under B21.4's required A-E taxonomy, the
result is **E — unresolved reproduction gap**. It does not establish a production defect or an
author protocol strongly enough to select A-D. Production parser, weight, threshold, support,
candidate, and mining semantics were not changed.

### 15.1 Source, byte, and parser verification

The public SIGN source was checked again at
`https://www.philippe-fournier-viger.com/spmf/publicdatasets/SIGN.txt` on 2026-10-16:

| Artifact | Bytes | SHA-256 |
|---|---:|---|
| Local `src/main/resources/datasets/SIGN.txt` | 237,455 | `41F2250001AC5E6DCA40C3966F74D036CC4E982F270DCBCD55968CA21B9023DD` |
| Public SPMF download | 236,727 | `3C93441FD0FA2B9EBB85B2EF606F1D435596AE8962DEF78ACA5AC27D83188981` |
| Local after CRLF-to-LF and terminal-newline removal | 236,726 | `3991E7D62EF4C99701826E4FDC96EC946032E2A337C75F03B9BBF55C4B18E3C8` |
| Public after the same normalization | 236,726 | `3991E7D62EF4C99701826E4FDC96EC946032E2A337C75F03B9BBF55C4B18E3C8` |

The raw files are **not byte-identical**. Their normalized contents are identical. The local file
uses CRLF and has no final newline; the public file uses LF and has a final newline. The source URL,
not merely the catalog page, was hashed.

An independent raw-token parser was compared with `SpmfSequenceParser` for all 730 SIDs and every
itemset, including explicit checks at the beginning (SID 1), middle (SID 365), and end (SID 730).
There were zero structural differences. SID assignment is stable and one-based. No item ID is
remapped. No itemset/sequence is sorted, removed, merged, or split by the parser. Consequently no
raw/expected/actual mismatch record exists to report.

The sequence-length histogram is also the itemset-count histogram because every itemset is a
singleton:

```text
18:1, 19:1, 21:2, 22:2, 23:1, 24:1, 25:3, 26:1, 28:3, 29:4, 30:1,
31:6, 32:8, 33:6, 34:8, 35:8, 36:14, 37:10, 38:15, 39:12, 40:18,
41:14, 42:17, 43:14, 44:27, 45:20, 46:21, 47:35, 48:23, 49:28,
50:16, 51:34, 52:28, 53:19, 54:31, 55:30, 56:12, 57:8, 58:22,
59:17, 60:20, 61:16, 62:10, 63:16, 64:19, 65:10, 66:10, 67:5,
68:9, 69:7, 70:7, 71:8, 72:4, 73:13, 74:3, 75:7, 76:2, 77:3,
78:2, 79:4, 80:4, 81:2, 82:1, 84:2, 86:1, 89:1, 91:2, 94:1
```

The total is 730 records; minimum/maximum/mean length is
`18 / 94 / 51.9972602739726`. There are 37,958 item occurrences, 267 distinct items, zero empty
records, zero duplicate rows, zero multi-item itemsets, zero duplicate items inside an itemset,
and zero sequences that repeat an item across itemsets.

### 15.2 Independent item-weight and F1 verification

The B21.4 raw scanner independently computed all 267 occurrence counts, sequence supports, and
weights before comparing them with production. The result was:

```text
267/267 occurrence counts equal
267/267 sequence supports equal
267/267 item weights equal
independent F1 count = production F1 count = 49
```

The complete 49-item F1 diagnostic remains in Section 6. Items 1 and
`142,148,155,156,158,194,217,221,223,235,238,243,244,270,279,285,291,294,296`
define MaxW and MinW respectively:

```text
MaxW  = 680 / 37958
MinW  =   1 / 37958
MeanW = ((680 / 37958) + (1 / 37958)) / 2
      = 681 / 75916
      = 0.0089704410137520417303335265293218820801938985194162... (exact rational decimal)
```

The production Java double is `0.008970441013752042`, exactly represented for display by:

```text
Double.toHexString              = 0x1.25f183647315bp-7
BigDecimal.valueOf(double)      = 0.008970441013752042
new BigDecimal(double)          = 0.00897044101375204204484870018632136634550988674163818359375
```

This use of `BigDecimal` is diagnostic only. There is no claim that the paper implementation used
`BigDecimal`.

### 15.3 Exact threshold and boundary arithmetic

For `0.4%`, `N=730`:

```text
effectiveMinSup = 0.004 * 730 = 2.92 = 73/25
requiredSupport = (73/25) / (681/75916) = 325.5135389133627...
```

Exact integer cross-multiplication, rather than `ceil(double)`, gives the first accepted support
as 326. Java double reaches the same decision:

| Support | Java-double weighted support | Hex representation | Delta from 2.92 | Decision |
|---:|---:|---|---:|:---:|
| 324 | 2.9064228884556615 | `0x1.7405aa4b21a77p1` | -0.013577111544338383 | reject |
| 325 | 2.915393329469414 | `0x1.752b9bce861a9p1` | -0.0046066705305860545 | reject |
| 326 | 2.9243637704831658 | `0x1.76518d51ea8dap1` | +0.004363770483165830 | accept |
| 327 | 2.9333342114969176 | `0x1.77777ed54f00bp1` | +0.013334211496917714 | accept |
| 328 | 2.9423046525106700 | `0x1.789d7058b373dp1` | +0.022304652510670042 | accept |

There is no equality-boundary pattern because the real-valued threshold falls between integer
supports 325 and 326. Inclusive `>=` and strict `>` therefore produce the same SIGN output here.

### 15.4 Complete audited-candidate support distribution

The universe below is explicitly bounded: all 267 singleton items plus all 18,198 extensions
generated from the exact-run accepted prefixes (`2,763` I and `15,435` S). It is not claimed to be
every syntactically possible SIGN pattern. Every row's support was recomputed by the independent
structural matcher.

| Support band | Total | Singleton | I-extension | S-extension | Accepted | Rejected |
|---|---:|---:|---:|---:|---:|---:|
| `<=323` | 18,144 | 216 | 2,763 | 15,165 | 0 | 18,144 |
| `324` | 4 | 2 | 0 | 2 | 0 | 4 |
| `325` | 2 | 0 | 0 | 2 | 0 | 2 |
| `326` | 8 | 0 | 0 | 8 | 8 | 0 |
| `327` | 5 | 0 | 0 | 5 | 5 | 0 |
| `328` | 4 | 0 | 0 | 4 | 4 | 0 |
| `329-444` | 242 | 32 | 0 | 210 | 242 | 0 |
| `445` | 0 | 0 | 0 | 0 | 0 | 0 |
| `446` | 1 | 0 | 0 | 1 | 1 | 0 |
| `>=447` | 55 | 17 | 0 | 38 | 55 | 0 |

Thus both filters `support >= 445` and `support >= 446` retain 56 current patterns only because no
audited pattern has support 445. This remains a non-causal observation.

### 15.5 Controlled rounding hypotheses

The independent projected enumerator rebuilt the result under each policy; counts are complete and
none hit its 10,000,000-candidate fail-fast bound.

| Hypothesis | Paper support | Minimum accepted support | Complete pattern count | Change vs exact |
|---|---|---:|---:|---|
| No rounding, inclusive `>=` | algorithm condition; percentage conversion is project inference | 326 | 315 | baseline |
| Strict `>` | not supported; paper uses inclusive condition | 326 | 315 | none |
| MeanW rounded to paper's five displayed decimals | display only, not an algorithm instruction | 326 | 315 | none |
| Effective threshold rounded to two decimals | not needed: it is already 2.92 | 326 | 315 | none |
| Weighted support rounded to five decimals | unsupported | 326 | 315 | none |
| Weighted support rounded to two decimals | unsupported | 325 | 317 | adds two patterns |
| `floor(2.92)` | unsupported | 223 | 1,774 | adds 1,459 patterns |
| `ceil(2.92)` | unsupported | 335 | 273 | removes 42 patterns |
| `round(2.92)` | unsupported | 335 | 273 | removes 42 patterns |

The only boundary changes under decimal display rounding are:

| Pattern | Support/SID count | Exact weighted support | Threshold | Delta | Exact | Rounded-to-2-decimal weighted support |
|---|---:|---:|---:|---:|:---:|:---:|
| `<(26)(246)(253)>` | 325 | 2.915393329469414 | 2.92 | -0.0046066705305860545 | reject | accept (`2.92`) |
| `<(143)(246)(253)>` | 325 | 2.915393329469414 | 2.92 | -0.0046066705305860545 | reject | accept (`2.92`) |

All changes for the integer floor/ceil/round probes, including pattern, support, SID count,
weighted support, threshold, delta, and both decisions, are generated at
`target/b21.4/sign-rounding-boundary.tsv`. It is a build diagnostic, not a tracked paper oracle.
No rounding hypothesis was applied to production.

### 15.6 Full support, SID, and completeness validation

For every production result pattern, the direct ordered-subsequence matcher scanned the raw
itemset sets and compared both support and the complete ascending SID list with the production
Prime-block encoding:

```text
315/315 pattern supports equal
315/315 supporting SID sets equal
315/315 weighted acceptance decisions equal
```

In addition, an independent vertical positional projection enumerator used raw per-item positions,
not production parsing, Prime-block joins, Candidate Engine, or the B18 oracle. At required support
326 it independently enumerated exactly the same complete `pattern -> support` map:

```text
production patterns             = 315
independent projected patterns  = 315
missing                         = 0
unexpected                      = 0
support mismatches              = 0
projection S-candidates checked = 15,435
```

Because all source itemsets are singleton and the threshold is positive, every I-extension has
support zero and cannot be missing from the accepted output. This closes the B21.3 limitation for
this exact local SIGN input: completeness, not only accepted-pattern soundness, was checked.

### 15.7 Pattern-count decomposition and count invariants

| Classification | Count |
|---|---:|
| Length 1 | 49 |
| Length 2 | 131 |
| Length 3 | 114 |
| Length 4 | 21 |
| Longer | 0 |
| Singleton | 49 |
| I-only (one multi-item itemset) | 0 |
| S-only (at least two singleton itemsets) | 266 |
| Mixed I/S (multiple itemsets and at least one multi-item itemset) | 0 |
| Repeated item across itemsets | 0 |

The categories singleton/I-only/S-only/mixed are mutually exclusive; repeated-item is an
additional orthogonal flag. All four count paths agree:

```text
PrefixTree.nodeCount() - 1          = 315
WspmMiningStatistics.patternCount   = 315
acceptedPatterns().size()           = 315
unique Pattern set size             = 315
```

There is no root, duplicate pattern, duplicate serialization, duplicate SID, repeated F1 addition,
or rejected candidate in the reported count.

### 15.8 Paper output semantics and the four-row SIGN clue

Table 4's heading explicitly calls 56 the **number of weighted sequential patterns**. The paper's
Table 1 worked result calls its complete result nine patterns and includes its three F1 singletons,
so singleton inclusion is source-confirmed for that worked example. Table 4 does not separately
state its singleton policy, but it introduces no different output semantics.

No minimum/maximum pattern length, closed/maximal filter, output cap, top-k restriction, or special
SIGN pruning is described in the algorithm or experimental section. Closed/top-k weighted mining
appears only as future work. Support remains the number of containing sequences. Any such filter
for Table 4 is therefore **UNSPECIFIED**, not inferred.

A stronger numerical clue was found by checking all four SIGN rows, not by sweeping arbitrary
rounding levels:

| Table 4 label | Paper count | Count on current SIGN if weighted threshold is the integer shown |
|---|---:|---:|
| `0.4%` | 56 | threshold `4` -> 56 |
| `0.3%` | 273 | threshold `3` -> 273 |
| `0.2%` | 1,774 | threshold `2` -> 1,774 |
| `0.1%` | 25,550 | threshold `1` -> 25,550 |

All four identities were independently enumerated (`requiredSupport` 446, 335, 223, and 112).
This strongly suggests that the reported SIGN counts were produced with effective weighted
thresholds `4,3,2,1`, or with an equivalent undisclosed conversion, rather than the project's
unrounded `(p/100)*N` interpretation. However, the paper never states that protocol, the exact
author input/code is unavailable, and this correspondence alone does not prove which step was
performed. It is recorded as an **unsupported but systematic hypothesis**, not applied to
production and not sufficient by itself to classify the cause as D.

### 15.9 Pumsb secondary validation

The exact Table 4 Pumsb rows are:

| minSup | Paper pattern count | Paper WSPM_PreTree memory | Paper SPMW memory |
|---:|---:|---:|---:|
| 0.67% | 1 | 1060.5 MB | 212.5 MB |
| 0.66% | 15 | 1366.51 MB | 232.58 MB |
| 0.65% | 62 | 1385.52 MB | 252.2 MB |
| 0.60% | 3,684 | 1442.61 MB | 311.9 MB |

Therefore `62` is a pattern count, not time or memory. `64` does not occur in that paper row.
Using the local one-itemset Pumsb representation, an independent raw-token vertical intersection
enumerator produced:

| minSup | Required support | Independent count | Production count |
|---:|---:|---:|---:|
| 0.67% | 48,735 | 1 | 1 |
| 0.66% | 48,007 | 15 | 15 |
| 0.65% | 47,280 | 64 | 64 |
| 0.60% | 43,643 | 4,059 | NOT RUN |

The 0.65% production run was count validation, not a benchmark; pattern output and export were
disabled. The 0.60% production miner was deliberately not rerun solely to collect time/memory on a
large dataset. The independent enumerator is complete for the repository's explicit one-sequence,
one-itemset transaction mapping. It shows that the discrepancy starts at 0.65%, but does not prove
that paper values 62 or 3,684 are typographical errors. Author preprocessing is still unknown, so
R7 remains OPEN.

### 15.10 B21.4 root-cause classification and R8

**Classification: E — Unresolved reproduction gap.**

- **A not confirmed:** local SIGN is normalized-content identical to today's SPMF file and all 730
  parsed structures match an independent raw parser. The authors' exact historical bytes and
  conversion script remain unavailable.
- **B not confirmed:** exact rational, high-precision decimal, and Java-double arithmetic agree on
  support 326. Paper-display rounding and strict comparison do not change the 315 count. The only
  two-pattern decimal-rounding change cannot explain 56.
- **C not confirmed:** all 315 supports/SID sets agree, and independent projection finds neither a
  missing nor an extra production pattern under the project's exact semantics.
- **D not confirmed:** the four exact `4/3/2/1 -> paper count` correspondences are strong evidence
  of an undisclosed SIGN threshold protocol or reporting inconsistency, but no source states the
  transformation and author input/code/output is unavailable.

R8 remains **OPEN**, narrowed to this statement:

> The current implementation is internally and independently consistent with the project's
> unrounded percentage semantics on the public-content-equivalent SIGN file. Table 4's complete
> SIGN count series instead matches effective weighted thresholds 4, 3, 2, and 1 exactly. The
> source does not disclose enough information to determine whether this came from author input,
> threshold conversion, preprocessing, or reporting.

R1, R2, R3, R4, R5, R6, and R7 retain the statuses in Section 11; no unrelated issue was changed.

### 15.11 B21.4 acceptance evidence

- [x] Public/local SIGN raw and normalized hashes distinguished.
- [x] Full raw-parser-versus-production structure comparison for all 730 SIDs.
- [x] All 267 item counts, supports, and weights independently compared.
- [x] Exact rational, `BigDecimal`, and Java-double threshold checks completed.
- [x] Supports 324-328 and complete audited-candidate boundary distribution checked.
- [x] Controlled rounding policies enumerated without production changes.
- [x] All 315 accepted supports and full SID sets independently checked.
- [x] Complete independent projected `pattern -> support` result equals production.
- [x] Count/decomposition invariants exclude duplicate/root/rejected-pattern inflation.
- [x] All Table 4 SIGN and Pumsb rows re-read in their correct columns.
- [ ] Paper SIGN count 56 reproduced under the documented percentage interpretation — **NOT
  VERIFIED**.
- [ ] Exact author SIGN/Pumsb preprocessing and threshold code obtained — **NOT VERIFIED**.

### 15.12 Verification executed for B21.4

```text
mvn -q -Dtest=vn.edu.wspm.audit.SignDeepAuditTest,
  vn.edu.wspm.mining.Table1EndToEndCorrectnessTest,
  vn.edu.wspm.mining.WspmDifferentialCorrectnessTest test
PASS — focused B21.4 numerical/support/projection audit, exact Table 1 nine-pattern
       regression, and B19 differential suite

mvn clean test
BUILD SUCCESS — 349 tests, 0 failures, 0 errors, 0 skipped

mvn clean verify
BUILD SUCCESS — 349 tests, 0 failures, 0 errors, 0 skipped; JAR built
```

The SIGN test is conditional on the intentionally Git-ignored research input being present. It ran
in this audit (zero skipped); a clean environment without that file will skip only this external
dataset audit rather than silently substitute a fixture.

## Appendix A — exact SIGN SID sets for representative extensions

Ranges are inclusive; commas denote union. These lossless sets produced the hashes in Section 8.

### `<(1)(22)>` — support 326

```text
3-7,10,13-17,19-31,33-34,36-37,39-42,47-48,55-57,60-61,67-68,73,75-76,
78-79,81,83,85-86,90-91,93,98-100,103-104,108-110,112-113,115-117,126-127,
130,136,139,142-144,147-148,152,159,161,164,169-173,175,179,181,183,191,
198-199,202-204,209-210,213-214,217,219-223,228-229,231-235,237-241,247,
249-251,253-254,257-258,260,262-264,267-268,272,277,281,284-287,289,291-292,
295,297,299,305-307,313,315,320,323,325-327,332,337-339,345-348,350-351,
353-355,357,359-360,362,365-367,372,378,380-382,385-386,390,392,394,396-402,
404-405,407,410,412-422,425-426,428,430,435-436,438-439,450,455,457,459,461,
465,467,472,474,480,482-484,486,494-497,505,507-508,512,514-520,522,524-526,
531,533-534,538,541,545,547,549-550,555,559-562,564,566,568-569,571-572,
574-575,580,583,585,587,589,591,595-597,600,602-604,606-608,612-613,621,624,
626,628,633,635-636,641,646,648-649,651,654,657,660,663-664,668,680,682,684,
687-688,691,694,701,703,705-709,712-713,716-717,719,723,730
```

### `<(8)(143)(253)>` — support 479

```text
1-8,13,16-24,26-31,33,36-37,44,48-51,53-54,56,58,63,65-66,68,70-72,75,77,
79-81,83,85,88-92,98,109-110,117,125,128,132-133,135-136,139-148,150-152,
156,158-160,166-169,171,174-179,181-185,188-189,192,198-202,205-208,210-214,
216-220,222,224-228,230-231,233-236,238-241,244,249,251-255,257-265,275-281,
283-286,288-292,294-296,298-300,302-303,305-308,311-313,315-319,321,323,
325-327,329-331,333,335-341,343-346,348,351,353-357,360-362,364-367,369-375,
378-382,384-386,389-393,395,397-399,401-408,410-420,426-428,431,435-437,
439-440,442,444-447,449-451,453,456-459,463,465,468,471,473-474,476-477,
479-483,485-486,488-500,502-503,507-509,517,519,521,523-526,528-530,536,
540-541,543,546,548-560,562-566,568-569,571-594,596-597,602-607,609-613,
615-617,619-624,626-636,638-642,644-654,656-664,666,668-676,678-682,684,
688-693,698,703-709,712-713,716-718,720-725,730
```

### `<(17)(144)(253)>` — support 326

```text
1,10-12,16-18,22-23,25-27,29-30,36,49,53,56,58-61,65-66,72-73,75-78,80-81,
85-86,88-91,97-98,108-110,112,116,118,120-121,123,125-128,130-135,137-141,
144-148,150-154,158-159,166,169-170,172-174,176,178,181,184-189,192,198-200,
204,209,212-214,219-223,225,229,231,234-242,246,253-255,258-259,263-265,
275-276,280,283,285-291,293-296,298-300,302-306,308,311-312,315-318,321,323,
325-326,329-331,335,338-341,345-346,354,356,360-361,365-366,369,373,376-377,
382,384,386,388-390,392-399,401-402,406-408,413,421,426,432,436,439-442,
444-447,449-451,453,456-459,465,469,471,478-479,483,485-486,492-494,497,500,
508,517,523-524,528,536,540-541,543,549-550,552-553,555,562,566,571-573,
577-579,601-604,607,620-621,628-636,638-649,651-653,656-657,659-663,666,
668-670,673-679,681,684,689,693,698,702,704-715,719,721-722,724
```

### `<(1)(143)(144)(253)>` — support 326

```text
1,4,11-12,16-18,22-23,26-27,29-30,36,48-49,53,56,58,65,72-73,75,77,80-81,
85,88,90-92,94,97-98,108-110,112,116,118,120,123,125-128,130-153,155,
158-160,166-167,169-170,173-174,176,178,181,184,186-189,192,198-200,204,
207-208,211-214,217,219-220,222-225,227,230-231,233-235,237-241,246,251,
253-255,258,260,262,264-265,276-281,283,285-292,294-295,299-300,302,304-306,
311-313,315-317,323,325-326,332-333,335-341,345-350,352,354,356-357,360-361,
363-366,369,371,375-379,381-382,384,386,390-399,401-402,405-408,413-414,426,
432,436,439-440,442,444-445,447,450,457-459,465,469,471,474,479,483,485-486,
492-494,497,500,508,517,523-524,528,536,540-541,543,549-550,552-553,555,562,
566,569,571-573,577-579,602-604,607,620-621,628-633,635-636,638-641,645-646,
648-649,651-653,656-657,659-663,666,668-669,673-674,676-679,681,684,689,693,
698,702,704-710,712-715,718-719,721-722,724,730
```
