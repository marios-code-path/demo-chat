# The topic register on node 9

Status: draft for owner review. Issue: `CHAT-jmnjvupr`. Date: 2026-10-17.

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
  secrets.env       passwords, the service password, and the embedding key,
                    ignored by git
  schema/           keyspace-uuid.cql, copied from shared-resources-cassandra
  bin/              start, stop, save-images, load-images, register
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
from an environment value in `secrets.env`, in the same way the shipped
`Service` user reads `CHAT_SERVICE_PASSWORD`. The `Admin` password also comes
from `secrets.env`, because the shipped value is `changeme`. The core needs
`CHAT_SERVICE_PASSWORD` too, and `secrets.env` holds it.

A setup step creates `secrets.env` with random passwords if the file does not
exist. It never replaces an existing file. This is the designer's call.

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
<fp-id> | <topic-name> | <task or archive> | <one-line title>
```

An archive pointer that spans many issues writes `-` as its fp ID.

The fp ID makes the owner's rule 4 an exact match. An agent searches the index
for its fp ID. A match means that the topic exists, and the agent uses it.

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

### The session-start command

`AGENTS.md` replaces `@forward-register.md` with one instruction: run
`node-9/bin/register read` and treat the output as part of `AGENTS.md`.
Every agent reads `AGENTS.md`, so the one instruction reaches Sigma, Theta,
and Phi. No harness file changes.

The command prints:

1. Every pointer of `register-index`, oldest first.
2. The newest message of each topic whose newest message is less than
   24 hours old.

An agent reads an older topic with `node-9/bin/register read <topic-name>`.

If node 9 does not answer, the command exits non-zero. It prints one line
that says the register is not available and names the reason. The agent then
reports this to the user, and does not treat the register as empty.

### The write command

`node-9/bin/register` also holds the write steps, so that every agent writes
the same way:

- `register open <fp-id> <short-name> <title>` finds the pointer for the fp
  ID, or creates the topic and adds its pointer.
- `register post <fp-id>` reads an entry from standard input and posts it to
  the topic of that fp ID.

Each write names its user with `--as <name>`. An agent uses the name that
its harness instruction file gives it: `Sigma`, `Theta`, or `Phi`. `AGENTS.md`
tells each agent to do this. The owner uses `Mario`. The command reads the
password of that user from `node-9/secrets.env`. It must not prompt. A read
needs no `--as`, and it uses the `Mario` account. This is the designer's call.

Every entry has several lines. The command must send the newlines of an entry
unchanged. Unverified item 6 covers this.

## The migration

The migration is a separate step, after the register works. It needs PR
#256 merged, because that PR creates `forward-register-archive-2026-10-17.md`.
Until the migration, the 24-hour `forward-register.md` of PR #256 stays the
register.

1. Import `forward-register.md` and the four archive files:
   `forward-register-archive-2026-08.md`, `-2026-09.md`, `-2026-10.md`, and
   `-2026-10-17.md`.
2. Each file becomes one topic. Each `##` section becomes one message, in
   file order. The first message names the source file and the commit. The
   user who runs the import is the sender.
3. The import adds one archive pointer per file to `register-index`.
4. A check compares the imported text with each file. It must find no lost
   section.
5. Then the files leave the repo.

Other files change with the removal:

- `AGENTS.md` imports the file today.
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

## Unverified

Each item below is believed from reading code or config. None is measured.

1. **A one-shot `chat-shell` run.** The shell starts with
   `spring.shell.interactive.enabled=true`. Tests start it with `false`. No
   run has shown that the shell can log in, run one command, print, and exit.
   `login --password` avoids the prompt. `script -f` is a second path. **The
   first build task must prove this.**
2. **The time of one read.** Each run starts a JVM. If a read takes too long
   for a session start, a long-running shell may be needed. Measure it.
3. **The shell address.** The shell reads its core address from
   `config/client-rsocket-local.yml` and its root keys from
   `http://127.0.0.1:6791`. `chat-build shell` has no flag for another port.
   The builder must find how to point the shell at 6890 and 6891.
4. **The size of one message.** No text length limit was found in the
   application. Cassandra and RSocket frames may still set one. The largest
   archive section must pass the import check.
5. **Live follow.** A `listen` on the owner's shell should show an entry that
   an agent posts, through Redis. No run has shown it.
6. **Newlines in one command.** An entry has several lines. Spring Shell
   parses a command line, and a one-shot run passes the entry as an
   argument. No run has shown that newlines arrive unchanged.
7. **The agent sandbox.** An agent may run in a sandbox that blocks a
   connection to `127.0.0.1:6890` or a call to Docker. The read must work
   from the agent's own shell.

## Left to the builder

- The exact image name of the node-9 core, and how the build tags it.
- The pinned versions of the Cassandra, Redis, and Consul images. Consul is
  `latest` today, and a move needs a fixed version.
- The script language inside `node-9/bin/`.
- The way the shell reaches node 9 (unverified item 3).
- The volume ownership step for Lucene and the vector store. The shipped
  overlays use a `busybox` init container that runs `chown`.
- The keyspace setup on the new Cassandra.

## Designer's calls

The owner did not decide these. Each one can change.

- Node 9 has its own Consul, Cassandra, and Redis. It uses its own Compose
  project and image name.
- Host ports 6890 and 6891 on `127.0.0.1`. No other host port.
- Key type `uuid`. Embeddings from the node-16 endpoint.
- The index pointer form, with the fp ID as the match key.
- The entry fields, and the 24-hour rule of the session-start output.
- Hand-off points as the times to write.
- `--as <name>` on writes. Reads use the `Mario` account.
- A setup step that creates `secrets.env` with random passwords.
- One message per `##` section in the import.
- A shell script in `node-9/bin/`, not a Kotlin tool.
- The register is not empty when node 9 is down. The command fails.
