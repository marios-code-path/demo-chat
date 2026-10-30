# chat-build

For partial verification builds, see [Partial builds](README-build-partial.md).

A single Python CLI that composes the Maven command for any demo-chat service from
declarative feature flags. It replaces the flag assembly spread across
`build-app.sh` and the five `run-*.sh` scripts.

Python 3.10+, stdlib only. No virtualenv, no `pip install`.

```bash
./shell-scripts/chat-build core --memory --run --notls --node-id 0 --key-type long
# or via the wrapper
./shell-scripts/run core --memory --run --notls --node-id 0
```

The shell scripts it replaced have been removed. `test-flags.sh` asserts that
chat-build keeps emitting the flags recorded under `golden/`.

---

## Quick start

```bash
# Core RSocket service, in-memory backend, seeding users and root keys
chat-build core --memory --run --notls --node-id 0 --init users,rootkeys --key-type long

# Same, but Cassandra-backed with UUID keys
chat-build core --cassandra --run --notls --node-id 0 --uuid --init users,rootkeys

# Kafka backend (Kafka messaging, memory persistence, Lucene index)
chat-build core --kafka --run --notls --node-id 0 --key-type long

# REST facade (talks to core over RSocket)
chat-build rest --run --notls --node-id 0 --key-type long

# Interactive shell client
chat-build shell --run --notls --node-id 0 --key-type long

# Build a container image instead of running
chat-build core --cassandra --build --notls --node-id 0 --key-type long

# See what would happen, run nothing
chat-build core --memory --run --notls --node-id 0 --dry-run --key-type long
```

`--dry-run` prints the resolved feature set, the Maven command, every
`JAVA_TOOL_OPTIONS` flag one per line, and the env file path. It is the fastest
way to answer "why is this property set".

## Build image manifest

Pass `--image-manifest PATH` with `--build` to record each successful image
build. `CHAT_IMAGE_MANIFEST` sets the default path. Reuse one path for the
builds in one deployment run. Use a new path for the next run.

```bash
./shell-scripts/chat-build core --cassandra --build --notls --node-id 9 --uuid \
    --image-manifest /tmp/release-images.json
./shell-scripts/image-references /tmp/release-images.json
```

Each record holds the image reference, image id, architecture, service, and
module. A matching repository digest is optional. The writer locks the file
and replaces it as one operation. `image-references` prints one reference per
line. Use its output for a push command.

The path has no effect with `--run` or `--dry-run`. Without a path, the build
does not query Docker for manifest data. The manifest test runs with
`just check-image-manifest`.

## Container launch

`chat-deploy` builds one core RSocket image through `chat-build` and starts one
core container. It selects the providers inside that image. It does not start
the REST facade, authorization server, gateway, or a separate pub/sub service.

| Backend flag | Key | Persistence | Index | Secrets | Pub/sub |
|---|---|---|---|---|---|
| `--memory` | memory | memory | Lucene | memory | memory |
| `--cassandra` | Cassandra | Cassandra | Cassandra | Cassandra | memory |
| `--cassandra --index lucene --pubsub redis-pubsub` | Cassandra | Cassandra | Lucene | Cassandra | Redis |

The selected persistence provider serves all core domains in its deploy
module. `chat-build core` enables the user, topic, message, persistence,
index, key, pub/sub, and secrets controllers. It also enables composite
services. The backend flag selects a deploy module. The Cassandra module now
contains both index providers and both pub/sub providers. The selectors choose
one provider for each capability. `chat-build core` sets
`app.command.bus=memory` for every row. The memory command bus keeps its
handlers in the core process. The launcher requires an explicit pub/sub choice.
On a terminal, it asks when `--pubsub` is absent. In a script, pass either
`--pubsub memory` or `--pubsub redis-pubsub` with Cassandra.

### Requirements

Install Docker with Compose and Miniforge with a `base` environment. The
launcher runs `chat-build` through `conda run -n base`. Choose a unique
`--node-id` for deployments that write to the same Cassandra store. The node
id has no default. Supply `CHAT_SERVICE_PASSWORD` before a live launch. The
current container path supports `--notls` only.

