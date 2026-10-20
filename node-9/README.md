# Node 9

Node 9 runs the topic register core on its own Compose project,
`chat-node-9`. It has its own Cassandra, Redis, and Consul. It shares no
project, network, image tag, or volume with node 16.

The design is in
`docs/superpowers/specs/2026-10-17-topic-register-design.md`, section
"Node 9". The live proof is in
`docs/superpowers/proofs/2026-10-18-node-9/RECORD.md`.

## Prerequisites

- Docker with Compose v2.
- `curl`, `nc`, and `openssl`.
- A route to `node2.lan:6090` for embeddings.
- Free loopback ports 6890 and 6891.
- Maven and a JDK, for a build only. `start` calls no Maven when the image is
  present, so a host that loads the image needs no Maven and no repository.

## Ports

| Use | Host | Container |
|-----|------|-----------|
| RSocket | `127.0.0.1:6890` | `6790` |
| Management | `127.0.0.1:6891` | `6791` |

Cassandra, Redis, and Consul publish no host port.

## Start

Run `node-9/bin/start`.

1. It creates `secrets.env` when it is missing.
2. It builds the image when the reference is absent.
3. It starts Cassandra, Redis, and Consul.
4. It applies the schema only when the keyspace is absent.
5. It starts the core.
6. It waits for health on port 6891.

`--dry-run` prints the Compose command, the build command with its
environment, and the image reference. A dry run writes no secret. It
creates the data directories.

Exit status:

- 2: an unknown argument, a bad toggle, or a failed environment load.
- 3: port 6890 or 6891 is in use.
- 4: the image reference is not a node-9 reference.
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

`node-9/secrets.env` has mode 0600. `node-9/bin/setup-secrets` creates it
and never replaces it. It prints no value.

`CHAT_EMBEDDING_OPENAI_API_KEY` starts as `replace-me`. Replace it with the
node-2 key.

`node-9/userinit.yml` overrides the shipped `Admin` password with
`${CHAT_ADMIN_PASSWORD}`. The core imports it from
`/etc/node-9/userinit.yml`.

## Fixed isolation values

`NODE9_PROJECT`, `NODE9_RSOCKET_PORT`, `NODE9_MGMT_PORT`, and `CORE_IMAGE`
are fixed. `lib.sh` rejects a changed value. `NODE9_DIR` comes from
`lib.sh`, not from the caller.

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
