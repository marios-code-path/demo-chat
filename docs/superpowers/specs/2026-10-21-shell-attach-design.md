# Shell attach to a registry and a core

Issue: CHAT-yexqiyie. Parent: CHAT-mvvvnujw.

## Goal

```
just launch-shell 1 --key-type uuid --registry 127.0.0.1:6892 --core-id 9
```

The shell finds the core of node 9 in the named Consul registry and connects to it.

## Decisions

Mario decided these:

- One node is one workspace. Each node has its own registry.
- The registry holds the node id of each core as instance metadata. The shell filters on it.
- `--core-id` names the target core. The first argument of `launch-shell` stays the shell's own `app.nodeid`.
- A shared registry for many nodes is out of scope. See CHAT-kzepoghr.

Claude chose these. Mario can change them:

- The core registers an advertised host and port as metadata.
- The node-9 Consul publishes host port 6892 on 127.0.0.1.

## Current behavior

- `chat-build shell` selects `--consul` or "local" discovery. Local is the default.
- Local discovery reads `CORE_HOST` and `CORE_PORT` (default 127.0.0.1:6790) through `client-rsocket-local.yml`.
- Consul discovery calls `ConsulClientDiscovery.getServiceInstance`. It takes the first instance of `core-service-rsocket`.
- The core registers `instance-id: ${spring.application.name}`. Two cores in one registry would share one id.
- The core registers the container address, for example 172.26.0.5:6790. A host process cannot reach it.

## Design

### Core registration

File: `shared-deploy-configuration/src/main/config/server-rsocket-consul.yml`.

- `instance-id` becomes `${spring.application.name}-${app.nodeid}`.
- Metadata adds `nodeId: ${app.nodeid}`.
- Metadata adds `advertiseHost` and `advertisePort` when the properties `app.advertise.host` and `app.advertise.port` exist.
  A core without them registers no advertise metadata.
- `node-9/compose.yaml` sets both properties. The host is `127.0.0.1`. The port is `NODE9_RSOCKET_PORT`.

### Discovery

File: `chat-client-consul/.../ConsulClientDiscovery.kt`. The `ClientDiscovery` interface does not change.

- A new optional property `app.client.discovery.core-id` (integer).
- When set, `getServiceInstance` keeps only the instances whose `nodeId` metadata equals the value.
- No match raises `DiscoveryException`. The message names the service, the id, and the registry address.
- The existing 10-retry loop stays.
- A kept instance with `advertiseHost` metadata becomes a `DefaultServiceInstance` with the advertised host and port.
  Without that metadata, the registered address stays.
- When the property is unset, the behavior does not change.

### Launch

- `chat-build` accepts `--registry HOST:PORT` and `--core-id N` for the `shell` service.
  `--registry` implies the `consul` feature. It sets `spring.cloud.consul.host` and `spring.cloud.consul.port`.
  `--core-id` sets `-Dapp.client.discovery.core-id=N`.
  `--core-id` without a registry is an error, because local discovery has no node ids.
- `CHAT_REGISTRY` sets a default for `--registry`.
- `just launch-shell` passes both flags through. `dry-run-shell` prints them.
- `shell-scripts/golden/` gains a golden file for the new flags.

### node-9

- `node-9/compose.yaml` publishes Consul port 8500 as `127.0.0.1:${NODE9_REGISTRY_PORT}:8500`.
- `node-9.env` sets `NODE9_REGISTRY_PORT=6892`. `lib.sh` treats it as a fixed isolation value, as it does the other two ports.
- The README states the launch command for node 9.

## Tests

- Unit: `ConsulClientDiscovery` with a fake instance list.
  - One match returns that instance.
  - Two instances, ids 9 and 16, return the requested one.
  - No match raises `DiscoveryException` with the id and the registry address in the message.
  - Advertise metadata replaces the host and port.
  - An unset `core-id` returns the first instance, as today.
- Shell script: `chat-build shell --consul --registry H:P --core-id 9 --dry-run` matches a golden file.
  `--core-id` without `--registry` exits 2.
- Live: the shell attaches to node 9 with `--core-id 9`. `--core-id 16` fails with the message above.

## Risks

- The Consul health check may fail for an advertised address that differs from the real one. Check this live.
- An old core without `nodeId` metadata never matches `--core-id`. This is correct, and the error names the cause.
- The change to `instance-id` can leave an old registration in a persistent Consul. Node-9 Consul data is persistent. Deregister the old entry once.
