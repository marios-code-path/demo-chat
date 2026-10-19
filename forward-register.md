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

Measured on 2026-10-18 at 22:55 PDT.

| | |
|---|---|
| Checkout | `master` at `d8f9713a`, the merge commit of PR #259, in sync with `origin`. PR #260 merged after it as `95799ce5`. That was a register change, so this row does not move. Untracked owner files stay in the main checkout. |
| Last merged PR | #260, merged on 2026-10-19 at 05:41 UTC, after #259 at 02:27 UTC. |
| Merge strategy | Merge commits only. Every merge needs Mario's explicit authorization and the owner's named `--admin` bypass. A review comment is not that authorization. |
| Open PRs | The PR of this change. |
| Branches and worktrees | Measured with `git cherry` on 2026-10-18. Two local branches hold a commit that master lacks, with no equivalent patch: `backup/chat-bmmtojqm-before-review` and `chat-urhjrwbt-indexelastic`. Four more are not ancestors of master, and master holds their change as an equivalent patch or by tree: `chat-hazcatpc-review-fix`, `chat-mcp-spec`, `chat-xcmpudyb-message-id`, and `chat-znprrzhn-composite-enforcement`. Every branch ref is kept. The worktrees of `CHAT-jmnjvupr` are removed. |

## In flight

| Issue | Branch | State |
|-------|--------|-------|
| `CHAT-jmnjvupr` | none | The topic register on node 9. Stage 1 is merged: PR #259, as `d8f9713a`. It holds `chat-shell-cli` (one-shot mode, a silent start, one exit status table, and a login from the environment), `post`, `messages --format records --since`, one output path, and `register-read`. `register-read` is the session-start read in one JVM process. It takes about 4.4 s from launch to exit on representative data, under the 10 s target of decision 20, and its 30 s limit counts from the launch with status 124. Sigma recommended that command, and Mario confirmed on 2026-10-18 that `AGENTS.md` will direct agents to run it in stage 3. Resident mode is not built for this workload. `CHAT-siaetqtr` (the `add-topic` failure) is done. The build order continues with stage 2, node 9 and the `node-9/` directory. The hand-off is the newest comment on the issue. Read it with `fp context CHAT-jmnjvupr`. |

**Open in stage 1, and carried to the issue comment:** the 4.4 s time is for 3
recent topics on one host. A blocked reader at the limit can leave part of the
output, so a caller discards the output of a command that does not exit with 0.
The native image builds and its context does not start. Case A11 and the
interactive shell against the old failure were not run.

**The next Theta session starts here.**

1. Run `fp context CHAT-jmnjvupr`, and read the newest comments.
2. Ask Mario whether stage 2 (node 9 and the `node-9/` directory) starts now. Read the design sections "Node 9" and "Build order" first. No plan for stage 2 exists yet.
3. Stage 3 builds `node-9/bin/register read --as <name>` on `register-read`. It then changes `AGENTS.md`. It still needs the later duplicate pointers of one fp ID, with a warning line, and a measurement on the real register.
4. Every live run uses the stage 0 sandbox and a disposable core on 6990 and 6991. Never use `chat-deploy core` near node 16. Test processes by the exact PID with `kill -0`, because a process list can be unreadable in a sandbox.

Five more issues are `in-progress` in fp, with no update in the last 24 hours:
`CHAT-aqpcacwv`, `CHAT-owiksxvz`, `CHAT-ivtnvgyg`, `CHAT-uxlstrdy`, and `CHAT-pphwooqm`.

## Landed in the last 24 hours

| PR | Issue | What |
|----|-------|------|
| #257 | `CHAT-jmnjvupr` | The design of the topic register, the stage 0 proof, and the stage 1 plan |
| #258 | `CHAT-jmnjvupr` | The hand-off of the topic register work in this register |
| #259 | `CHAT-jmnjvupr`, `CHAT-siaetqtr` | Stage 1: `chat-shell-cli`, `post`, `messages --format records`, `register-read`, and the UUID fix for CBOR |
| #260 | `CHAT-jmnjvupr` | The register entry for stage 1. It merged before it showed #259 as merged. |

## The archive

Nothing imports these files, so they cost no context until a reader opens one.

- `forward-register-archive-2026-08.md`, for 2026-08-25 to 2026-08-31.
- `forward-register-archive-2026-09.md`, for 2026-09-01 to 2026-09-30.
- `forward-register-archive-2026-10.md`, for 2026-10-01 to 2026-10-15.
- `forward-register-archive-2026-10-17.md`, the whole register at PR #255. It
  holds the decisions carried forward, the unverified claims, and the things
  worth not relearning.
- `forward-register-archive-2026-10-18.md`, the whole register at PR #260. It
  holds the deadline findings of PR #259 and the landed rows of #253 to #256.
