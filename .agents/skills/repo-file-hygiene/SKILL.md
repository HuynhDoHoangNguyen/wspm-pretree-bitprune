---
name: repo-file-hygiene
description: Keep repository edits within the authorized task, track created/modified/deleted files, clean disposable files created during that task, and prepare approval lists for deleting pre-existing files. Use for repository mutations and cleanup in wspm-pretree-bitprune.
---

# Repository file hygiene

Apply the file rules in AGENTS.md alongside the relevant module skill. This
skill controls artifacts and edit scope; it does not authorize business,
algorithm, schema, publishing or Git-history changes.

## Establish ownership and scope

- Before writing, inspect Git status and identify the task's allowed files or
  narrow directories, including required tests and documentation. Record what
  already exists; ignored/untracked files can belong to the user or prior tasks.
- Keep a working list of files created, modified and deleted during this task,
  including ignored temporary files. Use conversation/session state rather than
  creating a manifest/report file by default. Retain this distinction across
  turns and interruptions of the same task.
- Preserve unrelated dirty changes. Before expanding the stated scope, explain
  which additional files are necessary and why. Ask only when the expansion
  needs an unprovided requirement or exceeds the authorized task.
- Limit formatters, generators and search-driven edits to the selected files.
  Do not use a repository-wide rewrite for a feature-scoped change.

## Avoid disposable artifacts

- Prefer read-only commands, inline scripts and existing maintained tools over
  saving a new helper script. Do not add scaffolds, duplicate reports, logs or
  backups without a concrete need.
- If a temporary file is needed, put it under one clearly owned task directory
  in ignored .agent-workspace or an OS temporary directory. Track its exact path;
  use existing maintained documentation/checklists for lasting information.
- Source code, tests, fixtures, configuration, contracts, acceptance evidence,
  deliverables and reusable tooling are not temporary merely because the agent
  created them. Do not reclassify them to avoid approval.

## Deletion authorization

- A disposable file created by this agent during the current task may be deleted
  without another question only after verifying that nothing in the project,
  needed evidence or user work depends on it. Clean it before finishing.
- Any file present before this task needs explicit user confirmation before
  deletion, including ignored/generated files and helpers/logs from earlier
  tasks. Unknown provenance means keep it and ask. A general instruction to do
  a task or clean the repo is not approval of an unreviewed deletion list.
- Prepare the concrete deletion list first: exact paths, purpose, reason,
  references/dependents and evidence or replacement that will remain. A batch
  approval is sufficient for that list; do not ask again for the same unchanged
  approved action. New paths need their own approval.
- Moving/archiving a pre-existing file out of its location, truncating evidence
  or making it disappear through a helper is not a deletion-approval workaround.
- Inspect cleanup side effects of tools/runners, including git clean and mvn
  clean. Never let a wrapper or automatic cleanup delete pre-existing files that
  are outside the approved list. Defer that operation while doing independent
  authorized work.
- Before any recursive deletion on Windows, resolve and verify the absolute
  target stays inside its intended owned/approved directory. Use native
  PowerShell with literal paths throughout. Do not recursively delete a mixed
  directory containing pre-existing or unapproved files.

## Close the task

Review the final diff, actual file list and temporary artifacts against the
starting state. Run verification relevant to the change; instruction-only edits
need skill/link/diff validation, not application regression tests.

Report created, modified and deleted files, what temporary files were cleaned,
what pre-existing candidates await approval, and verification/limitations in
the task's final chat report. Do not create another persistent report just to
record this inventory. Never claim cleanup is complete while proposed deletions
are awaiting confirmation; report completed and pending work separately.
