# Browser E2E gates

TV2 Pattern/Combo fixture gate: `node frontend/test/e2e/tv2-run.mjs` from root.
It uses a test-classpath backend, actual application build and owned disposable
MySQL with V1–V4. It does not use the running demo server or fake HTTP responses.
See [INT-14 manual setup and mining handoff](../../../docs/research/TV2-INT-14_manual_and_mining_handoff.md)
for compilation/classpath prerequisites, optional manual mode, expected results
and cleanup. Its fixture acceptance is separate from real mining acceptance.

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
