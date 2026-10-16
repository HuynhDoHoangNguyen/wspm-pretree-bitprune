# Phase B WSPM_PreTree Reproducibility Manifest

Manifest date: 2026-10-16

Scope: Phase B WSPM_PreTree baseline checkpoint preparation. This manifest records the current
working-tree inputs and the post-commit verification procedure. It does not claim that a fresh
checkout has passed until an approved checkpoint exists and is independently checked out.

## 1. Required environment

- JDK 21. Audited runtime: Oracle JDK 21.0.12.1 LTS.
- Apache Maven. Audited version: 3.9.16.
- UTF-8 source and reporting encoding, declared in `pom.xml`.
- Audited OS: Windows 11 64-bit. The implementation uses Java Core and is not intentionally tied to
  Windows.

Maven project dependencies:

- JUnit Jupiter 5.12.2, test scope only.
- Maven Surefire Plugin 3.5.3.
- No non-JDK production dependency.

## 2. Repository inputs required for the checkpoint

The proposed checkpoint consists of the already tracked repository files plus the following
modified or untracked paths. Counts below are based on the file-level inventory performed for
B21.1, not directory-only `git status` output.

| Category | Paths | Count | Checkpoint policy |
|---|---|---:|---|
| Build/configuration | `.gitignore`, `pom.xml` | 2 modified | Include |
| Process instructions | `AGENTS.md`, `PHASE_B_WSPM_PRETREE.md` | 2 | Include |
| Research documentation | `docs/research/phase-b-baseline-audit.md`, `docs/research/phase-b-reproducibility.md`, `docs/research/prism-prime-block.md`, `docs/research/wspm-pretree-spec.md` | 4 | Include |
| Production Java | `src/main/java/vn/edu/wspm/config/**`, `experiments/**`, `io/**`, `mining/**`, `model/**`, `prism/**`, `statistics/**`, `tree/**` | 40 | Include |
| Java tests/oracles | `src/test/java/vn/edu/wspm/config/**`, `experiments/**`, `fixture/**`, `io/**`, `mining/**`, `model/**`, `prism/**`, `statistics/**`, `tree/**` | 29 | Include |
| Small test fixtures | `src/test/resources/datasets/wspm/table1.spmf`, `src/test/resources/spmf/single-sequence.txt` | 2 | Include |

Together these are 77 untracked files after this manifest is created and the three local real
datasets become ignored, plus the two modified tracked files. Existing tracked files remain part of
the repository automatically. No source, test, fixture, or Maven configuration required by the
current baseline was found outside this proposed set.

The repository layout relevant to reproduction is:

```text
pom.xml
src/main/java/                 production baseline and standalone runner
src/test/java/                 unit, paper-oracle, and differential tests
src/test/resources/            small committed fixtures
docs/research/                 source-fidelity and audit records
```

## 3. Dataset fixtures and input formats

Required committed fixtures:

| Fixture | Purpose | Bytes | SHA-256 |
|---|---|---:|---|
| `src/test/resources/datasets/wspm/table1.spmf` | Paper Table 1 oracle and default standalone input | 273 | `F7E409F982EE42E0E34C7B940496074750AD8AEB44F191397EFEDDD0653AE8F5` |
| `src/test/resources/spmf/single-sequence.txt` | Minimal SPMF parser fixture | 12 | `5214A320728C925A2648A46AC7A16DC048BADF96B8B9DD370A9A0A9E2AE81F48` |

Supported formats are selected explicitly:

- `SPMF_SEQUENTIAL`: `-1` ends an itemset and `-2` ends a sequence.
- `FIMI_TRANSACTION`: one nonblank transaction line becomes one sequence containing exactly one
  itemset. The adapter does not invent temporal order.

The following locally acquired datasets are not required by any test or the default Main and are
excluded by `.gitignore` pending a provenance, licensing, storage, and R7 decision:

| Local file | Bytes | Lines | SHA-256 |
|---|---:|---:|---|
| `src/main/resources/datasets/SIGN.txt` | 237455 | 730 | `41F2250001AC5E6DCA40C3966F74D036CC4E982F270DCBCD55968CA21B9023DD` |
| `src/main/resources/datasets/T40I10D100K.txt` | 15578111 | 100000 | `CAA0C26C7494D3BAF93A8627E19225F275463D51F14E08886B435B5FA73D967D` |
| `src/main/resources/datasets/pumsb.txt` | 16691407 | 49046 | `33B8E1B577C2325052E5B2EB5E6B3E6DA8AC46C1237559A16B61ACE6F06A2D74` |

Do not add large or real experimental datasets to Git until their source, license, exact format,
preprocessing, and storage policy are approved. Small deterministic test fixtures belong under
`src/test/resources` and are not excluded by the real-dataset rule.

## 4. Authoritative research sources

The local reference documents are deliberately excluded from Git. Their exact audited copies are:

