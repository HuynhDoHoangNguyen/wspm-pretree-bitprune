---
name: spring-backend
description: Develop or review Spring Boot REST APIs in the backend module, including controllers, services, DTOs, mappers, JPA entities, repositories, validation, and backend tests. Use for backend feature work; do not activate for mining-core changes or frontend-only tasks.
---

# Spring Backend

Use `repo-context-router` first and keep work inside one backend feature slice. `AGENTS.md` and `ARCHITECTURE.md` remain authoritative.

## Route context by feature

Start from the requested controller, service, repository, entity, DTO, mapper, or test. Discover filenames before opening contents, then read only:

- the target file and direct collaborators on its request-to-persistence path;
- matching focused tests under `backend/src/test/`;
- `backend/pom.xml` only when dependencies, plugins, or test tooling matter;
- `backend/src/main/resources/application.yaml` only for runtime, persistence, validation, Flyway, port, or profile behavior;
- exact migrations through `database-migration` only when schema compatibility is involved.

Do not scan the whole backend. Keep JPA entities, API DTOs, and mining domain models separate. Controllers/services may call a deliberate `mining-core` boundary but must not copy mining formulas or representations.

If backend code merely consumes an unchanged mining result, inspect only the public boundary it calls. Activate `wspm-research-guard` and `mining-correctness` only when the task can change mining semantics, thresholds, pattern identity/support, pruning, or scientific claims.

## Workflow

1. State the endpoint or feature slice, expected API contract, direct collaborators, and whether persistence or mining is involved.
2. Trace only the relevant path: HTTP mapping and validation -> service transaction/business rule -> mapper/DTO -> repository/entity.
3. Add service-layer logging at meaningful business transitions and failure points using the existing logging stack.
4. Preserve dependency direction and avoid unrelated refactors across feature slices.
5. Add or update the smallest focused test for the changed behavior.
6. Run focused tests first; run `mvn -pl backend -am test` only when the requested change and dependencies justify module verification.

## Service logging

When creating or materially changing a service, add logs that make failures diagnosable without changing business behavior:

- use SLF4J parameterized logging and the logging dependencies already provided by Spring Boot;
- use `INFO` for material state changes or completed business operations, `DEBUG` for routine read/diagnostic detail, `WARN` for rejected or recoverable conditions, and `ERROR` for unexpected failures;
- include stable context such as operation name and safe entity/run identifiers, but never credentials, tokens, raw personal data, full request payloads, mining datasets, or other sensitive/large values;
- log an exception with its stack trace at the service boundary that owns enough business context, then rethrow or translate it according to the existing error contract; do not swallow it;
- avoid duplicate logging of the same exception across repository, service, and controller layers, and avoid noisy per-row/per-item logs in loops.

Do not add broad `try/catch` blocks solely to produce a log. Expected validation or not-found paths should follow the established exception contract and use a level proportional to their operational significance.

## Classify verification

- **Unit test:** collaborators are mocked; verifies one class or mapping rule.
- **Web/controller test:** verifies routing, validation, status, and JSON while dependencies may be mocked.
- **Integration test:** loads the relevant Spring/database boundary and verifies real wiring or persistence behavior.

Report each category actually executed. Do not call a mocked or standalone test an integration test, and mark unexecuted criteria `NOT VERIFIED`.

## Stop conditions

Stop and request the missing contract or decision when the endpoint behavior is ambiguous, DTO/entity/schema mappings conflict, a required migration is unapproved, or the task would alter mining behavior without its governing research contracts. Do not invent an API because a frontend screen exists.

## Verification checklist

- Request mapping, status, validation, and response DTO match the stated contract.
- Service transaction boundary and repository query semantics are appropriate.
- Service logs cover meaningful state transitions and unexpected failures with safe context and appropriate levels.
- Exceptions are not swallowed or redundantly logged, and logs contain no secret, token, PII, dataset, or oversized payload.
- Entity and DTO remain separate; schema-dependent fields match approved migrations.
- Focused tests cover success plus relevant validation, empty, not-found, or error paths.
- Broader tests and runtime/database checks are reported separately from unit evidence.
