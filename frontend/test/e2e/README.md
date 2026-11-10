# Browser E2E gates

The client-combo extension of the Native gate verifies explicit approval/publication
of supported kinds, anonymous detail/latest reads, protected HISTORY_PREFIX_V1
recommendations using synthetic COMPLETED orders linked to real registered users,
checkbox selection, URL-backed filters, desktop/mobile and a held real response
delivered after logout. Mining/Combo results still come from the real pipeline.
SQL does not insert Pattern/Combo results. Evidence remains under the runner's
unique directory; development accounts/orders are untouched.

## Native WDM-06 → Combo generation/review gate

Build current frontend into a fresh `BUILD_PATH`, compile/package backend, set
`WDM_E2E_BUILD_DIR`, then run from repository root:

```powershell
$env:TV2_NATIVE_COMBO_GATE='1'
$env:WDM_ENGINE_REGRESSION='0'
node frontend/test/e2e/engine-run.mjs
```

This mode reuses the real Engine E2E runner with owned MySQL/Flyway V1–V5, shared
Auth/CSRF, current application build and production business policy. It uploads
`native-combo.csv`, validates/converts, runs both engines and publishes through
native WDM services. No Pattern or Combo is seeded. Explicit synthetic catalog
uses the approved category IDs. UI preview writes no batch; generation persists
all validated groups beyond K, then native review checks current catalog/evidence.
The gate tests stable retry/reconciliation, two Admin contexts, principal audit,
protected mining readback and restart with execution disabled. It is fixture
correctness evidence, not live rollout or performance acceptance.

Evidence is retained under ignored `.agent-workspace/phase-wdm/TV2-NATIVE-COMBO-*`.
If an existing serving JVM holds the backend JAR, do not replace/stop it implicitly.
`WDM_E2E_BACKEND_JAR` can point to an owned Boot JAR copy updated with all current
compiled `BOOT-INF/classes` by JDK `jar --update`. Dependencies/core remain from
the Maven package; runner verifies the nested core hash. Record this packaging
method and JAR/source hashes; it is separate from a fresh Maven release package.
No source changes may be made while a gate runs (the runner compares inventories).

After WDM-06 integration, `/admin/patterns` and its detail route belong to the
published-result pages. The retained TV2 filter Explorer uses
`/admin/pattern-explorer` and `/api/v1/admin/pattern-runs/...`; its fixture runner
follows those separate routes. The WDM publication endpoints remain unchanged.
The TV2 migration-review candidates still require version/schema reconciliation
with production V3/V4 before their old disposable schema harness can be reused.

TV2 Pattern/Combo fixture gate: `node frontend/test/e2e/tv2-run.mjs` from root.
It uses a test-classpath backend, actual application build and owned disposable
MySQL with V1–V4. It does not use the running demo server or fake HTTP responses.
See [NEXT-04 manual setup and mining handoff](../../../docs/research/TV2-NEXT-04_manual_and_mining_handoff.md)
for compilation/classpath prerequisites, optional manual mode, expected results
and cleanup. Its fixture acceptance is separate from real mining acceptance.

## WDM-06.12 Final Result E2E gate

From the repository root (PowerShell), use a fresh build directory and compile
the production package plus test helpers, without `clean`:

```powershell
$env:BUILD_PATH='../.agent-workspace/phase-wdm/WDM-06.12-build-' + [guid]::NewGuid().ToString('N')
$env:CI='false'
npm --prefix frontend run build
mvn -B -pl backend -am package -DskipTests
$env:WDM_E2E_BUILD_DIR=$env:BUILD_PATH.Substring(3)
$env:WDM_FINAL_RESULT_GATE='1'
Remove-Item Env:WDM_FINAL_RESULT_CONTROLS_ONLY -ErrorAction SilentlyContinue
$env:WDM_ENGINE_REGRESSION='1'
node frontend/test/e2e/engine-run.mjs
```

