# Review hand-off: the register portal path

Issue: `CHAT-lmwuxiiq`.
Branch: `chat-lmwuxiiq-portal-path`.
Range: `93a127b6..3ad649bc`, twelve commits. The hand-off commit follows
the code commit.
Plan: `docs/superpowers/plans/2026-10-28-register-portal-path.md`.
Spec: `docs/superpowers/specs/2026-10-28-register-portal-path-design.md`.
Proof: `docs/superpowers/proofs/2026-10-28-register-portal-path/RECORD.md`.
Author: Theta. Reviewer: Sigma. The author wrote no subagent review.

## What the branch does

1. The `register` command reads, opens, and posts through the portal.
   The portal needs no password.
2. The adapter takes a socket file, and it reads the file on every call.
   A portal restart needs no adapter restart.
3. `register config` writes the app config that names the socket file and
   one register selector.
4. A read falls back to the one-shot login when the portal is absent.
   A write never falls back.

## Commits

```text
4db6dcf6 CHAT-lmwuxiiq: Add the register portal path design and plan
b81f9b7c CHAT-lmwuxiiq: Read the portal socket path per call
0bd3c559 CHAT-lmwuxiiq: Publish the live portal socket path
312120fa CHAT-lmwuxiiq: Write the register app config
8cd58052 CHAT-lmwuxiiq: Route the register command through the portal
4341de19 CHAT-lmwuxiiq: Hold the adapter stream until the portal reply
2dc25546 CHAT-lmwuxiiq: Document the register portal path
3ea69f7a CHAT-lmwuxiiq: Prove the register portal path
019ea8f3 CHAT-lmwuxiiq: Correct the proof record and the deadline prose
8f0b20a5 CHAT-lmwuxiiq: Write the review hand-off
912265bd CHAT-lmwuxiiq: Record the proof-root cleanup
3ad649bc CHAT-lmwuxiiq: Reject a protocol failure in the portal reply
```

## Gate evidence

| Check | Command | Result |
|---|---|---|
| Full build and tests | `just ci-local` | exit 0, `BUILD SUCCESS`, 451 test reports |
| Register suites | `just check-register` | exit 0, 118 checks |
| Portal setup | `just check-setup-portal` | exit 0 |
| Tunnel socket rules | `just check-chat-shell-portal-tunnel` | exit 0 |
| Complete-read rule | `just check-chat-libs` | exit 0 |
| Installed launcher | `just check-chat-mcp-installed` | exit 0 |
| Doc provenance | `drift check` | exit 0 |
| Live proof | `acceptance.sh` | exit 0, ten checks |

The five shell suites ran together with exit 0. The proof printed
`portal_read=ok portal_write=ok fallback=ok` and
`register=demo-chat-register-proof-125301e3`.

## Review focus, from the plan

1. **A stale socket file after a crash.** A read falls back and a write stops.
   Covered by `a_transport_failure_stops_for_post` and
   `a_missing_socket_falls_back_for_read`. The socket file holds a path to a
   dead socket, so the adapter answers `UNAVAILABLE`.
2. **A config name against an environment id.** The command stops and names
   both values. Covered by `portal_stops_on_a_mixed_selector`.
3. **`register config --register-id` with a character that the adapter
   refuses.** The command refuses first, so the adapter never starts with a
   bad config. Covered by `node9_register_id_error` and its bootstrap cases.
4. **A register name at the 37 character bound.** A short name that would mint
   a topic past 64 characters is refused. Covered by
   `open refuses a topic name past 64 characters` in
   `PortalWriteServiceTests`.
5. **A portal restart between selection and the call.** The adapter reads the
   new path and a write does not retry. Covered by
   `each tool call reads the socket file again` and
   `a missing socket file answers unavailable`.

## Three defects, and how each was found

The first two came from the live proof. The third came from the Sigma review.

Both have a test that failed first.

1. `PortalWriteService` answered `WRITE_FAILED` with no cause. The operator
   could not see why a write stopped. `PortalReply.detail` existed and was
   never set. The service now fills it from the refusal message or the
   exception. The field never reaches the wire.
   Tests: `a failed post names its cause in the diagnostic detail` and
   `a refusal names its reason in the diagnostic detail`.
2. The adapter derived the post request id from the key and the text alone.
   The same key and text in another register made the same id, and the core
   refused it. The register selector is now part of the hash input.
   Test: `a post in another register sends a different request id`.
3. The wrapper ignored a JSON-RPC error in the adapter reply, and it exited 0
   with no lines. A failed read looked empty. The wrapper now answers the
   local code `ADAPTER_ERROR` with status 1 for three shapes: a JSON-RPC
   error, a reply with no structured content, and a refusal with no code.
   Tests: `a_json_rpc_error_stops_the_read`,
   `a_refusal_without_structured_content_stops`, and
   `a_json_rpc_error_stops_the_post`. The register suite holds 118 checks
   with no failure. Commit `3ad649bc`.

