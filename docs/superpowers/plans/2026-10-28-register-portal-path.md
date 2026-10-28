# Register Through the Portal — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. This project forbids subagent-driven development, so do not dispatch subagents. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The register command uses the live portal login when one exists, so a read or a write needs no password file.

**Architecture:** `setup-portal` publishes the live socket path in a small file. The MCP adapter accepts that file as the socket source and reads it per call. The register command resolves an agent and a project, finds the app config, and sends one MCP tool call to the adapter. A read may fall back to the one-shot login. A write never does.

**Tech Stack:** Bash (node-9 launchers), Kotlin (chat-mcp adapter), JUnit 5 and AssertJ, Maven, fp for tracking.

**Spec:** `docs/superpowers/specs/2026-10-28-register-portal-path-design.md`

**Issue:** CHAT-lmwuxiiq

## Global Constraints

- In `portal-only` mode, exactly one of `portalSocket` and `portalSocketFile` holds a value. Both set is an error. Neither set is an error.
- The register name rule is `^[a-z0-9][a-z0-9-]{0,63}$` and at most 37 characters.
- The adapter selector rule is `[A-Za-z0-9][A-Za-z0-9-]{0,63}`.
- The `register` limit stays 30 seconds. The adapter client deadline stays 35 seconds. The wrapper budget must stop the adapter first.
- The socket file holds one line: an absolute path. File mode 600. Directory mode 700.
- No default register name.
- A write never falls back to the one-shot path.
- All prose follows the controlled English rules of `AGENTS.md`.

## Review Focus

1. A stale socket file after a crash: a read falls back, a write stops.
2. A config name against an environment id: the command stops and names both values.
3. `register config --register-id` with a character that the adapter refuses: the command refuses first, so the adapter never starts with a bad config.
4. A register name at the 37 character bound: a name that would mint a topic past 64 characters is refused.
5. A portal restart between selection and the call: the adapter reads the new path, and a write does not retry.

---

### Task 1: The adapter accepts a socket file and reads it per call

**Files:**
- Modify: `chat-mcp/src/main/kotlin/com/demo/chat/mcp/config/McpLaunchConfig.kt`
- Modify: `chat-mcp/src/main/kotlin/com/demo/chat/mcp/config/AdapterConfigLoader.kt`
- Modify: `chat-mcp/src/main/kotlin/com/demo/chat/mcp/portal/PortalToolRegistration.kt`
- Modify: `chat-mcp/src/main/kotlin/com/demo/chat/mcp/McpAdapterServer.kt`
- Modify: `chat-mcp/src/main/kotlin/com/demo/chat/mcp/config/AdapterConfig.kt` (comment only, if needed)
- Test: `chat-mcp/src/test/kotlin/com/demo/chat/mcp/config/PortalOnlyConfigTests.kt`
- Test: `chat-mcp/src/test/kotlin/com/demo/chat/mcp/PortalOnlyToolTests.kt`

**Interfaces:**
- Produces: `PortalSocketSource` in package `com.demo.chat.mcp.config`:

```kotlin
sealed interface PortalSocketSource {
    fun resolve(): Path
    data class Fixed(val path: Path) : PortalSocketSource
    data class FileBacked(val file: Path) : PortalSocketSource
}
```

- Produces: `fun loadPortalSocketSource(properties: Properties): PortalSocketSource`.
- Changes: `McpLaunchConfig.PortalOnly(portalSocketSource: PortalSocketSource, registerSelector: String, registerName: String? = null)`.
- Changes: `fun registerPortalTool(server: Server, source: PortalSocketSource, selector: String, registerName: String? = null)`.
- Keeps: `AdapterConfig.portalSocket: Path?` for HTTP mode.

- [ ] **Step 1: Write the failing config tests**

Add to `PortalOnlyConfigTests.kt`:

```kotlin
@Test
fun `portal only reads the socket path from a file`() {
    val socketFile = directory.resolve("socket")
    Files.writeString(socketFile, "/tmp/portal.sock\n")
    val path = directory.resolve("adapter.properties")
    Files.writeString(path, "mode=portal-only\nportalSocketFile=$socketFile\nregisterName=register-index\n")

    val config = loadMcpConfigFile(path) as McpLaunchConfig.PortalOnly

    assertEquals(Path.of("/tmp/portal.sock"), config.portalSocketSource.resolve())
}

@Test
fun `portal only rejects both socket keys`() {
    val path = directory.resolve("adapter.properties")
    Files.writeString(
        path,
        "mode=portal-only\nportalSocket=/tmp/a.sock\nportalSocketFile=/tmp/b\nregisterName=r\n",
    )

    val failure = assertThrows(ConfigException::class.java) { loadMcpConfigFile(path) }

    assertTrue(failure.message!!.contains("portalSocket"), failure.message)
}

@Test
fun `portal only rejects neither socket key`() {
    val path = directory.resolve("adapter.properties")
    Files.writeString(path, "mode=portal-only\nregisterName=r\n")

    val failure = assertThrows(ConfigException::class.java) { loadMcpConfigFile(path) }

    assertTrue(failure.message!!.contains("portalSocket"), failure.message)
}

@Test
fun `portal only rejects a relative path in the socket file`() {
    val socketFile = directory.resolve("socket")
    Files.writeString(socketFile, "portal.sock\n")
    val path = directory.resolve("adapter.properties")
    Files.writeString(path, "mode=portal-only\nportalSocketFile=$socketFile\nregisterName=r\n")

    assertThrows(ConfigException::class.java) { loadMcpConfigFile(path) }
}

@Test
fun `portal only rejects an empty socket file`() {
    val socketFile = directory.resolve("socket")
    Files.writeString(socketFile, "")
    val path = directory.resolve("adapter.properties")
    Files.writeString(path, "mode=portal-only\nportalSocketFile=$socketFile\nregisterName=r\n")

    assertThrows(ConfigException::class.java) { loadMcpConfigFile(path) }
}
```

- [ ] **Step 2: Run the config tests and watch them fail**

Run: `mvn -q -pl chat-mcp -am test -Dtest=PortalOnlyConfigTests`
Expected: FAIL. The tests do not compile, because `PortalSocketSource` is absent.

- [ ] **Step 3: Add `PortalSocketSource` and the loader**

In `McpLaunchConfig.kt`, add the type above and change `PortalOnly` to hold it. In `AdapterConfigLoader.kt`:

- Add `portalSocketFile` to `PORTAL_ONLY_CONFIG_KEYS` alone.
- Add `loadPortalSocketSource(properties)`. `portalSocket` gives `Fixed`. `portalSocketFile` gives `FileBacked`.
- Both keys set throws `ConfigException("portalSocket and portalSocketFile are exclusive")`.
- Neither set throws `ConfigException("portalSocket or portalSocketFile is required in portal-only mode")`.
- `FileBacked.resolve()` reads the first line, drops one trailing `\n` or `\r\n`, trims nothing else, and rejects an empty value or a relative path with `ConfigException`.
- `loadMcpConfigFile` calls `loadPortalSocketSource`. It calls `resolve()` once at startup to fail early.

- [ ] **Step 4: Run the config tests and watch them pass**

Run: `mvn -q -pl chat-mcp -am test -Dtest=PortalOnlyConfigTests`
Expected: PASS.

- [ ] **Step 5: Write the failing per-call and transport tests**

Add to `PortalOnlyToolTests.kt` a test double and one test:

```kotlin
private class CountingSource(private val paths: List<Path>) : PortalSocketSource {
    var calls = 0
    override fun resolve(): Path = paths[minOf(calls++, paths.lastIndex)]
}

@Test
fun `each tool call resolves the socket again`() {
    val source = CountingSource(listOf(Path.of("/tmp/one.sock"), Path.of("/tmp/two.sock")))

    // Two calls to the registered read tool. No socket exists, so each call
    // answers UNAVAILABLE after resolve().
    runTwoReadCalls(source)

    assertEquals(2, source.calls)
}
```

Add a second test that a missing socket file answers `UNAVAILABLE`:

```kotlin
@Test
fun `a missing socket file answers unavailable`() {
    val source = PortalSocketSource.FileBacked(directory.resolve("gone"))

    val result = runOneRead(source)
    val code = (result.structuredContent as JsonObject)["error"]?.jsonObject?.get("code")

    assertEquals("\"UNAVAILABLE\"", code.toString())
}
```

