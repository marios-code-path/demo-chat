# Live acceptance of the topic register on node 9

Date: 2026-10-20. Issue: `CHAT-jmnjvupr`. Plan: stage 3, task 6.
Worktree: `chat-jmnjvupr-stage-3`.

The run used the disposable core of node 9 alone. It ran no `chat-deploy core`
command. Node 16 stayed untouched.

## Environment

- `node-9/bin/start` returned `0` at 2026-10-21T01:09Z. Health on 6891 reads
  `UP`. RSocket listens on 6890. See `start.log`.
- The core image `node-9/cassandra-core-service-rsocket:0.0.1` was present.
  The start ran no Maven.
- `just build-shell-cli` built the CLI classpath. It took 31 s.
- The key type is `uuid`. The Compose project is `chat-node-9`.
- `node-9/bin/setup-secrets --local` wrote the secrets. The Mario password is a
  generated test value for this core, by the owner's choice. It is not the
  owner vault value of decision 24.
- Every shell call logged in with `CHAT_SHELL_USER` and a password file.
  Mario and Admin used a copy in the run directory. Sigma, Theta, and Phi used
  `node-9/secrets/`.

## Results

| Check | Result | Evidence |
|-------|--------|----------|
| Node-16 containers before the run | 5 running. Recorded. | `node16-ps-before.txt` |
| Node-16 images before the run | 52 tags. Recorded. | `node16-images-before.txt` |
| Named test topic, reversible data | `live-check-6`, `forward-register-livetest`. Test text only. | this record |
| Mario creates `register-index` | Room `55db9c80-ccec-11f1-b7c1-47b553663159`, status `0` | `live-add-topic.out` |
| Stored owner is Mario | One `*` row. Principal `1aee9eb0-...` is handle `Mario`, name `MARIO OWNER`. | `owner-grant.txt` |
| Four users log in | `whoami` returns one distinct id for each user, status `0` | `live-users.log`, this record |
| Four users join and post | Topic `live-check-6` holds four records from four senders, status `0` each | `live-users.log` |
| Admin and Mario are distinct | Admin `1a9f6d40-...` reads `Admin \| SUPER USER`. Mario `1aee9eb0-...` reads `Mario \| MARIO OWNER`. | this record |
| `register open` | Status `0`, `state: created`, topic `forward-register-livetest`, pointer `01800a80-...` | `live-open.out` |
| `register post` | Status `0`, message `0e4e1630-...`, command id and request id present | `live-post.out` |
| `register read` with no topic | Status `0`, one record line per message, typed | `live-read-session.out` |
| `register read <topic>` | Status `0`, the newest entry of the topic | `live-read-topic.out` |
| Duplicate pointer warning | Status `0`. Warning keeps `01800a80-...` and ignores `2529d510-...`. | `live-read-duplicate.out` |
| Error statuses | `1` no pointer, `2` unknown user, `2` title delimiter, `2` empty entry. The `77` for Mario was measured before decision 37. | `live-errors.log` |
| Down node | Status `69`, one line `chat-shell: Connection refused` on port 6899. Node 9 stayed up. | `live-down.out` |
| Node-16 containers after the run | The same 5 keep the same images. | `node16-ps-after.txt` |
| Node-16 images after the run | No change. The full image list is identical. | `node16-images-after.txt` |

The `whoami` lines:

```
1abeb510-ccec-11f1-b7c1-47b553663159 : Sigma | SIGMA AGENT | chatimg://sigma.png
1acee1b0-ccec-11f1-b7c1-47b553663159 : Theta | THETA AGENT | chatimg://theta.png
1ade9920-ccec-11f1-b7c1-47b553663159 : Phi | PHI AGENT | chatimg://phi.png
1aee9eb0-ccec-11f1-b7c1-47b553663159 : Mario | MARIO OWNER | chatimg://mario.png
1a9f6d40-ccec-11f1-b7c1-47b553663159 : Admin | SUPER USER | chatimg://plaid.png
```

## Finding 1: a new agent cannot read the register

This finding was fixed on 2026-10-20, as owner decision 36.

A caller must be a member of `register-index` before the first read. A caller
must also be a member of every topic that the index names. `register-read`
reads the index and then the newest entry of each named topic. It prints
nothing until every read succeeds, as the contract stated then. A denied topic
failed the whole command.

Measured:

1. `register open` as Sigma, before any join, returned `1` with
   `register: chat-shell: Access Denied`.
2. `register read` as Theta, before any join, returned `1` with the same line.
   See `live-read-theta-before-join.out`.
3. Theta joined `register-index`. `register read` still returned `1`, because
   the pointer names `forward-register-livetest` and Theta was not a member.
4. Theta joined `forward-register-livetest`. `register read` returned `0` and
   printed the index, the newest entry, and the duplicate warning.
   See `live-read-theta2.out`.

The result blocked the session-start step of task 7. That step replaces the
`AGENTS.md` import with `node-9/bin/register read --as <name>`.

