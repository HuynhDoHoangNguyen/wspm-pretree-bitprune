# Architecture

Tài liệu này là bản đồ cấu trúc hiện hành của repository. Các hợp đồng toán học
và bằng chứng nghiệm thu nằm trong [docs/research](docs/research), không lặp lại
ở đây.

## System context

```text
Browser
  |
  | HTTP / JSON
  v
frontend (React + TypeScript, dev port 3000)
  |
  | proxy /api -> 127.0.0.1:8080
  v
backend (Spring Boot, port 8080)
  |                         |
  | JPA / JDBC              | Java dependency
  v                         v
MySQL 8.4               mining-core
(Docker, host 3307)      WSPM_PreTree + WSPM_BitPrune
```

`frontend` không thuộc Maven reactor. Root `pom.xml` quản lý `mining-core` và
`backend`; backend phụ thuộc trực tiếp vào artifact `mining-core`.

## Repository map

```text
wspm-pretree-bitprune/
├── AGENTS.md                 Quy tắc nghiên cứu và workflow cho agent
├── README.md                 Thiết lập, công nghệ và lệnh chạy
├── ARCHITECTURE.md           Bản đồ module và dependency
├── pom.xml                   Maven parent/reactor
├── compose.yaml              MySQL 8.4 cho local development
├── .env.example              Mẫu biến môi trường, không chứa secret thật
├── mining-core/
│   ├── pom.xml
│   └── src/
│       ├── main/java/vn/edu/wspm/
│       │   ├── config/       Minimum support và cấu hình mining
│       │   ├── io/           Parser và output
│       │   ├── model/        Sequence, itemset, pattern
│       │   ├── prism/        Prime-block encoding
│       │   ├── tree/         Prefix Tree
│       │   ├── mining/       WSPM_PreTree baseline
│       │   ├── statistics/   Baseline statistics
│       │   ├── measurement/  Timing/JVM heap measurement
│       │   ├── experiments/  Baseline standalone runner
│       │   └── bitprune/     Bitmap, CMAP, index, verifier và DFS miner
│       └── test/             Unit, paper, differential và regression tests
├── backend/
│   ├── pom.xml
│   └── src/
│       ├── main/java/vn/edu/wspm/backend/
│       │   ├── common/api/   Shared success/error DTOs
│       │   ├── config/       Auth/runtime bean configuration
│       │   ├── controllers/  REST endpoints
│       │   ├── dto/          API response models
│       │   ├── entities/     JPA entities
│       │   ├── exception/    ErrorCode, ApplicationException, global MVC handler
│       │   ├── mappers/      MapStruct entity-to-DTO mapping and shared config
│       │   ├── repositories/ Spring Data repositories
│       │   ├── security/jwt/ HS256 token signing/verification
│       │   ├── web/          Auth/WDM guards and shared correlation/error writer
│       │   └── services/     Application services
│       ├── main/resources/application.yaml
│       └── test/             Backend unit/integration tests
├── frontend/
│   ├── package.json
│   ├── public/
│   └── src/
│       ├── api/admin/data-mining/  Admin data/mining API client, types and tests
│       ├── api/common/       Shared response contracts and strict envelope parser
│       ├── auth/             Shared Auth client, memory session and UI boundary
│       ├── components/       Shared UI components
│       ├── pages/            Client/admin pages and view models
│       ├── hooks/            API and state hooks
│       ├── models/           TypeScript domain models
│       ├── stores/           Client state
│       ├── types/            Shared TypeScript types
│       └── utils/            HTTP and utility functions
└── docs/
    ├── README.md             Documentation router/current research state
    ├── research/             Active contracts and acceptance evidence
    ├── database/             Database design drafts
    └── archive/roadmaps/     Completed Phase B/C execution plans
```

Generated directories (`target/`, `frontend/node_modules/`, `frontend/build/`)
are reproducible artifacts and are not architecture components.

## Mining-core boundaries

### WSPM_PreTree baseline

The baseline owns the paper-faithful Prefix Tree search and PRISM Prime-block
support representation. Its accepted behavior is frozen for scientific
comparison. Changes must preserve support semantics, weights, extension rules,
candidate space and deterministic output.

### WSPM_BitPrune

BitPrune owns bitmap upper bounds, CMAP-I/CMAP-S, vertical positional data,
exact temporal verification, three-tier lazy pruning and DFS integration. It
must produce the same complete `pattern -> support` map as the baseline on the
accepted comparison scope.

