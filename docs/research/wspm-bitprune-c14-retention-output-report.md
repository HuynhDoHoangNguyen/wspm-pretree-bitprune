# C14 Research Report — Result Retention & Stable Output

**Repository:** `wspm-pretree-bitprune`  
**Task:** C14 — Result Retention & Stable Output  
**Sub-tasks:** C14.1 (Stable Output Projection), C14.1-R1 (Trailing-LF Remediation), C14.2 (Stable Output Writer), C14.2-R1 (Trailing-LF Remediation), C14 Retention Decision & Closure Audit  
**Branch:** `feature/wspm-bitprune`  
**HEAD:** `107b0d6bc84adc22e8dc6da2911617d8352dd011`  
**Status:** ACCEPTED  
**Operational Scope:** positive-effective-threshold operational scope; single Owner-approved `FINAL_SCIENTIFIC_RESULT` model  
**Research Issue Tracker:** R6 (`Zero Minimum Support Semantics`) remains OPEN; Java `double` exact-real comparator safety remains unresolved.

---

## 1. Task Result & Status

- **Sub-task C14.1 (Stable Output Projection Model):** `ACCEPTED`
- **Sub-task C14.1-R1 (Documentation & Trailing-LF Remediation):** `ACCEPTED`
- **Sub-task C14.2 (Stable Output Writer):** `ACCEPTED`
- **Sub-task C14.2-R1 (Trailing-LF Artifact Remediation):** `ACCEPTED`
- **Sub-task C14 Retention Decision & Closure Audit:** `ACCEPTED`
- **Parent C14 (Result Retention & Stable Output):** `ACCEPTED`

---

## 2. Scope & Authorization Boundary

The scope of C14 was authorized exclusively to define the normative result retention semantics and deliver a deterministic, stable file-output subsystem for `WSPM_BitPrune`.

- **Authorized Components:**
  - `vn.edu.wspm.bitprune.result.BitPruneOutputEntry`: immutable value record representing a projected pattern and its support.
  - `vn.edu.wspm.bitprune.result.BitPruneOutputProjection`: deterministic in-memory SPMF projection model.
  - `vn.edu.wspm.bitprune.io.BitPruneOutputWriter`: physical disk writer operating on `BitPruneOutputProjection`.
  - Retention Decision Verification: formalization of the single `FINAL_SCIENTIFIC_RESULT` model.
- **Strict Boundary Prohibitions:**
  - No modification to candidate generation, pruning rules, or mining traversal logic in `BitPruneMiner`.
  - No modification to baseline algorithms or Phase B classes.
  - No creation of synthetic retention policy enums or fake multi-policy switches.
  - No file I/O timing, execution sampler orchestration, CLI argument parsing, or console printing inside C14 components (all reserved for C15).
  - Parent C13 remains `PARTIAL` (scopes 1, 5, 6 pending C15 runtime orchestration); C15 and C16 remain `NOT STARTED`.

---

## 3. Owner-Approved Retention Decision

The Repository Owner formally evaluated and approved the normative retention model for `WSPM_BitPrune` during the C14 closure audit:

- **Normative Semantic Policy:** `FINAL_SCIENTIFIC_RESULT`
- **Nature of Policy:** This is a semantic documentation concept, not a Java enum, interface, or public policy switch.
- **Decision Rationale:**
  1. *PRISM Coupling Isolation:* Phase B's `ResultRetentionPolicy` (`FULL_ENCODING` vs `SUPPORT_ONLY`) was coupled to PRISM Prime-block encoding structures (`PrismBlockSequence`, `PrismSequenceDatabase`). Reusing it or porting that enum to BitPrune would introduce inappropriate historical baggage.
  2. *Sufficiency of Final Scientific Carrier:* `BitPruneMiningResult` already encapsulates all necessary data to satisfy scientific reproduction, pattern inspection, tree traversal, and differential testing against the baseline: `PrefixTree`, `acceptedPatterns`, exact `Map<Pattern, Integer>`, and `BitPruneStatistics`.
  3. *Rejection of Redundant Synthetic APIs:* Introducing multiple retention policies (such as a pseudo `SUPPORT_ONLY` enum alongside a default policy) without distinct internal retention behavior would represent a fake API.
  4. *Heap Retention Considerations:* Retaining full intermediate bitwise representations for every accepted pattern would retain additional heap objects whose quantity grows with the retained state. The magnitude of the heap impact has not been measured, so no quantitative or comparative memory-efficiency claim is made.

---

## 4. Retained Final Scientific Data

