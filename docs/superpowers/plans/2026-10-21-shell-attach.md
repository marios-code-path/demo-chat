# Shell attach to a registry and a core Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. AGENTS.md forbids subagents, so the session implementer runs every task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `just launch-shell 1 --key-type uuid --registry 127.0.0.1:6892 --core-id 9` attaches the shell to the core of node 9.

**Architecture:** The core registers its node id in its own Consul registry. The shell reads the registry the caller names, and keeps the one instance whose `nodeId` metadata matches `--core-id`. A core behind published Docker ports also registers an advertised host and port, because its registered address is a container address.

**Tech Stack:** Kotlin, Spring Cloud Consul 5, Reactor, Python (`chat-build`), Bash, Docker Compose.

**Spec:** `docs/superpowers/specs/2026-10-21-shell-attach-design.md`

## Global Constraints

- One node is one workspace. Each node keeps its own Consul registry. A shared registry is CHAT-kzepoghr.
- `--core-id` names the target core. The first argument of `just launch-shell` stays the shell's own `app.nodeid`.
- Controlled English for every comment, message, and document. See AGENTS.md.
- Semantic tools for symbol lookup. `grep` for raw text only.
- Branch: `chat-yexqiyie-shell-attach`, stacked on `chat-mvvvnujw-node9-install`.
- Node 9 fixed values: project `chat-node-9`, RSocket port 6890, management port 6891, registry port 6892.
- Metadata keys use camel case, as `rsocketPort` and `keyType` do: `nodeId`, `advertiseHost`, `advertisePort`.
- Every commit names CHAT-yexqiyie.

## Review Focus

These inputs reach the new code, and no task above owns them. Each line names the test that pins it.

- A registered instance with no `nodeId` metadata, together with one that matches. The filter must keep the match and must not fail on the absent key. Task 1 test 6.
- `advertisePort` metadata that is not a number. The shell must not crash with a number format error. It must keep the registered port and say why. Task 1 test 7.
- `--registry` with no port, such as `--registry 127.0.0.1`. `chat-build` must exit 2 and name the expected form. Task 2 refusal 3.
- `--core-id` with local discovery, which has no node ids. `chat-build` must exit 2. Task 2 refusal 1.
- A core id outside 0 to 1023. `chat-build` must refuse it, as `--node-id` does. Task 2 refusal 4.

---

### Task 1: The discovery filter

**Files:**
- Modify: `chat-client-consul/src/main/kotlin/com/demo/chat/client/discovery/ConsulClientDiscovery.kt`
- Modify: `chat-client-consul/src/main/kotlin/com/demo/chat/config/client/discovery/DiscoveryClientConfiguration.kt`
- Test: `chat-client-consul/src/test/kotlin/com/demo/chat/deploy/tests/ConsulClientDiscoveryTests.kt` (create)

**Interfaces:**
- Consumes: `ClientDiscovery.getServiceInstance(serviceName: String): Mono<ServiceInstance>` and `DiscoveryException(errorBy: String)` from `chat-core`. `ClientProperty.create(dest, prefix)` for test properties.
- Produces: `ConsulClientDiscovery(discovery: ReactiveDiscoveryClient, configProps: ClientProperties<ClientProperty>, coreId: Int? = null, registry: String = "")`. The constructor takes the Spring Cloud interface `org.springframework.cloud.client.discovery.ReactiveDiscoveryClient`, not `ConsulReactiveDiscoveryClient`, so a test supplies a fake. `registry` is the registry address for the error message alone.

- [ ] **Step 1: Write the failing tests**

```kotlin
// One fake, no mocking framework. ReactiveDiscoveryClient is an interface.
private class FakeDiscovery(private val instances: List<ServiceInstance>) : ReactiveDiscoveryClient {
    override fun description() = "fake"
    override fun getInstances(serviceId: String): Flux<ServiceInstance> = Flux.fromIterable(instances)
    override fun getServices(): Flux<String> = Flux.just("core-service-rsocket")
}

private fun instance(nodeId: String?, host: String = "172.26.0.5", port: Int = 6790,
                     advertiseHost: String? = null, advertisePort: String? = null): ServiceInstance

private val props = object : ClientProperties<ClientProperty> {
    override fun getServiceConfig(serviceKey: String) =
        ClientProperty.create("core-service-rsocket", "key.")
}
```

