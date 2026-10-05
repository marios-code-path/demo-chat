# Chat log view implementation plan

Issue: `CHAT-bmmtojqm`. The owner approved this design on 2026-10-04.

## Design

The shell reads messages through the existing service and preserves its SUBSCRIBE check.
It sorts messages by their stored timestamps.
An optional positive limit selects the newest messages before display.
The display uses the shell timezone and the format `yyyy-MM-dd'T'HH:mm | handle | text`.
Each distinct sender resolves once per command.
A missing user displays its key id.
Additional text lines align under the text column.

## Execution

1. Run the shell unit reactor before production changes with `shell-scripts/build-partial.sh --modules chat-shell --mode unit`.
2. Add failing tests in `chat-shell/src/test/kotlin/com/demo/chat/test/commands/PubSubMessagesTests.kt`.
3. Cover stored time, unordered messages, repeated senders, missing users, multiline text, and service failures.
4. Update the messages option expectations in `ShellCommandContractTests.kt`.
5. Run the tests before changing production code.
6. Change `PubSubCommands.kt` and `PubSubCommandsRegistrar.kt`.
7. Test parser dispatch in `ShellParsedInputTests.kt` with and without `--limit`.
8. Extend `ShellPubSubCommandsTests.kt` with two senders and three messages.
9. Check stored time through the running server before and after a delayed read.
10. Update the command contract, including the existing REST route.
11. Run focused unit and integration builds sequentially.
12. Run a mutation check for timestamp formatting, ordering, and limits.
13. Run `shell-scripts/build-health.sh --ci` with an empty Docker configuration, disabled container reuse, and a test context cache of one.
14. Compare the integration image id before and after the build.
15. Run `git diff --check` and `drift check`.
16. Commit named paths, push the branch, and open a PR.
17. Leave merging to the owner.

## Verification boundary

A failed or unavailable required build stops this task with the command and observed failure.
No result counts as passing without an observed exit code.
No concurrent Maven builds run in this worktree.

## Timestamp transport defect

The first full CI run found a transport defect.
The server sent the stored timestamp, but both Jackson decoders replaced it with the current time.
The view requires the stored timestamp, so this correction belongs to this issue.
`ChatDeserializers.kt`, `ChatJackson3Deserializers.kt`, and `NodeValueRules.kt` now retain supplied timestamps.
`MessageTimestampWireTests.kt` covers JSON, CBOR, Long keys, UUID keys, messages, and both key decode types.
The codec regression test failed on the substituted current time before the correction.
The corrected codecs passed all 16 selected codec tests.
The shell integration test also passed its delayed timestamp reread.
The decoder repair precedes the shell feature in a separate commit.
Both decoders retain decimal precision and report malformed timestamps as mapping failures.
Whole numbers use seconds by default.
Readers can select milliseconds with `READ_DATE_TIMESTAMPS_AS_NANOSECONDS` disabled.
The reader setting must match the writer setting.
Decimal numbers always use seconds.
The delayed integration read compares exact stored timestamps without host clock bounds.

## Local gate settings

Concurrent Cassandra tests lost their connections during the first full run.
Docker confirmed memory kills during a second run with container reuse disabled.
Limit the test context cache to reduce retained containers.
Pass that setting through Maven arguments.
`JAVA_TOOL_OPTIONS` adds a JVM startup line that fails the MCP stderr contract.
The focused MCP test passed with Maven arguments and confirmed a cache limit of one.
These settings apply only to this process.
They do not change machine settings or Cassandra source.

Run the complete gate with these settings:

```sh
taskDockerConfig=$(mktemp -d /tmp/chat-bmmtojqm-docker.XXXXXX)
DOCKER_CONFIG="$taskDockerConfig" \
TESTCONTAINERS_REUSE_ENABLE=false \
MAVEN_ARGS="${MAVEN_ARGS:+$MAVEN_ARGS }-Dspring.test.context.cache.maxSize=1" \
./shell-scripts/build-health.sh --ci
```