A completed mining run encapsulated in `BitPruneMiningResult` (or inside `MeasuredBitPruneRun.result()`) retains only immutable, scientifically essential objects:

1. **Frozen `PrefixTree prefixTree`:** The tree structure containing the root and all accepted patterns inserted as child nodes, frozen against further mutations.
2. **Immutable `List<Pattern> acceptedPatterns`:** The list of all accepted weighted sequential patterns preserved in deterministic PrefixTree pre-order traversal.
3. **Immutable `Map<Pattern, Integer> supports`:** The exact integer support snapshot for each accepted pattern, wrapped via `Collections.unmodifiableMap(...)`.
4. **Immutable `BitPruneStatistics statistics`:** The 12-counter accounting snapshot tracking mining lifecycle metrics and satisfying conservation invariants.

---

## 5. Transient Structures Not Retained by Final Result

The mining lifecycle constructs temporary bitwise, positional, and index structures to evaluate candidates. None of the following are referenced by `BitPruneMiningResult` or `MeasuredBitPruneRun`:

- `SidBitmap` instances (singleton, candidate, and parent bitmaps);
- `PatternEndpoints` value objects;
- `VerificationResult` evaluation records;
- `VerticalOccurrenceIndex` structures;
- `CmapI` and `CmapS` extension matrices;
- DFS recursion stack frames and intermediate candidate lists;
- `ExecutionMemorySampler` instances.

---

## 6. Rejected and Deferred Retention APIs

The following designs were explicitly rejected or deferred:

- `BitPruneRetentionPolicy` enum: **REJECTED** (avoids artificial API layers).
- `FULL_ENCODING` retention: **REJECTED** (unnecessary for benchmark and mining validation).
- `SUPPORT_ONLY` retention enum: **REJECTED** (the single result model already acts as the definitive scientific carrier).
- `BitPrunePatternState`: **REJECTED** (avoids heavy per-node state retention).
- Reuse of Phase B `ResultRetentionPolicy`: **REJECTED** (preserves clean separation from PRISM).
- `FULL_AUDIT_STATE`: **DEFERRED** to future research work as a dedicated offline auditing capability.

---

## 7. C14.1 — Stable Output Projection

Component: `vn.edu.wspm.bitprune.result.BitPruneOutputProjection`  
Supporting Entry: `vn.edu.wspm.bitprune.result.BitPruneOutputEntry`

- **Separation of Concerns:** Pure in-memory transformation of `BitPruneMiningResult`. Contains zero file system or disk I/O code.
- **Ordering Guarantee:** Traverses `BitPruneMiningResult.acceptedPatterns()` directly. Because `acceptedPatterns()` is populated in deterministic PrefixTree pre-order, the projection preserves this exact deterministic order.
- **Line Formatting:** Each pattern is formatted according to standard SPMF sequence syntax:
  ```text
  pattern.serialize() + " #SUP: " + support
  ```
- **Line Separator & Encoding:** Content is composed strictly using Unix LF (`\n`, `0x0A`). No carriage return (`\r`, `0x0D`) is emitted. Byte encoding responsibility is delegated to the output writer.
- **Empty Output Representation:** For an empty mining result (0 accepted patterns):
  - `entries()` returns an unmodifiable empty list (`Collections.emptyList()`).
  - `entryCount()` returns `0`.
  - `content()` returns `""` (empty string).
  - `byteLength()` returns `0L`.
- **Trailing LF:** When non-empty, `content()` terminates with exactly one LF character (`\n`). There is no double LF at the end.

---

## 8. C14.2 — Stable Output Writer

Component: `vn.edu.wspm.bitprune.io.BitPruneOutputWriter`

- **Separation of Concerns:** Pure disk I/O utility. Contains no projection logic, timing instrumentation, or console output.
- **API Signature:**
  ```java
  public void write(Path outputPath, BitPruneOutputProjection projection) throws IOException
  ```
- **Null Safety:** Validates both `outputPath` and `projection` with `Objects.requireNonNull(...)`.
- **Parent Directory Creation:** Automatically creates non-existent parent directories using `Files.createDirectories(parent)` if `parent != null`.
- **Open Options:** Uses `StandardOpenOption.CREATE`, `StandardOpenOption.TRUNCATE_EXISTING`, and `StandardOpenOption.WRITE`. Replaces existing files by overwriting from byte 0.
- **Encoding Guarantee:** Uses `StandardCharsets.UTF_8` explicitly via `Files.writeString(absoluteOutput, projection.content(), StandardCharsets.UTF_8, ...)`. Emits no byte-order mark (BOM).
- **Empty Output File:** Writes zero bytes (0-byte file) when `projection.entryCount() == 0`, flushing the buffer without writing any characters.
- **Resource Management & Exceptions:** Resource lifecycle is managed internally by Files.writeString. The stateless writer retains no file handle after the call completes, and IOException propagates unchanged.

