# Topic Register Stage 2: Node 9 Launcher Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. AGENTS.md forbids sub-agent driven development. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build `node-9/`, a directory that starts the register core on any host with one script, and writes the embedded vectors to disk under a toggle.

**Architecture:** Node 9 is a Compose project `chat-node-9`. It has its own core, Cassandra, Redis, and Consul containers. The core image comes from the existing `chat-build` with `IMAGE_REPO_PREFIX=node-9`, so the tag differs from node 16. Every mount is a bind mount under `node-9/data/`. A toggle `NODE9_VECTOR_PERSIST` (`on` or `off`) adds or omits one Compose overlay, and that overlay sets the vector path.

**Tech Stack:** Bash, Docker Compose, the existing `shell-scripts/chat-build`, Cassandra 4.1.3, Redis 7, Consul.

**Spec:** `docs/superpowers/specs/2026-10-17-topic-register-design.md`, sections "Node 9", "Build order", "Left to the builder". Issue `CHAT-hcfgkbuz`, child of `CHAT-jmnjvupr`.

## Global Constraints

- Node 9 must never change node 16. A start, stop, or build of node 9 must leave every node-16 container, image tag, port, and volume as it was.
- Project name `chat-node-9`. Never run `chat-deploy core` for node 9.
- Host ports `127.0.0.1:6890` (RSocket) and `127.0.0.1:6891` (management). Container ports 6790 and 6791. Cassandra, Redis, and Consul publish no host port.
- Key type `uuid`. Node id `9`. Selectors: `key=cassandra`, `persistence=cassandra`, `secrets=cassandra`, `index=lucene`, `pubsub=redis-pubsub`, `vector=embedded`, `embedding=openai`.
- Every mount path is relative to `node-9/`. No mount names a directory of this host.
- Image builds use an empty `DOCKER_CONFIG`.
- The build names its own backends. It sets `CONSUL_HOST=consul`, `CONSUL_PORT=8500`, and `CASSANDRA_CONTACT_POINTS=cassandra` in its environment, and it passes `--notls`. `chat-build` finds a Consul container by the name `consul` when `CONSUL_HOST` is unset, and that search can select a node-16 container. Cassandra defaults to `127.0.0.1`. Neither default is allowed.
- The shared Docker daemon holds images that node 16 may use. Node 9 never removes, retags, or prunes an image on a shared daemon. Four image tags (Cassandra, Redis, Consul, busybox) can be the same tags that node 16 uses.
- Prose follows ASD-STE100 strict mode.
- The vector store is a derived cache. Cassandra holds the source of truth. The vector directory per embedding identity is `<path>/<identity-hash-or-name>` (see `EmbeddedVectorStoreConfiguration.storageDirectoryFor`).
- The user chose the directory: `node-9/data/vector`. In the container it is `/var/lib/chat/vector`, the path of the shipped overlay `devops/docker/compose-core-vector-embedded.yaml`.
- Stage 3 owns `userinit.yml`, the agent password files, and `bin/register`. Stage 2 does not build them.

## Review Focus

- `NODE9_VECTOR_PERSIST` holds a value other than `on` or `off` (for example `ON`, empty, `1`): the launcher stops with status 2 and names the value. It never guesses.
- Toggle `off` after a run with `on`: the launcher starts without the path and does not delete `data/vector`. A later `on` finds the old files.
- Toggle `off` and the embedding settings: the base Compose file supplies them, so `off` still starts a core that has an embedding endpoint. The overlay never carries them.
- A node-16 Consul container exists on the daemon: the build and the core use the `consul` container of `chat-node-9`, never the other one.
- `start` runs twice: the second run does not apply the schema again, so no data is dropped.
- `data/vector` is missing or owned by root: `start` creates it and sets the owner before the core starts.
- Ports 6890 or 6891 are in use: `start` stops before it changes Docker.
- A node-16 core runs on the same Docker daemon: node 9 starts beside it and node 16 is unchanged.
- The embedding endpoint is unreachable at start: `start` reports it and still starts, because the vector store is a derived cache. (Confirm in the proof. If the core fails to start, `start` must stop and say so.)

---

