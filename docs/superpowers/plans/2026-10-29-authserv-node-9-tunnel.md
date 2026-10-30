# Authserv access through the node-9 tunnel: implementation plan

> Implement this plan with one agent. Track progress in `CHAT-anegybnr`.
> The repository forbids subagent development and Markdown task checkboxes.

**Goal:** Add optional authserv access to the associated node-9 host through the existing SSH tunnel.

**Architecture:** `node-9-tunnel` owns the fourth SSH forward and readiness check.
`node-9-remote` owns offset selection and `AUTHSERV_PORT` for its local command.
Both commands keep their current output when `--authserv` is absent.

**Tools:** Bash, stub SSH tests, stub TCP tests, `drift`, and the existing token endpoint.

**Basis:** [Approved design](../specs/2026-10-29-authserv-node-9-tunnel-design.md), commit `de1a6dcb`.

## File responsibilities

| File | Responsibility |
|---|---|
| `node-9/bin/node-9-tunnel` | Parse `--authserv`. Forward and probe port `9000 + OFFSET`. Report the local token URL. |
| `shell-scripts/test-node-9-tunnel.sh` | Test direct calls, fourth-port readiness, failure text, and unchanged flag-off output. |
| `node-9/bin/node-9-remote` | Check the optional fourth local port. Export `AUTHSERV_PORT` and pass the flag to the tunnel. |
| `shell-scripts/test-node-9-remote.sh` | Test optional offset selection, environment ownership, persistent mode, and flag-off behavior. |
| `docs/SHELL-OPERATOR.md` | Show direct, automatic-offset, and persistent tunnel commands. |

The credential guide and two-agent assertion matrix remain separate work in `CHAT-anegybnr`.
This plan does not change authserv client registration, replay storage, or token lifetime.
The current `failed_tunnel_prints_ssh_text` case probes host ports through real `nc`.
A live listener can change that case's result, so its current pass count is environment-dependent.
The test repair must not stop a tunnel process with unknown ownership.

## Task 1: Add the optional forward to `node-9-tunnel`

**Files:** `shell-scripts/test-node-9-tunnel.sh`, `node-9/bin/node-9-tunnel`.

1. Record the current flag-off dry-run text from `node-9/bin/node-9-tunnel --dry-run node2.lan 100 non-persistent true`.
   The command starts no SSH process.
   Record the current non-persistent standard-error summary through the existing stub SSH test.
2. Add test cases to `shell-scripts/test-node-9-tunnel.sh` before changing the tunnel.
   Use the existing `run_tunnel` helper and stub SSH.
   The selected dry run must contain `-L 127.0.0.1:9100:127.0.0.1:9000`.
   It must name `http://127.0.0.1:9100/oauth2/token` and list `9100` in readiness output.
   A direct non-persistent call must name that URL on standard error.
   A direct persistent call must print only a numeric PID on standard output and name the URL on standard error.
   A misplaced `--authserv` must exit 2 before SSH starts.
   `--help` must name `--authserv` in the usage line.
   The flag-off dry run and standard-error summary must match their recorded values.
3. Add a readiness fixture under the test work directory.
   Put an executable `nc` stub on `PATH` for one selected test.
   Make the stub accept ports 6990, 6991, and 6992, and reject 9100.
   Set `CHAT_TUNNEL_READY_SECONDS=1` for that test.
   Expect exit 1, no local command, and a failure line that names all four ports.
   Use this selected-case assertion after `run_tunnel` captures the result:

   ```bash
   [[ $status -eq 1 && "$err" == *'6990 6991 6992 9100'* \
      && "$out" != *'LOCAL-COMMAND-RAN'* ]]
   ```

   Have the local command print `LOCAL-COMMAND-RAN` only if it runs.
   Add a positive selected case with an `nc` stub that accepts all four ports.
   Expect `LOCAL-COMMAND-RAN` and the local token URL on standard error.
   Apply an `nc` stub that rejects every port to `failed_tunnel_prints_ssh_text`.
   That case must show `SSH-FAILURE-MARKER` even when a host listener owns port 6990.
   Keep both stubs under the test work directory and add only their directory to `PATH`.
4. Run `bash shell-scripts/test-node-9-tunnel.sh`.
   Expected failure: selected cases cannot find the fourth forward or URL.
   The readiness case may return success before implementation because only three ports are probed.
