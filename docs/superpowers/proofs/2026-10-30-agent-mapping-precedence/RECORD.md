# Agent mapping precedence: proof record

Date: 2026-10-30.
Branch: `chat-vidnpywh`.
Issue: CHAT-vidnpywh.
Script: `docs/superpowers/proofs/2026-10-30-agent-mapping-precedence/precedence.sh`.
Design: `docs/superpowers/specs/2026-10-30-agent-mapping-launch-time-design.md`.

## What this proof shows

The authserv image bakes each agent property as a JVM system property.
`pom.xml:607` appends `JAVA_TOOL_OPTIONS` into the image environment.
A launch-time channel must beat that baked value.

1. `SPRING_APPLICATION_JSON` beats a baked system property.
2. A plain environment variable loses to a baked system property.
3. A plain environment variable wins when no baked value exists.
4. `SPRING_APPLICATION_JSON` binds an indexed agent list.
5. A launch agent list replaces a baked agent list. It does not merge with it.
6. A baked agent list survives when no launch value exists.
7. A baked agent list survives when the launch value is empty.

Result 2 is the reason to reject plain environment variables. An old image
would win over the launch value, and the operator would see no error.

Result 5 is the behavior the design needs. An image that holds two old agents
must not add them to the launch agent set.

Result 7 is the deployed control. Compose renders an unset operator variable
as an empty value, measured on 2026-10-30 as `SPRING_APPLICATION_JSON: ""`. So
the control case cannot compare raw text, and it must compare behavior.

## Command

```bash
bash docs/superpowers/proofs/2026-10-30-agent-mapping-precedence/precedence.sh
```

The script needs no host, no container, and no credential. It resolves the
`chat-authorization-server` classpath from the local Maven repository with
`-o`. It writes only inside one private root, and it removes that root on
exit. It changes no repository file.

## Method

The script holds two probes. Each probe builds a `StandardEnvironment`. Each
probe then calls `SpringApplicationJsonEnvironmentPostProcessor`, which is the
component that reads `SPRING_APPLICATION_JSON`.

`Probe` prints three resolved property values. Cases 1 to 4 give it one launch
input and one baked `-Dapp.oauth2.agent-scope` value.

`ListProbe` binds `app.oauth2.agents` with `Binder` and `Bindable.listOf`. It
prints the bound agent count and each bound agent. Cases 5, 6 and 7 give it two
baked agents through `-Dapp.oauth2.agents[0]` and `-Dapp.oauth2.agents[1]`.

## Measured result, 2026-10-30

Spring Boot 4.0.8. macOS Darwin 25.0.0.

| Case | Launch input | Baked `-D` flag | `app.oauth2.agent-scope` |
| --- | --- | --- | --- |
| 1 | `SPRING_APPLICATION_JSON` | present | `from-launch` |
| 2 | `APP_OAUTH2_AGENT_SCOPE` | present | `from-baked-image` |
| 3 | `APP_OAUTH2_AGENT_SCOPE` | absent | `from-env` |
| 4 | none | present | `from-baked-image` |

Case 1 also resolved `app.oauth2.agents[0].client-id` to `agent-one`. It
resolved `app.oauth2.agents[0].jwk-set-uri` to
`https://example.test/one/jwks`.

Case 4 resolved `app.oauth2.agents[0].client-id` to no value. This confirms
the baked path carries no agent list unless the build adds one.

| Case | Baked agents | Launch agents | Bound list |
| --- | --- | --- | --- |
| 5 | 2 | 1 | count 1, `launch-one` alone |
| 6 | 2 | none | count 2, both baked agents |
| 7 | 2 | one empty value | count 2, both baked agents |

Case 5 also checked every printed line for the text `baked-`. No line held it.

The run reported 0 failures for all seven cases.

## Limits

This proof measures property resolution and list binding. It does not start
the authorization server, and it does not register a client.

`ListProbe` uses a Java class that holds the `AgentClientProperties.AgentClient`
shape. It does not use the Kotlin class. So the plan must repeat case 5 against
`AgentClientProperties` itself. The design names that check.