## File Structure

| Path | Responsibility |
|------|----------------|
| `node-9/node-9.env` | Launch values with no secret. Includes `NODE9_VECTOR_PERSIST=on`. |
| `node-9/compose.yaml` | Core, Cassandra, Redis, Consul, network. It holds the secret and embedding settings of the core, with or without the vector path. |
| `node-9/compose.vector-disk.yaml` | Overlay. It controls three things only: the vector path, the bind mount, and the ownership step. |
| `node-9/bin/lib.sh` | `node9_load_env`, `node9_compose_args`, `node9_check_toggle`. Sourced by every script. |
| `node-9/bin/setup-secrets` | Creates `secrets.env` with random values when it is missing. |
| `node-9/userinit.yml` | Overrides the shipped `Admin` password with `${CHAT_ADMIN_PASSWORD}`. Stage 3 adds the agent users here. |
| `node-9/schema/keyspace-uuid.cql` | A copy of the shipped schema. Its first line is `DROP KEYSPACE`. `start` applies it only when the keyspace is absent. |
| `node-9/bin/start`, `node-9/bin/stop` | Launch and stop. |
| `node-9/bin/save-images`, `node-9/bin/load-images` | Move the images to another host. |
| `node-9/README.md` | Connectivity notes, start, stop, toggle, move. |
| `node-9/.gitignore` | `secrets.env`, `secrets/`, `data/`, `images/`, `run/`. |
| `shell-scripts/test-node-9.sh` | Offline tests. No container starts. |

The test script follows the pattern of `shell-scripts/test-deploy-recipe.sh`. Read it first.

---

### Task 1: The toggle and the Compose files

**Files:**
- Create: `node-9/node-9.env`, `node-9/compose.yaml`, `node-9/compose.vector-disk.yaml`, `node-9/bin/lib.sh`, `node-9/.gitignore`
- Test: `shell-scripts/test-node-9.sh`

**Interfaces:**
- Produces: `node9_load_env` (no argument, sets the variables of `node-9.env`, then validates the toggle). `node9_compose` (runs `docker compose -p chat-node-9 --env-file node-9/node-9.env --env-file node-9/secrets.env <node9_compose_args> "$@"`, so Compose interpolation reads both files and no secret appears in a command line. When `secrets.env` is missing it fails with status 2 and says to run `setup-secrets`.) `node9_check_toggle VALUE` (status 0 for `on` or `off`, else prints `node-9: NODE9_VECTOR_PERSIST must be on or off, got '<VALUE>'` to stderr and returns 2). `node9_compose_args` (prints the `-f` arguments, one per line: `compose.yaml`, then `compose.vector-disk.yaml` only when the toggle is `on`).

- [ ] **Step 1: Write the failing tests** in `shell-scripts/test-node-9.sh`. Cases:
  - `toggle_on_adds_overlay`: `node9_compose_args` with `on` prints both files.
  - `toggle_off_omits_overlay`: with `off`, it prints `compose.yaml` alone.
  - `toggle_rejects_other_values`: `ON`, empty, and `1` each return 2 and name the value.
  - `config_on_sets_vector_path`: `docker compose -p chat-node-9 <args> config` with `on` contains `APP_SERVICE_CORE_VECTOR_EMBEDDED_PATH: /var/lib/chat/vector` and a bind source ending in `node-9/data/vector`.
  - `config_off_has_no_vector_path`: with `off`, the output contains neither the variable nor `data/vector`.
  - `config_off_keeps_embedding_settings`: with `off`, the core environment still holds `APP_SERVICE_CORE_EMBEDDING_OPENAI_BASE_URL`, `APP_SERVICE_CORE_EMBEDDING_OPENAI_MODEL`, and `APP_SERVICE_CORE_EMBEDDING_IDENTITY` with the values of `node-9.env`, and `APP_SERVICE_CORE_EMBEDDING_OPENAI_API_KEY` with the value of a fixture `secrets.env`. `node-9.env` holds no API key, and a test asserts that. The `on` and `off` outputs differ only in the vector path, the vector mount, and the `vector-volume-init` service.
  - `config_core_has_no_unset_variable`: `node9_compose config` exits 0 with a fixture `secrets.env`, and exits non-zero with a message that names the variable when `CHAT_EMBEDDING_OPENAI_API_KEY` is missing.
  - `no_absolute_host_mount`: every bind source in the `on` output starts with the `node-9/` directory of the checkout, and `node-9/` holds no `/Users` or `/home` string.
  - `no_node16_names`: the output has no `chat-deploy-core`, no `docker_chatnet`, and no image reference that begins `docker.io/library/`. The core image is `node-9/cassandra-core-service-rsocket:<version>`. That name holds the base name `cassandra-core-service-rsocket`, so the test checks the full reference and never the base name alone.
  - `ports_are_loopback_only`: the only `published` ports are `6890` and `6891`, both with host IP `127.0.0.1`.
