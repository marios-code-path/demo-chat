# MCP messaging profile check

Issue: `CHAT-teujorxl`, Task 6, `CHAT-jtzhmxrs`.
Source revision: `9d3ca5ed`.
The measured tree also includes the six-line test dependency addition in `chat-deploy-memory/pom.xml`.

Tasks 1 through 5 are committed.
The adapter's latest complete test run passed 237 tests with no failures, errors, or skips.
Task 6 has no deployment test yet.
The owner must decide whether to accept the existing REST-profile failures as a baseline.

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

The proposed next step accepts this profile failure as a separate baseline.
Task 6 would then continue with focused authenticated MCP deployment tests and the agent HTTP gate.
Repairing the full REST profile requires a separate scope decision.
No Cassandra source changed, and these checks did not select Cassandra modules.