---

## 9. Deterministic Ordering & Exact Output Syntax

- **Ordering Contract:** The output sequence follows the deterministic PrefixTree pre-order traversal (`acceptedPatterns()`). It is not an arbitrary HashMap iteration.
- **Distinction from Lexicographic Order:** The ordering is strictly tree pre-order (F1 roots in canonical order, followed recursively by depth-first child extensions). It must not be referred to as "lexicographic canonical" without clarification, as tree pre-order traverses extensions depth-first before returning to sibling branches.
- **Syntax Compatibility:** The output lines match SPMF sequence format:
  ```text
  item_1 item_2 ... -1 item_k ... -1 -2 #SUP: support_count
  ```
  This guarantees 100% interoperability with baseline outputs and differential comparison tools.

---

## 10. Empty-Output Behavior

Both projection and writer handle empty pattern sets cleanly and predictably:

- `BitPruneOutputProjection.from(emptyResult)` yields `entryCount() == 0` and `content() == ""`.
- `BitPruneOutputWriter.write(emptyProjection, path)` produces a zero-byte file on disk.
- File size on disk is exactly `0` bytes.
- The SHA-256 hash of an empty output file is `E3B0C44298FC1C149AFBF4C8996FB92427AE41E4649B934CA495991B7852B855`.

---

## 11. UTF-8, LF, and Trailing-LF Behavior

- **Explicit Charset:** `StandardCharsets.UTF_8` is hardcoded in the writer, preventing platform-default encoding differences on Windows, Linux, or macOS.
- **No BOM:** Output contains no UTF-8 Byte Order Mark (`0xEF 0xBB 0xBF`).
- **Strict LF:** Lines are delimited exclusively by `\n` (`0x0A`).
- **Single Trailing LF:** Non-empty output files end with exactly one `0x0A`.
- **Code Remediation:** Sub-tasks C14.1-R1 and C14.2-R1 remediated repository artifact issues to ensure source code files end with exactly one trailing newline, eliminating spurious hash mismatches.

---

## 12. Direct Truncate-and-Write Limitation

`BitPruneOutputWriter` writes directly to the destination path using `CREATE` and `TRUNCATE_EXISTING`.

- **No Atomic File Swap:** The writer does not write to a temporary file (`.tmp`) followed by an atomic rename (`ATOMIC_MOVE`).
- **No Automatic Rollback:** If an I/O exception occurs midway through writing (e.g., disk full, permission failure), the destination file may remain in a partially written or truncated state.
- **Baseline Parity:** This direct-write behavior is identical to Phase B's `WspmPreTreeOutputWriter`.

---

## 13. Memory and Lifecycle Implications

The lifecycle of BitPrune mining structures is strictly isolated from the output carrier:

- **Formal Lifecycle Statement:**
  > “Các cấu trúc tạm thời không được `BitPruneMiningResult` hoặc `MeasuredBitPruneRun` giữ tham chiếu. Khi quá trình chạy hoàn tất và không còn tham chiếu bên ngoài, chúng đủ điều kiện để Garbage Collector thu hồi.”
- **Important Lifecycle Limits:**
  - There is no guarantee that Garbage Collection occurs immediately upon completion of mining.
  - Callers holding external references to intermediate collections would prevent GC reclamation.
  - No quantified memory-saving claim (e.g., "saves X MB" or "reduces heap by Y%") is asserted, as no comparative multi-policy benchmark was conducted in Phase C.

---

## 14. Test Evidence

Testing across C14 is divided strictly into focused subsystem tests, closure-compatibility tests, and full regression.

### 14.1. Output-Subsystem Focused Tests (43 tests)
Focused tests covering projection and physical writing:
- `BitPruneOutputProjectionTest`: **23 tests**, 0 failures, 0 errors, 0 skipped.
- `BitPruneOutputWriterTest`: **20 tests**, 0 failures, 0 errors, 0 skipped.
- **Total Output Subsystem:** **43 tests PASS**.

