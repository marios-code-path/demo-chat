# The topic register on node 9

Status: draft for review by Sigma and the owner. Issue: `CHAT-jmnjvupr`. Date: 2026-10-17.

Superseded: the register shape in this file is history. The current design is
`docs/superpowers/specs/2026-10-24-project-register-design.md`. That design
gives each project one register, and its name prefixes every topic. The node-9
sections here still describe the host.

## Intent

The forward register moves out of markdown files and into demo-chat itself.
Each fp issue gets a chat topic. Each topic holds hand-off entries. One index
topic points at every register topic. Agents read and write the register
through `chat-shell`. The owner can open a shell and follow an agent while it
writes.

The owner wants three results:

1. A register that the project's own product holds, not a file in the repo.
2. A hand-off that a new agent or a person can continue without the earlier
   conversation.
3. A register that moves with its data to another host.

## Why

`forward-register.md` loads in every session through `AGENTS.md`. It grew to
29 KB and went 26 PRs out of date. On 2026-10-17 it still called merged work
"not merged". PR #256 cut it to a 24-hour tip. This design replaces the file.

## Goals

- One topic per fp issue. Branches and PRs do not change that mapping.
- One index topic, `register-index`, that points at every register topic.
- Hand-off-ready entries in plain text.
- A session-start command whose output an agent treats as part of `AGENTS.md`.
- A separate user for each agent, so a reader sees who wrote each entry.
- A new core, node 9, that holds the register alone and moves to another host.
- The import of `forward-register.md` and its four archives as topics, and
  then the removal of those files.

## Non-goals

- No MCP access in the first version. The MCP server cannot create a topic.
  It reads only topic IDs in its config file, and it needs a REST backend.
- No JSON entry format in the first version. Plain text is the baseline.
- No change to fp. fp comments stay the running log of each issue.
- No change to node 16.

## Anti-goals

These results would make the work fail, even if every part runs:

- **Any effect on node 16.** A build, start, or stop of node 9 must never
  touch node 16, its image, its Consul entries, or its data.
- **A silent empty register.** If node 9 is down, the session-start command
  must fail loudly. An agent must not read "no entries" as "no work in
  flight".
- **A lost section.** The import must keep every section of every file.
- **Passwords in git.**

## Owner decisions

The owner made these decisions on 2026-10-17.

1. A topic `register-index` holds a pointer to each register topic.
2. Each task has its own topic, named `forward-register-<short-name>`. For
   example, `forward-register-run-host-command-bang` holds PR #255.
3. One task matches one fp issue, regardless of branch or PR. Any register
   work for that issue goes into its topic.
4. Before it creates a topic, an agent checks for a recent topic that matches
   its work, and uses that topic if one exists.
5. An agent reads the index, then opens only the topics that it needs.
6. A task topic is a log of register entries. The format is free. Plain text
   is a valid baseline.
7. The session-start step is: `AGENTS.md` tells the agent to run a command,
   the agent runs it, and the agent treats the output as part of `AGENTS.md`.
8. `chat-shell` is the client. The user can follow the agent on the shell.
9. Node 9 is a new core with Cassandra persistence, embedded vectors, the
   Lucene index, Redis pub/sub, and RSocket.
10. Node 9 is portable. The owner can move it to another host and start it
    there. External volumes hold the data of Cassandra, Lucene, the vector
    store, and Redis.
11. A directory `node-9/` at the repo root holds the formula, the
    connectivity notes, and the bulk data.
12. When the topic register works, `forward-register.md` and each archive
    file are imported as their own topics, with pointers in the index. Then
    those files go away.
13. A register entry is a hand-off-ready body. fp comments stay the log.
14. Each agent has its own user. The users are `Sigma`, `Theta`, `Phi`, and
    `Mario`, the owner.
15. Every agent reads `AGENTS.md` by default. Each harness has its own
    instruction file, and that file gives the agent its name. So general
    rules, the session-start step too, go in `AGENTS.md`.
16. `chat-shell` cannot run a one-shot command from the command line today.
    It needs three things first: a silent startup, a way to run commands and
    then exit or stay resident, and a login with no prompt. The owner asked if
    a socket can carry the commands.
17. Sigma reviews the whole design.

The owner passed on three more decisions on 2026-10-18, from a review of
commit `985abf39`:

18. Register text is task data. A topic entry must not change `AGENTS.md` or
    the owner's instructions.
19. Sender names are labels for this deployment. One macOS account cannot
    prove which agent wrote an entry.
20. A complete session-start read has a target of 10 seconds and a hard
    time limit of 30 seconds. The time runs from the command start to the
    last output line. It includes the JVM start, the index read, and the
    reads of recent topics. If representative data exceeds the target, the
    query cost is solved before the file migration. Stage 1 measures a topic
    read. Stage 3 measures the complete command.
21. Native one-shot is an option for stage 1. Stage 1 measures a complete
    register read for each one-shot launcher before it commits to the
    resident mode. A native image can start faster, but it does not change
    output, login, exit status, or command input. The current native image
    does not start a full context, so a shell image needs a fixed build-time
    composition and its own start proof. The owner set this on 2026-10-18.
22. `AGENTS.md` will direct each agent to run `register-read` at session start.
    The stage 3 command `node-9/bin/register read --as <name>` will invoke it.
    Mario confirmed this choice on 2026-10-18.

