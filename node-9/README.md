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

Ansible Vault holds the stored values. `node-9/ansible/vault/shared.yml` is
encrypted. Its password stays outside the repository, at
`~/.config/chat-node-9/vault-pass`, with mode 0600.

`node-9/bin/setup-secrets` writes the values. It runs the shared playbook by
default. It runs the owner playbook with `--owner`, from Mario's terminal.
The owner playbook reads `node-9/ansible/vault/owner.yml` and writes
`CHAT_USER_MARIO_PASSWORD`. No client copy exists. Mario creates that Vault
file once:

```
ansible-vault create --vault-id owner@prompt node-9/ansible/vault/owner.yml
```

The file holds one line: `mario_password: <the owner password>`.

`node-9/bin/setup-secrets --local` generates random values without Ansible.
The default run never falls back to it. A missing `ansible-playbook` or
Vault password stops the default run with one line.

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

The installer copies the code files, writes the `.installed` marker, and
creates `data/`, `secrets/` (mode 0700) and `images/` when they are missing.
A re-run replaces code files alone. It never reads, writes, or deletes
`data/`, `secrets/`, `secrets.env`, or `images/`.

A source tree refuses to run. `start`, `stop`, `setup-secrets`, `register`,
`load-images` and `save-images` stop with status 2 unless the `.installed`
marker is present. `NODE9_ALLOW_SOURCE_TREE=1` overrides the stop. Use it to
stop containers that started from a work tree. Use it in the offline tests.

The installer refuses a prefix inside a git work tree. It refuses a
non-empty prefix that has no marker.

`--import-state DIR` copies `secrets.env` and `secrets/` from the node-9
directory `DIR`. It never overwrites a file. It skips the import and names the
gap when the set is incomplete. It never copies Cassandra data. `--dry-run`
changes nothing.

The marker changes three scripts. An installed `start` never calls Maven. A
missing image stops it with status 4. Run `load-images` first. An installed
`register` takes the CLI from `CHAT_SHELL_CLI`, or from `bin/chat-shell-cli`.
An installed `setup-secrets` skips its repository check.

The owner Vault stays at `<parent of the prefix>/vault/owner.yml`. The
installer links it into `ansible/vault/owner.yml`. The Vault password stays
at `~/.config/chat-node-9/vault-pass`.

Containers that started from a work tree keep the old mounts. Run `stop`
there, then run `start` from the installed copy.

## The installed shell CLI

An installed home runs the shell CLI from `lib/`. That directory holds every
runtime jar and the file `shell-cli.options`. The home then needs no
repository, no Maven, and no `~/.m2`. See `CHAT-bjqzvsoe`.

The install builds the jar set on demand. It needs Java 25 or later and
Maven. It stops with status 2 and names the missing command when either one
is absent. The measured set holds 149 jars and about 66 MB.

`--skip-cli` installs no `lib/`. The marker then records `cli: skipped`, so no
reader takes that home for a complete one. The offline suites use that flag,
because a full build takes minutes.

`NODE9_JAVA` names the java of the version check and of the installed
launcher. `NODE9_MVN` names the mvn of the build. Maven selects its own JDK
through `JAVA_HOME`. So a host with several JDKs also sets `JAVA_HOME` for the
build.

`shell-cli.options` holds the JVM options of the launch. The install freezes
them, because the source-tree launcher builds that list by calling
`shell-scripts/chat-build`, and an installed home holds no such script. The
file serves key type `uuid` and node 9 alone. Its first two lines record that
pair. A caller that asks for another pair gets one line and status 2. A caller
that asks for neither takes the frozen pair.

The installed launcher defaults to core port 6890 and management port 6891.
The frozen root key URL uses management port 6891.

The login is no part of the installed runtime. It arrives at run time in two
values, `CHAT_SHELL_USER` and `CHAT_SHELL_PASSWORD_FILE`. No installed file
holds a login name.

```bash
just build-shell-cli-lib /tmp/cli-lib     # stage the jar set alone
node-9/install                            # build and install it
node-9/install --skip-cli                 # install the scripts alone
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

## The embedding route

The base Compose file holds the embedding settings. The overlay never holds
them. So `off` still starts a core with an embedding endpoint.

An index rebuild calls the embedding endpoint. A slow or absent endpoint
delays or fails the rebuild. An unreachable endpoint at start was not
measured.

## Tests

`bash shell-scripts/test-node-9.sh` runs offline. It starts no container.