`WDM_FINAL_RESULT_GATE=1` selects `final-result-cases.mjs`. Unset it to run the
independent 05.10 engine or 06.11 publication modes. Each invocation creates a
new evidence directory, labelled MySQL 8.4 container, private storage and Java
processes; it does not change owner `.env`, database, or running application.
Only the invocation's owned runtime/container/temp files are cleaned. Evidence
is retained on failure and success; no credentials or tokens are exported.
For a focused control-development run only, set `WDM_FINAL_RESULT_CONTROLS_ONLY=1`
and `WDM_ENGINE_REGRESSION=0`. This skips the full matrix/multipage/restart checks,
labels RESULT scope `FOCUSED_CONTROLS_ONLY`, and cannot satisfy the final AC gate.
Unset `WDM_FINAL_RESULT_CONTROLS_ONLY` for the final full run.
If Windows locks an existing backend JAR, package to a fresh owned output directory
and set `WDM_E2E_BACKEND_JAR` to that workspace-relative JAR path and
`WDM_E2E_BACKEND_TEST_CLASSES` to its compiled test-classes directory. The runner
still extracts and verifies the actual packaged core bytes and helper allowlist;
it does not stop an owner process to replace a locked artifact.

Preparation uploads/validates/converts snapshots through real APIs. All six tiny
matrix runs (two engines times ABSOLUTE 0.1 / PERCENT 14 / PERCENT 100) and both
multipage runs, plus both numeric-item-order regressions, are created through the
real Admin form and HTTP 202. Original
input, complete maps and raw-double metadata are captured atomically in the
test-only transparent delegate before return. Serial call indices bind each
capture to its actual request/run; retained-Outcome polling is not used.
`FinalResultE2EVerifier.java` reuses the accepted core oracle bridge and the exact
core bytes extracted from the backend JAR. It is not production code.

`backend/src/test/resources/wdm/csv-v1/multipage.csv` is a synthetic CSV-v1
correctness fixture, not acquired transaction data. Customers MP-A and MP-B each
have orders at 00:00:01/02/03.000000Z on 2026-11-01; their product itemsets are
`<(mp-p1 mp-p2)(mp-p1 mp-p3)(mp-p2)>`. One order remains one itemset; each customer
remains one sequence. There are at most 4*4*2 subsequence choices per sequence.
The focused core test independently enumerates the expected full map and requires
more than 20 patterns. The gate checks the converted structure explicitly:
after the tiny fixture allocates IDs 1..3, these products use global IDs 4..6.
No mined output is seeded and no threshold is adjusted to obtain pagination.

`numeric-item-order.csv` is an independent hand-authored synthetic regression:
ten customers, each with one order containing one distinct product. All ten
singleton patterns have support 1 at ABSOLUTE 0.1. After the earlier fixtures,
global item IDs are 7..16, crossing 9/10. The original query sorted the decimal
CHAR alias (`10,11,...,7,8,9`) instead of the numeric column and publication
rejected the manifest. The corrected query orders by `s.item_id`. Both engines'
original/direct/oracle maps and every downstream layer must still agree across
restart. This does not alter numeric comparisons, mining, schema or manifest
version; it restores the contract's existing numeric item ordering.

The gate compares every DB temporal coordinate, every API and rendered UI
page/detail, all item statistics, actual UI downloads and the production Java
provider. It repeats all reads across real JVM restart and execution-disabled
restart, checks historical catalog snapshots and hashes mining/result/Combo
tables to reject read-side writes (Auth lifecycle writes are excluded). Each DB
corruption uses a separate genuine run and is never repaired. Copied artifacts
exercise the independent verifier separately. Edge network throttling changes
timing only for real request/download cancellation on run change, logout and
role loss; it does not fabricate HTTP success responses.

`PREPARATION.json`, `FLYWAY.tsv`, pre-return captures, `*-ORACLE.log`, full
`<runId>-<phase>.json` comparisons, screenshots and `RESULT.json` identify each
layer. `REGRESSION.log` exists only with `WDM_ENGINE_REGRESSION=1`. A browser PASS
does not itself close AC11/12 or grant owner acceptance: run frontend regression,
TypeScript, targeted lint and build separately, then review the contract AC table.
This is correctness evidence, not a performance benchmark or owner rollout.

## WDM-06.11 publication gate

Build the frontend to a fresh task-owned BUILD_PATH, package the reactor with
`mvn -B -pl backend -am package -DskipTests` (keep test helper compilation), then
set `WDM_E2E_BUILD_DIR` to that build and `WDM_RESULT_PUBLICATION_GATE=1` before
running `node frontend/test/e2e/engine-run.mjs`. Set `WDM_ENGINE_REGRESSION=1` to
run the full core/backend suite on this gate's disposable datasource. Owner DB,
.env and owner processes are untouched. The harness now expects Flyway V1–V4.