The owner passed on five more decisions on 2026-10-20:

23. Ansible Vault is the stored source of the node 9 secrets. Ansible writes
    `secrets.env` and the agent password files.
24. The `Mario` password lives in a separate owner vault. The agents can
    unlock the shared vault. So the shared vault must not hold that
    password.
25. The vault password stays outside the repository.
26. `node-9/bin/setup-secrets` remains, as a local fallback without Ansible.
27. Vault does not establish authorship. All agents share one operating
    system account.

The owner confirmed four command decisions on 2026-10-20:

28. `register post` preserves the input bytes. It does not check the entry
    template. Empty or whitespace-only input is status `2`.
29. A duplicate pointer warning is one typed JSON line on standard output.
    Every line of a read carries `type`. Standard error stays empty on
    status `0`.
30. `--as <name>` is required. A missing flag or an unknown name is status
    `2`. No fallback exists.
31. `register open -` and `register post -` are status `2`. Only the stage 4
    import creates archive topics.

The owner made two more decisions on 2026-10-20:

32. An owner run prompts for the owner vault password each time. No stored
    owner vault password exists.
33. Stage 3 live acceptance runs on the current host. The move to another
    host stays unverified.
34. The local mode of `setup-secrets` runs only with an explicit `--local`
    flag. A missing Ansible executable or vault password stops the default
    run. This amends decision 26.
35. The owner accepts the label model. Agents on the shared account can read
    the rendered `Mario` password in `node-9/secrets.env`. The owner vault
    protects the stored copy alone.
36. `register-read` reports a denied topic as a warning line, and it keeps
    every readable topic. The owner chose this on 2026-10-20, after the live
    run of task 6. An agent is not a member of every topic that the index
    names, so a read that fails as a whole leaves no session-start read.
37. The owner login of the wrapper reads the rendered
    `CHAT_USER_MARIO_PASSWORD` in `node-9/secrets.env`. No `Mario.password`
    file exists. This uses the label model of decision 35, and it settles
    the open owner decision of "Users and passwords". A missing file or value
    gives status `77`.
38. A denied index stops `register open` and `register post`. On a
    `denied-index` warning, the wrapper joins `register-index` once and reads
    once more. A second denial is status `1` with one line and no output. The
    command creates no topic and posts no entry. A readable index joins
    nothing, so a member's command adds no membership row. The owner chose
    this on 2026-10-20, after the stage 3 hand-off found that `open` read a
    denied index as an empty index. `register-read` keeps the rule of
    decision 36.
39. A pointer names a key, not an fp issue. A key is opaque text that names
    one topic. `open` and `post` accept any key that can stand in a pointer
    line: 1 to 64 characters, no `|`, no backslash, no control character,
    and no space at either end. The bash tool reads pointers through a
    tab-separated form, and that form doubles a backslash. A doubled key
    cannot match a written one, so both decoders refuse it. The key `-` is
    reserved for archive pointers. The commands
    check no `CHAT-` shape. The JSON field is `key`. This project passes an
    fp issue ID as the key. That value is a project convention, and the tool
    does not know it. One split rule serves both decoders, and
    `chat-shell/src/test/resources/register/pointer-lines.tsv` holds its
    cases. The owner chose this on 2026-10-21, so that another project can
    use the register.

## Node 9

### Composition

Node 9 runs the same core as the `deploy-core-cassandra-lucene-redis` recipe
with vectors. A dry run of that plan with `--node-id 9 --key-type uuid
--vector embedded --embedding openai` gives these selectors:

- `app.service.core.key=cassandra`, `persistence=cassandra`,
  `secrets=cassandra`, `index=lucene`, `pubsub=redis-pubsub`
- `app.service.core.vector=embedded`, `app.service.core.embedding=openai`
- `app.key.type=uuid`, `app.nodeid=9`, RSocket on 6790, management on 6791
  inside the container

Embeddings use the endpoint of node 16: `node2.lan:6090`, model
`nomic-embed-text`, 768 dimensions. This is the designer's call.

### Isolation from node 16

The recon of 2026-10-17 found five shared names. Each one would let node 9
change node 16. Node 9 must avoid all five.

1. **The Compose project.** `shell-scripts/chat-deploy` starts every core as
   project `chat-deploy-core`, and `chat-deploy core --stop` stops that
   project. Node 16 runs as `chat-deploy-core-core-service-rsocket-1`. So node
   9 uses its own Compose file in `node-9/` and its own project name,
   `chat-node-9`. Node 9 must not start through `chat-deploy core`.
2. **The image tag.** The core image bakes the launch options, the node id
   too, into the image. The live image `cassandra-core-service-rsocket:0.0.1`
   carries `app.nodeid=16`. A node-9 build under that tag would replace the
   node-16 image. So node 9 uses its own image name, such as
   `node-9-core-service-rsocket:0.0.1`.
3. **Consul.** The core publishes root keys to Consul KV under
   `/chat/rootkeys`, and it registers as `core-service-rsocket`. The register
   claim 1 says that a constant instance ID makes a second process overwrite
   the first. So node 9 runs its own Consul container.
4. **Cassandra.** PR #244 allows one current lease holder per store service
   on a shared backend. Node 9 on the node-16 keyspace would conflict. So
   node 9 runs its own Cassandra container.
