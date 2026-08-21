# chat-build

A single Python CLI that composes the Maven command for any demo-chat service from
declarative feature flags. It replaces the flag assembly spread across
`build-app.sh` and the five `run-*.sh` scripts.

Python 3.10+, stdlib only. No virtualenv, no `pip install`.

```bash
./shell-scripts/chat-build core --memory --run --notls
# or via the wrapper
./shell-scripts/run core --memory --run --notls
```

The old shell scripts still work and are not deleted. `test-parity.sh` asserts the
two paths produce the same JVM flags.

---

## Quick start

```bash
# Core RSocket service, in-memory backend, seeding users and root keys
chat-build core --memory --run --notls --init users,rootkeys

# Same, but Cassandra-backed with UUID keys
chat-build core --cassandra --run --notls --uuid --init users,rootkeys

# Kafka backend (Kafka messaging, memory persistence, Lucene index)
chat-build core --kafka --run --notls

# REST facade (talks to core over RSocket)
chat-build rest --run --notls

# Interactive shell client
chat-build shell --run --notls

# Build a container image instead of running
chat-build core --cassandra --build --notls

# See what would happen, run nothing
chat-build core --memory --run --notls --dry-run
```

`--dry-run` prints the resolved feature set, the Maven command, every
`JAVA_TOOL_OPTIONS` flag one per line, and the env file path. It is the fastest
way to answer "why is this property set".

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

| Flag | Maven profile | Notes |
|---|---|---|
| `--memory` | `memory-backend` | Memory persistence + pubsub + Lucene index. Default for `core`. |
| `--cassandra` | `cassandra-backend` | Cassandra persistence + index. |
| `--kafka` | `kafka-backend` | Kafka messaging with memory persistence + Lucene index. Reads `KAFKA_BOOTSTRAP_SERVERS`. |
| `--client` | `client-backend` | RSocket client, no local datastore. Default for `rest`, `gateway`, `authserv`, `shell`. |
| `--redis` | `redis-backend` | Placeholder — the profile exists but declares no dependencies yet. |

The module never changes. Backends are `chat-deploy` profiles that pull the
matching deploy module in as a dependency, so `--cassandra` still builds
`chat-deploy` with `chat-deploy-cassandra` on the classpath.

### Add-ons

| Flag | Maven profile | Effect |
|---|---|---|
| `--e2ee` | `e2ee` | E2EE substrate: sets `app.service.e2ee.enabled`, `app.service.crypto.backend`, `app.service.presence.backend`. |

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
| `--local` | Default. Disables Consul. When `rootkeys` is *not* an init phase, adds the HTTP root-key consume chain so a joining node pulls keys off a peer's actuator. |
| `--consul` | Adds `register-consul`. When `rootkeys` *is* an init phase, publishes keys to Consul KV instead. Falls back to `docker inspect` to find the Consul IP if `CONSUL_HOST` is unset. |

### Key type (pick one)

`--long` (Snowflake, default) or `--uuid`.

### Other

| Flag | Effect |
|---|---|
| `--tls DIR` | Enable TLS with certificates from `DIR`. Requires `KEYSTORE_PASS`. |
| `--notls` | Disable TLS. |
| `--websocket` | WebSocket transport for RSocket. |
| `--debug` | RSocket frame logging plus a JDWP agent on `DEBUG_PORT`. |
| `--init PHASES` | Comma-separated: `users`, `rootkeys`. |
| `--profile NAME` | Additional Spring profile. Repeatable. |
| `--run` / `--build` | `spring-boot:run` (default) or `spring-boot:build-image`. |
| `--bake` | Pass options via the env file rather than inheriting them. |
| `--env-file PATH` | Where to write the env file. |
| `--dry-run` | Print everything, execute nothing. |
| `--native` | Rejected — GraalVM native builds are still unsupported. |

**TLS is fail-closed.** You must pass `--tls <dir>` or `--notls`; there is no
implicit default, matching `build-app.sh`.

---

## Migration

| Old | New |
|---|---|
| `run-core.sh runlocal memory -c notls` | `chat-build core --memory --run --notls --init users,rootkeys` |
| `run-core.sh runlocal cassandra -c notls` | `chat-build core --cassandra --run --notls --init users,rootkeys` |
| `run-core.sh runlocal kafka` | `chat-build core --kafka --run --notls` |
| `run-core.sh build memory -c notls` | `chat-build core --memory --build --notls` |
| `run-rest.sh local` | `chat-build rest --run --notls` |
| `run-gateway.sh local` | `chat-build gateway --run --notls` |
| `run-authserv.sh local` | `chat-build authserv --run --notls` |
| `run-shell.sh local` | `chat-build shell --run --notls` |
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

## Parity testing

```bash
./shell-scripts/test-parity.sh     # -v to dump both flag sets
```

Runs `build-app.sh -x` and `chat-build --dry-run` over four configurations
(core/memory/local, core/memory/consul, core/memory/TLS, shell/client/local) and
diffs the flag sets and Maven profiles. Any undeclared difference fails the run.

Comparison ignores three non-semantic differences: shell quoting that never
needed to reach the JVM, `-P` ordering, and repeated entries inside comma-lists.

Two intentional behavioural differences are declared in the harness:

1. `build-app.sh` emits `client-<proto>-<discovery>.yml` twice for client
   services. `chat-build` emits it once.
2. `build-app.sh` overwrites `MAIN_FLAGS` after sourcing the run script, so
   `run-shell.sh`'s `spring.shell.interactive.enabled` and
   `web-application-type=reactive` never reach the JVM. `chat-build` carries
   them through.

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

If the feature needs code on the classpath, it also needs a matching profile in
`chat-deploy/pom.xml` that declares the dependency — see `kafka-backend` and
`e2ee` for the pattern. A feature that only sets properties for classes that
aren't packaged will silently do nothing.