This mode enables the actual publication consumer and records each original
engine map inside the transparent test-only engine delegate before returning it.
It verifies both engines against DB/browser-download full maps, published
Explorer/detail navigation, capacity reuse through a third run, empty output,
actual JVM restart, anonymous export denial and post-publication corruption.
No original Outcome polling race is needed. Helpers stay outside production JARs.
Evidence lives in a unique ignored WDM-06.11 directory with RESULT/PREPARATION,
Flyway history, original maps, downloaded JSON, logs and desktop/mobile images.
Credentials/tokens are never written into evidence. Observations are correctness
evidence, not performance measurements.

## WDM-05.10 real Engine E2E Gate

Use existing pinned dependencies, JDK 21, Maven, Node, Docker/MySQL 8.4 and
Microsoft Edge. From repository root, build to a **fresh** output directory:

```powershell
$env:BUILD_PATH='../.agent-workspace/phase-wdm/WDM-05.10-build-' + [guid]::NewGuid().ToString('N')
$env:CI='false'
npm --prefix frontend run build
mvn -B -pl backend -am package -DskipTests
$env:WDM_E2E_BUILD_DIR=$env:BUILD_PATH.Substring(3)
$env:WDM_ENGINE_REGRESSION='1'
node frontend/test/e2e/engine-run.mjs
```

`-DskipTests` still compiles the test-only helpers; do not use
`-Dmaven.test.skip=true` or `clean`. A normal production build retains existing
lint warnings; strict `CI=true` currently rejects unrelated legacy warnings.
The regression flag runs the full core/backend suites serially on this gate's
owned datasource after browser cases. Without the flag, that regression is
not executed and must be reported separately. Run frontend regression/type/lint
checks separately; the runner never changes dependency versions.

This runner serves the actual CRA build over loopback HTTPS with Secure cookies
and real shared Auth. A new labelled disposable MySQL starts empty and receives
actual Flyway V1, V2, V3 and Hibernate validation. It uses neither the earlier
definitions-only dataset bootstrap nor a mocked Auth/HTTP success harness.
Owner `.env`, database, running processes and storage are not modified.

Production classes/dependencies are extracted from the exact backend JAR.
Only four explicitly allowed E2E helper classes, compiled from backend test
sources outside production component scan, are added to that application's
classpath. No JUnit/test-library classpath or public observation route is added.
The original retained result is read non-destructively in the same JVM using a
bounded, atomically published run-ID inbox. A transparent test-only engine
delegate captures actual structural input and supports a bounded first-call
latch. It always calls the real engine; it never substitutes results or metrics.
Neither helper nor the core oracle bridge may occur in production JARs.

Eight base browser groups cover the real CSV upload/validation/conversion and
downloads, both engines at ABSOLUTE 0.1/PERCENT 14/PERCENT 100, queued-artifact
tamper, loss of an actual committed 202, and zero/underflow refusal. Each JVM
retains at most two runs; each positive pair also proves a third POST gets 503
without insertion. JVMs restart between batches; no automatic acknowledgement
or durable pattern storage is introduced. Hidden tabs are brought forward for
UI assertions because the real client deliberately pauses background polling.

Every successful original full canonical `Pattern.serialize() -> exact support`
map/count is compared against both direct engines and the existing independent
tiny-database oracle. The separate Java verifier uses byte-identical packaged
core code, confirms downloaded publication hashes, actual engine input structure,
configured/resolved raw double bits and per-run native metrics vs DB/API/UI.
Copied support/structural/deletion/one-ULP corruption controls must fail. Any
unexpected mining disagreement stops the gate; never adjust thresholds or core
to make it pass. R6 stays open; zero controls never enter an engine.

Evidence goes to a unique ignored `.agent-workspace/phase-wdm/WDM-05.10-*`
directory: `PREPARATION.json` (source/build/helper/tool hashes), `FLYWAY.tsv`,
`RESULT.json`, downloaded synthetic artifacts, complete `.map.tsv`/`.properties`
captures, verifier/negative-control/service logs and desktop/mobile screenshots.
Original files and failed attempts remain for review. No passwords, JWTs, cookie
jars or Auth storage dumps are written. Private staging/TLS/storage is removed
only after absolute-path ownership checks; only this invocation's labelled
container and spawned JVM/browser are stopped. Timing/heap observations under
test instrumentation are correctness evidence, not performance benchmarks.