5. Add `--authserv` to the existing option parser before `HOST`.
   Add it to the misplaced-option rejection after the fixed operands.
   Update `usage()` to list `[--authserv]`.
   Update the header comment to describe the optional fourth port and its offset rule.
   State that the flag must precede `HOST`.
   Keep the current argument parser shape and command argument rule.
   Compute `local_authserv=$((9000 + offset))` after the offset check.
   Append `-L "127.0.0.1:$local_authserv:127.0.0.1:9000"` before `-- "$host"` only when selected.
   Add the fourth port to the readiness loop and failure line only when selected.
   Add the token URL to dry-run output and to standard error for both runtime modes.
   Print the runtime URL before the persistent branch returns its PID.
   Keep every flag-off message byte-identical.
   Use the same port list for readiness and failure output:

   ```bash
   required_ports=("$local_rsocket" "$local_management" "$local_registry")
   (( authserv )) && required_ports+=("$local_authserv")
   ```
6. Run `bash shell-scripts/test-node-9-tunnel.sh`.
   Expected result: all old and new cases pass.
7. Run `git diff --check`.
   Commit this task with `CHAT-anegybnr` in the message.

The implementation seam is the existing `ssh_args` array, `tunnel_ready`, and status output.
Use one conditional port array for readiness and failure text so both show the same ports.
The direct command sets no `AUTHSERV_PORT`.
Its standard-error URL gives the selected port to the caller.

## Task 2: Select the optional port in `node-9-remote`

**Files:** `shell-scripts/test-node-9-remote.sh`, `node-9/bin/node-9-remote`.

1. Add a separate stub output file for `AUTHSERV_PORT` in `test-node-9-remote.sh`.
   Keep the current two-line `STUB_TUNNEL_ENV` file and its assertions intact.
   Clear an inherited `AUTHSERV_PORT` before the selected cases.
   Record the value in the tunnel stub with:

   ```bash
   printf '%s\n' "${AUTHSERV_PORT-unset}" > "${STUB_AUTHSERV_ENV:?}"
   ```
2. Add a selected case with `STUB_BUSY=9000`.
   Call `node-9-remote --authserv node2.lan non-persistent true` through `run_remote`.
   Expect offset 100, tunnel arguments `--authserv node2.lan 100 non-persistent true`, and `AUTHSERV_PORT=9100`.
   Confirm that the TCP stub checks 9000 at offset 0 and 9100 at offset 100.
   Add `printf '%s\n' "$*" >> "${STUB_TCP_CALLS:?}"` to the TCP stub.
   Pass `STUB_TCP_CALLS="$work/tcp.calls"` through `run_remote`.
3. Add a flag-off control with `STUB_BUSY=9000`.
   Clear an inherited `AUTHSERV_PORT` immediately before this control.
   Expect offset 0, the existing tunnel argument string, and no exported `AUTHSERV_PORT`.
   Add selected dry-run and persistent cases.
   Expect the flag before `HOST`, offset text on standard error, and no command in persistent mode.
   Check that `--help` names `[--authserv]` in the usage line.
4. Run `bash shell-scripts/test-node-9-remote.sh`.
   Expected failure: the parser rejects `--authserv`, and the selected offset and environment cases fail.
5. Add `--authserv` to the existing option parser before `HOST`.
   Add it to the misplaced-option rejection after the fixed operands.
   Update `usage()` to list `[--authserv]`.
   Update the header comment to describe the optional fourth-port check and `AUTHSERV_PORT`.
   State that the flag must precede `HOST`.
   Keep `offset_ports()` at three values for the existing `read -r` assignment.
   After the three-port TCP check succeeds, check `9000 + offset` when the flag is selected.
   Treat status 1 as a busy offset and status 2 as a failed port check.
   Export `AUTHSERV_PORT=$((9000 + offset))` only when selected.
   Add `--authserv` to `tunnel_args` before `HOST` only when selected.
   Keep the flag-off status line and all other output byte-identical.
   Preserve the three-value split with this pattern:

   ```bash
   ports="$(offset_ports "$offset")"
   read -r core_port core_mgmt_port registry_port <<< "$ports"
   (( authserv )) && export AUTHSERV_PORT="$((9000 + offset))"
   ```
6. Run `bash shell-scripts/test-node-9-remote.sh`.
   Expected result: all old and new cases pass.
7. Run both tunnel suites and `git diff --check`.
   Commit this task with `CHAT-anegybnr` in the message.

The implementation seam is `find_offset()` and the `tunnel_args` array.
`node-9-remote` is the only command that sets `AUTHSERV_PORT`.
Persistent mode has no local command, so its offset on standard error is the port source.

## Task 3: Document the operator path and verify the boundary

**Files:** `docs/SHELL-OPERATOR.md`.

1. Run `drift refs docs/SHELL-OPERATOR.md` before editing the guide.
   Review any binding before a provenance update.