Use the existing harness in `PortalOnlyToolTests.kt` for the server and the call. Name the helpers in the test file itself.

- [ ] **Step 6: Run the tool tests and watch them fail**

Run: `mvn -q -pl chat-mcp -am test -Dtest=PortalOnlyToolTests`
Expected: FAIL.

- [ ] **Step 7: Resolve the socket per call**

In `PortalToolRegistration.kt`, change the tool lambdas to call `source.resolve()` inside the existing `try`. Catch `java.io.IOException` and `ConfigException` and answer `portalAnswer(null, emptyList(), false, "UNAVAILABLE")`. Call `report(<tool>, call, started, "UNAVAILABLE", null)` for that case.

In `McpAdapterServer.kt`, pass the source through: `registerPortalTool(it, config.portalSocketSource, ...)`.

- [ ] **Step 8: Run the tool tests and the full adapter suite**

Run: `mvn -q -pl chat-mcp -am test`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add chat-mcp
git commit -m "CHAT-lmwuxiiq: Read the portal socket path per call"
```

---

### Task 2: `setup-portal` publishes the live socket path

**Files:**
- Modify: `node-9/bin/setup-portal` (pass `CHAT_PORTAL_SOCKET_FILE`)
- Modify: `shell-scripts/chat-shell-portal-tunnel` (write and remove the file)
- Test: `shell-scripts/test-setup-portal.sh`
- Test: `shell-scripts/test-chat-shell-portal-tunnel.sh`

**Interfaces:**
- Consumes: the ready path, after the portal binds the socket.
- Produces: `CHAT_PORTAL_SOCKET_FILE` names a file. It holds `$socket` and one newline. Mode 600. Its directory is mode 700.

- [ ] **Step 1: Write the failing start and stop tests**

In `test-chat-shell-portal-tunnel.sh`, with the existing stub portal, add checks:

```bash
# After a successful start.
socket_file="$work/state-parent/portal-fixture/socket"
[[ -f "$socket_file" ]] || bad 'socket file' 'the start wrote no socket file'
[[ "$(cat "$socket_file")" == "$socket" ]] || bad 'socket file value' 'the socket file holds another path'
[[ "$(perl -e 'printf "%o", (stat($ARGV[0]))[2] & 07777' "$socket_file")" == "600" ]] \
    || bad 'socket file mode' 'the socket file is not 600'

# After stop.
"$tunnel" stop
[[ ! -e "$socket_file" ]] || bad 'socket file removal' 'the stop left the socket file'
```

Add two more cases:

```bash
# A failed start writes no socket file.
# A stop leaves a socket file that names another socket.
```

- [ ] **Step 2: Run the tests and watch them fail**

Run: `shell-scripts/test-chat-shell-portal-tunnel.sh`
Expected: FAIL on the socket file checks.

- [ ] **Step 3: Write the file on ready and remove it on stop**

In `chat-shell-portal-tunnel`:

- Read `socket_file="${CHAT_PORTAL_SOCKET_FILE:-}"`.
- After the ready loop succeeds, before the ready line, write the file:

```bash
if [[ -n "$socket_file" ]]; then
    mkdir -p "$(dirname "$socket_file")"
    chmod 700 "$(dirname "$socket_file")"
    ( umask 077; printf '%s\n' "$socket" > "$socket_file" )
fi
```

- In `stop_all`, remove the file only when its first line equals `$socket`.
- In `node-9/bin/setup-portal`, set `CHAT_PORTAL_SOCKET_FILE="$HOME/.local/state/${project}/portal-${agent}/socket"` in the exec environment.

- [ ] **Step 4: Run the tunnel and setup tests**

Run: `shell-scripts/test-chat-shell-portal-tunnel.sh && shell-scripts/test-setup-portal.sh`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add node-9/bin/setup-portal shell-scripts/chat-shell-portal-tunnel shell-scripts/test-chat-shell-portal-tunnel.sh shell-scripts/test-setup-portal.sh
git commit -m "CHAT-lmwuxiiq: Publish the live portal socket path"
```

---

### Task 3: `register config` writes the app config

