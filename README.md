# Demo Chat

**Code name: ImpactDriver.**

Demo Chat is a substrate for message flows with chat-room semantics. It holds
users, rooms, memberships and messages. It gives identities to people and to
agents, and it checks access between them. Its persistence, index, messaging
and key backends are pluggable. You can also use it as a plain chat
application between people.

The code is Kotlin on Spring Boot. RSocket is the main transport, and REST is
also available. The project also teaches 12-factor services, test-driven
development and iterative deployment.

## Requirements

| Need | Version or note |
|---|---|
| JDK | 25. The build compiles at release 25. |
| Kotlin | 2.4.20, through Maven. |
| Spring Boot | 4.0.8. |
| Maven | The build system. |
| `just` | The command menu over Maven and the scripts. |
| Docker Engine | For the container tests and the backend images. Measured against 29.7.2. |

## Build and run

Start with `docs/BUILD.md`. It is the command reference. `docs/DEPLOYMENT-WORKFLOW.md`
gives the order of the steps, from a fresh machine to a running deployment.

Show the command menu:

```bash
just --list
```

Check the build:

```bash
just check          # offline, test phase
just check-ci       # builds the shell test image, then runs every test
```

Start a core service with memory storage, and then the shell client:

```bash
just launch-memory 0
just launch-shell 1
```

Each launch needs its own node id. `chat-build --node-id` is the normal launch
path, and the argument is required. See `Rules that matter`.

Use `shell-scripts/` for the implementation scripts and the advanced launch
details. `shell-scripts/README-chat-build.md` describes `chat-build`.

## How it fits together

Each module has one role. `docs/ARCHITECTURE.md` has the full map and its
diagram.

| Role | Modules |
|---|---|
| Domain and contracts | `chat-core` |
| Composition | `chat-service-composite` |
| Security | `chat-security`, `chat-authorization-server` |
| Server transport | `chat-service-controller`, `chat-webflux`, `chat-web`, `chat-gateway` |
| Clients | `chat-client-rsocket`, `chat-client-consul`, `chat-shell`, `chat-mcp` |
| Backend adapters | `chat-persistence-*`, `chat-index-*`, `chat-messaging-*`, `chat-vector-*`, `chat-embedding-*` |
| Deployment | `chat-deploy*`, `shared-*`, `devops/`, `shell-scripts/` |

### Capability selectors

A launch selects one provider for each capability. One classpath carries every
provider.

- An unset `key`, `persistence`, `pubsub` or `secrets` selector selects
  `memory` today.
- The capability design requires a startup failure for an unset or unknown
  selector. The plan that enforces it is not started. See `forward-register.md`,
  `Planned, not started`.
- `VectorSelectorValidation` checks the `vector` and `embedding` pair at
  startup.

| Selector | Values |
|---|---|
| `app.service.core.key` | `memory`, `redis`, `cassandra` |
| `app.service.core.persistence` | `memory`, `redis`, `cassandra` |
| `app.service.core.index` | `lucene`, `cassandra` |
| `app.service.core.pubsub` | `memory`, `redis-pubsub`, `redis-xstream`, `kafka` |
| `app.service.core.secrets` | `memory`, `cassandra` |
| `app.service.core.vector` | `simple`, `redis`, `embedded`, `mock` |
| `app.service.core.embedding` | `local`, `openai`, `mock` |
| `app.key.type` | `long`, `uuid` |

The design is in `docs/superpowers/specs/2026-08-24-capability-composition-design.md`.

## Modules

The reactor holds 36 modules.

| Module | Purpose |
|---|---|
| `chat-core` | Domain types, the core service interfaces, codecs and shared test bases |
| `chat-service-composite` | User, topic and message use cases, and the memory command bus |
| `chat-security` | Authentication, access checks and grant policies |
| `chat-authorization-server` | The OAuth2 authorization server |
| `chat-service-controller` | RSocket controllers over the services |
| `chat-webflux` | REST endpoints |
| `chat-web` | The web UI |
| `chat-gateway` | The Spring Cloud Gateway edge |
| `chat-client-rsocket` | Typed RSocket client proxies |
| `chat-client-consul` | Configuration for Consul-bound clients |
| `chat-shell` | The interactive client, and the Claude conversation import |
| `chat-mcp` | A standalone MCP adapter over stdio |
| `chat-persistence-memory` | Memory persistence |
| `chat-persistence-redis` | Redis persistence |
| `chat-persistence-cassandra` | Reactive Cassandra persistence |
| `chat-persistence-xstream` | Redis pub/sub and Redis Stream messaging |
| `shared-resources-cassandra` | The Cassandra keyspace and truncate scripts |
| `chat-index-lucene` | Lucene indexes, in memory or in files |
| `chat-index-cassandra` | Reactive Cassandra indexes |
| `chat-messaging-memory` | Memory messaging |
| `chat-messaging-kafka` | Kafka messaging and streams |
| `chat-vector-simple` | An in-memory vector store, for local and test use |
| `chat-vector-redis` | A Redis vector store. It needs Redis Stack. |
| `chat-vector-embedded` | The embedded vector provider |
| `chat-embedding-openai` | An OpenAI-compatible embedding provider |
| `chat-embedding-local` | An in-process ONNX embedding provider |
| `chat-crypto` | End-to-end encryption services: device registry, pre-keys, relay and franking |
| `chat-presence` | Presence: heartbeat TTL, online, away and offline, typing indicators |
| `chat-deploy-e2ee` | The deployment wiring for encryption and presence |
| `shared-deploy-configuration` | Shared configuration properties |
| `chat-deploy` | The base deployment configuration and startup routines |
| `chat-deploy-memory` | The memory deployment |
| `chat-deploy-redis` | The Redis deployment |
| `chat-deploy-cassandra` | The Cassandra deployment |
| `chat-deploy-kafka` | The Kafka messaging deployment |
| `chat-deploy-memory-integration-test` | The container image for the shell integration tests |