5. **Redis.** Node 9 runs its own Redis container. Its streams stay apart
   from node 16, and the move to another host carries them.

### Ports and network

- Node 9 publishes two host ports on `127.0.0.1` alone: RSocket on 6890 and
  management on 6891. Both were free on 2026-10-17.
- Cassandra, Redis, and Consul publish no host port. They talk to the core on
  a Compose network of node 9 alone.
- The host must reach `node2.lan:6090` for embeddings.

### The `node-9/` directory

```
node-9/
  README.md         connectivity notes, start, stop, move
  compose.yaml      core, cassandra, redis, consul, volume init
  node-9.env        launch values with no secret
  userinit.yml      the register users, passwords from the environment
  secrets.env       core passwords and the embedding key, ignored by git
  secrets/          one password file per agent, ignored by git
  schema/           keyspace-uuid.cql, copied from shared-resources-cassandra
  bin/              start, stop, save-images, load-images, register
  run/              resident shell sockets, mode 0700, ignored by git
  data/             ignored by git
    cassandra/  lucene/  vector/  redis/  consul/
  images/           saved image tar files, ignored by git
```

Every mount uses a path relative to `node-9/`. No mount names a directory of
this host.

### The move to another host

1. Stop node 9 with `node-9/bin/stop`. Cassandra must stop cleanly before a
   copy.
2. Run `node-9/bin/save-images`. It saves the core, Cassandra, Redis, and
   Consul images to `node-9/images/`.
3. Copy `node-9/` to the other host.
4. Run `node-9/bin/load-images`, then `node-9/bin/start`.

The core image is built on this host and is in no registry, so step 2 is
needed. The other host needs Docker and a route to `node2.lan:6090`.

### Users and passwords

`node-9/userinit.yml` adds four users to the shipped users: `Sigma`, `Theta`,
`Phi`, and `Mario`. Each is a plain user, not `Admin`. Each password comes
from an environment value at the core start, in the same way the shipped
`Service` user reads `CHAT_SERVICE_PASSWORD`. The `Admin` password also comes
from the environment, because the shipped value is `changeme`.

Ansible Vault stores the secrets of a vault-backed node 9. Vault encrypts
the stored copy alone. The files that Ansible writes are plaintext.

Amendment 2026-10-25 (CHAT-cnwkjsrp). The vaults are per host. The current
Git tree tracks no ciphertext. `node-9/install` requires exactly one secrets
choice: `--vault-source DIR`, `--import-state DIR`, `--local-secrets`, or
`--keep-secrets`. A vault-source bundle holds `vault-pass`, `shared.yml`,
and `owner.yml`. The installer copies `shared.yml` and `owner.yml` to
`<parent of prefix>/vault/`, links them into the installed `ansible/vault/`,
and records `plaintext: pending`. The marker is a code-install record, not
a readiness signal. The earlier tracked `shared.yml` remains in Git history
and is not a current source.

- `shared.yml` holds the service password, the `Admin` password, the
  embedding key, and the three agent passwords. It is encrypted at rest.
  It lives at `<parent of prefix>/vault/shared.yml` on each host.
- `owner.yml` holds the `Mario` password alone. It has its own vault ID and
  its own password. It lives beside `shared.yml`.
- The shared vault password stays outside the repository, at
  `${XDG_CONFIG_HOME:-$HOME/.config}/chat-node-9/vault-pass`, with mode
  `0600`.
- The owner run prompts for the owner vault password. Ansible never stores
  that password.
- `node-9/ansible/secrets-shared.yml` writes the shared values.
  `node-9/ansible/secrets-owner.yml` writes the owner values.
- `node-9/bin/setup-secrets` runs the shared playbook. It runs the owner
  playbook only with `--owner`, from the owner's terminal.
- `node-9/bin/setup-secrets --local` generates random values on an explicit
  request alone. It prompts once for the `Mario` password. The default run
  never falls back to it.
- The default run stops when `ansible-playbook`, the shared vault, or the
  shared vault password is absent. A missing vault names `--vault-source`
  and the runbook. The run never selects `--local` on its own.
- `node-9/secrets.env` holds the values that the core needs:
  `CHAT_SERVICE_PASSWORD`, `CHAT_ADMIN_PASSWORD`,
  `CHAT_EMBEDDING_OPENAI_API_KEY`, and one `CHAT_USER_<NAME>_PASSWORD` for
  each user. Git ignores it.
- `node-9/secrets/<user>.password` holds one agent password per file, with
  mode `0600`, for `Sigma`, `Theta`, and `Phi`.
- Ansible writes each secret file with mode `0600`, and `node-9/secrets/`
  with mode `0700`. It refuses a symbolic link at each secret path.
- Every task that holds a secret sets `no_log: true`.
- A write is create-only for each key and each file. An existing value stays
  byte for byte.
- `setup-secrets` does not create a second source of any value. Vault values
  and generated values never mix in one file.
- The core start fails when `CHAT_USER_MARIO_PASSWORD` is absent. A core
  without `Mario` is not a valid node 9.

**Owner decision: the `Mario` client password.** The start rule requires
`CHAT_USER_MARIO_PASSWORD`, and Compose reads it from `secrets.env`. That
file is plaintext on the shared account. So an agent can read the rendered
value. The owner vault protects the stored copy alone. The owner accepted
this limit on 2026-10-20, as decision 35.

The options are:

