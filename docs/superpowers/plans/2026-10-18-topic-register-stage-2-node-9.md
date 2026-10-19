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
- Prose follows ASD-STE100 strict mode.
- The vector store is a derived cache. Cassandra holds the source of truth. The vector directory per embedding identity is `<path>/<identity-hash-or-name>` (see `EmbeddedVectorStoreConfiguration.storageDirectoryFor`).
- The user chose the directory: `node-9/data/vector`. In the container it is `/var/lib/chat/vector`, the path of the shipped overlay `devops/docker/compose-core-vector-embedded.yaml`.
- Stage 3 owns `userinit.yml`, the agent password files, and `bin/register`. Stage 2 does not build them.

## Review Focus

- `NODE9_VECTOR_PERSIST` holds a value other than `on` or `off` (for example `ON`, empty, `1`): the launcher stops with status 2 and names the value. It never guesses.
- Toggle `off` after a run with `on`: the launcher starts without the path and does not delete `data/vector`. A later `on` finds the old files.
- `data/vector` is missing or owned by root: `start` creates it and sets the owner before the core starts.
- Ports 6890 or 6891 are in use: `start` stops before it changes Docker.
- A node-16 core runs on the same Docker daemon: node 9 starts beside it and node 16 is unchanged.
- The embedding endpoint is unreachable at start: `start` reports it and still starts, because the vector store is a derived cache. (Confirm in the proof. If the core fails to start, `start` must stop and say so.)

---

## File Structure

| Path | Responsibility |
|------|----------------|
| `node-9/node-9.env` | Launch values with no secret. Includes `NODE9_VECTOR_PERSIST=on`. |
| `node-9/compose.yaml` | Core, Cassandra, Redis, Consul, network. No vector settings. |
| `node-9/compose.vector-disk.yaml` | Overlay. Bind mount, ownership step, and the vector path. |
| `node-9/bin/lib.sh` | `node9_load_env`, `node9_compose_args`, `node9_check_toggle`. Sourced by every script. |
| `node-9/bin/setup-secrets` | Creates `secrets.env` with random values when it is missing. |
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
- Produces: `node9_load_env` (no argument, sets the variables of `node-9.env`, then validates the toggle). `node9_check_toggle VALUE` (status 0 for `on` or `off`, else prints `node-9: NODE9_VECTOR_PERSIST must be on or off, got '<VALUE>'` to stderr and returns 2). `node9_compose_args` (prints the `-f` arguments, one per line: `compose.yaml`, then `compose.vector-disk.yaml` only when the toggle is `on`).

- [ ] **Step 1: Write the failing tests** in `shell-scripts/test-node-9.sh`. Cases:
  - `toggle_on_adds_overlay`: `node9_compose_args` with `on` prints both files.
  - `toggle_off_omits_overlay`: with `off`, it prints `compose.yaml` alone.
  - `toggle_rejects_other_values`: `ON`, empty, and `1` each return 2 and name the value.
  - `config_on_sets_vector_path`: `docker compose -p chat-node-9 <args> config` with `on` contains `APP_SERVICE_CORE_VECTOR_EMBEDDED_PATH: /var/lib/chat/vector` and a bind source ending in `node-9/data/vector`.
  - `config_off_has_no_vector_path`: with `off`, the output contains neither the variable nor `data/vector`.
  - `no_absolute_host_mount`: every bind source in the `on` output starts with the `node-9/` directory of the checkout, and `node-9/` holds no `/Users` or `/home` string.
  - `no_node16_names`: the output has no `chat-deploy-core`, no `docker_chatnet`, and no `cassandra-core-service-rsocket`.
  - `ports_are_loopback_only`: the only `published` ports are `6890` and `6891`, both with host IP `127.0.0.1`.
