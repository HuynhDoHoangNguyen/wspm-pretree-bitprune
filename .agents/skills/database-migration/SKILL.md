---
name: database-migration
description: Develop or review MySQL, Flyway, JPA schema, entity mappings, migrations, and seed data for the backend database. Use for schema lifecycle work; do not activate for ordinary repository queries or UI-only tasks with no schema impact.
---

# Database Migration

Use `repo-context-router` first. Treat `AGENTS.md`, `ARCHITECTURE.md`, current application configuration, and the actual Flyway classpath as authoritative for runtime state; proposals under `docs/database/` are not deployed merely because they exist.

## Route database context

Read only:

- `backend/src/main/resources/application.yaml` for datasource, Flyway locations, and Hibernate validation policy;
- filenames and order under `backend/src/main/resources/db/migration/`, then the exact migration being reviewed;
- related JPA entities, repositories, and focused persistence tests when schema compatibility is in scope;
- `compose.yaml` only for local MySQL version, ports, credentials contract, and storage;
- the specific `docs/database/` proposal or data dictionary named by the task.

Do not scan every entity, migration, SQL draft, or database document by default.

## Distinguish draft from applied migration

- Files under `docs/database/` are review material unless an approved process explicitly promotes them.
- Files under the configured Flyway classpath are migration candidates, but filesystem presence alone does not prove a database has applied them; use Flyway history or an authorized validation step.
- Never automatically copy `docs/database/V2__complete_laptop_combo_schema.sql` into Flyway, overwrite a classpath migration with it, or assume same-named files are identical.
- Preserve `spring.jpa.hibernate.ddl-auto: validate`; schema ownership remains with reviewed migrations.

## Workflow

1. Inventory current ordered migration filenames and identify the exact database/environment in scope.
2. Establish whether each relevant SQL file is a proposal, pending migration, or applied migration using path plus runtime evidence when available.
3. Compare the proposed schema change with existing migrations and only the affected entity mappings, constraints, indexes, and seed dependencies.
4. Keep schema change, backend integration, and frontend integration as separate reviewable tasks.
5. Validate safely before any authorized migrate operation; report static review, Flyway validation, migration execution, and application startup as distinct evidence.

## Data and safety rules

- Do not edit an already-applied versioned migration; use a new reviewed migration unless the environment is explicitly disposable and the user authorizes another approach.
- Do not seed mined patterns or combos manually as a substitute for actual mining output.
- Separate reference seed data, test fixtures, pipeline-generated data, and runtime data.
- Do not run destructive DDL/DML, clean, drop, truncate, data-rewriting migration, or volume removal without explicit approval and an exact target/recovery plan.

## Stop conditions

Stop when migration history is unknown but application of a migration is requested, draft and classpath files conflict, entity mappings cannot be reconciled with the approved schema, required data semantics are unclear, or a destructive operation lacks explicit approval. Do not infer that a schema proposal is implemented.

## Verification checklist

- Version and description ordering are valid with no duplicate migration version.
- SQL matches the intended MySQL version and preserves required data or documents the approved transformation.
- Affected entities match table/column names, types, nullability, keys, relationships, and indexes.
- Flyway validation and Hibernate `ddl-auto: validate` outcomes are reported only if actually executed.
- Seed provenance is explicit and no mining result is fabricated.
- Roll-forward, rollback/recovery expectations, and destructive risk are documented for material changes.