1. **Accept the label model.** The render writes the value to `secrets.env`.
   No second copy exists. Authorship stays a label, per decision 19.
2. **Seed the user once.** Withdrawn as a stage 3 test. Compose refuses the
   start before the core reads the key (`node-9/compose.yaml:62`). The
   initializer also writes the configured users during initialization
   (`chat-deploy/src/main/kotlin/com/demo/chat/service/init/InitialUsersService.kt:46`).
   So a start without the key tests the guard alone. It cannot show whether a
   saved login survives.
3. **One operating system account per agent.** Out of scope per decision
   19.

Option 1 is chosen, as decision 35. Sigma recommended it on 2026-10-20.
The owner accepted the limit on the same day. Task 6 and live acceptance are
no longer blocked on this point. Task 6 still needs Mario to create
`register-index` once.

The "Authorship" section below states what these files protect.

The shipped roles let a plain user create a topic (`NEW` on `MessageTopic`)
and join any topic (`JOIN` on `MessageTopic`). A join grants `SEND`,
`SUBSCRIBE`, and `FORWARD` on that topic. `messages` checks `SUBSCRIBE`. So an
agent joins a topic, and then it can read and post.

## The register

### Topics

- **`register-index`** holds one pointer per register topic. Its owner is
  `Mario`.
- **A task topic** is `forward-register-<short-name>`. The agent that starts
  the task creates it and owns it.
- **An archive topic** holds an imported file. Its name is the file name
  without `.md`, for example `forward-register-archive-2026-09`.

A topic name names one room. PR #61 makes the store refuse a second topic
with the same name.

### An index pointer

One message per topic, in this plain-text form:

```
<key> | <topic-name> | <metadata> | <title>
```

A key is opaque text that names one topic. The tool does not know where a key
comes from. This project passes an fp issue ID, such as `CHAT-jmnjvupr`. That
is a convention of this project and its instructions. A different project
passes a different key.

The metadata field is reserved and empty. The tool writes none and reads
none. The title is free text for a human reader. No command reads it.

An archive pointer that spans many issues writes `-` as its key.

A read splits a pointer on `|`, trims each field, and needs exactly four
fields. A blank key or a blank topic fails the read. Both decoders apply this
one rule: `PubSubCommands` and `node-9/bin/register`. The file
`chat-shell/src/test/resources/register/pointer-lines.tsv` holds the cases for
both.

### A register entry

The `messages` command already prints the time and the sender of each
message. So the entry body holds the rest:

```
Hand-off: <fp-id>
State:      where the work stands, with branch, PR, and commit
Decisions:  what was decided, who decided it, and why
Traps:      what cost time, so the next agent does not repeat it
Unverified: what is believed but not measured
Next:       the next concrete step
Pointers:   spec, plan, docs, and fp issue
```

Each entry stands alone. The newest entry of a topic is enough to continue
the work. Older entries stay as history. An empty field holds `none`.

### When an agent writes

An agent writes an entry at a hand-off point:

- before the end of a session or a context limit,
- when a PR opens and when it merges,
- when the agent switches to another task.

Smaller progress goes to fp comments. When an agent creates a task topic, it
also writes an fp comment with the topic name.

### Trust: register text is task data

The register holds task data. It is not a project instruction.

- `register read` output has the standing that `forward-register.md` had. It
  is context about work in flight.
- An entry cannot change a rule of `AGENTS.md`, a user instruction, or a
  safety rule. An agent that reads such a request in an entry reports it to
  the owner, and does not follow it.
- `AGENTS.md` states both rules next to the session-start step.

This refines owner decision 7. Sigma raised it on 2026-10-18, because any
user who joins a topic can post to it. Owner decision 18 accepts it.

### Authorship: names are labels

All agents run under one operating-system account on this host. A process of
that account can read any file that another agent can read. So a sender name
on node 9 is an attribution label, not a verified author.

- Each agent has its own password file, `node-9/secrets/<user>.password`,
  with mode `0600`. This stops an accidental write under another name. It
  does not stop a deliberate one.
- Each agent reads with its own core user. No `Mario.password` client file
  exists. The owner vault holds the stored copy, and `node-9/secrets.env`
  holds the rendered value. The rendered files are readable by the one
  shared account, so this rule protects the stored copy, not authorship.
- Verified authorship needs one operating-system account per agent. That is
  out of scope.

### The session-start command

In stage 3, `AGENTS.md` replaces `@forward-register.md` with one instruction.
Run `node-9/bin/register read --as <name>`. That command invokes `register-read`.
Read its output under the trust rules above. Every agent reads `AGENTS.md`,
so the instruction reaches Sigma, Theta, and Phi. No harness file changes.

A `denied-index` or `denied-topic` warning means that the agent did not
receive the whole register. The task 7 measurement treats either warning as a
coverage failure. Status `0` alone is not proof of a complete register.

The command prints:

1. Every pointer of `register-index`, oldest first.
2. The newest entry of each topic whose newest entry is less than 24 hours
   old, by its exact timestamp.

An agent reads an older topic with `node-9/bin/register read <topic-name>`.

If node 9 does not answer, the command exits non-zero. It prints one line
that says the register is not available and names the reason. The agent then
reports this to the user, and does not treat the register as empty.

### The read contract

