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