With no `CASSANDRA_CONTACT_POINTS`, the launcher starts the existing Compose
Cassandra service. It uses `cassandra:9042` inside the Docker network. It
applies the matching schema only when the keyspace is absent. The repository
schema starts with `DROP KEYSPACE`, so never apply it to an existing keyspace.
With an explicit Cassandra address, the launcher checks for the keyspace.
It does not change an external Cassandra cluster. The address must be
reachable from the core container. `localhost` and `127.0.0.1` are rejected.

With Redis pub/sub, the launcher starts the existing Compose Redis service
when `REDIS_TOPICS_HOST` is absent. Set `REDIS_TOPICS_HOST` and, if needed,
`REDIS_TOPICS_PORT` to use another reachable Redis server. The Redis gate
requires `PONG` before the core build starts.

### Commands

Inspect a memory launch without starting services or building an image:

```bash
./shell-scripts/chat-deploy core --memory --pubsub memory --notls --node-id 7 --dry-run
```

Start a memory core container:

```bash
export CHAT_SERVICE_PASSWORD='<service password>'
./shell-scripts/chat-deploy core --memory --pubsub memory --notls --node-id 7
```

Start the mixed Cassandra, Lucene, and Redis core container:

```bash
export CHAT_SERVICE_PASSWORD='<service password>'
just deploy-core-cassandra-lucene-redis --key-type long
```

The task uses node ID 7. Use `node_id=16` or `16` to select node ID 16.
The task selects the memory command bus through `chat-build`.

The task needs `--key-type TYPE`, and no key type is a default. Without it,
the task exits 2 before any container starts. `long` uses keyspace
`chat_long`, and `uuid` uses keyspace `chat_uuid`. Claude import supports the
`uuid` key type alone. The task passes the value to `chat-build`, which checks
it:

```bash
just deploy-core-cassandra-lucene-redis 16 --key-type uuid
```

Add `--vector embedded --embedding openai` to enable vector indexing. Both
options are required together. The Cassandra image contains both providers.
Redis remains the pub/sub provider. The vector store uses a named volume.

Set these values before a live vector launch:

```bash
export CHAT_EMBEDDING_OPENAI_BASE_URL=http://node2.lan:6090/v1
export CHAT_EMBEDDING_OPENAI_MODEL=nomic-embed-text-v1.5.Q8_0.gguf
export CHAT_EMBEDDING_IDENTITY=nomic-embed-text-v1-5-768
export CHAT_EMBEDDING_OPENAI_API_KEY=not-a-secret
just deploy-core-cassandra-lucene-redis 16 --key-type uuid \
    --vector embedded --embedding openai
```

The base URL ends in `/v1`. The current SDK adds `/embeddings`.
The node2 endpoint does not read the key. It accepts `not-a-secret`.
Use a real runtime key when another endpoint requires one.
The launcher requires all four values before it starts a container.
Compose passes them to the core when the container starts. The image plan
contains the provider selectors, but it contains no API key. The vector
volume mounts at `/var/lib/chat/vector`. The store creates one directory for
each embedding identity.

The vector launch exposes `/actuator/vectorindex`. Trigger a rebuild after
imports. Trigger a rebuild after each restart. Wait for a `SUCCEEDED` job.
See `docs/VECTOR-RECALL-API.md`.

Start the shell with the same key type. `--build` installs the current shell
modules first:

```bash
just launch-shell 1 --key-type uuid --build
```

Add `--dry-run` to print the plan. A dry run starts no service:

```bash
just deploy-core-cassandra-lucene-redis 16 --key-type uuid --dry-run
```

The launcher applies `keyspace-uuid.cql` only when `chat_uuid` is absent. A
`chat_uuid` keyspace from before 2026-10-16 fails the start, because it lacks
the `UUID` message columns and the `service_lease` table. Drop it, and the
next start creates it again. `just check-deploy-recipe` checks these flags.

A deployment prints one `[chat-deploy]` line at the start of each stage. The
stages are the install, the image build, the container start, the health
wait, and the Consul registration. The install and the image build print
their time. Their Maven output goes to the build log that the first line
names. A failed stage prints its name and the last 40 lines of that log. The
two waits print a line every 10 attempts. The last line is
`Core is ready on port 6791 and registered in Consul`.

Stop this composition with the same task:

```bash
just deploy-core-cassandra-lucene-redis --stop
```

