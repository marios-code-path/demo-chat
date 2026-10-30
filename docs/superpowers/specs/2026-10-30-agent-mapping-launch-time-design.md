# Agent client mappings at launch time

Issue: `CHAT-vidnpywh`
Date: 2026-10-30
Status: awaiting approval

## Context

`chat-build --agent CLIENT_ID=HANDLE` declares a runtime identity.
`chat-build --agent-jwks-uri CLIENT_ID=HTTPS_URI` declares that agent's public key location.
The authorization server registers the client when it starts.
The image build does not register anything.

The current path writes the mapping into the image.
`shell-scripts/chat-build:749` builds one `-D` flag for each agent property.
`shell-scripts/chat-build:987` joins those flags into `java_tool_options()`.
`shell-scripts/chat-build:675` selects `spring-boot:build-image` for `--build`.
`shell-scripts/chat-build:1163` exports `JAVA_TOOL_OPTIONS` into the Maven process.
`pom.xml:607` sets `BPE_APPEND_JAVA_TOOL_OPTIONS` from that variable.
Paketo writes the value into the image environment.
One image therefore serves one agent set.

The consequences are these:

1. A published image carries one deployment's agent client ids and JWKS URIs.
2. `docker inspect` on any host that holds the image shows those values.
3. An agent change needs a source tree, Maven, and a full image build.
4. A launch command cannot register an agent, so `CHAT-vnljyxpl` cannot deliver its wrapper.
5. `CHAT-anegybnr` acceptance item one needs two live agents, and no launch path registers them.

No current caller passes `--agent` with `--build`.
Every `--agent` case in `shell-scripts/test-flags.sh` uses `--run`.
`just deploy-authserv` passes no agent flag.
So the baked path has no user today, and a refusal breaks no caller.

A launch channel already exists.
`chat-authorization-server/docker-compose.yaml:25` passes `APP_OAUTH2_JWK_PATH` at launch.
One application property already reaches the container through the compose environment block.

## Measured precedence

Spring Boot 4.0.8 ranks `SPRING_APPLICATION_JSON` above Java system properties.
A baked `-D` flag is a Java system property.
A plain environment variable ranks below a Java system property.

The measurement used `StandardEnvironment` and `SpringApplicationJsonEnvironmentPostProcessor`.
`docs/superpowers/proofs/2026-10-30-agent-mapping-precedence/` holds the script and the record.

| Launch input | Baked `-Dapp.oauth2.agent-scope` | Value that wins |
| --- | --- | --- |
| `SPRING_APPLICATION_JSON` | present | the launch value |
| `APP_OAUTH2_AGENT_SCOPE` | present | the baked value |
| `APP_OAUTH2_AGENT_SCOPE` | absent | the launch value |

The JSON form also binds an indexed list.
`app.oauth2.agents[0].client-id` and `app.oauth2.agents[0].jwk-set-uri` both resolved.

## Measured list replacement

A launch list replaces a baked list. It does not merge with it.
The measurement gave the JVM two baked agents and one launch agent.
The bound list then held the launch agent alone, and no baked value reached it.

| Baked agents | Launch agents | Bound list |
| --- | --- | --- |
| 2 | 1 | the launch agent alone |
| 2 | none | both baked agents |

Row two shows the control case. An operator who sets no launch value keeps the current behavior.

The measurement used `Binder` and `Bindable.listOf`.
It used a probe class that holds the `AgentClientProperties.AgentClient` shape.
It did not use the Kotlin class, so the plan must repeat this check against `AgentClientProperties`.

## Decision

Pass agent mappings at launch through `SPRING_APPLICATION_JSON`.

Add a `chat-build` output mode that prints the agent JSON and exits.
The mode takes the existing `--agent` and `--agent-jwks-uri` flags.
It prints one JSON object that holds `app.oauth2.agents` and `app.oauth2.agent-scope`.
It writes nothing else to standard output.

