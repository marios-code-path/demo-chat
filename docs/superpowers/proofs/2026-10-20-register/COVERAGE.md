# Continuity coverage of the forward register

Date: 2026-10-21. Issue: `CHAT-jmnjvupr`. Plan: stage 3, task 7, items 1 to
11. This record covers the continuity copy, the read measurement, and the
30-second boundary. The `AGENTS.md` switch is not made.

The source is `forward-register.md` at commit `2d75f0cd`. That commit is the
freeze hash H, and no later commit edits the file. The entry is message
`87a33b20-cd77-11f1-9611-47b553663159` of
`forward-register-topic-register`.

Each row names one source fact and one check string. The check removes
backticks and collapses every run of white space on both sides. It then looks
for the string in the file at H and in the stored entry text. A row passes
only when both sides hold it. The check ran on 2026-10-21 against the message
read back from the store, not against the local draft. See
`live-coverage-2.out`.

The first table of this record, dated 2026-10-20, checked 26 rows against
entry `ec7756c0-cd14-11f1-b7c1-47b553663159`. Decision 39 and the later
branch facts made it stale. The table below replaces it.

## The messages

Every entry was posted as `Theta` with `node-9/bin/register post`. The
wrapper returned status `0` and one message id for each post.

| Issue | Topic | Pointer message | Entry message |
|-------|-------|-----------------|---------------|
| `CHAT-jmnjvupr` | `forward-register-topic-register` | `bd46df80-cd12-11f1-b7c1-47b553663159` | `87a33b20-cd77-11f1-9611-47b553663159` |
| `CHAT-aqpcacwv` | `forward-register-durable-deployment` | `d296ce40-cd12-11f1-b7c1-47b553663159` | `dc3f5700-cd12-11f1-b7c1-47b553663159` |
| `CHAT-owiksxvz` | `forward-register-toolchain` | `e86e8aa0-cd12-11f1-b7c1-47b553663159` | `f2111ff0-cd12-11f1-b7c1-47b553663159` |
| `CHAT-ivtnvgyg` | `forward-register-keytests-codec` | `fe023920-cd12-11f1-b7c1-47b553663159` | `07d0e780-cd13-11f1-b7c1-47b553663159` |
| `CHAT-uxlstrdy` | `forward-register-message-capture` | `13c8de80-cd13-11f1-b7c1-47b553663159` | `1d5330e0-cd13-11f1-b7c1-47b553663159` |
| `CHAT-pphwooqm` | `forward-register-kafka-stage-2` | `29708a30-cd13-11f1-b7c1-47b553663159` | `336b9fc0-cd13-11f1-b7c1-47b553663159` |

The topic of `CHAT-jmnjvupr` holds five entries. The newest is
`87a33b20-cd77-11f1-9611-47b553663159`, and it replaces the four before it.
The post returned command id `d264133a-1e26-4dc5-b44f-a9e8b678fedd` and
request id `9160c357-53e1-42e0-bce9-72aeaddd1789`. See
`live-post-entry.out`.

The entry read back byte for byte. The only difference was the final newline
that `jq -r` adds.

**Frozen.** The entry names H and the SHA-256 of the file,
`2a256ab9756a803c0cf9ea8b2a7a39ec4f9501b116dfd3377620746f59e55677`. No later
commit edits `forward-register.md`.

Every agent joined every new topic. All four agents also joined
`forward-register-livetest`, because the index names it. Phi and Mario joined
it for the first time. Sigma and Theta joined it again, so they hold a second
membership row. Every join returned status `0`.

## The coverage table

Result: **30 rows, 0 failures**. See `live-coverage-2.out`.

| # | Source fact | Check string | Result |
|---|-------------|--------------|--------|
| 1 | Checkout | `master at bb0796d3` | ok |
| 2 | Last merged PR | `#262, stage 2` | ok |
| 3 | Merge strategy | `Every merge needs Mario's explicit authorization` | ok |
| 4 | Open PRs | `It is not pushed` | ok |
| 5 | Branches and worktrees | `chat-hcfgkbuz-node-9 is kept at 1ad85a5e` | ok |
| 6 | Stage 3 tasks | `Stage 3 tasks 1 to 6 are committed` | ok |
| 7 | Task 7 items | `Task 7 items 1 to 11 are done` | ok |
| 8 | Read median | `The read median is 5089 ms` | ok |
| 9 | Decision 39 | `Decision 39 makes a pointer name a key` | ok |
| 10 | The backslash rule | `A key holds no backslash` | ok |
| 11 | The two commits | `Commits f24358c0 and 83b9b6c3 carry` | ok |
| 12 | The plan path | `docs/superpowers/plans/2026-10-19-topic-register-stage-3.md` | ok |
| 13 | The switch | `Mario reviews that switch first` | ok |
| 14 | Decisions range | `The design records decisions 1 to 39` | ok |
| 15 | Live run safety | `Node 9 runs on 6890 and 6891` | ok |
| 16 | The lease trap | `Never pause the core to stall a read` | ok |
| 17 | The 30 s boundary, stalled shell | `A stalled shell gave status 124 at 30032 ms` | ok |
| 18 | The 30 s boundary, paused core | `A paused core gave status 124 at 29716 ms` | ok |
| 19 | Stage 2 vector write | `A post wrote a vector without a rebuild` | ok |
| 20 | Stage 2 start cost | `node-9/bin/start calls no Maven when the image is present` | ok |
| 21 | Open: the move to another host | `move to another host is unverified` | ok |
| 22 | Open: case A11 | `Case A11 is not run` | ok |
| 23 | Open: the owner vault | `node-9/ansible/vault/owner.yml is absent` | ok |
| 24 | Open: the second-denial branch | `The second-denial branch of decision 38 has offline tests only` | ok |
| 25 | Landed: PR #262 | `image save and load, and the live proof` | ok |
| 26 | The archive list | `forward-register-archive-2026-10-19.md` | ok |
| 27 | The in-progress issue list | `CHAT-pphwooqm` | ok |
| 28 | Open: design items 5 and 7 | `Design items 5 (live follow) and 7 (agent sandbox)` | ok |
| 29 | Open: the native image | `The native one-shot image builds and its context does not start` | ok |
| 30 | Open: the shared vault | `did not verify the content of the shared vault` | ok |

