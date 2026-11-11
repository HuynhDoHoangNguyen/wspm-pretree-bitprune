---
name: repo-context-router
description: Route work in wspm-pretree-bitprune to the smallest relevant module, documentation, configuration, and tests. Use when starting a repository task, locating implementation boundaries, or deciding whether backend, frontend, database, or mining research context is required.
---

# Repository Context Router

Select context before reading implementation. `AGENTS.md` is authoritative; this skill narrows its reading routes and never overrides it.

## Start with a context budget

1. Read `AGENTS.md` and the user's request.
2. Identify the owned module and whether the task changes mining semantics or only consumes mining results.
3. List likely files from paths and filenames before opening their contents.
4. Open only the target file, its direct collaborators, module configuration, and focused tests needed to complete or review the task.
5. Expand context one dependency edge at a time and state why expansion is necessary.

Do not recursively read `src/`, all tests, all `docs/research/`, or `docs/archive/`. Ignore generated `target/`, `node_modules/`, and `build/` trees.

## Route by task

| Task | Initial context | Conditional expansion |
|---|---|---|
| Repository setup, commands, module ownership | `README.md` relevant section, `ARCHITECTURE.md`, applicable `pom.xml` or `frontend/package.json` | Runtime configuration only when the command or environment is in scope |
| React component/page/hook | Target under `frontend/src/`, direct imports, nearest types/models/styles, `frontend/package.json`, focused frontend tests if present | Backend endpoint contract only when request/response behavior is involved; no research contracts by default |
| Spring endpoint/service/repository | Target chain under `backend/src/`, related DTO/entity/mapper, `backend/pom.xml`, matching backend tests | `application.yaml` or migration only when persistence/runtime schema is involved; do not read `mining-core` unless the endpoint calls it or changes mining result semantics |
| Database or migration | Exact migration/schema file, related entities/repositories, database configuration, focused tests | `docs/database/` document named by the task; never treat a review draft as an applied migration |
| Mining implementation or research claim | `docs/README.md`, exact changed package under `mining-core`, focused tests | Activate the research and correctness routes below; open only contracts selected by the algorithmic concern |
| Cross-module integration | `ARCHITECTURE.md`, configuration and boundary files for only the participating modules | Follow the dependency direction: frontend -> HTTP -> backend -> mining-core; never copy mining formulas outward |

## Escalate only on semantic triggers

Use `wspm-research-guard` when a task touches WSPM/PRISM behavior, support or weight formulas, thresholds, candidate generation, pruning, temporal verification, dataset semantics, benchmarks, or research claims.

Use `mining-correctness` when implementing or reviewing mining behavior or tests. A backend or frontend task does not need these skills merely because the repository contains mining code.

## Verify the route

Before editing, record:

- in-scope module and likely files;
- whether mining output can change;
- documents/tests that must be read;
- large or historical areas intentionally excluded.

After the task, verify only the owning module first, then broader modules only when a dependency boundary or the requested acceptance criteria require it.
