# Deployment Workflow

The order of work from a fresh checkout to a running deployment.

`docs/BUILD.md` is the command reference. This file is the order those
commands go in, and the inputs that have no default.

**Read the measured marks.** A step marked *measured* was run on 2026-09-22 at
master `a63284f0`. A step marked *not measured* is written from the code and
the configuration, and nobody has run it end to end. Do not quote an unmeasured
step as proof.

---

## Step 0 — the machine

| Need | Why |
|---|---|
| JDK 25 | `java.version` is 25. A JDK 17 toolchain cannot compile at release 25. |
| Docker Engine | Every backend, and both image paths. Measured against 29.7.2. |
| Maven | The build system. `just` is a menu over it, not a replacement. |
| `openssl`, `keytool`, `jq`, `eckles` | Only for TLS material. See step 3. |

Boot 4 reads `~/.docker/config.json` before it pulls a builder image. A config
holding a `credsStore` beside empty `auths` entries fails an image build with
`'username' must not be null`, and the message names no registry. Point
`DOCKER_CONFIG` at a directory holding `{}` to work around it. This is a
property of the machine, not of this repository.

## Step 1 — prove the tree before you deploy from it

```bash
just check          # offline, test phase
just check-ci       # builds the shell test image, then runs every test
```

`just check-ci` is the honest gate. `--integration` stops at the test phase, so
it runs the `chat-shell` container tests against whatever image the machine
already holds.

**Neither gate builds a deployment image.** That path is exercised only by step
5, and two defects have already been found there that no gate could see. See
the one-time notes in `docs/BUILD-HEALTH.md`.

*Measured: both modes exit 0.*

## Step 2 — choose the composition

A deployment is a set of selectors, not a build. One classpath carries every
provider, and the launch chooses.

| Choice | Flag | Notes |
|---|---|---|
| Backend | `--memory`, `--redis`, `--cassandra`, `--kafka` | One per launch |
| Key type | `--long`, `--uuid` | `long` is the Snowflake generator |
| Transport | `--rsocket`, `--rest`, `--websocket` | `rsocket` is the default |
| Discovery | `--local`, `--consul` | `local` is the default |

`./shell-scripts/chat-build core --help` lists every flag, and `--dry-run`
prints the resolved command without running it. Read the resolved command
before the first launch of a composition you have not used.

## Step 3 — the inputs that have no default

**This step is why a deployment fails on a fresh machine.** Each value below is
required, and none has a default, on purpose.

### The node id, always

```bash
--node-id <0..1023>
```

It identifies one host in the Snowflake key generator. Two deployments that
write to one shared store must not state the same value. A committed default
would make every deployment the same node in silence.

A kafka deployment also derives its consumer group from it, so a duplicate
there makes two instances share one group and one of them reads nothing. See
`docs/NODEID-CLAIM.md`.

### A signing key, for the authorization server

```bash
--jwk /absolute/path/to/key.jwk
```

`AuthorizationServerConfig` reads an ES256 key in JWK form and signs tokens
with it. The repository commits no key. `docs/BUILD.md` carries the command
that makes one.

**`shell-scripts/gen-dckeys.sh` makes this key**, as
`encrypt-keys/server_keycert.jwk`, alongside the TLS material. See step 3 TLS
below. Use the command in `docs/BUILD.md` instead when you want a key and no
TLS material.

### TLS material, only when you use `--tls`

```bash
./shell-scripts/gen-dckeys.sh <password>
export KEYSTORE_PASS=<password>
./shell-scripts/chat-build core --memory --run --tls ./encrypt-keys --node-id 0
```

That script builds one authority, a server identity and a client identity, and
writes them to `encrypt-keys/`. Its own header lists every file.

**It also writes `server_keycert.jwk`, which is the signing key step 3 asks
for.** So one run of this script covers both the TLS material and the
authorization server key, and the same file reaches a Kubernetes secret and a
docker volume. *Measured: the server started with it and minted a token whose
header reads `{"alg":"ES256"}`.*