2. Add an authserv section to `docs/SHELL-OPERATOR.md`.
   Show a direct `node-9-tunnel --authserv HOST OFFSET non-persistent CMD` call.
   State that direct calls read the local token URL from standard error.
   Show `node-9-remote --authserv HOST non-persistent CMD` with `AUTHSERV_PORT` in `CMD`.
   Show the persistent mode, which prints a PID and reports an offset on standard error.
   State that its authserv port is `9000 + OFFSET`.
   State that authserv must run on the associated node-9 host.
   State that authserv must reach each registered public JWKS HTTPS URI.
   State that the agent retains the private key outside the repository.
3. Run `bash shell-scripts/test-node-9-tunnel.sh` and `bash shell-scripts/test-node-9-remote.sh`.
   Expected result: both offline suites pass.
4. Run `git diff --check` and `drift check`.
   Expected result: both commands exit 0.
   Commit the guide with `CHAT-anegybnr` in the message.

## Live gate for this tunnel design

The offline suites cannot prove a token request through SSH.
Use a node-9 host that runs authserv on loopback port 9000.
Use a registered test agent whose public JWKS URI answers from that host.
The JWKS must contain the public key with the assertion's JOSE `kid`.
Keep the agent private key and signed assertion outside the repository.

1. Start one persistent tunnel with `node-9/bin/node-9-remote --authserv "$NODE9_HOST" persistent`.
   Save its PID from standard output and offset from standard error.
2. Set `AUTHSERV_PORT` to `9000 + OFFSET`.
   Have the test agent sign a fresh RS256 assertion for `http://127.0.0.1:$AUTHSERV_PORT/oauth2/token`.
   Set `iss` and `sub` to its client ID, with a short expiry and unique `jti`.
   Set the JOSE `kid` to the registered public key ID.
3. Put the assertion in `ASSERTION_FILE` outside the repository.
   Save the response in a temporary file with mode 600.
   Send the assertion with this request:

```sh
RESPONSE_FILE="$(mktemp "${TMPDIR:-/tmp}/authserv-tunnel-response.XXXXXX")"
chmod 600 "$RESPONSE_FILE"
curl --fail-with-body -sS \
  -d grant_type=client_credentials \
  -d "client_id=$CLIENT_ID" \
  -d scope=chat.mcp \
  -d client_assertion_type=urn:ietf:params:oauth:client-assertion-type:jwt-bearer \
  --data-urlencode "client_assertion@$ASSERTION_FILE" \
  "http://127.0.0.1:$AUTHSERV_PORT/oauth2/token" > "$RESPONSE_FILE"
jq -e '.scope == "chat.mcp" and (.access_token | type == "string")' "$RESPONSE_FILE"
```

4. Expect HTTP 200, response scope `chat.mcp`, and an access token with the test client ID.
   Decode the access token payload from standard input and check its `client_id`:

```sh
export CLIENT_ID
jq -r '.access_token' "$RESPONSE_FILE" | node -e '
let token = "";
process.stdin.on("data", part => token += part);
process.stdin.on("end", () => {
  const claims = JSON.parse(Buffer.from(token.trim().split(".")[1], "base64url"));
  if (claims.client_id !== process.env.CLIENT_ID) process.exit(1);
});'
```

   Send the same assertion again and expect HTTP 401 with `invalid_client`.
   `JdbcPrivateKeyAgentTests.kt:147-148` proves this replay response through the local endpoint.
   Use this replay request without `--fail-with-body`:

```sh
REPLAY_FILE="$(mktemp "${TMPDIR:-/tmp}/authserv-tunnel-replay.XXXXXX")"
chmod 600 "$REPLAY_FILE"
replay_status="$(curl -sS -o "$REPLAY_FILE" -w '%{http_code}' \
  -d grant_type=client_credentials \
  -d "client_id=$CLIENT_ID" \
  -d scope=chat.mcp \
  -d client_assertion_type=urn:ietf:params:oauth:client-assertion-type:jwt-bearer \
  --data-urlencode "client_assertion@$ASSERTION_FILE" \
  "http://127.0.0.1:$AUTHSERV_PORT/oauth2/token")"
[[ "$replay_status" == 401 ]]
jq -e '.error == "invalid_client"' "$REPLAY_FILE"
```

5. Stop only the tunnel process started in step 1.
   Remove both temporary response files.
   Record the host, offset, client ID, request status, token claim checks, and replay result in `CHAT-anegybnr`.
   Do not record keys, assertions, access tokens, or client secrets.

`JdbcPrivateKeyAgentTests` already proves a local token request with a matching audience.
This gate proves the forwarded request path and remains separate from that test.
If the host or registered JWKS is unavailable, report the live gate as unverified.
Do not mark the issue done from offline tunnel checks alone.

## Final review gate

Review the diff against the approved design and this plan.
Run both offline suites, `git diff --check`, and `drift check` again after the last edit.
Check the branch status and cite exact commit IDs in the FP handoff.
Keep two-agent acceptance and the broader assertion matrix open until their separate plan and evidence exist.