The stop flag stops core, Redis, Cassandra, and Consul in that order. It takes
no other flag. It uses their current Compose container names. It also stops
Consul when other services use that registry. Docker keeps the Cassandra and Lucene named volumes.
The launcher command with `--dry-run` prints the resolved module, selectors,
and image. It starts no service. Use `--key-type uuid` or `--uuid` to select the
UUID Cassandra schema. The keyspace follows the key type in the plan.
Use `--index cassandra --pubsub memory` for the original Cassandra composition.

### Authorization server container

Create an authorization signing key once:

```bash
just generate-authserv-jwk
```

For an existing JDBC database, complete [the Flyway baseline](../docs/AUTHSERV-JDBC-MIGRATION.md) first.

Set `CHAT_SERVICE_PASSWORD`, `SPRING_DATASOURCE_USERNAME`, and
`SPRING_DATASOURCE_PASSWORD`. Start the authserv container with:

```bash
just deploy-authserv jwk=encrypt-keys/authserv.jwk --key-type long
```

The recipe checks core health and its passing RSocket Consul registration.
It starts Postgres and waits for database readiness. It builds the authserv
image and starts the container on `${CHAT_NETWORK:-docker_chatnet}`. Compose mounts the JWK file read-only at
`/run/secrets/authserv.jwk`. The recipe requires the current user to own the
JWK file. The container runs with that user's UID so it can read the key.
The image starts with the `jdbc` and `client-init` Spring profiles.
Compose does not publish the Postgres host port. A host-run authserv needs a reachable PostgreSQL URL.
The host Consul check uses `CONSUL_HOST` and `CONSUL_PORT`.
The image uses `consul:8500` on `CHAT_NETWORK` by default.
Set `AUTHSERV_CONTAINER_CONSUL_HOST` or `AUTHSERV_CONTAINER_CONSUL_PORT` when that network uses another Consul name or port.
The selected Consul name must resolve from the authserv container.
The recipe prints its current phase and sends a progress update every ten
seconds while it waits for Postgres or authserv health.

### Start order and gates

1. The launcher checks the provider choices and password.
2. It checks `CONSUL_HOST:CONSUL_PORT` for a leader. The defaults are `127.0.0.1:8500`.
3. If local Consul is absent, it starts the existing Compose `consul` service.
4. It checks the Docker network, Cassandra keyspace, and Redis `PONG` when selected.
5. It asks `chat-build --dry-run` to resolve the module and provider flags.
6. It installs reactor dependencies, then builds the selected image through `chat-build`.
7. It starts the core Compose service and waits for Actuator health and passing Consul registration.

An unavailable configured remote Consul address stops the launch. The launcher
does not start another registry for that address. Its `--dry-run` skips
Consul, data services, image creation, and container startup.

The Lucene Compose overlay mounts a named volume at `/var/lib/chat/lucene`.
It sets `app.index.lucene.root` through `APP_INDEX_LUCENE_ROOT`. An init
container makes the volume writable by the image user. The overlay limits
core memory to 3 GiB by default. Set `CORE_MEMORY_LIMIT` to change the limit.
It reserves 256 MiB of direct memory for Netty. The other compositions do
not use this overlay.

The core container joins `${CHAT_NETWORK:-docker_chatnet}`. Locally started
Consul uses `docker_chatnet`. An existing Consul must reach the core container
for its health check. Set `CHAT_NETWORK` for an existing Docker network.
The Compose project publishes core ports on host loopback. The defaults are
6790 for RSocket and 6791 for management. Set `CORE_PORT` and `CORE_MGMT_PORT`
to change them.

The launcher reads the image name from the selected module POM. With the
default `IMAGE_REPO_PREFIX`, memory uses
`docker.io/library/chat-deploy-memory:0.0.1`. Cassandra uses
`docker.io/library/cassandra-core-service-rsocket:0.0.1`.

The launcher uses a temporary Docker config without registry credentials for
the image build. It removes that config after the command. Compose still uses
the normal Docker config. Set `CHAT_BUILD_DOCKER_CONFIG` to a config directory
when the image build needs registry credentials.

On 2026-10-12, the mixed command started Cassandra, Redis, and a healthy core
container. Consul reported one passing core service. The launcher checks core
health and Consul registration. It does not check message reads or restart
state. The launcher still starts only core.
Authorization, REST, and gateway services need separate launches.

---

## Services

Each service mirrors one of the old `run-*.sh` scripts.

