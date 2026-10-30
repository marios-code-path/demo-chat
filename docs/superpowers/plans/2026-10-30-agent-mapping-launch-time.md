# Agent client mappings at launch time: implementation plan

> Implement this plan with one agent. Track progress in `CHAT-vidnpywh`.
> The repository forbids subagent development and Markdown task checkboxes.

**Goal:** Move the authserv agent client mapping from the image build to launch time.

**Architecture:** `chat-build` owns the agent JSON output and the `--build` refusal.
The authserv compose service owns the `SPRING_APPLICATION_JSON` passthrough.
`AgentClientProperties` owns the binding, and it changes in no way.

**Tools:** Python, Bash, `shell-scripts/test-flags.sh`, Kotlin tests, `docker compose config`, and `drift`.

**Basis:** [Approved design](../specs/2026-10-30-agent-mapping-launch-time-design.md), commit `94ade97c`.
Approved on 2026-10-30. The approval covers the design and planning. It does not cover implementation.
[Precedence proof](../proofs/2026-10-30-agent-mapping-precedence/RECORD.md), six cases, 0 failures.

## One stated deviation from the approved design

The design states exit 2 for each new refusal. This plan uses **exit 1**.

The repository already refuses two bake attempts, and both use exit 1:

1. `shell-scripts/chat-build:1389` refuses `authserv --build --jwk`. Its text reads `--jwk cannot be baked into an image`.
2. `shell-scripts/chat-build:1458` refuses `core --build --index-root`. Its text reads `--index-root cannot be baked into an image`.

Exit 2 in `chat-build` belongs to argument shape errors and to a missing key type.
Exit 1 belongs to a refused flag combination.
The new refusal is a refused combination, so exit 1 keeps one meaning for one code.
The refusal text follows the same `cannot be baked into an image` wording.

The owner must accept this deviation before Task 1 starts.

## File responsibilities

| File | Responsibility |
|---|---|
| `shell-scripts/chat-build` | Add `--agent-json`. Refuse `--agent` and `--agent-jwks-uri` for `authserv --build`. |
| `shell-scripts/test-flags.sh` | Add the refusal cases, the JSON case list, and the core control case. |
| `shell-scripts/golden/` | Hold the agent JSON golden and the core control flags golden. |
| `chat-authorization-server/docker-compose.yaml` | Pass `SPRING_APPLICATION_JSON` to the authserv service. |
| `justfile` | Forward one operator variable to the compose call in `deploy-authserv`. |
| `shell-scripts/test-authserv-profile.sh` | Prove the compose service carries the variable, set and unset. |
| `chat-authorization-server/src/test/kotlin/com/demo/chat/AgentLaunchBindingTests.kt` | New. Bind `AgentClientProperties` from launch JSON. |
| `docs/MCP-CREDENTIAL-ISSUANCE.md` | State the launch procedure and the refused flag. |

`AgentClients.kt` and `AgentClientProperties.kt` do not change.
The authorization server keeps its registration behavior, its replay storage, and its token lifetime.
Core and rest keep their present agent behavior.

## Task 1: Refuse the baked agent mapping for authserv

**Files:** `shell-scripts/test-flags.sh`, `shell-scripts/chat-build`.

1. Record the current result first.
   Run `./shell-scripts/test-flags.sh` and record the pass count and the fail count.
   The baseline on 2026-10-30 at `71042f11` reads `flags: all 57 case(s) match`.
2. Add two cases to `REFUSALS` in `shell-scripts/test-flags.sh` before any code change.
   Use the existing `name|arguments|exit|text` shape.

   ```
   "refuse-agent-build|authserv --build --notls --long --node-id 0 --agent a=Agent|1|--agent cannot be baked into an image"
   "refuse-agent-jwks-build|authserv --build --notls --long --node-id 0 --agent a=Agent --agent-jwks-uri a=https://example.test/jwks|1|--agent cannot be baked into an image"
   ```

   `authserv --build` takes no `--jwk`, because `chat-build:1389` already refuses that pair.
3. Add one control case to `CASES`.

   ```
   "core-build-image-agent|core --memory --build --notls --long --node-id 0 --jwk $GOLDEN_JWK --agent 5b2c9e1a-3f47-4d8e-9a61-0c7d2e8f4b13=Agent"
   ```

   This case proves core keeps its baked mapping. The refusal must not reach core.
4. Run `./shell-scripts/test-flags.sh`.
   The two refusal cases must fail now.
   The control case must fail with `no golden`.
   Record both failures as the failing-test-first evidence.
