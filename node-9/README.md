# Node 9

Node 9 runs the topic register core on its own Compose project,
`chat-node-9`. It has its own Cassandra, Redis, and Consul. It shares no
project, network, image tag, or volume with node 16.

The design is in
`docs/superpowers/specs/2026-10-17-topic-register-design.md`, section
"Node 9". The live proof is in
`docs/superpowers/proofs/2026-10-18-node-9/RECORD.md`. The shell attach proof
is in `docs/superpowers/proofs/2026-10-22-shell-attach/RECORD.md`.

## Prerequisites

- Docker with Compose v2.
- `curl`, `nc`, and `openssl`.
- `jq` and `uuidgen` for `node-9/bin/register`.
- A route to `node2.lan:6090` for embeddings.
- Free loopback ports 6890, 6891, and 6892.
- Maven and a JDK, for a build only. `start` calls no Maven when the image is
  present, so a host that loads the image needs no Maven and no repository.

## Ports

| Use | Host | Container |
|-----|------|-----------|
| RSocket | `127.0.0.1:6890` | `6790` |
| Management | `127.0.0.1:6891` | `6791` |
| Consul registry | `127.0.0.1:6892` | `8500` |

Cassandra and Redis publish no host port.

## Start

Run `node-9/bin/start`.

1. It creates `secrets.env` when it is missing.
2. It builds the image when the reference is absent.
3. It starts Cassandra, Redis, and Consul.
4. It applies the schema only when the keyspace is absent.
5. It starts the core.
6. It waits for health on port 6891.

`--dry-run` prints the Compose command, the build command with its
environment, and the image reference. An installed copy prints no build
command, because it runs no build. A dry run writes no secret. It
creates the data directories.

Exit status:

- 2: an unknown argument, a bad toggle, or a failed environment load.
- 3: port 6890, 6891, or 6892 is in use.
- 4: the image reference is not a node-9 reference, or an installed copy
  has no image.
- 5: a build, schema, start, or health step failed.

## Stop

Run `node-9/bin/stop`. It stops the `chat-node-9` project with a 60 s
timeout. The default 10 s timeout sends SIGKILL to Cassandra. Measured on
2026-10-19.

## The vector toggle

`NODE9_VECTOR_PERSIST=on` is the default. It adds
`compose.vector-disk.yaml`. The core writes its embedded vectors under
`node-9/data/vector`.

`NODE9_VECTOR_PERSIST=off` starts without the vector path. It keeps the old
files. A later `on` finds them.

Any other value stops with status 2 and names the value.

## The vector directory

The path is `node-9/data/vector/<identity>`. The identity of this host is
`nomic-embed-text-v1-5-768`.

The vector store is a derived cache. Cassandra holds the source of truth.
A post writes a new generation. A restart reuses the files. Measured on
2026-10-19.

## Secrets

The vaults are per host. The current Git tree tracks no vault ciphertext. The full
runbook is `node-9/ansible/vault/README.md`. See CHAT-cnwkjsrp.

Two Ansible Vault files hold the stored values:

- `shared.yml` holds `service_password`, `admin_password`,
  `embedding_api_key`, `sigma_password`, `theta_password`, and
  `phi_password`. Its vault ID is `shared`.
- `owner.yml` holds `mario_password` alone. Its vault ID is `owner`.

`node-9/install --vault-source DIR` copies a complete bundle to fixed
per-host paths:

| Bundle file | Target | Mode |
|-------------|--------|------|
| `shared.yml` | `<parent of prefix>/vault/shared.yml` | 0600 |
| `owner.yml` | `<parent of prefix>/vault/owner.yml` | 0600 |
| `vault-pass` | `${XDG_CONFIG_HOME:-$HOME/.config}/chat-node-9/vault-pass` | 0600 |

The installer links the installed `ansible/vault/shared.yml` and
`ansible/vault/owner.yml` to the stable files. A code replacement removes
neither vault.

