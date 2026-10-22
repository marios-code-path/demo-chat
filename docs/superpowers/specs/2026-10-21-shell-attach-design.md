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
- A core can register `advertiseHost` and `advertisePort` metadata. A core
  without them registers no advertise metadata. The shared configuration file
  declares neither key, so no deployment gets them by default.
- Each deployment that needs them supplies its own file. Node 9 uses
  `node-9/advertise.yml`, and `node-9/compose.yaml` mounts it at
  `/etc/node-9/advertise.yml` and names it in `SPRING_CONFIG_IMPORT`. The file
  sets `spring.cloud.consul.discovery.metadata.advertiseHost` and
  `advertisePort` as literal values: `127.0.0.1` and `6890`.

**Correction, 2026-10-22.** The approved text said that `node-9/compose.yaml`
sets the properties `app.advertise.host` and `app.advertise.port`. The
implementation uses a mounted file instead, and it sets the metadata keys with
no `app.advertise.*` indirection. Two reasons:

1. A Compose `environment:` value arrives as an environment variable. Spring
   makes the key of a map property lower case. So `advertiseHost` would
   register as `advertisehost`, and no client would find it. A file keeps the
   capital letters.
2. The `app.advertise.*` indirection added a second name for one value and no
   behavior. The metadata keys alone are enough.

The literal values are a cost of the file. No variable can hold
`advertisePort` equal to `NODE9_RSOCKET_PORT`. The test case
`advertise_matches_the_published_port` compares the two files. A node other
than node 9 needs its own file. See CHAT-yexqiyie and Sigma's review.

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
  **Measured on 2026-10-22: it passes.** The check keeps the registered
  container address, `http://172.26.0.5:6791/actuator/health`, and gives 200
  OK. Consul runs in the same network, so it reaches that address. The
  advertise metadata changes the client address alone.
- An old core without `nodeId` metadata never matches `--core-id`. This is correct, and the error names the cause.
  **Measured on 2026-10-22: correct.** The test case
  `anInstanceWithoutANodeIdDoesNotMatch` pins it.
- The change to `instance-id` can leave an old registration in a persistent Consul. Node-9 Consul data is persistent. Deregister the old entry once.
  **Measured on 2026-10-22: it did not happen.** After the restart the catalog
  held one instance alone, `core-service-rsocket-9`. No deregister was needed.
- A registry that does not answer gives a client error with no registry in it.
  **Fixed after Sigma's review.** `getServiceInstance` maps such an error to a
  `DiscoveryException` that names the registry and keeps the cause. The lookup
  then also retries, so a shell that starts before its registry waits for it.