1. `noCoreIdKeepsTheFirstInstance` — two instances, `coreId = null`. The result host equals the first instance host.
2. `coreIdKeepsTheMatch` — instances with `nodeId` 16 then 9, `coreId = 9`. The result metadata `nodeId` equals `"9"`.
3. `noMatchFails` — one instance with `nodeId` 16, `coreId = 9`. `StepVerifier` expects an error whose message holds `"core-service-rsocket"`, `"9"`, and the registry address `"127.0.0.1:6892"`.
4. `advertiseMetadataReplacesTheAddress` — one instance, `nodeId` 9, `advertiseHost` `127.0.0.1`, `advertisePort` `6890`, `coreId = 9`. The result host equals `127.0.0.1` and the port equals `6890`. The result metadata still holds `nodeId`.
5. `noAdvertiseMetadataKeepsTheRegisteredAddress` — `nodeId` 9, no advertise keys. The host equals `172.26.0.5` and the port equals `6790`.
6. `anInstanceWithoutANodeIdDoesNotMatch` — instance A has no `nodeId`, instance B has `nodeId` 9, `coreId = 9`. The result is instance B. The call raises no exception for instance A.
7. `aBadAdvertisePortKeepsTheRegisteredPort` — `nodeId` 9, `advertiseHost` `127.0.0.1`, `advertisePort` `"abc"`. The port equals `6790` and the host equals `172.26.0.5`. The call raises no exception.