**Files:**
- Modify: `node-9/bin/register`
- Test: `shell-scripts/test-node-9-register.sh`

**Interfaces:**
- Produces: `register config --agent <a> --project <p> (--register <n> | --register-id <id>)`.
- Produces: helper `node9_register_id_error ID`. It prints one reason and returns 1, or returns 0 for a safe id. The rule is `^[A-Za-z0-9][A-Za-z0-9-]{0,63}$`.
- Produces: file `~/.config/demo-chat/mcp-portal-<agent>-<project>.properties` with mode 600.

- [ ] **Step 1: Write the failing checks**

Add to `test-node-9-register.sh`:

```bash
# config writes the file
out="$(XDG_CONFIG_HOME="$work/config" "$wrapper" config --agent Theta --project demo-chat --register demo-chat-register)"
file="$work/config/demo-chat/mcp-portal-theta-demo-chat.properties"
[[ -f "$file" ]] || bad 'config file' 'no file was written'
grep -qx 'mode=portal-only' "$file" || bad 'config mode' 'mode is absent'
grep -qx 'registerName=demo-chat-register' "$file" || bad 'config register' 'the name is absent'
grep -q '^portalSocketFile=.*/portal-theta/socket$' "$file" || bad 'config socket' 'the socket file is absent'
[[ "$(perl -e 'printf "%o", (stat($ARGV[0]))[2] & 07777' "$file")" == "600" ]] \
    || bad 'config mode bits' 'the file is not 600'

# no register resolved is an error
XDG_CONFIG_HOME="$work/config" "$wrapper" config --agent Theta --project demo-chat 2> "$work/err"
[[ $? == 2 ]] || bad 'config no register' 'the command did not fail with 2'
grep -q 'set --register or --register-id' "$work/err" || bad 'config no register line' 'the message is wrong'

# an uppercase name is refused
# an underscore id is refused
# a 38 character name is refused
# a uuid id is accepted and written as registerId
# a missing --agent is refused
```

- [ ] **Step 2: Run the tests and watch them fail**

Run: `shell-scripts/test-node-9-register.sh`
Expected: FAIL. `config` is an unknown command.

- [ ] **Step 3: Implement the verb and the id rule**

In `node-9/bin/register`:

- Add `config` to the case list and to `usage`.
- Parse `--agent` and `--project`, both required.
- Reject a bad agent with `^[A-Za-z][A-Za-z0-9_-]*$`. Reject a bad project with `^[A-Za-z0-9][A-Za-z0-9_-]*$`.
- Lowercase both for the file name.
- Resolve the selector in this order: `--register`, `--register-id`, `NODE_9_REGISTER_TOPIC_NAME`, `NODE_9_REGISTER_TOPIC_ID`. A name wins over an id.
- A missing selector prints:
  `register: set --register or --register-id, or set NODE_9_REGISTER_TOPIC_NAME or NODE_9_REGISTER_TOPIC_ID.`
  It returns 2.
- `--register` uses `node9_register_name_error`.
- `--register-id` uses `node9_register_id_error`. Add it to `node-9/bin/lib.sh` beside the name rule. The comment must say why: the adapter applies this rule to both selector keys.
- Compute `config_dir="${XDG_CONFIG_HOME:-$HOME/.config}/demo-chat"`. Create it with mode 700.
- Write a temporary file with umask 077, then `mv` it over the target.
- Print the written path.

- [ ] **Step 4: Run the tests and watch them pass**

Run: `shell-scripts/test-node-9-register.sh`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add node-9/bin/register node-9/bin/lib.sh shell-scripts/test-node-9-register.sh
git commit -m "CHAT-lmwuxiiq: Write the register app config"
```

---

### Task 4: The register command uses the portal

**Files:**
- Modify: `node-9/bin/register`
- Test: `shell-scripts/test-node-9-register.sh`

**Interfaces:**
- Consumes: the app config of Task 3, the socket file of Task 2, and the adapter of Task 1.
- Produces: flags `--agent` and `--project` on `read`, `open`, and `post`.
- Produces: helpers `portal_socket_path`, `config_selector`, `environment_selector`, `selector_check`, `run_portal_tool`.
- Produces: `CHAT_MCP_STDIO` selects the adapter launcher.

- [ ] **Step 1: Write the failing selector checks**

Add to `test-node-9-register.sh`:

```bash
# Equal names pass.
# Equal ids pass.
# A config name against an environment id stops with 2 and names both.
NODE_9_REGISTER_TOPIC_ID=aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee \
    "$wrapper" read --agent Theta --project demo-chat 2> "$work/err"