The rows 21 to 30 come from the file's open items and its records. The
entries for the other five issues carry the newest state that the fp record
holds, as the first table recorded.

## The read as each agent

Measured on 2026-10-20. Each agent read the register once. The four outputs
are identical. The SHA-256 of each file is
`977c3edaab30564996f329c65c1a96f774ba35870dbde8f86e6a20cba39d195c`.

| Agent | Status | Records | Denied warnings | Duplicate warnings |
|-------|--------|---------|-----------------|--------------------|
| Sigma | 0 | 15 | 0 | 1 |
| Theta | 0 | 15 | 0 | 1 |
| Phi | 0 | 15 | 0 | 1 |
| Mario | 0 | 15 | 0 | 1 |

The 15 records are 8 pointers and 7 topic entries. The one warning is the
approved `duplicate-pointer` warning for `CHAT-live6`. No agent saw a
`denied-index` or `denied-topic` warning.

## The measurement

Measured on 2026-10-20. Five reads as `Theta`. The time covers the wrapper
process from its start to its exit. The exit follows the last output byte.

| Run | Status | Time | Records | Bytes |
|-----|--------|------|---------|-------|
| 1 | 0 | 5065 ms | 15 | 10454 |
| 2 | 0 | 5082 ms | 15 | 10454 |
| 3 | 0 | 5089 ms | 15 | 10454 |
| 4 | 0 | 5118 ms | 15 | 10454 |
| 5 | 0 | 5185 ms | 15 | 10454 |

**Median 5089 ms.** The data holds 7 topics, 8 pointers, 7 entries, and
15 messages. Standard error was empty in every run. See `live-measure.out`.

The median is under the 10-second target of owner decision 20. The stage gate
for stage 4 planning is met on this data.

## The time limit

Measured on 2026-10-21. Both runs used the default limit of 30000 ms and
started from the wrapper process. Standard output was empty in both runs, and
standard error held one line.

| Run | Stall | Status | Time | Standard error |
|-----|-------|--------|------|----------------|
| 1 | A stand-in shell that never answers | 124 | 30032 ms | `register: the command passed its 30000 ms limit` |
| 2 | A paused core | 124 | 29716 ms | `register: chat-shell: register-read passed its 30000 ms limit` |

Run 1 shows the wrapper watchdog. Run 2 shows the shell limit, which the
wrapper passes on. See `live-deadline-30s-stall.*` and `live-deadline-30s.*`.

The pause in run 2 also stopped the core. The core renews two leases every
few seconds, and `LeaseKeeper` closes the application context after 25
seconds without a renewal. `node-9/bin/start` restored the core, and a read
as `Theta` then returned status `0` with 15 records and one warning.

The earlier 1000 ms run of 2026-10-20 stays in `live-deadline.*`. The offline
suite covers the watchdog at other limits. It now also checks that a timeout
prints one line on standard error.

## The sandboxed read

Measured on 2026-10-21, for task 7 item 15. The profile denies an outbound
connection to each published host port except 6890 and 6891. It uses the
any-host form `remote ip "*:<port>"`, because a `localhost` rule does not
block a JVM. A Java probe proved the profile first: 5432, 6379, 8500, 8600,
9000, 9001, and 9042 were denied, and 6890 connected.

The read ran as `Theta` inside the same profile. It returned status `0` with
15 records, one `duplicate-pointer` warning, no denied warning, and empty
standard error. See `live-sandbox-probe.out`, `live-sandbox-read.*`, and
`live-sandbox.sb`. The script is `sandbox-read.sh`.

## Limits of this record

- The live index holds two test pointers for `CHAT-live6`, from the task 6
  proof. The store has no delete command for a message. So every read prints
  one `duplicate-pointer` warning, and the test topic
  `forward-register-livetest` stays in the read. The warning is approved
  behavior, and it is not a coverage failure.
- `forward-register-duplicate` does not exist as a topic. The read ignores
  that pointer, because a later pointer for one key is never read. A join
  of that name returns status `1`.
- The read data is 15 messages. The stage 4 archives are larger, and this
  record does not measure them.
- The `AGENTS.md` switch is not made. The file `forward-register.md` stays in
  place, and `AGENTS.md` still imports it.
- The coverage check proves that each fact is present. It does not prove that
  the entry holds no other false claim.
