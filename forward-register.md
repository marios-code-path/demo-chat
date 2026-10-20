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

Measured on 2026-10-19 at 21:35 PDT.

| | |
|---|---|
| Checkout | `master` at `bb0796d3`, the merge commit of PR #262. That is the last substantive merge, and the register change follows it. |
| Last merged PR | #262, stage 2. |
| Merge strategy | Merge commits only. Every merge needs Mario's explicit authorization and the owner's named `--admin` bypass. A review comment is not that authorization. |
| Open PRs | The PR of this register change. |
| Branches and worktrees | `chat-hcfgkbuz-node-9` is kept at `1ad85a5e`. Its worktree is removed. Every branch ref is kept. |

## In flight

| Issue | Branch | State |
|-------|--------|-------|
| `CHAT-jmnjvupr` | none | The topic register on node 9. Stage 1 is merged: PR #259. Stage 2 is merged: PR #262, merge commit `bb0796d3`. It delivers `node-9/`: one Compose project `chat-node-9`, its own image prefix, its own Consul, Cassandra, and Redis, `start`, `stop`, `setup-secrets`, `save-images`, `load-images`, the vector toggle, the live proof, the documents, and the gates. `CHAT-hcfgkbuz` is done. Stage 3 has no plan yet. |

**The next Theta session starts here.**

1. Read `fp context CHAT-jmnjvupr` and the newest comments first.
2. Ask Mario whether stage 3 starts now. Stage 3 builds
   `node-9/bin/register read --as <name>` on `register-read`. It then changes
   `AGENTS.md`. Read the design sections "Node 9" and "Build order" first.
3. Stage 3 also needs the later duplicate pointers of one fp ID, with a
   warning line, and a measurement on the real register.
4. Every live run uses a disposable core. Never use `chat-deploy core` near
   node 16. Node 9 runs on 6890 and 6891.

**Measured in stage 2, live.** Node 9 started beside node 16 and reported
health on 6891. A second start applied no schema. A post wrote a vector
without a rebuild. A restart reused the vector files. Toggle off kept the
files. Node 16 stayed unchanged. `node-9/bin/start` calls no Maven when the
image is present.

**Open:** the move to another host is unverified. The owner named no
destination host. Design items 5 (live follow) and 7 (agent sandbox) are
unverified. The native one-shot image builds and its context does not start.
Case A11 is not run.

The in-progress issues are `CHAT-aqpcacwv`, `CHAT-owiksxvz`, `CHAT-ivtnvgyg`,
`CHAT-uxlstrdy`, `CHAT-pphwooqm`, and `CHAT-jmnjvupr`.

## Landed in the last 24 hours

| PR | Issue | What |
|----|-------|------|
| #262 | `CHAT-hcfgkbuz` | Stage 2: the `node-9/` launcher, its Compose project, the schema step, image save and load, and the live proof |

## The archive

Nothing imports these files, so they cost no context until a reader opens one.

- `forward-register-archive-2026-08.md`, for 2026-08-25 to 2026-08-31.
- `forward-register-archive-2026-09.md`, for 2026-09-01 to 2026-09-30.
- `forward-register-archive-2026-10.md`, for 2026-10-01 to 2026-10-15.
- `forward-register-archive-2026-10-17.md`, the whole register at PR #255. It
  holds the decisions carried forward and the unverified claims.
- `forward-register-archive-2026-10-18.md`, the whole register at PR #260. It
  holds the deadline findings of PR #259.
- `forward-register-archive-2026-10-19.md`, the whole register at PR #262. It
  holds the stage 2 measurements, the review fixes, and the landed rows of
  #257 to #262.