5. Create the control golden with `./shell-scripts/test-flags.sh --update core-build-image-agent`.
   Read the written file. It must hold each `-Dapp.security.agents[0]` flag and each `-Dapp.init.initial-users` flag.
   Commit that golden with this task.
6. Add the refusal to `shell-scripts/chat-build` inside the existing `if args.service == "authserv":` block.
   Put it next to the `--jwk` refusal at line 1389, so one block holds every authserv bake rule.
   Refuse when `args.build` holds and `args.agent` holds any value.
   Refuse when `args.build` holds and `args.agent_jwks_uri` holds any value.
   Write one message to standard error. Name `--agent-json` and the compose variable in it.
   Return 1.
7. Run `./shell-scripts/test-flags.sh`. Every case must pass.
   The recorded pass count must rise by three, and the fail count must stay at zero.

## Task 2: Add the `--agent-json` output mode

**Files:** `shell-scripts/test-flags.sh`, `shell-scripts/chat-build`, `shell-scripts/golden/`.

1. Add a `JSON_CASES` array and its loop to `shell-scripts/test-flags.sh` before any code change.
   The loop must not append `--dry-run`, because the mode prints and exits.
   The loop compares standard output against `golden/<name>.json`.
   It honours `--update` and `ONLY` exactly as the `CASES` loop does.
   Add one case with two agents and one JWKS URI:

   ```
   "authserv-agent-json|authserv --agent-json --notls --long --node-id 0 --agent a=Agent --agent b=Claude --agent-jwks-uri a=https://example.test/a/jwks"
   ```

2. Add three cases to `REFUSALS`:

   ```
   "refuse-agent-json-service|core --memory --agent-json --notls --long --node-id 0 --agent a=Agent|1|--agent-json requires the authserv service"
   "refuse-agent-json-no-agent|authserv --agent-json --notls --long --node-id 0|1|--agent-json needs at least one --agent"
   "refuse-agent-json-build|authserv --agent-json --build --notls --long --node-id 0 --agent a=Agent|2|not allowed with argument"
   ```

   The third case relies on the argparse group, so it keeps exit 2.
3. Run `./shell-scripts/test-flags.sh`. Each new case must fail. Record that evidence.
4. Add `--agent-json` to the existing goal group in `shell-scripts/chat-build` near line 1232.
   The group already holds `--run` and `--build`, so argparse refuses `--agent-json --build`.
   Write the help text as one sentence under 25 words.
5. Make `main()` handle the mode before any Maven work and before any env file write.
   Refuse a service other than authserv. Refuse an empty agent list.
   Print one JSON object to standard output. Exit 0.
   The object must hold `app.oauth2.agents` as a list and `app.oauth2.agent-scope` as a string.
   Each list entry holds `client-id` and `username`. An entry holds `jwk-set-uri` only when one exists.
   Print with `json.dumps(..., indent=2, sort_keys=True)` and one closing newline.
   Reuse `parse_agents` and the existing `--agent-jwks-uri` validation, so one rule serves both modes.
6. Create the golden with `./shell-scripts/test-flags.sh --update authserv-agent-json`.
   Read the file. It must hold two agents, one `jwk-set-uri`, and the scope `chat.mcp`.
7. Run `./shell-scripts/test-flags.sh`. Every case must pass.

## Task 3: Pass the launch JSON to the authserv container

**Files:** `chat-authorization-server/docker-compose.yaml`, `justfile`, `shell-scripts/test-authserv-profile.sh`.

1. Record the current `docker compose config` output for the authserv service.
   Use `CHAT_NETWORK` and every variable that the current recipe sets.
   Parse the `services.authserv.environment` mapping out of that output.
   Keep that mapping as the control value. Do not keep the whole text.

   The change adds one key with an empty value, so the whole text must change.
   Measured on 2026-10-30: compose renders `${AUTHSERV_AGENTS_JSON:-}` as `SPRING_APPLICATION_JSON: ""`.
   An exact text control would therefore fail by construction.
   The control must compare effective behavior instead.

2. Add three cases to `shell-scripts/test-authserv-profile.sh` before any code change.

   Case A sets the operator variable to a one-agent JSON value.
   It parses the rendered environment mapping.
   It expects `SPRING_APPLICATION_JSON` to equal that JSON value exactly.

   Case B leaves the operator variable unset.
   It parses the rendered environment mapping.
   It expects `SPRING_APPLICATION_JSON` to be present and to hold an empty value.
   It expects every other key and value to equal the control mapping from step 1.
   It expects no key of the control mapping to be absent.

   Case C leaves the operator variable unset.
   It expects the rendered `services.authserv` keys outside `environment` to equal the control.
   This keeps the image, the ports, the volumes, the networks, and the user unchanged.

   Use `docker compose config` and parse the result. Do not compare raw text.
   Run the script. Case A must fail now. Record that evidence.
   Case B and case C must pass before the change, because the control holds the old mapping.