A `--vault-source` install copies ciphertext alone. Its marker records
`plaintext: pending`. Finish with the installed commands:

```bash
/opt/demo-chat/node-9/bin/setup-secrets
/opt/demo-chat/node-9/bin/setup-secrets --owner
```

The first command runs the shared playbook. The second prompts for the
owner vault password and writes `CHAT_USER_MARIO_PASSWORD`. Ansible stores
that password nowhere.

`node-9/bin/setup-secrets --local` generates random values without Ansible.
It runs only on an explicit request, and the owner password needs a
terminal. The default run never falls back to it. When the shared vault is
absent, `setup-secrets` names `--vault-source` and the runbook.

A write is create-only. `secrets.env` keeps mode 0600, `node-9/secrets/`
keeps mode 0700, and each agent password file keeps mode 0600. The written
files hold plaintext. Vault protects the stored copy alone.

The tested Ansible is ansible-core 2.21.5.

`CHAT_EMBEDDING_OPENAI_API_KEY` starts as `replace-me`. Replace it in the
Vault, then run `setup-secrets` again.

`node-9/bin/register --as Mario` reads `CHAT_USER_MARIO_PASSWORD` from
`secrets.env`, which has mode 0600. No `Mario.password` file exists. A
missing value gives status 77.

`node-9/userinit.yml` adds `Sigma`, `Theta`, `Phi`, and `Mario`, and it
overrides the shipped `Admin` password. The core imports it from
`/etc/node-9/userinit.yml`.

## Fixed isolation values

`NODE9_PROJECT`, `NODE9_RSOCKET_PORT`, `NODE9_MGMT_PORT`,
`NODE9_REGISTRY_PORT`, and `CORE_IMAGE` are fixed. `lib.sh` rejects a
changed value. `NODE9_DIR` comes from `lib.sh`, not from the caller.

The three ports bind to `127.0.0.1` alone. 6890 is the RSocket port of the
core. 6891 is its management port. 6892 is the Consul registry.

## Attach the shell to node 9

Run this from a source tree:

```bash
just launch-shell 1 --key-type uuid --registry 127.0.0.1:6892 --core-id 9
```

`--registry` names the registry of node 9. `--core-id 9` names the core. The
first argument is the node id of the shell, not of the core. The key type
must match the core, which is `uuid`.

The core registers the instance id `core-service-rsocket-9`. Its metadata
holds `nodeId`, `advertiseHost` and `advertisePort`. `advertise.yml` sets the
last two, because Consul holds the address of the container network, and a
client on the host cannot reach it. See CHAT-yexqiyie.

## Install outside the source tree

Run `node-9/install --prefix /opt/demo-chat/node-9`. The prefix defaults to
that path. Run node 9 from the installed copy, not from a work tree. A work
tree holds its own `data/`, `secrets/` and `secrets.env`, so removing it
removes node 9's state.

Every install states exactly one secrets choice:

```bash
node-9/install (--vault-source DIR | --import-state DIR \
                | --local-secrets | --keep-secrets)
```

- `--vault-source DIR` copies a complete vault bundle. The bundle holds
  `vault-pass`, `shared.yml`, and `owner.yml`. Plaintext setup stays
  pending; the run prints both `setup-secrets` commands.
- `--import-state DIR` copies a complete plaintext set. The set holds seven
  keys, three agent files, and each file equals its key value.
- `--local-secrets` generates new local values during the install. The owner
  password needs a terminal. It creates no vault, and the values are not
  reproducible after loss.
- `--keep-secrets` keeps complete state on a reinstall. It changes no secret
  or vault file.

For a full install, zero or two choices stop with status 2 before any build or copy, and print
the four choices.

For an existing installed home, run `node-9/install --bin-only` to replace
only `bin/`. This mode needs no secrets choice, Java, or Maven.
It keeps `.installed`, `lib/`, secrets, data, images, schema, and Ansible files.
It refuses an uninstalled home and a symbolic link at `bin/`.
Use `--dry-run` to inspect the source and target paths without copying files.