Add `SPRING_APPLICATION_JSON` to the authserv compose service.
The value comes from one operator variable.
An unset variable leaves the value empty.
Spring Boot ignores an empty value, so the current deployment behavior holds.

Make `chat-build authserv --build` refuse `--agent` and `--agent-jwks-uri`.
The refusal message names the output mode and the compose variable.

### Which services the refusal covers

The refusal covers the authserv service alone.
Core and rest keep their present `--build` behavior.
They still accept `--agent` with `--build`, and they still bake their mapping.

Core and rest need their own design for three reasons.
They bind `app.security.agents` instead of `app.oauth2.agents`.
They also receive `app.init.initial-users`, which creates a chat user.
They read `app.security.jwt.jwk-path`, which `--agent` requires through `--jwk`.
A refusal for every service would change core and rest behavior outside this scope.

This decision leaves one inconsistency. One service refuses the flag, and two accept it.
`chat-build --help` must name the service that refuses the flag.
The refusal message must name that service too.
The operator documents must state the same rule.
A follow-up issue must remove the inconsistency when core and rest move.

No caller builds a core or rest image with `--agent` today.
No caller builds an authserv image with `--agent` today.
So the authserv refusal breaks no caller, and it changes no core or rest behavior.

Keep `--run` unchanged.
A local run keeps the `-D` flags in the process environment.
That run registers its agents in the same process, so no image holds them.

## Rejected options

**Plain relaxed-binding environment variables.**
The measurement shows a baked system property beats such a variable.
An old image would then win over the launch value, and the operator would see no error.
The variable set also needs one fixed index per agent, and compose cannot repeat a block.

**An env file for compose.**
It carries the same precedence defect as plain variables.
It also adds a file whose lifetime the recipe must manage.

**Keep the baked path and document it.**
This leaves the identity in the image and blocks `CHAT-vnljyxpl`.

## Failure behavior

The output mode with no `--agent` value exits 2 and names the missing flag.
The output mode with a service other than authserv exits 2 and names authserv.
`authserv --build` with `--agent` exits 2 and names the output mode.
`authserv --build` with `--agent-jwks-uri` exits 2 and names the output mode.
Each of those messages names the authserv service.
`core --build` and `rest --build` keep their present behavior with `--agent`.
Each refusal writes its message to standard error.
Each existing refusal in `shell-scripts/test-flags.sh` keeps its present status and text.

## Verification

Add a failing check before each code change.

1. A `test-flags.sh` case proves `--build --agent` exits 2 with the new message.
2. A `test-flags.sh` case proves the output mode prints the expected JSON for two agents.
3. A golden file holds that JSON, as `shell-scripts/golden/` holds the flag files today.
4. A Kotlin test binds `AgentClientProperties` from the golden JSON through `SPRING_APPLICATION_JSON`.
5. The same test proves the launch value wins over a system property for one key.
6. A Kotlin test gives `AgentClientProperties` two baked agents and one different launch agent.
7. That test proves the bound list holds the launch agent alone.
8. That test also proves the bound list holds both baked agents when no launch value exists.
9. A compose configuration check proves the authserv service passes `SPRING_APPLICATION_JSON`.
10. A control check proves an unset operator variable leaves the current deployment unchanged.
11. A `test-flags.sh` case proves `core --build --agent` keeps its present exit status and output.

Run the focused authserv unit build.
Run `shell-scripts/test-flags.sh`.
Run `shell-scripts/test-authserv-profile.sh`.
Run `drift check`.

## Scope boundary

This design covers authserv alone.
Core and rest keep their present agent behavior.
They receive `app.security.agents` through the same baked path.
The `--build` refusal does not cover them, so they keep that path.
That move needs its own design, because core and rest also receive `app.init.initial-users`.
This document records that follow-up and does not change it.

This design does not publish a JWKS.
It does not register an agent on a live host.
`CHAT-anegybnr` keeps the live two-agent acceptance check.
`CHAT-vnljyxpl` keeps the node-9 launch wrapper.