| Service | Module | Ports | Replaces |
|---|---|---|---|
| `core` | `chat-deploy` | 6790 rsocket, 6791 mgmt | `run-core.sh` |
| `rest` | `chat-deploy` | 6792 http, 6793 mgmt | `run-rest.sh` |
| `gateway` | `chat-deploy` | 80 http, 8080 mgmt | `run-gateway.sh` |
| `authserv` | `chat-authorization-server` | 9000 http, 9001 mgmt | `run-authserv.sh` |
| `shell` | `chat-deploy` | 9101 mgmt | `run-shell.sh` |

`chat-build <service> --help` lists exactly the features that service accepts.

---

## Features

Features are composable. Each one maps to Maven profiles, Spring properties and
JVM flags. Options within a group are mutually exclusive.

### Backends (pick one)

| Flag | Module or Maven profile | Notes |
|---|---|---|
| `--memory` | module `chat-deploy-memory` | Memory persistence + pubsub + Lucene index. Default for `core`. |
| `--cassandra` | module `chat-deploy-cassandra` | Cassandra keys, persistence, and secrets. The default index is Cassandra. |
| `--kafka` | module `chat-deploy-kafka` | Kafka messaging with memory persistence + Lucene index. Reads `KAFKA_BOOTSTRAP_SERVERS`. |
| `--redis` | module `chat-deploy-redis` | Redis persistence, keys and Pub/Sub, with memory secrets and the Lucene index. |
| `--client` | profile `client-backend` | RSocket client, no local datastore. Default for `rest`, `gateway`, `authserv`, `shell`. |

A store backend selects its own deploy module, and `chat-build` runs Maven in
that module directory. `--client` keeps `chat-deploy` and adds the
`client-backend` profile. `--dry-run` prints the module.

For `core --cassandra`, use `--index lucene` to select Lucene. Use
`--pubsub redis-pubsub` to select Redis pub/sub. These options keep
`app.command.bus=memory`. The default selectors remain the Cassandra index
and memory pub/sub. Other backend flags do not accept these overrides.

### Add-ons

| Flag | Maven profile | Effect |
|---|---|---|
| `--e2ee` | `e2ee` | E2EE substrate: sets `app.service.e2ee.enabled`, `app.service.crypto.backend`, `app.service.presence.backend`. |
| `--host-command` | `shell-host` | The `!` command of `shell`, in `chat-shell-host`. The profile also sets the bash run image. Default for `shell`. |

`shell` includes `--host-command` by default. Use `--no-host-command` to build
a shell without the `!` command. That build holds no `!` code, and its image
keeps the tiny run image, which holds no shell. `--host-command` together
with `--no-host-command` is refused.

`--e2ee` pulls modules that are not yet published to the local repository
(`chat-deploy-e2ee`, `chat-crypto`, `chat-presence`), as does `--kafka`
(`chat-deploy-kafka`). Run `mvn -DskipTests install` from the repo root once
before using either flag, or the single-module build cannot resolve them.

### Transport (pick one)

| Flag | Maven profile |
|---|---|
| `--rsocket` | `expose-rsocket` (default for `core`) |
| `--rest` | `expose-webflux` (default for `rest`) |
| `--shell` | `shell` (default for `shell`) |
| `--gateway` | `expose-gateway` (default for `gateway`) |

### Discovery (pick one)

| Flag | Effect |
|---|---|
| `--local` | Default. Disables Consul. A client with no store reads the root key snapshot from the actuator of a store node over HTTP. |
| `--consul` | Adds `register-consul`. A store node with the `rootkeys` phase publishes the root key snapshot to Consul KV. A client with no store reads that snapshot. Falls back to `docker inspect` to find the Consul IP if `CONSUL_HOST` is unset. |

### Key type (required)

`--long` (Snowflake) or `--uuid`. `--key-type TYPE` selects the same feature by
name. Its choices are the features of the `keytype` group. Every run and every
image build is a deployment, and a deployment names its key type. No key type
is a default. `chat-build` exits 2 without one.

Key types are rare and stable. A new key type needs its keyspace, its
integration code, and its own acceptance tests. The `--key-type` option does
not make one cheaper to add.

### Other

