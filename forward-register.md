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

Measured on 2026-10-19 at 15:10 PDT.

| | |
|---|---|
| Checkout | `master` at `d8f9713a`, the merge commit of PR #259. PR #260 merged as `95799ce5` and PR #261 as `43bb46bd`. Both were register changes, so this row does not move. The stage 2 branch starts at `43bb46bd`. |
| Last merged PR | #261, the register change for stage 2. #260 merged before it. |
| Merge strategy | Merge commits only. Every merge needs Mario's explicit authorization and the owner's named `--admin` bypass. A review comment is not that authorization. |
| Open PRs | #262, ready for Mario's acceptance test at `45ed70ef`. It holds the stage 2 plan, tasks 1 to 6, and the fixes of one review. The review found a secret in a proof file, a Maven call in `start`, a stale register row, and a stale plan step. All four are fixed. The review holds no blocker. The three GitHub checks pass: agent-http, build, and integration. |
| Branches and worktrees | `chat-hcfgkbuz-node-9` holds the stage 2 work. Its worktree is `.worktrees/chat-hcfgkbuz-node-9`. Every branch ref is kept. |

## In flight

| Issue | Branch | State |
|-------|--------|-------|
| `CHAT-jmnjvupr` | `chat-hcfgkbuz-node-9` | The topic register on node 9. Stage 1 is merged as PR #259. Stage 2 is in PR #262, ready for review. Tasks 1 to 6 are complete. Task 1 is the toggle and the Compose files. Task 2 is secrets, the Admin password file, and the schema step. Task 3 is the image, start, and stop. Task 4 is image save and load. Task 5 is the live proof. Task 6 is the documents, the gates, and this register. |
| `CHAT-hcfgkbuz` | `chat-hcfgkbuz-node-9` | Stage 2 of the topic register. The plan is `docs/superpowers/plans/2026-10-18-topic-register-stage-2-node-9.md`. The live proof is `docs/superpowers/proofs/2026-10-18-node-9/RECORD.md`. |

**Measured in stage 2, live.** Node 9 started beside node 16 and reported
health on 6891. The Admin password came from `node-9/userinit.yml`. A second
start applied no schema. A post wrote a vector without a rebuild. A restart
reused the vector files. Toggle off kept the files. Node 16 stayed unchanged.

**Fixed after review, on 2026-10-19.** The proof held the live Admin password.
The value is `<redacted>` now. The password is rotated. The new value logs
in with exit 0. The old value is refused with status 77. `node-9/bin/start` reads `CORE_IMAGE` from
`node-9.env` and calls Maven only when the image is absent. A host with the
loaded image needs Docker, `curl`, `nc`, and `openssl` alone. A live start
proved it and built nothing.

**Open:** the move is unverified. The owner named no destination host. A
Docker-in-Docker daemon needs `--privileged` and the owner's approval. Design
items 5 (live follow) and 7 (agent sandbox) are unverified and carry their
reasons in the design.

**Open from stage 1:** a blocked reader at the limit can leave part of the
output, so a caller discards output from a nonzero exit. The native one-shot
image builds and its context does not start. Case A11 and the interactive
shell against the old failure were not run.

**The next Theta session starts here.**

1. Mario runs the acceptance test on PR #262 at `45ed70ef`.
2. Do not merge. A merge needs Mario's explicit authorization and the
   owner's named `--admin` bypass. Review acceptance is not that
   authorization.
3. Stage 3 builds `node-9/bin/register read --as <name>` on `register-read`.
   It then changes `AGENTS.md`.
4. Read `fp context CHAT-jmnjvupr` and the newest comments first.

The in-progress issues are `CHAT-aqpcacwv`, `CHAT-owiksxvz`, `CHAT-ivtnvgyg`,
`CHAT-uxlstrdy`, `CHAT-pphwooqm`, `CHAT-jmnjvupr`, and `CHAT-hcfgkbuz`.

## Landed in the last 24 hours

| PR | Issue | What |
|----|-------|------|
| #257 | `CHAT-jmnjvupr` | The design of the topic register, the stage 0 proof, and the stage 1 plan |
| #258 | `CHAT-jmnjvupr` | The hand-off of the topic register work in this register |
| #259 | `CHAT-jmnjvupr`, `CHAT-siaetqtr` | Stage 1: `chat-shell-cli`, `post`, `messages --format records`, `register-read`, and the UUID fix for CBOR |
| #260 | `CHAT-jmnjvupr` | The register entry for stage 1 |
| #261 | `CHAT-jmnjvupr` | The register change for stage 2 |

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
