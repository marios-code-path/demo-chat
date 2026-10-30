# Agent client mappings at launch time: implementation plan

> Implement this plan with one agent. Track progress in `CHAT-vidnpywh`.
> The repository forbids subagent development and Markdown task checkboxes.

**Goal:** Move the authserv agent client mapping from the image build to launch time.

**Architecture:** `chat-build` owns the agent JSON output and the `--build` refusal.
The authserv compose service owns the `SPRING_APPLICATION_JSON` passthrough.
`AgentClientProperties` owns the binding, and it changes in no way.

**Tools:** Python, Bash, `shell-scripts/test-flags.sh`, Kotlin tests, `docker compose config`, and `drift`.

**Basis:** [Approved design](../specs/2026-10-30-agent-mapping-launch-time-design.md), commit `94ade97c`.
Approved on 2026-10-30. The owner approved this plan at `db38d81a` for implementation on 2026-10-30.
[Precedence proof](../proofs/2026-10-30-agent-mapping-precedence/RECORD.md), seven cases, 0 failures.

## One accepted deviation from the first approved design

The design at `94ade97c` stated exit 2 for each new refusal. This plan uses **exit 1**.

The repository already refuses two bake attempts, and both use exit 1:

1. `shell-scripts/chat-build:1389` refuses `authserv --build --jwk`. Its text reads `--jwk cannot be baked into an image`.
2. `shell-scripts/chat-build:1458` refuses `core --build --index-root`. Its text reads `--index-root cannot be baked into an image`.

Exit 2 in `chat-build` belongs to argument shape errors and to a missing key type.
Exit 1 belongs to a refused flag combination.
The new refusal is a refused combination, so exit 1 keeps one meaning for one code.
The refusal text follows the same `cannot be baked into an image` wording.

The owner accepted exit 1 on 2026-10-30. Commit `cfa56cd3` carries that text into the design.
So the design and this plan now agree, and this section records the history alone.

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
4. Run `./shell-scripts/test-flags.sh` and record this sequence:

   | Case | Before the change | Reason |
   | --- | --- | --- |
   | `refuse-agent-build` | fails | `chat-build` accepts the pair and exits 0 |
   | `refuse-agent-jwks-build` | fails | `chat-build` accepts the pair and exits 0 |
   | `core-build-image-agent` | fails with `no golden` | step 5 has not written the file yet |

   The two refusal failures are the failing-test-first evidence.
   The `no golden` failure is not that evidence. It only reports a missing file.
5. Create the control golden with `./shell-scripts/test-flags.sh --update core-build-image-agent`.
   Read the written file. It must hold each `-Dapp.security.agents[0]` flag and each `-Dapp.init.initial-users` flag.
   Commit that golden with this task.
   Run `./shell-scripts/test-flags.sh` again, still before the code change.
   The control case must now pass, and the two refusal cases must still fail.
   The control must pass before the change and after it, because core behavior does not change.
6. Add the refusal to `shell-scripts/chat-build` inside the existing `if args.service == "authserv":` block.
   Put it next to the `--jwk` refusal at line 1389, so one block holds every authserv bake rule.
   Refuse when `args.build` holds and `args.agent` holds any value.
   Refuse when `args.build` holds and `args.agent_jwks_uri` holds any value.
   Write one message to standard error. Name `--agent-json` and the compose variable in it.
   Return 1.
7. Run `./shell-scripts/test-flags.sh`. Every case must pass.
   This task adds three cases, so the count must read `all 60 case(s) match`.
   The fail count must stay at zero.

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

   | Case | Failure before the change |
   | --- | --- |
   | `authserv-agent-json` | `chat-build` exits non-zero on an unrecognized `--agent-json` |
   | `refuse-agent-json-service` | argparse exits 2, and the case expects exit 1 |
   | `refuse-agent-json-no-agent` | argparse exits 2, and the case expects exit 1 |
   | `refuse-agent-json-build` | argparse names an unrecognized argument, not a group conflict |

   The `JSON_CASES` loop must report a non-zero exit as a failure. It must not stop the script.
   Give it the same guard that the `CASES` loop uses for a non-zero exit.
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
   Parse that output, and keep two controls. Do not keep the whole text.

   Control 1 is the `services.authserv.environment` mapping. Case B compares it.
   Control 2 is every other key of `services.authserv`. Case C compares it.
   Control 2 holds the image, the ports, the volumes, the networks, the user, and `depends_on`.

   The change adds one key with an empty value, so the whole text must change.
   Measured on 2026-10-30: compose renders `${AUTHSERV_AGENTS_JSON:-}` as `SPRING_APPLICATION_JSON: ""`.
   An exact text control would therefore fail by construction.
   Each control must compare effective behavior instead.

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
   It expects every key of `services.authserv` outside `environment` to equal control 2.
   This keeps the image, the ports, the volumes, the networks, the user, and `depends_on` unchanged.

   Use `docker compose config` and parse the result. Do not compare raw text.

   Run the script before the change, and record this sequence:

   | Case | Before the change | Reason |
   | --- | --- | --- |
   | A | fails | the key is absent, so no value can equal the JSON value |
   | B | fails | the key is absent, so the presence check cannot hold |
   | C | passes | the keys outside `environment` do not change in this task |

   Case A and case B are the failing-test-first evidence. Record both failures.
   Case C is the control. It must pass before the change and after it.
   A case C failure before the change means control 2 is wrong. Repair step 1 first.