- [ ] **Step 2: Run** `bash shell-scripts/test-node-9.sh`. Expected: every case FAIL because the files are missing.
- [ ] **Step 3: Write the files.**
  - `node-9.env` holds: `NODE9_PROJECT=chat-node-9`, `NODE9_VECTOR_PERSIST=on`, `NODE9_RSOCKET_PORT=6890`, `NODE9_MGMT_PORT=6891`, `NODE9_IMAGE_REPO=node-9`, `CHAT_EMBEDDING_OPENAI_BASE_URL=http://node2.lan:6090/v1`, `CHAT_EMBEDDING_OPENAI_MODEL=nomic-embed-text-v1.5.Q8_0.gguf`, `CHAT_EMBEDDING_IDENTITY=nomic-embed-text-v1-5-768`, `VECTOR_OWNER=1002:1001`, and pinned versions `CASSANDRA_VERSION=4.1.3`, `REDIS_VERSION=7`, `CONSUL_VERSION` (pin the version that the shipped Consul container reports, not `latest`). Every value can come from the caller's environment first.
  - `compose.yaml` follows `devops/docker/compose-core.yaml` and `docker-compose.yaml`. It uses a network of its own named `node-9-net` (not external), with no `host.docker.internal` for Cassandra. The core publishes `127.0.0.1:${NODE9_RSOCKET_PORT}:6790` and `127.0.0.1:${NODE9_MGMT_PORT}:6791`. Cassandra mounts `./data/cassandra`, `./schema` (read-only). Redis mounts `./data/redis`. Consul mounts `./data/consul`. The Lucene root mounts `./data/lucene` with the `busybox:1.36` ownership step of `compose-core-lucene.yaml`.
  - `compose.yaml` also holds, in the core `environment`: `CHAT_SERVICE_PASSWORD`, `CHAT_ADMIN_PASSWORD`, `APP_SERVICE_CORE_EMBEDDING_OPENAI_BASE_URL`, `APP_SERVICE_CORE_EMBEDDING_OPENAI_MODEL`, `APP_SERVICE_CORE_EMBEDDING_IDENTITY`, and `APP_SERVICE_CORE_EMBEDDING_OPENAI_API_KEY`. Each uses the `${NAME:?Set NAME}` form of `devops/docker/compose-core.yaml`. The core also mounts `./userinit.yml` read-only and sets `SPRING_CONFIG_IMPORT=file:/etc/node-9/userinit.yml`, which is required, not `optional:` (see task 2).
  - `compose.vector-disk.yaml` is based on `devops/docker/compose-core-vector-embedded.yaml`. It holds three things only: the service `vector-volume-init` (busybox `chown` of the bind mount, owner `${VECTOR_OWNER}`), the core `depends_on` of that service, and for the core the variable `APP_SERVICE_CORE_VECTOR_EMBEDDED_PATH: /var/lib/chat/vector` and the bind mount `./data/vector:/var/lib/chat/vector`. It holds no embedding variable.
  - `lib.sh` implements the three functions. Resolve `node-9/` from `BASH_SOURCE`, not from the working directory.
- [ ] **Step 4: Run** `bash shell-scripts/test-node-9.sh`. Expected: all cases PASS.
- [ ] **Step 5: Commit** with the message `CHAT-hcfgkbuz: Add the node-9 Compose files and the vector toggle`.