### 14.2. Closure-Compatibility Suite (69 tests)
Integration and regression checks verifying mining result encapsulation, projection, writing, and measured execution parity:
- `BitPruneOutputWriterTest`: **20 tests**
- `BitPruneOutputProjectionTest`: **23 tests**
- `BitPruneMiningResultTest`: **14 tests**
- `BitPruneMeasurementIntegrationTest`: **12 tests**
- **Total Compatibility Suite:** **69 tests PASS** (0 failures, 0 errors, 0 skipped).

### 14.3. Full Regression Suite (1,073 tests)
Executed across the entire repository:
```bash
mvn -q test
```
- **Surefire XML Reports:** **73 reports**
- **Total Tests Run:** **1,073 tests**
- **Failures:** **0**
- **Errors:** **0**
- **Skipped:** **0**

---

## 15. Protected Hashes Verification (17/17)

Before and after C14 closure documentation, all 17 protected artifacts were verified via SHA-256:

### C12 (5 artifacts):
- `8428D040A11555E7F5E578EE2DF66AC385319BA2717791CD0F6C4AD18FE2C65C`  
  `src/main/java/vn/edu/wspm/bitprune/statistics/BitPruneStatistics.java`
- `00ED3E8B66F6C7AED75C978E0F184C977BC0CB9A71FD1C776CFF5E12FF80DAD3`  
  `src/main/java/vn/edu/wspm/bitprune/mining/BitPruneStatisticsAccumulator.java`
- `177004247777BF45E79969A5E1EDED6008649A0F11897A58B9E50B04B6086998`  
  `src/main/java/vn/edu/wspm/bitprune/result/BitPruneMiningResult.java`
- `E7D669A945563704DC29D5F54BDC287E4F517DB8C84A916CB091937D5204D189`  
  `src/test/java/vn/edu/wspm/bitprune/mining/BitPruneMiningStatisticsIntegrationTest.java`
- `C63D9B8B8D7034A74A74556F2E15EF2118D414A0562FB5D41311F62610671416`  
  `docs/research/wspm-bitprune-c12-statistics-accounting-report.md`

### C13 (7 artifacts):
- `72D5BE3F543EBE0D6BBABEA109F46299BA6216954016CD95997B9CD401411D5D`  
  `src/main/java/vn/edu/wspm/bitprune/measurement/BitPruneAlgorithmMetrics.java`
- `716F7CD44048EDB9991DD2B91EF47AAA441FE9563D84ED353294093E09642F8D`  
  `src/main/java/vn/edu/wspm/bitprune/measurement/BitPruneExecutionMetrics.java`
- `09B84670909DACA281B05CDE4B265CDCD30AC74794C9C16EB26DDBAAAF203EE0`  
  `src/main/java/vn/edu/wspm/bitprune/mining/BitPrunePreparedInput.java`
- `62DB26298A5BE31212570C0E2D71E0256209FE3A5E74A919F4091B4658312676`  
  `src/main/java/vn/edu/wspm/bitprune/mining/BitPruneMiner.java`
- `05C1DF2BB6455226B988E7792BDFEBCA5A4AF7F62F8368311DA4E8B9348FF181`  
  `src/main/java/vn/edu/wspm/bitprune/mining/MeasuredBitPruneRun.java`
- `1694D5C91C67AFAE3F0744C321CEB1EAFD21273F09C66BFD370FB43F18E3E467`  
  `src/test/java/vn/edu/wspm/bitprune/mining/BitPruneMeasurementIntegrationTest.java`
- `66627727C41DFCCDF127E16716CD91440A2FD698F89FCEC8A406DC0A677AB525`  
  `src/test/java/vn/edu/wspm/measurement/BitPruneExecutionMemoryLifecycleTest.java`

### C14.1 (3 artifacts):
- `F72511770ADC28CC0BBA713F65DA251891B0596221B18726CFE2554A86B1DA45`  
  `src/main/java/vn/edu/wspm/bitprune/result/BitPruneOutputEntry.java`
- `A3B40329F7A80C6CDDD634F4EB82E17C82442BF9109470D793C70FDA06D17F6A`  
  `src/main/java/vn/edu/wspm/bitprune/result/BitPruneOutputProjection.java`
- `213614FC4FEF1A8A5D515B5822CEDD59328179DEACAE31DE295B94A20939EC1C`  
  `src/test/java/vn/edu/wspm/bitprune/result/BitPruneOutputProjectionTest.java`

### C14.2 (2 artifacts):
- `92F328E24DB149002CD8FDB1B720B1744743DFF34C7C9B6AE645C37510D387DF`  
  `src/main/java/vn/edu/wspm/bitprune/io/BitPruneOutputWriter.java`
