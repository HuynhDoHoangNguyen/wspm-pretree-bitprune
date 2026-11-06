# WDM-01.1 — Dataset/CSV contract v1

Date: 2026-11-04. Status: WDM-01.1 accepted by owner on 2026-11-04.
Contract ID: `wdm-order-csv-v1`. This is an application data contract, not a
mining formula or an implementation of export, upload, validation or conversion.

## Authority and decisions

Schema authority is the runtime
[Flyway V2](../../backend/src/main/resources/db/migration/V2__complete_laptop_combo_schema.sql).
The [data dictionary](../database/laptop-combo-data-dictionary.md) establishes
UTC `DATETIME(6)`, historical prices, non-PII exports and COMPLETED-only input.
WDM-00's accepted API/security conventions remain applicable: Admin namespace,
raw success DTOs, UTC wire dates, exact integer strings, no public storage paths,
no-store downloads, and multipart part `file`.

The owner explicitly selected **ordered_at** on 2026-11-04 for both period
selection and order chronology. This resolves the difference between protected
WDM_CHECKLIST.md section WDM-03.3 (`ordered_at`) and the data dictionary's
`completed_at` chronology description. For this WDM pipeline, this explicit
decision takes precedence. Neither protected WDM documents nor the dictionary
are rewritten. `completed_at` must not be silently substituted.

All other choices below are engineering decisions under the authorized plan;
they are not paper-derived rules. Frozen mining algorithms, support, weights,
thresholds, R6 and product-to-mining-item mapping remain unchanged/unresolved
under their existing contracts.

## Row and column contract

One record represents one historical ORDER_ITEM, not one order or one product.
Do not aggregate, deduplicate or expand quantity during export/upload. An order
may contain several lines with the same product. IDs are opaque text except
order_item_id, which is an exact positive decimal integer string. Never route
BIGINT through JavaScript Number or money through binary floating point.

The exact, case-sensitive header and order are:

```text
order_id,order_item_id,customer_code,ordered_at,status,product_id,sku_snapshot,product_name_snapshot,quantity,list_unit_price,allocated_discount_amount,line_total,currency_code
```

All 13 fields are required and nonempty. No extra, missing, renamed, duplicate
or reordered columns are accepted by the later structural parser.

| Column | Source | Value contract |
|---|---|---|
| order_id | ORDERS.order_id | Opaque string, at most 40 characters |
| order_item_id | ORDER_ITEM.order_item_id | Decimal digits, no sign/leading zeros, 1..18446744073709551615 |
| customer_code | CUSTOMER.anonymous_code via ORDERS.customer_id | Stable pseudonymous grouping key, at most 80 characters |
| ordered_at | ORDERS.ordered_at | UTC ISO-8601, exactly six fractional digits and Z on canonical export |
| status | ORDERS.status | Exactly COMPLETED for usable commercial input |
| product_id | ORDER_ITEM.product_id | Opaque string, at most 40 characters; catalog reference, not mining item ID |
| sku_snapshot | ORDER_ITEM.sku_snapshot | Historical string, at most 80 characters |
| product_name_snapshot | ORDER_ITEM.product_name_snapshot | Historical string, at most 200 characters |
| quantity | ORDER_ITEM.quantity | Decimal integer, 1..4294967295; no sign/leading zeros |
| list_unit_price | ORDER_ITEM.list_unit_price | Nonnegative DECIMAL(19,2), historical list price per unit |
| allocated_discount_amount | ORDER_ITEM.allocated_discount_amount | Nonnegative DECIMAL(19,2), discount for the entire line |
| line_total | ORDER_ITEM.line_total | Nonnegative DECIMAL(19,2), historical line total |
| currency_code | ORDERS.currency_code | Three uppercase ASCII letters, e.g. VND; no implicit currency conversion |

Opaque strings are not trimmed, case-folded or Unicode-normalized. Required
text must not be whitespace-only. Anonymous codes are pseudonyms, not a promise
of irreversible anonymization: no name, email, phone, address, user ID or raw
customer ID is exported. Imported customer_code denotes the same stable customer
within the declared source; do not infer identity from a person's name.

Export reads stored line amounts and SKU/name snapshots, never current PRODUCT
price/SKU/name. No fallback to current catalog values is allowed for missing
historical fields. Decimal wire values use a dot and exactly two fractional
digits, at most 17 integer digits, without grouping/exponents/signs. Later
business validation checks quantity > 0, discount <= price * quantity, and
line_total = price * quantity - discount using exact decimal arithmetic. Do not
recompute a stored line_total during export. Shipping/tax/order-level amounts
are outside these line amounts. Different currencies are preserved, not summed
or interpreted as mining weights. Missing history is a data-quality failure,
not permission to drop a line silently.

## Period selection and deterministic output

GET `/api/v1/admin/order-exports` reserves exactly `fromTime` and `toTime` as
required filters. Both are ISO-8601 timestamps with explicit offsets, precision
at most microseconds, normalized to UTC. Require fromTime < toTime; reject
missing/naive/malformed timestamps, excessive precision and unknown filters
with 400 VALIDATION_FAILED. Do not truncate or round precision silently.
No client-selected status, timestamp field, sort or pagination in this version.

Selection is `status = 'COMPLETED' AND ordered_at >= fromTime AND ordered_at <
toTime`. The period is **[fromTime, toTime)**. UI date-only selection later maps
local midnight boundaries through an explicit display timezone; it never adds
23:59:59 or assumes the machine timezone. DATETIME(6) stores UTC wall-clock
components: convert an offset input to Instant, then UTC LocalDateTime for a
query; attach UTC on read. Never attach the host timezone. A real JDBC/DB
round-trip test is required in WDM-01.2; existing data's compliance has not been
verified by this document. Mixed/unknown-zone legacy data must be reported
before export, not reinterpreted from guessed offsets.

Eligibility means current status at the consistent export read, not status as
of toTime. A completed order placed in the period remains eligible even if its
completion falls later. RETURNED/REFUNDED/CANCELLED and all other statuses are
excluded. Historical status reconstruction and future backtesting are outside
v1. A row set must come from one consistent read in WDM-01.2, not ad hoc reads
that can mix mutations. Re-export after commercial changes can differ; persist
the acquisition time and freeze the raw snapshot bytes.

Canonical row ordering is customer_code ASC, ordered_at ASC, order_id ASC,
order_item_id ASC. Strings use case-sensitive Unicode code-point order without
locale folding; order_item_id uses numeric order. WDM-01.2 must establish this
ordering independently of MySQL's default collation. For each customer, orders
with equal ordered_at use order_id as a reproducible tie-break, not a claim of
observed sub-microsecond chronology. All lines of an order remain one order;
same-timestamp different orders are not merged. Source order-item IDs remain
distinct even if their products match.

## CSV bytes and upload boundary

Canonical exports: UTF-8 with exactly one leading BOM (`EF BB BF`) so Excel's
direct-open path recognizes Vietnamese correctly; comma delimiter; CRLF record separator;
one final CRLF; quote fields containing comma, double quote, CR or LF; double
embedded quotes. Preserve embedded field newlines and all text values. Empty
export is the BOM followed by the header plus CRLF, with no fabricated data record.
This supersedes the original BOM-free export choice following the owner's
Excel encoding bug report on 2026-11-05. Existing BOM-free CSV fixtures remain
valid upload inputs; the export tests prepend the expected three-byte signature.

Upload accepts UTF-8 with an optional single leading UTF-8 BOM, CRLF or LF
record separators and standard quoted fields, including embedded newlines.
The header stays exact after BOM removal. Other encodings, invalid UTF-8,
unclosed quotes and incorrect record width are structural errors for WDM-02.
No automatic delimiter detection, spreadsheet type conversion, trimming or
formula-prefix mutation. This is a data exchange CSV; text beginning with =,
+, - or @ remains text in the artifact, not a spreadsheet-safe transformed
variant. Consumers must import text as text. Only canonical timestamp/decimal
forms above are accepted in CSV v1; offset normalization is for API filters.

WDM-01.4 transport contract: multipart/form-data with one nonempty `file` part,
case-insensitive .csv suffix; accepted part media types text/csv,
application/csv, application/vnd.ms-excel or application/octet-stream (also
permit absent part media type). MIME/suffix are transport hints, not proof of
valid CSV. Header-only CSV is nonempty and may be retained as PENDING; a zero-byte
file is rejected. Missing/empty part is 400, disallowed media/suffix is 415,
oversize is 413, all using the accepted error envelope. Final configurable
file/request byte limits and multipart overhead are owned by WDM-01.4; no
limit is claimed to be enforced here. Business/structural row failures belong
to WDM-02's persisted report, not an upload-time VALID result.

Downloads use text/csv; charset=UTF-8, attachment with a server-generated name,
and Cache-Control: no-store. Original upload names are display metadata only;
the backend generates storage names and never resolves a client path. Admin
authorization is required before business processing/file access. Runtime
Auth/401/403 evidence remains BLK-01 OPEN.

## Raw snapshot lifecycle and provenance

WDM-01.5 persists immutable original bytes and SHA-256 (64 lowercase hex),
not a parsed/reserialized approximation. A BOM/newline change changes the raw
checksum. Uploading an exported file creates source_type CSV; do not pretend
its origin has been verified as DATABASE. A future direct DB acquisition uses
source_type DATABASE only when performed by the backend itself.

Initial values:

| Field | Initial meaning |
|---|---|
| source_type | CSV for upload; DATABASE for trusted direct acquisition |
| source_name | Safe nonblank display/provenance label, <=255 characters; never a storage path |
| from_time / to_time | Trusted acquisition filter bounds for direct DB acquisition; null for upload unless a later API explicitly records unverified declared bounds |
| validation_status | PENDING, including uploads produced by the application's export |
| raw_file_path / raw_sha256 | Internal generated artifact reference / SHA-256 of exact committed bytes |
| created_at | Actual server acquisition Instant stored as UTC, explicitly initialized |
| conversion_report_json | Structured lifecycle/provenance object below, not a fabricated validation report |
| sequence_count | Internal 0 = no converted sequences materialized; never a computed dataset cardinality before conversion |
| sequence/mapping paths and checksums | null |
| All nullable derived statistics | null |

Initial JSON shape persisted by WDM-01.5:

```json
{
  "contractVersion": "wdm-order-csv-v1",
  "conversionStatus": "NOT_STARTED",
  "statisticsStatus": "NOT_COMPUTED",
  "provenance": {
    "acquisition": "CSV_UPLOAD",
    "sourceTimeZone": null,
    "timeField": "ordered_at",
    "statusPolicy": "COMPLETED_CURRENT_AT_ACQUISITION",
    "rawSizeBytes": "1234"
  }
}
```

rawSizeBytes is actual measured bytes as a decimal string, never the example
value. For trusted DB acquisition, acquisition is DATABASE_EXPORT,
sourceTimeZone is UTC, and persisted from_time/to_time carry normalized bounds.
CSV_UPLOAD denotes that the origin and business content remain unverified;
timeField/statusPolicy identify the required contract, not successful validation.
Optional safe original filename may be recorded in provenance, bounded and
sanitized without revealing paths. Size/hash/creation metadata are actual
observations, not values trusted from the CSV or client.

The internal sequence_count = 0 accommodates V2 NOT NULL without a migration:
public sequenceCount is **null while statisticsStatus != COMPUTED**, even if
validation later succeeds. Only successful conversion may expose the actual
decimal-string count, including a genuine computed zero if later conversion
policy permits it. Mining must require successful conversion, not test a
placeholder count. This explicitly documented storage convention is not a
claim that the unvalidated dataset is empty. No VALID or converted state is
assigned by upload. Validation reports may evolve later, but raw bytes,
checksum and acquisition provenance must not be rewritten in place.

## Executable fixtures and acceptance

Fixtures are in
[backend/src/test/resources/wdm/csv-v1](../../backend/src/test/resources/wdm/csv-v1).
All data is synthetic. `valid.csv` covers multiple customers/orders/lines,
same-product lines, historical text, quotes/comma/embedded CRLF, exact decimals,
timestamp ties and BIGINT beyond JavaScript's safe range. `empty.csv` is a
header-only export. `business-invalid.csv` is structurally readable but contains
unknown product, order-level conflict and a repeated line identity.
`cases.json` records expected observations, catalog references, eligibility
inputs/bounds, normalized offsets and future business failures. It is not seed
data and must not be loaded into a live database.

Run the offline contract-fixture checks from repository root:

```powershell
python backend/src/test/resources/wdm/csv-v1/verify_contract.py
```

This standard-library checker checks fixture bytes/shape, typed values, declared
expected ordering/eligibility and failure examples. It is not a production CSV
parser, repository query, cleaning pipeline or proof of HTTP/DB integration.
WDM-02 must reuse these examples in tests of its real implementation. Unknown
products, conflicting rows within an order and repeated line IDs must be
reported; v1 deliberately does not decide cleaning, order-discard or merge
policy (BLK-02 remains open for those later decisions). Same product on distinct
line IDs is not a duplicate line. No fixture claims a mined result.

Acceptance for WDM-01.1: exact sourced columns; explicit COMPLETED/current-state
policy; owner-selected ordered_at; UTC mapping and half-open bounds; deterministic
ordering and CSV bytes; honest initial snapshot semantics; reproducible fixture
checks. Real repository/DB timezone round-trip, endpoint responses, upload limits,
persist/reload, parser/cleaning behavior, browser/Auth E2E and conversion remain
NOT VERIFIED and belong to WDM-01.2 onward. No backend/frontend runtime code,
applied migration, protected local document or mining implementation was changed
by WDM-01.1.

## WDM-01.2 implementation and verification boundary

`OrderExportRepository.findCompletedInPeriod(OffsetDateTime, OffsetDateTime)`
implements the query boundary with Spring JDBC and a dedicated immutable
`OrderExportRow` projection. One parameterized SELECT reads ORDERS, CUSTOMER
and ORDER_ITEM inside a read-only REPEATABLE_READ transaction; the returned
list is immutable and completely materialized before the transaction ends.
No per-order queries or current PRODUCT fields are read. Missing lines on an
eligible order fail the read rather than silently dropping the order.

Bounds are normalized to UTC LocalDateTime and bound through JDBC without
java.sql.Timestamp conversion; DATETIME values are read as LocalDateTime and
attached to UTC. Inputs reject null, reversed/equal periods, sub-microsecond
precision and UTC values outside MySQL DATETIME years 1000..9999. The later
HTTP layer must map these invalid bounds to the contract's 400 error envelope.
BIGINT UNSIGNED uses BigInteger; quantity uses long; prices use BigDecimal;
the later CSV/API layer must preserve exact textual representations.

Binary UTF-8 ordering implements Unicode code-point order, including
supplementary characters and case differences, without default-collation
folding. Status is checked exactly as COMPLETED, including a binary equality
guard against case-insensitive or space-padding comparisons. These are
engineering details preserving the accepted v1 contract.

Reproduce focused repository integration tests (Docker required):

```powershell
mvn -B -pl backend -am '-Dtest=OrderExportRepositoryTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

The test uses Boot-managed Testcontainers and disposable mysql:8.4, creates
the 45 actual V2 CREATE TABLE definitions plus the two final FK ALTERs, then
inserts synthetic test data. It does not execute Flyway migrations, legacy
rename/copy/drop statements, application seed scripts or writes against the
development database. Assertions cover every projected field, historical
values, period/status boundaries, order time versus completion time, exact
unsigned values, binary/Unicode ordering, empty periods, invalid inputs,
missing lines, non-UTC JVM/session microsecond round-trip and repeatable reads
across a separate committed transaction.

Executed on 2026-11-04: the focused run passed 9/9 before the supplementary
Unicode case was added. The final reactor run below passed all 10 repository
integration cases, 16 backend tests in total and 1182 mining-core tests, with
zero failures/errors/skips. The existing full-backend context used Hibernate
validate against the configured development DB with Flyway/SQL initialization
disabled; repository fixture writes were confined to disposable MySQL.

```powershell
mvn -B -pl backend -am '-Dspring.flyway.enabled=false' '-Dspring.sql.init.mode=never' '-Dspring.jpa.hibernate.ddl-auto=validate' test
```

The existing Python contract-fixture checker also passed 6/6. Four protected
local WDM documents and runtime Flyway V2 retained their SHA-256 hashes. No
stage, commit or push was performed. WDM-01.2 was subsequently accepted by the
owner on 2026-11-04. Its acceptance does not establish Admin security acceptance.

This repository read currently materializes the entire result in memory;
streaming/large-export limits need deliberate treatment in WDM-01.3. No
scalability claim is made. Production legacy data's UTC compliance, HTTP/Auth,
CSV serialization and persisted snapshot behavior remain NOT VERIFIED. This
query does not add an endpoint or supply authorization by itself.

## WDM-01.3 endpoint boundary

`GET /api/v1/admin/order-exports?fromTime=2026-11-01T00:00:00Z&toTime=2026-11-02T00:00:00Z`
defines the CSV download. Offset inputs must be URL encoded normally (`+` in
an offset is `%2B`). The controller accepts one value for each required filter,
rejects unknown/duplicate filters and parses ISO offset timestamps without
truncating excess fractional digits, including trailing zeros beyond six.
The repository enforces increasing UTC bounds and MySQL DATETIME range.

An authorized successful response is 200, text/csv; charset=UTF-8, with exact
Content-Length, Cache-Control: no-store, a server-generated UUID attachment
filename and server-generated X-Request-Id. CSV bytes are produced completely
before response headers are committed: UTF-8 with a single leading BOM, CRLF, six fractional
UTC timestamp digits, exact integer/decimal strings and preserved quoted text.
Header-only output is valid. No snapshot, file or conversion is created.

The endpoint uses a bounded overload of the accepted query. SQL LIMIT fetches
at most maxRows + 1 rows from the same consistent read; a sentinel row rejects
the export, never returns a truncated successful file. Serialization separately
counts UTF-8 bytes, including the three-byte BOM, header, quotes and CRLF. Defaults are 100,000 data
lines (excluding the header) and 104,857,600 bytes (100 MiB), increased at the
owner's request on 2026-11-04 for larger experiments. These are configurable
limits, not benchmark-established capacity guarantees. Configure `wdm.order-export.max-rows` /
`wdm.order-export.max-bytes`, or WDM_ORDER_EXPORT_MAX_ROWS /
WDM_ORDER_EXPORT_MAX_BYTES. Invalid nonpositive limits fail startup; row limit
counts order-item records, not physical newlines inside quoted fields, and
must also leave room for its sentinel in a signed Java int. Either output limit
returns 413 PAYLOAD_TOO_LARGE with an instruction to narrow the period. This
extends the common payload-limit convention to exported artifacts. The old
unbounded query method remains for compatibility; the endpoint always uses the
bounded overload. Result and byte buffers remain in memory within configured
limits; database scan/sort cost and concurrent-request memory are not bounded
by these limits, and no scalability claim is made.

**Historical WDM-01.3 state before AUTH-04; current security update follows below.**

WdmRequestFilter gates the Admin namespace before MVC validation/routing and
sets correlation/no-store headers. Namespace detection uses Spring's parsed
path semantics, including encoded segments and matrix parameters. A shared
Auth provider must supply one unique WdmAdminAuthorizer bean that verifies
credentials, account eligibility and its agreed Admin authority mapping before
returning. It rejects with safe 401 AUTHENTICATION_REQUIRED or 403 ACCESS_DENIED
errors. No token parsing, login/session lifecycle, role-header fallback or
standalone WDM identity system is implemented here.

**There is currently no such provider: default real HTTP behavior is 503
SERVICE_UNAVAILABLE before any data read, even if a client sends a token or
an invalid filter.** Provider errors also deny data access. Mock authorizers
in controller tests establish only the consumer boundary, not working Auth.
BLK-01 remains OPEN; do not enable downloads through a permissive provider.
Shared Auth must still agree filter ordering, identity/role mapping,
401 challenge, CORS/CSRF and actual 401/403/Admin-success evidence.

WDM errors use the accepted status/code/message/path/requestId/details shape,
application/json, no-store and matching X-Request-Id; path excludes query.
400 validation errors contain safe field details without rejected input.
405 preserves Allow; incompatible Accept yields 406 NOT_ACCEPTABLE with the
same JSON envelope. Unexpected failures use sanitized 500 INTERNAL_ERROR.
Non-WDM exception handling is delegated unchanged. After response commit,
transfer failures are logged and no JSON is appended to CSV; Content-Length
allows a client to detect an incomplete transfer.

Controller tests use real service/serialization and mocked repository/Auth;
successful CSV is compared byte-for-byte with the accepted valid/empty
fixtures. Real HTTP application tests verify missing-provider 503 (including
encoded/matrix/unknown resource paths) and unchanged public health access.
MySQL integration tests exercise the real bounded query and limit rejection.
These checks do not establish a working authenticated download or browser E2E.

Focused verification:

```powershell
mvn -B -pl backend -am '-Dtest=OrderExportControllerTest,OrderExportAvailabilityTest,OrderExportRepositoryTest,WdmExceptionHandlerTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Full regression uses the Flyway-disabled/SQL-init-disabled/Hibernate-validate
command recorded in WDM-01.2 above. No migrations/seed scripts or fixture writes
against the development DB are part of this task. The four protected local
WDM documents remain unchanged. WDM-01.4 upload/storage/UI work has not started.

Executed on 2026-11-04: final full regression passed 33/33 backend tests
(13 controller/content consumer tests, 11 disposable-MySQL repository tests,
1 real-HTTP default availability test, 2 exception-boundary unit tests, and
6 existing backend tests), plus 1182/1182 mining-core tests. The unchanged
Python fixture checker passed 6/6; git diff --check passed. Protected-document
and V2 SHA-256 comparisons matched, and temporary MySQL containers were removed
by Testcontainers. Development MySQL was left running. No stage/commit/push.
WDM-01.3 implementation is ready for owner review; authenticated operational
download and actual-provider 401/403/Admin success remain NOT VERIFIED.

After the owner's larger-experiment request on 2026-11-04, defaults and controller
test limits were raised to 100,000 records / 100 MiB. Focused controller and real
HTTP availability checks passed 14/14, and git diff --check passed. These checks
use small correctness fixtures; a 100,000-record capacity/performance run was
NOT VERIFIED. Environment/property overrides still take precedence over defaults.

## AUTH-04 shared security update (2026-11-04)

The default shared Auth provider is now connected through
`SharedAuthWdmAdminAuthorizer`. It reuses Auth's Bearer/JWT/account/session
verification and requires exact current DB role ADMIN before MVC validation,
resource lookup or export queries. Normal default HTTP is now 401 for missing/
invalid/expired/ineligible identity, 403 for eligible non-ADMIN, and continuation
for eligible ADMIN. Missing or ambiguous providers still fail closed with 503;
storage failure also denies access. No WDM-specific login or browser role trust.

`ApiSecurityFilter` applies strict HTTP firewall defaults and explicit API route
policy first. Canonical encoded segments still require Auth; matrix/ambiguous
and double-encoded paths are rejected safely with 400. Tomcat may reject encoded
separators before application JSON handling. Denials and Auth errors preserve
no-store/requestId, 401 Bearer challenge and the WDM JSON error shape when the
application handles the response. The namespace guard covers unknown Admin
resource paths and unsupported methods before MVC handling.

`WdmAuthorizationIntegrationTest` uses real shared login/JWT/session and export
controller/service/JDBC on disposable MySQL. It proves 401/403/Admin-success,
exact 13-column historical CSV, no export repository calls on rejection,
revocation/role removal/account state/expiry, path rejection and default denial
of a real fixture endpoint without policy. Mocked/fault-injected evidence is
classified separately in the local AUTH-04 report. Existing CSV/time/limit
semantics above are unchanged. Browser/frontend E2E remains AUTH-05/06; BLK-01
stays OPEN, and this update does not infer owner acceptance of WDM-01.3.

## Historical WDM-01.4 upload transport boundary (2026-11-05)

The owner explicitly selected WDM-01.4 only: transport-valid uploads must return
503 until WDM-01.5 implements durable storage. This is not a successful upload
or an accepted/persisted snapshot. No snapshot ID, validation result, checksum,
DB row or durable raw artifact is created by this endpoint in its current state.
WDM-01.3 owner acceptance is still independent. The current checklist records
AUTH-01–06 owner acceptance and BLK-01 CLOSED on 2026-11-05; the OPEN statements
in the historical sections above describe their earlier verification boundaries.

