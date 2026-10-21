# Continuity coverage of the forward register

Date: 2026-10-20. Issue: `CHAT-jmnjvupr`. Plan: stage 3, task 7, items 1 to
11. This record covers the continuity copy and the read measurement. The
`AGENTS.md` switch is not made.

The source is `forward-register.md` at commit `914d96ca`. Each row names one
source fact, the message that holds it, and a check string. The check string
is a copy of the text in the message. A reader can search the register for it.

## The messages

Every entry was posted as `Theta` with `node-9/bin/register post`. The
wrapper returned status `0` and one message id for each post.

| Issue | Topic | Pointer message | Entry message |
|-------|-------|-----------------|---------------|
| `CHAT-jmnjvupr` | `forward-register-topic-register` | `bd46df80-cd12-11f1-b7c1-47b553663159` | `ec7756c0-cd14-11f1-b7c1-47b553663159` |
| `CHAT-aqpcacwv` | `forward-register-durable-deployment` | `d296ce40-cd12-11f1-b7c1-47b553663159` | `dc3f5700-cd12-11f1-b7c1-47b553663159` |
| `CHAT-owiksxvz` | `forward-register-toolchain` | `e86e8aa0-cd12-11f1-b7c1-47b553663159` | `f2111ff0-cd12-11f1-b7c1-47b553663159` |
| `CHAT-ivtnvgyg` | `forward-register-keytests-codec` | `fe023920-cd12-11f1-b7c1-47b553663159` | `07d0e780-cd13-11f1-b7c1-47b553663159` |
| `CHAT-uxlstrdy` | `forward-register-message-capture` | `13c8de80-cd13-11f1-b7c1-47b553663159` | `1d5330e0-cd13-11f1-b7c1-47b553663159` |
| `CHAT-pphwooqm` | `forward-register-kafka-stage-2` | `29708a30-cd13-11f1-b7c1-47b553663159` | `336b9fc0-cd13-11f1-b7c1-47b553663159` |

The topic of `CHAT-jmnjvupr` holds four entries. The first is
`c661ce40-cd12-11f1-b7c1-47b553663159`. The next are
`a86c5260-cd13-11f1-b7c1-47b553663159` and
`72247d30-cd14-11f1-b7c1-47b553663159`. The newest is
`ec7756c0-cd14-11f1-b7c1-47b553663159`, and it replaces the three. The
newest entry stands alone, as the design requires. It holds the issue state
and the four facts without an fp ID: Checkout, merge strategy, open PR, and
branches.

Every agent joined every new topic. All four agents also joined
`forward-register-livetest`, because the index names it. Phi and Mario joined
it for the first time. Sigma and Theta joined it again, so they hold a second
membership row. Every join returned status `0`.

## The coverage table

The check ran with `node-9/bin/register read <topic> --as Theta`. It selected
the record with the message id, and it searched the `text` field for the
check string. It printed `ok` for each row. Result: **26 rows, 0 failures**.
See `live-coverage.out`.

