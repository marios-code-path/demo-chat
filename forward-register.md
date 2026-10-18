# Forward Register

This file holds the tip of in-flight work alone. `AGENTS.md` imports it, so
every session reads it as current fact. Nothing in it is authoritative on its
own. Each row points at the artifact that is.

## Rules for this file

The owner set these rules on 2026-10-17, under `CHAT-exkzongc`.

1. Keep only in-flight work, and the state that a reader needs for it.
2. Keep no known data older than 24 hours.
3. Move older data to `forward-register-archive-<date-of-archive>.md`.
4. To archive, update this file first. Then move it whole to the archive name.
5. Write a new `forward-register.md` for the work at hand.

**The `Checkout` row records the last substantive merge before a register
change.** A register change does not move the row. That lag is not drift. The
owner set this rule on 2026-09-29.

`shell-scripts/build-health.sh` fails when this file passes 65,536 bytes.

## Where things stand

Measured on 2026-10-17 at 21:00 PDT.

| | |
|---|---|
| Checkout | `master` at `be71cd44`, the merge commit of PR #255, in sync with `origin`. Untracked owner files stay in the main checkout. |
| Last merged PR | #255, merged on 2026-10-18 at 02:06 UTC, which is 2026-10-17 in PDT. |
| Merge strategy | Merge commits only. Every merge needs the owner's named `--admin` bypass. |
| Open PRs | None before this work. |
| Branches and worktrees | Twenty-one local branches. Two hold commits that master lacks, and the archive of 2026-10-17 names them. Every branch ref is kept. |

## In flight

| Issue | Branch | State |
|-------|--------|-------|
| `CHAT-exkzongc` | `chat-exkzongc-register-tip` | This change. It archives the register of 2026-10-17 and starts this file. The PR is not merged. |
| `CHAT-aqpcacwv` | none | Initial durable deployment: Cassandra, Redis pub/sub, Lucene, REST, MCP, and the shell. Last update on 2026-10-17 at 06:03 UTC. |

Four more issues are `in-progress` in fp, with no update in the last 24 hours:
`CHAT-owiksxvz`, `CHAT-ivtnvgyg`, `CHAT-uxlstrdy`, and `CHAT-pphwooqm`.

## Landed in the last 24 hours

| PR | Issue | What |
|----|-------|------|
| #249 | `CHAT-msanybaw` | `just launch-shell` selects the key type of its core |
| #250 | `CHAT-tkijkkbc` | `just build-shell` and `launch-shell --build` |
| #251 | `CHAT-ehpgyapa` | The silent long key type fallback is gone |
| #252 | `CHAT-wmfemntw` | `import-claude` uses a logged-in Admin session |
| #253 | `CHAT-dteylnje` | Embedded vectors in the Cassandra deployment |
| #254 | `CHAT-ynkmugeb` | The README states that vector indexing is opt-in |
| #255 | `CHAT-jrlctimq` | The `!` command runs a host command from chat-shell |

## The archive

Nothing imports these files, so they cost no context until a reader opens one.

- `forward-register-archive-2026-08.md`, for 2026-08-25 to 2026-08-31.
- `forward-register-archive-2026-09.md`, for 2026-09-01 to 2026-09-30.
- `forward-register-archive-2026-10.md`, for 2026-10-01 to 2026-10-15.
- `forward-register-archive-2026-10-17.md`, the whole register at PR #255. It
  holds the decisions carried forward, the unverified claims, and the things
  worth not relearning.