| Flag | Effect |
|---|---|
| `--tls DIR` | Enable TLS with certificates from `DIR`. Requires `KEYSTORE_PASS`. |
| `--notls` | Disable TLS. |
| `--websocket` | WebSocket transport for RSocket. |
| `--debug` | RSocket frame logging plus a JDWP agent on `DEBUG_PORT`. |
| `--init PHASES` | Comma-separated: `users`, `rootkeys`. |
| `--profile NAME` | Additional Spring profile. Repeatable. |
| `--run` / `--build` / `--agent-json` | `spring-boot:run` (default), `spring-boot:build-image`, or print the agent launch JSON and exit. |
| `--image-manifest PATH` | Record the image after a successful `--build`. |
| `--bake` | Pass options via the env file rather than inheriting them. |
| `--env-file PATH` | Where to write the env file. |
| `--dry-run` | Print everything, execute nothing. |
| `--native` | Rejected — GraalVM native builds are still unsupported. |
| `--jwk PATH` | The JWK file that the deployment trusts. Absolute. Required with `--agent` on `core` and `rest`, and to run `authserv`. |
| `--agent CLIENT_ID=HANDLE` | One agent. Repeat it for each agent. See below. |
| `--agent-jwks-uri CLIENT_ID=HTTPS_URI` | Authserv reads this public JWKS for `private_key_jwt`. Repeat for each agent that uses a key. |
| `--agent-scope SCOPE` | The one scope of every agent token. Defaults to `chat.mcp`. |
| `--agent-json` | `authserv` only. Print the agent launch JSON and exit. See below. |
| `--index-root DIR` | `core` only. Keep the Lucene indexes in files under `DIR`. See below. |
| `--vector embedded` | Select the embedded vector store for `core --cassandra`. Requires `--embedding openai`. |
| `--embedding openai` | Select the OpenAI-compatible provider for `core --cassandra`. Requires `--vector embedded`. |

**Agents.** `--agent` names one OAuth client and the chat user that it acts as.
The token `client_id` selects the agent. See `CHAT-frcrctdp`.

| Service | Emitted properties |
|---|---|
| `core` | `app.init.initial-users[<H>].{handle,name,image-uri}`, `app.security.agents[i].{client-id,username}`, `app.security.required-scope`, `app.security.jwt.jwk-path` |
| `rest` | `app.security.agents[i].{client-id,username}`, `app.security.required-scope`, `app.security.jwt.jwk-path` |
| `authserv` | `app.oauth2.agents[i].{client-id,username,jwk-set-uri}`, `app.oauth2.agent-scope` |

The core creates each agent user only when the launch runs the `users` init
phase. Pass the same `--agent` list to the core and to REST. No check compares
the two lists.

`chat-build` refuses these values, with exit 1:

- A value that is not `CLIENT_ID=HANDLE`.
- A handle that holds a character other than a letter, a digit or `_`.
- The handle `Admin`, `Anon` or `Service`, in any case.
- A client id or a handle that two `--agent` values share. Handles compare
  without case.
- `--agent` on a service other than `core`, `rest` or `authserv`.
- `--agent` on `core` or `rest` without `--jwk`.
- `--agent` or `--agent-jwks-uri` with `authserv --build`. See below.

The removed flags `--agent-client-id` and `--agent-username` exit 2 and name
`--agent`.

**An authserv image holds no agent.** An agent mapping names a runtime
identity. `--build` writes every emitted property into the image environment,
so one image would serve one agent set. `authserv --build` therefore refuses
`--agent` and `--agent-jwks-uri` with exit 1. See `CHAT-vidnpywh`.

`--agent-json` prints the mapping instead. The deployment passes that value at
launch:

```sh
agents_json="$(./shell-scripts/chat-build authserv --agent-json \
  --notls --node-id 8 --key-type long \
  --agent 'CLIENT_ID=HANDLE' --agent-jwks-uri 'CLIENT_ID=HTTPS_URI')" \
  && export AUTHSERV_AGENTS_JSON="$agents_json"
just deploy-authserv "$PWD/encrypt-keys/authserv.jwk" --key-type long
```

The export runs only when `chat-build` succeeds. `export VAR="$(command)"`
reports the status of `export`, so it hides a failed command.

The recipe passes `AUTHSERV_AGENTS_JSON` to the container as
`SPRING_APPLICATION_JSON`. That source ranks above the system properties that
an image bakes, and a launch list replaces a baked list. An unset variable
gives an empty value, which Spring Boot ignores.

**The recipe serves a first deployment alone.** It exits with status 0 when
authserv already answers its health endpoint, and it rebuilds the image
otherwise.