- [ ] **Step 2: Run** `bash shell-scripts/test-node-9.sh`. Expected: every case FAIL because the files are missing.
- [ ] **Step 3: Write the files.**
  - `node-9.env` holds: `NODE9_PROJECT=chat-node-9`, `NODE9_VECTOR_PERSIST=on`, `NODE9_RSOCKET_PORT=6890`, `NODE9_MGMT_PORT=6891`, `NODE9_IMAGE_REPO=node-9`, `CHAT_EMBEDDING_OPENAI_BASE_URL=http://node2.lan:6090/v1`, `CHAT_EMBEDDING_OPENAI_MODEL=nomic-embed-text-v1.5.Q8_0.gguf`, `CHAT_EMBEDDING_IDENTITY=nomic-embed-text-v1-5-768`, `VECTOR_OWNER=1002:1001`, and pinned versions `CASSANDRA_VERSION=4.1.3`, `REDIS_VERSION=7`, `CONSUL_VERSION` (pin the version that the shipped Consul container reports, not `latest`). Every value can come from the caller's environment first.
  - `compose.yaml` follows `devops/docker/compose-core.yaml` and `docker-compose.yaml`. It uses a network of its own named `node-9-net` (not external), with no `host.docker.internal` for Cassandra. The core publishes `127.0.0.1:${NODE9_RSOCKET_PORT}:6790` and `127.0.0.1:${NODE9_MGMT_PORT}:6791`. Cassandra mounts `./data/cassandra`, `./schema` (read-only). Redis mounts `./data/redis`. Consul mounts `./data/consul`. The Lucene root mounts `./data/lucene` with the `busybox:1.36` ownership step of `compose-core-lucene.yaml`.
  - `compose.vector-disk.yaml` copies `devops/docker/compose-core-vector-embedded.yaml`, with two changes. The volume is the bind mount `./data/vector:/var/lib/chat/vector`. The embedding variables come from `node-9.env`. Name the ownership service `vector-volume-init`.
  - `lib.sh` implements the three functions. Resolve `node-9/` from `BASH_SOURCE`, not from the working directory.
- [ ] **Step 4: Run** `bash shell-scripts/test-node-9.sh`. Expected: all cases PASS.
- [ ] **Step 5: Commit** with the message `CHAT-hcfgkbuz: Add the node-9 Compose files and the vector toggle`.

### Task 2: Secrets and the keyspace schema

**Files:**
- Create: `node-9/bin/setup-secrets`, `node-9/schema/keyspace-uuid.cql`
- Modify: `shell-scripts/test-node-9.sh`

**Interfaces:**
- Consumes: `lib.sh` of task 1.
- Produces: `node-9/bin/setup-secrets` (status 0). It writes `node-9/secrets.env` with mode `0600` and the keys `CHAT_SERVICE_PASSWORD`, `CHAT_ADMIN_PASSWORD`, `CHAT_EMBEDDING_OPENAI_API_KEY`. It never replaces an existing file. The embedding key for the node-2 endpoint is a fixed placeholder value, and the README tells the owner to replace it.

- [ ] **Step 1: Write failing tests.** `secrets_created_with_mode_0600`. `secrets_not_replaced`: run twice, and the file bytes match. `secrets_values_are_random_and_distinct`. `secrets_never_printed`: standard output holds no password.
- [ ] **Step 2: Run, expect FAIL.**
- [ ] **Step 3: Implement.** Use `openssl rand -hex 16`. Copy `shared-resources-cassandra/src/main/resources/keyspace-uuid.cql` to `node-9/schema/`. Check the `CHAT_ADMIN_PASSWORD` name against the shipped users configuration first (`grep -rn changeme` in the resources). If the shipped name differs, use that name.
- [ ] **Step 4: Run, expect PASS.**
- [ ] **Step 5: Commit** `CHAT-hcfgkbuz: Add node-9 secret setup and the uuid schema`.

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
  4. Build the core image when `${NODE9_IMAGE_REPO}/cassandra-core-service-rsocket:<version>` is absent: run `IMAGE_REPO_PREFIX=node-9 shell-scripts/chat-build core --cassandra --index lucene --pubsub redis-pubsub --node-id 9 --key-type uuid --vector embedded --embedding openai --init users,rootkeys --consul --build` with an empty `DOCKER_CONFIG`. The version is the `project.version` of `chat-deploy-cassandra`.
  5. Run `docker compose -p chat-node-9 <args> up -d`.
  6. Wait for `http://127.0.0.1:6891/actuator/health`. Up to 180 s.
  `--dry-run` prints the Compose arguments, the chat-build command, and the image reference, and changes nothing.