The installer copies the code files and launchers, then writes the `.installed` marker last.
It creates `data/`, `secrets/` (mode 0700) and `images/` when they are
missing. It never deletes `data/`, `secrets/`, `secrets.env`, or a stable
vault file. The marker records the choice:

```
secrets: vault-source
plaintext: pending
```

The values are `vault-source`, `import-state`, `local`, or `kept`, and the
plaintext state is `pending` or `complete`.

A source tree refuses to run. `start`, `stop`, `setup-secrets`, `register`,
`register-create`, `load-images` and `save-images` stop with status 2 unless
the `.installed` marker is present. `NODE9_ALLOW_SOURCE_TREE=1` overrides the stop. Use it to
stop containers that started from a work tree. Use it in the offline tests.

The installer refuses a prefix inside a git work tree. It refuses a
non-empty prefix that has no marker.

`--dry-run` prints the choice, source paths, target paths, and create or
keep actions. It writes no file and prints no value.

The marker changes three scripts. An installed `start` never calls Maven. A
missing image stops it with status 4. Run `load-images` first. An installed
`register` takes the CLI from `CHAT_SHELL_CLI`, or from `bin/chat-shell-cli`.
An installed `setup-secrets` skips its repository check.

Both vault files stay outside the code directory, at
`<parent of the prefix>/vault/`. The installer links them into
`ansible/vault/`. The Vault password stays at
`${XDG_CONFIG_HOME:-$HOME/.config}/chat-node-9/vault-pass`.

An installed home from the earlier layout may hold a copy of the last
tracked `shared.yml`. A copy with the recorded hash is inert: the code
replacement removes it, and any choice proceeds. A different file stops the
run with status 1 and is never removed. See `node-9/ansible/vault/README.md`.

Containers that started from a work tree keep the old mounts. Run `stop`
there, then run `start` from the installed copy.

## The installed shell CLI

An installed home runs the shell CLI from `lib/`. That directory holds every
runtime jar and the file `shell-cli.options`. The home then needs no
repository, no Maven, and no `~/.m2`. See `CHAT-bjqzvsoe`.

The install also copies `chat-shell-portal`, `chat-shell-portal-tunnel`, `chat-mcp-stdio`, and
`generate-agent-instructions` into `bin/`. It copies `setup-portal` from
`node-9/bin/`. The MCP jars are in `lib/mcp/`.
The installed MCP launcher needs no source tree or Maven repository.
Run `bin/generate-agent-instructions PROJECT_NAME /absolute/project/directory`
to create the agent include and add it to that project's `AGENTS.md`.

Run `bin/setup-portal AGENT PROJECT [start|status|stop]` to manage one named portal and tunnel.
The default action is `start`. The `status` and `stop` actions need no password.
The default destination is `node2.lan`.
Set `CHAT_PORTAL_HOST` to another host before this command when needed.
The command prints the socket path when the portal is ready.
The command also writes the live socket path to a small file. The file is
`${HOME}/.local/state/<project>/portal-<agent>/socket`. Both names are lower
case. The file holds one absolute path and one newline. Its mode is `600`, and
its directory mode is `700`. A stop removes the file only when it names this
socket.
`bin/setup-portal` reads no register and sets no register variable. Run
`register config` to write the app config that names the register and the
socket file. See `docs/MCP-ADAPTER.md`.
It reads `secrets/AGENT.password` by default.
Set `CHAT_SHELL_PASSWORD_FILE` to another file, or set `CHAT_SHELL_PASSWORD` for a temporary password file.

The install builds both jar sets on demand. It needs Java 25 or later and
Maven. It stops with status 2 and names the missing command when either one
is absent. The earlier shell set measured 149 jars and about 66 MB.