3. Add `SPRING_APPLICATION_JSON: ${AUTHSERV_AGENTS_JSON:-}` to the `environment` block of the authserv service.
   Put it after `APP_OAUTH2_JWK_PATH` at line 25, so one block holds every application property.
   An unset variable gives an empty value. Spring Boot ignores an empty value.
4. Export `AUTHSERV_AGENTS_JSON` in `deploy-authserv` beside `AUTHSERV_JWK_PATH`.
   The recipe passes the operator value through and sets no default.
   The recipe must not call `--agent-json` itself, because this issue does not own agent selection.
5. Run `shell-scripts/test-authserv-profile.sh`. Case A, case B, and case C must all pass.
   Case B must report the new key with an empty value.
   Case B must report every other key and value unchanged against control 1.
   Case C must report no change against control 2.

   | Case | After the change |
   | --- | --- |
   | A | passes |
   | B | passes |
   | C | passes |
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
   6. Two baked agents and one empty launch value bind to both baked agents.
   7. Two baked agents and no launch value bind to both baked agents.
   8. One launch value and one baked `agent-scope` bind to the launch scope.
3. Run the focused class. Every case must pass on its first run.

   This task adds no production code, because `AgentClientProperties` does not change.
   So no case starts red, and the failing-test-first rule does not apply here.
   Each case states a claim that the design already makes.
   A failure therefore means the design is wrong, not that code is missing.
   Stop on any failure. Report it, and do not start Task 5.
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
4. `bash docs/superpowers/proofs/2026-10-30-agent-mapping-precedence/precedence.sh` still reports 0 failures for seven cases.
5. `bash -n shell-scripts/chat-build` is not valid for Python. Parse the file instead:
   `python3 -I -c 'import ast,sys; ast.parse(open(sys.argv[1]).read())' shell-scripts/chat-build`.
   Do not use `python3 -m py_compile`. It writes `shell-scripts/__pycache__`, and no rule ignores that path.
6. `bash -n shell-scripts/test-flags.sh` and `bash -n shell-scripts/test-authserv-profile.sh` pass.
7. `git diff master...HEAD --check` reports nothing.
8. `drift check` exits 0.

## Evidence level of each acceptance item

`CHAT-vidnpywh` holds this acceptance item:

> One authserv image serves two different agent sets across two launches. No rebuild occurs between them.

**Offline evidence alone supports that item.** Nothing here starts a container.

What the offline work proves:

1. `SPRING_APPLICATION_JSON` ranks above the JVM system properties that an image bakes.
2. A launch agent list replaces a baked agent list, and an empty launch value changes nothing.
3. `AgentClientProperties` binds the emitted JSON, and the launch value wins over a baked one.
4. The authserv compose service passes the operator value to the container.
5. `chat-build authserv --build` refuses an agent mapping, so no image takes one.

What no test here proves:

1. One built image, started twice with two different mappings, serves both agent sets.
2. No rebuild occurs between those two launches.

Those two statements need a launch test on a host that runs Docker.
Keep the acceptance item marked as offline evidence until such a test runs.
`CHAT-vnljyxpl` owns the live work, and it depends on this issue.

## Out of scope

This plan starts no container and registers no live agent.
It publishes no JWKS.
It changes no core or rest behavior.
`CHAT-vnljyxpl` owns the node-9 launch wrapper and the live two-agent check.
A follow-up issue owns the core and rest move.