`POST /api/v1/admin/dataset-snapshots` consumes multipart/form-data and produces
JSON errors through the shared error handler. It requires exactly one file part
named `file`, no extra file/text fields and no query parameters. Shared Auth's
ADMIN authorization executes before lazy multipart parsing. Missing/empty parts,
duplicate parts, extra fields and unsafe filenames return 400 VALIDATION_FAILED;
malformed multipart returns 400 MALFORMED_REQUEST. A non-multipart request,
unsupported suffix or part MIME returns 415 UNSUPPORTED_MEDIA_TYPE. Only the
transport checks defined above run: arbitrary nonempty bytes, including header-only
CSV, business-invalid CSV, BOMs and invalid UTF-8, can reach the storage boundary.
Their content is unvalidated; WDM-02 owns parsing and business validation.

Filename policy is an engineering restriction: require a nonblank plain filename
of at most 255 Java string units, reject slash/backslash, colon and ISO control
characters, and require a case-insensitive `.csv` suffix. The client filename
is never resolved as a path or logged. Part MIME parameters are permitted; the
base media type must match the accepted list case-insensitively, or be absent.
MIME/suffix never establish that the content is valid CSV.

Defaults are 104,857,600 bytes per file (100 MiB) and 105,906,176 bytes per request
(101 MiB, including multipart overhead). Set `WDM_CSV_UPLOAD_MAX_FILE_BYTES` and
`WDM_CSV_UPLOAD_MAX_REQUEST_BYTES` to positive integer byte counts. Standard
`spring.servlet.multipart.max-file-size` / `max-request-size` overrides are also
honored; service validation reads the actual servlet MultipartConfigElement,
not a separate copy of these limits. Startup rejects unlimited/nonpositive limits
or a request limit no greater than the file limit. Overhead counts toward the
request limit, so a file at its own limit also needs a request within the total
limit. Either limit returns 413 PAYLOAD_TOO_LARGE with an upload-specific message.
Multipart parsing is lazy and the disk threshold is zero; the servlet manages
temporary upload parts and request cleanup, not WDM snapshot storage.

Transport-valid ADMIN uploads return 503 SERVICE_UNAVAILABLE with the message
`Dataset snapshot storage is not available yet; no snapshot was created.`
Do not automatically retry this POST or expose a success state. Missing/invalid
identity and eligible non-ADMIN retain shared Auth's 401/403, no-store,
Bearer challenge on 401 and server-generated request ID. WDM-01.5 must replace
the explicit unavailable boundary with original-byte storage, SHA-256 and
honest persisted metadata before any success response is enabled.

Focused verification (Docker required for real shared Auth/MySQL cases):

```powershell
mvn -B -pl backend -am '-Dtest=DatasetUploadControllerTest,DatasetUploadHttpTest,WdmAuthorizationIntegrationTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Ddebug=false' test
```

`DatasetUploadControllerTest` uses real MVC/service and a test-only authorizer.
`DatasetUploadHttpTest` uses real Tomcat multipart handling with small explicit
limits and a test-only authorizer; it does not establish shared Auth behavior.
`WdmAuthorizationIntegrationTest` uses the real shared provider/JWT/session and
disposable MySQL; its upload case checks 401/403/503, malformed/oversized requests
before business calls and unchanged snapshot row count. No fixture writes target
the development database. Large-file capacity/performance, browser upload,
persistence/reload/checksum and WDM-02 content validation remain NOT VERIFIED.

Executed on 2026-11-05: focused MVC/servlet tests passed 10/10; the shared
Auth/MySQL class passed 28/28, including the new upload case. Final regression:

```powershell
mvn -B -pl backend -am '-Dtest=*Test,!OrderExportAvailabilityTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Ddebug=false' test
python backend/src/test/resources/wdm/csv-v1/verify_contract.py
git diff --check
```

The reactor passed 1182 mining-core cases and 157 backend cases, with zero
failures/errors/skips; fixture checker passed 6/6 and diff whitespace checks
passed. This is not the entire default backend suite: BackendApplicationTests
(plural, outside `*Test`) and OrderExportAvailabilityTest were intentionally
not run because they use the default development datasource rather than a
self-contained test datasource. The full-context cases that did run supplied
disposable MySQL, test signing secrets, Flyway/SQL-init disabled and Hibernate
validate. Testcontainers managed its own database/container cleanup. No mining
source, migrations, frontend code, development DB fixtures, stage/commit/push
or pre-existing artifact deletion was part of WDM-01.4.

An initial isolated HTTP-test configuration inadvertently included the main
application and failed context creation with datasource auto-configuration
disabled. The test configuration was corrected to an explicitly selected,
scan-excluded test component. The final focused and regression runs above
passed with that correction; no production Auth/DB workaround was introduced.
WDM-01.4 is technically ready for owner review within the explicitly selected
transport-only scope. It does not establish owner acceptance or complete the
WDM-01 snapshot acquisition exit gate.

## WDM-01.5 snapshot acquisition boundary (2026-11-05)

WDM-01.4 was accepted by the owner on 2026-11-05 for its transport-only/temporary
503 behavior. The owner subsequently authorized WDM-01.5, replacing that explicit
unavailable branch with durable acquisition. This section describes current
upload behavior and supersedes the temporary 503 behavior in the historical
section above. CSV content validation and conversion remain later WDM tasks.

After the unchanged ADMIN and transport checks, POST
`/api/v1/admin/dataset-snapshots` streams original bytes to a generated artifact,
counts actual bytes and computes SHA-256. Storage independently enforces the
actual stream byte limit, rather than trusting MultipartFile size metadata.
It neither parses nor reserializes the CSV. Nonempty header-only, business-invalid
and invalid-UTF-8 inputs can be acquired as PENDING. They are not declared valid.

Storage allocates a fresh UUID directory exclusively and opens its `raw.csv`
with CREATE_NEW, WRITE and NOFOLLOW_LINKS. No client filename is passed to storage.
Each upload creates an independent snapshot, including repeated identical files;
there is no checksum deduplication, overwrite or update API. The file is forced
to disk and closed, and the input stream is closed, before metadata persistence.
The DB stores only the generated relative reference `<snapshotId>/raw.csv`.

Configuration: `wdm.snapshot-storage.root`, environment `WDM_SNAPSHOT_STORAGE_ROOT`,
default `${user.home}/.wspm/snapshots`. Require an absolute normalized path outside
the application working tree (the repository parent when launched from backend/).
The directory is created lazily at acquisition; inaccessible storage fails that
request safely. Existing symbolic links, non-directory or special/reparse
ancestors are rejected. The storage directory has no static-resource mapping.
Deployment must give the service account private OS access to the root; this is
not protection against an OS administrator or concurrent privileged filesystem
mutation. No ACL rewrite is performed on pre-existing directories.

`DatasetSnapshotRepository` exposes insert and read-by-ID, with no generic
save/merge/delete surface. `DatasetSnapshotService` creates the initial metadata
defined above using an explicitly initialized server UTC Instant truncated to
microseconds. source_name is the filename already accepted by transport validation,
not a path or a verified original data source. The acquisition transaction uses
REQUIRES_NEW so commit completes before the service returns, even with a future
transactional caller. The existing V2 entity/schema is reused without migration.

After successful commit the endpoint returns **201 Created** with the shared
`ApiResponse<DatasetSnapshotResponse>` envelope. Its public data fields are:

| Field | Meaning at acquisition |
|---|---|
| snapshotId | Backend-generated UUID string |
| sourceType / sourceName | CSV / accepted display filename |
| validationStatus | PENDING |
| conversionStatus / statisticsStatus | NOT_STARTED / NOT_COMPUTED |
| rawSha256 | SHA-256 of exact stored bytes, 64 lowercase hex |
| rawSizeBytes | Actual byte count as a decimal string |
| sequenceCount | null, despite internal sequence_count = 0 |
| fromTime / toTime | null, including files exported from the app |
| createdAt | Explicit acquisition time in UTC, consistent with DB microseconds |

No raw path, mapping path, sequence path or internal conversion_report_json is
returned. No read/list/download endpoint or Location pointing to an unimplemented
read endpoint is introduced here. Request ID and Cache-Control: no-store remain
unchanged. Actual export → upload is source_type CSV / CSV_UPLOAD, never DATABASE.

### Failure consistency and reconciliation

Filesystem and MySQL are not one atomic transaction. Metadata is inserted only
after the complete artifact is closed. A transaction synchronization owns cleanup:
rollback before the commit phase discards only the current acquisition's receipt;
UNKNOWN or COMMITTED outcomes retain it. Once beforeCommit has run, even a later
rollback notification retains the artifact conservatively: a runtime commit
failure followed by rollback does not establish that the DB never committed.
Failure to start a transaction or close the input
stream before persistence also discards this request's artifact. Storage read/write
failure cleans its own partial allocation before returning failure.

Cleanup accepts a private receipt created by the same storage instance. It checks
the directory relationship, ancestors and recorded file/directory identity,
deletes only its known `raw.csv`, and removes the UUID directory only if empty.
It never enumerates/deletes an arbitrary tree or follows a client path. Native
fileKey is used where available; Windows' null fileKey uses creation time/type
together with the exclusive UUID allocation and receipt under the trusted-root
assumption. Unexpected directory contents or cleanup failure are retained/logged
for review, not recursively removed.

An uncertain commit, process termination between file completion and DB commit,
or interrupted cleanup can leave an artifact without a confirmed row. Lost HTTP
response after commit can leave a complete snapshot even though the client saw
failure. When the application can send an acquisition error, it returns 503
SERVICE_UNAVAILABLE with no success receipt; a lost response may instead appear
as a client transport error. The service logs snapshot ID and sanitized failure
context. Automatic POST retry
and exactly-once acquisition are not promised. Reconcile operator-side by snapshot
ID, DB raw_file_path/raw_sha256, exact bytes and checksum before deciding what to
retain or remove. There is no automatic sweeper or deletion of older artifacts.
The file force operation is not an end-to-end power-loss/crash guarantee for
directory metadata, MySQL durability settings or storage hardware.

### WDM-01.5 verification routes

```powershell
mvn -B -pl backend -am '-Dtest=RawSnapshotStorageTest,DatasetSnapshotServiceTest,DatasetUploadControllerTest,DatasetUploadHttpTest,WdmAuthorizationIntegrationTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Ddebug=false' test
```

RawSnapshotStorageTest uses real temporary files for byte/hash fidelity, stream
limit/read failures, concurrent allocations and receipt-only cleanup. The service
test uses real temporary files with a mocked repository and synthetic transaction
resource to verify rollback, begin/input-close failure, unknown commit retention,
commit-before-return and initial metadata. These are not DB/crash evidence.
MVC/servlet tests use mocked acquisition responses for successful transport;
the HTTP test still exercises real Tomcat parsing and request/file limits.

WdmAuthorizationIntegrationTest uses real shared Auth/JWT/session, disposable
MySQL with V2 definitions/Hibernate validate, and JUnit-owned external storage.
It verifies ADMIN-only 201, separate persistence-context readback, byte/hash fidelity
including BOM/mixed newlines/invalid UTF-8, all initial null statistics/paths,
header-only/business-invalid PENDING snapshots, real export-upload provenance and
eight concurrent HTTP acquisitions. Its rollback case performs a real INSERT/flush
before injecting an exception and verifies real rollback plus owned-file cleanup.
Fault injection is classified explicitly; it is not a physical DB outage test.
The existing default-datasource tests remain outside this isolated verification
route; no development DB fixture writes or default-root uploads are performed.

Final verification on 2026-11-05: **172/172 backend tests PASS** across the 22
isolated backend test classes, zero failures/errors/skips. This includes 32
Auth/WDM MySQL cases, 7 MVC transport cases, 3 servlet multipart cases, 5 real-file
storage cases and 6 service/transaction-lifecycle cases. Reproduce the exact
backend-only selection from repository root in PowerShell:

```powershell
$wdmTestNames = @(rg --files backend/src/test/java | Where-Object { $_ -match 'Test\.java$' -and $_ -notmatch 'OrderExportAvailabilityTest\.java$' } | ForEach-Object { [IO.Path]::GetFileNameWithoutExtension($_) })
$wdmTestSelection = '-Dtest=' + ($wdmTestNames -join ',')
mvn -B -pl backend -am $wdmTestSelection '-Dsurefire.failIfNoSpecifiedTests=false' '-Ddebug=false' test
```

BackendApplicationTests (plural) and OrderExportAvailabilityTest are deliberately
outside this selection because they use a default development datasource. The
earlier reactor run during this task passed 1182/1182 mining-core cases and
171/171 backend cases before the final conservative commit-phase tightening;
mining source was unchanged. The final backend run above includes the added
runtime-commit-failure/rollback-notification regression. The unchanged Python CSV
fixture checker passed 6/6, and git diff --check passed.

Initial verification exposed Windows' null fileKey preventing owned-file cleanup;
the receipt/creation-identity fallback fixed it. The initial rollback fault test
also tried to stub through a MANDATORY transactional proxy without a transaction;
the test was corrected to stub its actual Mockito target, preserving production
transaction rules. Final tests pass without skipping these cases or weakening
the rollback/retention assertions. Testcontainers and JUnit cleaned only their
owned disposable containers/temporary storage. No pre-existing artifacts were
deleted and no stage/commit/push was performed.

| Acceptance | Evidence |
|---|---|
| AC1 — ADMIN upload creates real snapshot / 201 | Shared Auth + HTTP/MySQL acquisition case |
| AC2 — original bytes / independent SHA-256 / measured size | Fixture/BOM/mixed-newline/invalid-UTF-8 readback + storage tests |
| AC3 — persisted metadata survives separate read contexts | Two repository read transactions, DB fields and UTC microsecond round-trip |
| AC4 — honest PENDING / null derived state | Header-only/business-invalid fixtures and complete initial metadata assertions |
| AC5 — no client paths / no overwrite / independent concurrency / private DTO | Transport path rejection, exclusive allocation, 12 concurrent storage writes, 8 HTTP uploads |
| AC6 — failure consistency | Input/read/begin failures; actual INSERT then injected rollback; synthetic UNKNOWN and runtime-commit-error retention |
| AC7 — Auth and regression | 172 final backend cases; real 401/403 before multipart/business work |
| AC8 — scope preservation | No changes to V1/V2, entity, mining-core or frontend; no validation/conversion/UI implementation |

WDM-01.5 was subsequently **OWNER ACCEPTED in chat on 2026-11-05**, after the
technical verification above. Recording acceptance does not rerun tests or
implement WDM-01.6. Browser
upload, 100 MiB capacity/performance, physical DB outage/process-kill/power-loss,
cross-platform symlink/ACL deployment behavior and production rollout remain
NOT VERIFIED. Runtime immutability is enforced through the acquisition-only API
and generated storage allocation under the trusted-root assumption; it does not
prevent a privileged operator from modifying files or database rows directly.

## WDM-01.6 Dataset Admin UI (2026-11-05)

The owner authorized the planned frontend export/upload slice after accepting
WDM-01.5. `/admin/datasets` now has two independent forms under the existing
shared Auth ADMIN guard. Mount/navigation performs no export, upload or fake
snapshot-list request. The other WDM pages retain their availability skeletons.

The export form accepts two calendar dates with an inclusive last day. Business
display uses explicit fixed UTC+07, independent of browser/host timezone. It
converts the first day's midnight and the day after the last day's midnight to
UTC [fromTime,toTime). For example 2026-11-01 through 2026-11-02 sends
2026-10-31T17:00:00.000Z through 2026-11-02T17:00:00.000Z. There is no
23:59:59 endpoint, implicit local Date parse or client-selected order status.
Client validation rejects absent/reversed/impossible dates and UTC bounds
outside the server's MySQL year range. This is application date-input handling,
not an algorithmic or historical timezone-reconstruction decision.

The domain API wrapper requires HTTP 200 and nonempty text/csv before preparing
a browser download. It keeps exact Blob bytes, including a header-only artifact.
It accepts the server's generated order-export UUID filename or uses the safe
fixed fallback order-export.csv. It never treats JSON/HTML or partial 206 as a
successful CSV export. The temporary anchor is removed and its Object URL is
revoked after the click. Success text describes handing the file to the browser,
not proof that the user saved it to disk. Export does not initiate upload.

The upload form accepts one file, prechecks nonempty bytes and a case-insensitive
.csv suffix, and leaves MIME/content/byte-limit enforcement authoritative on the
backend. FormData contains only file; the browser owns its boundary. The wrapper
requires HTTP 201 plus a runtime-validated acquisition receipt: CSV, PENDING,
NOT_STARTED, NOT_COMPUTED, actual size/checksum strings and null sequenceCount/
period. It neither coerces exact integer strings into Number nor accepts an
internal placeholder zero as a computed sequence count. The success panel shows
the real snapshot ID/source and PENDING, with no fake counts or pipeline actions.
The file selection is cleared after success to avoid accidentally repeating it.

Both forms disable relevant controls while busy and use an immediate in-flight
guard against duplicate submit. They reuse the existing bearer adapter/session
signal, add per-form unmount cancellation, and ignore late success after abort.
Shared client response handling additionally checks cancellation after fetch and
body reads, and supports optional expectedStatus without changing existing callers.
There is no refresh/login implementation or POST replay in this slice.

Safe localized UI covers 400/401/403/413/415/503, cancellation, unexpected response
and network failures. It shows request ID when available. An uncertain upload
result warns that a snapshot may already exist and asks the user to verify before
trying again. It does not present a failed receipt as success. Export 413 asks
for a narrower period; upload 413 asks for a smaller file without asserting that
a frontend hardcoded limit reflects the backend's current override.

### WDM-01.6 verification and scope

From frontend/:

```powershell
$env:CI = 'true'
npm test -- --watchAll=false --runInBand
npx tsc --noEmit
```

Production build was run separately with `npm run build` under its normal
environment, without changing the CI warning policy. It completed with the
pre-existing hook/non-null warnings in LoadingMiddleware, ProductVariants,
ProductVariantsForUpdate, use-select-address and promotion view models, plus
the existing Browserslist database notice. Focused ESLint on all 14 in-scope
TypeScript/TSX files passed with no warnings/errors. git diff --check passed.

Final full frontend regression: **157 PASS, 2 SKIPPED**, 12 passing suites.
The two existing opt-in suites are health.live.test.ts and auth/auth.live.test.ts;
they were not enabled or treated as passing. Focused date/API/client/navigation
verification also passed 92 cases with TZ=America/Los_Angeles, followed by 19
Dataset page cases under the normal environment. These tests cover actual UI,
shared client serialization/error handling and mocked fetch responses. They do
not call a live backend, access the development DB or establish browser E2E.

Initial page-test setup lacked the Router needed by the existing title hook;
the setup was corrected to use MemoryRouter. A reference-identity assertion
against a FormData File clone triggered a Node/Jest matcher crash; it was corrected
to assert the selected file's name/size and actual multipart contract. No test was
skipped for that failure. Mantine 4's existing typed Input/InputWrapper supports
the native date control without dependency upgrades. Final tests/type/lint/build
pass with these corrections.

| Acceptance | Evidence |
|---|---|
| AC1 — real forms, existing ADMIN route/nav/guard | Page + navigation/role tests; no requests on mount |
| AC2 — explicit UTC half-open period | Golden UTC+07/date-boundary cases, leap/year boundaries, different process timezone |
| AC3 — safe actual Blob download / cleanup | API and page tests: header-only bytes, generated/fallback filename, JSON/206 rejection, URL revoke |
| AC4 — exact multipart / verified 201 / honest initial state | Native FormData/API/page cases; count strings preserved; 200/202/204 or misleading receipt rejected |
| AC5 — busy/error/cancel/no replay | Duplicate submits, 400/401/403/413/415/503, network loss, unmount/session abort and late response tests |
| AC6 — frontend verification | 157 passing tests, typecheck, focused lint, normal build; inherited warnings classified |
| AC7 — scope | No backend/schema/mining-core/dependency/Auth-lifecycle changes; no detail/list/validation/conversion/mining UI |

At the original WDM-01.6 checkpoint, technical verification was ready for owner
review and its checkbox remained unchecked. It was subsequently closed through
the accepted WDM-01.8 gate and the owner's checkbox reconciliation request; see
the WDM-01.8 evidence below. WDM-01.3's separate owner acceptance is still not
inferred from other tasks. WDM-01.7 owns snapshot detail; WDM-01.8 owns the new
business UI's real browser/API/DB gate. Browser layout/accessibility/download and
upload with actual backend, strict CI=true build, cross-browser and 100 MiB
capacity behavior are NOT VERIFIED in this task. Historical AUTH-06 harness
evidence does not establish the newly implemented Dataset business UI gate.

## WDM-01.7 Snapshot detail and original artifact read (2026-11-05)

The owner approved the read-only DB inspection and authorized the proposed
WDM-01.7 slice. The development container had commerce data but zero snapshots;
no production/development upload, seed or DB write is used to implement this
task. Test data is confined to disposable MySQL/JUnit temporary storage.

### Read API and public metadata

| GET under /api/v1/admin | Result |
|---|---|
| /dataset-snapshots/{snapshotId} | 200 ApiResponse<SnapshotDetailResponse> |
| /dataset-snapshots/{snapshotId}/artifacts/raw | 200 original bytes as text/csv |

Both routes reuse the existing namespace ADMIN guard before MVC lookup/ID
validation/data/file access. IDs must be lowercase canonical UUID strings:
malformed ID is 400 VALIDATION_FAILED, unknown UUID is 404 RESOURCE_NOT_FOUND.
Unknown resources still receive normal authorization-first handling. A pre-existing
unknown-resource test now uses an actually unmapped resource family because the
snapshot detail resource is no longer an unknown route. Incompatible Accept
can yield 406 through the common handler; it does not bypass authorization.

The detail response includes snapshotId, nullable sourceType/sourceName and
validation/conversion/statistics status, recorded rawSha256/rawSizeBytes,
sequenceCount, fromTime/toTime/createdAt, rawAvailability and an explicit
provenance object. Provenance exposes only known contractVersion, acquisition,
sourceTimeZone, timeField and statusPolicy values. No entity, arbitrary report
JSON or storage path is serialized. Unrecognized metadata becomes null rather
than an invented default or raw untrusted report value. Legacy source names
that look like paths/control-bearing strings are withheld as null. This is an
application projection decision, not a mutation of the stored record.

Known validation states are PENDING, VALID, INVALID, LEGACY_UNVERIFIED. Conversion
recognizes only the current NOT_STARTED contract; future conversion states need
their own contract integration. Statistics recognizes NOT_COMPUTED/COMPUTED.
sequenceCount is null unless statisticsStatus is explicitly COMPUTED and the
mapped count is nonnegative. Exact counts and sizes stay decimal strings. This
does not define mining threshold/R6 behavior or prove future conversion semantics.

### Raw availability, integrity and read limits

rawAvailability is AVAILABLE, MISSING or UNAVAILABLE. AVAILABLE means the generated
reference is safe, required size/hash metadata is usable, and a regular file
exists at the recorded size. It is an existence/shape/size probe, **not a fresh
checksum attestation**. MISSING means no raw reference or a referenced file is
absent. UNAVAILABLE covers unsafe references, unusable metadata, access errors,
special/link paths and unsupported read size. Existing detail metadata can still
be read when raw is missing/unavailable.

The raw route validates the exact generated `<snapshotId>/raw.csv` reference,
checks ancestor directories without following links/reparse/special paths, and
opens the final regular file READ/NOFOLLOW_LINKS. It independently bounds the
stream and checks actual total size and SHA-256 against recorded acquisition
metadata. All bytes are buffered and verified **before** returning a successful
ResponseEntity, so a same-size tamper cannot be sent as a successful download.
The returned byte array is exactly the verified array content, preserving BOM,
embedded/newline bytes and even unvalidated encoding. It is labeled text/csv
without asserting that WDM-02 has validated its content/encoding.

The file bound is the currently configured multipart max-file-size, additionally
capped below Java's array-size limit. Lowering that limit can make an older,
larger artifact unavailable. Buffering can use more than one file-sized allocation
and does not bound concurrent-request heap consumption. No 100 MiB capacity,
throughput or memory-efficiency claim is made. Trusted private OS access to the
storage root remains an assumption; this is not protection against a privileged
OS writer racing/replacing the filesystem. There is no file rewrite, cleanup,
directory creation, checksum repair or DB update on GET.

Missing raw returns 404 RESOURCE_NOT_FOUND. Unsafe reference, malformed expected
metadata, size/hash mismatch or unreadable storage returns safe 503
SERVICE_UNAVAILABLE before CSV headers/body are committed. Metadata is retained
unchanged, and logs use snapshot ID with sanitized failure context. Attachment
name is server-generated `snapshot-<UUID>-raw.csv`; Content-Length is exact.
Downloads and metadata/errors retain no-store and server request IDs. Transfer
failure after commit follows the existing rule: never append JSON to artifact
bytes; a completed checksum check is not a guarantee of a successful client save.

### Frontend detail slice