Keep the output in `encrypt-keys/`, which is in `.gitignore`. A private key
under a tracked directory is one `git add` away from a commit.

Use `--notls` when you are not testing the TLS path. You still need a signing
key for the authorization server, and `docs/BUILD.md` carries a command that
makes one without any TLS material.

### An image repository prefix, only for `--build`

```bash
export IMAGE_REPO_PREFIX=docker.io/library
```

`shell-scripts/build.sh` defaults this. `chat-build` does not, so
`chat-build ... --build` fails without it. `CHAT-gkwqnnxn` holds that gap.

## Step 4 — start the backend the composition names

| Backend | What it needs |
|---|---|
| memory | nothing |
| redis | a Redis reachable at the configured address |
| cassandra | a Cassandra, **plus the keyspace applied** |
| kafka | a broker reachable at `KAFKA_BOOTSTRAP_SERVERS` |
| authorization server | a Postgres. `chat-authorization-server/docker-compose.yaml` starts one |

Cassandra needs its schema before the application starts, because the node id
claim writes to `node_claim` in the keyspace:

```bash
docker exec <container> cqlsh -f keyspace-long.cql
```

The file is `shared-resources-cassandra/src/main/resources/keyspace-long.cql`,
and `keyspace-uuid.cql` beside it for the other key type.

`chat-build` supplies the cassandra contact points from
`CASSANDRA_CONTACT_POINTS` and `CASSANDRA_PORT`, defaulting to `127.0.0.1` and
`9042`. The kafka feature reads `KAFKA_BOOTSTRAP_SERVERS` the same way.

*Measured for redis, cassandra, kafka and postgres.*

## Step 5 — launch

### Run on this machine

```bash
./shell-scripts/chat-build core --cassandra --run --notls --node-id 7
./shell-scripts/chat-build authserv --run --notls --node-id 8 --jwk /tmp/authserv.jwk
```

`just launch-memory 0` and `just launch-shell 1` are shorthands for the two
common cases.

A start looks like this, and both lines matter:

```
app.nodeid=7 claimed in cassandra keyspace chat_long
Started ChatApp.Companion in 3.172 seconds
```

The claim line appears only for a backend that claims. A deployment on memory
stores claims nothing.

*Measured: all five composition roots start this way.*

### Build an image instead

```bash
export IMAGE_REPO_PREFIX=docker.io/library
./shell-scripts/chat-build core --cassandra --build --notls --node-id 7
```

The image bakes the launch options, so a value that names a path or a host on
your machine is wrong inside a container. `chat-build` refuses `--jwk` with
`--build` for that reason. The cassandra contact point default of `127.0.0.1`
names the container itself, so override it through the environment variable
before you build, or supply the property when you run the image.

*Measured: the cassandra image builds and starts. No other backend image was
built or run.*

## Step 6 — read the result

| Signal | Meaning |
|---|---|
| `Started ChatApp.Companion in N seconds` | the context refreshed and the servers bound |
| `claimed in <store>` | the node id lease was taken |
| `Netty RSocket started on port` | the RSocket transport is listening |
| `Tomcat started on port` | the authorization server is listening |

An `APPLICATION FAILED TO START` block names the missing bean or property.
Read it before changing anything, because the two failures this repository met
most often were a missing property and a bean that only a test supplied.

---

## What this document does not cover

- **Consul discovery.** `--consul` exists and this order of work has not been
  run with it. `shell-scripts/run-consul-in-docker.sh` starts one.
- **A real cluster.** Every measurement here is one machine, with backends in
  local containers.
- **The native image.** `--native` is refused today. `CHAT-arcqfjuc` holds the
  reason, and `docs/NATIVE-IMAGE.md` the detail.
- **Vector recall and embeddings.** No deployment yml sets those selectors.
  `docs/EMBEDDING-PROVIDERS.md` carries that path.
- **Four backend images.** Only the cassandra deployment image has been built
  and started.