### Task 2: Secrets, the Admin password, and the keyspace schema

**Files:**
- Create: `node-9/bin/setup-secrets`, `node-9/userinit.yml`, `node-9/schema/keyspace-uuid.cql`
- Modify: `shell-scripts/test-node-9.sh`

**Interfaces:**
- Consumes: `lib.sh` of task 1.
- Produces: `node-9/bin/setup-secrets` (status 0). It writes `node-9/secrets.env` with mode `0600` and the keys `CHAT_SERVICE_PASSWORD`, `CHAT_ADMIN_PASSWORD`, `CHAT_EMBEDDING_OPENAI_API_KEY`. Compose reads the file through `--env-file` (task 1, `node9_compose`). `node9_apply_schema` in `lib.sh` (status 0 when `chat_uuid` exists or the apply succeeded, non-zero otherwise). It never replaces an existing file. The embedding key for the node-2 endpoint is a fixed placeholder value, and the README tells the owner to replace it.

- [ ] **Step 0: Find the Admin mechanism.** The shipped `shared-deploy-configuration/src/main/config/userinit.yml` sets `Admin.password: changeme` as a literal, with no placeholder. The core loads that file from the classpath of the image. The `Service` user reads `${CHAT_SERVICE_PASSWORD:}` and `Admin` does not. A relaxed-binding variable such as `APP_INIT_INITIALUSERS_ADMIN_PASSWORD` is not safe, because it can make a second user `admin` beside `Admin` (see the bracket-form comment in `chat-build`, `agent_flags`). A system property cannot carry the password, because it bakes into the image. So the plan uses a file: `node-9/userinit.yml` holds `app.init.initialUsers.Admin.password: ${CHAT_ADMIN_PASSWORD}` alone, the core mounts it at `/etc/node-9/userinit.yml`, and the core sets `SPRING_CONFIG_IMPORT=file:/etc/node-9/userinit.yml`. Write a probe first in the live proof (task 5 step 2a). If the probe fails, stop and ask the owner. Do not fall back to a build-time password.
- [ ] **Step 1: Write failing tests.** `secrets_created_with_mode_0600`. `secrets_not_replaced`: run twice, and the file bytes match. `secrets_values_are_random_and_distinct`. `secrets_never_printed`: standard output holds no password. `userinit_overrides_only_admin_password`: the YAML has the one key and the placeholder, and no literal password. `compose_passes_admin_password`: `node9_compose config` shows `CHAT_ADMIN_PASSWORD` in the core environment with the fixture value. `schema_applied_when_absent`: with a stub `cqlsh` that answers `(0 rows)` with exit 0, then `(1 rows)` after the apply, `node9_apply_schema` runs `cqlsh -f /schema/keyspace-uuid.cql` once and returns 0. `schema_not_applied_when_present`: with a stub that answers `(1 rows)`, it runs no `-f` call and returns 0. `schema_not_applied_on_connection_error`: with a stub whose query exits non-zero and prints a connection error, it runs no `-f` call and returns non-zero. `schema_not_applied_on_unexpected_output`: with a stub that exits 0 and prints neither `(0 rows)` nor `(1 rows)`, it runs no `-f` call and returns non-zero. `schema_fails_when_verify_fails`: after an apply, a stub that still answers `(0 rows)` makes it return non-zero. `schema_waits_for_cassandra`: with a stub that fails 3 times, then succeeds, the function polls and then applies. `schema_gives_up`: after 180 attempts of failure it returns non-zero and applies nothing.
- [ ] **Step 2: Run, expect FAIL.**
- [ ] **Step 3: Implement.** Use `openssl rand -hex 16`. Copy `shared-resources-cassandra/src/main/resources/keyspace-uuid.cql` to `node-9/schema/`. Its first line is `DROP KEYSPACE IF EXISTS chat_uuid`, so `node9_apply_schema` must tell an absent keyspace from any other failure. `DESCRIBE KEYSPACE` is not enough, because a connection error also fails. The function does these steps. (a) Poll `cqlsh -e 'DESCRIBE KEYSPACES'` in the `cassandra` service, up to 180 attempts, 1 s apart. (b) Run `cqlsh -e "SELECT keyspace_name FROM system_schema.keyspaces WHERE keyspace_name = 'chat_uuid'"` once. (c) Read the result in three cases. Exit non-zero: return non-zero and apply nothing. Exit 0 and the output holds `(1 rows)`: the keyspace exists, so apply nothing. Exit 0 and the output holds `(0 rows)`: run `cqlsh -f /schema/keyspace-uuid.cql`. Any other output: return non-zero and apply nothing. (d) After an apply, repeat the query and require `(1 rows)`. The function uses `node9_compose exec -T cassandra`. `start` calls it after Cassandra is up and before the core starts (task 3).
- [ ] **Step 4: Run, expect PASS.**
- [ ] **Step 5: Commit** `CHAT-hcfgkbuz: Add node-9 secret setup, the Admin password file, and the schema step`.