A recreate of the container adds a new agent. It also changes the JWKS URI of
an agent that the store already holds, because the `client-init` reconciliation
updates the stored client when the URI is its only difference. The start prints
the old URI and the new one.

A recreate does not repair any other drift, and it does not withdraw an agent
that leaves the mapping. Neither case has an app function yet. `CHAT-vepxpmlk`
holds that gap, under the audit in `CHAT-tkabpfxm`. Ask the repository owner
before you repair either case. See `docs/MCP-CREDENTIAL-ISSUANCE.md`, under
`What a recreate can and cannot change`.

`--agent-json` joins the goal group, so it cannot combine with `--run` or
`--build`. It refuses a service other than `authserv` with exit 1, and it
refuses an empty agent list with exit 1.

`core` and `rest` keep their `--build` behavior and still bake their mapping.
They bind `app.security.agents` and `app.init.initial-users`, so their move
needs its own design.

**Lucene index files.** `--index-root DIR` sets `app.index.lucene.root`.
Without it, the Lucene indexes stay in memory. `chat-build` expands `~`, then
requires an absolute path. The directory can be absent, because the index start
creates it. See `docs/LUCENE-INDEX-FILES.md` and `CHAT-eesnvnad`.

The files help `--redis` and `--cassandra --index lucene`, because their stores
survive a restart. With `--memory` or `--kafka`, the store starts empty. So
each start builds the indexes again from an empty store.

`chat-build` refuses these values, with exit 1:

- An empty value, such as `--index-root=`.
- A relative path.
- A service other than `core`.
- `--cassandra` without `--index lucene`. That selection uses the Cassandra index.
- `--build`. A host path does not exist inside a container. Mount a volume
  and set `app.index.lucene.root` when you run the image.

**Root keys.** Each service has one root key role. `RootKeySource` in
`chat-deploy` refuses any other combination at start.

| Role | Services | Local discovery | Consul discovery |
|---|---|---|---|
| `store` | `core` | loads or creates each root in its store | the same, and the `rootkeys` phase publishes the snapshot to Consul KV |
| `consume` | `rest`, `gateway`, `shell` | reads the snapshot over HTTP | reads the snapshot from Consul KV |
| `none` | `authserv` | sets `app.rootkeys.required=false` | sets `app.rootkeys.required=false` |

A client under Consul discovery needs a store node that ran the `rootkeys`
phase. Without a published snapshot, the client fails its start.

**TLS is fail-closed.** You must pass `--tls <dir>` or `--notls`; there is no
implicit default, as `build-app.sh` required before it was removed.

---

## Migration

The shell scripts below no longer exist. The table is kept for anyone working
from muscle memory, an old runbook, or a branch that predates their removal.

| Old | New |
|---|---|
| `run-core.sh runlocal memory -c notls` | `chat-build core --memory --run --notls --node-id 0 --init users,rootkeys --key-type long` |
| `run-core.sh runlocal cassandra -c notls` | `chat-build core --cassandra --run --notls --node-id 0 --init users,rootkeys --key-type long` |
| `run-core.sh runlocal kafka` | `chat-build core --kafka --run --notls --node-id 0 --key-type long` |
| `run-core.sh build memory -c notls` | `chat-build core --memory --build --notls --node-id 0 --key-type long` |
| `run-rest.sh local` | `chat-build rest --run --notls --node-id 0 --key-type long` |
| `run-gateway.sh local` | `chat-build gateway --run --notls --node-id 0 --key-type long` |
| `run-authserv.sh local` | `chat-build authserv --run --notls --node-id 0 --key-type long` |
| `run-shell.sh local` | `chat-build shell --run --notls --node-id 0 --key-type long` |
| `build-app.sh ... -x` | `chat-build ... --dry-run` |
| `-g` | `--debug` |
| `-w` | `--websocket` |
| `-k long` / `-k uuid` | `--long` / `--uuid` |
| `-i users,rootkeys` | `--init users,rootkeys` |
| `-p prod` | Implied by the backend; `--profile` adds extras. |
| `-o` | `--bake` |
| `-s` / `-e` / `-d` / `-m` | Implied by the service and its features. |

Two old invocations have no direct equivalent because they were broken:
`run-rest.sh local` and `run-gateway.sh local` never passed `-s`, so
`build-app.sh` exited with "you forgot to select a backend". `chat-build` gives
both services a `client` backend by default.