| # | Source fact | Message | Check string | Result |
|---|-------------|---------|--------------|--------|
| 1 | Checkout | `ec7756c0-...` | `master at bb0796d3` | ok |
| 2 | Last merged PR | `ec7756c0-...` | `PR #262, stage 2` | ok |
| 3 | Merge strategy | `ec7756c0-...` | `Every merge needs Mario's explicit authorization` | ok |
| 4 | Open PRs | `ec7756c0-...` | `No PR is open` | ok |
| 5 | Branches and worktrees | `ec7756c0-...` | `chat-hcfgkbuz-node-9 is kept at 1ad85a5e` | ok |
| 6 | `CHAT-jmnjvupr` in-flight row | `ec7756c0-...` | `Stage 3 tasks 1 to 6 are committed` | ok |
| 7 | Next-session: task 7 and the switch | `ec7756c0-...` | `switch AGENTS.md after Mario reviews the switch` | ok |
| 8 | Next-session: decisions 1 to 38 | `ec7756c0-...` | `Design decisions 1 to 38` | ok |
| 9 | Next-session: live run safety | `ec7756c0-...` | `Node 9 runs on 6890 and 6891` | ok |
| 10 | Next-session: coverage rule | `ec7756c0-...` | `is a coverage failure` | ok |
| 11 | Measured in stage 2 | `ec7756c0-...` | `A post wrote a vector without a rebuild` | ok |
| 12 | Open: the move to another host | `ec7756c0-...` | `The move to another host` | ok |
| 13 | Open: design items 5 and 7 | `ec7756c0-...` | `Design items 5 and 7` | ok |
| 14 | Open: the native image | `ec7756c0-...` | `its context does not start` | ok |
| 15 | Open: case A11 | `ec7756c0-...` | `Case A11 is not run` | ok |
| 16 | Open: the owner vault is absent | `ec7756c0-...` | `owner.yml is absent` | ok |
| 17 | Open: the shared vault is unverified | `ec7756c0-...` | `shared vault content is unverified` | ok |
| 18 | Open: the second-denial branch | `ec7756c0-...` | `offline tests only` | ok |
| 19 | Landed: PR #262 | `ec7756c0-...` | `It delivered the node-9 launcher` | ok |
| 20 | The archive list | `ec7756c0-...` | `forward-register-archive-2026-10-19.md` | ok |
| 21 | The in-progress issue list | `ec7756c0-...` | `CHAT-pphwooqm` | ok |
| 22 | `CHAT-aqpcacwv` state | `dc3f5700-...` | `durable deployment ran on 2026-10-16` | ok |
| 23 | `CHAT-owiksxvz` state | `f2111ff0-...` | `Java 25, Kotlin 2.4.10` | ok |
| 24 | `CHAT-ivtnvgyg` state | `07d0e780-...` | `Jackson 3 codec error` | ok |
| 25 | `CHAT-uxlstrdy` state | `1d5330e0-...` | `2026-10-07-domain-command-bus-design.md` | ok |
| 26 | `CHAT-pphwooqm` state | `336b9fc0-...` | `Stage 1 merged in PR #201` | ok |

The rows 22 to 26 come from the issue list and from `fp context`. The source
file names those issues alone. The entries carry the newest state that the fp
record holds.

## The read as each agent

Each agent read the register once. The four outputs are identical. The SHA-256
of each file is `977c3edaab30564996f329c65c1a96f774ba35870dbde8f86e6a20cba39d195c`.

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

Five reads as `Theta`. The time covers the wrapper process from its start to
its exit. The exit follows the last output byte.

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

The default limit is 30000 ms. The live core answers in about 5 s, so a live
run cannot reach the default limit without a stall. The check used the
wrapper setting `CHAT_REGISTER_DEADLINE_MS=1000`.

Result: status `124` after 1027 ms, with empty standard output and one line on
standard error: `register: the command passed its 1000 ms limit`. See
`live-deadline.out` and `live-deadline.err`.

The 30-second default and its watchdog paths are covered by the offline suite
(`shell-scripts/test-node-9-register.sh`, 69 checks) and by the stage 1 live
cases A14 and A15.

## Limits of this record

- The live index holds two test pointers for `CHAT-live6`, from the task 6
  proof. The store has no delete command for a message. So every read prints
  one `duplicate-pointer` warning, and the test topic
  `forward-register-livetest` stays in the read. The warning is approved
  behavior, and it is not a coverage failure.
- `forward-register-duplicate` does not exist as a topic. The read ignores
  that pointer, because a later pointer for one fp ID is never read. A join
  of that name returns status `1`.
- The read data is 15 messages. The stage 4 archives are larger, and this
  record does not measure them.
- The `AGENTS.md` switch is not made. The file `forward-register.md` stays in
  place, and `AGENTS.md` still imports it.