`/admin/datasets/:snapshotId` reuses shared Auth/ADMIN and the Dataset navigation
family without adding a new menu item. The upload receipt links to this page.
Opening a direct URL or remount/reload performs GET; no local receipt is fabricated
as persisted detail. The page renders original source/provenance, record status,
UTC timestamps without dropping microsecond digits, recorded size/hash and
unknown/uncomputed counts honestly. It allows raw download only when the probe
is AVAILABLE, and presents missing/unavailable raw separately from metadata errors.

GET retry is explicit. ID changes, unmount and session clear cancel reads/downloads
and prevent stale previous-snapshot content or late downloads. API wrappers
validate the returned ID/schema, retain exact count strings, strip unknown private
fields and reject JSON/partial responses as successful raw downloads. Anchor/Object
URL cleanup is shared with export through `downloadDatasetFile`. No detail/list seed, list endpoint,
validation/conversion/mining action, sequence or mapping download is introduced.

### Verification routes

```powershell
mvn -B -pl backend -am '-Dtest=SnapshotReadServiceTest,WdmAuthorizationIntegrationTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Ddebug=false' test
```

SnapshotReadServiceTest uses a mocked repository and real temporary files for
unknown metadata, exact counts, missing/tampered artifacts, malformed IDs,
absolute/foreign/traversal references, bounds and no directory creation on read.
WdmAuthorizationIntegrationTest uses real Auth/JWT/provider, disposable MySQL/JPA
and temporary storage. New cases verify public detail projection, exact binary
raw download/length/filename, persisted metadata/bytes retained, 401/403/400 before
repository/file calls, unknown UUID, missing/tampered/unsafe raw and safe errors.
Fixture tampering/deletion is confined to files and rows created by those tests.

Frontend API/page tests use mocked fetch with actual routing/guard/client behavior:
deep links/remount, unknown state, missing raw, error/retry, exact strings and bytes,
download cleanup/duplicate prevention, ID changes and session/unmount cancellation.
These are separate from the real backend HTTP/MySQL evidence. The browser/API/DB
full-system gate remains WDM-01.8 and is not claimed by either category alone.

Final verification on 2026-11-05:

| Check | Result |
|---|---|
| Targeted read service + four relevant real Auth/HTTP cases | 12/12 PASS |
| Final reactor: `mvn -B -pl backend -am '-Dtest=*Test,!OrderExportAvailabilityTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Ddebug=false' test` | Backend 183/183 and mining-core 1182/1182 PASS, no failures/errors/skips |
| Focused frontend API/detail/upload/navigation | 85/85 PASS |
| Full frontend: `CI=true npm test -- --watchAll=false --runInBand` | 182 PASS, 2 existing live HTTP opt-in SKIPPED, 13 passing suites |
| TypeScript / focused ESLint (nine in-scope frontend files) | PASS |
| Normal `npm run build` | PASS with the same inherited warnings outside WDM and Browserslist notice |
| CSV fixture checker / git diff --check | 6/6 PASS / PASS |
| Final read-only development DB check | DATASET_SNAPSHOT=0; ORDERS=1000 |

BackendApplicationTests and OrderExportAvailabilityTest remain outside the isolated
backend selection because of their default development datasource. No development
fixture writes, live upload, migration or seed were performed. Testcontainers/JUnit
managed their disposable test resources; no pre-existing source/artifact cleanup or
stage/commit/push was requested. The regular frontend build regenerated ignored
production output as part of the approved build check.

Initial verification found and corrected API factory method placement, explicit
PathVariable names required by the existing compiler settings, the Java spy-target
generic in varargs, and Mantine 4/test lint typing/setup. Regressions were fixed
without skipping the failing tests or weakening the authorization/integrity checks.

| Acceptance | Evidence |
|---|---|
| AC1 — persisted detail and direct URL/remount | Real HTTP/MySQL projection plus actual frontend route/refetch tests |
| AC2 — honest pending/unknown/computed flags | Null metadata and placeholder count cases; exact count strings beyond JS safe integer |
| AC3 — original bytes/hash/length/private paths | BOM/mixed-newline/invalid-byte raw fixture; verified download; safe attachment and explicit DTO |
| AC4 — rejection before business access | Real shared 401/403/400, snapshot repository/file spies; unknown UUID/missing/tamper/unsafe reference cases |
| AC5 — UI states/cancel/stale prevention | Error/retry, missing raw, duplicate download, ID change, unmount/session late-response cases |
| AC6 — isolated verification and read-only behavior | Final regression above; record JSON/hash/bytes unchanged; no directory creation on read |
| AC7 — task boundaries | No list, validation, conversion, mining, schema, dependency or Auth-lifecycle changes |

WDM-01.7 is technically complete and explicitly owner accepted on 2026-11-05.
The real Dataset business UI browser/API/DB gate
is still NOT VERIFIED here and remains WDM-01.8. Production deployment, strict
CI=true build, 100 MiB/concurrent-memory capacity, hostile filesystem races and
cross-platform link/ACL behavior are NOT VERIFIED. Research contracts, R6 and
the exact-real comparator question remain unchanged.

### Post-acceptance architecture review and original WDM-01.8 plan

This planning checkpoint predates execution. The gate evidence below supersedes
its NOT VERIFIED status for the functional acceptance criteria.

The owner accepted WDM-01.7 and requested a Dataset architecture review/refactor
on 2026-11-05. The review traced the Dataset controller/service/repository/storage
and DTO boundary, acquisition transaction, frontend API parsers, request lifecycle,
export and detail pages. Existing Auth changes and mining implementation were
outside the refactor. Controllers retain HTTP ownership, services orchestrate
business operations, the repository owns persistence and raw storage owns file
I/O. Explicit projection keeps private report/path fields outside the public DTO.
The projection's allowlists are deliberate security/unknown-state rules; introducing
generic entity serialization or a new abstraction layer is not required.

Export and raw-download pages duplicated browser Blob URL/anchor handling. Both now
use a small `downloadDatasetFile` helper; API validation, cancellation, duplicate
prevention and success state remain with their existing owners. No backend runtime,
schema, dependency, auth policy or mining behavior changed in this refactor.
Two stale architecture sentences about the pending Dataset UI were corrected.

Post-refactor verification: full frontend tests 182 PASS and 2 existing opt-in
live tests SKIPPED; `npx tsc --noEmit`, focused ESLint on the three changed
TypeScript files, normal `npm run build` and `git diff --check` PASS. Build still
reports inherited non-Dataset warnings and the Browserslist notice. The existing
page tests exercise both downloads, filenames, URL release, duplicate prevention
and cancellation; no tests merely mirroring the helper were added. Backend Maven
tests were not rerun because backend sources/configuration were unchanged in
this review. No browser gate or development DB write was executed.

WDM-01.8 is planned, not implemented by this review. It must drive the actual built
application and login/Dataset routes, rather than AUTH-06's `window.auth06` harness.
A separate runner/case module should create disposable MySQL, synthetic historical
order fixtures and an external temporary storage root with per-run credentials.
The positive flow is UI login -> period export -> actual browser download -> upload
that exact file -> HTTP 201 -> fresh DB/file/hash checks -> receipt link -> reload
and deep URL -> raw download with exact byte equality. Export must leave snapshot
count unchanged. Initial states/counts must remain honest, and GETs must preserve
the acquired metadata/artifact. Negative cases include anonymous/CUSTOMER access,
invalid upload/ID, missing raw and same-size tampering on runner-owned fixtures.
No fake snapshot inserts or development DB writes are part of this gate.

The runner must isolate and identify its resources, use finite deadlines and
nonzero failure exit codes, preserve separate evidence per run and clean only
resources it created. Reusing V2 table definitions for the fixture does not prove
the Flyway upgrade lifecycle. AUTH-06 should remain separately runnable; changes
to any shared setup require its regression too. All WDM-01.8 acceptance criteria
remain NOT VERIFIED until that gate runs. Cross-browser, production deployment
and capacity measurements remain outside this proposed functional gate.

Proposed version-control boundary: keep reusable runner/cases, small synthetic
fixtures, package metadata/lockfile and instructions in Git. Keep generated
downloads, reports, traces, browser state, keys/secrets, storage and dependencies
outside source control. Raw run evidence belongs in ignored local output or a
controlled CI artifact store; record a safe summary and rerun command here.
No stage, commit or push was performed by this review.

## WDM-01.8 — Dataset browser/API/DB gate

Implemented, technically verified and explicitly owner accepted on 2026-11-05.
At the owner's request, the six existing Auth E2E source/package/lock files and
their ignored dependencies moved from `frontend/e2e/` to `frontend/test/e2e/`.
Relative harness/CRA/root imports and maintained run instructions were updated.
`dataset-run.mjs`, `dataset-cases.mjs` and an E2E README were added. The shared
runner selects independent Dataset cases, serves only the application build in
Dataset mode, overrides raw storage to its fresh OS-temp directory, bounds CLI/
readiness/browser waits and allocates a separate evidence directory per run.
No test hook/route is added to the application. Auth's separate harness remains
available only in Auth mode. Dependencies and lockfile versions are unchanged.

Run from repository root, with Java 21/keytool, Node 22+, Docker and Edge:

```powershell
npm --prefix frontend/test/e2e ci
mvn -B -pl backend -am package -DskipTests
npm --prefix frontend run build
npm --prefix frontend/test/e2e run test:dataset
npm --prefix frontend/test/e2e test
```

The current task used the relocated installed dependencies; `npm ls --depth=0`
confirmed pinned playwright-core 1.64.1 and esbuild 0.28.2. A fresh `npm ci` was
not rerun over pre-existing dependencies. Backend packaging succeeded with tests
explicitly skipped; it is preparation rather than Maven test evidence. The real
Spring/MySQL HTTP boundary is exercised by both browser gates.

Synthetic test data consists of six orders/seven lines: exact period start,
last microsecond before the exclusive upper bound, records just outside the
period and a cancelled order. Four completed historical lines export for calendar
day 2026-11-01 at fixed UTC+07. A line ID is 9007199254740993, above JavaScript's
safe integer range; expected literal CSV independently checks its exact text.
Current catalog values differ from historical snapshots. Reference roles and
orders are fixture-only; accounts use actual registration with random passwords.
Every DATASET_SNAPSHOT originates from a real upload, never a fabricated INSERT.
Each SQL check opens a fresh mysql connection after verifying the disposable
container's identity and task label. V2 definitions initialize the isolated DB;
Hibernate validates it. This is not a Flyway V1-to-V2 migration lifecycle test.

The test file limit is 4096 bytes and request limit 8192 bytes. It allows a small
real 413 case and does not establish the normal 100 MiB or concurrent-memory
capacity. Raw storage is explicitly overridden outside the repo; the runner does
not use development DB or storage. No development account or snapshot is created.

Initial browser execution exposed a real integration defect: `Admin.tsx` allowed
only fixed paths and rendered a legacy notice for the WDM-01.7 detail route.
Export/upload and direct route unit tests could pass while the actual shell
blocked receipt navigation. The shell now allows the exact one-segment Dataset
detail pattern; an App regression exercises the authenticated real shell.
No auth policy, backend API, schema or mining behavior was weakened.

A later duplicate-submit case initially tried to replay multipart with
Playwright route.fetch(), which was not equivalent to the browser's file stream.
The test now holds and continues the original browser request. Duplicate submits
must still produce exactly one upload and one new snapshot. The logout case
holds a real backend export response and verifies late bytes never become a
download after session invalidation. These request/response delays are test-only
network scheduling, not mocked API success. Earlier failed-run evidence remains
in separate ignored directories; failing assertions were not removed or relaxed.

Final evidence:

| Check | Result |
|---|---|
| `node frontend/test/e2e/dataset-run.mjs` | 7/7 PASS, exit 0; Edge 156.0.4314.58 |
| Dataset safe evidence | `.agent-workspace/phase-wdm/WDM-01.8-BTaooh/RESULT.json` |
| Export fixture | Four rows, 731 bytes, SHA-256 `9695d9a51272b6e302c258b792c72eb3e81f829b846b1b58da01247c1d6100b2`; snapshot count remains zero |
| Upload/readback/raw | HTTP 201, one request/one snapshot, exact bytes/size/hash; DB placeholder 0 remains API null; GET leaves DB metadata unchanged |
| `node frontend/test/e2e/run.mjs` | Auth 10/10 PASS, CRA health proxy PASS, exit 0; `.agent-workspace/phase-wdm/AUTH-06-2PZaAa/` |
| Full frontend regression | 183 PASS, 2 pre-existing live opt-in SKIPPED; 13 passing suites |
| Focused actual App shell regression | 4/4 PASS |
| TypeScript / focused ESLint / node syntax checks | PASS |
| Normal frontend build / backend package | PASS; inherited non-WDM frontend warnings / Maven tests skipped |
| Dependency inventory / git diff --check | PASS |
| Development DB read-only check after gate | DATASET_SNAPSHOT=0; ORDERS=1000 |
| Resource cleanup | Only `wspm-mysql` remains in docker ps; owned browser/process/container/temp resources removed |

| Acceptance | Evidence |
|---|---|
| AC1 — actual browser login/export/upload/detail/reload/raw | Real built UI and shell; actual downloads and file picker; direct URL/refetch |
| AC2 — period/filter/historical fidelity | Independent literal CSV, boundary/cancelled fixtures, exact large ID, zero snapshot creation on export |
| AC3 — committed snapshot and integrity | Fresh SQL readback, real artifact, independent SHA/byte comparison, honest initial states and null counts |
| AC4 — rejection and no silent repair | Anonymous/CUSTOMER UI/API; 400/401/403/404/413/415; tamper 503/missing 404 retain metadata/hash; no tampered download |
| AC5 — isolated and reproducible evidence | Per-run results/CLI, unique resources, ownership-checked SQL/cleanup, observed nonzero failures, no dev writes |
| AC6 — regression and scope | Auth and frontend regression pass; no algorithm/schema/validation/conversion implementation changes |

All six functional ACs have technical PASS evidence. The owner subsequently
accepted WDM-01.8 and asked why WDM-01.6 was still unchecked. Its earlier pending
state reflected missing acceptance recording, not missing implementation. The
checklist now closes WDM-01.6 through the accepted gate's real export/upload UI
evidence and that reconciliation request. WDM-01.3 remains separately pending;
no further implementation was authorized in this acceptance turn.
Cross-browser, production TLS/deployment, migration upgrade,
100 MiB/concurrent capacity and hostile OS filesystem races remain NOT VERIFIED.
No unit/backend Maven suite was newly claimed from packaging. Research contracts,
R6 and numeric comparator questions are unchanged. No stage/commit/push occurred.

Reusable test source, synthetic fixtures and configuration are suitable for Git;
generated output and browser auth state are excluded. No disposable helper script
was created. Per-run evidence is retained locally; no older evidence was removed.

## Follow-up — Vietnamese CSV opened directly in Excel