The real gate also covers the actual Admin shell's run-detail route. The former
mocked smoke entry did not include that shell and could not catch its missing
run-detail allowlist; `src/App.test.tsx` now provides a focused regression.

The workspace follow-up extends this same real gate with snapshot rediscovery
after URL-filtered refresh, current SPMF/mapping storage metadata, denial of all
four new read endpoints to anonymous clients, newest-first run history, and
full retained capacity without release. It captures storage/history desktop/
mobile and full-capacity screenshots. Select the "Tạo dữ liệu mới" tab before file-picker
upload in the library. The extra group gives eight browser groups plus the
optional ninth reactor-regression group. Timestamp decoders accept live Instant
nanoseconds as well as persisted DATETIME microseconds. Legacy display/ID
limitations are covered by focused MySQL and frontend tests, without manually
seeding mining results or changing the real browser acquisition fixture.

## WDM-05.9 metrics UI extension (mocked HTTP)

From repository root:

```powershell
node frontend/test/e2e/execution-ui-smoke.mjs --metrics
```

This preserves the six execution UI groups and adds four metrics groups: both
verified algorithm identifiers/native-counter labels, historical unknown scopes,
missing versus measured zero/output absence, and stale metrics on failed GET.
Synthetic values test full unsigned precision and presentation, not mining output
or benchmark performance. One request produces one status/metrics snapshot;
the panel must not issue its own GET.

The flag retains separate evidence in a fresh `.agent-workspace/phase-wdm/WDM-05.9-browser-*` directory,
including measured desktop/mobile/legacy screenshots and RESULT.json. The default
05.8 mode/output path remains supported. Both modes use fixture identity in the
real client/store/guard and intercepted HTTP, not a real backend/Auth/DB gate.

## WDM-05.8 browser UI smoke (mocked HTTP)

Run from repository root with existing pinned E2E dependencies installed:

```powershell
node frontend/test/e2e/execution-ui-smoke.mjs
```

The test-only entry bundles actual Admin configuration/detail routes, guard,
hooks and shared API client with esbuild. It installs a synthetic identity in
the real in-memory store and intercepts HTTP through Playwright; this does not
verify real backend/shared-Auth/database integration or replace WDM-05.10.
No production test hook, dependency update, real account or owner DB is used.

Six groups cover SUPPORTED create/double click, queued/running/succeeded polling,
direct refresh GET only, GET error retaining the last snapshot, lost POST without
replay and CUSTOMER guard. Desktop/mobile screenshots (including a 390px
horizontal-overflow check) and RESULT.json are retained under ignored
`.agent-workspace/phase-wdm/WDM-05.8-browser-*`. Every invocation preserves prior evidence. The local server uses an ephemeral
loopback port; browser/server are closed after the run, with no artifact deletion.

## WDM-04.6 configuration gate

Dataset mode now runs **25 cases: the existing 17 plus 8 configuration groups**.
`configuration-cases.mjs` uses the actual Admin form, strict four-field POSTs,
verified conversion artifacts, shared Auth and isolated MySQL. It never serves
the Auth harness or introduces production test hooks. New cases cover both
algorithms/modes, precision/zero/underflow, real errors and artifact tamper,
double-click/no-replay behavior, edit/navigation/logout cancellation and unchanged
snapshot/mapping/file data with no mining run.

Use a task-owned build directory to preserve existing build artifacts:

```powershell
# From repository root; use a fresh task-owned output path for another task.
$env:BUILD_PATH='../.agent-workspace/phase-wdm/WDM-04.6-build'
npm --prefix frontend run build
mvn -B -pl backend -am package -DskipTests
$env:WDM_E2E_BUILD_DIR='.agent-workspace/phase-wdm/WDM-04.6-build'
$env:AUTH_E2E_LOG_DIR='.agent-workspace/phase-wdm/WDM-04.6'
npm --prefix frontend/test/e2e run test:dataset
```

