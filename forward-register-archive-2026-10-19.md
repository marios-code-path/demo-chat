# Forward Register archive, 2026-10-19

This is the whole register at PR #262, the merge of stage 2. The live
register is `forward-register.md`. Nothing imports this file.

## Where things stand

Measured on 2026-10-19 at 21:35 PDT.

| | |
|---|---|
| Checkout | `master` at `bb0796d3`, the merge commit of PR #262. PR #260 merged as `95799ce5` and PR #261 as `43bb46bd`. Both were register changes, so they did not move the row. |
| Last merged PR | #262, stage 2. |
| Merge strategy | Merge commits only. Every merge needs Mario's explicit authorization and the owner's named `--admin` bypass. A review comment is not that authorization. |
| Open PRs | The PR of this register change. |
| Branches and worktrees | `chat-hcfgkbuz-node-9` is kept at `1ad85a5e`. Its worktree is removed. Every branch ref is kept. |

## In flight

| Issue | Branch | State |
|-------|--------|-------|
| `CHAT-jmnjvupr` | none | The topic register on node 9. Stage 1 merged as PR #259. Stage 2 merged as PR #262: the `node-9/` directory, its Compose project and image prefix, its own Consul, Cassandra, and Redis, `start`, `stop`, `setup-secrets`, `save-images`, `load-images`, the vector toggle, the live proof, the documents, and the gates. `CHAT-hcfgkbuz` is done. The build order continues with stage 3. |

**Measured in stage 2, live.** Node 9 started beside node 16 and reported
health on 6891. The Admin password came from `node-9/userinit.yml`. A second
start applied no schema. A post wrote a vector without a rebuild. A restart
reused the vector files. Toggle off kept the files. Node 16 stayed unchanged.

**Fixed after review, on 2026-10-19.** The proof held the live Admin password.
The value is `<redacted>`. The password is rotated. The new value logs in with
exit 0. The old value is refused with status 77. `node-9/bin/start` reads
`CORE_IMAGE` from `node-9.env` and calls Maven only when the image is absent.
A host with the loaded image needs Docker, `curl`, `nc`, and `openssl` alone.

**Open:** the move is unverified. The owner named no destination host. A
Docker-in-Docker daemon needs `--privileged` and the owner's approval. Design
items 5 (live follow) and 7 (agent sandbox) are unverified.

**Open from stage 1:** a blocked reader at the limit can leave part of the
output, so a caller discards output from a nonzero exit. The native one-shot
image builds and its context does not start. Case A11 and the interactive
shell against the old failure were not run.

## Landed in the last 24 hours

| PR | Issue | What |
|----|-------|------|
| #257 | `CHAT-jmnjvupr` | The design of the topic register, the stage 0 proof, and the stage 1 plan |
| #258 | `CHAT-jmnjvupr` | The hand-off of the topic register work in this register |
| #259 | `CHAT-jmnjvupr`, `CHAT-siaetqtr` | Stage 1: `chat-shell-cli`, `post`, `messages --format records`, `register-read`, and the UUID fix for CBOR |
| #260 | `CHAT-jmnjvupr` | The register entry for stage 1 |
| #261 | `CHAT-jmnjvupr` | The register change for stage 2 |
| #262 | `CHAT-hcfgkbuz` | Stage 2: the `node-9/` launcher, its Compose project, the schema step, image save and load, and the live proof |

## The archive

- `forward-register-archive-2026-08.md`, for 2026-08-25 to 2026-08-31.
- `forward-register-archive-2026-09.md`, for 2026-09-01 to 2026-09-30.
- `forward-register-archive-2026-10.md`, for 2026-10-01 to 2026-10-15.
- `forward-register-archive-2026-10-17.md`, the whole register at PR #255.
- `forward-register-archive-2026-10-18.md`, the whole register at PR #260.
- `forward-register-archive-2026-10-19.md`, this file, the whole register at
  PR #262.