On 2026-11-05 the owner reported garbled `product_name_snapshot` text and
confirmed opening the downloaded CSV directly in Excel. A read-only development
DB check found utf8mb4 server/column settings and correct Vietnamese sample
values/UTF-8 hex. The exporter already encoded text as UTF-8, but its original
BOM-free format did not provide Excel's direct-open encoding signature.
[Microsoft's Excel guidance](https://support.microsoft.com/en-gb/excel/opening-csv-utf-8-files-correctly-in-excel)
states that UTF-8 CSV saved with BOM opens normally in Excel.

The requested correction deliberately supersedes the original export byte policy:
`OrderCsvWriter` emits one leading UTF-8 BOM, exactly `EF BB BF`, through the same
byte-bound check as all other content. Content-Length and max-bytes include these
three bytes, including for header-only exports. The header, 13 columns, quoting,
CRLF, historical text, period filtering and numeric values retain their contracts.
There is no frontend-only Blob modification: direct API downloads get identical
Excel-compatible bytes. Upload/raw storage still preserve the file exactly;
acquisition hash/size naturally include its BOM. Existing files/snapshots are not
rewritten. CSV v1 already allows input both with and without a leading BOM, so
the contract version is retained and BOM-free input fixtures stay valid.

MVC regression covers the leading signature exactly once, the historical name
`Bàn phím "Cơ", tiếng Việt`, double-quote/comma escaping, Content-Length and byte
limits at/under the header+BOM boundary. The real Auth HTTP test now expects the
signature. The Dataset browser fixture also uses that historical name and checks
its UTF-8 DB hex independently before exporting, then compares the complete
download/upload/raw bytes and SHA-256.

The first new browser run correctly rejected corrupt Vietnamese fixture data:
the runner's mysql CLI had used default latin1 for incoming UTF-8 text. The
shared runner now explicitly uses `--default-character-set=utf8mb4`; this changes
only the isolated test connection, not development data. The exact-byte assertion
remains in place, and both browser gates are rerun after this shared setup fix.
Earlier failed-run evidence is retained separately. No production DB rewrite,
migration, mining or CSV validation/conversion work is part of this correction.

The backend process must load the updated code before the user exports a new
file. Previous downloaded CSVs and acquired raw files keep their previous bytes.
Actual desktop Excel interaction remains NOT VERIFIED; automated checks verify
the documented encoding signature and lossless Vietnamese bytes end to end.

Verification after the correction:

| Check | Result |
|---|---|
| `mvn -B -pl backend -am '-Dtest=OrderExportControllerTest,WdmAuthorizationIntegrationTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Ddebug=false' test` | 50/50 PASS: 15 MVC export cases and 35 real Auth/HTTP/MySQL cases |
| `mvn -B -pl backend -am package -DskipTests` | PASS, preparation only |
| Dataset browser gate | 7/7 PASS, exit 0; `.agent-workspace/phase-wdm/WDM-01.8-q3gwth/RESULT.json` |
| Updated fixture | 755 bytes, including BOM and Vietnamese quoted name; export/upload/raw bytes match, SHA-256 `21d2ab63afb43836fb4a8c59c3c518a2411ee0c6c5382481367093a4c97e9239` |
| Auth browser regression / CRA proxy | 10/10 PASS / PASS, exit 0; `.agent-workspace/phase-wdm/AUTH-06-0Y2UWd/` |
| CSV input fixture checker / node syntax / git diff --check | 6/6 PASS / PASS / PASS |

Eight existing files were changed: the writer, two backend tests, Dataset browser
cases, shared E2E runner, this contract, frontend README and checklist. No source
was added or removed. Frontend runtime sources were unchanged, so unit/type/build
checks were not repeated; the real browser gate used the existing application
build. No development DB write, application restart, snapshot repair or commit/
push was performed. Earlier accepted gate measurements remain historical evidence;
the updated Unicode/BOM fixture deliberately has a different byte count/hash.

## WDM-01 review, refactor and integration preparation

The owner requested a WDM-01 architecture/technical review followed by commit,
merge into `experiment/combo-recommendation-web` and push, explicitly including
`.env.example` and `.vscode/launch.json`. The reviewed integration snapshot also
contains the previously implemented Auth UI/navigation follow-up needed by its
current browser cases. Local `frontend/.env` and all runtime artifacts are excluded.

Review conclusion: the existing controller -> service -> repository/storage ->
public DTO boundaries are appropriate. Transport checks precede acquisition,
ADMIN authorization precedes business access, metadata commits before HTTP 201,
uncertain commit outcomes retain evidence, and raw reads verify exact bytes before
HTTP success. Read DTOs explicitly whitelist fields and do not serialize entities
or private JSON/path metadata. No new generic repository surface, mapper framework,
interface hierarchy or mining dependency is required for these operations.

The frontend API slice was refactored: `datasets.ts` owns HTTP/file transport;
`dataset-response.ts` owns receipt/detail validation and explicit public projection.
UUID, timestamp, SHA, source-name and count predicates are shared. Status/null
rules, exact numeric strings, error codes, API shape and raw bytes are preserved.
Existing API/page tests exercise this boundary rather than duplicating helper code.

Review found two reproducibility issues and corrected them before integration:

- `OrderExportAvailabilityTest` had used the default datasource. It now owns a
  disposable MySQL V2 schema, random test Auth keys and temporary storage. V1/V2
  migration source is unchanged. The original default `BackendApplicationTests`
  smoke test is run through the browser runner's explicit isolated process config;
  invoking that smoke test outside the runner still requires an explicit safe DB.
- Exact CSV fixture CRLF depended on local Git attributes. A tracked `.gitattributes`
  now marks only the small contract CSV fixtures `-text`. Their staged blobs were
  compared byte-for-byte with the fixtures. Data files are not rewritten.

The optional Auth browser regression now invokes `mvn -B -pl backend -am test`
with isolated datasource/keys/storage and a finite deadline, including the smoke
and availability tests previously excluded from the isolated selection. The runner
handles the Maven executable on Windows and other platforms, without claiming a
new cross-platform browser certification. `.env.example` has one complete Auth
origin definition, documented WDM limits and an optional private storage example;
the IDE launcher references the ignored root .env rather than embedding secrets.

Verification on 2026-11-05, before committing the integration snapshot:

| Check | Result |
|---|---|
| Focused Dataset API/upload/detail after refactor | 63/63 PASS |
| Full frontend regression | 199 PASS, 2 existing live opt-in SKIPPED |
| TypeScript / focused ESLint / normal production build | PASS; inherited warnings outside the Dataset slice remain |
| Initial isolated backend selection | 185/185 PASS, mining-core 1182/1182 PASS |
| Full `mvn -B -pl backend -am test` through isolated Auth runner | Backend 187/187 and mining-core 1182/1182 PASS; includes smoke and availability tests, no skips |
| Auth browser | Edge 156.0.4314.58, 12/12 PASS |
| Dataset browser/API/DB | 7/7 PASS; Vietnamese/BOM, fresh DB/file/SHA and failure cases retained |
| Input fixture checker / node syntax / diff checks | 6/6 PASS / PASS / PASS |
| Staging audit | Explicit source/config list; fixture bytes exact; no local env, node_modules, target/build, large acquired data or private key files |
| CRA proxy | NOT VERIFIED this run: port 8080 is owned by an existing service, which was preserved |

Evidence parent: `.agent-workspace/phase-wdm/WDM01-REVIEW-20261105-0df3f269/`.
The Auth run is `AUTH-06-Dpni7r` with `AUTH-06_BACKEND.log`; the Dataset run is
`WDM-01.8-50coh2` with `RESULT.json`. Top-level backend/frontend/build logs are
retained in that same ignored parent. Earlier reports retain their historical
counts/exclusions; the full-run evidence above supersedes those exclusions for
this integration snapshot. Git history records the resulting commit/merge.

No algorithm, threshold, schema, CSV validation/conversion or commerce API feature
was changed. Filesystem/MySQL non-atomicity, buffered-download memory limits,
cross-browser/production/100 MiB concurrency capacity and hostile OS races remain
the documented limitations, not new performance or deployment claims.

## WDM-02.1 — CSV structural parser (2026-11-05)

`services/dataset/csv/OrderCsvParser` is a standalone Java reader for CSV v1.
It takes an InputStream and a Consumer of immutable ParsedCsvRecord values and
returns the number of data records after successfully reaching EOF. The caller
owns/closes the stream. The parser has no Spring, storage, database or HTTP
dependency and does not change acquisition, raw bytes, checksums or snapshot
status. Integration with validation/report persistence belongs to later WDM-02
tasks. It must eventually receive integrity-verified raw content through the
existing storage boundary, not an arbitrary client path.

Structural acceptance is distinct from business validity. All fields remain
strings; empty/whitespace-only values, lengths, typed formats, monetary rules,
catalog references, order conflicts and duplicate identities are deferred to
WDM-02.2/02.3. A successfully parsed business-invalid.csv is intentional, not a
VALID snapshot or permission to convert/mine it. No rows are deduplicated,
reordered, cleaned, expanded by quantity or silently skipped.

### Explicit parser engineering decisions

These are application implementation choices, not paper-derived mining rules:

- Decode UTF-8 with REPORT rather than replacement. Remove at most one leading
  UTF-8 BOM; reject a double leading BOM. Preserve U+FEFF inside data fields.
- Compare the 13 decoded header names case-sensitively and in contract order.
  CSV-quoted header names are allowed if their decoded values match exactly;
  header whitespace is not trimmed. Wrong width is a column-count error; a
  13-field header with wrong names/order is a header-mismatch error.
- Accept CRLF and LF, including mixed record separators. Preserve embedded
  CR/LF/CRLF in quoted fields. Reject bare CR outside quoted fields.
- Quotes may open only at field start; double quotes escape a quote inside a
  quoted field. After a closing quote only an escaped quote, comma, record
  separator or EOF is allowed. Do not accept spaces after a closing quote.
- Accept a complete last record without a trailing newline. A final record
  separator does not create an extra record. Blank physical records outside
  quoted fields are not ignored: they fail the 13-field width check.
- Header-only content is structurally readable with zero data records; empty
  or BOM-only content fails with a missing-header error. Downstream policy for
  zero usable orders is not decided by this task.
- Fail on the first structural error. The Consumer may already have received a
  provisional prefix. Callers must stage/discard effects until parse returns
  normally; a prefix is never a successful complete dataset. Do not write final
  artifacts or mark VALID from individual callbacks. Consumer failures and
  source I/O failures propagate; source I/O failure is not mislabeled bad CSV.
- Each invocation has independent cursor state. The parser holds one record at
  a time plus bounded decoder buffering. A positive maxRecordCharacters is
  mandatory at construction: this is a caller-selected resource budget, not
  a new CSV-v1 business limit. Count UTF-16 code units including delimiters,
  quotes and record terminators, excluding the optional leading BOM. Budget
  rejection must not be presented as evidence that CSV syntax is malformed.
  No application-wide default/configuration is introduced here. A caller that
  collects every emitted record still incurs whole-dataset memory usage.

### Diagnostics and traceability

Records carry a 1-based logical recordNumber (header = 1, first data record = 2)
and inclusive physical startLine/endLine. CRLF counts as one line break, also
inside quoted fields. CsvStructureException carries only a fixed code and these
positions, without raw fields, values, paths or a decoder exception cause.

Codes: CSV_INVALID_UTF8, CSV_INVALID_BOM, CSV_MISSING_HEADER,
CSV_HEADER_MISMATCH, CSV_UNCLOSED_QUOTE, CSV_INVALID_QUOTE,
CSV_INVALID_RECORD_SEPARATOR, CSV_COLUMN_COUNT_MISMATCH,
CSV_RECORD_LIMIT_EXCEEDED. These are parser diagnostics, not new public HTTP
error codes or a persisted validation-report schema. Decoder read-ahead means
INVALID_UTF8 positions describe the parser cursor when decoding failed, not an
exact offending byte/record location; consumers must retain this distinction.

### Verification and task boundary

Unit tests cover the maintained valid/header-only/business-invalid fixtures,
writer-to-parser field round-trip, Unicode/BOM/quoting/newlines, malformed UTF-8,
header/width failures, physical positions, immutable output, fail-fast prefix,
stream ownership, consumer/I/O failures and explicit record-budget boundaries.
No fixture or live database is seeded or rewritten by the parser tests.
Verification performed on 2026-11-05:

| Check | Result |
|---|---|
| Initial focused OrderCsvParserTest | 34/34 PASS |
| Final focused OrderCsvParserTest, including single-byte stream and quote-at-EOF cases | 36/36 PASS, no skips |
| Selected backend regression | 122/122 PASS, no skips within selection; includes initial 34 parser cases |
| Mining-core regression in that selected reactor run | 1182/1182 PASS, no skips |
| Existing offline CSV fixture checker | 6/6 PASS |
| Diff whitespace check | PASS |
| Full DB-backed backend regression / smoke / browser | NOT VERIFIED this task |

Commands from repository root (quote Maven properties in PowerShell):

```powershell
mvn -pl backend -am '-Dtest=OrderCsvParserTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -pl backend -am '-Dtest=*,!BackendApplicationTests,!AuthSessionIntegrationTest,!WdmAuthorizationIntegrationTest,!RegistrationIntegrationTest,!OrderExportRepositoryTest,!OrderExportAvailabilityTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
python backend/src/test/resources/wdm/csv-v1/verify_contract.py
git diff --check
```

The unrestricted reactor invocation was interrupted during mining-core before
backend execution: BackendApplicationTests uses the default application DB,
which must not be migrated/accessed as an incidental parser check. Docker was
unavailable (Docker Desktop Linux engine pipe not present), so the selected run
explicitly excluded that smoke class and the five MySQL/Testcontainers classes
listed in the command. These exclusions are NOT VERIFIED, not successful or
JUnit-skipped tests. No development DB or owner service was changed. The final
two test-only additions were verified in the final focused run; production
parser source was unchanged after the selected regression. The fixture checker
is contract evidence, not HTTP/DB integration evidence. Surefire reports remain
in the owning modules' ignored target directories; no clean/delete was run.

Owner accepted WDM-02.1 in chat on 2026-11-05 and requested planning WDM-02.2.
The checklist marks 02.1 complete; this does not close WDM-02 as a whole or
BLK-02, nor verify the excluded DB/smoke/browser checks. No migration, dependency, frontend, Auth, threshold, algorithm or
scientific benchmark change is included.

## WDM-02.2 — Row business validation (2026-11-05)

Implementation is limited to row-level rules and a read-only catalog lookup.
OrderCsvBusinessValidator consumes an accepted ParsedCsvRecord and a set of
exact existing product IDs. It returns an immutable Result: either a typed
ValidatedOrderRow and no issues, or no typed row and a nonempty immutable issue
list. A caller must provide the complete lookup result for the IDs it validates;
an empty set means no matching products, not an unavailable lookup. Lookup/I/O
failure must propagate as an operational failure, not become PRODUCT_NOT_FOUND.
Passing a record with other than 13 fields is programmer misuse and throws;
structure parsing remains the responsibility of WDM-02.1.

### Implemented row rules

- Every field is required. Empty or whitespace-only means all code points
  satisfy Character.isWhitespace or Character.isSpaceChar; NBSP/figure/narrow
  spaces are included. Zero-width space is not silently stripped or classified
  as blank. Values are never trimmed, case-folded or Unicode-normalized.
- Opaque-text limits are 40/80/40/80/200 Unicode code points for order_id,
  customer_code, product_id, sku_snapshot, product_name_snapshot respectively.
  Combining marks count separately. This explicitly matches code-point-oriented
  fixture lengths rather than Java UTF-16 string length.
- order_item_id uses positive ASCII decimal digits with no sign/leading zeros,
  at most 18446744073709551615. quantity has the same lexical rule and is at most
  4294967295. Oversized integers are compared as digit strings before allocation;
  accepted values use BigInteger and long respectively. Zero is an invalid
  positive-integer representation; canonical numbers beyond the maximum are
  VALUE_OUT_OF_RANGE.
- ordered_at must match the four-digit-year CSV-v1 UTC timestamp with exactly
  six fractional digits and uppercase Z. Strict calendar parsing rejects invalid
  dates, 24:00 and leap-second normalization. No API offset normalization, host
  timezone, future-date restriction or acquisition-window filter is applied.
- status is exactly COMPLETED. currency_code is three uppercase ASCII letters;
  this task does not introduce an ISO currency whitelist or exchange rates.
- Each monetary field matches `(0|[1-9][0-9]{0,16})\.[0-9]{2}`. No signs,
  grouping, exponents, extra scale or rounding are accepted. BigDecimal computes
  gross = price * quantity without restricting the intermediate to DECIMAL(19,2).
  When all four numeric inputs are valid, check discount <= gross and compare
  stored line_total exactly with gross - discount. Report both relationship
  failures if both fail; do not replace the stored total. If any of these typed
  inputs is invalid, do not emit arithmetic relationship errors.
- product_id must match a catalog ID exactly. Current product status, price,
  SKU/name and customer existence are not extra eligibility conditions. No live
  ORDER/ORDER_ITEM lookup is required for imported source IDs.

### Diagnostics and product lookup

BusinessValidationIssue contains only code, fixed field name and the source
recordNumber/startLine/endLine. Issues are ordered by CSV column, independently
of validation pass; required/length failure suppresses dependent field checks.
These codes are internal row diagnostics, not new HTTP response codes or a
persisted report schema:

FIELD_REQUIRED, FIELD_TOO_LONG, INVALID_INTEGER_FORMAT, VALUE_OUT_OF_RANGE,
STATUS_NOT_COMPLETED, INVALID_TIMESTAMP, INVALID_MONEY_FORMAT,
DISCOUNT_EXCEEDS_GROSS, LINE_TOTAL_MISMATCH, INVALID_CURRENCY_CODE,
PRODUCT_NOT_FOUND.

ProductReferenceLookup separates catalog access from the pure validator.
RepositoryProductReferenceLookup is a Spring read-only transactional adapter
using ProductRepository.findReferenceCandidates, an ID-only JPQL projection.
It sorts requested distinct IDs, executes queries in chunks of at most 256 IDs,
and intersects returned IDs using exact Java string matching. Case/accent/space
equivalence under database collation must not validate a different CSV ID. The
immutable result iterates in sorted request order. Empty requests do not query.
There is no per-row catalog query, full catalog/entity load or product-status
filter. Failures propagate without being relabeled or automatically retried.
Only safe aggregate counts are logged at DEBUG; no row/ID/value is logged.
The 256-ID chunk is an engineering query budget, not a data eligibility limit.

This lookup checks the catalog at execution time. It does not establish a
catalog version or a consistent view across a future entire validation job;
WDM-02.5 owns orchestration, transaction scope and repeat-operation policy.
The parser's provisional-prefix contract still applies: individually valid
rows must not be persisted/used as a successful dataset before parsing reaches
EOF normally. Query chunking bounds individual queries, not the caller's total
requested-ID set. Large-job buffering/caching is not implemented by this task.

### Scope and verification boundary

Tests compose the real parser with this validator: valid.csv yields five valid
rows; empty.csv yields none; business-invalid.csv yields PRODUCT_NOT_FOUND for
the unknown product and four row-valid records. The latter still include an
order-level conflict and repeated line identity, deliberately left to WDM-02.3.
No order is dropped, merged or marked globally valid here. Cleaning, report/API,
snapshot status, UI and conversion gates remain WDM-02.3 through 02.7.

ProductReferenceLookupIntegrationTest uses its own MySQL 8.4 container, only the
three catalog CREATE definitions from V2, actual JPQL and a Spring transactional
lookup bean. It tests case/accent-insensitive collation candidates and existing
DISCONTINUED/DRAFT products without overwriting historical data. It is explicitly
disabled without Docker, and skipped checks must be reported as NOT VERIFIED.
It never loads application/.env datasource settings or executes migrations on
the development database. The standalone adapter tests use mocks and are unit
tests, not proof of MySQL or Spring transaction integration.

The Unicode blank predicate, code-point lengths, diagnostic/result model and
query chunking are documented engineering choices under the authorized plan;
the business rules come from the accepted CSV-v1 application contract. They
are not mining-paper formulas. No accepted parser source, mining behavior,
schema, dependency, Auth, frontend or upload lifecycle is changed.

### Verification evidence and remaining checks

| Check on 2026-11-05 | Result |
|---|---|
| Business validator unit tests, including parser/fixture composition | 65/65 PASS |
| Repository lookup unit tests (mock repository) | 4/4 PASS |
| Accepted structural parser regression | 36/36 PASS |
| Focused selection including MySQL integration | 105 PASS, 2 SKIPPED, no failures/errors |
| Selected backend regression | 193 PASS, 2 SKIPPED, no failures/errors |
| Mining-core regression in the selected reactor run | 1182/1182 PASS, no skips |
| Existing offline fixture checker | 6/6 PASS |
| Diff whitespace check | PASS |
| MySQL JPQL/collation/read-only transaction runtime | NOT VERIFIED: 2 tests skipped because Docker engine is unavailable |
| Full DB-backed backend suite / browser | NOT VERIFIED this task |

Commands from repository root:

```powershell
mvn -pl backend -am '-Dtest=OrderCsvBusinessValidatorTest,RepositoryProductReferenceLookupTest,ProductReferenceLookupIntegrationTest,OrderCsvParserTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -pl backend -am '-Dtest=*,!BackendApplicationTests,!AuthSessionIntegrationTest,!WdmAuthorizationIntegrationTest,!RegistrationIntegrationTest,!OrderExportRepositoryTest,!OrderExportAvailabilityTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
python backend/src/test/resources/wdm/csv-v1/verify_contract.py
git diff --check
```

The selected regression explicitly excludes the default-datasource smoke class
and five existing MySQL classes shown in the command. These are exclusions,
not successful tests. The new ProductReferenceLookupIntegrationTest is included
but its two tests are JUnit-skipped without Docker. Docker Desktop's Linux
engine pipe was unavailable; no development DB connection, migration or owner
service restart was used as a substitute. Surefire reports are retained under
backend/target/surefire-reports and mining-core/target/surefire-reports. Existing
Mockito dynamic-agent warnings remain; no test dependencies were changed.

Initial implementation and unit/selected regression checks were complete; task
status at that point was PARTIAL pending MySQL verification (superseded by the
follow-up below). Owner acceptance is not recorded, WDM-02.2 remains unchecked,
and BLK-02 is not closed. No WDM-02.3
implementation, cleanup deletion, stage, commit or push is included.

### Follow-up — MySQL integration verified (2026-11-05)

After the owner enabled Docker and requested the targeted run,
ProductReferenceLookupIntegrationTest completed with **2 tests, 0 failures,
0 errors, 0 skipped**; Maven BUILD SUCCESS, exit 0. Runtime: Docker 29.7.2,
MySQL 8.4.13 from mysql:8.4, isolated database wdm_business_test on an ephemeral
host port. This supersedes only the previous NOT VERIFIED status for this
class; previous skipped-run counts and broader-suite exclusions remain
historical evidence, not retroactively changed totals.

```powershell
mvn -pl backend -am '-Dtest=ProductReferenceLookupIntegrationTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

The real JPQL query returned case/accent-insensitive candidates and the Spring
transactional adapter rejected non-exact IDs. Exact existing DISCONTINUED/DRAFT
product IDs were accepted while historical name/amount stayed unchanged. Only
the three catalog definitions from V2 and synthetic fixture rows were created
inside the test container; no development DB or owner service was accessed.
No production/test source changes were required. The run did not rerun all
mining-core/backend tests or browser tests; the earlier selection evidence
remains applicable without a new full-suite claim.

Evidence: backend/target/surefire-reports/TEST-vn.edu.wspm.backend.services.dataset.csv.ProductReferenceLookupIntegrationTest.xml
and its corresponding text report. **Current WDM-02.2 status: TECHNICAL PASS /
OWNER ACCEPTED (2026-11-05).** Owner accepted WDM-02.2 in chat after the MySQL
follow-up and requested planning WDM-02.3. The checklist marks 02.2 complete;
BLK-02 remains open for order consistency and the remaining WDM-02 work.

## WDM-02.3 — Order consistency (2026-11-05)

The owner accepted the Order Consistency plan and authorized this task separately.
OrderConsistencyChecker is a pure Java checker for one complete snapshot. It
does not modify the accepted parser/business validator, query a database, clean
rows, create artifacts, expose an endpoint or mark a snapshot valid.

### Input and preservation of business errors

OrderConsistencyInput pairs an original ParsedCsvRecord with that same record's
trusted WDM-02.2 Result. Callers must not pair stale or unrelated results, forge
diagnostics or discard business-invalid rows. The handoff rejects non-data
records, wrong field counts, invalid position ranges and mismatched result/issue
positions. It checks metadata correspondence, not cryptographic/content identity;
same-position content substitution remains caller misuse. No catalog lookup or
duplicate copy of the business-validation formulas is performed by the checker.

All inputs, including rows without usable keys, remain in the immutable result.
A field is comparable/indexable only when its matching business result has no
issue for that field. Thus an unknown product or bad monetary row can still
contribute valid order/line identity and common metadata. Invalid IDs are not
indexed; invalid field values never become evidence of a content/common-field
conflict. Existing 02.2 issues remain attached unchanged to their original row.

Canonical CSV-v1 integer, timestamp and money spellings make exact string
comparison equivalent for accepted values here. This preserves large unsigned
line identities without floating point or alternate conversion/rounding rules.
Opaque IDs/text use exact Java string equality, without trimming, case folding,
Unicode normalization or a database collation.

### Findings and internal links

| Finding | Rule |
|---|---|
| ORDER_FIELD_CONFLICT | Same usable order_id, distinct comparable customer_code, ordered_at or currency_code values; one finding per field |
| DUPLICATE_LINE_ID | A usable order_item_id occurs more than once anywhere in the snapshot, including identical repetitions |
| LINE_ID_ORDER_CONFLICT | One repeated line ID has more than one usable order_id |
| LINE_ID_CONTENT_CONFLICT | A repeated line ID has distinct comparable values in a payload field other than order_id/order_item_id; one finding per field |

Every valid status is already COMPLETED under 02.2; invalid statuses keep their
business errors rather than generating a different status policy. Same product
on different line IDs is not a duplicate. Equal timestamps on different orders
do not merge orders. No match depends on adjacent rows in the CSV.

A repeated group always receives DUPLICATE_LINE_ID. Cross-order and payload
findings may additionally apply; order_id differences use LINE_ID_ORDER_CONFLICT
rather than a second payload finding. Absence of a payload finding does not prove
all rows identical when some fields are business-invalid. Findings list all
comparable source positions for the field; duplicate findings list all repeated
positions. Invalid-field rows remain in the result even when omitted from that
specific finding's evidence. No pairwise expansion of group findings is used.

OrderConsistencyIssue exposes only fixed code/field and numeric source positions,
never raw values or order/customer IDs. The internal Result retains source rows
and affectedOrderIds to support 02.4. This set includes every usable order ID
involved in a consistency finding, not all orders with 02.2 business errors and
not a rejection list. The cleaning stage must combine retained business errors,
consistency findings and its own accepted policy. These internal objects must
not be exposed directly as HTTP DTOs. Their toString summaries exclude raw data.

Rows and position lists are sorted by source recordNumber; duplicate record
numbers are rejected as caller misuse. Findings are ordered by their first
contributing record number, code declaration order, then CSV column order.
Affected order IDs iterate in deterministic Java string sort order. Lists and
sets are defensively copied/unmodifiable. Reordering an input collection does
not change the result for the same source-numbered records. Group indexes and
retained rows use memory proportional to input size; no large-job capacity or
performance claim is made. Callers must supply the complete parsed snapshot,
after parser EOF succeeds; hasConsistencyIssues is not a dataset-valid predicate.

### Scope and evidence boundary

Conflict/duplicate categories, internal handoff/result models and deterministic
group reporting are documented engineering choices under the accepted plan.
The underlying contract/fixtures require reporting contradictory order metadata
and repeated line identity. CT-04's order-level integrity policy is preserved:
this task identifies affected groups but does not execute discard or merging.
Those decisions/operations remain WDM-02.4. Report persistence/API, UI and the
conversion gate remain WDM-02.5 through 02.7; BLK-02 is not closed here.

Tests exercise nonadjacent conflicts, all common/payload fields, identical
repetitions, global cross-order identity, unsigned IDs, invalid-field evidence,
source/metadata preservation, immutable/safe diagnostics, and real parser →
business-validator → checker fixture composition. valid.csv has five retained
rows and no consistency findings. business-invalid.csv retains all five rows,
the unknown-product business error, one customer_code ORDER_FIELD_CONFLICT
(records 3/4) and one DUPLICATE_LINE_ID (records 5/6). No fixture is cleaned or
changed, and no database is needed by the checker itself.

Verification performed on 2026-11-05:

| Check | Result |
|---|---|
| Initial focused checker + business + parser selection | 131/131 PASS (30 + 65 + 36), no skips |
| Final OrderConsistencyCheckerTest, including large unsigned identity case | 31/31 PASS in selected regression |
| Selected backend regression | 226/226 PASS, no failures/errors/skips |
| ProductReferenceLookupIntegrationTest in that selection | 2/2 PASS on isolated MySQL 8.4.13, no skips |
| Mining-core regression in the selected reactor run | 1182/1182 PASS, no skips |
| Existing offline fixture checker | 6/6 PASS |
| Diff whitespace check | PASS |
| Full DB-backed backend suite / browser | NOT VERIFIED this task |

```powershell
mvn -pl backend -am '-Dtest=OrderConsistencyCheckerTest,OrderCsvBusinessValidatorTest,OrderCsvParserTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -pl backend -am '-Dtest=*,!BackendApplicationTests,!AuthSessionIntegrationTest,!WdmAuthorizationIntegrationTest,!RegistrationIntegrationTest,!OrderExportRepositoryTest,!OrderExportAvailabilityTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
python backend/src/test/resources/wdm/csv-v1/verify_contract.py
git diff --check
```

The selected command explicitly excludes the default-datasource smoke test and
five existing DB integration classes. Those are exclusions, not successful or
JUnit-skipped tests. The relevant product lookup integration is included and
actually ran after Docker was available. No development DB or owner service
was used, stopped or migrated. Evidence is retained in the owning modules'
ignored target/surefire-reports directories, including
TEST-vn.edu.wspm.backend.services.dataset.csv.OrderConsistencyCheckerTest.xml.
No clean/delete, new dependency, stage, commit or push was performed.

**Current WDM-02.3 status: IMPLEMENTED / TECHNICAL PASS / OWNER ACCEPTED
(2026-11-05).** Owner accepted the implementation result in chat and requested
planning WDM-02.4. The checklist marks 02.3 complete. Cleaning implementation
has not been started; BLK-02 remains open for the remaining WDM-02 work.

## WDM-02.4 — Cleaning pipeline (2026-11-05)

The owner accepted the cleaning plan/policy and authorized implementation in
chat. **Policy version: WDM_ORDER_CLEANING_V1.** The policy is an explicit
application data decision, not a mining-paper formula or change to the accepted
engines. It resolves the cleaning/deduplication choices previously reserved by
CSV v1 for this task; export/upload still preserve original lines and bytes.

OrderCleaningPipeline consumes a complete, matching OrderConsistencyChecker
Result after parsing reaches EOF normally. It reuses the accepted checker to
verify that the supplied findings/links match its rows, rejecting stale/omitted
findings as caller misuse. This does not prove that an entire file was supplied,
validate a substituted same-position business result, recheck catalog existence
or replace storage integrity verification. Those remain upstream handoff/job
responsibilities. The parser, business validator and checker sources are unchanged.

### Approved policy and processing order

1. If any row lacks a usable order_id under 02.2 (including an overlong ID),
   block the entire cleaning result. Return no clean orders or partially
   processed rejection list. Every row receives BLOCKED: the unassignable rows
   have UNATTRIBUTABLE_ORDER_ID, all other rows SNAPSHOT_BLOCKED. Preserve the
   complete original findings/business errors in source.
2. Otherwise, index all orders before selecting any representatives. Every
   02.2 business-invalid row rejects its entire order (BUSINESS_RULE_FAILURE),
   including valid lines and exact duplicate copies from that order.
3. ORDER_FIELD_CONFLICT rejects its order. LINE_ID_ORDER_CONFLICT and
   LINE_ID_CONTENT_CONFLICT reject all involved orders. Whole-order rejection
   reasons accumulate in fixed enum order; a later conflict can reject an order
   seen earlier. Never interpret affectedOrderIds or DUPLICATE_LINE_ID alone
   as a rejection list.
4. For surviving orders, collapse only repetitions of the same line ID whose
   entire 13-field source payload matches exactly and whose rows are all
   business-valid. Retain the lowest source recordNumber, preserve all source
   positions and point each duplicate disposition to that representative.
   Never sum the same line's quantity or money again. Any unexpected retained
   payload mismatch fails closed as a handoff error.
5. Different line IDs remain separate even when product_id is the same. Build
   ProductGroup views referencing those unchanged lines. Different historical
   prices, SKU/name, quantities, discounts and totals are retained; do not invent
   an aggregate unit price or choose one historical text for other lines.

The pipeline never repairs values, changes customer identity, recomputes stored
line amounts, invents temporal order or introduces mining item IDs/SPMF. Input
and output are in-memory commercial-data models only; no file/DB write or
snapshot status transition is performed. Raw bytes/checksums remain untouched.

### Output, accounting and readiness

OrderCleaningResult retains policyVersion, outcome, clean orders, rejected-order
summaries, one disposition per input record, and the original consistency result.
The four internal outcomes are READY (some usable orders), EMPTY_INPUT,
EMPTY_AFTER_CLEANING and BLOCKED_UNATTRIBUTABLE_ORDER. The latter three expose no
clean orders. hasUsableOrders is cleaning readiness only, not a persisted VALID
state or authorization to run mining. Subsequent report/conversion gates must
handle empty/blocked results explicitly.

Disposition actions partition every source record exactly once:

- KEPT: representative source line, representative position points to itself.
- DEDUPLICATED: identical repeat, representative points to its retained line;
  reason IDENTICAL_LINE_REPETITION. The retained line's origins include both
  itself and all repeats, without creating extra retained commercial lines.
- REJECTED_ORDER: every source row in a rejected order, including duplicates;
  no representative. Reasons describe the whole rejected order; original row
  errors and consistency evidence remain available through source.
- BLOCKED: no row was cleaned because an order cannot safely be identified;
  no representative or partial usable data is returned.

CleanedOrder stores order/customer/time/currency metadata, unique retained lines
and product-group views. Each line retains its unchanged ValidatedOrderRow and
all source positions. Collections are defensively copied/unmodifiable. Internal
models contain commercial data and must not be returned directly as HTTP DTOs;
their toString summaries expose only counts, fixed reasons and numeric positions.

Orders/rejection summaries follow first source occurrence, unique lines follow
their representative source position, product groups follow first retained line,
and dispositions are globally sorted by source recordNumber. No chronology sort
is introduced: WDM-03 owns conversion/sequence ordering. Shuffling an input
collection with the same source positions does not alter the result. Re-cleaning
only retained lines produces no further drops/deduplication; previous discarded
origins are not reconstructed from that reduced input. Whole-snapshot indexes,
retained evidence and the checker verification require memory for the supplied
dataset; no capacity/performance claim is made.

### Fixture expectations and task boundary

valid.csv yields three clean orders, five retained lines; the first order has
two distinct line IDs grouped under one product. empty.csv yields EMPTY_INPUT.
business-invalid.csv rejects order-unknown and order-conflict (three source
rows total), retains one line for order-duplicate (record 5), and records record
6 as DEDUPLICATED. Thus KEPT=1, REJECTED_ORDER=3, DEDUPLICATED=1, BLOCKED=0;
all five original rows and their errors/findings remain available for reporting.

The whole-order rejection principle is CT-04. The exact duplicate, product-view,
unassignable-key and empty-result policies are owner-approved application
decisions; concrete result models, fixed reason order and checker handoff
verification are engineering details recorded for reproducibility. WDM-02.5
still owns report persistence/API and lifecycle; UI/gates remain 02.6/02.7.
BLK-02 is not closed by this task alone. No source research formula, mining
threshold, algorithm, schema, dependency, Auth or frontend change is included.

Verification performed on 2026-11-05:

| Check | Result |
|---|---|
| OrderCleaningPipelineTest | 32/32 PASS |
| Focused cleaning + consistency + business + parser selection | 164/164 PASS, no skips |
| Selected backend regression | 258/258 PASS, no failures/errors/skips |
| ProductReferenceLookupIntegrationTest in that selection | 2/2 PASS on isolated MySQL 8.4.13 |
| Mining-core regression in the selected reactor run | 1182/1182 PASS, no skips |
| Existing offline fixture checker | 6/6 PASS |
| Diff whitespace check | PASS |
| Full DB-backed backend suite / browser | NOT VERIFIED this task |

```powershell
mvn -pl backend -am '-Dtest=OrderCleaningPipelineTest,OrderConsistencyCheckerTest,OrderCsvBusinessValidatorTest,OrderCsvParserTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -pl backend -am '-Dtest=*,!BackendApplicationTests,!AuthSessionIntegrationTest,!WdmAuthorizationIntegrationTest,!RegistrationIntegrationTest,!OrderExportRepositoryTest,!OrderExportAvailabilityTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
python backend/src/test/resources/wdm/csv-v1/verify_contract.py
git diff --check
```

The regression excludes the default-datasource smoke class and five existing
DB integration classes explicitly listed above. Exclusions are not successful
or JUnit-skipped tests. Product lookup integration is included and actually ran
on its disposable MySQL container. No development DB or owner service was used,
stopped or migrated. Surefire evidence remains in the owning modules' ignored
target/surefire-reports directories, including
TEST-vn.edu.wspm.backend.services.dataset.csv.OrderCleaningPipelineTest.xml.

**Current WDM-02.4 status: IMPLEMENTED / TECHNICAL PASS / OWNER ACCEPTED
(2026-11-05).** Owner accepted the implementation in chat and requested planning
WDM-02.5. The checklist marks 02.4 complete. WDM-02.5 has not been started;
no clean/delete, source refactor, dependency change, stage, commit or push is included.

## WDM-02.5 — Persisted validation report and APIs (2026-11-05)

Owner approved the plan and authorized implementation. ValidationReportEngine
connects the unchanged accepted 02.1–02.4 stages after RawSnapshotStorage verifies
the original bytes. Version WDM_VALIDATION_REPORT_V1 binds snapshotId, rawSha256,
rawSizeBytes, CSV contract and cleaning policy WDM_ORDER_CLEANING_V1. completedAt
is server UTC report-generation completion, not a precise DB commit timestamp.

### Status, counters and lineage

| Outcome | Persisted validation_status |
|---|---|
| Cleaning READY, at least one usable order | VALID, including a cleaned subset of dirty raw input |
| EMPTY_INPUT / EMPTY_AFTER_CLEANING / BLOCKED_UNATTRIBUTABLE_ORDER | INVALID |
| Structural CSV error | INVALID, countsComplete=false, no manifest |
| Storage/checksum/catalog/budget/DB failure | Operational error; no fabricated INVALID report |

VALID means a usable cleaned subset, not that every raw record is valid.
business-invalid.csv therefore produces VALID with keptRecords=1,
rejectedRecords=3 and deduplicatedRecords=1 while preserving original diagnostics.
No raw file, sequence/mapping artifact or sequence statistics are modified.

Counts are decimal integer strings. After structural failure, parsedRecords is
only the complete prefix emitted by the parser; inputRecords/inputOrders and
uncomputed cleaning counters are null, never invented zeros/totals. No partial
manifest is persisted. Invalid UTF-8 positions have DECODER_CURSOR accuracy,
not an exact offending byte/record guarantee. With complete parsing, knownOrders
counts usable keys; inputOrders is null if a key is unusable, and rejectedOrders
is null for blocked cleaning. businessInvalidRecords counts rows, issueCount
counts diagnostic entries; one grouped issue can reference many records.

Diagnostics contain only stage/code/fixed field/numeric source positions. Sort
is first source record (empty-position summaries last), stage, code, then field.
The private manifest retains every complete source record's disposition,
representative and reasons. WDM-03 can use retained representatives from the
checksum-bound raw without repeating live catalog decisions; it must still
verify raw integrity and supported report/policy versions before conversion.

### Persistence, repeat operations and concurrency

Existing V2 conversion_report_json gains a validation namespace while preserving
all other JSON members/provenance. SnapshotValidationRepository is separate
from acquisition-only persistence. There is no migration, new table/artifact,
VALIDATING status, job or worker. Short REQUIRES_NEW publication locks the row
with NOWAIT hint and a five-second transaction timeout, verifies PENDING and
unchanged raw reference/hash/root JSON/no conversion artifacts, and flushes
report/status together. Computation and raw I/O occur outside that transaction.

Per-instance same-snapshot exclusion returns 409 when busy; a bounded semaphore
limits concurrent computations. Different instances may compute concurrently;
only the first publication wins. A completed winner is verified/replayed rather
than overwritten. Stale metadata/state yields 409. NOWAIT/DB failure may return
503; no distributed exactly-once execution or transparent retry is claimed.
A real ambiguous commit can have succeeded despite a 503: read status before
retrying. A crash before publication leaves PENDING.

Replay verifies stored snapshot/hash/size/version/policy/status bindings, skips
file/catalog work, and never revalidates automatically when catalog changes.
It is historical evidence, not a fresh attestation of physical raw bytes.
No force/overwrite mode is added; use a new upload for new validation. Reports
can be read/replayed after conversion without mutating or rerunning validation.
Unsupported legacy/state/version or mismatched report returns 409.

### API and budgets

POST/GET `/api/v1/admin/dataset-snapshots/{snapshotId}/validation` use the existing
ApiResponse envelope, request correlation, Cache-Control: no-store and WDM
shared-Auth ADMIN guard. POST accepts no body/query and returns 200 after commit,
even for INVALID data. GET returns summary plus a diagnostic page; no completed
report returns 409 INVALID_RESOURCE_STATE. That ErrorCode now implements the
409 convention already defined by the WDM technical contract. Malformed/missing
snapshot IDs use existing 400/404 conventions.

GET accepts only canonical nonnegative page and size=1..100 (default 20).
Unknown/repeated/out-of-range query parameters fail 400. A page beyond the end
is empty with exact totals. ValidationResponse hides the internal manifest,
storage paths, raw values and catalog/customer IDs.

Defaults under wdm.validation map to WDM_VALIDATION_* environment variables:

| Setting | Default |
|---|---|
| max-record-characters | 16384 UTF-16 units, including syntax/terminators |
| max-rows | 100000 complete records |
| max-raw-bytes | 16777216 (16 MiB), separate from upload transport limit |
| max-report-bytes | 8388608 (8 MiB), entire application-serialized root JSON |
| max-duration-millis | 30000; positive and at most 300000 |
| max-concurrent | 2 per instance |

Budget/capacity rejection is 503, not INVALID data. Diagnostics/manifest are
never silently truncated. JSON size is measured into a bounded byte-counting
sink before creating the persistence Map, without an unlimited JSON byte array.
Exact byte boundaries are tested. The raw and pipeline object graphs remain
buffered up to their input budgets; no high-volume capacity claim is made.

Time/interruption checks are cooperative at stages, parser callbacks, validation
rows and JSON writes, with a final check before publication. They cannot forcibly
interrupt already-blocked filesystem/JDBC calls and are not a hard HTTP deadline.
The publication transaction has its own timeout. No performance claim is made.

### Verification evidence

| Check on 2026-11-05 | Result |
|---|---|
| Final new engine/service/MVC/MySQL tests | 26/26 PASS (8 + 8 + 4 + 6), no skips |
| Broad backend regression before final byte-counting refinement | 379/379 PASS; excludes only BackendApplicationTests and OrderExportAvailabilityTest |
| Final selected backend regression after refinement + two boundary/interruption tests | 284/284 PASS, no failures/errors/skips |
| Mining-core regression | 1182/1182 PASS, no skips |
| Existing offline fixture checker | 6/6 PASS |
| Diff whitespace check | PASS |
| Default-datasource smoke/availability, full unrestricted suite, browser/UI | NOT VERIFIED this task |

```powershell
mvn -pl backend -am '-Dtest=ValidationReportEngineTest,SnapshotValidationServiceTest,SnapshotValidationControllerTest,SnapshotValidationIntegrationTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -pl backend -am '-Dtest=*,!BackendApplicationTests,!OrderExportAvailabilityTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -pl backend -am '-Dtest=*,!BackendApplicationTests,!AuthSessionIntegrationTest,!WdmAuthorizationIntegrationTest,!RegistrationIntegrationTest,!OrderExportRepositoryTest,!OrderExportAvailabilityTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
python backend/src/test/resources/wdm/csv-v1/verify_contract.py
git diff --check
```

The broad selection exercised full application wiring and existing isolated
Auth/repository MySQL tests. Its final local refinement only replaced JSON size
measurement; final focused and selected runs verify that version. Excluded
classes are not successful or JUnit-skipped tests. Compile-only success and the
initial test-stubbing failure are not acceptance evidence.

SnapshotValidationIntegrationTest uses real storage/catalog/JPA/transactions on
isolated MySQL 8.4.13, with four exact V2 table definitions and synthetic data.
It verifies readback/provenance, INVALID structural/empty reports, checksum
tampering/retry, actual pre-commit failure preserving both PENDING and absence
of report, competing instances, and stale-state rejection. The fault explicitly
rolls back before raising its synthetic commit error, not a proof about rollback
of ambiguous real network commits. No development DB, Flyway migration or owner
service was used/stopped/restarted. Current-task test containers/temp storage
are disposed by Testcontainers/JUnit; Surefire evidence remains under ignored
target/surefire-reports, including the four new test-class reports.

MVC tests use a mock service/test authorizer: they verify ADMIN guard ordering
and HTTP/JSON behavior, not shared-Auth browser E2E. No existing parser/business/
checker/cleaning source, mining formula, schema, dependency, Auth implementation
or frontend is changed. Architecture text is updated for the new backend API.
WDM-02.6 UI and 02.7 conversion gates remain unimplemented; BLK-02 stays open.

**Current WDM-02.5 status: IMPLEMENTED / TECHNICAL PASS / OWNER ACCEPTED
(2026-11-05).** Owner accepted the result in chat and requested planning WDM-02.6.
Checklist marks 02.5 complete; UI implementation has not started. No stage/commit/push.

## WDM-02.6 — Validation Admin UI (2026-11-05)

Owner accepted the plan and authorized implementation. SnapshotValidationPanel
extends the existing `/admin/datasets/:snapshotId` page; no extra route or backend
change is included. The panel reuses verified shared identity, WDM API client,
ADMIN guard, request correlation and session cancellation. No token persistence,
decoded-only identity, Auth flow or automatic request replay is introduced.

### Public API and rendering contract

validation.ts uses POST with undefined body (no JSON Content-Type/body/query)
and GET with page/size (20 per page). validation-response.ts verifies requested
snapshot/page/size, supported report/CSV/policy versions, SHA/size/time shape,
status/outcome/count-completeness agreement, decimal string/null counters,
diagnostic fields and safe-integer positions. Unknown response properties such
as manifest/private paths are projected away. Unknown safe diagnostic codes get
a fallback description; unsupported versions/stages or malformed data fail
closed. Counts stay strings, including values beyond JavaScript's safe range;
page navigation compares decimal totals without imprecise count conversion.

VALID is labeled as a usable cleaned subset with an explicit explanation that
raw may still contain rejected orders/duplicates. INVALID describes structural,
empty or unassignable-order outcomes. Null is Chưa xác định, not zero; when
countsComplete=false the displayed parsed count is explicitly a prefix, not a
dataset total. UTC completion text retains source microseconds. Report/policy
version and checksum identify the data previously validated, without claiming
a fresh physical-file/catalog verification on a report read.

The diagnostic table exposes stage, fixed code/explanation, field and original
record/physical-line ranges. Server paging is separate from local position-group
paging (five positions at a time), with explicit total/displayed ranges. Decoder
cursor accuracy is explained, not represented as an exact offending byte/row.
No raw values, full server error message or internal manifest is rendered.

### Request lifecycle

- PENDING does not auto-POST. A start button is offered for a snapshot with raw
  availability, supported CSV contract and no conversion, while backend remains
  authoritative. Legacy/unavailable state is explained; stored reports may
  still be read without opening raw.
- VALID/INVALID metadata triggers GET on mount. Report binding must match the
  current snapshot's raw SHA/size. Repeated reads/page changes never run POST.
- A ref lock blocks duplicate clicks immediately; busy controls are disabled.
  Successful POST refreshes metadata without unmounting/losing the report. GET
  recovery also refreshes metadata when its status differs from the report.
  Metadata refresh failure has a separate warning and retains the report.
- Network/protocol/409/503 after POST requires explicit GET status recovery,
  not automatic POST retry. Network/503 GET failures do not clear the recovery
  requirement. HTTP 409 clears that local requirement because no readable completed
  report is available, but
  does not prove server work has stopped. A later POST is a deliberate user action.
- AbortController, immutable binding and identity/session checks prevent stale
  requests/results from replacing another snapshot/user's view. Browser abort,
  navigation and logout are not advertised as cancelling server computation.
- Errors use fixed safe UI messages and a request ID when available, not raw
  server text/details. Auth denial remains owned by the shared Auth boundary.

The UI displays established backend data only; it does not calculate cleaning,
support, weights, thresholds or sequence/mining output. Internal backend statuses
and accepted 02.1–02.5 rules remain unchanged. No dependency/env/schema changes.

### Verification evidence

| Check on 2026-11-05 | Result |
|---|---|
| Focused API/reader + panel + snapshot-detail tests | 61/61 PASS |
| Final frontend regression | 244 PASS, 2 existing live opt-in SKIPPED |
| TypeScript noEmit / focused ESLint | PASS / PASS, no new focused warnings |
| Normal production build | PASS; inherited non-WDM hook/non-null warnings and Browserslist notice remain |
| Dataset + Validation real Edge/HTTPS/API/MySQL gate | 10/10 PASS, exit 0 (7 previous Dataset + 3 validation cases) |
| Node browser-case syntax / diff whitespace | PASS / PASS |
| Backend package with skipTests | PASS preparation only, not unit-test evidence |
| Backend/Maven unit regression / separate Auth browser / CRA dev proxy | NOT VERIFIED this task; no backend source changed |

Browser evidence: `.agent-workspace/phase-wdm/WDM-01.8-UTjVX4/RESULT.json`, with
safe case summaries and SERVER.log in that ignored run directory. Edge version
156.0.4314.58; real shared Auth, current production UI/backend build, isolated
MySQL 8.4 and HTTPS reverse proxy. No application test hook/harness is injected.
The runner uses its existing 4096-byte upload fixture limit, not a capacity test.

The new browser cases prove a single bodyless POST despite duplicate clicks,
atomic committed validation/report readback, updated metadata, GET-only reload,
and unchanged raw bytes/hash. A test deliberately loses the browser response
after the real server committed a dirty-input report; UI disables another POST,
recovers with GET, displays 1 kept/3 rejected/1 deduplicated and refreshes metadata.
Other real POSTs produce INVALID for bad UTF-8, header-only and unassignable
order input, preserving null counts and decoder cursor wording. Existing seven
Dataset/Auth-boundary/export/upload/raw/cancellation cases remain green.

Commands: from frontend, `npm test -- --watchAll=false --runInBand`,
`npx tsc --noEmit`, focused `npx eslint` on the changed API/UI/test files and
`npm run build`; from root, package current backend with
`mvn -pl backend -am package '-DskipTests'`, then
`npm --prefix frontend/test/e2e run test:dataset`. The package operation is not
a new test run. Initial fixture/matcher/lint failures were corrected in tests,
without weakening envelope validation or backend semantics.

Task file inventory: created validation-response.ts, validation.ts,
validation-test-data.ts (maintained synthetic test fixture only), validation.test.ts,
useSnapshotValidation.ts, SnapshotValidationPanel.tsx and its test. Modified API
index, snapshot detail and its test, dataset browser cases, frontend README,
browser README, ARCHITECTURE, this contract and ignored WDM checklist. No source
deletions; pre-existing backend changes and staged frontend/.env were preserved.
Browser/process/container/temp resources created by the runner were cleaned by
its owned-resource checks; existing service/container and older evidence remain.

**Current WDM-02.6 status: IMPLEMENTED / TECHNICAL PASS / OWNER ACCEPTED
(2026-11-05).** Owner accepted the result in chat and requested planning WDM-02.7.
Checkbox marks 02.6 complete. Conversion gate is not implemented; BLK-02 remains
open, no stage/commit/push.

## WDM-02.7 — Validation Gate

Implementation scope approved by the owner: read-only conversion admission,
not conversion execution, SPMF serialization or a new UI conversion action.

### Admission contract

POST `/api/v1/admin/dataset-snapshots/{id}/conversion` is ADMIN-only through
shared Auth. It accepts no body or query parameters. Anonymous/customer requests
return 401/403 before input validation or snapshot/file access. Admin malformed
UUID/body/query returns 400, missing snapshot 404, ineligible snapshot 409
`INVALID_RESOURCE_STATE`, and operational/storage/capacity failure sanitized 503.

`ConversionValidationGate.prepare` requires all of:

- CSV/DATABASE acquisition using `wdm-order-csv-v1`; VALID snapshot and a matching
  completed `WDM_VALIDATION_REPORT_V1` / `WDM_ORDER_CLEANING_V1` report bound to
  snapshot ID, raw SHA-256 and canonical raw-size metadata.
- READY, countsComplete, at least one kept record/order, supported counts and
  a full manifest matching every actual logical record and physical line span.
- Exact partition into kept, rejected whole orders and identical-line dedup;
  dedup references an earlier retained representative with identical 13 fields.
  Count/diagnostic positions, action/reason codes and kept-order consistency
  are cross-checked. BLOCKED or partial-order retention cannot pass.
- NOT_STARTED / NOT_COMPUTED state, sequence_count acquisition placeholder 0,
  and no sequence/mapping artifact references or checksums.
- Raw bytes verified by the existing private storage's size/checksum/path rules;
  record/raw/time/concurrency budgets are enforced, with a fresh repository read
  after verification rejecting changes in relevant state/report/binding.

The gate trusts reports published through the private validation pipeline; it
does not authenticate an adversarial, fully forged private database or rerun
current product-catalog/business validation. Frozen cleaning decisions remain
authoritative. `StoredValidationReport` extracts the existing 02.5 binding reader
without changing validation endpoints; its regression tests remain green.

The internal immutable `VerifiedConversionInput` contains defensive copies of
the verified raw bytes, the frozen report and ordered retained source records.
It exposes no storage path and is never serialized by the endpoint. It proves
eligibility at verification time, **not an execution reservation**. WDM-03 must
atomically claim/recheck state and consume this verified input rather than
reopening raw paths or assuming a previous HTTP admission reserves conversion.

After successful admission the endpoint deliberately returns 503
`SERVICE_UNAVAILABLE`, message `Conversion is not available yet.` No fake success,
queue, status write, statistics or conversion artifacts are created. A raw-integrity
failure instead returns the generic admission failure message. Successful
conversion/SPMF is **NOT VERIFIED — WDM-03**, outside this gate's acceptance.

### Verification and task scope

Focused backend run: 59/59 PASS (34 gate unit cases, 4 standalone MVC cases,
5 complete-application/disposable-MySQL gate cases, 16 existing validation
service/engine cases). Positive integration cases use actual acquisition ->
validation -> admission for both clean input and dirty input retaining one row.
Negative integration cases cover pending/invalid, a VALID column without report,
stale binding, conversion already started, same-size raw tamper and a state
change during verified file reading. Repeated admission leaves JSON/status/raw
unchanged and creates no additional artifacts.

Selected backend regression: 327 PASS; mining-core regression: 1182 PASS, no
failures/errors/skips. Command from repository root:

```powershell
mvn -pl backend -am '-Dtest=*,!BackendApplicationTests,!AuthSessionIntegrationTest,!WdmAuthorizationIntegrationTest,!RegistrationIntegrationTest,!OrderExportRepositoryTest,!OrderExportAvailabilityTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

The default-datasource application/export availability tests were deliberately
excluded. Separate existing Auth/session/registration/export-repository integration
suites were not rerun; real shared Auth is exercised by Dataset browser cases.
No development database is used by gate integration tests. Packaging with
skipTests is preparation only. Logs remain ignored local evidence at
`.agent-workspace/phase-wdm/WDM-02.7-regression.log` and `WDM-02.7-package.log`.

Task file inventory: created StoredValidationReport, ConversionValidationGate,
ConversionManifestVerifier, VerifiedConversionInput, ConversionAdmissionController
and three gate unit/MVC/MySQL test files. Modified SnapshotValidationService,
dataset browser cases, browser README, ARCHITECTURE, this contract and ignored
WDM checklist. No source deletions, schema/dependency/env/frontend-runtime or
mining changes. Existing dirty changes and staged frontend/.env are preserved;
no stage/commit/push. WDM-02.7 checkbox remains pending owner acceptance.

Real Edge 156.0.4314.58 / HTTPS / shared Auth / isolated MySQL browser gate:
**12/12 PASS, exit 0** (7 Dataset regressions + 3 Validation UI cases + 2 gate
API cases). Evidence: `.agent-workspace/phase-wdm/WDM-01.8-KpadnX/RESULT.json`.
The positive gate API path uses actual acquisition and published validation;
repeated eligible POSTs return explicit converter-unavailable 503, without state
changes or new artifacts. Negative paths verify 401/403 before malformed lookup,
400/404, PENDING/INVALID/forged-status 409 and same-size raw-tamper sanitized 503.
This is gate admission evidence, not successful conversion or SPMF evidence.
Existing UI build was reused because this task changes no frontend runtime code;
the backend jar was freshly packaged. Node case syntax and diff whitespace checks
PASS. Runner-owned browser/process/container/temp resources were cleaned; owner
services/storage/container and earlier evidence were preserved. No pending source
cleanup. New logs and RESULT.json are retained as acceptance evidence.

**Current WDM-02.7 status: IMPLEMENTED / TECHNICAL PASS / OWNER ACCEPTED
(2026-11-05).** Owner explicitly accepted WDM-02.7 in chat. WDM-02.1–02.7 are
accepted; the checklist marks the phase tasks complete. Conversion/SPMF execution
remains WDM-03. BLK-02 closure remains pending separate owner review; unrelated
BLK-03–06 and R6 are unchanged. WDM-03 has not been started.

## WDM-03.1 — Global product/item mapping (2026-11-06)

Policy version: `GLOBAL_PRODUCT_ITEM_INT_V1`. The owner authorized implementation
of the mapping plan. Owner acceptance of the implementation is recorded separately.

### Identity and reproducibility contract

- V2 `ITEM_MAPPING` is the global, append-only product/item dictionary. Its
  product PK/FK and unique positive item ID remain unchanged. Exact `product_id`
  is identity; SKU, historical product name, quantity, price, status and line ID
  do not participate. CSV values are never trimmed, case-folded or normalized.
- Web mapping uses `1..2147483647`, the intersection of V2's positive UNSIGNED
  IDs and mining-core's Java `int`. Core continues to allow zero; its parser and
  algorithms are unchanged. JDBC reads UNSIGNED values as `long` before narrowing.
  An existing global maximum above the core range fails closed (409), even if
  the requested subset would otherwise fit. No overflow, wraparound or repair.
- Input is exclusively `VerifiedConversionInput.keptRecords()` from the accepted
  gate. Rejected and deduplicated source rows are not reconsidered. The mapper
  neither reruns cleaning nor accepts a public request's arbitrary product list.
  Test fixtures may construct this package-local handoff directly; those fixtures
  are not gate evidence. A separate actual pipeline test covers acquisition,
  validation, gate and mapping together.
- Existing mappings are reused. New distinct product IDs are sorted with Java
  `String.compareTo` (UTF-16 lexicographic, no locale/collation) within each batch,
  then allocated above the observed global maximum. IDs may have gaps in a
  snapshot; they are not renumbered per snapshot. Quantity does not create IDs.
- Catalog and mapping lookup must return the exact requested product string.
  Collation-only aliases (case, accent/normalization or trailing-space matches)
  are rejected as incompatible state, not silently merged. Product removal or
  incompatibility after validation fails the mapping transaction; current product
  status/price/name is not an added eligibility test.
- Output `ProductItemDictionary` is a defensive, immutable bijection, both
  directions ordered by ascending numeric item ID, and contains only products
  in the kept set. Empty kept input returns an empty dictionary without DB work;
  this is not permission to convert an empty/invalid snapshot.
- Stable IDs mean reuse of committed mappings in the same mapping history.
  Independently provisioned databases or concurrent new-product batches can
  assign different IDs. Reproducibility therefore requires WDM-03.5's frozen
  snapshot dictionary/checksum, not reconstruction from current catalog order.
- No update/delete operation is provided, IDs are not recycled, and mapped
  products must not be physically deleted. The maximum-based allocator assumes
  this append-only lifecycle; manual DB deletion/rewrite is outside that guarantee.

### Transactions and failure boundary

`ProductItemMappingStore` uses a fresh Spring `REQUIRES_NEW`, READ_COMMITTED
transaction with a 10-second timeout for each complete batch. MySQL's product PK,
item unique constraint, check and product FK remain final guards. A duplicate-key
or concurrency/lock failure rolls back the whole attempt; the service retries
from a fresh read/transaction, at most four attempts. This also handles an empty
mapping table without relying on locking nonexistent rows or an in-process lock.
Four failed attempts return SERVICE_UNAVAILABLE; non-contention database failures
are not retried. Insufficient remaining IDs return SERVICE_UNAVAILABLE and roll
back the whole batch. Incompatible catalog/mapping state returns INVALID_RESOURCE_STATE.
Neither SQL text/details nor product data is exposed in application errors/logs.

Committed global allocation survives caller rollback or later conversion failure:
it establishes identity, not a completed snapshot. Future conversion orchestration
must claim/recheck snapshot state before calling this writer. The existing gate
remains read-only and the conversion endpoint still returns 503 for eligible input.
No conversion status, raw bytes, SPMF, mapping file, statistics or snapshot checksum
is written here. Mapping artifact serialization/storage belongs to WDM-03.5.

### Source classification and verification boundary

Repository-confirmed: V2 global PK/unique/FK/>0 constraints; data dictionary's
stable non-recycled mapping plus snapshot dictionary; accepted Java-int item model;
WDM-02.7 kept-record handoff. These are repository contracts, not a new paper claim.

Implementation inference: positive core-compatible range, UTF-16 batch ordering,
MAX-based allocation, fresh-transaction retries, immutable dictionary representation
and fail-closed collation/range checks. No WSPM/PRISM formula, threshold, comparator,
candidate generation or pruning is changed. R6, BLK-03–06 and exact-real equivalence
are unchanged. End-to-end SPMF conversion and artifact persistence remain NOT VERIFIED.

Verification results are recorded below after execution; this contract alone does
not establish technical PASS or owner acceptance.

### Verification

Focused mapping plus actual application/gate integration: **27/27 PASS**, zero
failures/errors/skips, Maven and shell exit 0. Breakdown: 10 service/dictionary
unit tests, 11 mapping MySQL integration tests, 6 actual Spring application/gate
integration tests (including the new acquisition -> validation -> gate -> mapping
test). Mapping integration uses V2's 47 CREATE/ALTER statements in disposable
MySQL 8.4, real Spring transaction proxies and forced concurrent MAX-read races.
This is static-DDL compatibility, not a fresh Flyway migration execution. The full
application integration additionally uses Hibernate `validate` against isolated V2.
Initial mapping-only run was 19/19 PASS; final focused run added alias/core parser
and actual pipeline coverage. Logs retain both checkpoints separately.

```powershell
mvn -pl backend -am '-Dtest=ProductItemMappingServiceTest,ProductItemMappingIntegrationTest,ConversionValidationGateIntegrationTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -pl backend -am '-Dtest=*,!BackendApplicationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Evidence: `.agent-workspace/phase-wdm/WDM-03.1-focused.log`,
`WDM-03.1-focused-final.log` and `WDM-03.1-regression.log`. The regression excludes
only `BackendApplicationTests`, whose default datasource is environment-dependent;
it does include the now-isolated `OrderExportAvailabilityTest`. No development DB,
frontend browser, production capacity or complete conversion acceptance is inferred.

Final selected regression: **backend 542/542 PASS; mining-core 1182/1182 PASS**,
zero failures/errors/skips, Maven/shell exit 0. This run includes the final parser
readback assertion for both 1 and Integer.MAX_VALUE, all 27 focused cases and the
existing Auth/API/export/validation/snapshot coverage. `BackendApplicationTests`
remains NOT VERIFIED for this task. No frontend unit/build/browser rerun was
needed: frontend and HTTP conversion behavior are unchanged. `git diff --check`
passed; tracked mining-core, frontend and migration diffs are empty.

File scope: five new Java source/test files (dictionary, service, JDBC store,
service tests and mapping integration tests), one existing gate integration test
modified, ARCHITECTURE and this contract updated, plus the ignored local checklist.
Three task-owned logs above are retained ignored as evidence, not disposable
helpers. Testcontainers/JUnit cleaned their own test resources; final Docker
inventory contains only the pre-existing `wspm-mysql`. Docker Desktop was started
for isolated testing; no development database read/write, owner resource deletion,
source deletion, stage/commit/push or automatic WDM-03.2 work was performed.

**WDM-03.1: IMPLEMENTED / TECHNICAL PASS / OWNER ACCEPTED (2026-11-06).**
Owner accepted WDM-03.1 in chat and requested planning WDM-03.2. The checklist now
marks 03.1 complete. This acceptance update records the existing verification;
it does not rerun tests or implement grouping. BLK-02 closure and BLK-03–06
retain their independent status. Complete conversion/SPMF remains NOT VERIFIED.

## WDM-03.2 — Customer/order grouping (2026-11-06)

The owner accepted the proposed grouping plan and authorized implementation.
Policy version: `CUSTOMER_ORDER_ITEMSET_V1`. Implementation acceptance remains
separate from authorization and verification.

### Membership and provenance contract

- `CustomerGroupingService.group` accepts only `VerifiedConversionInput` and
  its `ProductItemDictionary`. Production callers must obtain kept records from
  the accepted gate and resolve their dictionary through WDM-03.1. Grouping does
  no DB/catalog/file access, mapping allocation, cleaning replay or snapshot claim.
- Group within this one input by exact `customer_code`, then exact `order_id`.
  One customer group is the future source of one sequence; each distinct order
  is the future source of one itemset. A global order index rejects one order ID
  assigned to different customers. Values are not trimmed, folded or normalized.
  Identity is the supplied pseudonymous customer code, not a reconstructed person.
- All kept records of an order must agree on customer, ordered_at and currency.
  Timestamps are decoded using the existing strict CSV-v1 UTC/microsecond format,
  preserving all six fractional digits. No timestamp rounding, local timezone
  conversion, time-window merging or inference of chronology is performed.
  Different orders may have different currencies without splitting the customer.
- Every order exposes ascending distinct numeric item IDs from the supplied
  dictionary. Multiple kept lines with the same product, different historical
  text/prices, or quantity greater than one contribute just one item to that
  order's itemset membership. The same item remains present in other orders and
  customers. No quantities/money are summed and no weights/supports are computed.
- All kept source records are retained exactly once with their original immutable
  13 fields and record/line positions. Different line IDs with the same product
  remain distinct source records. Previously rejected/deduplicated rows are not
  reintroduced. Repeated kept record numbers or line IDs are rejected as an
  inconsistent internal handoff, not silently deduplicated again.
- The dictionary's product set must equal the full kept product set: missing,
  substituted or extra product keys fail closed. The service trusts the accepted
  dictionary's ID assignments; it does not prove registry freshness or detect a
  forged dictionary with the same keys and different IDs. Future orchestration
  must carry the matching dictionary and snapshot proof together.
- `GroupedDataset` contains immutable customer/order maps, immutable sorted item
  lists and immutable retained-record lists. Map and record iteration preserve
  encounter order only for traceability. For the same input this is deterministic;
  shuffling rows preserves membership but can change encounter order. That order
  is explicitly NOT chronology, SID assignment or a ready core sequence. Even
  orders sharing one timestamp remain separate. WDM-03.3 establishes ordering.
- Empty internal kept input with an empty dictionary produces an empty result;
  a nonempty dictionary with empty input is rejected. This utility behavior does
  not admit an empty snapshot: the actual gate still requires usable kept rows.
- Inconsistent handoff data returns `INVALID_RESOURCE_STATE` with a fixed safe
  message. Null top-level arguments are caller misuse. Logs contain counts and
  fixed reason tokens only; nested result `toString` methods show counts, never
  customer/order identifiers or source payloads. These models are internal, not
  HTTP DTOs: explicit source-record access remains sensitive provenance data.

### Scope and source classification

Repository-confirmed: accepted checklist's one-customer/one-sequence and
one-order/one-itemset policy, cleaning's retained distinct source lines and exact
opaque IDs, WDM-03.1 dictionary and WDM-02.7 kept-record handoff. The research
contract defines an itemset as a nonempty set with each item occurring at most
once, and permits the same item in different itemsets. The commercial grouping
policy is not claimed to be specified by the WSPM/PRISM papers.

Implementation inference: immutable grouping models, encounter-order provenance,
strict timestamp decoding, dictionary-key coverage and defensive handoff checks.
No mining-core representation or algorithm, threshold, weight, support, temporal
verification or pruning is changed. Grouping is not another cleaning stage.

Only the grouping service/result/tests, existing pipeline integration test,
ARCHITECTURE, this contract and ignored checklist are in task scope. Existing
WDM-03.1 source/tests/evidence are retained. The old pipeline test's mapping count
now asserts the target product's one global row, rather than assuming no other
test has populated the shared fixture's registry.

No schema/migration, endpoint, frontend, configuration, dependency or artifact
change is made. The conversion endpoint's eligible response remains 503. Temporal
ordering, core SequenceDatabase construction, SPMF serialization and artifact
publication are NOT VERIFIED and remain WDM-03.3 onward. Existing R6, numeric
policy and independent blockers keep their previous status.

### Verification

Verification evidence is recorded after execution below. Unit fixtures construct
the package-local kept-record handoff directly; they are not business-validation
or gate evidence. The actual pipeline integration uses acquisition, persisted
validation, gate, mapping and grouping on isolated MySQL with real Spring wiring.

Initial grouping-only checkpoint: **14/14 PASS**. Final focused command below:
**43/43 PASS**, zero failures/errors/skips, Maven/shell exit 0. Breakdown:
15 grouping unit tests (including supplementary Unicode code-point boundaries),
10 mapping unit tests, 11 mapping MySQL integration tests, 7 actual application/gate
integration tests. The new pipeline test exercises both existing clean and dirty
CSV fixtures and compares exact customer/order/item membership plus all retained
source records. It confirms unchanged mapping registry, snapshot state, raw bytes
and raw-only artifact directory after grouping. V2 DDL compatibility and Hibernate
validate run in disposable MySQL; no Flyway migration execution is claimed.

```powershell
mvn -pl backend -am '-Dtest=CustomerGroupingServiceTest,ConversionValidationGateIntegrationTest,ProductItemMappingServiceTest,ProductItemMappingIntegrationTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -pl backend -am '-Dtest=*,!BackendApplicationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Evidence: `.agent-workspace/phase-wdm/WDM-03.2-focused.log`,
`WDM-03.2-pipeline.log` and `WDM-03.2-regression.log`. The selected regression
excludes only `BackendApplicationTests` because of its environment-default
datasource. Actual API/Auth/export/validation tests use their isolated fixtures.
Frontend/browser and complete temporal/SPMF conversion remain NOT VERIFIED.

Final selected regression: **backend 558/558 PASS; mining-core 1182/1182 PASS**,
zero failures/errors/skips, Maven/shell exit 0. The run includes all 43 focused
cases and existing Auth, authorization, API/export, CSV/cleaning/validation and
snapshot tests. `BackendApplicationTests` remains NOT VERIFIED in this task;
no development datasource was used. Frontend/runtime HTTP behavior is unchanged,
so no frontend unit/build/browser gate was rerun. `git diff --check` and whitespace
checks on the new Java files PASS; mining-core/frontend/migration diffs are empty.

Task file inventory: three new Java files (`GroupedDataset`,
`CustomerGroupingService`, `CustomerGroupingServiceTest`); four existing files
modified (gate integration test, ARCHITECTURE, this contract, ignored checklist).
All WDM-03.1 changes and evidence that predate this task are preserved. Three
task-owned logs above remain ignored acceptance evidence, not disposable helpers.
No source/evidence file was deleted and no cleanup is pending. Testcontainers/JUnit
cleaned their own resources; final Docker inventory contains only pre-existing
`wspm-mysql`. No dev DB access, owner-resource cleanup, stage/commit/push or
automatic WDM-03.3 implementation was performed.

**WDM-03.2: IMPLEMENTED / TECHNICAL PASS / OWNER ACCEPTED (2026-11-06).**
Owner accepted WDM-03.2 in chat and requested planning WDM-03.3 only. Checklist
03.2 is now checked; no tests were rerun or temporal implementation performed
by this acceptance update. R6 and independent
blockers keep their previous status; temporal ordering, SPMF and artifact publication
remain later-task obligations.

## WDM-03.3 — Temporal ordering (2026-11-06)

The owner accepted the temporal-ordering plan and authorized implementation.
Policy version: `CUSTOMER_ORDER_TIME_CODEPOINT_V1`. Authorization is not owner
acceptance of the resulting implementation.

### Ordering and preservation contract

- `TemporalOrderingService.order` consumes the accepted `GroupedDataset` and
  produces immutable `OrderedDataset` lists. Customer codes are sorted ascending
  by exact Unicode code-point order. Within each customer, orders are sorted by
  `orderedAt` (`Instant`) ascending, then exact opaque `order_id` by code points.
- No trim, case folding, Unicode normalization, locale-dependent collation or
  numeric interpretation of string IDs. Thus `order-10` precedes `order-2` at
  equal timestamps. The explicit comparator iterates code points (including
  supplementary characters); Java UTF-16 `String.compareTo` is not substituted.
  A string prefix sorts before its extension. U+E000 precedes U+10000, unlike
  UTF-16 natural ordering. This commercial string comparator is separate from
  WDM-03.1's documented UTF-16 ordering when assigning previously unseen IDs.
- Compare the existing `Instant` values without rounding or timezone conversion;
  all CSV-v1 microsecond precision survives. Equal timestamp orders remain separate
  itemsets-to-be. The ID tie-break is a reproducibility policy, not evidence of
  actual sub-microsecond purchase chronology. Order dates never move a customer
  before another customer; the outer customer comparator alone determines that.
- Each output order is the same immutable `GroupedOrder` object as the input:
  metadata, ascending distinct item IDs, currency and all retained source records
  and positions are unchanged. Source-record encounter order is not resorted.
  No merging, regrouping, quantity/money aggregation, weight/support computation,
  dictionary allocation, cleaning replay, catalog lookup or source-row repair.
- The service rejects inconsistent structural handoffs: blank/mismatched customer
  and order map keys, empty customer groups or itemsets/source-record lists,
  repeated global order identity and item lists that are not strictly ascending
  positive Java-int IDs. It checks the structure required for ordering, not every
  business/source-field invariant already established by the gate and grouping.
  The caller must carry the matching snapshot/dictionary/grouping lineage.
- Empty grouped utility input gives an empty ordered list; this does not bypass
  the gate's empty-input rejection. Null top-level input is caller misuse. Other
  structural rejection uses INVALID_RESOURCE_STATE with a fixed safe message.
  Logs contain counts/fixed reason tokens; model toString methods redact identity.
- Sorting uses new local lists and does not mutate grouping maps/lists. Identical
  customer/order/item membership and timestamps lead to identical ordered
  customer/order/item structure even if raw rows or grouping-map encounter order
  change. Raw source provenance can retain a different encounter order after a
  row permutation; it is deliberately not normalized to manufacture byte equality.

### Scope and fidelity

Repository-confirmed: existing accepted CSV canonical customer/time/order-ID
ordering and Unicode code-point policy, one-customer/one-sequence and
one-order/one-itemset commercial policy, and WDM-03.2's immutable membership view.
This is application preprocessing; it does not redefine core item comparison,
sequence matching, temporal verification or the WSPM/PRISM algorithms.

Implementation inference: ordered result types, comparator implementation,
structural guards and retaining original immutable grouped-order objects. The
commercial timestamp/ID tie-break is not claimed to come from the WSPM papers.
R6, numeric acceptance policy and independent blockers retain their prior status.

Three new Java files (ordered model, service and unit test) plus the existing
pipeline integration test, ARCHITECTURE, this contract and ignored checklist are
the task scope. Prior WDM-03.1/03.2 source/tests/evidence remain unchanged except
the planned additions in shared docs/integration test. No schema, config,
dependency, frontend or HTTP change. Eligible conversion still returns 503.
No SID allocation, core SequenceDatabase, SPMF serialization or artifact publication
is performed; complete conversion remains NOT VERIFIED and belongs to later tasks.

### Verification

Ordering-only unit checkpoint: **12/12 PASS**, zero failures/errors/skips,
Maven/shell exit 0. Tests cover year/day/second/microsecond ordering, equal-time
opaque-ID prefix ordering, case/accent/normalization distinctions and supplementary
Unicode ordering, independent customer ordering, retained-order object identity,
immutable output, source encounter-order preservation and structural rejection.
Eight fixed-seed raw-row shuffles through actual grouping yield the expected
ordered customer/order/item structure while preserving all source records.

Unit fixtures construct internal grouping objects directly; they are not gate
evidence. A separate actual acquisition/validation/gate/mapping/grouping/ordering
integration case uses clean CSV, business-invalid CSV and an inline shuffled
microsecond/tie fixture with the real Spring application and disposable MySQL.
The final focused and selected regression results are recorded after execution.

```powershell
mvn -pl backend -am '-Dtest=TemporalOrderingServiceTest,CustomerGroupingServiceTest,ConversionValidationGateIntegrationTest,ProductItemMappingServiceTest,ProductItemMappingIntegrationTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -pl backend -am '-Dtest=*,!BackendApplicationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Final focused pipeline: **56/56 PASS**, zero failures/errors/skips, Maven/shell
exit 0. Breakdown: 12 ordering + 15 grouping + 10 mapping unit tests, 11 mapping
MySQL integration tests, 8 actual application/gate integration tests. The new
pipeline case runs the original clean/dirty fixtures and a shuffled inline CSV,
asserting expected customer/order identity lists, microsecond values, distinct
tied orders, original grouped-order object identity and the complete retained-row
set/count. Mapping registry, snapshot state, raw bytes and raw-only artifact
directory remain unchanged by ordering. V2 DDL compatibility and Hibernate validate
run on disposable MySQL; this is not a fresh Flyway migration execution.

Evidence: `.agent-workspace/phase-wdm/WDM-03.3-focused.log`,
`WDM-03.3-pipeline.log` and `WDM-03.3-regression.log`. Selected regression excludes
only `BackendApplicationTests` because of its environment-default datasource.
Frontend/browser, production capacity and complete SPMF conversion remain NOT VERIFIED.

Final selected regression: **backend 571/571 PASS; mining-core 1182/1182 PASS**,
zero failures/errors/skips, Maven/shell exit 0. All 56 focused cases and existing
Auth, authorization, API/export, CSV/validation/cleaning and snapshot tests pass.
`BackendApplicationTests` remains NOT VERIFIED in this task. No development DB
was used. Frontend/browser was not rerun because no frontend or runtime HTTP
contract changed. `git diff --check` and whitespace checks on new Java files PASS;
mining-core/frontend/migration diffs are empty.

Task file inventory: three new Java files (`OrderedDataset`,
`TemporalOrderingService`, `TemporalOrderingServiceTest`); four existing files
modified (gate integration test, ARCHITECTURE, this contract, ignored checklist).
All pre-existing WDM-03.1/03.2 source/test/evidence changes remain preserved.
The three task-owned logs above remain ignored acceptance evidence, not disposable
helpers. Zero files deleted; no cleanup pending. Testcontainers/JUnit cleaned
owned resources; final Docker inventory contains only pre-existing `wspm-mysql`.
No owner-resource cleanup, dev DB access, stage/commit/push or automatic WDM-03.4
implementation was performed.

**WDM-03.3: IMPLEMENTED / TECHNICAL PASS / OWNER ACCEPTED (2026-11-06).**
Owner accepted WDM-03.3 in chat and authorized WDM-03.4. Checklist 03.3 is checked;
this acceptance records the existing evidence without rerunning 03.3 tests.
R6, numeric policy
and independent blockers retain their prior status. SIDs/core sequence creation,
SPMF serialization, artifact publication and complete conversion remain later work.

## WDM-03.4 — SPMF serialization (2026-11-06)

The owner accepted WDM-03.3 and authorized the WDM-03.4 serializer work.
Policy version: `ORDERED_CUSTOMER_SPMF_UTF8_LF_V1`. Owner acceptance of this
implementation is separate from authorization and verification.

### Canonical bytes and semantic boundary

- `SpmfDatasetSerializer.serialize` consumes the accepted `OrderedDataset` and
  returns a new caller-owned byte array. It operates entirely in memory, with
  no DB/catalog/file access, resource claim, status update, statistics persistence
  or conversion HTTP integration. Supported production input is the matching
  verified/grouper/temporal-ordering handoff from WDM-02.7 and WDM-03.1–03.3.
- One ordered customer produces one sequence, on one line. Each ordered order
  produces one nonempty itemset. All item IDs in an order are emitted exactly
  once in the supplied ascending numeric order, then `-1`; after the last order,
  emit `-2` and one LF byte. Customer/order boundaries are never merged or split.
  Repeated items in different orders or different customers are preserved.
- Canonical format: decimal integers without signs/leading zeros for item IDs,
  one ASCII space between tokens, no trailing space before LF, no blank lines,
  header, comments, BOM or carriage returns; exactly one final LF for nonempty
  output. Bytes are ASCII-compatible UTF-8 independent of host newline/locale.
  Example for two customers, the first with two orders:

  ```text
  1 2 -1 3 -1 -2
  2 12 -1 -2
  ```

- Web item IDs remain `1..2147483647`. Zero, negative/reserved delimiter IDs,
  duplicate or descending items within an itemset are rejected, not normalized
  or repaired. This does not change mining-core's accepted ability to parse zero.
  Empty customer sequences/itemsets and blank/repeated customer or global order
  identities are also rejected with INVALID_RESOURCE_STATE and a fixed safe
  message. A failed call exposes no partially built byte array.
- The service preserves supplied customer/order list order; it does not sort or
  revalidate chronology, timestamp/ID tie-breaks or Unicode customer ordering.
  Those belong to the accepted temporal-ordering boundary. A manually forged
  OrderedDataset is not proof that the previous stages ran; callers must carry
  the matching handoff. The serializer checks the structural/ID conditions
  needed for its sequence/itemset encoding and leaves source-field validation
  and lineage binding with the prior stages.
- Source-record payloads, quantity, money, historical text and currency do not
  become tokens. Grouped/ordered models and retained source records are not
  mutated. Private IDs/text do not appear in serialization logs or errors; logs
  contain counts, byte size and fixed rejection tokens only.
- Empty internal ordered utility input produces zero bytes, which the accepted
  parser interprets as an empty database. This does not admit an empty snapshot
  through the gate or define R6/zero-effective-threshold mining behavior.
- The existing SpmfSequenceParser reads emitted bytes with UTF-8 and assigns
  one-based SIDs in sequence order. Customer position i therefore corresponds to
  parser SID i+1. Customer/product identities remain in the preprocessing/mapping
  handoff, not in SPMF. No separate SID mapping artifact is introduced in this task.
- Serialization does not execute the parser/miner at runtime or change Prime-block
  encoding. Parser round-trip is verification evidence at test time. No pattern,
  support, threshold, weight or pruning behavior is changed.

### Scope and fidelity

Repository-confirmed: existing SPMF parser's `-1`/`-2`, positive Java-int
compatibility, ordered sequence/itemset boundaries and one-based SIDs, accepted
commercial customer/order/item mapping contracts. The core model/parser also
retain their independent zero-item policy. The distinction between one itemset,
two ordered itemsets and two sequences is preserved in full readback tests.

Implementation inference: UTF-8 without BOM, ASCII decimal tokens, single-space/LF
canonical bytes, per-call byte ownership, in-memory byte buffer and fixed safe
structural rejection. These are serialization/reproducibility choices, not new
WSPM/PRISM paper claims. No benchmarks or production memory/capacity claims.

Task scope: the serializer and its new test, additions to the existing actual
pipeline integration test, ARCHITECTURE, this contract and ignored checklist.
The existing ordering pipeline case now additionally checks exact SPMF bytes and
complete parser readback for all three fixtures; original chronology, source-row,
mapping/snapshot/raw/artifact assertions are preserved. Prior accepted runtime
source/tests/evidence otherwise remain unchanged.

No schema/migration, dependency/configuration, frontend, endpoint, artifact path,
checksum or storage publication is added. The eligible conversion endpoint still
returns 503. WDM-03.5 owns SPMF/mapping artifact storage and checksums; full published
conversion/API/UI/E2E remains NOT VERIFIED. R6 and independent blockers are unchanged.

### Verification

Serializer-only unit checkpoint: **13/13 PASS**, zero failures/errors/skips,
Maven/shell exit 0. Tests assert literal canonical bytes and use the unchanged
core parser to compare every SID, sequence position, itemset boundary and exact
item ID. Cases include max int, repeated cross-itemset items, `<(ab)>`
versus `<(a)(b)>` versus separate sequences, no payload/identity leakage,
caller-owned byte arrays, immutable source and invalid/empty handoff behavior.
Eight fixed-seed raw-row shuffles through actual grouping/ordering produce
identical expected bytes and complete parser readback. Unit handoff fixtures
are not actual snapshot admission evidence; the separate Spring/MySQL pipeline
verification is recorded below after execution.

```powershell
mvn -pl backend -am '-Dtest=SpmfDatasetSerializerTest,TemporalOrderingServiceTest,CustomerGroupingServiceTest,ConversionValidationGateIntegrationTest,ProductItemMappingServiceTest,ProductItemMappingIntegrationTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -pl backend -am '-Dtest=*,!BackendApplicationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Final focused pipeline: **69/69 PASS**, zero failures/errors/skips, Maven/shell
exit 0. Breakdown: 13 serializer + 12 ordering + 15 grouping + 10 mapping unit,
11 mapping MySQL integration and 8 actual application/gate integration tests.
The expanded actual pipeline test verifies expected literal byte strings for
clean, dirty and shuffled microsecond/tied-ID CSV with real shared application
wiring, acquisition, persisted validation, gate, mapping, grouping and ordering.
Core parser readback compares every SID and every itemset's full numeric item list.
All prior chronology and retained-source assertions still pass; registry/snapshot/
raw bytes and raw-only artifact directory remain unchanged after serialization.
This is V2 DDL compatibility/Hibernate validate on isolated MySQL, not a fresh
Flyway upgrade, public conversion endpoint or full-system browser/E2E gate.

Evidence: `.agent-workspace/phase-wdm/WDM-03.4-focused.log`,
`WDM-03.4-pipeline.log` and `WDM-03.4-regression.log`. Selected regression excludes
only `BackendApplicationTests` because of its environment-default datasource.
Frontend/browser, artifact publication, public conversion and production capacity
remain NOT VERIFIED in this task; WDM-03.8's final round-trip E2E is not closed here.

Final selected regression: **backend 584/584 PASS; mining-core 1182/1182 PASS**,
zero failures/errors/skips, Maven/shell exit 0. All 69 focused cases and existing
Auth, authorization, API/export, CSV/validation/cleaning and snapshot tests pass.
`BackendApplicationTests` remains NOT VERIFIED for this task; no development DB
was used. Frontend/browser was not rerun because frontend and HTTP behavior are
unchanged. `git diff --check` and new-Java whitespace checks PASS; migration,
mining-core and frontend diffs are empty.

Task inventory: two new Java files (`SpmfDatasetSerializer`,
`SpmfDatasetSerializerTest`); four existing files modified (gate integration test,
ARCHITECTURE, this contract, ignored checklist, including WDM-03.3 acceptance).
All earlier accepted WDM runtime source/tests/evidence remain preserved. Three
task-owned evidence logs above are retained ignored; no disposable helper or
pending cleanup, zero files deleted. Testcontainers/JUnit cleaned their own
resources; final Docker inventory contains only pre-existing `wspm-mysql`.
No owner-resource cleanup, dev DB access, stage/commit/push or automatic WDM-03.5
work was performed.

**WDM-03.4: IMPLEMENTED / TECHNICAL PASS / OWNER ACCEPTED (2026-11-06).**
Owner accepted WDM-03.4 in chat and requested planning WDM-03.5 only. Checklist
03.4 is now checked; no tests were rerun or artifact code implemented by this
acceptance update. R6 and independent
blockers retain their prior status. Artifact persistence/checksums, conversion
orchestration/API/UI and WDM-03.8 final E2E remain later-task obligations.

## WDM-03.5 — Conversion artifact publication (2026-11-06)

Owner accepted the proposed artifact plan and authorized implementation. The
internal service is implemented independently of WDM-03.6 HTTP execution/download
and WDM-03.7 UI. No migration or mining-core change is made.

### Artifact identity, dictionary and statistics

`ConversionArtifactStorage` shares the private snapshot root. References are
server-generated canonical UUID paths:

```text
<snapshotId>/conversions/<attemptId>/sequences.spmf
<snapshotId>/conversions/<attemptId>/mapping.json
```

Each attempt directory/file is exclusively created, never overwritten. SPMF bytes
are the accepted WDM-03.4 output. Mapping is deterministic UTF-8 JSON with one final
LF, formatVersion `CONVERSION_ARTIFACT_V1`, mapping policy
`GLOBAL_PRODUCT_ITEM_INT_V1` and an `items` array of `{itemId,productId}` entries
in numeric item-ID order. Identity is preserved exactly, including JSON escapes;
no current product display name/price/status is frozen into this dictionary.
Only products retained by cleaning are included. Decode rejects unsupported policy,
duplicate/invalid mappings or noncanonical bytes by reconstructing canonical JSON.

SHA-256 is lowercase hex over exact bytes, including final LF. File contents and
sizes are read back and verified after closing/forcing file channels; descriptor
hashes must match the persisted bytes. The unchanged core parser reconstructs
the database, and every SID/itemset/item is compared to the ordered input before
publication. The frozen mapping's exact item set must match the parsed item set.
No count-only round-trip acceptance.

Statistics are measured from the readback SequenceDatabase and stored in existing
V2 columns: sequence_count; single/multi-itemset sequence counts; itemset_count;
distinct_item_count; total_item_occurrences; min/max/average sequence length.
Length is total items across a sequence's itemsets, not order count, source-line
count or quantity. Cross-order item repetitions count separately. Average is
`(double) total_item_occurrences / sequence_count`, a descriptive DOUBLE metric,
not a mining weight/threshold/comparator. Empty databases are not publishable.

### Claim, binding and lifecycle

The gate remains read-only. Its internal proof now includes exact source type and
raw reference in addition to frozen report/bytes/kept records; older package-local
three-argument test fixtures remain unbound and cannot claim publication.

`SnapshotConversionRepository` uses short REQUIRES_NEW transactions. Claim locks
the snapshot row, compares the full typed validation report and source/raw binding,
requires VALID/READY supported policies, NOT_STARTED/NOT_COMPUTED, zero acquisition
placeholder count, null artifact/statistics fields and no referencing MINING_RUN.
It commits RUNNING plus a server-generated attempt UUID before any mapping or file
work. The JSON operation stores artifact version, stage policy versions, UTC start
time and a SHA-256 binding over the typed validation report. Existing validation
and acquisition provenance are preserved. No transaction spans pipeline/file I/O.

The service reuses accepted mapping/grouping/ordering/SPMF stages. It consumes the
verified kept records without rereading raw for parsing or rerunning business/catalog
validation. Raw integrity is checked again before publication. After verified file
readback, publication locks the row again and checks the same attempt, complete
validation/source binding, unpublished fields and absence of runs. Both file
references/hashes, all statistics, operation sizes/completion time and
SUCCEEDED/COMPUTED are written in one DB transaction. V2 constraints remain intact.

```text
NOT_STARTED -> RUNNING -> SUCCEEDED  (statisticsStatus = COMPUTED)
                      -> FAILED     (statisticsStatus = NOT_COMPUTED)
```

Validation status remains VALID on conversion failure: an unavailable disk or DB
does not redefine the input's validation result. Global mapping committed by
WDM-03.1 remains allocated even if conversion fails. FAILED and abandoned RUNNING
are not automatically retried, reclaimed or overwritten in this task.

### Failure and repeat semantics

- Before publication starts, a work failure records FAILED only for the owning
  still-unpublished claim. Only after that succeeds may the service discard its
  current storage receipt. Failure while writing a new attempt cleans its own
  partial files. Cleanup verifies directory/file identity, never recurses or
  deletes shared parents/raw.csv/other attempts; unexpected entries are retained.
- If recording FAILED is uncertain, retain the files and claim for reconciliation.
  If claim commit itself is uncertain, no artifact work begins and no blind retry
  is attempted. Once publication has been invoked, any error conservatively retains
  files even if the DB reports rollback. A connection error may follow an actual
  successful commit; deleting those files could break published references.
- A later call on SUCCEEDED verifies supported operation policies, validation
  digest/binding, exact canonical references, artifact sizes/hashes, raw integrity,
  frozen dictionary/SPMF item consistency and DB statistics. A fresh DB reread must
  still match before returning. It does not call live product mapping/catalog or
  allocate another attempt. Missing/tampered/incompatible data fails closed,
  without repairing files or downgrading/replacing published metadata.
- Filesystem and MySQL do not share an atomic transaction. RUNNING leftovers,
  retained files from uncertain publication and manual recovery are observable
  reconciliation states; no automatic recovery worker is introduced. File forcing
  is not a claim of distributed atomicity or hardware/power-loss durability.

### Boundaries, budgets and public projection

`WDM_CONVERSION_MAX_ARTIFACT_BYTES` / `wdm.conversion.max-artifact-bytes` defaults
to 67108864 bytes per file; startup rejects nonpositive or array-incompatible
limits. Storage checks canonical references, ancestors, file type/size, no-follow
reads and checksum with bounded buffering. It assumes the same trusted private OS
root as raw storage; it is not an isolation boundary against a privileged local
actor changing filesystem objects during I/O. No public path/name is accepted.
Parser token/width and derived occurrence limits reuse validation's max-row budget.
Mapping entry count/identity and file byte budgets bound supported input. The
service also reuses validation max-concurrent for a local permit limit and duration
for cooperative stage checkpoints, not hard preemption of blocking filesystem/DB I/O.
Replay verification is subject to current resource limits; no production capacity
or cross-process global semaphore guarantee is claimed.

SnapshotResponseMapper now recognizes NOT_STARTED/RUNNING/SUCCEEDED/FAILED.
Public JSON shape remains unchanged: no operation metadata, attempt paths or
validation manifest is exposed. Computed sequence count remains an exact string
under the existing statistics projection. Existing unknown-state tests now use a
truly unsupported FUTURE_STATE; explicit whitelist/privacy tests cover all four
recognized states. The controller still returns 503 for eligible unconverted input;
converted/in-progress/ineligible inputs remain denied by its existing admission gate.
No new start/query/download route or UI is introduced.

### Research fidelity and verification

Repository-confirmed: existing V2 artifact/statistic fields and immutable snapshot
lifecycle after a referencing run; accepted raw/validation binding, mapping and
SPMF/sequence contracts. Implementation inference: versioned dictionary JSON,
attempt layout, validation-report digest, claim/publication state machine,
storage ownership receipts, descriptive statistics, budgets and conservative
failure/reconciliation policy. These are application/reproducibility decisions,
not WSPM/PRISM paper rules. No mining weights, support, thresholds, candidate space,
pruning or core representations change. R6, exact-real equivalence and independent
blocker closure remain unchanged; no performance/heap claims are made.

Initial codec/storage/mapper unit run: 16/16 PASS. Initial pipeline run: 70 tests,
69 passed and one test-setup error creating a rollback-injection trigger because
the disposable MySQL user lacked the required binary-log trust setting. The test
container alone now starts with log-bin-trust-function-creators=1; production
configuration/schema is unchanged. The first log is retained, not overwritten.
Follow-up focused pipeline: 71/71 PASS, no failures/errors/skips, Maven/shell exit 0.
That run includes actual publication, competing claims, source binding mutation,
raw tamper before publication, known work failures, a real MySQL trigger-induced
publication rollback and a simulated lost acknowledgment after an actual commit.
The simulation is not a physical network-outage/power-loss experiment.

The final regression additionally covers a 4/3 average-length DOUBLE readback and
the final UUID/cooperative-budget checks. Results follow after execution.

```powershell
mvn -pl backend -am '-Dtest=ConversionValidationGateIntegrationTest,ConversionValidationGateTest,ConversionArtifactCodecTest,ConversionArtifactStorageTest,SnapshotResponseMapperTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -pl backend -am '-Dtest=*,!BackendApplicationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Task evidence under ignored `.agent-workspace/phase-wdm/`: WDM-03.5-compile.log
(compile only, not test evidence), WDM-03.5-focused.log, WDM-03.5-pipeline.log
(initial trigger setup error), WDM-03.5-pipeline-final.log (71 PASS) and
WDM-03.5-regression.log. BackendApplicationTests is excluded because of its
environment-default datasource. All DB/file writes here are in disposable test
fixtures; no development DB or owner artifact is changed. Public conversion
execution/download/UI, recovery workflow, production durability/capacity and the
WDM-03.8 final browser/round-trip E2E remain NOT VERIFIED.

Final selected regression: **backend 610/610 PASS; mining-core 1182/1182 PASS**,
zero failures/errors/skips, Maven/shell exit 0. This includes the final 22 actual
Spring/application/gate/DB tests, 34 admission unit cases, codec/storage/mapper
tests and all prior Auth/API/export/validation/mining regressions. The final suite
proves 4/3 average length survives MySQL DOUBLE readback using exact current Java
arithmetic, without epsilon or mining comparator changes. Fresh UUID/limit checks
are exercised by this final build; all previous focused cases still pass.

`git diff --check` and new-source whitespace checks PASS. No migration/Flyway
runtime, mining-core or frontend change. The schema checks use isolated V2 DDL and
Hibernate validate, not a new migration execution. BackendApplicationTests remains
NOT VERIFIED; frontend/browser was not rerun because no frontend runtime changed.
API start/query/download and UI are deliberately not implemented by this task.

Task inventory: seven new Java files (immutable artifact result, codec, storage,
claim/publication repository, orchestration service, codec unit test, storage unit
test). Eleven existing files modified: gate, verified proof, snapshot mapper,
application.yaml; gate unit/integration and mapper tests; README, ARCHITECTURE,
this contract and ignored checklist. The configuration/proof/mapper scope expansions
were announced and implement the accepted binding, bounded-storage and status plan.
All earlier WDM source/tests/evidence remain preserved. Zero files deleted and
no disposable helper/pending cleanup. Five task-owned logs above remain ignored
acceptance evidence, including the initial trigger setup error. Testcontainers/
JUnit cleaned owned processes/containers/temp resources; final Docker inventory
contains only pre-existing wspm-mysql. No dev DB access, owner-resource deletion,
stage/commit/push or automatic WDM-03.6 implementation was performed.

**WDM-03.5: IMPLEMENTED / TECHNICAL PASS / OWNER ACCEPTED (2026-11-06).**
Owner accepted WDM-03.5 in chat and requested planning WDM-03.6 only. Checklist
03.5 is now checked; no tests were rerun or API execution/download code added by
this acceptance update. R6, exact-real numeric
equivalence and independent blockers keep their prior status. RUNNING/uncertain
outcomes still require explicit reconciliation; no background recovery is claimed.

## WDM-03.6 — Conversion API (2026-11-06)

Owner accepted the API plan and authorized implementation. This supersedes the
historical gate-only 503 for eligible POST; prior WDM-02.7 evidence remains a
historical checkpoint. WDM-03.5 publication/reconciliation rules are preserved.

### Routes and request/response contract

Base: `/api/v1/admin/dataset-snapshots/{snapshotId}`.

| Method/path | Behavior |
|---|---|
| POST `/conversion` | Synchronous execution/idempotent verified repeat; 200 on confirmed success |
| GET `/conversion` | Read-only state or fully verified completed result |
| GET `/artifacts/sequence` | Verified SPMF bytes, text/plain;charset=UTF-8 attachment |
| GET `/artifacts/mapping` | Verified mapping JSON bytes, application/json attachment |

All require the existing shared ADMIN identity/session guard before body/query,
UUID or resource lookup. Missing/invalid/revoked credentials return 401; eligible
CUSTOMER returns 403. All four routes reject body/query input (including `force`,
filesystem paths or token query parameters). POST retains the existing negative
request-shape detail message. GET/download have a fixed safe read-shape message.
No algorithm/config/dataset parameters, asynchronous 202, force/retry/recovery or
overwrite interface is added. POST RUNNING/FAILED/ineligible input returns 409;
POST SUCCEEDED verifies existing output without new allocation. Unknown snapshot
is 404, malformed canonical UUID is 400. Read/download before successful conversion
returns 409; GET status may show NOT_STARTED/RUNNING/FAILED with null result fields.

JSON uses the unchanged ApiResponse success envelope and shared API error shape,
server request ID and no-store. Explicit ConversionResponse includes snapshotId,
conversionStatus/statisticsStatus, UTC startedAt/completedAt/failedAt, whitelisted
failureCode (`CONVERSION_FAILED` only), supported artifactVersion, statistics and
two artifact descriptors. Integer counts/sizes/lengths are exact decimal strings;
average length is a finite descriptive JSON number. Artifact descriptors contain
sha256, sizeBytes and protected relative API downloadUrl only. No storage path,
attempt ID, validation digest/manifest or arbitrary operation/report JSON is
serialized. Pending states have no statistics or download links. The snapshot
detail endpoint's existing JSON shape is unchanged.

Attachments use `snapshot-<UUID>.spmf` and `snapshot-<UUID>-mapping.json`, exact
verified Content-Length, Cache-Control:no-store and X-Content-Type-Options:nosniff.
File bodies are direct bytes, not an ApiResponse envelope; in particular the JSON
mapping download is the frozen dictionary document. Errors are sanitized JSON with
no attachment header or partially committed file body. Unexpected/missing/tampered
published files return 503; inconsistent publication metadata returns 409. Reads
never downgrade SUCCEEDED or repair/rewrite artifacts. URLs remain authenticated,
not public/static storage paths.

### Read boundary and preservation of accepted conversion

`ConversionReadService` has no dependency on execution/mapping/grouping services.
It uses read-only repository reads, raw/artifact integrity, codec/parser/statistic
verification and a fresh metadata reread. It extracts the former private completed
read routine from SnapshotConversionService and retains all WDM-03.5 checks, adding
supported operation/timestamp projection. Pending state reads check their null/
zero unpublished shape, supported binding/policies, UUID and safe timestamps/code;
they perform no artifact I/O. Unsupported/corrupt metadata is not presented as a
valid pending or computed result.

VerifiedBundle owns immutable response/result plus defensive copies of the verified
file bytes. Downloads use those bytes directly after verification, never reopen
the path. GETs do not claim, publish, fail, allocate mapping, create files or call
convert(). Local permit and cooperative checkpoints reuse validation resource
limits. POST still uses the accepted conversion service; its confirmation/repeat
checks now use the same read boundary. The controller additionally verifies before
projecting a successful POST response. Snapshot/raw/artifact state remains unchanged
by reads; shared Auth lifecycle behavior is preserved.

The class name ConversionAdmissionController is retained to avoid an unrelated
source rename/deletion; it now owns the four actual routes. No schema, migration,
dependency, artifact format, mining-core or frontend application change is made.
Conversion UI remains WDM-03.7 and final cross-module/browser round-trip gate remains
WDM-03.8. No new FAILED retry, RUNNING takeover or background recovery is introduced.

### Verification and scope

Initial controller unit checkpoint: 9/9 PASS. Focused reader/mapper/controller plus
accepted DB pipeline: 40/40 PASS. Real shared JWT/ADMIN HTTP matrix and pipeline:
77/77 PASS, zero failures/errors/skips, Maven/shell exit 0. The latter includes new
actual HTTP upload -> validation -> conversion -> GET -> two downloads -> repeat,
full core-parser readback, private-field absence, hash/length/header checks, invalid
input/unknown ID, anonymous/customer and logout denial, published-file tamper and
unchanged publication metadata. Existing conversion rollback/concurrency/binding
tests pass after read extraction; pending RUNNING/FAILED projections are checked
on their real DB states. The Auth fixture deletes its own ITEM_MAPPING rows before
PRODUCT to isolate the new conversion tests; no development DB is involved.

Dataset test source replaces only the historical temporary 503 success assertion
with actual conversion/API verification. Earlier pre-claim raw tamper and all
negative security/admission cases are retained. Fresh browser evidence and final
regression results follow after execution. The frontend runtime is unchanged;
the browser still uses the real built login/Dataset application plus authenticated
API requests, not a new conversion UI or injected application hooks.

```powershell
mvn -pl backend -am '-Dtest=ConversionAdmissionControllerTest,ConversionReadServiceTest,ConversionResponseMapperTest,ConversionValidationGateIntegrationTest,WdmAuthorizationIntegrationTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -pl backend -am '-Dtest=*,!BackendApplicationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Evidence under ignored `.agent-workspace/phase-wdm/`: WDM-03.6-focused.log,
WDM-03.6-pipeline.log, WDM-03.6-http.log and WDM-03.6-regression.log. The default-
datasource BackendApplicationTests is excluded. Repository-confirmed: accepted
Auth/API, UUID, artifact and readback contracts. Implementation inference: route/
DTO/header choices, public safe projection and shared read-service extraction.
These are application boundary decisions; no mining formulas or output semantics
change. R6, exact-real equivalence and independent blocker status are unchanged.

Final selected regression: **backend 626/626 PASS; mining-core 1182/1182 PASS**,
zero failures/errors/skips, Maven/shell exit 0. This includes the final constructor/
verifier extraction, HTTP/MVC/reader/mapper checks, all accepted conversion failure/
concurrency tests and existing Auth/API/export/validation/mining suites. Only
BackendApplicationTests is excluded (environment-default datasource); no dev DB
was used. Backend package with skipTests PASS is preparation, not test evidence.
Frontend runtime/package/lock files are unchanged, so the existing accepted UI
build was reused without another frontend unit/build run.

Fresh Edge 156.0.4314.58 / HTTPS reverse proxy / shared Auth / isolated MySQL
Dataset gate: **12/12 PASS**, exit 0. Safe result:
`.agent-workspace/phase-wdm/WDM-01.8-IYAfld/RESULT.json`; launcher log:
`WDM-03.6-dataset-browser.log`. Packaging evidence: `WDM-03.6-package.log`.
Commands were `mvn -pl backend -am '-DskipTests' package` then
`npm --prefix frontend/test/e2e run test:dataset`. Browser results confirm the
actual registered/admin login, protected UI export/upload/validation and real
authenticated conversion API/SQL/file behavior. Expected one customer/three-order
SPMF bytes and frozen mapping match downloaded bytes and SHA-256; same-size
artifact tamper denies reads/downloads/repeat without repair; the original
pre-claim raw-tamper check remains on a separately acquired/validated snapshot.
No conversion UI interaction is claimed. WDM-03.7 and WDM-03.8 remain open.

`git diff --check`, new-production-Java whitespace checks and Node syntax PASS.
Schema/migration, mining-core, frontend runtime/package/lock diffs remain empty.
Separate Auth browser gate was not rerun: shared Auth/runtime/runner setup did
not change; real Auth HTTP regression and the combined Dataset gate ran instead.
This is isolated V2 DDL/Hibernate validation, not a new Flyway upgrade execution,
production deployment, physical outage, cross-browser or capacity certification.

Task inventory: five new Java files (ConversionResponse, ConversionResponseMapper,
ConversionReadService and their two unit test files). Eleven existing files
modified: conversion controller/service, controller test, gate integration test,
real Auth HTTP integration fixture/test, Dataset browser cases; README,
ARCHITECTURE, E2E README, this contract and ignored checklist. No pre-existing
file was deleted/renamed; all earlier WDM changes/evidence remain preserved.
Six task-owned top-level logs plus the runner's unique evidence directory are
retained ignored, with no disposable helper/pending cleanup. Runners cleaned only
their owned browser/process/container/temp resources; final Docker inventory
contains only pre-existing wspm-mysql. No owner resource deletion, dev DB access,
stage/commit/push or automatic WDM-03.7 implementation was performed.

**WDM-03.6: IMPLEMENTED / TECHNICAL PASS / OWNER ACCEPTED (2026-11-06).**
Owner accepted WDM-03.6 in chat and requested planning WDM-03.7 only. Checklist
03.6 is checked; no tests were rerun or UI code implemented by this acceptance
update. Recovery/takeover/retry
policy, R6, exact-real numeric equivalence and independent blocker states are
unchanged. UI, final WDM-03.8 E2E and production deployment/capacity remain later work.

## WDM-03.7 — Conversion Admin UI (2026-11-06)

**IMPLEMENTED / TECHNICAL PASS / OWNER ACCEPTED (2026-11-06).** The owner authorized
implementation after accepting WDM-03.6. This section supersedes the earlier UI
limitation; it does not accept or automatically implement WDM-03.8.

The existing `/admin/datasets/:snapshotId` page now owns the validation-to-
conversion binding. Its conversion panel accepts a report only for the current
snapshot ID and immutable raw SHA-256/size, with VALID/READY/countsComplete and
current VALID metadata. Conversion availability remains authoritative on the
backend. The initial/reloaded page only GETs conversion status; it never POSTs
automatically. An immediate active-request lock plus a consumed-POST guard
exclude duplicate submission and replay after an uncertain result. Successful
POST is reconciled through verified GET, followed by snapshot metadata refresh.
An uncertain POST also attempts GET, retaining a safe error/request ID when
reconciliation fails. RUNNING is read sequentially every two seconds, with a
60-second polling budget before manual refresh. FAILED exposes the fixed safe
failure code and no retry. Unmount, snapshot binding changes and Auth changes
abort client waiting and suppress stale preview/download/state updates. They do
not cancel server work or implement recovery/takeover.

The runtime decoder validates all four status shapes, supported artifact version,
timestamps, canonical decimal strings, SHA-256 shape and exact public artifact
paths. It projects only public fields. Snapshot detail now recognizes all four
conversion states; initial upload receipt decoding remains strictly NOT_STARTED.
POST accepts only a SUCCEEDED response, while GET accepts all four valid states.
RUNNING/POST have a waiting spinner without a fabricated progress percentage.
The client always constructs authenticated artifact requests from the canonical
UUID/API root instead of following response-provided URLs. Endpoint decoder
failures retain the transport request ID. Download validates the MIME, exact
snapshot filename and byte size; mapping JSON is handled separately from CSV.
Downloads reuse the existing Blob URL creation/revocation helper.

SUCCEEDED displays the nine established server statistics. Counts, sizes and
sequence lengths remain decimal strings, including values beyond Number's safe
integer range. Average length remains the finite server number. UI explains that
sequence length counts items, not orders or quantity. It displays server SHA-256
and size without claiming browser checksum verification or computing mining
statistics in TypeScript.

SPMF preview is explicit and only enabled for artifacts <=1 MiB. The transport
rejects a declared length over the cap before reading, reads a bounded stream
when length is absent/false, and cancels it when accumulated bytes exceed the
cap; it has no unbounded blob fallback for preview. The preview contains at most
20 complete sequence lines and 4000 characters, preserving original delimiters
and line endings. If a complete first sequence cannot fit, UI asks for download.
Partial output is labeled and rendered as escaped React text. Large artifacts
remain downloadable; no preview endpoint, HTML rendering, mining parser or
product-name reconstruction was added.

Verification evidence under ignored `.agent-workspace/phase-wdm/`:

- `WDM-03.7-final2-regression.log`: complete frontend regression, 313 PASS /
  2 skipped, 18 suites PASS / 2 skipped. The existing Auth/health live suites
  require explicit environment variables and were not enabled; they are not
  counted as passing. Earlier green regression logs are retained as evidence.
- `WDM-03.7-final2-typecheck.log`: `tsc --noEmit -p tsconfig.json`, exit 0.
  `WDM-03.7-final2-lint.log`: scoped ESLint exit 0, zero errors/warnings.
- `WDM-03.7-final2-build.log`: production build exit 0; existing unrelated hook/non-null
  warnings and outdated Browserslist notice remain. No dependencies were changed.
- `WDM-03.7-final2-dataset-browser.log` and `WDM-01.8-iJlLsF/RESULT.json`: 15/15 PASS,
  Edge 156.0.4314.58, HTTPS/Secure cookies, real shared Auth/backend and isolated
  MySQL 8.4. All twelve previous Dataset/validation/conversion API cases remain.
  Three new cases cover real validation/conversion UI, one bodyless POST despite
  duplicate clicks, all displayed statistics, exact published preview bytes,
  actual browser SPMF/mapping downloads checked against files/hash/size, SQL/raw
  immutability and GET-only reload. A real committed POST response is deliberately
  lost; UI recovers by GET without replay. Logout suppresses a delayed real
  artifact download. Routing delays/aborts real responses, without a UI harness.
  The final gate used the final production build; the earlier 15/15 green run
  (`WDM-03.7-dataset-browser.log`, `WDM-01.8-3ANIZv/RESULT.json`) remains preserved.

Stream overflow, >1 MiB preview refusal, escaping, character/sequence limits,
RUNNING nonoverlap/deadline and stale response suppression are covered by focused
frontend tests. The browser's existing small upload limit does not prove large-
file/production capacity. Backend/core suites were not rerun because no backend,
schema or mining source changed; the real HTTP/browser gate ran instead. Mining-
core artifact parse/sequence round-trip remains **NOT VERIFIED — WDM-03.8**.
Cross-browser, production deployment/capacity and recovery/takeover also remain
NOT VERIFIED. R6 and exact-real numeric equivalence remain unresolved.

Research fidelity: source-confirmed engineering behavior follows the accepted
WDM-03.6 API/artifact contract; no mining semantics or research claims changed.
The polling budget, preview presentation and client lifecycle are implementation
choices from the accepted UI plan. No unspecified algorithm decision was made.

Task inventory: seven new frontend files (conversion API/decoder/test fixture/API
tests, panel, hook, panel tests); twelve existing files modified (ten frontend
source/tests/docs, this contract and ignored checklist). No deletion or rename;
all prior dirty WDM work and evidence remain. Eleven top-level logs and both runner
evidence directories are retained as acceptance evidence, with no disposable
helpers or pending cleanup. The runner cleaned only owned browser/process/
container/temp resources; final Docker inventory retains pre-existing wspm-mysql.
No dev DB access, stage/commit/push, owner-resource deletion or automatic next-task
implementation. The owner subsequently accepted WDM-03.7 in chat and requested
WDM-03.8 planning only. Its checklist is checked; this acceptance update does not
rerun tests or implement WDM-03.8. All recorded verification evidence is retained.

## WDM-03.8 — Round-trip E2E (2026-11-06)

**IMPLEMENTED / TECHNICAL PASS / OWNER ACCEPTED (2026-11-06).** The owner accepted
the plan, requested implementation and subsequently accepted the delivered result.
The accepted plan below is retained for
scope traceability; executed evidence follows. At planning time WDM-03.5–03.7 were
accepted. Existing serializer tests already parsed generated bytes with mining-core,
the artifact verifier checks persisted structures, and the browser gate verifies
downloads for a one-product fixture. The remaining acceptance boundary is a rich
independent oracle applied to actual authenticated HTTP and browser downloads.

Use small shared synthetic CSV and expected-product-layout fixtures under the
existing backend test resources. Cover multiple customers and products; one order
with multiple products versus multiple ordered singleton itemsets; repeated
products across orders; quantity >1 and repeated product rows within one order;
exact duplicate-line cleaning and whole-order rejection; microsecond ordering,
equal-time order-ID tie breaks and deterministic customer sequence order. Include
a fixed shuffled-row variant and Unicode/opaque IDs where allowed by the accepted
CSV contract. Document expected kept/rejected rows, nested product layouts and
all nine statistics by hand. Expected construction must not invoke production
grouping, ordering, serializer or statistics helpers. Mapping resolves product
labels to their actual stable item IDs; tests must not assume registry starts at 1
or that ID allocation is independent of other fixtures.

Add real Auth/HTTP integration coverage on disposable MySQL using existing test
infrastructure. Each positive snapshot must originate from upload/export, then
actual validation and conversion, rather than snapshot SQL insertion or a forged
validation result. Download both published artifacts through the public API,
verify byte sizes/hashes/attachment headers, mapping bijection and registry
identity, then parse the downloaded SPMF bytes using unchanged SpmfSequenceParser.
Assert the full ordered SID -> itemsets -> item IDs structure and all expected
statistics. One-based SIDs describe deterministic sequence order, not customer IDs
embedded in SPMF. Check raw bytes/hash, persisted validation and metadata
immutability outside the intended conversion publication. Reload/repeated
SUCCEEDED requests must preserve the same attempt and files. Shuffled fixtures
must yield the same canonical SPMF/mapping for an unchanged mapping registry.

Extend the existing production Edge/HTTPS Dataset gate with the same fixture
semantics. Exercise real file selection/upload, validation, conversion, stats and
artifact downloads. Invoke a small maintained test-only Java verifier on the
actual browser-downloaded SPMF file, using the built mining-core parser. Feed it a
separate expected layout resolved from hand-authored product layouts plus the
verified mapping; do not use the production SPMF output as its own oracle or add
a JavaScript SPMF parser. The verifier may live under frontend/test/e2e/java and
use Java 21 source launching with mining-core/target/classes, avoiding a new
runtime endpoint or dependency. Its inputs and concise output contain synthetic
test data only. Missing Java/core build or any structural mismatch must fail the
gate. Deliberately wrong expectations with equal sequence/item totals must also
fail, proving the verifier checks structure rather than counts alone.

Preserve the fifteen existing browser cases and their authorization, lifecycle,
pending/invalid and artifact/raw tamper coverage. Add only missing round-trip
checks; no broad test refactor. Run focused parser/serializer/HTTP integration
checks, the appropriate backend/mining-core regression, then relevant frontend
tests/type/lint/build and the expanded browser gate. Record exact commands,
results, exclusions and safe evidence in this contract/checklist. Packaging is
preparation, not passing-test evidence. Use unique WDM-03.8 evidence paths and
clean only the current runner's owned resources. Default-datasource tests or
other checks not actually run remain NOT VERIFIED.

Expected scope is backend test(s)/fixture resources, Dataset browser cases,
test-only Java verifier and minimal runner wiring if required, plus existing
documentation/checklist. No production algorithm/parser/threshold, schema,
dependency upgrade or mining execution/benchmark is planned. A discovered runtime
defect must be reported with a minimal regression and its scope impact before
claiming acceptance; do not change frozen semantics to make a test pass.

Source-confirmed: ordered sequence versus unordered itemset membership, repeated
items across itemsets, and sequence length as total item occurrences, per the
accepted research contract. Customer grouping, tie-breaking, mapping allocation
and one-based parser SIDs are accepted engineering contracts, not paper-derived
claims. The independent oracle/test-only verifier arrangement is implementation
inference. R6, exact-real equivalence, production capacity, recovery/takeover and
cross-browser certification remain outside this task. Do not automatically start
WDM-04 after this gate.

### Implementation and independent fixture oracle

Added `ConversionRoundTripIntegrationTest` and three shared resources in
`backend/src/test/resources/wdm/csv-v1/`: `round-trip.csv`,
`round-trip-shuffled.csv`, `round-trip-expected.json`. These are newly authored
synthetic fixtures, not acquired/paper datasets. UTF-8 CSV has the accepted 13
columns, LF record boundaries, 12 data rows, 7 orders and 3 customer codes; raw
contains 5 product IDs, including one missing catalog product. Cleaning keeps
9 rows/6 orders, removes 2 rows as one rejected order and deduplicates one exact
line. The surviving database contains 3 distinct mapped products. `rt-p4` is a
real catalog product in the rejected order and must not acquire an item mapping.
An unrelated existing mapping and an existing `rt-p2` mapping establish gaps and
non-product-sorted IDs; no assertion assumes global IDs start at 1.

The oracle fixes the product layout independently of production transforms:

| SID | Customer code | Ordered product itemsets |
|---|---|---|
| 1 | `A` | `<(rt-p1 rt-p2)(rt-p3)(rt-p1)>` |
| 2 | `C\uE000` (actual Unicode code point in CSV) | `<(rt-p1 rt-p2)>` |
| 3 | `C` + U+1F600 | `<(rt-p1)(rt-p2)>` |

Customer ordering is by the accepted Unicode code-point policy, not Java UTF-16
code-unit order. SID 1 has order `o-early` at .000001, then `o-10` and `o-2` at
.000002: preserving microseconds and lexical opaque-ID ties is observable. Two
rows/quantity >1 for `rt-p1` in one order still produce a single item occurrence
in that itemset. Repeated products across orders remain present. Identical
historical SKU/name text across different product IDs must not merge identities.
SID 2 and SID 3 explicitly distinguish one multi-item basket from two temporal
singleton itemsets. Expected structures/counts/statistics are literal fixture
data; production grouping/ordering/serializer/codec/statistic helpers do not
construct the expected result.

Expected statistics are 3 sequences (1 single-itemset / 2 multi-itemset), 6
itemsets, 3 distinct items, 8 occurrences, min/max length 2/4 and average
`2.6666666666666665` (8/3 as Java double). This is descriptive sequence length,
not quantity, support or a new mining comparator policy. The shuffled file uses
the same exact rows, without changing their IDs or data. Both sources are 1571
bytes in this executed checkout. Source SHA-256:

- `round-trip.csv`: `0f225544cd30576ae27a9a1e5880ee70591b6c30f44ad9eb655859cfbd987d68`
- `round-trip-shuffled.csv`: `fb838520ec64e54e72e12e1df6b83667ef5a5be0515f6073260124b3bc93f449`
- `round-trip-expected.json`: `80f85d5d6854c72451664f9fd1dd8f593c64fc5c0991831f6a2698626af1d304`

### Executed HTTP and actual browser/core boundaries

The new Spring/Testcontainers test provisions only synthetic Auth/catalog/registry
data on disposable MySQL, registers/logs in through real shared Auth and acquires
each snapshot through multipart HTTP upload. Validation and conversion POSTs and
both downloads use the public API. It checks cleaning counts, nine statistics,
mapping bijection/registry identity, exact attachment headers/hash/size, public
URLs, full core parser readback, physical file bytes, raw/report preservation,
metadata and idempotent repeated POST/GET without a second attempt. Original and
shuffled inputs produce identical SPMF/mapping under the same registry.

Dataset mode now has 16 cases, retaining all fifteen earlier security/lifecycle/
validation/conversion cases. The added case selects each shared CSV with the real
file picker, validates and converts through production UI, checks displayed
counts/statistics, then obtains both actual browser download paths. Their bytes
are checked against published storage and server hash/size/filename; mapping IDs
are checked against SQL. `frontend/test/e2e/java/SpmfRoundTripVerifier.java` is
source-launched by JDK 21 with the built mining-core classes, reading the actual
download path using unchanged `SpmfSequenceParser`. A separate expected-layout
file resolves only hand-authored product membership through verified mapping;
Node does not parse SPMF or reconstruct customer/time order. The Java verifier
compares every one-based SID, itemset position and item ID, not only counts.
Three deliberately wrong layouts retain the same aggregate sequence/itemset/item
totals (and nine statistics) but must exit 1: customer/SID exchange, temporal
order exchange, and exchanged basket/temporal boundaries. All three are rejected.
Missing Java/core classes or malformed inputs fail the verifier/gate. No test-only
production route or new runtime dependency was added; runner wiring was already
sufficient and remains unchanged.

### Verification evidence

All paths below are under ignored `.agent-workspace/phase-wdm/`:

- `WDM-03.8-native-confirmed.log`: focused parser 15/15 and backend serializer/
  HTTP 14/14 PASS, zero failures/errors/skips, explicit native Maven exit 0.
  `WDM-03.8-focused-final.log` retains the earlier green run. Initial
  `WDM-03.8-focused.log` failed because the newly authored test incorrectly
  prepended `/api` twice to its expected download URL; that assertion was fixed
  without a production change and is not used as passing evidence.
- `WDM-03.8-regression.log`: selected reactor regression **mining-core 1182/1182,
  backend 627/627 PASS**, zero failures/errors/skips, Maven BUILD SUCCESS. Only
  `BackendApplicationTests` is excluded because it uses an environment-default
  datasource; it remains NOT VERIFIED. Actual Auth/API/DB tests use isolation.
- `WDM-03.8-frontend.log`: **313 PASS / 2 skipped**, 18 suites PASS / 2 skipped.
  Existing Auth/health live tests need explicit environment variables and are
  not counted as passing. No separate Auth browser harness gate was rerun.
- `WDM-03.8-typecheck.log`, `WDM-03.8-lint.log`: tsc and scoped frontend ESLint
  exit 0 with no output; Node syntax check also PASS. Java source compilation and
  execution occur in the browser gate itself.
- `WDM-03.8-build.log`: production build exit 0. Existing unrelated React hook/
  non-null and outdated Browserslist notices remain. Production backend source
  did not change, so the accepted backend jar was reused; mining-core classes
  were prepared/verified by Maven. Packaging is not claimed as test evidence.
- `WDM-03.8-dataset-browser.log`, `WDM-01.8-KFuv35/RESULT.json`: **16/16 PASS**,
  native browser process exit 0, Edge 156.0.4314.58, Java 21.0.12.1, real shared
  Auth/backend, HTTPS/Secure cookies and isolated MySQL 8.4. `roundTrip` records
  both source hashes, identical canonical artifact hashes, full core readback,
  all expected statistics and the three rejected wrong expectations. Original/
  shuffled SPMF hash is `263c12ff158d1545d26b89781d841777b50f480659195daef01247528c9433c6`;
  mapping hash is `8ff2ab50ac7d0bd8f8b3be3056bcc949567f5016bb6c679274184607cf4ffe5b`
  for this browser registry. IDs/hashes can differ under another pre-existing
  registry; stability is required within the same registry, not across arbitrary
  database histories.

Executed commands from repo root (lint/tsc use the existing frontend binaries):

```powershell
mvn -B -pl backend -am '-Dtest=ConversionRoundTripIntegrationTest,SpmfDatasetSerializerTest,SpmfSequenceParserTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -B -pl backend -am '-Dtest=*,!BackendApplicationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
npm --prefix frontend test -- --watchAll=false --runInBand
frontend/node_modules/.bin/tsc.cmd --noEmit -p frontend/tsconfig.json
npm --prefix frontend run build
node --check frontend/test/e2e/dataset-cases.mjs
npm --prefix frontend/test/e2e run test:dataset
```

The earlier redirected Maven invocations reported Maven BUILD SUCCESS but their
PowerShell wrapper returned 1 after promoting Mockito's existing stderr agent
advisory to NativeCommandError. The final focused confirmation explicitly
captures `$LASTEXITCODE`, records native Maven exit 0 and exits with it. No test
or assertion was removed/skipped to address this wrapper behavior.

### Acceptance, fidelity and task inventory

Technical criteria PASS: authenticated HTTP and actual UI downloads parse through
mining-core to the full independent expected structure; statistics/mapping match;
wrong equal-count structures fail; shuffled inputs are deterministic; raw/report
and repeat publication are preserved; existing security/lifecycle coverage stays
green. The owner accepted WDM-03.8 in chat on 2026-11-06; its checklist is checked.
This acceptance update changes only this contract and the ignored checklist.
Existing verification evidence is preserved; tests were not rerun. Per the
owner's instruction, no next-task plan or implementation is started. R6 and
independent blocker states are unchanged.

Source-confirmed research invariants are sequence order, itemset membership and
the distinction `<(a)(b)> != <(ab)>`. Customer grouping, Unicode/opaque-ID ties,
positive global mapping IDs, parser SID assignment and canonical serialization
follow existing accepted engineering contracts. Synthetic fixtures, independent
oracle and test-only verifier are implementation inference. No unspecified
algorithm decision or production defect was discovered. No runtime source,
parser/model, mining algorithm, threshold, schema, dependency or API changed.
R6 and exact-real equivalence remain unresolved. Production capacity, recovery/
takeover, physical outage, cross-browser certification, Flyway upgrade execution
and independent blocker closure remain NOT VERIFIED/outside scope.

Five new files: the HTTP integration test, three fixture resources and Java
browser verifier. Four existing files modified: Dataset browser cases, E2E README,
this contract and ignored checklist. All earlier dirty WDM work and evidence
remain preserved. No deletion/rename or stage/commit/push. Nine top-level task
logs and the unique runner evidence directory are retained as acceptance evidence.
Temporary expected-layout/download/storage resources were owned by this run;
runner/JUnit/Testcontainers cleaned their own resources only. No disposable
helper, dev DB access, owner resource deletion or pending cleanup.

## Post-WDM-03.8 UI/UX refresh (2026-11-06)

The owner authorized implementation of four presentation improvements. Technical
verification is complete; visual acceptance remains with the owner. This is a
frontend task and does not change the accepted WDM-03.1--03.8 contracts or start
WDM-04.

Delivered behavior:

- Upload success has a separate receipt with readable filename/status, copyable
  snapshot ID and a direct next-step link to validation.
- Admin and customer accounts share a responsive identity layout with login,
  email verification, account state and role labels.
- `/admin` is a real seven-step guide. Current data preparation steps are linked;
  mining, patterns and combo creation are explicitly unavailable. Admin login,
  navigation and authenticated entry points now land on this guide. Admin sign-in
  preselects this route before awaiting identity because authentication replaces
  the login form; this avoids losing navigation to the form's unmount guard.
- Datasets has clearer typography and export/upload sections. Snapshot detail
  has a compact summary and three keyboard-accessible workflow tabs, next-step
  actions, collapsible technical metadata, metric cards and square bordered
  tables with colored sticky headers and local scrolling. Light/dark and mobile
  layouts were reviewed using synthetic browser screenshots.

Hidden tab panels stay mounted, preserving reports, preview state, request locks
and existing lifecycle guards. Tab selection is URL UI state only and does not
replay a POST. Hidden-panel activity/errors remain visible. API contracts,
validation/conversion eligibility, exact count strings and preview limits remain
unchanged. No mining formula, threshold, schema, dependency or production backend
source was changed by this task. R6 and exact-real equivalence remain unresolved.

Final verification:

- Full frontend: 322 PASS, 2 existing opt-in live tests skipped; 21 passing suites.
  Evidence: `.agent-workspace/phase-wdm/UI-UX-regression-reviewed.log`.
- TypeScript no-emit, scoped ESLint, production build, browser-module syntax and
  diff whitespace checks PASS. Build retains existing unrelated warnings.
- Dataset real Edge/MySQL/HTTPS gate: 17/17 PASS, including all sixteen prior
  cases and actual downloaded SPMF readback through unchanged Java mining-core.
  Evidence: `.agent-workspace/phase-wdm/WDM-01.8-nqBYJL/RESULT.json` and
  `UI-UX-dataset-browser-reviewed.log` in the same phase workspace.
- Auth real browser gate after session interruption: 12/12 PASS, native exit 0.
  Evidence: `.agent-workspace/phase-wdm/AUTH-06-snJHAR/` and
  `.agent-workspace/phase-wdm/UI-UX-auth-browser-resumed.log`. CRA development
  proxy is NOT VERIFIED in this run because port 8080 was occupied; the existing
  service was preserved. Interrupted/checkpoint logs remain as history, not
  acceptance evidence. Backend/core full suites were not rerun for this UI task.

Seven synthetic screenshots in `WDM-01.8-nqBYJL` cover home, upload receipt,
Admin/customer accounts and desktop/mobile/dark snapshot detail. Automated mobile
checks include no document overflow, colored sticky header and 2px table border.
These checks do not establish complete WCAG or cross-browser certification.

Task inventory: six new frontend source/test files; twenty-six modified files
(thirteen runtime components/routes, five unit-test files, two browser case
modules and six documentation/checklist files). Earlier dirty/untracked WDM work
is preserved. No source deletion/rename, dependency update, stage/commit/push or
development database write. Runner-owned resources are cleaned separately from
owner services; acceptance logs/screenshots are retained.

Final Docker inventory contains only the pre-existing `wspm-mysql`. The isolated
container left by the interrupted Auth run was verified and stopped. Cleanup of
its sole temporary `tls.p12` under the system-temp `wspm-auth06-Jrn5Gv` directory
was rejected by automatic approval review (blocked by policy); this file remains
pending cleanup. No source or acceptance evidence was removed.