### Task 3: The image, `start`, and `stop`

**Files:**
- Create: `node-9/bin/start`, `node-9/bin/stop`
- Modify: `shell-scripts/test-node-9.sh`

**Interfaces:**
- Consumes: `node9_load_env`, `node9_compose_args`.
- Produces: `node-9/bin/start [--dry-run]`. It runs these steps in order, and each step stops the script on failure with a message that begins `node-9:`.
  1. Load the environment. Run `setup-secrets`.
  2. Refuse when `127.0.0.1` ports 6890 or 6891 are in use (use `nc -z`). Status 3.
  3. Create `data/{cassandra,lucene,redis,consul}` and, only when the toggle is `on`, `data/vector`.
  4. Resolve the full image reference with Maven, not from the `# image:` line of `chat-build`, which holds only the base name. Run `IMAGE_REPO_PREFIX=node-9 mvn -q -f pom.xml -pl chat-deploy-cassandra help:evaluate -Dexpression=appImageName -DimageName=cassandra-core-service-rsocket -DforceStdout`. Stop with status 4 when the result does not begin `node-9/` or holds `${`.
  5. Build the core image when that reference is absent. Install the module first as `chat-deploy` does (`mvn -pl chat-deploy-cassandra -am -Pregister-consul -Dmaven.test.skip=true install`). The `register-consul` profile adds dependencies only, and it writes nothing to a Consul. The install writes jars to `~/.m2`, as `chat-deploy` does, and the README notes it. Then run, with an empty `DOCKER_CONFIG` and this environment, `env IMAGE_REPO_PREFIX=node-9 CONSUL_HOST=consul CONSUL_PORT=8500 CASSANDRA_CONTACT_POINTS=cassandra shell-scripts/chat-build core --cassandra --index lucene --pubsub redis-pubsub --notls --node-id 9 --key-type uuid --vector embedded --embedding openai --init users,rootkeys --consul --build`. The `--notls` flag is required, and the run stops without it.
  6. Start Cassandra, Redis, and Consul: `node9_compose up -d cassandra redis consul`. Then run `node9_apply_schema`.
  7. Run `node9_compose up -d core-service-rsocket`.
  8. Wait for `http://127.0.0.1:6891/actuator/health`. Up to 180 s.
  `--dry-run` prints the Compose arguments, the build command with its environment, and the full image reference, and changes nothing. It does not print a secret.
- `node-9/bin/stop`: `node9_compose stop`, so Cassandra stops cleanly. It uses `-p chat-node-9` alone and never reads another project. It stops with the same file set as `start`, so a toggle change between runs does not leave the vector service behind.