### Shared domain

Both algorithms reuse the canonical sequence/pattern model, minimum-support
resolution and measurement concepts. A brute-force implementation exists only
as an independent test oracle for tiny controlled databases.

## Backend boundaries

The backend exposes health, product and category reads.

Shared Auth additionally exposes registration, login, refresh, identity and logout
under `/api/v1/auth`. `security/jwt` signs/verifies HS256 access JWTs using one secret;
the root `.env` supplies `AUTH_JWT_SECRET`,
`AUTH_JWT_ISSUER`, `AUTH_JWT_KEY_ID`, `AUTH_COOKIE_SECURE` and `AUTH_ALLOWED_ORIGINS`.
The imported properties are explicitly mapped in application.yaml; the local
profile also honors these values. The Base64 secret decodes to at least 32 random
bytes, stays in ignored .env, and has no generated/default fallback at startup.
No PEM files are used; legacy RS256 tokens are rejected. Explicit test properties
still override local settings for isolated test databases/secrets.
`services/auth` and `repositories/auth` own account/session lifecycle; `web/auth`
owns CSRF/Origin and identity HTTP protection. `exception/GlobalExceptionHandler`
owns API-wide MVC error translation; `web/common/ApiErrorWriter` serves pre-MVC
filter failures using the same immutable `common/api` error DTO. Session checks
read current account/session/roles from MySQL. Setup and evidence boundaries are
in [Shared Auth](docs/auth/README.md). AUTH-04 connects the same authenticator to
the WDM ADMIN provider. `ApiSecurityFilter` applies strict HTTP firewall checks
and an explicit API route allowlist before downstream guards; other API routes
are denied by default, including routes added without a declared policy.

It uses:

- Spring Web MVC for HTTP;
- Spring Data JPA/JDBC for persistence;
- MySQL Connector/J;
- Actuator for operational health;
- `mining-core` as an in-reactor Java dependency.

WDM also defines `GET /api/v1/admin/order-exports` with a historical JDBC
projection and bounded CSV serialization. `web/wdm` owns the WDM authorization
guard and shared Auth integration seam. `web/common/RequestIdFilter` runs before
the firewall and guards, generating one server request ID for headers and JSON;
incoming correlation IDs are not trusted. Without a unique
`WdmAdminAuthorizer` provider, `/api/v1/admin/**` returns 503 before business
processing. The default `SharedAuthWdmAdminAuthorizer` verifies shared JWT/account/
session identity and requires exact DB role ADMIN: absent/invalid credentials
return 401, eligible non-ADMIN returns 403, eligible ADMIN reaches the export.
HTTP/Auth/export integration is tested with disposable MySQL. AUTH-06 additionally
verifies Edge UI/session/cookies, concurrency and actual CSV file download through
a test-only harness using the production WDM client. Dataset business UI is
implemented in WDM-01.6/01.7; WDM-01.8 verifies its browser/API/DB flow. The exact CSV/filter
contract and test evidence are in [dataset-csv-contract.md](docs/wdm/dataset-csv-contract.md).

`POST /api/v1/admin/dataset-snapshots` now acquires a CSV snapshot after the
same ADMIN guard and multipart transport checks. `services/dataset` streams
original bytes to an exclusively allocated UUID directory, computes SHA-256,
and commits acquisition metadata through `repositories/dataset` before HTTP 201.
The public DTO in `dto/dataset` exposes PENDING acquisition metadata without
internal paths or computed sequence statistics. Storage is external to the
application working tree and has no static resource mapping. DB rollback before
the commit phase cleans only the current request's owned artifact; once the commit
phase begins, an uncertain outcome keeps it for
manual reconciliation. Storage and MySQL do not share an atomic transaction.
WDM-02.5 adds POST/GET `/api/v1/admin/dataset-snapshots/{id}/validation`.
`services/dataset/validation` verifies raw integrity, runs the accepted CSV/business/
consistency/cleaning stages, and builds a versioned report with complete record
lineage. A dedicated repository publishes report/status atomically inside V2's
existing JSON field; public DTOs paginate safe diagnostics and hide the manifest.
Configured budgets, local concurrency limits and first-writer-wins publication
bound the synchronous operation. WDM-02.6 provides the snapshot validation panel.
WDM-02.7 adds the read-only `services/dataset/conversion` admission gate and POST
`/api/v1/admin/dataset-snapshots/{id}/conversion`. It checks completed report binding,
READY manifest/counts, raw integrity and fresh snapshot state, returning immutable
verified bytes/kept records internally. Ineligible snapshots return 409; eligible
snapshots currently return explicit 503 because conversion is not implemented.
The proof does not reserve execution; WDM-03 must atomically claim/recheck state
and consume verified input before producing artifacts. SPMF conversion remains
WDM-03. Details and evidence are in the Dataset CSV contract.