The human output of `messages` cannot carry the register read. It prints
local time to the minute and indents the lines of a multi-line body
(`PubSubCommands.kt`, `messages`). Sigma found this on 2026-10-18.

Stage 1 adds a record output for machines, and keeps the human output for
the interactive shell:

- `messages <topic> --format records` prints one JSON object per line, with
  `id`, `topic`, `sender`, `timestamp`, and `text`.
- `timestamp` is an ISO-8601 instant in UTC, with the full stored precision.
- `text` holds the body unchanged. JSON escapes its newlines.
- `--since <instant>` keeps the messages at or after that instant.
- `--limit N` keeps the newest N messages after the `--since` filter.

**The register read is typed JSON in both forms.** `register read --as
<name>` and `register read <topic> --as <name>` each write one JSON object
per line. The shapes are fixed:

```
{"type":"record","id":"...","topic":"...","sender":"...","timestamp":"...","text":"..."}
{"type":"warning","kind":"duplicate-pointer","key":"...","kept":{"topic":"...","id":"..."},"ignored":{"topic":"...","id":"..."}}
```

No line leaves out `type`. The wrapper owns this shape. The output of
`messages --format records` stays as it is.

**The bound is in the shell, not in the store.** `messages` loads the whole
history of a topic, then applies `--limit`. The UUID message key gives no
time order in the store, so a store-side "newest N" needs a new time-ordered
query. Register topics are small, so the shell bound comes first. This
departs from Sigma's request for a bounded query.

Owner decision 20 sets the limit. Stage 1 measures one topic read. Stage 3
measures the complete `register read`, from the command start to the last
output line. If representative data exceeds 10 seconds, the query cost is
solved before stage 4 starts. A store-side bound is one possible solution.
At 30 seconds, `register read` stops and exits with status 124. The limit
covers the whole wrapper command. It starts at the wrapper process, and it
ends after the last output byte. The shell timer alone is not the limit,
because JSON conversion and final output follow it.

### The write command

`node-9/bin/register` also holds the write steps, so that every agent writes
the same way:

- `register open <key> <short-name> <title>` finds the pointer for the key,
  or creates the topic and adds its pointer.
- `register post <key> [--requestId <id>]` reads an entry from standard
  input and posts it to the topic of that key.

Each command names its user with `--as <name>`. An agent uses the name that
its harness instruction file gives it: `Sigma`, `Theta`, or `Phi`. `AGENTS.md`
tells each agent to do this. The owner uses `Mario`. The command reads the
password file of that user. It never prompts. A missing or unreadable file
is a login failure with status `77`. For `Mario`, the source is the rendered
`CHAT_USER_MARIO_PASSWORD` in `node-9/secrets.env`, per decision 37. The
wrapper writes it to a private file for the shell call and removes that file
when the command ends.

A denied index stops both write commands. The read of a denied index holds no
pointer, so the command cannot answer "does this key exist". The wrapper
joins `register-index` once after a `denied-index` warning, and it reads once
more. A second denial is status `1` with one line, and the command creates no
topic and posts no entry. A readable index joins nothing. See decision 38.

### Concurrent writes