| Source | Metadata | Local SHA-256 |
|---|---|---|
| *Mining Weighted Sequential Patterns Based on Prefix-Tree and Prism Encoding* | Thi-Thiet Pham, Thuy-Duong Vu, Tai-Du Nguyen, Bao Huynh, Trang Van; 2023; DOI [10.1142/S219688882350001X](https://doi.org/10.1142/S219688882350001X) | `B1F019867FD61E21F4E951F464A5CBE79B3917E09CF99DD3B67C8AB18C119C41` |
| *PRISM: An Effective Approach for Frequent Sequence Mining via Prime-block Encoding* | Karam Gouda, Mosab Hassaan, Mohammed J. Zaki; 2010; DOI [10.1016/j.jcss.2009.05.008](https://doi.org/10.1016/j.jcss.2009.05.008) | `EBABC7B9AC95605F9D7C30A894B0426D8A7586728B0F9C88D5972C9F5BA81298` |

The ignored proposal and BitPrune word-processing documents are also local research inputs, but
they are not required to compile or execute the Phase B baseline. Their distribution and Git policy
remain a user/licensing decision.

## 5. Build and regression procedure

From the repository root:

```text
mvn clean verify
```

The B21/B21.1 working-tree reference result is 338 tests, zero failures, zero errors, zero skipped,
and `BUILD SUCCESS`. Test count is evidence for this recorded revision, not a permanently hardcoded
contract.

The deterministic differential suite uses seed:

```text
20261016
```

It performs 18 fixed and 128 generated comparisons, for 146 comparisons total. Equality checks
include the complete `Pattern -> support` map and supporting SIDs.

## 6. Standalone execution

After compilation from the repository root:

```text
java -cp target/classes vn.edu.wspm.experiments.MainWspmPreTree
```

The source-level default configuration is:

```text
dataset       = src/test/resources/datasets/wspm/table1.spmf
input format  = SPMF_SEQUENTIAL
relative flag = true
MIN_SUP       = 0.14 (14 percent)
print summary = true
print patterns = true
export        = false
```

Expected Table 1 result:

```text
<(1)>      #SUP: 7
<(1 2)>    #SUP: 7
<(1 2)(3)> #SUP: 6
<(1)(1)>   #SUP: 6
<(1)(2)>   #SUP: 7
<(1)(3)>   #SUP: 6
<(2)>      #SUP: 7
<(2)(3)>   #SUP: 6
<(3)>      #SUP: 6
```

Expected deterministic statistics are seven sequences, three F1 patterns, 34 candidates, six
accepted candidates, 28 rejected candidates, seven I-candidates, 27 S-candidates, nine expanded
nodes, and nine accepted patterns. Elapsed time is environment-dependent and must not be asserted.
No export file should be created with the default configuration.

## 7. Fresh-checkout verification after the user-approved commit

B21-F01 cannot be closed from the current working tree. After the user creates the checkpoint:

1. Record the checkpoint commit hash.
2. Clone or create a separate Git worktree at that exact commit. Do not copy the current working
   tree and call it a fresh checkout.
3. Confirm `git status --short` is empty in the independent checkout.
4. Run `mvn clean verify` and confirm the test result.
5. Run the standalone command and compare the exact nine pattern/support lines and deterministic
   counters above.
6. Confirm default export remains absent.
7. Record JDK, Maven, OS, commit hash, and command output in the follow-up verification report.

Only after these steps pass may B21-F01 be marked RESOLVED.

## 8. Research limitations retained

| Issue | Status | Reproducibility boundary |
|---|---|---|
| R1 - sequence-weight discrepancy | OPEN | Paper formula and worked value conflict; checkpointing does not resolve the source ambiguity. |
| R2 - percentage minimum support | RESOLVED FOR IMPLEMENTATION | Explicit `(percentage / 100) * sequenceCount`, without rounding. |
| R3 - Prime-block mechanics | RESOLVED | Source-derived representation and joins remain documented and tested. |
| R4 - full Table 1 oracle | RESOLVED | Exact nine-pattern map, SIDs, and tree structure are tested. |
| R5 - item-weight denominator | RESOLVED | Total database item-occurrence length. |
| R6 - zero minimum support | OPEN | Use strictly positive effective thresholds until the source/policy is resolved. |
| R7 - transaction-to-sequence mapping | OPEN | FIMI one-itemset mapping is implementation policy, not verified author preprocessing. |

Checkpointing preserves these classifications and does not turn implementation policy into
source-confirmed behavior.

## 9. Git inclusion and exclusion policy

Include all production source, test source/oracles, small fixtures, Maven/build configuration,
process instructions, and `docs/research` records listed in Section 2.

Keep excluded:

- `target/`, compiled classes, packaged JARs, Surefire reports, logs, and generated outputs;
- IDE/editor/OS caches already covered by `.gitignore`;
- local source PDFs and word-processing documents under `docs/`;
- the three real/large dataset files listed in Section 3;
- any future sensitive, personal, or generated experimental data.

The B21.1 credential-pattern and absolute-user-path scans found no suspicious value in the proposed
checkpoint set. This is a scoped text scan, not a formal secret-scanning guarantee. No file remains
unclassified: the ignored research documents need a user/licensing decision only if the user wants
to distribute them, and the real datasets need the policy decisions described above.

Proposed commit message:

```text
feat(wspm): complete and audit WSPM_PreTree baseline
```

B21.1 does not run `git add`, `git commit`, `git push`, `git reset`, or `git clean`.
