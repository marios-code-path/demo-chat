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

Measured on 2026-10-18 at 00:35 PDT.

| | |
|---|---|
| Checkout | `master` at `be71cd44`, the merge commit of PR #255, in sync with `origin`. PR #256 merged after it as `e1070ff4`. That was a register change, so this row does not move. Untracked owner files stay in the main checkout. |
| Last merged PR | #256, merged on 2026-10-18 at 04:05 UTC, which is 2026-10-17 in PDT. |
| Merge strategy | Merge commits only. Every merge needs the owner's named `--admin` bypass. |
| Open PRs | #257, a draft, and the PR of this change. |
| Branches and worktrees | Two local branches hold commits that master lacks, and the archive of 2026-10-17 names them. Every branch ref is kept. |

## In flight

| Issue | Branch | State |
|-------|--------|-------|
| `CHAT-jmnjvupr` | `chat-jmnjvupr-topic-register` | The topic register on node 9. Draft PR #257 at `e3e0216a` holds the design, the stage 0 proof, and the stage 1 plan. Stage 1 has not started, and it waits for the owner's approval. The hand-off is the newest comment on the issue. Read it with `fp context CHAT-jmnjvupr`. |
| `CHAT-siaetqtr` | none | A child of `CHAT-jmnjvupr`. `add-topic` fails on a `uuid` memory core with `A key id cannot be a byte[]`. It blocks task 8 of stage 1 and all of stage 3. |

**The next Theta session starts here.**

1. Run `fp context CHAT-jmnjvupr`, and read the hand-off comment.
2. Ask the owner whether stage 1 runs.
3. Run stage 1 in `.worktrees/chat-jmnjvupr-topic-register` with `superpowers:executing-plans`. Tasks 1 to 7 and 10 can run. Tasks 8 and 9 wait for `CHAT-siaetqtr`.
4. Every live run uses the stage 0 sandbox and a disposable core on 6990 and 6991. Never use `chat-deploy core` near node 16.

Five more issues are `in-progress` in fp, with no update in the last 24 hours:
`CHAT-aqpcacwv`, `CHAT-owiksxvz`, `CHAT-ivtnvgyg`, `CHAT-uxlstrdy`, and `CHAT-pphwooqm`.

## Landed in the last 24 hours

| PR | Issue | What |
|----|-------|------|
| #253 | `CHAT-dteylnje` | Embedded vectors in the Cassandra deployment |
| #254 | `CHAT-ynkmugeb` | The README states that vector indexing is opt-in |
| #255 | `CHAT-jrlctimq` | The `!` command runs a host command from chat-shell |
| #256 | `CHAT-exkzongc` | The register became this 24-hour tip, and its earlier text moved to `forward-register-archive-2026-10-17.md` |

## The archive

Nothing imports these files, so they cost no context until a reader opens one.

- `forward-register-archive-2026-08.md`, for 2026-08-25 to 2026-08-31.
- `forward-register-archive-2026-09.md`, for 2026-09-01 to 2026-09-30.
- `forward-register-archive-2026-10.md`, for 2026-10-01 to 2026-10-15.
- `forward-register-archive-2026-10-17.md`, the whole register at PR #255. It
  holds the decisions carried forward, the unverified claims, and the things
  worth not relearning.