CRA resolves BUILD_PATH against `frontend`; the runner resolves WDM_E2E_BUILD_DIR
against repository root, canonicalizes it, checks workspace containment and
requires index.html. Without the override, frontend/build remains the default
for compatibility. This is not a deployment command. Packaging with skipTests
is preparation only. Use existing pinned E2E dependencies when already installed;
do not clean older evidence, builds or dependency directories as preparation.

Each run retains PREPARATION.json with Node/Java versions, Git HEAD (the working
tree may be dirty) and hashes of served index/manifest/backend JAR. RESULT.json
contains safe configuration IDs/counts/hashes, request counts and verifier status.
It never saves credentials, access tokens, browser auth storage or wire headers.
The shared directory prefix remains WDM-01.8; the combined gate label includes
WDM-04.6 and the optional parent above groups evidence by task.

`java/MiningThresholdVerifier.java` is a test-only JDK 21 source launcher. It
parses the actual browser-downloaded SPMF and compares captured wire tuples
against MinimumSupportResolver, using exact double bits. It also checks that
the core JAR on its classpath is byte-identical to the nested core JAR in the
running backend package. Its TSV input and downloaded bytes exist only in the
run-owned temporary directory and are cleaned by the runner. Two negative
controls alter a captured threshold and the expected sequence count and must
exit 1 after the valid input has exited 0.

The gate captures the actual JSON number sent. In particular, JSON serializes
JS -0 as 0; raw form input -0.0 is still checked as an operational zero rejection.
No JavaScript threshold formula substitutes for core. Routes used for races
fetch real backend responses and then hold/drop/release those bytes. No fake
success payload grants a PASS.

Native final run 2026-11-07: **25/25 PASS**, 57 core-verified tuples and 2 rejected
negative controls, Edge 156.0.4314.58, JDK 21.0.12.1, Node v22.23.4, isolated
MySQL 8.4/HTTPS. Parser/resolver focused suite: 23/23 PASS. Desktop/mobile
configuration screenshots were inspected; mobile width 390 has no horizontal
overflow. Evidence and AC details are in docs/wdm/mining-request-contract.md.
Actual run creation/execution, other browser engines, production capacity and
Flyway upgrade behavior are outside this gate.

## Shared setup and earlier gates

This directory contains test source, separate from `src` and the application
bundle. Dependencies are pinned in this directory's package/lockfile; no React,
CRA or application dependency upgrades are required.

Requirements: Node 22+, JDK 21 (including keytool), Maven, Docker with MySQL 8.4,
and Microsoft Edge installed. Commands below run from the repository root:

```powershell
npm --prefix frontend/test/e2e ci
mvn -B -pl backend -am package -DskipTests
npm --prefix frontend run build

# Actual built application: login -> Dataset export -> upload -> detail -> raw.
npm --prefix frontend/test/e2e run test:dataset

# Separate Auth UI/session/concurrency gate and optional CRA health proxy check.
npm --prefix frontend/test/e2e test
```

Packaging with `-DskipTests` is preparation, not test evidence. The frontend build
and backend jar must match current source. Never prepare this gate by starting
the application against development MySQL. Keep `.env` and credentials private.

`dataset-run.mjs` selects independent Dataset cases through the shared
`run.mjs` infrastructure. Dataset mode serves the actual application build and
has no Auth harness endpoint or injected application hooks. Auth mode retains
its test-only harness for session races; it is never part of the production bundle.

Each run creates a container identified by both its ID and task label, a fresh
database, random credentials/TLS key and an OS temporary directory. Raw storage
is explicitly set to that directory, outside the repository. The Dataset gate
uses a **4096-byte file / 8192-byte multipart request limit** to test overflow
cheaply; this does not establish the default 100 MiB capacity. V2 CREATE/ALTER
definitions bootstrap the isolated schema and Hibernate validates it; this is
not a Flyway V1-to-V2 upgrade test. Fixtures are synthetic order history; no
snapshot is inserted directly. Each SQL call rechecks container ownership.

Dataset cases cover exact period boundaries, historical values, an order-line
ID beyond JavaScript's safe integer range, actual browser file selection and
downloads, fresh SQL/file/SHA-256 checks, null statistics, reload/deep URL,
authorization/transport rejection, missing/tampered raw, duplicate submit and
logout while a real export response is delayed. Delayed-response cases use
Playwright routing to hold real backend responses; they do not fabricate success.