WDM-03.1 adds the internal `ProductItemMappingService`: it consumes only the
gate's kept records and resolves an immutable, bidirectional snapshot dictionary.
`repositories/dataset/ProductItemMappingStore` is the append-only JDBC writer for
V2 `ITEM_MAPPING`; each bounded contention retry uses a fresh REQUIRES_NEW
transaction. Existing product IDs retain their global positive Java-int item IDs;
new IDs follow the global maximum, with exact opaque-product checks independent
of MySQL collation. This service is not connected to the HTTP conversion endpoint;
it neither claims snapshot execution nor publishes mapping/SPMF artifacts.

WDM-03.2 adds `CustomerGroupingService` and the immutable `GroupedDataset`
membership/provenance view: exact customer code -> exact order ID -> distinct
numeric item IDs plus all retained source records and order metadata. It consumes
the verified kept-record handoff and that input's exact product dictionary without
DB/file access, cleaning replay or quantity/money aggregation. Map/record encounter
order is traceability only; WDM-03.3 must establish chronology before creating core
sequences. No HTTP conversion, snapshot publication or artifact is added here.

WDM-03.3 adds the pure `TemporalOrderingService` and immutable `OrderedDataset`:
customers are ordered by exact Unicode code points, and each customer's orders
by `Instant` ascending then opaque order ID in the same code-point order. Equal
timestamps retain separate orders. Sorted lists reference the original immutable
grouped orders, preserving item IDs, timestamps, currency and source lineage.
No SIDs/core sequences, SPMF, storage or HTTP conversion execution is added.

WDM-03.4 adds `SpmfDatasetSerializer`: it turns the accepted ordered view into
caller-owned canonical UTF-8 bytes, one customer/sequence per LF-terminated line,
ascending positive numeric item tokens and SPMF `-1`/`-2` boundaries. It does not
resort the handoff or read source payload fields. Full sequence/itemset/item
round-trip is verified with the unchanged mining-core parser; SIDs are the parser's
one-based sequence order. Serialization has no file/DB/HTTP publication side effects;
artifact storage and conversion orchestration remain later work.

WDM-03.5 adds internal `SnapshotConversionService`, `SnapshotConversionRepository`,
`ConversionArtifactCodec`, `ConversionArtifactStorage` and immutable result metadata.
The gate proof now includes a source/raw-reference binding. A short REQUIRES_NEW
row-lock claim compares that binding and the complete frozen validation report,
rejects snapshots already referenced by runs and assigns an attempt UUID. Pipeline
work and private exclusive artifact writes happen outside the DB transaction.
Persisted SPMF/mapping bytes are read back, hashed and verified against the entire
ordered sequence structure before one publication transaction writes both file
references/hashes and all descriptive statistics on existing V2 columns.
Conversion JSON moves NOT_STARTED -> RUNNING -> SUCCEEDED/FAILED, with COMPUTED
statistics only on success. Lost/uncertain publication outcomes retain files and
claims for reconciliation; no automatic RUNNING takeover or FAILED retry. Repeated
SUCCEEDED calls verify frozen files/raw/report binding and DB statistics without
querying current catalog/mapping. The public snapshot mapper recognizes the new
states while hiding operation metadata/paths. The conversion controller is still
not wired to execution; write/query/download API work remains WDM-03.6.

WDM-03.6 activates the existing POST `/dataset-snapshots/{id}/conversion` with
the synchronous conversion service and adds GET on that path plus GET
`/artifacts/sequence` and `/artifacts/mapping`. Shared ADMIN filters run before
shape/UUID/resource checks. POST accepts no body/query and returns 200 only after
verified success; repeated success is idempotent, RUNNING/FAILED are not retried.
`ConversionReadService` extracts the accepted published-file verifier for POST
confirmation, status and download without depending on execution or making claims.
Pending states are strict metadata-only reads. Completed reads check raw/files/
mapping/parser/statistics and fresh DB metadata; immutable bundles hold verified
bytes so the download never reopens a path after attestation. Public
`ConversionResponse`/mapper whitelist timestamps, states, string counts/sizes and
protected relative download URLs; no internal attempt/reference/report is exposed.
All routes use no-store; attachments use fixed UUID filenames, exact Content-Length
and nosniff. Schema, mining and artifact publication rules remain unchanged.

