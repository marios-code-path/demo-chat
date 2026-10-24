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

A backend flag selects a deploy module. Each module carries a fixed set of
providers. Launch flags select providers from that module. The approved
[capability composition design](superpowers/specs/2026-08-24-capability-composition-design.md)
would allow one artifact to carry all providers. That design is not implemented.

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
./shell-scripts/chat-build core --memory --run --tls ./encrypt-keys --node-id 0 --key-type long
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
export IMAGE_REPO_PREFIX=registry.example.invalid/chat
```

Replace the example prefix with your registry path before a build or push.

**This variable is optional.** The parent pom defaults `image.repo` to
`docker.io/library`, and the `image-repo-from-env` profile takes this value
when you set one. Set it only to name a different registry.

The POMs for `chat-deploy`, `chat-deploy-memory`, `chat-deploy-cassandra`,
`chat-deploy-redis`, `chat-deploy-kafka`, and `chat-authorization-server` use
`${image.repo}`. Direct builds of `chat-shell`, `chat-web`, `chat-gateway`, and
`chat-deploy-memory-integration-test` use fixed `docker.io/library` names.

`chat-build` selects `chat-deploy` for its REST, gateway, and shell services.
The prefix therefore affects those service builds too. It selects
`chat-authorization-server` for authserv.

`CHAT-gkwqnnxn` added the default. Before 2026-09-22 only
`shell-scripts/build.sh` defaulted it, and `chat-build ... --build` failed
without it.

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
export CHAT_SERVICE_PASSWORD='<a password you choose>'
./shell-scripts/chat-build core --cassandra --run --notls --node-id 7 --key-type long
./shell-scripts/chat-build authserv --run --notls --node-id 8 --jwk /tmp/authserv.jwk --key-type long
```

Both launches read `CHAT_SERVICE_PASSWORD`. The core routes require the
`Service` account since 2026-10-02. See `docs/BUILD.md` and `CHAT-rdlghoqe`.

`just launch-memory 0` and `just launch-shell 1` are shorthands for the two
common cases.

A start looks like this, and both lines matter:

```
app.nodeid=7 claimed in cassandra keyspace chat_long
Started ChatAppKt in 3.172 seconds
```

The claim line appears only for a backend that claims. A deployment on memory
stores claims nothing.

*Measured: all five composition roots start this way.*

### Build an image instead

```bash
./shell-scripts/chat-build core --cassandra --build --notls --node-id 7 --key-type long
```

That command needs no environment variable. It builds
`docker.io/library/cassandra-core-service-rsocket:0.0.1`. Measured on
2026-09-22.

The image bakes the launch options, so a value that names a path or a host on
your machine is wrong inside a container. `chat-build` refuses `--jwk` with
`--build` for that reason. The cassandra contact point default of `127.0.0.1`
names the container itself, so override it through the environment variable
before you build, or supply the property when you run the image.

*Measured on 2026-09-22: the Cassandra image builds and starts. The memory
image was built and run later through the container launcher.*

### Record images for a push

`chat-build ... --build --image-manifest PATH` records the image of each
successful build. `CHAT_IMAGE_MANIFEST` sets the same path. One path serves one
deployment run, so builds in that run leave one list for the push.

```bash
set -euo pipefail
export CHAT_IMAGE_MANIFEST=/tmp/release-2026-10-24.json
shell-scripts/chat-build core --cassandra --build --uuid --node-id 9 --notls
shell-scripts/chat-build rest --client --build --uuid --node-id 10 --notls
shell-scripts/image-references "$CHAT_IMAGE_MANIFEST" | while IFS= read -r image; do
    docker push "$image"
done
```

Each record holds the image reference, image id, architecture, service, and
module. Docker supplies a repository digest only when it reports one for the
same repository. Maven supplies the image reference from the selected module.

The build writes a record after Maven succeeds and Docker confirms the image.
A failed build adds nothing. A lock protects concurrent writes to one manifest.
The writer renames a temporary file to replace the manifest as one operation.

Use a new path for each deployment run. An old path can contain images from
an earlier run.

### Launch the core container

The [container launcher](../shell-scripts/README-chat-build.md#container-launch)
documents its provider map, commands, requirements, and startup gates. It
builds one core RSocket image through `chat-build`. It reuses a healthy Consul
or starts the existing local Consul service. It then starts the core container.
For the mixed composition, it starts Cassandra and Redis when no external
addresses are set. It checks the Cassandra keyspace before core starts.
Select pub/sub explicitly. The Cassandra image supports memory or Redis
pub/sub and Cassandra or Lucene indexes.

On 2026-10-12, the memory command built its image and started a healthy core
container. Consul reported one passing core service. The root key entry
answered HTTP 200. The Cassandra image built with Consul support. No live
Cassandra core launch was measured in that run. It had no Cassandra service
or schema. Later on 2026-10-12, the mixed launcher reached core health and
passing Consul registration. Authorization, REST, and gateway remain
separate service launches.

## Step 6 — read the result

| Signal | Meaning |
|---|---|
| `Started ChatAppKt in N seconds` | the context refreshed and the servers bound |
| `claimed in <store>` | the node id lease was taken |
| `Netty RSocket started on port` | the RSocket transport is listening |
| `Tomcat started on port` | the authorization server is listening |

An `APPLICATION FAILED TO START` block names the missing bean or property.
Read it before changing anything, because the two failures this repository met
most often were a missing property and a bean that only a test supplied.

---

## What this document does not cover

- **Other Consul clients.** The container launcher measured one memory core
  registration. This document does not measure discovery from REST or other
  client services.
- **A real cluster.** Every measurement here is one machine, with backends in
  local containers.
- **The native image.** `--native` is refused today. `CHAT-arcqfjuc` holds the
  reason, and `docs/NATIVE-IMAGE.md` the detail.
- **Vector recall and embeddings.** The Cassandra recipe can select embedded
  vectors and OpenAI-compatible embeddings. This document does not measure
  that launch. See `docs/EMBEDDING-PROVIDERS.md`.
- **Four backend images.** Only the cassandra deployment image has been built
  and started.
