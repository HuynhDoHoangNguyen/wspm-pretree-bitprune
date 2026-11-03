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
│       │   ├── controllers/  REST endpoints
│       │   ├── dto/          API response models
│       │   ├── entities/     JPA entities
│       │   ├── mappers/      Entity-to-DTO mapping
│       │   ├── repositories/ Spring Data repositories
│       │   └── services/     Application services
│       ├── main/resources/application.yaml
│       └── test/             Backend unit/integration tests
├── frontend/
│   ├── package.json
│   ├── public/
│   └── src/
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

The backend currently exposes health, product and category reads. It uses:

- Spring Web MVC for HTTP;
- Spring Data JPA/JDBC for persistence;
- MySQL Connector/J;
- Actuator for operational health;
- `mining-core` as an in-reactor Java dependency.

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

The development proxy points to backend port 8080. A legacy
`ApplicationConstants.HOME_PATH` currently references port 8085; resolve this
configuration mismatch in a dedicated frontend task rather than hiding it in
documentation.

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