`--skip-cli` does not build or replace `lib/`. A new home has no `lib/`, and its
marker records `cli: skipped`. An existing home keeps its jar set, and its
marker records `cli: lib, kept`. The offline suites use this flag because a
full build takes minutes. The installed MCP launcher needs `lib/mcp/`.

`NODE9_JAVA` names the java of the version check and of the installed
launcher. It also sets `JAVA_HOME` of the build, so Maven compiles with that
JDK. The home comes from the JVM, and never from the path of the java. A java
that reports no JDK home leaves `JAVA_HOME` as it stands, and the build names
that limit on standard error. An install that names no java also leaves
`JAVA_HOME` as it stands, because that value is the choice of the environment.
`NODE9_MVN` names the mvn of the build.

Every probe of a java takes a time limit, because a java can answer nothing.
`NODE9_JAVA_LIMIT` sets the seconds, and the default is 30. The install stops
with status 2 and names the limit when the version probe passes it. The limit
needs no `timeout` command. `shell-scripts/java-probe.sh` holds it, and the
install and the build script both read that one file.

`shell-cli.options` holds the JVM options of the launch. The install freezes
them, because the source-tree launcher builds that list by calling
`shell-scripts/chat-build`, and an installed home holds no such script. The
file serves key type `uuid` and node 9 alone. Its first two lines record that
pair. A caller that asks for another pair gets one line and status 2. A caller
that asks for neither takes the frozen pair.

The installed launcher defaults to core port 6890 and management port 6891.
The frozen root key URL uses management port 6891.

The launcher and its frozen options hold no fixed login name. The caller
selects a login at run time with `CHAT_SHELL_USER` and
`CHAT_SHELL_PASSWORD_FILE`.

```bash
just build-shell-cli-lib /tmp/cli-lib     # stage the jar set alone
node-9/install --keep-secrets             # rebuild and install all code
node-9/install --keep-secrets --skip-cli  # keep lib while updating code
node-9/install --bin-only                 # update bin alone
```

## Move to another host

1. Run `node-9/bin/stop`.
2. Run `node-9/bin/save-images`.
3. Copy `node-9/` to the other host.
4. Run `node-9/bin/load-images`, then `node-9/bin/start`.

The core image is in no registry. `save-images` writes five tar files under
`node-9/images/`. The move was not verified in stage 2.

The destination needs Docker with Compose v2, `curl`, `nc`, and `openssl`.
It needs no Maven, no JDK, and no repository copy. `start` reads
`CORE_IMAGE` from `node-9.env` and finds the loaded image. It calls Maven
only when the image is absent.

## The project register

The register is an application concern. Install creates no register. Run
`register-create` for each project checkout. Each install holds its own copy
of every project register that it imports.

`NODE_9_REGISTER_TOPIC_NAME` holds the register name, for example
`demo-chat-register`. It lives in `.envrc` of the project checkout. A read
command takes the name, or `NODE_9_REGISTER_TOPIC_ID`. A write command needs
the name, because the name mints every topic. The name wins when both
variables are set. No variable stops the command with status 2.

One register owns three topic shapes:

| Purpose | Shape |
|---------|-------|
| Pointer index | `<name>` |
| Entry topic | `<name>-fr-<short>` |
| Snapshot topic | `<name>-fr-snapshot-<12 hex>` |

The name holds at most 37 characters. That bound leaves room for
`-fr-snapshot-` and twelve hex characters inside the 64 character limit.

```bash
/opt/demo-chat/node-9/bin/register-create <name> [<path>] [--source-date DATE] --as <owner>
```

The command takes the name as an argument, and it reads no register variable.
Without a path it creates the room alone, with its ownership proof and its
membership. With a path it gives one entry to each `## ` section after the
preamble. A path needs `--source-date`, because the date enters the bundle
hash. A retry repeats the date and resumes. A new date makes a new bundle,
and the occupied index stops that run with status 1.

The command refuses a name that another register holds. A register owns every
room that starts with `<name>-fr-`. A room of this run's own topics is not a
collision, so a retry resumes.