- `node-9/bin/stop`: `docker compose -p chat-node-9 <both overlays> stop`, so Cassandra stops cleanly. It uses `-p chat-node-9` alone and never reads another project.

- [ ] **Step 1: Write failing tests.**
  - `start_dry_run_uses_node9_names`: the output has `-p chat-node-9`, `--node-id 9`, `IMAGE_REPO_PREFIX=node-9`, and has no `chat-deploy-core`.
  - `start_dry_run_changes_nothing`: `docker ps -a` and `docker images` listings match before and after.
  - `start_dry_run_off_has_no_vector_dir`: with `off`, `data/vector` is not created.
  - `start_refuses_busy_port`: bind a port 6890 with a short `nc -l`, expect status 3 and no `docker` call (put a stub `docker` first on PATH that records calls).
  - `stop_names_only_project_node9`: with a stub `docker`, every recorded call has `-p chat-node-9`.
  - `image_reference_differs_from_node16`: the reference starts with `node-9/` and the node-16 reference `docker.io/library/cassandra-core-service-rsocket` does not appear.
- [ ] **Step 2: Run, expect FAIL.**
- [ ] **Step 3: Implement** both scripts. Reuse the health loop pattern of `shell-scripts/chat-deploy` lines 270-300. Do not call `chat-deploy`.
- [ ] **Step 4: Verify the image tag claim.** Run the `chat-build ... --dry-run` of step 4 with `IMAGE_REPO_PREFIX=node-9` and read the `# image:` and the `appImageName` result. Expected: the reference begins `node-9/`. If it begins `docker.io/library`, stop and ask the owner, because a build then replaces the node-16 tag.
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
- [ ] **Step 3: Write a vector.** Create a topic and post one message through `chat-shell-cli` against `127.0.0.1:6890` (see the stage 1 proof for the login and the `CHAT_SHELL_*` variables). Wait for indexing. Record how the post reaches the vector store (the startup action, or the index job). If no vector write happens without a rebuild trigger, record the command that starts one.
- [ ] **Step 4: Prove the files.** `find node-9/data/vector -type f` lists files. Record the count and the byte total. Expected: more than zero.
- [ ] **Step 5: Prove reload.** Run `node-9/bin/stop`, then `node-9/bin/start`. Expected: the files remain. Record whether the core reuses them or rebuilds them, from the core log. This is design item 8's neighbor, and its answer is unknown today.
- [ ] **Step 6: Toggle off.** Set `NODE9_VECTOR_PERSIST=off`. Run `stop`, `start`. Expected: the core starts, `docker compose config` has no vector path, and `data/vector` keeps its old files and gains none. Then set `on` again.
- [ ] **Step 7: Prove node 16 is unchanged.** Repeat step 1 and compare. Expected: no difference in IDs, images, and status.
- [ ] **Step 8: Prove the move.** Run `stop`, `save-images`. Remove the five images from this host with `docker rmi` on the node-9 tags alone. Run `load-images`, `start`. Expected: health `UP`, same data.
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

- Spec coverage: composition (task 1, 3), isolation items 1 to 5 (tasks 1, 3, 5 step 7), ports (1, 3), directory (1, 2), move (4, 5 step 8), the toggle and the vector path (1, 5). Users and passwords: only the service, admin and embedding secrets here. The agent users move to stage 3 by the build order. This is a stated split.
- Unknowns the plan marks, not hides: whether the post writes a vector without a rebuild trigger, whether a restart reuses the files, the Consul version, the admin password variable name, and the image-tag prefix.
