# WDM-06.1 — Persistence schema audit

Date: 2026-11-07. Checkpoint: `e0b75b12e943cdc1d0ecfb10cad2d2d841265465`.
Initial tracked/untracked Git status was clean. This is an audit deliverable,
not implementation of WDM-06.2–06.6 or approval of a schema change.
Owner accepted this audit in chat on 2026-11-07 with the documented AC09
verification exception. Acceptance closes the audit task; it does not turn the
unverified owner-data criterion into PASS.
Subsequent WDM-06.2 implementation follows the separately approved plan and
adds V4 metadata; see the WDM-06.2 section of
[the request contract](mining-request-contract.md#wdm-062--run-metadata-persistence).

WDM-06.3 subsequently completes the PATTERN entity mapping and introduces a
bounded explicit writer. The historical F01 reproduction test is evolved into a
complete mapping insert/readback test; the original 06.1 failure/evidence below
remains historical. See [the 06.3 contract](mining-request-contract.md#wdm-063--pattern-persistence).
The matrix and test below remain an explicitly pinned V2/V3 checkpoint, not an
assertion that V4 does not exist.
WDM-06.5 subsequently adds the explicit RUN_ITEM_STATISTIC writer and atomic
three-table staging. Owner approved the capture decision: PRODUCT name/SKU at
the first successful statistics-persistence transaction, frozen on retry. This
replaces the earlier unresolved run-time-label wording for current WDM rows;
there is no claim of engine-start-time labels or a capture timestamp column.
F06's counts/mapping/capture responsibilities are addressed by that seam;
complete publication/manifest and durable restart recovery remain separate.
See the WDM-06.5 section of the request contract for final verification status.
WDM-06.6 subsequently verifies the existing application policy against versioned
manual fixtures and same-run stored item/temporal lineage; it adds no runtime
calculator, schema or writer. The request contract's 06.6 result is authoritative
for current F03/F09 policy evidence. The printed-equation/worked-example R1
discrepancy remains unresolved; application verification does not resolve it.
**Status: PARTIAL.** The schema audit and isolated tests are complete; AC09's
owner-data non-mutation criterion is NOT VERIFIED because of the initial broad
regression's unintended owner-database smoke (documented below).

## Scope and evidence boundaries

The owned boundary is `MINING_RUN`, `PATTERN`, `PATTERN_ITEM` and
`RUN_ITEM_STATISTIC`. Snapshot/conversion/product code is inspected only to
trace provenance and foreign keys. No production code, migration, frontend,
dependency, owner process or environment setting is changed. The first broad
regression unexpectedly connected to the owner database through an existing
smoke test; the verification deviation below records the exact limits.

Runtime schema sources:

- [V2](../../backend/src/main/resources/db/migration/V2__complete_laptop_combo_schema.sql),
  definitions at lines 560–651; legacy conversion at lines 1274–1382.
- [V3](../../backend/src/main/resources/db/migration/V3__mining_run_execution_ownership.sql),
  additive execution ownership and lease constraint/index.
- [application.yaml](../../backend/src/main/resources/application.yaml):
  `classpath:db/migration`, Hibernate `validate`, SQL initialization disabled,
  Hibernate JDBC timezone UTC. Schema files under `docs/database/` are not runtime migrations.

The [audit integration test](../../backend/src/test/java/vn/edu/wspm/backend/persistence/PersistenceSchemaAuditIntegrationTest.java)
uses an owned MySQL 8.4 Testcontainer, executes actual Flyway V1 → V2 → V3,
validates history/checksums, and starts Spring/JPA with Hibernate `validate`.
SQL probe rows are explicitly synthetic constraint fixtures, not mined patterns.
No owner DB inspection is necessary for this source/isolated-schema audit.
The incidental owner smoke confirmed startup at V3 with no pending migration;
it does not verify the complete owner-data state or all 52 owner schema columns.

## Column matrix

Notation: N = NOT NULL; Y = nullable. Unless stated otherwise, there is no
non-null default. Nullable fields default to SQL NULL. `created_at` alone has
`CURRENT_TIMESTAMP(6)`; `pattern_id` alone is AUTO_INCREMENT. No audited column
has ON UPDATE behavior. SQL types below include unsignedness and precision.

### MINING_RUN — 29 columns

Mapping: [RunEntity](../../backend/src/main/java/vn/edu/wspm/backend/entities/RunEntity.java).
Writers: [RunCreationRepository](../../backend/src/main/java/vn/edu/wspm/backend/repositories/run/RunCreationRepository.java)
(C), [RunLifecycleRepository](../../backend/src/main/java/vn/edu/wspm/backend/repositories/run/RunLifecycleRepository.java)
(L). Reads include [RunStatusRepository](../../backend/src/main/java/vn/edu/wspm/backend/repositories/run/RunStatusRepository.java)
(S) and [MiningWorkspaceReadService](../../backend/src/main/java/vn/edu/wspm/backend/services/workspace/MiningWorkspaceReadService.java).

| Column | SQL type / nullable | Java mapping | Source / write-read status |
|---|---|---|---|
| run_id | VARCHAR(40) / N | String runId | C: server-generated run ID; S |
| snapshot_id | VARCHAR(40) / N | String snapshotId | C: locked verified publication; FK; S |
| algorithm | VARCHAR(32) / N | String algorithm | C: validated engine enum; S |
| status | VARCHAR(32) / N | String status | C QUEUED; L fenced lifecycle; S |
| dataset_source | VARCHAR(255) / N | String datasetSource | C snapshot sourceName; descriptive, not full provenance |
| minsup_mode | VARCHAR(32) / N | String minsupMode | C resolved mode; S |
| minsup_configured | DOUBLE / N | Double minsupConfigured | C original configured value; S |
| minsup_relative_fraction | DOUBLE / Y | Double minsupRatio | C resolved optional fraction; S; no forced zero |
| effective_threshold | DOUBLE / N | Double minsupAbs | C resolved real threshold; name is legacy; S |
| max_w | DOUBLE / Y | Double maxW | C PreparedRun.weights.maxWeight; required by current creation gate |
| min_w | DOUBLE / Y | Double minW | C PreparedRun.weights.minWeight; required by current creation gate |
| mean_w | DOUBLE / N | Double meanW | C global weight midpoint, not pattern weight; S |
| pattern_count | BIGINT UNSIGNED / Y | Long patternCount | L result count; S exact decimal string |
| candidate_count | BIGINT UNSIGNED / Y | Long candidateCount | L native algorithm counter; S exact string |
| dataset_io_ms | BIGINT UNSIGNED / Y | Long datasetIoMs | L worker verified input-file read scope; S |
| preprocessing_ms | BIGINT UNSIGNED / Y | Long preprocessingMs | L native engine scope; S |
| dfs_mining_ms | BIGINT UNSIGNED / Y | Long dfsMiningMs | L native engine scope; S |
| complete_algorithm_ms | BIGINT UNSIGNED / Y | Long completeAlgorithmMs | L native complete-algorithm scope; S |
| output_io_ms | BIGINT UNSIGNED / Y | Long outputIoMs | L explicitly NULL; persistence scope not implemented |
| total_execution_ms | BIGINT UNSIGNED / Y | Long totalExecutionMs | L worker compute/input scope; excludes future durable handoff |
| peak_sampled_jvm_heap_bytes | BIGINT UNSIGNED / Y | Long peakSampledJvmHeapBytes | L JVM-wide algorithm sample, not precise/run-private RAM; S |
| legacy_metadata_json | JSON / Y | **Unmapped** | V2 legacy metadata only; new creation omits it intentionally |
| error_message | VARCHAR(2000) / Y | String errorMessage | L safe failure code; S allowlisted diagnostics |
| created_at | DATETIME(6) / N | Instant runTime | C UTC Instant truncated to micros; DB default also exists; S UTC format |
| started_at | DATETIME(6) / Y | Instant startedAt | L DB UTC claim time, not earlier than creation; S |
| finished_at | DATETIME(6) / Y | Instant finishedAt | L DB UTC terminal time; S |
| execution_owner_id | VARCHAR(40) / Y | String executionOwnerId | C owned admission, L fencing; internal |
| execution_attempt_id | VARCHAR(40) / Y | String executionAttemptId | C owned admission, L fencing; internal |
| execution_lease_until | DATETIME(6) / Y | Instant executionLeaseUntil | C/L DB-UTC lease authority; internal |

`RunEntity.getSequenceCount()` and `getInputSnapshot()` are transient null
compatibility views, not usable lineage sources. There is no run sequence-count
column: use the verified publication/result resolution. Legacy constructor and
compatibility setters are not the accepted new-run creation path. No K/ranking
or alpha/beta/gamma/delta fields are introduced.

### PATTERN — 11 columns

Mapping: [PatternEntity](../../backend/src/main/java/vn/edu/wspm/backend/entities/PatternEntity.java).
[PatternRepository](../../backend/src/main/java/vn/edu/wspm/backend/repositories/PatternRepository.java)
only exposes the existing unpaged support/weight read. No production result
writer exists. V2 legacy import is not the new mining-output write path.

| Column | SQL type / nullable | Mapping | Required source / finding |
|---|---|---|---|
| pattern_id | BIGINT UNSIGNED / N, AUTO_INCREMENT | Long patternId | DB identity; signed range boundary F07 |
| run_id | VARCHAR(40) / N | ManyToOne RunEntity run | Exact originating run; FK |
| canonical_pattern | TEXT / N | **Missing** | Pattern.serialize(); F01/F02 |
| canonical_pattern_hash | CHAR(64) / N | **Missing** | Proposed SHA-256 over canonical UTF-8; F02 |
| support_abs | BIGINT UNSIGNED / N | Long support | Engine supports entry, unmodified |
| support_ratio | DOUBLE / N | **Missing** | support / verified numberOfSequences, floating division; F01 |
| weighted_support | DOUBLE / N | **Missing** | support × result.globalMeanWeight; F01/F09 |
| pattern_item_count | INT UNSIGNED / N | Integer patternLength | Pattern.length(), not itemsets count |
| itemset_count | INT UNSIGNED / N | **Missing** | Pattern.size(); F01 |
| pattern_weight | DOUBLE / N | Double weight | Selected application policy, not MeanW; F03 |
| pattern_weight_policy | VARCHAR(80) / N | **Missing** | EQ2_EQ3_ARITHMETIC_MEAN_V1 for new WDM results; F03 |

Six mandatory columns are not mapped. Existing PatternEntity constructor
cannot insert a valid new row: test reproduces MySQL 1364 for canonical_pattern
even though Hibernate validation succeeded. Existing nullable Java fields and
optional association annotations do not mirror every SQL NOT NULL constraint;
the database remains authoritative. The eventual writer may use complete JPA
mapping or explicit SQL, but must supply all required values.

### PATTERN_ITEM — 4 columns

Mapping: [PatternItemEntity](../../backend/src/main/java/vn/edu/wspm/backend/entities/PatternItemEntity.java)
and [PatternItemId](../../backend/src/main/java/vn/edu/wspm/backend/entities/PatternItemId.java).
[PatternItemRepository](../../backend/src/main/java/vn/edu/wspm/backend/repositories/PatternItemRepository.java)
orders reads by itemsetOrder then itemOrder. All columns are mapped; there is no
production result writer.

| Column | SQL type / nullable | Mapping | Source / obligation |
|---|---|---|---|
| pattern_id | BIGINT UNSIGNED / N | Long patternId; read-only association | Persisted parent identity; FK |
| itemset_order | INT UNSIGNED / N | Integer itemsetOrder | 1-based temporal position, not flattened item index |
| item_order | INT UNSIGNED / N | Integer itemOrder | 1-based ascending numeric item position within itemset |
| item_id | INT UNSIGNED / N | Integer itemId | Original mining item ID; positive WDM subset |

Composite ID property names/types match entity ID properties. The association
shares pattern_id with insertable/updatable=false. SQL does not enforce
contiguous positions, sorted/deduplicated items, canonical equivalence or run
dictionary membership (F05).

### RUN_ITEM_STATISTIC — 8 columns

Mapping: [RunItemStatisticEntity](../../backend/src/main/java/vn/edu/wspm/backend/entities/RunItemStatisticEntity.java)
and [RunItemStatisticId](../../backend/src/main/java/vn/edu/wspm/backend/entities/RunItemStatisticId.java).
All columns are mapped; @MapsId(runId) binds the embedded ID to its run. No new
WDM production writer/repository is present for these statistics.

| Column | SQL type / nullable | Mapping | Source / obligation |
|---|---|---|---|
| run_id | VARCHAR(40) / N | embedded String runId + run association | Originating run; FK |
| item_id | INT UNSIGNED / N | embedded Integer itemId | Verified mapping artifact; positive Java-int subset |
| product_id | VARCHAR(40) / N | ProductEntity association | Artifact item→product mapping, not guessed catalog order |
| occurrence_count | BIGINT UNSIGNED / N | Long occurrenceCount | Core ItemStatisticsCalculator on verified sequence DB |
| sequence_support | BIGINT UNSIGNED / N | Long sequenceSupport | Core sequence-level item support, not quantity/occurrences |
| item_weight | DOUBLE / Y | Double itemWeight | Core ItemWeightCalculator; non-null for new current WDM rows |
| product_name_snapshot | VARCHAR(200) / N | String productNameSnapshot | Data dictionary says name at run time; capture point/source unresolved F06 |
| sku_snapshot | VARCHAR(80) / N | String skuSnapshot | Data dictionary says SKU at run time; capture point/source unresolved F06 |

Legacy V2 imports deliberately use zero counts and NULL item weight with
LEGACY_UNVERIFIED runs and LEGACY_V1_WEIGHT patterns. Do not upgrade these rows
to verified current-policy results or recompute them from today's catalog.

## Keys, checks, defaults and application obligations

| Table | Keys and indexes | FK delete policy / checks |
|---|---|---|
| MINING_RUN | PK(run_id); idx_v2_run_snapshot_algorithm(snapshot_id,algorithm); idx_v2_run_status_time(status,created_at); idx_run_execution_lease(status,execution_lease_until) | snapshot_id→DATASET_SNAPSHOT; RESTRICT; algorithm allowlist; status allowlist; configured/effective ≥0; V3 ownership all-null or all-non-null |
| PATTERN | PK(pattern_id); UNIQUE uk_v2_pattern_run_hash(run_id,canonical_pattern_hash); idx_v2_pattern_run_rank(run_id,support_abs,pattern_weight) | run_id→MINING_RUN; RESTRICT; item/itemset counts >0; ratio within [0,1] |
| PATTERN_ITEM | PK(pattern_id,itemset_order,item_order) | pattern_id→PATTERN; RESTRICT; itemset_order, item_order, item_id >0 |
| RUN_ITEM_STATISTIC | PK(run_id,item_id); UNIQUE uk_v2_run_stat_product(run_id,product_id); idx_v2_run_stat_product(product_id) | run_id→MINING_RUN and product_id→PRODUCT; RESTRICT; no additional CHECK |

There is no cascade delete, cross-run dictionary FK, content/hash CHECK,
support≤sequenceCount CHECK, ratio/support consistency CHECK, policy allowlist,
nonnegative weight CHECK, publication marker or publication count/checksum.
SQL can accept zero-effective thresholds; the current application gate still
rejects them while R6 remains open. This audit executes no zero-threshold mining.

The test verifies exact column inventories, defaults/identity/precision,
index column ordering/uniqueness, five FK targets/delete policies and seven
enforced CHECK names, with behavioral negative/positive controls. These controls
show what SQL guarantees and where a future application writer must validate.

## Value lineage and research fidelity

1. [RunPreparationService](../../backend/src/main/java/vn/edu/wspm/backend/services/run/RunPreparationService.java)
   reads hash-verified raw/sequence/mapping artifacts, checks publication equality,
   resolves thresholds and computes item statistics/weights through core APIs.
   [PreparedRun](../../backend/src/main/java/vn/edu/wspm/backend/services/run/PreparedRun.java)
   carries engine input, resolved threshold, weights and publication/raw binding.
   Creation locks/rechecks the publication before writing a run.
2. [CoreMiningEngineAdapter](../../backend/src/main/java/vn/edu/wspm/backend/services/run/engine/CoreMiningEngineAdapter.java)
   returns [MiningEngineResult](../../backend/src/main/java/vn/edu/wspm/backend/services/run/engine/MiningEngineResult.java):
   full structural pattern→exact support map, resolved sequence count/threshold,
   distinct item count, global MeanW, native statistics and metrics. It does not
   return the product dictionary, product text snapshots or per-item weights/counts.
3. [ManagedRunExecutionAdmission](../../backend/src/main/java/vn/edu/wspm/backend/services/run/execution/ManagedRunExecutionAdmission.java)
   drops entry.input at completion; Outcome retains result/metrics but not the
   PreparedRun binding/weights. Future handoff must preserve a verified immutable
   context before dropping input, or re-read/revalidate the exact originating
   publication and raw manifest. Outcome alone is insufficient (F04/F06).
4. [ConversionArtifactCodec](../../backend/src/main/java/vn/edu/wspm/backend/services/dataset/conversion/ConversionArtifactCodec.java)
   mapping.json stores itemId/productId, not SKU/name. Its Verified result exposes
   database/statistics, not the dictionary. [ProductItemMappingStore](../../backend/src/main/java/vn/edu/wspm/backend/repositories/dataset/ProductItemMappingStore.java)
   enforces positive item IDs ≤Integer.MAX_VALUE. Use the published dictionary,
   not a newly allocated mapping or present-day catalog metadata.
5. [Data dictionary section 8.4](../database/laptop-combo-data-dictionary.md)
   describes name/SKU "at run time". Neither result nor mapping artifact captures
   that text or defines the exact capture point (admission, worker start or
   publication). [ConversionManifestVerifier](../../backend/src/main/java/vn/edu/wspm/backend/services/dataset/conversion/ConversionManifestVerifier.java)
   can reconstruct KEPT records with historical sale-time text, but that is a
   different provenance. 06.5 must specify/freeze run-time catalog capture or
   obtain an explicit decision to use historical text (and then define selection
   across differing orders). The audit does not silently choose either (F06).

Classification:

- **SOURCE-CONFIRMED via accepted repository research contract:** exact support
  counts sequences; item occurrences are separate; item weight derives from
  total occurrences/database length; global MeanW is the midpoint of max/min
  item weights; acceptance uses support×MeanW. See
  [WSPM_PreTree contract](../research/wspm-pretree-spec.md), sections 4.8 and 5.
  No paper formula is changed or independently re-accepted here.
- **REASONABLE-INFERENCE / application representation:** support_ratio uses
  floating support/N from the same verified result; canonical text reuses
  [Pattern.serialize()](../../mining-core/src/main/java/vn/edu/wspm/model/Pattern.java),
  preserving temporal itemsets and numeric item ordering. Proposed hash is
  lowercase SHA-256 of exact UTF-8 bytes, no BOM/newline. This is consistent with
  the V2 legacy text/hash convention but still requires producer fixtures and a
  collision/content check in 06.3. UTF-8 size must fit MySQL TEXT (65,535 bytes),
  without truncation; longer patterns require a separately reviewed decision.
- **OWNER-SELECTED APPLICATION POLICY:** EQ2_EQ3_ARITHMETIC_MEAN_V1 is chosen by
  the WDM checklist: average item weights within each itemset, then average
  itemset weights. It is distinct from weighted_support and is not claimed to
  resolve the paper's worked-example inconsistency. Its fixture is a 06.6 duty.
- **UNSPECIFIED / unresolved:** run-time SKU/name capture source/time (or an
  explicitly approved historical alternative), publication
  representation/recovery and full unsigned historical entity read policy.
  No substitute defaults or research behavior are introduced to fill these gaps.

Core permits item 0, whereas the commercial WDM dictionary and PATTERN_ITEM
require positive items. That is an established application-domain restriction,
not an algorithm change. Do not persist arbitrary research datasets by inventing
PRODUCT rows.

## Findings and owning tasks

Severity: BLOCKER = blocks the named later capability; MAJOR = correctness or
recoverability obligation; NOTE = compatibility/coverage boundary. These do not
mean the already accepted WDM05 gate failed.

| ID | Severity / evidence | Required handling and owner | Acceptance evidence for later task |
|---|---|---|---|
| F01 | BLOCKER: six PATTERN required columns unmapped; current JPA insert fails 1364 after Hibernate validate | 06.3 complete writer/mapping; do not insert placeholders | Actual persist/readback of all values |
| F02 | MAJOR: SQL unique hash does not bind hash to content; TEXT size is finite; existing read has no deterministic tie-break/pagination | 06.3 canonical UTF-8/hash/length/collision and idempotency rules; 06.7 bounded stable reads | Distinct temporal identities, retry, collision negative control, oversized text and pagination tests |
| F03 | BLOCKER: weight and policy NOT NULL at insert, yet policy task is scheduled after 06.3 | Establish/verify the 06.6 pure projection policy before first 06.3 production insert; stage 06.5 dictionary/stats context first; 06.1 does not implement/reorder checklist | Policy fixture and full insert without fake weight; document owner-approved dependency refinement |
| F04 | BLOCKER: no durable complete-publication state; SUCCEEDED means compute only; empty result ambiguous; RAM disappears on restart | 06.2 define publication manifest/marker and owned idempotent protocol before 06.3 integration; migrate only if separately approved | Empty/nonempty, rollback, commit-unknown, duplicate callbacks, crash/restart and stale owner tests |
| F05 | MAJOR: PATTERN_ITEM lacks run dictionary membership, no-gap/sorted/unique-within-itemset or canonical consistency enforcement | 06.4 validate complete structural projection, same-run dictionary, read ordering and canonical readback | Temporal round-trip/full maps; malformed/missing/wrong-run rows rejected |
| F06 | BLOCKER: dictionary only maps IDs; result lacks counts/weights/text; input binding is discarded; data dictionary says run-time text but capture source/time is undefined | 06.5 preserve/reverify exact context; freeze run-time name/SKU once at an agreed point; historical kept-record alternative needs explicit decision; 06.2 plan context lifetime | Changed catalog between stages/retries, historical vs run-time text distinction, corrupted/rebound publication, counts/FK fixtures |
| F07 | MAJOR for arbitrary historical reads: SQL UINT64/UINT32 exceed Long/Integer; legacy DTO exposes numbers | 06.2/06.3 explicit supported signed write bounds; 06.7 exact-string external reads/full-range policy. Current core supports int counts, long statistics; mapping store restricts IDs | Boundary/overflow rejection and exact >2^53 API tests; no silent narrowing |
| F08 | NOTE: legacy_metadata_json intentionally unmapped; transient sequenceCount/inputSnapshot getters are null | 06.2 distinguish accepted current path from legacy; retain legacy data, use verified publication for denominator/provenance | Legacy/current read tests; no false current-policy verification |
| F09 | MAJOR: SQL permits inconsistent supports/ratio/weights/counts and item statistics (including item_id=0); app must enforce them | 06.3/06.5 finite/bounded consistent values, same run N/MeanW; 06.6 policy lineage | Negative producer validation; do not strengthen baseline numeric comparator |
| F10 | MAJOR: output_io_ms is absent; total_execution_ms currently excludes persistence; lifecycle success writes output NULL | 06.2 define separate publication timing/provenance without overwriting WDM05 measurement meaning; 06.3 implement agreed measurement | Scope tests, null vs zero and retry accounting |

## Proposed handoff design boundary — not implemented or approved migration

WDM05 already writes configured/resolved support, weights, QUEUED/RUNNING/
SUCCEEDED/FAILED lifecycle, UTC timestamps, native counters and sampled heap.
06.2 should extend its provenance/publication contract rather than build another
run lifecycle. Keep existing status semantics intact.

Recommended later protocol:

1. Obtain the original result plus immutable/reverified run-bound dictionary,
   statistics, weights and raw/publication provenance. Confirm lifecycle ownership
   and binding; the old execution lease may expire after compute confirmation,
   so a later publication writer cannot blindly reuse the active RUNNING predicate.
2. Validate every projected value before writing; do not mine again as a fallback.
   Publish patterns, temporal rows and item statistics as one atomic unit for
   bounded outputs, or use staged batches with a final atomic manifest for larger
   outputs. Both alternatives need an explicit boundedness/visibility decision.
3. Mark a complete publication with expected counts and sufficient identity/
   checksum/policy provenance, including the legitimate zero-pattern case. Partial
   rows must not become consumable. Decide marker location and recovery before
   implementing: current four tables contain no adequate explicit marker.
4. On duplicate delivery or ambiguous commit, read back the exact publication
   identity and verify completeness/content. Preserve RAM/capacity until the
   durable outcome is confirmed; failure must not be converted into fake success.
5. Only then acknowledge the matching in-memory generation token. Existing
   acknowledge checks token and lifecycle confirmation, not durable patterns;
   consumer integration must establish that precondition. Lost RAM after restart
   cannot be reconstructed from the current tables alone. Define unavailable/
   incomplete-result behavior explicitly, rather than claiming restart recovery.

**Migration conclusion:** V2 already has the data columns for patterns, temporal
items and item statistics. F01/F02/F03/F05/F06/F09 require application work, not
an established need to alter those tables. Durable publication/recovery (F04)
may need an additive migration; the representation is not decided by 06.1.
Do not overload legacy_metadata_json or infer completion from COUNT(PATTERN),
SUCCEEDED or output_io_ms. No migration is created/applied by this audit.

**Dependency proposal:** 06.2 first resolves publication/context/timing; prepare
06.5 item context and the pure 06.6 policy fixture before 06.3's first full insert;
06.3/06.4/06.5 then integrate and round-trip the complete unit. This refines
implementation order without marking any later task complete or changing their
acceptance criteria. Owner decision remains required before adopting this proposal.

## Verification and acceptance

Environment: Java 21.0.12.1 (Oracle), Maven 3.9.16, Docker server 29.7.2,
MySQL image `mysql:8.4`. The test uses UTC connection/session settings and
test-only signing keys. No dependency is added. No performance benchmark or
new algorithm correctness acceptance is claimed.

Reproduction from repository root (PowerShell):

```powershell
mvn -pl backend -am '-Dtest=PersistenceSchemaAuditIntegrationTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -pl backend -am '-Dtest=*,!BackendApplicationTests' '-Dspring.config.import=optional:file:./.wdm-audit-no-owner-env.properties' '-Dspring.datasource.url=jdbc:mysql://127.0.0.1:1/wdm_audit_forbidden_owner' '-Dwdm.execution.enabled=false' test
git diff --check
```

First focused execution: **10 tests, 0 failures, 0 errors, 0 skipped**, exit 0.
Actual Flyway applied three migrations to a fresh database and validated all
three; Spring/Hibernate validation then succeeded. This is distinct from the
expected JPA insert failure asserted by the F01 regression test.
The expected-failure probe characterizes this checkpoint's incomplete entity;
06.3 must evolve it into a successful full insert/readback test when F01 is
fixed. Permissive-SQL probes are evidence of required producer validation, not
approval to publish those intentionally inconsistent fixture rows.

Local evidence (ignored, retained as acceptance evidence):

- `.agent-workspace/phase-wdm/WDM-06.1-20261107/FOCUSED.log`
- `.agent-workspace/phase-wdm/WDM-06.1-20261107/REGRESSION.log`
- `.agent-workspace/phase-wdm/WDM-06.1-20261107/REGRESSION-ISOLATED.log`

### Verification deviation — initial broad regression

The initially planned plain `mvn -pl backend -am test` included the pre-existing
`BackendApplicationTests.contextLoads`, which has no isolated datasource. At
21:01:00 +07:00 it imported owner `.env`, connected to
`127.0.0.1:3307/wspm_combo`, and initialized the existing execution configuration
because owner WDM_EXECUTION_ENABLED was true. This was missed during test-suite
preflight. Flyway reported current V3 and **no migration necessary**; the smoke
itself passed. No engine run/request was submitted by this smoke.

On discovery, only this invocation's verified Surefire JVM (PID 16232) was
stopped, leaving owner processes/settings untouched. The broad invocation thus
exited 1 due to intentional termination and is **not a passing full suite**.
Its log contains no recovered-execution-count notice; nevertheless there is no
before/after owner-row audit, so absence of owner data changes is **NOT VERIFIED**.
No corrective owner writes, schema operations or environment edits were made.

The replacement invocation excludes that one smoke, prevents owner `.env`
import, supplies a fail-closed default datasource on loopback port 1, and disables
default execution. Existing Testcontainers dynamic/test properties still supply
their isolated databases and explicit execution settings. The named optional
properties file is absent; no helper file is created. Do not count the excluded
smoke as part of this isolated regression. A separate test-isolation follow-up
should make the plain repository test command safe; it is not implemented here.

Final isolated regression: **mining-core 1185 PASS + backend 1144 PASS**, zero
failures/errors/skipped, exit 0, total 6m56s. The 10 audit tests passed again
within the backend run. One existing default-datasource smoke was explicitly
excluded, not silently counted as passed/skipped. Migration, creation,
lifecycle, metrics, legacy compatibility, workspace/read and other backend
regressions ran in this invocation; no frontend/browser tests were needed.

Static checks: audit relative links resolve; new files have no trailing
whitespace; `git diff --check` passes; production/core/frontend/migration diff
is empty. The only planned-scope expansion is a one-line `.gitignore` exception
for the approved new audit document, which would otherwise remain ignored.
The local checklist/evidence are intentionally ignored. No commit/push or
pre-existing file deletion is performed. Testcontainers removes its own newly
allocated resources; required evidence logs are retained.

| AC | Result | Evidence / limit |
|---|---|---|
| 06.1-AC01 | PASS | Clean initial status/HEAD, source inventory V1–V3, actual fresh Flyway migration/validation; incidental owner smoke distinguished from isolated evidence |
| 06.1-AC02 | PASS | 52-column matrix, exact live metadata/default/key/FK/check assertions |
| 06.1-AC03 | PASS | Every field has a traced source or explicit missing-source decision; F04/F06 identify context gaps |
| 06.1-AC04 | PASS | Exact support, ratio, weighted_support, application policy and six timing scopes remain distinct |
| 06.1-AC05 | PASS | F02/F05/F07 evaluate identity, structure, mapping and range limits; SQL probes show constraint boundaries; production round-trip remains later work |
| 06.1-AC06 | PASS | F03/F04/F06/F10 and proposed protocol identify dependency, empty-result, publication, timing and acknowledge decisions without inventing implementation |
| 06.1-AC07 | PASS, scoped | Focused 10/10; final isolated core 1185/backend 1144; one unsafe pre-existing smoke excluded explicitly |
| 06.1-AC08 | PASS | Ten findings with severity, source evidence, owner and later acceptance evidence |
| 06.1-AC09 | PARTIAL | No production/migration/core/frontend/env changes; owner data non-mutation NOT VERIFIED following unintended smoke; no migration executed there |
| 06.1-AC10 | PASS | Technical report records partial status/deviation; owner subsequently accepted the AC09 exception and the local task checkbox is checked |

Owner acceptance was subsequently given explicitly in chat, with the AC09
exception retained. At that acceptance checkpoint the owner requested a detailed
06.2 plan; implementation was subsequently authorized separately in chat as
recorded in the request contract. Owner migration deployment remains separate.
Isolating the existing default
smoke is a separate testing follow-up, not a hidden WDM-06.1 production edit.