- [ ] **Step 1: Write failing tests.**
  - `start_dry_run_uses_node9_names`: the output has `-p chat-node-9`, `--node-id 9`, `IMAGE_REPO_PREFIX=node-9`, and has no `chat-deploy-core`.
  - `build_command_is_explicit`: the dry-run build command holds `--notls`, `CONSUL_HOST=consul`, `CONSUL_PORT=8500`, and `CASSANDRA_CONTACT_POINTS=cassandra`. The test sets a stub `docker ps` that lists a container named `chat-deploy-consul-1`, and the command still names `consul`. The test also fails when `CONSUL_HOST` is absent from the build environment.
  - `start_order`: with a stub `docker`, the recorded calls show Cassandra up, then the schema step, then the core up. The core `up` never comes before the schema check.
  - `second_start_keeps_data`: with a stub whose keyspace query answers `(1 rows)`, a second `start` records no `cqlsh -f` call.
  - `dry_run_prints_no_secret`: with a fixture `secrets.env`, the dry-run output holds none of its values.
  - `start_dry_run_changes_nothing`: `docker ps -a` and `docker images` listings match before and after.
  - `start_dry_run_off_has_no_vector_dir`: with `off`, `data/vector` is not created.
  - `start_refuses_busy_port`: bind a port 6890 with a short `nc -l`, expect status 3 and no `docker` call (put a stub `docker` first on PATH that records calls).
  - `stop_names_only_project_node9`: with a stub `docker`, every recorded call has `-p chat-node-9`.
  - `image_reference_differs_from_node16`: with a stub `mvn` that answers `node-9/cassandra-core-service-rsocket:0.0.1`, the reference starts with `node-9/`. With a stub that answers `docker.io/library/cassandra-core-service-rsocket:0.0.1`, `start` exits 4 and runs no build.
- [ ] **Step 2: Run, expect FAIL.**
- [ ] **Step 3: Implement** both scripts. Reuse the health loop pattern of `shell-scripts/chat-deploy` lines 270-300. Do not call `chat-deploy`.
- [ ] **Step 4: Verify the image tag claim against the real tools.** Run the Maven evaluate of step 4 of the interface with `IMAGE_REPO_PREFIX=node-9`. Then run the `chat-build ... --dry-run` of step 5 and confirm its `-DimageName` and the install target. Expected: the full reference is `node-9/cassandra-core-service-rsocket:<version>`. If it begins `docker.io/library`, stop and ask the owner, because a build then replaces the node-16 tag.
- [ ] **Step 5: Run tests, expect PASS.**
- [ ] **Step 6: Commit** `CHAT-hcfgkbuz: Add the node-9 start and stop scripts`.

### Task 4: Move the images

**Files:**
- Create: `node-9/bin/save-images`, `node-9/bin/load-images`
- Modify: `shell-scripts/test-node-9.sh`

**Interfaces:**
- Produces: `save-images` runs `docker save` for the core, Cassandra, Redis, Consul, and busybox images into `node-9/images/<name>.tar`. `load-images` runs `docker load` on each tar and fails when a file is missing. The image list comes from one function `node9_images` in `lib.sh`.

- [ ] **Step 1: Failing tests** with the stub `docker`: `save_images_names_five_images`, `load_images_fails_on_missing_tar`, `image_list_has_no_latest_tag`.
- [ ] **Step 2: Run, expect FAIL.**
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run, expect PASS.**
- [ ] **Step 5: Commit** `CHAT-hcfgkbuz: Add node-9 image save and load`.

### Task 5: Live proof of the disk writes

This task runs a real node 9. Run it alone, because concurrent container runs across sessions have killed Cassandra by OOM. Check `docker ps` first and record the node-16 container IDs and image IDs.

**Files:**
- Create: `docs/superpowers/proofs/2026-10-18-node-9/RECORD.md`