## Clients

- **Shell.** `just launch-shell` starts the interactive client. It also
  imports Claude conversation exports into topics. See
  `docs/SHELL-COMMAND-CONTRACT.md`.
- **MCP adapter.** `chat-mcp` speaks the Model Context Protocol over stdin and
  stdout. It reads topics and messages from a running deployment. It sends
  messages only when `enableSend=true`. It holds no store credential. See
  `docs/MCP-ADAPTER.md`.
- **RSocket and REST.** `chat-client-rsocket` gives typed RSocket proxies.
  `chat-webflux` gives the REST routes. See `docs/REST-TOKEN-RELAY.md`.

## Rules that matter

- **`app.nodeid` has no default.** It takes an integer from 0 to 1023. A
  store-side lease keeps each node id unique on a shared backend. See
  `docs/NODEID-CLAIM.md`.
- **Each store service has at most one current lease holder on a shared
  backend.** A second core on the same Redis or Cassandra store fails at
  start. A lease cannot prove that an old process stopped. A safe swap needs
  confirmed termination of the old process. See `docs/SERVICE-LEASE.md`.
- **Split deployments are not supported.** Run each store service in one
  core deployment.
- **A message send is a captured command.** The Stage 1 command bus is
  `memory`, and it runs in one process. A restart loses unresolved commands.
  See `docs/MESSAGE-COMMANDS.md`.
- **No store migration exists.** A Cassandra keyspace from before a schema
  change fails its start. Recreate it from the scripts in
  `shared-resources-cassandra`.

## Documents

| Document | Subject |
|---|---|
| `docs/BUILD.md` | Build and launch commands |
| `docs/DEPLOYMENT-WORKFLOW.md` | From a fresh machine to a running deployment |
| `docs/BUILD-HEALTH.md` | The measured build state and the known traps |
| `docs/ARCHITECTURE.md` | The module map |
| `docs/NODEID-CLAIM.md` | The node id lease |
| `docs/SERVICE-LEASE.md` | The store service lease |
| `docs/MESSAGE-COMMANDS.md` | The message command bus |
| `docs/KEY-ROOT-IDENTITY.md` | Key roots and identity |
| `docs/ANONYMOUS-AUTHORIZATION.md`, `docs/IDENTITY-POLICY.md` | Access rules |
| `docs/LUCENE-INDEX-FILES.md` | Lucene index files |
| `docs/VECTOR-RECALL-API.md`, `docs/EMBEDDING-PROVIDERS.md` | Vector recall and embeddings |
| `docs/MCP-ADAPTER.md` | The MCP adapter |
| `docs/NATIVE-IMAGE.md` | Native image notes |

## Status

Measured on 2026-10-16 at the head of PR #244: the default gate ran 2274 tests
and the `--ci` gate ran 2693 tests in 30 modules, with 0 failures. See
`docs/BUILD-HEALTH.md` for the current reading.

`forward-register.md` holds the current project state and the open threads.
The project tracks work in `fp`. Run `fp issue list --status in-progress` for
the active issues.

### Direction

The owner's goals for agent chat, with the state in this repository:

| Goal | State |
|---|---|
| Agent to agent chatter | The RSocket and REST APIs and the clients exist. |
| Integrate a client into MCP | Done for Stage 1 messaging: read tools, and optional send. |
| Agent to human chatter | Open. |
| A chat room bridged to another chat platform | Open. |
| Integrate a client into dagdir | Open. No work is recorded here. |
| A command-line client for one-shot tasks | Open. |
| Hourly token refresh | Open requirement. |
| A client sees its own public and private key, and never the chat private key | Open requirement. |
| Authentication through `chat-authorization-server` | The server deploys in Docker. See `just deploy-authserv`. |
