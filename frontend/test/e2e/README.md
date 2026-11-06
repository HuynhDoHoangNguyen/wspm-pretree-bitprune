# Browser E2E gates

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
