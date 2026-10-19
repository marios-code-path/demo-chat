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

Measured on 2026-10-18 at 17:16 PDT.

| | |
|---|---|
| Checkout | `master` at `be71cd44`, the merge commit of PR #255, in sync with `origin`. PR #256 merged after it as `e1070ff4`. That was a register change, so this row does not move. Untracked owner files stay in the main checkout. |
| Last merged PR | #258, merged on 2026-10-18 at 08:01 UTC, after #257 at 08:01 UTC. |
| Merge strategy | Merge commits only. Every merge needs the owner's named `--admin` bypass. |
| Open PRs | #259, a draft, and the PR of this change. |
| Branches and worktrees | Two local branches hold commits that master lacks, and the archive of 2026-10-17 names them. Every branch ref is kept. |

## In flight

| Issue | Branch | State |
|-------|--------|-------|
| `CHAT-jmnjvupr` | `chat-jmnjvupr-topic-register` | The topic register on node 9. PR #257 (the design, the stage 0 proof, and the stage 1 plan) and PR #258 (the hand-off) are merged. Draft PR #259 holds stage 1, and it is not merged. Sigma's reviews found the `!` status, the `read.sh` checks, the UUID byte length, and the launch deadline of `register-read`. Sigma then found two deadline gaps: the launcher could stall before Java starts, and blocked output could stop the watchdog. Those fixes are in. Sigma then found three more deadline paths: a child that ignores TERM, a stalled `perl` before the timer, and a late output that could report success. Sigma then found a fourth: an output that ends after the 30 s mark and before the watchdog runs. The branch carries fixes for all four at `5f8c8498`. Sigma has not reviewed the last fix yet. The session-start read is one command, `register-read`, in one JVM process. It takes about 4.5 s from launch to exit on representative data, under the 10 s target of decision 20. Sigma recommended that command. Mario confirmed on 2026-10-18 that `AGENTS.md` will direct agents to run it in stage 3. This decision does not authorize a merge. The 30 s limit counts from the launch and gives status 124: the launcher starts its timer first, ends every job group with TERM and then KILL, and uses no `perl`. A watchdog in the JVM covers the rest. Neither waits for a blocked write, and a late output is never a success. Live cases A14 and A15 show it. So resident mode (task 9) is not built for this workload. The hand-off is the newest comment on the issue. Read it with `fp context CHAT-jmnjvupr`. |
| `CHAT-siaetqtr` | `chat-jmnjvupr-topic-register` | A child of `CHAT-jmnjvupr`. `add-topic` failed on a `uuid` core with `A key id cannot be a byte[]`. The cause was CBOR, which writes a UUID as 16 bytes. The fix is in draft PR #259 and it is not merged. Live cases A8 to A10 pass with it. |

**The next Theta session starts here.**

1. Run `fp context CHAT-jmnjvupr`, and read the newest comments. Run `gh pr view 259`.
2. Do not merge PR #259 or PR #260 without Mario's explicit merge authorization and the named `--admin` bypass. A review comment is not that authorization.
3. Stage 3 builds `node-9/bin/register read --as <name>` on `register-read`. It then changes `AGENTS.md`. It still needs the later duplicate pointers of one fp ID, with a warning line, and a measurement on the real register.
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
| #257 | `CHAT-jmnjvupr` | The design of the topic register, the stage 0 proof, and the stage 1 plan |
| #258 | `CHAT-jmnjvupr` | The hand-off of the topic register work in this register |

## The archive

Nothing imports these files, so they cost no context until a reader opens one.

- `forward-register-archive-2026-08.md`, for 2026-08-25 to 2026-08-31.
- `forward-register-archive-2026-09.md`, for 2026-09-01 to 2026-09-30.
- `forward-register-archive-2026-10.md`, for 2026-10-01 to 2026-10-15.
- `forward-register-archive-2026-10-17.md`, the whole register at PR #255. It
  holds the decisions carried forward, the unverified claims, and the things
  worth not relearning.