Exit status:

- 0: the register is ready.
- 1: a room of another register holds the name space, the index holds
  messages without a matching receipt, or a readback differs.
- 2: an argument, a name, a path, a symbolic link, a section, or the
  installed guard.
- 69: the core is not available.
- 75: a post outcome stays uncertain. Retry with the same date.
- 77: a login is absent.
- 124: a bounded read passed its time limit.

A section above 16,384 bytes stops with status 2. A repeated slug takes
`<slug>-<section>`. A second collision, an empty slug, and a short name of
the snapshot shape also stop with status 2.

`node-9/bin/register` reads, opens, and posts one register. The design is
`docs/superpowers/specs/2026-10-24-project-register-design.md`.

The portal socket serves the same open and post rules. The shell path stays
the default for a login that holds a password file. The portal path serves an
agent that holds a socket. Both paths apply the key rule, the short name
rule, the title rule, the request id rule, the earliest pointer rule, the
join rule, and the readback rule. The portal adds a 3072-byte text limit.
It also limits titles to 256 characters and request IDs to 128 characters.
These limits apply to the portal only. A portal request frame holds at most
32768 bytes, including the newline. A change to a shared rule must update
both paths. See `CHAT-secgyzgg`.

## Import a register snapshot

One bundle carries one reviewed Markdown snapshot to a prepared host. See
`CHAT-jmnjvupr` and
`docs/superpowers/specs/2026-10-21-portable-register-import-design.md`.

```bash
node-9/bin/register-import pack --source FILE --mapping FILE \
    --bundle DIR --register NAME
/opt/demo-chat/node-9/bin/register-import apply --bundle DIR \
    --as Mario --register NAME
/opt/demo-chat/node-9/bin/register-import verify --bundle DIR \
    --as Mario --register NAME
```

`pack` makes no network call, and it needs no installed home. `apply` and
`verify` run from an installed copy alone. Every command takes the register
name from `--register`, or from `NODE_9_REGISTER_TOPIC_NAME`. An empty value
after the flag stops with status 2. The name mints every topic and every
request ID.

Exit status:

- 0: the command passed every check.
- 1: a conflict, a malformed receipt, a failed readback, or a byte difference.
- 2: an argument, a bundle, a path, the installed guard, or the shell CLI.
- 69: the core is not available.
- 75: a post outcome stays uncertain. Retry with the same bundle.
- 77: a login is absent.
- 124: a bounded read passed its time limit.

Receipts live in `$NODE9_DIR/data/register-import/<register>/<bundle-hash>/`.
They belong to one host and one register. Do not copy them to another host. A
retry reads them and makes no second message.

Mario keeps every agent off the host during the import. Mario directs agents
to the new register only after `verify` returns 0.

Mario reviews the changing facts again on the UTC import date. Those facts
hold the issue state, the open pull requests, the branches, and the current
checkout. The source date must equal that UTC date. A changed fact needs a new
snapshot, a new mapping, and a new review of both hashes.

## The embedding route

The base Compose file holds the embedding settings. The overlay never holds
them. So `off` still starts a core with an embedding endpoint.

An index rebuild calls the embedding endpoint. A slow or absent endpoint
delays or fails the rebuild. An unreachable endpoint at start was not
measured.

## Tests

`bash shell-scripts/test-node-9.sh` runs offline. It starts no container.

`bash shell-scripts/test-node-9-register.sh` checks the `register` wrapper
against a stub shell CLI. `bash shell-scripts/test-register-bundle.sh` checks
`register-range`, `register-uuid5`, and `pack`.
`bash shell-scripts/test-register-bootstrap.sh` checks `apply`, `verify`, and
`register-create` against a stub shell CLI and a stub cqlsh. The three suites
run offline, and they reach no core.

Ten cases of `test-node-9.sh` need free ports 6890, 6891, and 6892. Stop node
9 before that suite.