Test 3 must not wait for the retry loop. Pass a `Retry` value, or a retry count of zero, into the test instance. Keep the production default at 10 tries and 5 seconds.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn -B -ntp -pl chat-client-consul -am -Dtest=ConsulClientDiscoveryTests -DfailIfNoTests=false test`
Expected: FAIL. The compiler reports no `coreId` parameter.

Log the output to a file. Read the exit code and the summary lines alone.

- [ ] **Step 3: Implement the filter in `ConsulClientDiscovery`**

- Change the `discovery` parameter type to `ReactiveDiscoveryClient`.
- Add `coreId: Int? = null` and `registry: String = ""`.
- `getServiceInstance` keeps the current `dest` lookup and the retry loop.
- With `coreId` set, filter the `Flux` on `metadata["nodeId"] == coreId.toString()` before `next()`.
- Map a kept instance through a private function `advertised(instance: ServiceInstance): ServiceInstance`. It returns a `DefaultServiceInstance` with the advertised host and port when `advertiseHost` is not blank and `advertisePort` parses as an integer. It keeps the instance id, the service id, the secure flag, and the metadata. It returns the instance unchanged in every other case, and logs one debug line for a bad port.
- The `DiscoveryException` message names the service, the core id, and the registry.

- [ ] **Step 4: Pass the core id to the bean**

In `DiscoveryClientConfiguration`, read two values and pass them to `ConsulClientDiscovery`:

- `@Value("\${app.client.discovery.core-id:}") coreId: String` — a blank value means null. A value that is not an integer must fail the context start with a clear message.
- `@Value("\${spring.cloud.consul.host:}") consulHost: String` and `@Value("\${spring.cloud.consul.port:8500}") consulPort: Int` — the registry address for the error message.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `mvn -B -ntp -pl chat-client-consul -am -Dtest=ConsulClientDiscoveryTests -DfailIfNoTests=false test`
Expected: PASS, 7 tests.

- [ ] **Step 6: Run the module tests, to catch a broken caller**

Run: `mvn -B -ntp -pl chat-client-consul -am -Dmaven.test.skip=false test`
Expected: PASS. The Consul container tests may need Docker.

- [ ] **Step 7: Commit**

```bash
git add chat-client-consul
git commit -m "CHAT-yexqiyie: Keep the core instance that matches a node id"
```

---

### Task 2: The launch flags

**Files:**
- Modify: `shell-scripts/chat-build` (argument group near line 1045, validation in `main` near line 1131, the consul branch near line 832)
- Modify: `shell-scripts/test-flags.sh` (the `CASES` and `REFUSALS` arrays)
- Create: `shell-scripts/golden/shell-registry-core-id.flags`

**Interfaces:**
- Consumes: the property name `app.client.discovery.core-id` from Task 1.
- Produces: `chat-build shell --registry HOST:PORT --core-id N`. `--registry` implies the `consul` feature. `CHAT_REGISTRY` sets the default registry.

- [ ] **Step 1: Write the failing golden case and refusals**

Add to `CASES`:

```
"shell-registry-core-id|shell --run --notls --key-type uuid --node-id 1 --registry 10.0.0.9:6892 --core-id 9"
```

Add to `REFUSALS`:

1. `refuse-core-id-without-registry|shell --run --notls --key-type uuid --node-id 1 --core-id 9|2|--core-id needs --registry`
2. `refuse-registry-service|core --memory --run --notls --key-type uuid --node-id 0 --registry 10.0.0.9:6892|2|--registry requires the shell service`
3. `refuse-registry-shape|shell --run --notls --key-type uuid --node-id 1 --registry 10.0.0.9|2|--registry takes HOST:PORT`
4. `refuse-core-id-range|shell --run --notls --key-type uuid --node-id 1 --registry 10.0.0.9:6892 --core-id 2000|2|0 and 1023`

Refusal 4 reuses the `--node-id` range message. Copy its exact text into the expectation.

- [ ] **Step 2: Run the test to verify it fails**

Run: `./shell-scripts/test-flags.sh shell-registry-core-id`
Expected: FAIL. `chat-build` reports an unknown argument.

- [ ] **Step 3: Add the flags to `chat-build`**

- In `build_parser`, for the `shell` service alone, add `--registry` with `metavar="HOST:PORT"` and a default of `os.environ.get("CHAT_REGISTRY", "")`, and `--core-id` with `metavar="ID"` and `type=node_id_value`.
- In `main`, refuse `--registry` for another service, refuse a `--registry` value without a single `:` and an integer port, and refuse `--core-id` without `--registry`. Each returns 2.
- With `--registry` set, set `args.consul = True` before the build context is made, so the discovery mode becomes `consul`.
- In the consul branch of the discovery flags, a set `--registry` replaces the `CONSUL_HOST` lookup. It sets `spring.cloud.consul.host` and `spring.cloud.consul.port`. `find_consul_host` must not run in that case.
- With `--core-id` set, add `-Dapp.client.discovery.core-id=N`.

- [ ] **Step 4: Write the golden and read the diff**

Run: `./shell-scripts/test-flags.sh --update shell-registry-core-id && git diff shell-scripts/golden/`
Expected: one new file. It holds `-Dapp.client.discovery=consul`, `-Dapp.client.discovery.core-id=9`, `-Dspring.cloud.consul.host=10.0.0.9`, `-Dspring.cloud.consul.port=6892`, and `-Dapp.nodeid=1`.

Read the new file before the commit. A golden is an expectation, not an output.

- [ ] **Step 5: Run every flag case**

Run: `./shell-scripts/test-flags.sh`
Expected: PASS. No other golden changes.

The `justfile` needs no change. `launch-shell` collects every argument that is not `--build` and passes it to `chat-build`. `dry-run-shell` adds `--dry-run` to the same list. Prove that with:

Run: `just dry-run-shell 1 --key-type uuid --registry 10.0.0.9:6892 --core-id 9`
Expected: the printed options hold `-Dapp.client.discovery.core-id=9`.

- [ ] **Step 6: Commit**

```bash
git add shell-scripts/chat-build shell-scripts/test-flags.sh shell-scripts/golden/shell-registry-core-id.flags
git commit -m "CHAT-yexqiyie: Name a registry and a core id at the shell launch"
```

---

### Task 3: The core registration and the node-9 registry port

**Files:**
- Modify: `shared-deploy-configuration/src/main/config/server-rsocket-consul.yml`
- Create: `node-9/advertise.yml`
- Modify: `node-9/compose.yaml` (the `consul` service ports, the core `SPRING_CONFIG_IMPORT` and its mounts)
- Modify: `node-9/node-9.env`, `node-9/bin/lib.sh` (`node9_check_isolation`), `node-9/install` (`code_files`)
- Test: `shell-scripts/test-node-9.sh`

**Interfaces:**
- Consumes: the metadata keys `nodeId`, `advertiseHost`, and `advertisePort` from Task 1.
- Produces: `NODE9_REGISTRY_PORT=6892`, a fixed isolation value. The node-9 Consul answers at `127.0.0.1:6892`.

- [ ] **Step 1: Write the failing node-9 cases**

Add to `shell-scripts/test-node-9.sh`, in the installer and isolation groups:

1. `node9_check_isolation` fails with status 2 and names `NODE9_REGISTRY_PORT` when the caller sets it to `7000`.
2. `node-9.env` holds `NODE9_REGISTRY_PORT=6892`.
3. `install --prefix <sandbox>` copies `advertise.yml`.
4. `compose.yaml` publishes `127.0.0.1:${NODE9_REGISTRY_PORT...}:8500` for the consul service.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./shell-scripts/test-node-9.sh`
Expected: FAIL on the four new cases. Every other case passes.