3. Add `SPRING_APPLICATION_JSON: ${AUTHSERV_AGENTS_JSON:-}` to the `environment` block of the authserv service.
   Put it after `APP_OAUTH2_JWK_PATH` at line 25, so one block holds every application property.
   An unset variable gives an empty value. Spring Boot ignores an empty value.
4. Export `AUTHSERV_AGENTS_JSON` in `deploy-authserv` beside `AUTHSERV_JWK_PATH`.
   The recipe passes the operator value through and sets no default.
   The recipe must not call `--agent-json` itself, because this issue does not own agent selection.
5. Run `shell-scripts/test-authserv-profile.sh`. Every case must pass.
   After the change, case B must report the new key with an empty value.
   Case B must report every other key unchanged.
   Case C must report no change outside the environment block.
6. An empty value must change no bound property.
   Proof case 7 measures that result for two baked agents.
   Task 4 case 6 repeats it against `AgentClientProperties`.

## Task 4: Prove the binding against `AgentClientProperties`

**Files:** `chat-authorization-server/src/test/kotlin/com/demo/chat/AgentLaunchBindingTests.kt`.

The proof used a Java probe class. This task repeats each result against the Kotlin class.
The owner requires this task. It is not optional.

1. Write the new test class before any other change in this task.
   Build a `MutablePropertySources`. Add one `MapPropertySource` that holds `SPRING_APPLICATION_JSON`.
   Call `SpringApplicationJsonEnvironmentPostProcessor` to turn that value into a property source.
   Do not set a real process environment variable, and do not set a real system property.
   Emulate the baked flags with a second `MapPropertySource` that ranks below the JSON source.
   Bind with `Binder(ConfigurationPropertySources.from(sources)).bind("app.oauth2", AgentClientProperties::class.java)`.
2. Add these cases:
   1. The golden JSON from Task 2 binds two agents. The first holds its `jwkSetUri`.
   2. The bound `agentScope` equals `chat.mcp`.
   3. `requireValid()` accepts the bound result.
   4. Two baked agents and one different launch agent bind to the launch agent alone.
   5. The same case proves no bound client id holds the baked value.
   6. Two baked agents and no launch value bind to both baked agents.
   7. One launch value and one baked `agent-scope` bind to the launch scope.
3. Run the focused class. Each case must fail or pass for a stated reason.
   Case 4 is the case that the Java probe could not prove, so record its result first.
4. Run `just build-partial chat-authorization-server unit`.
   Report the test count and the failure count.

## Task 5: Update the operator documents and the help text

**Files:** `docs/MCP-CREDENTIAL-ISSUANCE.md`, `shell-scripts/chat-build`.

1. Check bindings with `drift refs docs/MCP-CREDENTIAL-ISSUANCE.md`.
2. State the launch procedure. Show `chat-build authserv --agent-json ...` and the operator variable.
3. State that `authserv --build` refuses `--agent` and `--agent-jwks-uri`.
4. State that core and rest keep the `--build` behavior. Name the follow-up issue.
5. Keep every existing procedure that this change does not touch.
6. Update the stale prose first. Only then run `drift link` if a binding needs it.
7. Verify with `drift check`.

## Verification

Run each check and record its result:

1. `./shell-scripts/test-flags.sh` reports zero failures.
2. `shell-scripts/test-authserv-profile.sh` passes.
3. `just build-partial chat-authorization-server unit` reports zero failures.
4. `bash docs/superpowers/proofs/2026-10-30-agent-mapping-precedence/precedence.sh` still reports 0 failures.
5. `bash -n shell-scripts/chat-build` is not valid for Python, so run `python3 -m py_compile shell-scripts/chat-build`.
6. `bash -n shell-scripts/test-flags.sh` and `bash -n shell-scripts/test-authserv-profile.sh` pass.
7. `git diff master...HEAD --check` reports nothing.
8. `drift check` exits 0.

## Out of scope

This plan starts no container and registers no live agent.
It publishes no JWKS.
It changes no core or rest behavior.
`CHAT-vnljyxpl` owns the node-9 launch wrapper and the live two-agent check.
A follow-up issue owns the core and rest move.