## Rulings I made

Each line names the decision, the reason, and the cost if the decision is
wrong.

1. **Socket path spelling.** The stable socket file path lower-cases the agent
   (`portal-theta/socket`). The legacy state and socket directories keep their
   spelling (`node2-portal-Theta`). Reason: the spec makes both names lower
   case in the new path. Cost: one rename of a three-segment path.
2. **Stub portal.** The test stub reads `CHAT_SHELL_PORTAL_SOCKET` from the
   environment, because the tunnel passes an environment variable and not a
   program argument. Cost: none, the stub is test-only.
3. **Two negative config cases.** They remove the file that an earlier case
   wrote, then assert its absence. Reason: a refused write leaves no config.
   Cost: none, test-only.
4. **Flag order.** `--agent` and `--project` follow the verb, because the
   wrapper reads its first word as the verb: `register read --agent Theta`.
   Cost: one relaxed parser if a caller wants the flags first.
5. **Register create runs on the node.** `register-create` needs a local CQL
   client, so the proof runs it over ssh with the node's own secrets. The
   fixture goes to a temporary node path, and the script removes it.
   Cost: one leftover file on the node if a run fails between the copy and the
   removal.
6. **Import read target.** The node's median import read is about 10.2 s
   against a 10 s gate. The proof sets `REGISTER_IMPORT_READ_TARGET_MS=20000`
   for the create step. Reason: the gate belongs to `register-import`, not to
   the portal path, and each wrapper call starts a JVM. Cost: a slow backend
   could pass a step that the default target would refuse.
7. **Adapter stream.** The wrapper closed the adapter's standard input at
   once, so the adapter exited after `initialize` and never answered
   `tools/call`. The wrapper now holds the stream open until the `id:2` reply.
   A regression test covers it (`portal_keeps_the_adapter_stream_open`).
   Cost: none, the defect was real and reproduced.
8. **Proof fixture heading.** The heading was the defect, not the portal or
   the wrapper. `## Proof entry` made `register-create` mint a topic that the
   pointer step owned by another key. The heading is now
   `## Imported section`. Cost: a proof that masks a real ownership defect.
   The direct probe and the passing run are the evidence against that.
9. **Proof socket file path.** The proof points `HOME` at a private root, so
   the app config path `$HOME/.local/state/<project>/portal-<agent>/socket`
   stays private and the config finds it. Cost: one extra `HOME` variable.
10. **`forward-register.md` stays frozen.** `AGENTS.md` freezes it at
    `2d75f0cd` and names the node-9 topic register as the current work record.
    The project instruction outranks the plan's file list. The branch changes
    no byte of the file. Cost: a reader who expects the frozen file to move
    finds no change there.
11. **Step-8 jq check.** `select(...) | test(...)` made `jq -e` read the last
    produced value, so a correct read failed. Steps 5, 7, and 8 now use
    `any(.[]; ...)` with `--slurp`. Cost: a proof that reports a false failure.
12. **Deadline prose.** The command contract said the wrapper passes its
    budget to the adapter and that the adapter stops first. The code is the
    other way around: the wrapper budget is 30 s and the adapter deadline is
    35 s, so the wrapper sends `TERM` then `KILL`. Corrected. Cost: a reader
    who trusts the old text sets a deadline that the adapter cannot reach.

## Deferred minors

None open. The earlier minor, `portal_finish` ignoring `complete=false` when
no error code is present, is closed by the `ADAPTER_ERROR` rule: an
`isError` reply with no code now stops the command.

## Known limits

- The author wrote this review pass. No fresh reviewer read the branch.
  The project instruction forbids subagent-driven development.
- The proof's fallback leg uses a stub shell. It proves that the one-shot
  path runs. It does not prove a live one-shot login.
- The proof shares the backend register store. It left
  `demo-chat-register-proof-125301e3`. Earlier runs left six more:
  `-112952ed`, `-113613d2`, `-114218ee`, `-11574420`, `-122754e5`,
  `-12350537`. Keep them, or clear the node-9 data at the next reinstall.
- The proof needs `just build-shell-cli` and `just build-mcp-stdio` first.

## Suggested review order

1. `node-9/bin/register`: the portal resolution block, `run_portal_tool`,
   `portal_read`, `portal_open`, `portal_post`, and `register_config`.
2. `chat-mcp/src/main/kotlin/com/demo/chat/mcp/config/McpLaunchConfig.kt`
   and `AdapterConfigLoader.kt`: `PortalSocketSource`.
3. `chat-shell/.../PortalWriteService.kt`: the write-failure detail.
4. `chat-mcp/.../PortalToolRegistration.kt`: the derived request id.
5. `docs/SHELL-COMMAND-CONTRACT.md` and `docs/MCP-ADAPTER.md`: the behaviour
   tables against the code.
