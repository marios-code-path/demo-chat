# Node 9 live proof

Run on 2026-10-19 under `CHAT-hcfgkbuz`, in the worktree of branch
`chat-hcfgkbuz-node-9`. Node 16 stayed up during the run.

## Environment

- Docker Compose v5.3.1 on the shared daemon.
- The embedding endpoint `http://node2.lan:6090/v1/models` returned 200.
- Ports 6890 and 6891 were free.
- The node-9 image was absent. The first start built it.
- Node 16 had five containers up. `node16-before-ps.txt` holds the list.
  `node16-before-images.txt` holds the core tags.

## Step 2: start with the toggle on

Command: `node-9/bin/start`

Result: exit 0. Health on 6891:
`{"groups":["liveness","readiness"],"status":"UP"}`.

- The module install took 27.3 s. The image build took 23.7 s.
- The image is `node-9/cassandra-core-service-rsocket:0.0.1`.
- The build log prints `Successfully built image
  'docker.io/node-9/cassandra-core-service-rsocket:0.0.1'`. The local tag
  uses the short form. No node-16 tag changed.
- The core PID 1 `JAVA_TOOL_OPTIONS` holds the baked flags and the Compose
  value. It holds `-Dapp.nodeid=9`,
  `-Dspring.cassandra.contact-points=cassandra`, and
  `-XX:MaxDirectMemorySize=256M`.

## Step 2a: the Admin password

- Login as `Admin` with the generated password: exit 0. Standard error is
  empty.
- Login as `Admin` with `changeme`: exit 77.
  `chat-shell: Authentication failed: Invalid Credentials`.
- `users` returned Anon, Service, Admin, and Agent. One `Admin`. No
  lowercase `admin`.

`SPRING_CONFIG_IMPORT=file:/etc/node-9/userinit.yml` overrides the Admin
password. The mechanism of task 2 step 0 works.

## Step 2b: the schema step is safe

- `add-topic node9-proof`, then `post`, then `messages --format records`:
  exit 0 for each.
- `stop`, then `bash -x node-9/bin/start`: exit 0. The trace holds no
  `-f /schema/keyspace-uuid.cql` line. The trace is `start-2b-trace.txt`.
- The message after the restart is byte for byte the same. The keyspace
  query answered `(1 rows)` on the second start.

## Step 3: the vector write

- The post writes a new generation. A second post created
  `gen-0000000000000002` within 10 s. No restart or rebuild trigger was
  needed.
- The identity directory is `nomic-embed-text-v1-5-768`.

## Step 4: the files

- `find node-9/data/vector -type f` lists 13 files.
- The total size is 10,763 bytes.
- gen-0 is empty. gen-1 holds one entry. gen-2 holds two entries.
- `vector-size.txt` holds the measurement.

## Step 5: reload

- `stop`, then `start`: both exit 0.
- The file list and every mtime are unchanged. The diff is empty.
- The core reused the files. It wrote no new generation.
- The core log holds no vector-store line. The file state is the evidence.

## Step 6: toggle off

- `NODE9_VECTOR_PERSIST=off node-9/bin/stop`, then the same with `start`:
  exit 0. Health is UP.
- `node9_compose config` with off holds 0 lines with `vector`. The output
  is `config-off.yaml`.
- `data/vector` kept its 13 files and gained none.
- A start with on while the off core ran returned status 3. Port 6891 was
  in use. Stop first, then start on: exit 0, health UP, 13 files.
- The launcher requires a stop before a toggle change. The port guard
  caused the refusal. This is not a defect.

## Redaction, 2026-10-19

Review of PR #262 found two secret values in `config-off.yaml`, the
Compose output. Both are now `<redacted>`. The Admin value matched the live
`node-9/secrets.env`. That password is rotated on 2026-10-19. The
service value was older than the current `secrets.env`. The redaction
changes those two lines alone. The rest of the output is as measured.

## Step 7: node 16 is unchanged

- The diff of `node16-before-ps.txt` and `node16-after-ps.txt` holds only
  four added `chat-node-9-*` lines.
- The five node-16 container IDs, names, images, and status are the same.
- The image diff holds only `node-9/cassandra-core-service-rsocket:0.0.1`.
  The node-16 tags are unchanged.

## Step 8: the move

Not verified.

- The owner named no destination host.
- A Docker-in-Docker daemon needs `--privileged`. The plan says ask the
  owner first.
- `node-9/bin/stop` and `node-9/bin/save-images` ran with exit 0. The five
  tar files are in `node-9/images/`: busybox 4.2 MB, Cassandra 330 MB,
  Consul 219 MB, core 467 MB, Redis 140 MB. The total is 1.2 GB.

## A finding from the proof: the stop timeout

- `node-9/bin/stop` first used the Compose default timeout. Cassandra and
  the core exited 137 (SIGKILL).
- `node9_compose stop --timeout 60` let both exit 143 (SIGTERM) in about
  5 s. The stop script now passes `--timeout 60`.
- Consul exits 1 on the stop. Redis and both volume-init containers exit 0.

## Design items

- Item 5, live follow: not measured. This proof ran one-shot commands
  alone. The reason holds.
- Item 7, the agent sandbox: not measured. No sandboxed agent ran.
- Item 10, the node-9 address: measured. `chat-shell` connected to 6890
  and 6891 for `whoami`, `add-topic`, `post`, and `messages`.

## Surprises

- The build log names `docker.io/node-9/...`. The local tag is
  `node-9/...`. Docker reads the short form as the same reference.
- GNU netcat closes after one connection. The offline test binds 6890 with
  `nc -l -p 6890`.
- `docker ps -a` status text ages. The offline snapshot compares IDs,
  names, and images alone.
- The `node-9/` home-path check now skips `data/`, `images/`, `secrets/`,
  and `run/`. The saved tar files hold image-layer paths, not mount paths.

## Final state

- Node 9 is stopped. Its containers are exited.
- Node 16 is unchanged.
- The five node-9 image tars remain in `node-9/images/`. Git ignores them.