Per-run evidence is preserved under `.agent-workspace/phase-wdm/<gate>-<suffix>`
(or an `AUTH_E2E_LOG_DIR` parent). `RESULT.json` holds safe Dataset case results,
IDs, hashes and byte counts. No browser auth state, passwords, token dumps,
traces or downloads are saved there. Service logs remain ignored local evidence.
The browser suite has a ten-minute deadline; browser operations and CLI commands
also have finite timeouts. Failures exit nonzero. Missing prerequisites fail the
gate instead of becoming skipped successes. Auth's optional CRA proxy check
reports NOT VERIFIED when port 8080 is already occupied and preserves its owner.

Cleanup closes only this run's browser/processes, checks container ID/label before
stopping it, and checks the unique OS-temp directory before removing it. It never
cleans development storage or older evidence. A forcibly killed runner can leave
owned resources for manual inspection; do not use broad container/volume cleanup.

Version reusable test source, package/lockfile, instructions and small synthetic
fixtures. Ignore dependencies/builds, generated reports/traces/downloads,
browser credential state and secrets. Evidence should be a controlled CI artifact
or ignored local output, with a safe summary recorded in the Dataset contract.

The former `frontend/e2e/` directory moved here at the owner's request. Historical
reports may reference its previous location; use the commands above for current runs.

WDM-02.6 extends Dataset mode with three validation browser cases, keeping the
seven existing Dataset cases. The combined RESULT.json gate is labeled
`WDM-01.8 + WDM-02.6`; its evidence directory retains the existing WDM-01.8 prefix.
No separate Auth harness or injected application API is served in this mode.
Cases cover a single bodyless POST, duplicate-click exclusion, committed DB
report/status, metadata refresh, GET-only reload, raw hash preservation and
recovery by GET after deliberately losing a real committed POST response.
Additional flows exercise INVALID UTF-8/empty/unassignable-order outcomes,
nullable counters and decoder accuracy. Snapshots still originate only through
the real upload API; only synthetic commercial fixtures are used.

Each run packages/serves the current backend and UI build; packaging with
skipTests is preparation only. Validation lifecycle is the real accepted backend
implementation, not mocked responses. Delayed/aborted browser responses are
test-only transport controls. This does not certify all browsers or establish
production capacity.

WDM-02.7 adds two API cases through the same real shared Auth/HTTPS/browser
session and disposable MySQL, bringing Dataset mode to twelve cases. The combined
label is `WDM-01.8 + WDM-02.6 + WDM-02.7`. Anonymous/customer requests are denied
before invalid input lookup. PENDING, INVALID and a deliberately corrupted
VALID-only status are rejected with 409. The sole direct snapshot SQL update is
that negative corruption fixture; every snapshot originates from actual upload.
Positive admission uses actual upload -> validation -> conversion POST, checks
the explicit 503 "Conversion is not available yet.", and verifies unchanged
metadata/raw and no artifacts beyond raw.csv on repeated requests. A same-size
raw tamper then produces sanitized admission 503 without changing validation.
Successful conversion/SPMF remains NOT VERIFIED and belongs to WDM-03.

WDM-03.6 supersedes the temporary eligible-admission 503 assertion above. Dataset
mode remains twelve cases, now labeled `WDM-01.8 + WDM-02.6 + WDM-02.7 + WDM-03.6`.
The real browser login/shared-Auth API flow converts a validated export, checks
SUCCEEDED/COMPUTED statistics, GET status, both downloads' exact bytes/hash/size,
fixed attachment headers and one-attempt repeated POST behavior against SQL/files.
Anonymous/customer denials cover all read/download paths before malformed input.
The former pre-claim raw-tamper assertion is preserved on another actual uploaded/
validated snapshot; tampered published mapping then denies status, both downloads
and repeated POST without metadata repair. No conversion button or UI test hook
is introduced. Conversion UI and final WDM-03.8 E2E remain later work.

WDM-03.7 supersedes the preceding UI limitation and adds three cases (15 total),
with combined label ending in `WDM-03.6 + WDM-03.7`. The production snapshot
detail page validates then converts real uploaded/exported bytes; the button is
disabled before bound validation and duplicate clicks cannot issue another POST.
The gate checks all nine displayed statistics, on-demand SPMF preview against
the published file, both actual browser downloads' exact name/bytes/size/hash,
SQL/raw immutability and GET-only reload. Another actual committed POST response
is lost deliberately; UI recovers through verified GET without replay. Logout
while a real artifact response is delayed must suppress the browser download.
Routing only delays/aborts real responses; no conversion UI harness is injected.