- **Two `register open` calls for one key.** The topic create refuses an
  existing name with `DuplicateException` (PR #61). The second call then
  reads the index. If the earliest pointer for its key names that topic, it
  uses the topic. If the name belongs to another key, it fails and names
  that key.
- **Two pointers for one key.** The earliest pointer is the valid one, by
  stored time and then by message id. After `register open` posts a pointer,
  it reads the index again. If an earlier pointer for the same key names
  another topic, it uses that topic and says so. `register read` ignores a
  later duplicate pointer and prints a warning line for it. An archive
  pointer uses `-` as its key, so it is never a duplicate.
- **A post is done when a read shows it.** `register post` takes the message
  ID from the `send` result. It then reads the topic until that ID appears,
  for at most 10 seconds. If the ID does not appear, it exits with status 75
  and prints the message ID and the command ID.

## chat-shell as a command tool

The register needs these changes in `chat-shell` before node 9 is useful.
The owner named parts 1 to 3 on 2026-10-17. Sigma's review of 2026-10-18
added the output path and the protocol details. This is build stage 1.

### 1. A silent startup

- Standard output holds command output alone.
- No banner prints. `chat-shell` ships `banner.txt` today.
- Logs go to a file, or to standard error at `WARN`. A successful run writes
  nothing to standard error.
- JLine must not print a terminal warning when no TTY exists.

### 2. One output path and one exit status

Today several commands write to `System.out` with `println`. These include
`send`, `command-status`, `listen`, and a failed `login`. A writer passed to
a command does not receive that text. The tests of PR #255 run commands with
a writer, but they do not prove that all output reaches it.

- Every command that one-shot or resident mode can run writes through the
  writer of its `CommandContext`.
- A test runs each such command with a captured `System.out`. Any text on
  `System.out` fails the test.
- `listen`, `hangup`, and `script` stream or hold state across commands. Both
  modes refuse them with status 2.
- One-shot mode also refuses a first argument that starts with `@`. Spring
  Shell reads such an argument as a script file. A script file is several
  commands, and the table gives one run one status. PR #255 also found that
  `FileInputProvider` changes script lines. The refusal has status 2.

One exit status table serves the one-shot process, the resident response, and
the register client:

| Status | Meaning |
|--------|---------|
| 0 | The command succeeded. |
| 1 | The command ran and failed. |
| 2 | The command line is not valid, or the mode refuses the command. |
| 69 | The core, the socket, or node 9 is not available. |
| 75 | A post was sent, but no read showed it in time. |
| 76 | A resident request or response broke the protocol. |
| 77 | The login failed. |
| 124 | The command passed its time limit. |

### 3. Command execution

Both modes use one execution path. It parses one command line, runs it
through the command registry, writes the output to the context writer, and
returns a status from the table above.

**One-shot mode.** Spring Shell 4.0.3 has `NonInteractiveShellRunner`. When
`spring.shell.interactive.enabled=false`, `JLineShellAutoConfiguration`
supplies it. It joins the program arguments into one command line and runs
it. A first argument `@<file>` runs each line of that file, and part 2
refuses that form. A failed command throws `CommandExecutionException` with
an exit code. Stage 0 confirmed the runner with `chat-shell`, and it found
that the exit status is 0 or 1 alone ("Measured in stage 0", item 1). These
parts are missing:

- a launch path. `chat-build shell` sets the interactive mode to `true`, and
  it starts the shell through Maven.
- a process exit after the command, with the status of the table.
- the silent startup of part 1.

Each one-shot call pays one JVM start and one connection to the core.

**Resident mode.** The shell starts once, logs in, and stays connected to
the core. It listens on a Unix domain socket. Java supports Unix domain
sockets in `SocketChannel` since Java 16.

- **Path.** `CHAT_SHELL_SOCKET` names the socket file. No default exists, so
  stage 1 does not depend on `node-9/`. Node 9 sets
  `node-9/run/<user>.sock`.
- **Directory.** The parent directory must belong to the current user, with
  mode `0700`. Otherwise the shell refuses to start. The socket file has mode
  `0600`.
- **A stale socket.** At start, if the path exists, the shell connects to it.
  A refused connection means a stale file, and the shell removes it. An
  accepted connection means another resident shell holds it, and the shell
  exits with status 69. A path that is not a socket stops the start.
- **Request framing.** One request per connection. A request is one header
  line, `chat-shell/1 <command-bytes> <input-bytes>`, then the command line,
  then the input. Both lengths count UTF-8 bytes.
- **Response framing.** The shell runs the command and keeps its output. It
  then sends one header line, `chat-shell/1 <status> <output-bytes>`, and the
  output. Command output cannot look like the status, because the status
  comes first with a byte count.
- **Time limits.** A request must arrive within 10 seconds of the
  connection. A command has 120 seconds by default, set by
  `CHAT_SHELL_COMMAND_TIMEOUT`. At the limit, the shell answers status 124,
  removes the socket file, and stops. A Java thread can continue its core
  request after a cancel. So the shell never starts a second command after a
  timeout, and two commands never overlap under one login. Sigma found this
  on 2026-10-18.
- **Order.** The shell runs one request at a time, because all requests
  share one login. Other connections wait.
- **Client.** A small client sends the request, reads the response, prints
  the output, and exits with the status. A missing socket or a refused
  connection gives status 69. A short or bad header gives status 76.

**An entry travels as command input, not as an argument.** A new shell
command, such as `post --topic <name>`, reads the entry text from its input.
In one-shot mode, the input is the process standard input. In resident mode,
it is the input part of the request. So the newlines of an entry need no
quotes.

### 4. A login with no prompt

- The shell logs in at start, before the first command.
- `CHAT_SHELL_USER` names the user. `CHAT_SHELL_PASSWORD_FILE` names a file
  that holds the password.
- A password never goes in a program argument, because `ps` shows arguments.
- A failed login prints one line to standard error. The shell runs no
  command, and it exits with status 77.
- Without these variables, the shell starts and logs in as it does today.
- In resident mode, one process holds one user.

### 5. The core address

No new setting is needed. `config/client-rsocket-local.yml` reads
`CORE_HOST` and `CORE_PORT` for every RSocket destination. `chat-build`
reads `CORE_PORT` and `CORE_MGMT_PORT`. Sigma reported a shell dry run with
6890 and 6891 on 2026-10-18. A second dry run on 2026-10-18 printed
`app.rootkeys.consume.source=http://127.0.0.1:6891`. The RSocket port reaches
the shell through the environment when it starts. Stage 0 showed this with
6990 and 6991 ("Measured in stage 0", item 3).

## Build order

0. **A proof run.** Start a one-shot `chat-shell` against a disposable core,
   with redirected standard input, output, and error. Record what each
   stream holds and the exit status. The plan of stage 1 fixes its interface
   only after this run. Node 16 is not the disposable core.
1. `chat-shell` as a command tool: parts 1 to 5 above. The resident mode of
   part 3 waits for the read measurement of owner decision 21.
2. Node 9 and the `node-9/` directory.
3. `node-9/bin/register`, the register users, and the `AGENTS.md` step.
4. The migration of the files. It starts only when the stage 3 measure
   meets the 10-second target, or after the query cost is solved.

Each stage from 1 to 4 ends in its own PR.

## The migration

The migration follows stage 3. PR #264 merged the register command and the
`AGENTS.md` session step. The file `forward-register.md` is frozen at commit
`2d75f0cdd09115da422cdc0a693f0227f31a6ef5` (H). Stage 4 reads each
source file from H. It does not edit the frozen file.

1. Import `forward-register.md` and the six tracked archive files:
   `forward-register-archive-2026-08.md`, `-2026-09.md`, `-2026-10.md`, and
   `-2026-10-17.md`, `-2026-10-18.md`, and `-2026-10-19.md`.
2. Each file becomes one topic. Each `##` section becomes one message, in
   file order. The text before the first `##` heading is the first section.
   A header message, before all sections, names the source file and the
   commit. The user who runs the import is the sender.
3. The import adds one archive pointer per file to `register-index`.
4. A check joins the section messages of each topic in file order, and
   compares the result with the full source file from H, byte for byte.
   Any difference stops the migration. If the store changes whitespace, the
   check finds it.
5. Measure the complete session-start read after the archive pointers exist.
   Solve a result above the 10-second target before removing the files.
6. Then the seven tracked files leave the repo.

Other files change with the removal:

- `AGENTS.md` already reads the topic register. It retains H so agents can
  locate the frozen source after the file leaves the current tree.
- `shell-scripts/build-health.sh` fails when the file passes 65,536 bytes.
  That check goes away with the file.
- `docs/BUILD-HEALTH.md`, `README.md`, and two test comments name the file or
  an archive.
- Older specs and plans name the file. They are history, and they stay as
  they are.
- The agent memory `forward-register-tip-rule` changes to the topic rule.

## Constraints

- Prose follows the project rule: ASD-STE100 strict mode.
- Kotlin tools must be GraalVM native. A shell script that calls
  `chat-shell` is not a Kotlin tool. This is the designer's call.
- Every merge needs the owner's named `--admin` bypass.
- Image builds use an empty `DOCKER_CONFIG`.

## Measured in stage 0

The stage 0 proof ran on 2026-10-17 against a disposable memory core on 6990
and 6991. `docs/superpowers/proofs/2026-10-17-one-shot/RECORD.md` holds the
evidence. These items were items 1 to 3 of the "Unverified" list. They keep
their numbers, because the stage 0 plan names them.

1. **The one-shot runner works with `chat-shell`.** With
   `spring.shell.interactive.enabled=false`, each process ran one command and
   exited by itself. The exit status is 0 or 1 alone. Record Q1 and Q3.
2. **One one-shot run takes 6.3 to 7.4 seconds.** So a session-start read
   must run in one process, or use the resident mode. Record Q5.
3. **`CORE_PORT` and `CORE_MGMT_PORT` route the shell.** With 6990 and 6991,
   every shell connection went to those two ports. Record Q6. Node 9 uses
   6890 and 6891, and no run has used those ports. Item 10 below covers it.

## Measured in stage 1

Stage 1 ran on 2026-10-18 against the disposable memory core on 6990 and 6991,
inside the stage 0 sandbox. `docs/superpowers/proofs/2026-10-18-stage-1/RECORD.md`
holds the cases. These facts are measured.

- **One-shot mode works with a login from a password file.** Case A2 logged
  in as Admin from `CHAT_SHELL_PASSWORD_FILE` and ran `whoami` with exit 0 and an
  empty standard error. Case A3, with a wrong password, gave 77. Case A4, with
  one variable alone, gave 2. This measures item 9 below.
- **The exit statuses 0, 2, 69, and 77 are measured.** A5, A6, and A7 gave 2.
  A12, with the core stopped, gave 69. The statuses 75, 76, and 124 have no
  producer yet.
- **A one-shot process takes 3.1 to 4.0 s** on the direct JVM launch, in cases
  A1 to A13. Four processes in a row, one for each read, take 14,935 ms in the
  median of five runs. That is over the 10-second target of decision 20, because
  it pays four JVM starts. See `READ-TIME.md`.
- **The session-start read in one process meets the target.** The command
  `register-read` reads the index and the newest recent entry of each topic in
  one JVM. On representative data (20 pointers, 3 topics with 5 entries of about 2
  KB each) it takes 4,476 ms in the median of five runs, from the launch to the
  exit. Sigma recommended this command on 2026-10-18, and said to build resident
  mode only if it missed 10 seconds. Mario confirmed the session-start choice
  on 2026-10-18. It did not miss, so task 9 is not built for this workload.
  The command enforces the 30-second limit from the launch, with status 124.
  A supervisor in `chat-shell-cli` covers the time before Java starts. A
  watchdog in `main` covers the JVM, and it takes no lock, so a blocked write
  cannot stop it. A spent budget refuses the read, and a budget that runs out
  during the read refuses the output. Live cases A14 and A15 and
  `shell-scripts/test-chat-shell-cli-deadline.sh` show it. When a blocked reader
  holds up the output at the limit, standard output can hold part of the text,
  and a caller discards the output of a command that does not exit with 0.
- **The native one-shot image builds and does not start a context.** The first
  failure is a missing `config/client-rsocket-local.yml`. See
  `docs/NATIVE-IMAGE.md`, section "Shell one-shot".
- **`add-topic` works on a `uuid` core** after the fix of `CHAT-siaetqtr`. The
  shell sends CBOR, which writes a UUID as 16 bytes, and the key routes now read
  16 bytes as a UUID.
- **`post` reads standard input unchanged.** Cases A9 and A10 posted a 3-line
  UTF-8 entry with no final newline and read it back through `messages --format
  records` with the same text. This measures item 6 below.
- **The modes refuse `!`.** The host escape returns no status to Spring Shell,
  so a mode would end with 0 after a failed host command. Case A13 gave 2.

## Measured in stage 2

Stage 2 ran on 2026-10-19 on this host. Node 16 stayed up. The full record
is `docs/superpowers/proofs/2026-10-18-node-9/RECORD.md`. These facts are
measured.

- **The launcher starts node 9 beside node 16.** `node-9/bin/start`
  returned 0. Health on 6891 was `UP`. The image was
  `node-9/cassandra-core-service-rsocket:0.0.1`. No node-16 container,
  image tag, or port changed.
- **The Admin password comes from the environment.** A login as `Admin`
  with the generated password succeeded. A login with `changeme` gave 77.
  `users` showed one `Admin` and no lowercase `admin`. The mechanism is the
  mounted `userinit.yml` and the required `SPRING_CONFIG_IMPORT`.
- **The schema step never drops a live keyspace.** A second start with the
  keyspace present ran no `cqlsh -f`. The posted message was unchanged.
- **A post writes a vector without a rebuild trigger.** A second post
  created a new generation within 10 s.
- **The vector files reload.** A stop and start left every file and mtime
  unchanged. The core reused them.
- **The toggle off keeps the files.** The core started, and the vector
  directory gained no file. A later on found the files.
- **The stop needs a 60 s timeout.** The Compose default sent SIGKILL to
  Cassandra (exit 137). A 60 s timeout let it end on SIGTERM (exit 143).
- **The move is not measured.** No destination host was named. A
  Docker-in-Docker daemon needs `--privileged` and the owner's approval.
- **A start with the image present calls no Maven.** `start` reads
  `CORE_IMAGE` from `node-9.env`. It calls Maven only when the image is
  absent and a build is due. A live start on 2026-10-19 built nothing, so a
  host with the loaded image needs Docker, `curl`, `nc`, and `openssl`.

## Unverified

Each item below is believed from reading code or config. No run has shown it.

4. **The size of one message.** No text length limit was found in the
   application. Cassandra and RSocket frames may still set one. The largest
   archive section must pass the import check.
5. **Live follow.** A `listen` on the owner's shell should show an entry that
   an agent posts, through Redis. No run has shown it. Stage 2 used
   one-shot commands alone.
6. **Command input.** Measured in stage 1, cases A9 and A10. See "Measured in
   stage 1". The number stays so that earlier references hold.
7. **The agent sandbox.** An agent may run in a sandbox that blocks a
   connection to `127.0.0.1:6890` or a call to Docker. The read must work
   from the agent's own shell. Stage 2 ran no sandboxed agent.
8. **The message text in the store.** The import check assumes that the
   store keeps text byte for byte. No run has shown it for a large section.
9. **The login from the environment.** Measured in stage 1, cases A2 and A3.
   See "Measured in stage 1". The number stays so that earlier references hold.
10. **The node-9 address.** Measured in stage 2. `chat-shell` connected to
    6890 and 6891 for `whoami`, `add-topic`, `post`, and `messages`. Item 3
    shows the mechanism with other ports.
11. **User initialization on a later start.** `RootKeyStartup` publishes the
    root-key ready event only when a load step loads a key
    (`chat-deploy/src/main/kotlin/com/demo/chat/config/deploy/init/RootKeyStartup.kt:63,81`).
    No run has shown whether a later start loads it. So no run has shown
    whether `initialUsers` applies again. This item does not authorize a
    test.

## Left to the builder

Stage 2 answers each item.

- The image is `node-9/cassandra-core-service-rsocket:<project version>`.
  `IMAGE_REPO_PREFIX=node-9` and the Maven `appImageName` produce it.
- The pinned versions are Cassandra 4.1.3, Redis 7, Consul 2.0.4, and
  busybox 1.36. Consul 2.0.4 is the version the shipped container reports.
- The scripts in `node-9/bin/` are bash.
- The ownership step is `busybox:1.36` with `chown`. Lucene and the vector
  store have one step each.
- `node9_apply_schema` applies the schema only when the keyspace query
  returns one `(0 rows)` line. A failed or unclear query applies nothing.

## Designer's calls

The owner did not decide these. Each one can change.

- Node 9 has its own Consul, Cassandra, and Redis. It uses its own Compose
  project and image name.
- Host ports 6890 and 6891 on `127.0.0.1`. No other host port.
- Key type `uuid`. Embeddings from the node-16 endpoint.
- The index pointer form, with the key as the match.
- The entry fields, and the 24-hour rule of the session-start output.
- Hand-off points as the times to write.
- `--as <name>` on every command. No agent holds the `Mario` password.
- One password file per agent.
- A setup step that creates missing secret files with random passwords.
- `messages --format records`, with the bound in the shell, not the store.
- The exit status table, the socket framing, and the time limits.
- The earliest index pointer for a key is the valid one.
- A post is done when a read shows it.
- A proof run before the plan of stage 1.
- One message per `##` section in the import.
- A shell script in `node-9/bin/`, not a Kotlin tool.
- One-shot mode first. The resident mode waits for the read measurement of owner decision 21.
- An entry travels as command input, through a new `post` command.
- `CHAT_SHELL_USER` and `CHAT_SHELL_PASSWORD_FILE` for the login.
- Four build stages after the proof run, each with its own PR.
- The register is not empty when node 9 is down. The command fails.