---

## Ports

Everything derives from `CORE_PORT` (default 6790), matching `ports.sh`.

| Variable | Derivation | Default |
|---|---|---|
| `CORE_PORT` | — | 6790 |
| `CORE_MGMT_PORT` | `CORE_PORT + 1` | 6791 |
| `HTTP_PORT` | `CORE_PORT + 2` | 6792 |
| `HTTP_MGMT_PORT` | `HTTP_PORT + 1` | 6793 |
| `AUTHSERV_HTTP_PORT` | — | 9000 |
| `AUTHSERV_MGMT_PORT` | — | 9001 |
| `GATEWAY_HTTP_PORT` | — | 80 |
| `GATEWAY_HTTP_MGMT_PORT` | — | 8080 |
| `SHELL_MGMT_PORT` | — | 9101 |
| `DEBUG_PORT` | — | 9060 |

Any of these can be overridden in the environment. Setting `CORE_PORT=7000`
moves the whole core/rest allocation.

---

## Environment variables

| Variable | Used for |
|---|---|
| `KEYSTORE_PASS` | Required with `--tls`. |
| `CONSUL_HOST` / `CONSUL_PORT` | Consul location. Host is auto-detected from a running container if unset. |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka brokers. Defaults to `localhost:9092`. |
| `ROOTKEY_SOURCE_URI` | Peer to fetch root keys from. Defaults to `http://$CORE_HOST:$CORE_MGMT_PORT`. |
| `DEBUG_SUSPEND` | `y` to have the JDWP agent wait for a debugger. Defaults to `n`. |
| `CHAT_IMAGE_MANIFEST` | Default path for `--image-manifest`. Used only with `--build`. |

---

## The env file

Written to `/tmp/chat-build-<service>-<image>.env` unless `--env-file` says
otherwise, as real `KEY=VALUE` pairs:

```
JAVA_TOOL_OPTIONS=...
MAVEN_PROFILES=-Pdeploy,memory-backend,expose-rsocket
MAVEN_GOAL=spring-boot:run
CWD=/path/to/chat-deploy
IMAGE_NAME=memory-core-service-rsocket
```

`build-app.sh` wrote literal `echo KEY = VALUE` lines here, which
`docker --env-file` cannot parse. If anything downstream depended on the old
shape, it needs updating.

---

## Flag testing

```bash
./shell-scripts/test-flags.sh                  # check every case
./shell-scripts/test-flags.sh core-memory-tls  # check one case
./shell-scripts/test-flags.sh --update         # rewrite goldens, then read the diff
```

Runs `chat-build --dry-run` over each case in `test-flags.sh`, and compares the
flag set and Maven profiles against committed expectations under `golden/`.
Every backend, every service, and the variants that change flag composition.
Each refusal case must exit with its code and print its text. The run sets
`HOME` to `/home/golden`, so a golden that expands `~` is the same on every
machine.

Comparison ignores three non-semantic differences: shell quoting that never
needed to reach the JVM, `-P` ordering, and repeated entries inside comma-lists.

The goldens were seeded while `test-parity.sh` still existed and passed, so the
four cases it covered carry the old scripts' authority. The other cases are
snapshots of chat-build's own output — they catch change, but nothing
independent vouches for them. The case list in `test-flags.sh` marks which is
which.

`--update` exists, and a diff it produces is a bug until you can say why it is
not. Committing a regenerated golden without that explanation turns the harness
into a rubber stamp.

---

## Adding a feature

Add one `Feature` to the `FEATURES` dict in `chat-build`, then list its name in
the `available_features` of every service that should accept it:

```python
"myfeature": Feature(
    name="myfeature",
    description="What it does",
    maven_profiles=["my-profile"],
    spring_properties={"app.service.mine.enabled": "true"},
    service_flags=["-Dapp.service.mine"],
    group=None,          # or "backend"/"expose"/"discovery"/"keytype"
    conflicts=["other"],
),
```

Set `removable=True` on a feature in the `default_features` of a service to
give that service a `--no-<name>` flag. `host-command` is the example.

If the feature needs code on the classpath, it also needs a matching profile in
`chat-deploy/pom.xml` that declares the dependency — see `kafka-backend` and
`e2ee` for the pattern. A feature that only sets properties for classes that
aren't packaged will silently do nothing.