Business JSON success responses are explicit `ApiResponse<T>` values containing
`code`, `message`, `data`, `requestId`; the HTTP status remains authoritative.
Failures retain `status`, `code`, `message`, `path`, `requestId`, `details`.
`ErrorCode` determines status/code together and `ApplicationException` carries
safe business errors independently of Auth/WDM web packages. Health stays raw
JSON, logout stays empty 204 and CSV stays a file. Auth/WDM retain no-store,
Bearer challenges and authorization-before-validation. See the
[shared API contract](docs/auth/README.md#api-contract).

RF-04's `web/common/RequestChecks` shares exact empty-body, no-parameters/body,
allowed query-name and single-value query checks. Controllers invoke them after
the existing Auth/WDM filters; this is no new filter or automatic global policy.
Endpoint messages/detail codes remain explicit. AuthJson.empty keeps its body-only
policy; validation/conversion POSTs reject parameters before touching the body.
Export timestamp/period rules and multipart upload checks remain endpoint-specific.

RF-05 keeps the global MVC boundary and error envelope, with Java 21 type dispatch
and named helpers for safe validation details, method headers and unexpected
failure logging. Input method validation stays 400; invalid controller return
values are 500. Both missing-resource and missing-handler exceptions map to 404.
Filters continue using ApiErrorWriter directly, with unchanged Auth/default-deny
ordering; committed responses and non-API exception resolution remain protected.

The backend alone configures Lombok and MapStruct annotation processing, including
lombok-mapstruct-binding. Lombok reduces trivial injection/accessor/logging code;
entities do not use generated equality/toString via `@Data`. Sensitive DTOs remain
records with redacted toString methods. RF-02 moves closed JSON projection to
`web/auth/AuthRequestReader` after strict `AuthJson` parsing, and pure input
normalization/validation to `services/auth/AuthInputValidator`. Registration
revalidates direct service callers before password hashing or transaction work;
email Bean Validation remains in the service. Constructors that only assign
dependencies use `@RequiredArgsConstructor`; transaction/semaphore initialization
remains explicit. MapStruct uses a shared Spring constructor
injection config and fails compilation on unmapped target fields. IdentityMapper
receives already-resolved roles; it does not query or assign permissions.
RF-03 adds SnapshotResponseMapper for acquisition/detail DTOs and
ValidationResponseMapper for public counts/diagnostics/positions. The snapshot
mapper prepares a sanitized metadata view without storage access; services own
lookups, availability/integrity checks and acquisition transactions.
ValidationReportPresenter slices frozen diagnostics before mapping. Public DTOs
own their nested types and immutable collections, with no service-report types or
manifest exposed. Mapping preserves the existing JSON contract and null/count rules.
AuthSessionService keeps explicit TransactionTemplate boundaries and
account-before-session locks, with named methods for session creation, rotation,
revocation and current-identity checks.

WDM-05.1/05.2 introduce the synchronous `services/run/engine/MiningEngine`
boundary and `CoreMiningEngineAdapter`, preserving full pattern/support maps,
native metrics and per-invocation sampler ownership. WDM-05.3 adds ADMIN POST
`/api/v1/admin/mining-runs`: `RunPreparationService` verifies publication bytes,
parses through the accepted codec and computes weights/resolved support with core
calculators. `repositories/run/RunCreationRepository` locks/rebinds the snapshot
and commits QUEUED in a short REQUIRES_NEW transaction. `RunCreationService`
activates a reserved `RunExecutionAdmission` only after that commit. WDM-05.4
supplies opt-in `RunExecutionConfiguration` / `ManagedRunExecutionAdmission`:
fixed workers, bounded queue and one bounded registry spanning reservation through
completed-result acknowledgement. It verifies committed run metadata before submit
and reverifies actual publication bytes before calling the engine outside DB
transactions. Completion stages/read/ack are internal Java APIs, not HTTP routes.
Default execution.enabled=false preserves 503/no insertion; enabled creation can
return 202 before mining completes. Outcomes are in memory and retain capacity
until explicit handoff acknowledgement; automatic persistence/handoff is not
wired. The explicit WDM-06.3 PATTERN seam is described below. WDM-05.5 binds owner/attempt/lease in the creation transaction
and uses fenced CAS transitions QUEUED -> RUNNING -> SUCCEEDED/FAILED in short
REQUIRES_NEW transactions. A separate maintenance scheduler renews live queued
and running leases and reconciles uncertain finalization through DB readback;
it never reruns the engine. Bounded recovery fails only expired owned nonterminal
rows, leaving unowned and legacy rows untouched. Success status alone cannot
acknowledge or discard the complete in-memory result. New lifecycle times use
Instant/UTC and the DB clock over DATETIME(6); legacy getter signatures remain.

WDM-05.6 adds immutable RunMetricsCapture and MeasuredRunPreparation outside the
unchanged engine boundary. ConversionReadService times only its three verified
storage calls; worker refresh propagates this observation without extra reads.
The worker total spans refresh through engine return after claim; queue wait,
pre-admission preparation, claim and terminal DB reconciliation are excluded.
Native core preprocessing/DFS/complete timing and algorithm JVM-wide sampled
heap are preserved. Times stay nanos internally and are independently floored
to milliseconds for existing V2 columns; missing output I/O is null. Success
CAS writes the complete metric payload atomically with SUCCEEDED, and readback
must match the entire expected payload before handoff confirmation. Retried DB
finalization retains the original result and capture, never remeasures or mines.

WDM-05.7 adds ADMIN GET /api/v1/admin/mining-runs/{runId} through a dedicated
controller/service/mapper/read repository. A single PK query projects only
status/config/times/counts/metrics and internal diagnostic/provenance evidence;
no live entity escapes the short read transaction. UTC DATETIME values are
formatted in SQL before Instant parsing to avoid JDBC default-zone shifts.
BIGINT counts/times/bytes become exact decimal strings, including unsigned values
above Long.MAX_VALUE. Public errors use fixed allowlisted messages; raw diagnostics,
owner/attempt/lease, tokens and artifacts are absent. GET is independent of the
execution flag/registry and never renews, recovers, acknowledges or invokes mining.

WDM-05.8 consumes POST creation and GET status in the existing Admin React slice.
Strict execution decoders project public fields and retain nullable BIGINT
decimal strings/UTC microseconds. The configuration form grants an explicit
single-flight create only for its current SUPPORTED binding; a confirmed 202
navigates to a guarded detail route. Bounded serial polling is GET-only and
Auth/run/controller-fenced, with hidden-page/unmount cleanup and manual resume.
Unknown POST outcomes retain submitted configuration without replay or guessed
run identity. Last-confirmed status survives transient GET errors with a stale
label. Durable pattern handoff remains 06.

WDM-05.9 adds MiningRunMetricsPanel using that exact report, with a pure row/scope
and decimal-string presentation helper. Two counters, six timing rows (ms) and
algorithm sampled heap (bytes) retain null/zero and full unsigned precision.
Known WDM-05.6 descriptors authorize current scope explanations; historical
values stay visible with unknown scope. Digit grouping is string-only; there
is no recomputation, extra request/polling or derived benchmark claim.

Application code must not copy mining logic into controllers/services. Future
combo use cases should call a deliberate application boundary around
`mining-core` and map scientific results into API-specific DTOs.

## Database boundary

`compose.yaml` creates a local MySQL 8.4 service and persistent named volume.
Hibernate uses `ddl-auto: validate`; schema ownership therefore belongs to an
explicit, reviewed schema/migration process. The active Flyway classpath under
`backend/src/main/resources/db/migration/` contains V1, V2, additive V3
execution ownership/attempt/lease columns, and additive V4 run metadata.
V4 adds RUN_PROVENANCE (versioned immutable input descriptors) and
RUN_RESULT_PUBLICATION (separate publication state). RunCreationRepository
inserts the run and both metadata rows in one transaction; execution verification
checks the stored provenance before mining. Completed outcomes retain only the
small input-descriptor context needed for later handoff. WDM-06.3 adds an explicit
internal PatternPersistenceService with artifact re-preparation, an immutable
dictionary/weight context, bounded canonical projection and a MANDATORY-transaction
PatternPersistenceRepository. The service opens a 30-second transaction after I/O;
later temporal/statistics writers can join the same repository transaction. It
locks/rechecks the successful run and provenance, inserts or exactly reconciles
the entire PATTERN set, and verifies readback. No automatic consumer, registry
acknowledgement or production COMMITTED transition exists yet. WDM-06.4 adds
TemporalPatternProjector, TemporalPatternPersistenceRepository and an explicit
TemporalPatternPersistenceService. It composes PATTERN and PATTERN_ITEM in one
transaction after verified preparation, obtains verified generated parent IDs,
and compares complete reconstructed pattern/support maps. A parent-only 06.3
stage can gain all temporal rows; nonempty partial/conflicting temporal stages
are rejected. Existing entity mappings and schema stay unchanged. No automatic
consumer, complete publication or acknowledgement is added.
WDM-06.5 adds an explicit RunItemStatisticsPersistenceService composing all three
tables in one 30-second REQUIRES_NEW transaction. RunItemStatisticsProjector uses
the immutable core statistics retained by PatternPersistenceContext; no formulas
or engine behavior are changed. Its MANDATORY repository rechecks the complete
temporal map/run/provenance before item writes. On first insertion it captures
PRODUCT name/SKU under shared locks; existing complete statistics retain those
snapshots even after catalog changes. Nonempty partial/conflicting statistics
are rejected. Limits are 10,000 items and 8 MiB of statistics accounting (192 bytes
per row plus UTF-8 product/name/SKU), separate from the unchanged temporal bounds.
Empty pattern output still retains all input-item statistics. This remains
staging: no automatic consumer, COMMITTED transition, eviction or acknowledgement.
WDM-06.7 adds RunResultController -> services/run/query ->
RunResultQueryRepository, a JDBC-only read slice with repeatable-read transactions.
Existing history/status APIs remain unchanged. New metadata/page/detail/JSON-export
routes expose exact integer strings, temporal structure and historical same-run
catalog snapshots. Explicit view=staging is inspection only; default published
reads reject until a supported complete-publication verifier exists. Reads do not
call engine/writer/registry seams. Export is bounded and prepared before HTTP
commit; wdm.result-query.max-export-bytes defaults to 16 MiB. Contract and evidence:
docs/wdm/mining-request-contract.md, WDM-06.7 section.
WDM-06.11 extends this boundary with ResultManifestCodec,
RunResultPublicationRepository/Service and an opt-in RunResultPublicationConsumer.
It reuses existing projections and three-table writers in one transaction before
marking V4 COMMITTED. Only a confirmed commit permits registry acknowledgement.
The bounded consumer reads fresh lifecycle-confirmed outcomes outside registry
locks, retries transient failures with capped backoff and retains rejected results.
Published readers verify the complete stored manifest in their repeatable-read
transaction and require no live Outcome/source files. Export V2 includes all
run-item snapshots; PublishedMiningResultProvider exposes the same immutable
verified bundle for future Combo generation, without writing Combo tables.
WDM-06.8 consumes only the metadata route through frontend run-results.ts and
RunResultMetadataPanel on the existing run detail page. History retains server
pagination/filter/back navigation, adds a snapshot filter form and distinguishes
engine counts from stored counts. The result read uses useWorkspaceRead's
identity/token/abort boundary, independently of status polling; no per-row metadata
fan-out, pattern/export UI or new write is added.
WDM-06.9 implements the existing /admin/patterns route as a staging-only Explorer.
It reads the 06.7 parent page through runResultApi.patterns with explicit
view=staging and size=20, retaining exact decimal IDs/counts and canonical
itemset boundaries. Run/page/session changes use the same useWorkspaceRead
abort boundary. The metadata panel links to Explorer only while stagingReadable;
Explorer/detail/history navigation preserves the originating history state.
No per-row temporal query, product lookup, export, publication writer or Combo
handoff is introduced. Full temporal/item detail remains WDM-06.10.
WDM-06.10 adds the guarded /admin/patterns/:runId/:patternId route and
runResultApi.detail over the existing explicit staging detail endpoint. It
reuses parent-summary validation, checks ordered itemset/item DTO structure and
historical same-run snapshots, and preserves exact decimal IDs/counters. The
detail response supplies both provenance and item statistics without catalog or
per-item requests. PatternStructure renders 10 itemsets per local page and 50
items per itemset page; the complete supported detail is still fetched once.
Explorer page is carried in the detail URL; router state preserves run/history
return context. No export, Combo handoff, publication or mining change is added.
The two native-SQL metadata tables
bring the V4 application schema to 47 tables and require explicit migration
verification beyond Hibernate validation. V4 is tested on isolated MySQL only;
owner deployment remains separate. V3 was verified with actual Flyway
upgrade on isolated MySQL; the owner database was not migrated in WDM-05.5.
WDM-05.7 separately inspected the local owner DB 127.0.0.1:3307/wspm_combo and
ran actual Flyway validate -> migrate(target=3) -> validate. V3 was already
SUCCESS (checksum 1697070581); migrate executed zero migrations. Its columns,
ownership CHECK and lease index were verified, with MINING_RUN row count zero
before/after. No repair/clean/baseline or application startup was performed.
V2 promotes the
12-table V1 schema to the 45-table target schema. A read-only verification of
the local development database on 2026-11-03 found successful V1 and V2 rows in
`flyway_schema_history`, a V2 checksum of `727165286` matching the runtime file,
exactly the 45 V2 application tables, and no `LEGACY_V1_*` tables. This does not
establish the state of any other database; inspect its history separately. The
copy under `docs/database/` is documentation, not an additional migration
source. Keep an applied versioned migration byte-for-byte immutable and use a
later migration for corrections.

## Frontend boundary

The frontend is a separately built React application inherited from Electro and
is broader than the currently implemented backend. Treat existing screens as an
adaptation source, not proof that corresponding backend capabilities exist.
Remove unused functionality by coherent feature slices only after route/import
and license review.

The development proxy points to backend port 8080. WDM uses `frontend/src/api/admin/data-mining/`
with `/api` as its default API root, optionally configured by
`REACT_APP_WDM_API_BASE_URL`, restricted to the frontend origin by shared Auth.
`frontend/src/auth/` owns the shared API contract, memory-only access token,
bootstrap/refresh/logout and verified `/me` identity. Refresh stays in the
HttpOnly cookie; Auth POSTs obtain a fresh CSRF token. Its WDM adapter attaches
Bearer credentials, aborts protected requests and clears query/cart state on
session invalidation. Protected requests are never automatically replayed.
Inherited Electro features still reference the legacy port 8085 and rich numeric
profiles. Unsupported protected screens display availability notices instead of
inventing profile data or invoking those APIs. Compatibility stores hold no Auth
identity/token. See `frontend/README.md` for client usage and tests.

`frontend/src/api/common/response.ts` owns transport response types and parsing.
Auth and WDM business clients unwrap success `data` exactly once and require its
request ID to match `X-Request-Id`; session/UI callers retain their domain DTOs.
Only GET /health may opt into raw-health JSON mode; downloads retain the Blob
path. This backend/frontend wire change is coordinated, without dual-format
fallbacks. Existing legacy port-8085 clients are not silently redirected.

Within the Dataset API slice, `datasets.ts` owns request paths/methods and file
transport, while `dataset-response.ts` validates and projects the receipt/detail
wire shapes. Shared primitive checks preserve canonical IDs, UTC timestamps,
SHA-256 and exact string counts without weakening unknown/PENDING handling.

Shared Auth UI resolves login destinations from verified roles: ADMIN reaches
`/admin` (the guided Admin home) from either sign-in form, CUSTOMER reaches the public home.
Client/Admin headers expose same-session navigation links; `auth/useSignOut`
centralizes notification and replacement navigation to `/` for every product
logout entry, including an unconfirmed server logout. Public browsing remains
available without identity; private account/order data stays guarded and commerce
legacy APIs are not enabled by these UI changes. Paper-wrapped forms render local
and whitelisted server field errors, with explicit password error accessibility.
Password confirmation is UI-only; registration keeps the four-field API DTO.
Manual recovery controls are replaced by sign-in/home links; automatic refresh
and the session's token/cookie/cancellation/role enforcement remain unchanged.

Admin Data & Mining pages live in `frontend/src/pages/admin/data-mining/`, with
routes `/admin/datasets`, `/admin/mining-runs` and `/admin/patterns`. They reuse the
existing Admin shell and have UI-only ADMIN role guards. The Dataset page now
provides period CSV export and multipart snapshot acquisition using the real WDM
client contracts. Its calendar uses explicit fixed UTC+07 bounds, downloads
CSV Blobs and shows only verified 201 PENDING acquisition receipts. The other
two pages remain availability skeletons; conversion and mining UI are pending.
WDM-02.6 adds validation actions/reporting within the snapshot detail route.
Shared frontend Auth is integrated. The backend
order export is protected by shared Auth/ADMIN independently of this UI guard.
Frontend module tests and isolated real HTTP/CSV integration are covered in
AUTH-05. `frontend/test/e2e/` owns AUTH-06's separate browser runner, disposable MySQL/
Spring setup, HTTPS test reverse proxy and test-only harness. Edge checks include
HttpOnly/Secure cookie handling, two-tab refresh/logout and downloaded CSV content.
WDM-01.6 adds mocked-HTTP UI/API tests for the Dataset forms and preserves
request cancellation/no POST replay. The new business UI's real browser/API/DB
gate is implemented in WDM-01.8 using the application build and real routes;
historical AUTH-06 harness evidence remains separate.

WDM-01.7 adds `/admin/datasets/:snapshotId` and a link from the successful upload
receipt. It reads explicit metadata from GET `/api/v1/admin/dataset-snapshots/{id}`
and downloads only original CSV from GET `.../{id}/artifacts/raw`, both behind
the shared ADMIN guard. `SnapshotReadService` projects approved public fields;
unknown provenance/status metadata stays null and uncomputed sequence counts
remain hidden. `RawSnapshotStorage` probes availability without creating files,
then bounds and verifies exact size/SHA-256 into a byte buffer before a download
response is committed. Missing raw does not hide existing snapshot metadata.
Validation actions are added separately by WDM-02.6; list, conversion and mining
actions remain pending. WDM-01.8 verifies
the full Dataset flow in Edge with disposable MySQL and an external temporary
storage root. It found and fixed the Admin shell's missing detail-route allowance;
an App regression now traverses the real Admin shell for a snapshot deep URL.
`frontend/test/e2e/` holds the relocated Auth gate and independent Dataset cases
using shared infrastructure. Dataset mode does not serve the Auth harness.
The gate checks exact CSV/UTC+07 bounds, one real upload/commit, independent SQL/
file/hash evidence, detail reload/raw download, authorization/transport failures,
missing/tampered raw, duplicate submit and logout cancellation. Technical checks
pass; owner acceptance is recorded separately in the checklist.
CRA health forwarding is separately verified with a loopback host allowlist and
owned port-8080 bridge. No E2E routes/hooks are shipped in the application bundle.
Other browser engines, real production deployment and export-capacity benchmarks
are not established by this gate; owner acceptance is recorded in the checklist.

## Dependency rules

Mining workspace read projections add newest-first paginated snapshot/run
metadata queries, Admin-only current publication file paths and a synchronized
instance-local registry capacity view. They do not read mining maps, acknowledge
outcomes, execute work or persist patterns. Storage projection is metadata only;
historical run input publication is not inferred from current snapshot paths.
UI auxiliary reads use the existing Auth adapter and abort on session changes.

WDM-05.10's dedicated `frontend/test/e2e/engine-run.mjs` verifies actual packaged
application classes/dependencies, built CRA UI/shared Auth, HTTPS, disposable
MySQL 8.4 and Flyway V1-V3. Backend test sources outside production scan observe
the original retained full maps non-destructively; a separate test-only core
bridge compares them to both direct miners and the existing brute-force oracle.
Fresh JVM batches preserve the two-result/no-auto-ack boundary. No result route
or durable pattern persistence is added. Test instrumentation is not benchmark
evidence. Reproduction and evidence schema: `frontend/test/e2e/README.md`.
The actual Admin shell now admits `/admin/mining-runs/:runId`, with a regression
through `App`; the earlier isolated mocked-HTTP UI entry did not cover this shell.

- `mining-core` must not depend on Spring, the backend, frontend or database.
- `backend` may depend on `mining-core`; the reverse dependency is forbidden.
- `frontend` communicates with backend through HTTP and must not encode mining
  formulas independently.
- Database entities and API DTOs remain separate from mining domain models.
- Research fixtures and large runtime datasets must not be silently converted
  into application seed data.
- Algorithm, API and UI changes should be separate reviewable tasks.

## Runtime ports and storage

| Component | Default |
|---|---|
| Frontend dev server | `http://localhost:3000` |
| Backend | `http://localhost:8080` |
| MySQL host binding | `127.0.0.1:3307` |
| MySQL container port | `3306` |
| MySQL volume | `wspm_mysql_data` |
| Raw snapshot storage | `${user.home}/.wspm/snapshots`, configurable with `WDM_SNAPSHOT_STORAGE_ROOT` |

## Documentation ownership

- `README.md`: commands and onboarding.
- `ARCHITECTURE.md`: current module map and dependency boundaries.
- `AGENTS.md`: mandatory engineering/research behavior.
- `docs/README.md`: reading guide and current accepted research status.
- `docs/research/*.md`: normative contracts and evidence.
- `docs/archive/`: historical plans; never treat intermediate status as current.