- [ ] **Step 3: Register the node id**

In `server-rsocket-consul.yml`:

- `instance-id: "${spring.application.name}-${app.nodeid}"`. Two cores in one registry then hold two ids.
- Add `nodeId: ${app.nodeid}` to `metadata`.

Do not change `server-authserv-consul.yml`. The authorization server is out of scope.

- [ ] **Step 4: Advertise the published address of node 9**

- `node-9/advertise.yml` sets `spring.cloud.consul.discovery.metadata.advertiseHost` to `127.0.0.1` and `advertisePort` to `6890`. A comment names the reason: Consul holds a container address, and a host client cannot reach it. The file holds no secret.
- `compose.yaml` mounts the file at `/etc/node-9/advertise.yml` and adds it to `SPRING_CONFIG_IMPORT` after `userinit.yml`.
- `compose.yaml` publishes `"127.0.0.1:${NODE9_REGISTRY_PORT:?Set NODE9_REGISTRY_PORT}:8500"` for the consul service.
- `node-9.env` sets `NODE9_REGISTRY_PORT=6892`, with the other fixed values in the header comment.
- `node9_check_isolation` refuses a changed `NODE9_REGISTRY_PORT`, as it does the other two ports.
- `install` adds `advertise.yml` to `code_files`.

The advertised values are literals in `advertise.yml`, not Compose variables. A Compose environment value becomes an environment variable, and Spring lowercases a map key that arrives that way. A file keeps the camel case.

- [ ] **Step 5: Run the node-9 tests**

Run: `./shell-scripts/test-node-9.sh`
Expected: PASS, every case.

- [ ] **Step 6: Commit**

```bash
git add shared-deploy-configuration node-9 shell-scripts/test-node-9.sh
git commit -m "CHAT-yexqiyie: Register the node id and the published address of node 9"
```

---

### Task 4: The live proof and the documents

A new image carries the registration change. The running core image does not.

**Files:**
- Modify: `node-9/README.md`
- Modify: `README.md` (the shell launch section, if it names `CORE_PORT`)

**Interfaces:**
- Consumes: every part above.

- [ ] **Step 1: Build the node-9 core image from this branch**

Run the build line that `node-9/bin/start --dry-run` prints. It ends with `--consul --build`.
Expected: the image `node-9/cassandra-core-service-rsocket:0.0.1` carries this branch.

Log the output to a file. Read the exit code and the summary lines alone.

- [ ] **Step 2: Restart node 9 with the new image**

- `/opt/demo-chat/node-9/bin/stop`, then `start`. The installed home may need `save-images` and `load-images` first. The README names that path.
- Remove the old `core-service-rsocket` registration. The node-9 Consul data is persistent, and the instance id changed.

Expected: `curl http://127.0.0.1:6891/actuator/health` returns `"status":"UP"`.

- [ ] **Step 3: Prove the registration**

Run: `curl -s http://127.0.0.1:6892/v1/catalog/service/core-service-rsocket`
Expected: one instance. `ServiceID` is `core-service-rsocket-9`. `ServiceMeta` holds `nodeId` `9`, `advertiseHost` `127.0.0.1`, and `advertisePort` `6890`.

- [ ] **Step 4: Prove the attach**

Run: `just launch-shell 1 --key-type uuid --registry 127.0.0.1:6892 --core-id 9`
Expected: the shell starts and a command that reads the core answers. `--core-id 16` fails with the message from Task 1, which names the registry and the id.

A shell login needs the user passwords of node 9. The shell CLI runtime is CHAT-bjqzvsoe. Record what the proof could not cover.

- [ ] **Step 5: Write the documents**

- `node-9/README.md` gains the launch command and the registry port, beside the other fixed values.
- The repository `README.md` shell section names `--registry` and `--core-id`. It keeps `CORE_HOST` and `CORE_PORT` for local discovery.
- Check drift: `drift refs node-9/README.md`. Update prose before provenance.

- [ ] **Step 6: Commit and report**

```bash
git add node-9/README.md README.md
git commit -m "CHAT-yexqiyie: Document the registry and core id flags"
```

Then `fp comment CHAT-yexqiyie "..."` with the live result and every gap.