- [ ] **Step 1: Snapshot node 16.** Save `docker ps --format '{{.ID}} {{.Names}} {{.Image}} {{.Status}}'` and `docker images --digests` for the `cassandra-core-service-rsocket` tag.
- [ ] **Step 2: Start with the toggle on.** Run `node-9/bin/start`. Expected: exit 0, health `UP` on 6891.
- [ ] **Step 2a: Prove the Admin password.** Log in as `Admin` with the generated password from `secrets.env`. Expected: success. Log in with `changeme`. Expected: failure. List the users. Expected: one `Admin` and no `admin`. If this fails, stop and ask the owner.
- [ ] **Step 2b: Prove the schema step is safe.** Post one message. Run `stop`, then `start`. Expected: the log shows no `cqlsh -f` call and the message is still there.
- [ ] **Step 3: Write a vector.** Create a topic and post one message through `chat-shell-cli` against `127.0.0.1:6890` (see the stage 1 proof for the login and the `CHAT_SHELL_*` variables). Wait for indexing. Record how the post reaches the vector store (the startup action, or the index job). If no vector write happens without a rebuild trigger, record the command that starts one.
- [ ] **Step 4: Prove the files.** `find node-9/data/vector -type f` lists files. Record the count and the byte total. Expected: more than zero.
- [ ] **Step 5: Prove reload.** Run `node-9/bin/stop`, then `node-9/bin/start`. Expected: the files remain. Record whether the core reuses them or rebuilds them, from the core log. This is design item 8's neighbor, and its answer is unknown today.
- [ ] **Step 6: Toggle off.** Set `NODE9_VECTOR_PERSIST=off`. Run `stop`, `start`. Expected: the core starts, `docker compose config` has no vector path, and `data/vector` keeps its old files and gains none. Then set `on` again.
- [ ] **Step 7: Prove node 16 is unchanged.** Repeat step 1 and compare. Expected: no difference in IDs, images, and status.
- [ ] **Step 8: Prove the move on an isolated daemon.** Run `stop` and `save-images`. Never run `docker rmi` on the shared daemon, because four tags (Cassandra, Redis, Consul, busybox) can be the tags of node 16. Use one of these, in this order:
  1. A destination host that the owner names. Copy `node-9/` there, then run `load-images` and `start`.
  2. A throwaway Docker-in-Docker daemon that publishes no port, on a private network, removed after the test. Ask the owner first, because the daemon needs `--privileged`. Run `load-images` and `start` against that daemon with `DOCKER_HOST`.
  If neither is available, leave the move unverified and say so in RECORD.md. Expected on success: health `UP` and the same data. Compare the image list of the shared daemon before and after. Expected: no difference.
- [ ] **Step 9: Write RECORD.md** with each result, each command, and each surprise. Mark design items 5, 10 and (if the proof shows it) 7 as measured, or leave them unverified with the reason.
- [ ] **Step 10: Commit** `CHAT-hcfgkbuz: Record the node-9 live proof`.

### Task 6: Documents, gates, and the register

**Files:**
- Create: `node-9/README.md`
- Modify: `docs/superpowers/specs/2026-10-17-topic-register-design.md` (add "Measured in stage 2"), `forward-register.md`, `README.md` if it lists top-level directories.

- [ ] **Step 1: Write `node-9/README.md`.** Cover: what node 9 is, the ports, `start`, `stop`, the toggle with the effect of each value, the vector directory, the move in four steps, the embedding route needed, and the known limit that a rebuild calls the embedding endpoint.
- [ ] **Step 2: Update the design** with the stage 2 measurements and the changed "Left to the builder" answers (image name, pinned versions, script language, ownership step, keyspace step).
- [ ] **Step 3: Run the gates.** `bash shell-scripts/test-node-9.sh`, `bash shell-scripts/build-health.sh`, and `drift check`. Read `drift refs` for each edited document first. Never relink without review.
- [ ] **Step 4: Check whitespace** with `git diff --check master...HEAD`.
- [ ] **Step 5: Update `forward-register.md`** for stage 2 by the rules of that file. Open the PR. Do not merge without Mario's explicit authorization and the named `--admin` bypass.
- [ ] **Step 6: Comment** on `CHAT-hcfgkbuz` and `CHAT-jmnjvupr` with the result.

## Self-Review

- Spec coverage: composition (task 1, 3), isolation items 1 to 5 (tasks 1, 3, 5 step 7), ports (1, 3), directory (1, 2), move (4, 5 step 8, on an isolated daemon), the toggle and the vector path (1, 5), the schema step (2, 3, 5 step 2b), the Admin password and the secret path to Compose (1, 2, 5 step 2a). The agent users move to stage 3 by the build order. This is a stated split.
- Unknowns the plan marks, not hides: whether the post writes a vector without a rebuild trigger, whether a restart reuses the files, the Consul version, whether `SPRING_CONFIG_IMPORT` overrides the `Admin` password (task 5 step 2a decides), whether an isolated daemon exists for the move proof, and the image-tag prefix (task 3 step 4 decides).