**A warning is not a complete read.** A `denied-index` or `denied-topic`
warning means that the agent did not receive the whole register. The task 7
measurement treats either warning as a coverage failure. Status `0` alone is
not proof of a complete register.

**The owner chose option 1.** `register-read` now reports a denied topic as a
warning line and keeps every readable topic. A denied index gives one warning
line and no records. Any other failure still ends the read with no output.

The server answers `AccessDeniedException`. A client decodes the same refusal
as `CoreAuthorizationRefusal`. The read treats both as a denial.

The warning line for a denied topic holds `type`, `kind`, `topic`, and
`reason`:

```
{"type":"warning","kind":"denied-topic","topic":"forward-register-livetest","reason":"Access Denied"}
```

Measured after the fix:

1. `register read` as Phi, with no membership, returned `0` and one
   `denied-index` line. See `live-read-phi-denied-index.out`.
2. Phi joined `register-index`. The read returned `0` with the two pointer
   records, the duplicate warning, and one `denied-topic` line for
   `forward-register-livetest`. See `live-read-phi-denied-topic.out`.
3. `register read` as Sigma, a member of both topics, still returned the entry
   record. See `live-read-sigma-after.out`.

Tests: `PubSubRegisterReadTests` holds 16 cases. The two new cases cover a
denied lookup and a denied read, and together they cover both denial types.
The suite of `chat-shell` ran 179 tests with 0 failures and 1 skip.

## Finding 2: the owner login in the wrapper

The wrapper refused `--as Mario` with status `77`, so the live Mario checks
used `chat-shell-cli` directly. Owner decision 37 settles this. The wrapper
now reads `CHAT_USER_MARIO_PASSWORD` in `node-9/secrets.env`, writes the value
to a private file for the shell call, and removes that file when the command
ends. No `Mario.password` client file exists. A missing file or value gives
`77`.

Measured after the fix: `node-9/bin/register read --as Mario` returned `0`,
printed the two pointer records and the duplicate warning, and printed one
`denied-topic` warning for `forward-register-livetest`, which Mario has not
joined. See `live-read-mario.out`.

Offline tests: 3 cases. One covers the rendered value and its mode `600`. Two
cover a missing value and a missing file, each with status `77` and no shell
call. The wrapper suite ran 64 checks with 0 failures.

## Finding 3: a denied index read as an empty index

Found in the stage 3 hand-off on 2026-10-20. Fixed the same day as owner
decision 38.

Decision 36 makes `register-read` return status `0` and one warning line for a
denied index. `register open` read that output as an empty index. So it could
create a topic and post a second pointer for an fp ID that the index already
held. `register post` read the same output as "no pointer".

The fix: `open` and `post` join `register-index` once after a `denied-index`
warning, then read once more. A second denial is status `1` with one line and
no output. A readable index joins nothing.

Measured live on 2026-10-20, on the same core:

| Step | Result | Evidence |
|------|--------|----------|
| `register read --as Phi`, before the change | Status `0`. Two pointer records, the duplicate warning, and one `denied-topic` line. | `live-guard-1-before.out` |
| Phi leaves `register-index` | `Left register-index`, status `0` | this record |
| `register read --as Phi`, before a join | Status `0`, one `denied-index` line, no record | `live-guard-2-denied-index.out` |
| `register post CHAT-zzzguard --as Phi` | Status `1`, empty standard output, one line: `register: no pointer for CHAT-zzzguard. Run register open first.` | `live-guard-3-post-denied.out`, `live-guard-3-post-denied.err` |
| `register read --as Phi`, after the command | Status `0`, the pointer records again. The automatic join restored the membership. | `live-guard-4-after-join.out` |

The post stopped after the second read found no pointer. It posted no entry.

Offline: `bash shell-scripts/test-node-9-register.sh` reports 69 checks and 0
failures. With the guard disabled, the same suite reports 5 failures. `open`
reached the topic create and the pointer post.

Limits of this check:

- The second-denial branch is offline only. A live second denial needs a
  member that the server refuses after a join. No such state exists on this
  core.
- `open` uses the same index read as `post`. `open` was not run live, because
  a live create adds a pointer to the real index.
- The Phi leave and join cycle removed one membership row and added another.
  It also wrote one leave alert and one join alert. Phi holds one membership
  row before and after, so the access state is the same.

## The label model, decision 35

The owner accepted the label model on 2026-10-20. This run keeps it.

- The rendered passwords stay in `node-9/secrets.env` with mode `600`.
- An agent on the shared account can read the Mario password there.
- There is no operating system account for each agent. Decision 19 holds.
- The run read the Mario password from the rendered file. That file held a
  generated test value, not the owner vault value.

## Limits of this run

- The Mario checks that ran before decision 37 used `shell-scripts/chat-shell-cli`
  directly. The wrapper now accepts `--as Mario`.
- The wrapper fixes `CORE_PORT` to 6890. The down-node check used the shell
  client on a closed port. The wrapper path for a failed shell call is not run
  live. The offline suite covers that path.
- The live core holds test data only. The topics `live-check-6`,
  `register-index`, and `forward-register-livetest` stay in place.
