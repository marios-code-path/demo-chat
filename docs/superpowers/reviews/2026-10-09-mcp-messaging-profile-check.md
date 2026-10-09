# MCP messaging profile check

Issue: `CHAT-teujorxl`, Task 6, `CHAT-jtzhmxrs`.
Source revision: `9d3ca5ed`.
The measured tree also includes the six-line test dependency addition in `chat-deploy-memory/pom.xml`.

Tasks 1 through 5 are committed.
The adapter's latest complete test run passed 237 tests with no failures, errors, or skips.
These checks preceded the Task 6 deployment test.
The separate baseline is accepted under `CHAT-gsddauhn`.
Task 6 continues with explicit REST test classes and `app.primary=REST`.

## Complete profile checks with the new dependency

Both commands ran sequentially, without a test filter or Maven `install`.

```sh
mvn -B -pl chat-deploy-memory -am -Pexpose-rsocket clean verify
mvn -B -pl chat-deploy-memory -am -Pexpose-webflux clean verify
```

| Profile | Exit | Memory tests | Failures | Errors | Skipped |
|---|---|---|---|---|---|
| `expose-rsocket` | 0 | 125 | 0 | 0 | 2 |
| `expose-webflux` | 1 | 77 | 3 | 67 | 0 |

The RSocket reactor ran 1,762 tests, with no failures or errors and 33 skips.
The REST reactor ran 1,714 tests, with three failures, 67 errors, and 31 skips.
Both runs passed `RequireUpperBoundDeps` and `DependencyConvergence` in all 17 modules.
The two RSocket-profile skips belong to `RestAgentSelectionTests`.
That class requires the REST profile.

The RSocket run also reported a Surefire fork shutdown deadline after `System.exit(0)`.
The build exited 0.
The cause of that shutdown diagnostic is not measured.

## Comparison without the new dependency

I removed only the new test dependency temporarily.
I then ran this comparison:

```sh
mvn -B -pl chat-deploy-memory -am -Pexpose-webflux clean verify \
  -Dtest=StandardUserJoinSendTests,RestAgentSelectionTests \
  -Dsurefire.failIfNoSpecifiedTests=false
```

The command exited 1.
`StandardUserJoinSendTests` reproduced the same missing `AgentResourceServerChain` error.
Both `RestAgentSelectionTests` cases passed.
The failing class's Surefire classpath contained no `chat-mcp/target/` entry.
The new dependency is restored after this comparison.

## Observed configuration conflict

`WebFluxSecurity` has an unconditional `@Configuration` declaration and requires `AgentResourceServerChain`.
`AgentSecurityConfiguration` supplies that chain only when `app.primary=REST`.
Existing RSocket test contexts fail when the REST profile adds `chat-webflux` to their classpath.
This conflict exists without the MCP dependency.
No security configuration changed during this comparison.

## Evidence and next boundary

Logs are in `/tmp/chat-teujorxl-logs/`:

- `task6-classpath-rsocket.log`
- `task6-classpath-webflux.log`
- `task6-without-mcp-comparison.log`

The accepted scope keeps this profile failure as a separate baseline.
Task 6 continues with focused authenticated MCP deployment tests and the agent HTTP gate.
Repairing the full REST profile requires a separate scope decision.
No Cassandra source changed, and these checks did not select Cassandra modules.

## Task 6 deployment evidence

The six deployment tests use `app.primary=REST` and real signed agent tokens.
The adapter and pinned Node client run as child processes.
The tests check submission, both message reads, joining, pending completion, repeats, conflicts, room refusal, and command ownership.
The persistence gate exists only in the test context.

Without the store wrapper, five tests passed and the pending test failed.
It expected `PENDING` and received `COMPLETED`.
With the wrapper, all six tests passed with no failures, errors, or skips.
The command used `clean verify`, the `expose-webflux` profile, and an explicit `McpMessagingDeploymentTests` filter.
The build exited 0.
Neither deployment run reported the Surefire shutdown warning.
Logs: `task6-red-pending.log` and `task6-green.log` in the directory above.

The broad deployment classpath adds Logback to the child adapter.
Its default configuration writes SDK log lines to stdout.
The fixture supplies a temporary configuration with the root level `OFF`.
The normal adapter runtime still has no logging provider.
Each fixture checks protocol-only stdout and adapter exit within five seconds.

The updated agent HTTP gate exited 0.
Its REST run passed two identity tests and six MCP messaging tests.
Its relay run passed seven tests, and the packaged core contained no `chat-webflux` jar.
All three classes reported zero failures, errors, and skips.
The REST run reported no Surefire shutdown warning.
Logs: `task6-agent-http-gate.log` and the retained `task6-agent-rest.log`.
The earlier RSocket warning still has no measured cause.