[[ $? == 2 ]] || bad 'mixed selector status' 'the command did not stop'
grep -q 'cannot compare a register name with a register id' "$work/err" \
    || bad 'mixed selector line' 'the message is wrong'

# A config that holds both keys stops with 1.
# A missing socket file falls back for read.
# A missing socket file stops for open.
# A transport failure stops for post and does not call the one-shot path.
# A post whose minted topic passes 64 characters answers INVALID_INPUT and status 2.
```

Add a stub adapter beside the shell stub:

```bash
cat > "$work/bin/chat-mcp-stdio" <<'STUB'
#!/usr/bin/env bash
set -uo pipefail
dir="${ADAPTER_DIR:?Set ADAPTER_DIR}"
count=0
[[ -f "$dir/count" ]] && count="$(cat "$dir/count")"
count=$((count + 1))
printf '%s\n' "$count" > "$dir/count"
cat > "$dir/request.$count"
cat "$dir/reply" 2>/dev/null || true
exit "${ADAPTER_STATUS:-0}"
STUB
chmod +x "$work/bin/chat-mcp-stdio"
```

The reply fixture is one MCP result line with the `structuredContent` shape of `docs/MCP-ADAPTER.md`.

- [ ] **Step 2: Run the tests and watch them fail**

Run: `shell-scripts/test-node-9-register.sh`
Expected: FAIL.

- [ ] **Step 3: Implement resolution, the selector check, and the adapter call**

In `node-9/bin/register`:

- Accept `--agent` and `--project` anywhere an option is accepted.
- `register` defaults: agent from `MY_AGENT_NAME`, project from `MY_PROJECT_NAME`, else the base name of `MY_PROJECT_HOME`.
- Config path: `~/.config/demo-chat/mcp-portal-<agent>-<project>.properties`, lower case.
- When the config is absent, run the one-shot path, as today.
- Read `portalSocketFile` from the config and take the first line.
- `config_selector` prints `name=<value>` or `id=<value>`. A config with both keys is an error with status 1.
- `environment_selector` reads `NODE_9_REGISTER_TOPIC_NAME` and `NODE_9_REGISTER_TOPIC_ID`.
- `selector_check` applies this table:

```text
config name + env name      exact match passes, a difference stops with 2
config id   + env id        exact match passes, a difference stops with 2
config name + env id        stops with 2
config id   + env name      stops with 2
one side absent             no check
```

The mixed form prints:
`register: cannot compare a register name with a register id. Clear one selector.`
A proven difference prints:
`register: the config register '<a>' differs from the environment register '<b>'.`

- When the portal path is chosen and `--as` is set, print on standard error:
  `register: user selection is ignored when connected through portal`
- `run_portal_tool TOOL JSON` starts the adapter from `CHAT_MCP_STDIO`, else the installed `bin/chat-mcp-stdio`, else the source tree. It sends `initialize`, `notifications/initialized`, and one `tools/call` with `id` 2. It waits at most the remaining wrapper budget. It prints the `id:2` line.
- Parse the reply with `jq`. Emit `structuredContent.lines[]` through `emit_typed`.
- Status mapping when the reply has no numeric `status`:

```text
INVALID_INPUT 2   WRITE_FAILED 1   NOT_OPEN 1   UNCONFIRMED 75
BUSY 1            TIMEOUT 124      UNAVAILABLE 1
```

- Fallback rules:
  - The socket file is absent, or the adapter answers `UNAVAILABLE`: a read runs the one-shot path and prints
    `register: portal unavailable, using the one-shot login`. A write stops with the error.
  - Any other error code stops. It never runs the one-shot path.

- [ ] **Step 4: Run the tests and watch them pass**

Run: `shell-scripts/test-node-9-register.sh`
Expected: PASS.

- [ ] **Step 5: Run the neighboring shell suites**

Run: `shell-scripts/test-chat-libs.sh && shell-scripts/test-setup-portal.sh && shell-scripts/test-chat-shell-portal-tunnel.sh`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add node-9/bin/register shell-scripts/test-node-9-register.sh
git commit -m "CHAT-lmwuxiiq: Route the register command through the portal"
```

