# Documentation

Design documents, audits and logging artifacts for Wizards & Beasts.

## Why this is not `docs/`, and why the repository root is ignored

A `git filter-repo` pass on 2026-08-10 stripped `tools/`, `docs/`, `tasks/`,
`blockbench/`, `.vscode/`, `.cursor/` and every root-level `.md` except `README.md`
from every commit in the repository's history. The corresponding ignore rules were
added at the same time so the purged paths would not immediately come back:

```
/tools/
/blockbench/
/docs/
/tasks/
/*.md
!/README.md
```

Those rules are still in force and this directory does not touch them. **`docs/` is
covered by that list** — `git check-ignore -v docs/anything.md` reports
`.gitignore:153:/docs/` — so moving documentation into `docs/` would have left it
exactly as invisible as it was at the root. `documentation/` is matched by no rule,
which is why it has this name.

## The failure this fixes

Writes to an ignored path succeed. Nothing errors, nothing warns, and `git status`
stays clean — so an audit written in one session was simply absent in the next, and
nobody could tell the difference between "this was never written" and "this was
written and git could not see it". Two agent sessions halted because a companion
schema "did not exist"; it did, on disk, unreadable to git.

If you are adding a design document, an audit or a log: **put it here, not at the
root.** A root `.md` will be silently discarded.

## Layout

| Path | Contents |
|---|---|
| `documentation/*.md` | Current design docs, audits, and the two live logging artifacts |
| `documentation/audit/` | Scoped audit reports |
| `documentation/history/` | Superseded design docs, kept for provenance |

The two files that are written continuously, and the reason this migration was
urgent:

- `AUDIT_PUNCHLIST.md` — open findings and things found but deliberately not fixed.
- `MIGRATION_DELTAS.md` — behavioural deltas, one entry per change that has one.

## Not moved

- `tasks/` (`todo.md`, `lessons.md`, `ministry_plan.md`) is still ignored. Those
  paths are prescribed by the user's global `CLAUDE.md` workflow, so relocating them
  would break a convention this migration has no mandate to change. They remain
  invisible to git. Logged in `AUDIT_PUNCHLIST.md`.
- `graphify-out/` is generated tool output, ignored by its own separate rule.

## Currency

Nothing here was edited by the migration — files were moved and inbound path
references updated, nothing else. Several documents carry a `Branch:` header naming
`fix/chamber-of-secrets-module-gate` and dates from July and August 2026; whether any
given document is still current was explicitly **not** assessed. Check the content,
not the fact that it is in this directory.