The >1 MiB preview refusal, stream overflow cancellation (including absent or
false Content-Length), escaping, 20-sequence/4000-character bounds and polling
deadline are frontend unit/integration checks. The browser gate's existing small
upload limit is unchanged and does not establish large-file or production
capacity. No mining-core round-trip acceptance is claimed; WDM-03.8 stays open.

WDM-03.8 adds a sixteenth Dataset case and supersedes the preceding round-trip
limitation. It reuses `backend/src/test/resources/wdm/csv-v1/round-trip{,-shuffled}.csv`
and the independently authored `round-trip-expected.json`. All snapshots originate
from real file-picker UI uploads, validation and conversion. Shared synthetic
catalog/registry setup deliberately retains an existing product ID and a gap in
item IDs. Both actual browser downloads are compared with published files/hash/
size; mapping is checked against the registry, including exclusion of a product
from a rejected order. The same expected nine statistics and cleaning counts
are checked in UI and API, alongside raw/report preservation and idempotency.

The browser case launches the maintained test-only
`java/SpmfRoundTripVerifier.java` with JDK 21 source launching and
`mining-core/target/classes` as its classpath. Build the reactor before the gate
as above; missing Java/classes/fixtures or any verifier failure fails the gate.
The verifier uses the unchanged core `SpmfSequenceParser` on the **actual browser
download path** and compares every SID/itemset position/item ID. It receives a
separate tiny expected-layout file: comma-separated numeric items, semicolons
between itemsets, one sequence per line. Node only resolves hand-authored product
membership through the verified mapping and sorts item membership numerically;
it does not parse SPMF or derive customer/order layout from production output.

Three wrong expectations with identical sequence/item/itemset totals must exit
1: swapped customer sequences, wrong temporal order, and exchanged basket versus
temporal boundaries. Original/shuffled source CSVs must produce identical
canonical SPMF/mapping under the same registry. The fixtures contain 3 sequences,
6 itemsets, 8 occurrences and 3 products, including Unicode ordering, microsecond
timestamps, opaque equal-time order IDs, quantity >1, repeated-product membership,
an exact duplicate row and whole-order rejection. These are small synthetic
correctness fixtures, not acquired datasets or capacity/performance evidence.

`RESULT.json.roundTrip` records safe source/artifact hashes, sizes, actual parser
verification, statistics and negative-verifier results. No new production
endpoint, parser, mining algorithm, threshold policy or Auth harness is added.

The UI/UX refresh after WDM-03.8 keeps those sixteen Dataset cases and adds a
seventeenth visual/responsive flow. Both gates now expect `/admin` as the Admin
landing page. Dataset login follows the real guide's Start link; snapshot cases
select visible workflow tabs and expand checksum details through the UI. These
changes retain every authorization, duplicate/lost-response, integrity and core
round-trip assertion, including the Java verifier on actual downloaded bytes.

The extra case checks seven guide steps and unavailable combo actions, upload's
next-step deep link, shared Admin/customer account views and zero POSTs on tab
switches. It checks the table's colored/sticky header and local scrolling at
390x844, and captures seven synthetic screenshots in the unique evidence folder:
Admin home, upload receipt, Admin account, snapshot desktop/mobile/dark and client
account. These are review evidence, not screenshots of development/user data or
a claim of complete WCAG/cross-browser certification. Auth mode retains its
separate session-race harness and optional CRA proxy check.

Run-detail header polish keeps these fixture-only smoke modes. It adds a grouped
summary, compact run ID with exact full-URL copying, readable UTC+07 update time
(raw UTC retained in the time element), and dataset/history navigation buttons.
The smoke also intercepts read-only workspace capacity/dataset/storage requests;
these fixtures do not prove real backend availability. Desktop/mobile screenshots
in the fresh evidence directory cover the header and unchanged metrics rendering.
`header-desktop.png` and `header-mobile.png` are cropped summary previews;
`completed*.png` includes dataset navigation and the full detail page.
