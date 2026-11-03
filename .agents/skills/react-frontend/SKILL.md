---
name: react-frontend
description: Develop or review the React and TypeScript frontend, including pages, components, Mantine UI, routing, hooks, API clients, models, and state. Use for frontend feature work; do not activate for backend-only, database-only, or mining-core tasks.
---

# React Frontend

Use `repo-context-router` first. Treat inherited Electro screens as adaptation candidates, not evidence that matching backend capabilities exist.

## Route context by UI slice

Start with the requested page, component, hook, route, or store. Read only:

- the target file and its direct imports;
- colocated view-model, styles, types, models, and focused tests that affect the behavior;
- the exact shared hook, HTTP utility, store, or route used by that slice;
- `frontend/package.json` and `frontend/tsconfig.json` only when scripts, dependencies, compiler behavior, or framework constraints matter;
- endpoint/proxy constants only for API integration.

Do not recursively read `frontend/src/`. Do not update dependencies, React, TypeScript, Mantine, routing, or state-library versions unless explicitly requested.

## Distinguish task scope

- **UI-only:** layout, rendering, local interaction, styling, or client state with no API contract change. Stay in frontend.
- **Cross-module integration:** request path, payload, response, authentication, error handling, or backend capability is involved. Read the exact backend controller/DTO/test contract, not the whole backend.

Before integrating an endpoint, verify that it exists and compare method, path, request fields, response fields, status codes, pagination, and error shape. Check both the development proxy and any constants used to build URLs; report mismatches instead of silently hiding them.

Do not implement mining formulas in TypeScript. Activate research/correctness skills only if the requested integration changes or interprets mining semantics rather than displaying an established API result.

## Workflow

1. Name the UI slice, route, state owner, and whether the task is UI-only or cross-module.
2. Trace direct imports and the smallest render/data path.
3. For API work, verify the backend contract before changing client code.
4. Preserve existing framework versions and established component/state patterns.
5. Run the smallest focused frontend test available, then type/build or broader tests only when warranted.

## Stop conditions

Stop when the required backend endpoint is absent or ambiguous, the UI model conflicts with the API contract, a dependency upgrade is required but unauthorized, or an inherited screen would imply unsupported backend behavior. Do not fabricate data contracts to make a screen appear functional.

## Verification checklist

- Target page/component renders the required states: loading, success, empty, and relevant error state.
- Direct imports, routes, hooks, models, and state updates remain type-consistent.
- API method/path/payload/response match verified backend evidence.
- Proxy and endpoint constants resolve to the intended backend without unrelated configuration edits.
- Tests, type checks, and builds actually run are reported separately; unrun checks are `NOT VERIFIED`.