---

### Task 5: Documents and the live proof

**Files:**
- Modify: `node-9/README.md`
- Modify: `docs/MCP-ADAPTER.md`
- Modify: `docs/SHELL-COMMAND-CONTRACT.md`
- Modify: `CHAT_MCP_AGENTS.md`
- Modify: `forward-register.md`
- Create: `docs/superpowers/proofs/2026-10-28-register-portal-path/RECORD.md`
- Create: `docs/superpowers/proofs/2026-10-28-register-portal-path/acceptance.sh`

**Interfaces:**
- Consumes: the behavior of Tasks 1 to 4.
- Produces: a proof that uses only private filesystem paths. It starts its own portal and stops only that portal. It never moves a shared file and never stops a portal it did not start.
- The proof shares the backend register store by design. It creates one uniquely named register per run and deletes nothing.

- [ ] **Step 1: Update the prose first**

- `node-9/README.md`: the socket tree, the socket file, and the rule that `setup-portal` sees no register.
- `docs/MCP-ADAPTER.md`: the `portalSocketFile` key, the exclusivity rule, the per-call read, and `register config` as the config producer.
- `docs/SHELL-COMMAND-CONTRACT.md`: the portal path, the selector check, the warning, the fallback table, and the status mapping.
- `CHAT_MCP_AGENTS.md`: the register command now has a safe portal path.

- [ ] **Step 2: Check the drift bindings**

Run: `drift refs docs/MCP-ADAPTER.md docs/SHELL-COMMAND-CONTRACT.md node-9/README.md`
Expected: the list of bound symbols. Record any binding whose prose changed.

- [ ] **Step 3: Refresh provenance after the prose is correct**

Run: `drift link` for each changed binding, then `drift check`.
Expected: exit 0.

- [ ] **Step 4: Write the isolated live proof script**

`acceptance.sh` uses one private root. Its filesystem paths are private. The
backend register store is shared.

```bash
proof_root="$(mktemp -d)"
socket_dir="$proof_root/portal"
state_dir="$proof_root/state/node2-portal-theta"
socket_file="$proof_root/state/portal-theta/socket"
config_home="$proof_root/config"
empty_secrets="$proof_root/empty-secrets"
one_shot_stub="$proof_root/bin/chat-shell-cli"
register_name="demo-chat-register-proof-$(date +%H%M%S)$(printf '%02x' $((RANDOM % 256)))"
```

The register name is 33 characters. The rule allows 37. A run in a new second
gets a new name. The proof deletes no register.

It does this, and prints one line per check:

1. Start one portal with `shell-scripts/chat-shell-portal-tunnel start` and these values:
   `CHAT_SHELL_USER=Theta`,
   `CHAT_SHELL_PASSWORD_FILE` from `PROOF_PASSWORD_FILE` or the installed
   `secrets/Theta.password` (read in place, never copied),
   `CHAT_PORTAL_SOCKET_DIR="$socket_dir"`,
   `CHAT_PORTAL_STATE_DIR="$state_dir"`,
   `CHAT_PORTAL_SOCKET_FILE="$socket_file"`.
   Read no other portal. Stop no other portal.
2. Write `$proof_root/ownership.txt` with the socket path, the state path,
   the register name, and `portal.pid` and `tunnel.pid`.
   The ownership check reads the two process command lines and applies two
   separate rules:
   - The portal pid is alive, and its command line holds
     `-Dchat.shell.portal.socket=$socket_dir/p`.
   - The tunnel pid is alive, and its command line holds `-- $host` and
     `127.0.0.1:6890`.
   A failed check stops the proof before any stop command.
