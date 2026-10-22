# Shell attach live proof

Run on 2026-10-22 under `CHAT-yexqiyie`, in the worktree of branch
`chat-yexqiyie-shell-attach`. The code under test is commit `0b042546` and
later. Mario ran each command that changes a shared resource. Theta ran the
read commands and the test suites.

## Why this record exists

Sigma reviewed the branch and could not repeat the live proofs. Its sandbox
refused the TCP probe that `test-node-9.sh` needs, and it did not repeat
`just check` or the attach. So the live result rested on one agent's runs
alone. This record holds the commands and the verbatim output, so a reader
can repeat each one.

## Environment

- The node-9 core image was built from this branch. The new image id is
  `47dcc9f2eb67`. It replaced `4db2ad149842`.
- `/opt/demo-chat/node-9` holds the installed copy. Mario installed the code
  files of this branch before the restart.
- Ports 6890, 6891 and 6892 were free before each start.
- Mario's own shell process from an earlier session stayed up. It uses local
  discovery, so it reached nothing and changed nothing here.

## Step 1: the core registers its node id

Command, which any reader can repeat while node 9 runs:

```bash
curl -s http://127.0.0.1:6892/v1/catalog/service/core-service-rsocket
```

Result: one instance. `catalog-core-service-rsocket.json` holds the answer.

```
core-service-rsocket-9  172.26.0.5  6790
{"advertiseHost": "127.0.0.1", "advertisePort": "6890", "keyType": "uuid",
 "nodeId": "9", "rsocketPort": "6790", "secure": "false"}
```

The instance id holds the node id. The metadata holds `nodeId`, and it holds
the published address of the host.

The catalog held one instance alone. The change to `instance-id` left no old
registration, so no deregister was needed.

## Step 2: the health check passes on the container address

Command:

```bash
curl -s http://127.0.0.1:6892/v1/health/service/core-service-rsocket
```

Result, in `health-core-service-rsocket.txt`:

```
serfHealth                      passing  Agent alive and reachable
service:core-service-rsocket-9  passing  HTTP GET http://172.26.0.5:6791/actuator/health: 200 OK
```

The check keeps the registered container address. Consul runs in the same
network, so it reaches that address. The advertise metadata changes the
client address alone. This closes the first risk of the design.

## Step 3: the shell attaches to core 9

Command:

```bash
printf 'show-topics\nquit\n' | just launch-shell 1 --key-type uuid \
    --registry 127.0.0.1:6892 --core-id 9
```

Result: exit 0.

- The shell reached its prompt, `chat-shell:anonymous :>`.
- It read the root keys of node 9 from the registry, which names `Anon`,
  `Admin`, `TopicMembership` and `MessageTopic` with node-9 values.
- `rsocket-core` loaded.
- The log holds 0 `DiscoveryException` and 0 connection errors.

The shell stayed anonymous, so this run does not prove a command that needs
a login. It proves the lookup, the address, and the connection.

## Step 4: the shell refuses a core id that is absent

Command:

```bash
printf 'show-topics\nquit\n' | just launch-shell 1 --key-type uuid \
    --registry 127.0.0.1:6892 --core-id 16
```

Result: exit 1. `core-id-16-refusal.txt` holds the line:

```
DiscoveryException(errorBy=persistence via core-service-rsocket, node id 16,
registry 127.0.0.1:6892, after 10 tries)
```

The message names the service, the node id, and the registry.

## Step 5: a registry that is down at launch

Command, with node 9 stopped:

```bash
printf 'quit\n' | just launch-shell 1 --key-type uuid \
    --registry 127.0.0.1:6892 --core-id 9
```

Result: exit 1. `registry-down-failure.txt` holds the line. The shell stops
in the Spring Cloud Consul configuration bootstrap, before the discovery code
runs:

```
ConsulPropertySources$PropertySourceNotFoundException:
  ResourceAccessException: I/O error on GET request for
  "http://127.0.0.1:6892/v1/kv/config/chat-shell,shell/":
  Connection refused: /127.0.0.1:6892
```

The launch sets `app.kv.store=consul`, and the configuration watch uses fail
fast. The message already names the registry, so the user sees the cause.

This result disproves an earlier claim of mine. The error map and the retry in
`ConsulClientDiscovery` do not act on a registry that is down at launch. They
act on a registry that answers at startup and then fails in a later lookup.
Mario chose to keep them for that case.

## Step 6: the offline suites

With node 9 stopped, so that the `start` cases can probe free ports:

```bash
bash shell-scripts/test-node-9.sh
```

Result: 58 cases, 0 failures. `test-node-9-window.txt` holds the case list.
It includes `start_refuses_a_busy_registry_port` and
`advertise_matches_the_published_port`.

A running node 9 blocks 10 of these cases, because `start` refuses a busy
port. Those 10 need a stopped node.

```bash
bash shell-scripts/test-flags.sh
just check
```

Results: 57 golden cases match. `just check` gives 31 modules, 2409 tests, 0
failures, 0 errors, 47 skipped, and reality matches `docs/BUILD-HEALTH.md`.
Neither suite needs a container, so any reviewer can repeat both.

## What a reader can repeat, and what needs a window

| Proof | Needs |
|---|---|
| Steps 1 and 2 | a running node 9 and `curl`. Seconds. |
| Steps 3 and 4 | a running node 9, Maven, and about 2 minutes each. |
| Step 5 | a stopped node 9. |
| `test-node-9.sh`, 10 start cases | a stopped node 9 and permission to probe a TCP port. |
| `test-flags.sh` and `just check` | nothing live. |

## Known gap

The installed copy at `/opt/demo-chat/node-9` was installed before commit
`0b042546`. So it holds the older `start`, which checks ports 6890 and 6891
alone. The 6892 check reaches the installed copy at the next
`node-9/install`.