- `F6B3A948BA0B76E6C99E5A482EFA07D1EEB2FAB8CD8858AFF9BD6CDB626D1417`  
  `src/test/java/vn/edu/wspm/bitprune/io/BitPruneOutputWriterTest.java`

**Result:** **17/17 MATCH (100%)**.

---

## 16. Research Fidelity Classification

### SOURCE-CONFIRMED:
- Sequential pattern representation format (`-1` itemset delimiter, `-2` sequence delimiter).
- Prefix-tree based candidate expansion principles.

### REPOSITORY-CONFIRMED:
- Output pattern syntax: `pattern.serialize() + " #SUP: " + support` and `#SUP:` label (confirmed by `BitPruneOutputEntry`, `BitPruneOutputProjection`, and C14.1/C14.2 tests).
- `BitPruneMiningResult` fields and structural immutability invariants.
- Exact support snapshot and frozen `PrefixTree` state.
- Deterministic tree pre-order projection via `BitPruneOutputProjection`.
- UTF-8 file creation, parent directory auto-creation, and truncate-on-write semantics in `BitPruneOutputWriter`.
- All 17 protected SHA-256 hashes and 1,073 passing regression tests.

### OWNER-APPROVED IMPLEMENTATION POLICY:
- Single normative `FINAL_SCIENTIFIC_RESULT` retention model.
- Explicit rejection of synthetic `BitPruneRetentionPolicy` enum and fake switches.
- Deferral of `FULL_AUDIT_STATE` to future research scope.

### REASONABLE-INFERENCE:
- Transient bitwise structures become eligible for JVM Garbage Collection once mining terminates and caller references are released.
- Frozen `PrefixTree` traversal provides consistent, deterministic output ordering.

### UNSPECIFIED / FUTURE WORK:
- Offline full-candidate audit trace (`FULL_AUDIT_STATE`).
- Comparative empirical heap benchmarks between retention models are deferred to future research or benchmark work; no specific phase is assigned by C14.

---

## 17. Known Limitations

1. **Direct Truncate-and-Write:** No atomic file swapping or automatic rollback upon partial I/O failure.
2. **JVM GC Dependency:** Physical heap reclamation timing is non-deterministic and depends on the JVM Garbage Collector.
3. **Operational Threshold Limitation:** Mining is scoped to positive effective thresholds; R6 (`Zero Minimum Support Semantics`) remains OPEN.
4. **Floating-Point Representation:** Java `double` exact-real comparator safety remains unresolved.
5. **Git Line-Ending Warnings:** Three known repository files (`PHASE_C_WSPM_BitPrune.md`, `BitPruneMiner.java`, `MainWspmPreTree.java`) produce Git LF→CRLF warnings on Windows.

---

## 18. C13 / C14 / C15 Ownership Boundaries

| Responsibility / Component | Owner Task | Status |
|---|:---:|:---:|
| Algorithm-scope metrics (Preprocessing, DFS, Complete Algorithm) | C13 | **ACCEPTED** |
| Execution-scope metrics (Dataset I/O, Output I/O, Total Execution) | C13 / C15 | **PARTIAL** (Pending C15) |
| Normative Result Retention Model (`FINAL_SCIENTIFIC_RESULT`) | C14 | **ACCEPTED** |
| In-Memory Deterministic Output Projection (`BitPruneOutputProjection`) | C14 | **ACCEPTED** |
| Physical Disk Writer (`BitPruneOutputWriter`) | C14 | **ACCEPTED** |
| Standalone Java Main CLI & Configuration (`BitPruneConfig`) | C15 | **NOT STARTED** |
| Execution-level Sampler Orchestration & Checkpoints | C15 | **NOT STARTED** |
| Console Logging & Experiment Summaries | C15 | **NOT STARTED** |

---

## 19. Final Acceptance Statement

Task **C14 — Result Retention & Stable Output** is fully **ACCEPTED**.

All sub-tasks (C14.1, C14.1-R1, C14.2, C14.2-R1, and the Retention Decision Audit) are verified and closed. Result retention semantics are locked to the Owner-approved `FINAL_SCIENTIFIC_RESULT` model, output projection and writing are decoupled and verified, 17/17 protected hashes match, and 1,073 full-regression tests pass cleanly.

---

## 20. Recommended Next Task

- Proceed to **C15 Readiness Audit & Standalone Main/Configuration Contract Draft**.
- Do **NOT** implement C15 automatically. Wait for formal Repository Owner authorization.