3. Create the controlled register `$register_name` with
   `register-create` and a fixture markdown file in the proof directory.
   It uses `--source-date 2026-10-28` and `--as Mario`. The owner password
   comes from `NODE9_SECRETS_FILE` or `PROOF_OWNER_PASSWORD_FILE`. The script
   reads it in place and copies nothing.
   Each run leaves one register. `RECORD.md` names it. The proof deletes
   nothing, because no delete command exists. Retention: keep the register,
   or clear the node-9 data at the next reinstall.
4. Write the app config into the private `XDG_CONFIG_HOME`:
   `env -u NODE_9_REGISTER_TOPIC_NAME -u NODE_9_REGISTER_TOPIC_ID register config --agent Theta --project demo-chat --register "$register_name"`.
   The unset variables keep the selector check out of this step.
5. Point `NODE9_SECRETS_DIR` at `$empty_secrets`. This makes a silent
   one-shot fallback fail. Run `register read --agent Theta --project demo-chat`.
   Expect status 0 and JSON lines. This proves the portal path needs no
   password.
6. Open the pointer for the fixture key:
   `register open proof-key proof-entry "Proof entry" --agent Theta --project demo-chat`.
   Expect one `open` line with state `created`.
7. Post the fixture text on standard input:
   `register post proof-key --agent Theta --project demo-chat`.
   Expect one `post` line.
8. Read again and expect the pointer of `proof-key`.
9. Stop the proof portal with `shell-scripts/chat-shell-portal-tunnel stop`,
   the same private state, socket, and socket-file values, and only after
   both ownership checks pass. The script repeats its own pid checks. It stops
   no other portal.
10. Point `CHAT_SHELL_CLI` at the stub `$one_shot_stub`. The stub prints one
    fixture record and records its arguments. Point `NODE9_SECRETS_DIR` at
    `$proof_root/stub-secrets`, which holds a fixture `Theta.password` with
    mode 600. This is a test value, not a real secret. Run
    `register read --as Theta --agent Theta --project demo-chat` with the
    private socket file removed. Expect the line
    `register: portal unavailable, using the one-shot login`, status 0, and
    one stub call. The stub call proves the one-shot path, and it uses no
    real credential.
11. Print `portal_read=ok portal_write=ok fallback=ok`.

The script removes `$proof_root` at exit. It removes no file outside that
root. It creates no register other than `$register_name`.

- [ ] **Step 5: Run the proof and record it**

Run: `docs/superpowers/proofs/2026-10-28-register-portal-path/acceptance.sh`
Expected: `portal_read=ok portal_write=ok fallback=ok`

Write `RECORD.md` with the date, the commands, the outputs, the portal
ownership record, the created register name and its retention note, and the
note that the fallback leg used a stub shell.

- [ ] **Step 6: Run the full verification**

Run: `just ci-local`
Expected: the same command as the CI build job, exit 0.

Run: `just check-register && just check-setup-portal && just check-chat-shell-portal-tunnel && just check-chat-libs && just check-chat-mcp-installed`
Expected: exit 0.

Run: `drift check`
Expected: exit 0.

The partial Maven commands in earlier tasks are for fast feedback. This step
is the full-suite gate that the issue requires.

- [ ] **Step 7: Commit**

```bash
git add node-9/README.md docs/MCP-ADAPTER.md docs/SHELL-COMMAND-CONTRACT.md CHAT_MCP_AGENTS.md forward-register.md docs/superpowers/proofs/2026-10-28-register-portal-path
git commit -m "CHAT-lmwuxiiq: Document the register portal path"
```

---

## Self-Review Notes

- Spec coverage: the platform file (Task 2), the app config (Task 3), the adapter key and per-call read (Task 1), the command behavior and selector check (Task 4), docs and proof (Task 5).
- The selector check is defined for every name and id pair. A mixed pair stops. The command never guesses.
- `register config --register-id` applies the adapter selector rule, so a value the adapter would refuse never reaches a config file.
- Writes never fall back. The one-shot write runs only when the app config is absent at selection time.
- The live proof uses one private filesystem root. It starts one portal, records its pids and paths, applies one ownership rule to each process, and stops only that portal. It moves no shared file and copies no real secret. It shares the backend store and leaves one uniquely named register per run, recorded in `RECORD.md`.
- The full-suite gate is `just ci-local`, the named shell checks, and `drift check`. The partial Maven commands serve fast feedback in the early tasks.
