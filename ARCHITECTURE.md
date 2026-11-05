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
a test-only harness using the production WDM client. Dataset business UI remains
pending. The exact CSV/filter
contract and test evidence are in [dataset-csv-contract.md](docs/wdm/dataset-csv-contract.md).

Business JSON success responses are explicit `ApiResponse<T>` values containing
`code`, `message`, `data`, `requestId`; the HTTP status remains authoritative.
Failures retain `status`, `code`, `message`, `path`, `requestId`, `details`.
`ErrorCode` determines status/code together and `ApplicationException` carries
safe business errors independently of Auth/WDM web packages. Health stays raw
JSON, logout stays empty 204 and CSV stays a file. Auth/WDM retain no-store,
Bearer challenges and authorization-before-validation. See the
[shared API contract](docs/auth/README.md#api-contract).

The backend alone configures Lombok and MapStruct annotation processing, including
lombok-mapstruct-binding. Lombok reduces trivial injection/accessor/logging code;
entities do not use generated equality/toString via `@Data`. Sensitive DTOs remain
records with redacted toString methods. MapStruct uses a shared Spring constructor
injection config and fails compilation on unmapped target fields. IdentityMapper
receives already-resolved roles; it does not query or assign permissions.
AuthSessionService keeps explicit TransactionTemplate boundaries and
account-before-session locks, with named methods for session creation, rotation,
revocation and current-identity checks.

Application code must not copy mining logic into controllers/services. Future
combo use cases should call a deliberate application boundary around
`mining-core` and map scientific results into API-specific DTOs.

## Database boundary

`compose.yaml` creates a local MySQL 8.4 service and persistent named volume.
Hibernate uses `ddl-auto: validate`; schema ownership therefore belongs to an
explicit, reviewed schema/migration process. The active Flyway classpath under
`backend/src/main/resources/db/migration/` contains V1 and V2. V2 promotes the
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

Admin Data & Mining pages live in `frontend/src/pages/admin/data-mining/`, with
routes `/admin/datasets`, `/admin/mining-runs` and `/admin/patterns`. They reuse the
existing Admin shell and have UI-only ADMIN role guards. The pages currently show
feature availability rather than fabricated business data; shared frontend Auth
is integrated, while dataset upload/mining business APIs remain pending. The backend
order export is protected by shared Auth/ADMIN independently of this UI guard.
Frontend module tests and isolated real HTTP/CSV integration are covered in
AUTH-05. `frontend/e2e/` owns AUTH-06's separate browser runner, disposable MySQL/
Spring setup, HTTPS test reverse proxy and test-only harness. Edge checks include
HttpOnly/Secure cookie handling, two-tab refresh/logout and downloaded CSV content.
CRA health forwarding is separately verified with a loopback host allowlist and
owned port-8080 bridge. No E2E routes/hooks are shipped in the application bundle.
Other browser engines, real production deployment and export-capacity benchmarks
are not established by this gate; owner acceptance is recorded in the checklist.

## Dependency rules

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

## Documentation ownership

- `README.md`: commands and onboarding.
- `ARCHITECTURE.md`: current module map and dependency boundaries.
- `AGENTS.md`: mandatory engineering/research behavior.
- `docs/README.md`: reading guide and current accepted research status.
- `docs/research/*.md`: normative contracts and evidence.
- `docs/archive/`: historical plans; never treat intermediate status as current.
