# XStream Live Reader Implementation Plan

> **For agentic workers:** Use `superpowers:executing-plans` to implement this plan task by task. Project policy prohibits subagent-driven development.

**Goal:** Deliver each new XStream topic message to local listeners after `open(topic)` completes.

**Architecture:** Use `StreamReceiver` with a cursor captured from the current stream tail. Reuse the existing Redis stream key, `data` field, and message serializers. Keep one reader per topic and align Redis Pub/Sub with the same one-source-per-topic startup rule.

**Tech Stack:** Kotlin, Reactor, Spring Data Redis 4.1.1, Redis Streams, Testcontainers, JUnit 5.

---

## File Map

- Modify `chat-persistence-xstream/src/main/kotlin/com/demo/chat/pubsub/impl/memory/messaging/XStreamTopicPubSubService.kt` to start, retain, and dispose one live reader per topic.
- Modify `chat-persistence-xstream/src/main/kotlin/com/demo/chat/pubsub/impl/memory/messaging/RedisTopicPubSubService.kt` to share concurrent startup and avoid duplicate channel readers.
- Modify `chat-persistence-xstream/src/test/kotlin/com/demo/chat/test/messaging/XStreamPubSubTests.kt` to prove live delivery, cursor behavior, repeated open, concurrent open, and close.
- Modify `chat-persistence-xstream/src/test/kotlin/com/demo/chat/test/messaging/RedisPubSubMessagingTests.kt` to prove concurrent open creates one channel reader.
- Modify `docs/BUILD-HEALTH.md` only if measured test counts change.

## Test Output Filter

Use this shell function for each Maven command. It prints test summaries and bounded failure details, not the full Maven log.

```bash
run_maven() {
  local log_file rc
  log_file="$(mktemp)"
  rc=0
  mvn -B -ntp "$@" >"$log_file" 2>&1 || rc=$?
  rg 'Tests run:|Reactor Summary|BUILD (SUCCESS|FAILURE)' "$log_file" | tail -40 || true
  if [ "$rc" -ne 0 ]; then
    rg -n -A5 '^\[ERROR\]|Caused by:' "$log_file" | tail -60 || true
  fi
  rm -f "$log_file"
  return "$rc"
}
```

### Task 1: Pin XStream Delivery Before the Repair

**Files:**
- Modify: `chat-persistence-xstream/src/test/kotlin/com/demo/chat/test/messaging/XStreamPubSubTests.kt`

- [ ] **Step 1: Add the immediate post-open delivery test.**

Create a listener before `open`. Complete `open`, then send one message. Assert its ID, sender, destination, data, and record flag.

```kotlin
@Test
fun `delivers a stream message sent after open`() {
    val topic = UUID.randomUUID()
    val sender = UUID.randomUUID()
    val message = Message.create(
        MessageKey.create(UUID.randomUUID(), sender, topic),
        "stream-payload",
        true,
    )

    StepVerifier.create(messaging.listenTo(topic))
        .then { messaging.open(topic).block(Duration.ofSeconds(10)) }
        .then { messaging.sendMessage(message).block(Duration.ofSeconds(10)) }
        .assertNext { actual ->
            assertThat(actual.key.id).isEqualTo(message.key.id)
            assertThat(actual.key.from).isEqualTo(sender)
            assertThat(actual.key.dest).isEqualTo(topic)
            assertThat(actual.data).isEqualTo("stream-payload")
            assertThat(actual.record).isTrue()
        }
        .thenCancel()
        .verify(Duration.ofSeconds(15))
}
```

Add `import org.assertj.core.api.Assertions.assertThat` if the test file does not already import it.
Add `import java.time.Duration` if the test file does not already import it.
The file currently has no test method. Add `@Test` and these imports if they are not already present:

```kotlin
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
import org.junit.jupiter.api.Test
import reactor.test.StepVerifier
import java.util.UUID
```

- [ ] **Step 2: Run the focused integration test and confirm failure.**

Run:

```bash
run_maven -Pintegration -pl chat-persistence-xstream -am \
  -Dtest=XStreamPubSubTests -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: the new test times out because the one-shot `XREAD` completes without feeding the sink.

### Task 2: Add a Typed Stream Receiver

**Files:**
- Modify: `chat-persistence-xstream/src/main/kotlin/com/demo/chat/pubsub/impl/memory/messaging/XStreamTopicPubSubService.kt`

- [ ] **Step 1: Replace the `Flux` cache with a reader subscription map.**

Use `ConcurrentHashMap<T, Disposable>` for active readers. Use `computeIfAbsent` so concurrent opens start one reader.

- [ ] **Step 2: Build the receiver with the message template's serializers.**

Use this record type:

```kotlin
StreamReceiver<String, MapRecord<String, String, Message<T, E>>>
```

Build `StreamReceiverOptions` with the key, hash-key, and hash-value serialization pairs from `messageTemplate.serializationContext`. The hash-value pair must match the `MessageSerializerRedis` used by `sendMessage`. Read the message from the record field named `data`.

Use these exact serialization-pair sources:

```kotlin
val context = messageTemplate.serializationContext
builder
    .keySerializer(context.keySerializationPair)
    .hashKeySerializer(context.hashKeySerializationPair)
    .hashValueSerializer(context.hashValueSerializationPair)
```

- [ ] **Step 3: Capture the stream cursor before receiver startup.**

Read the newest record with `reverseRange(streamKey, Range.unbounded<String>(), Limit.limit().count(1))`. Use its `RecordId` as the initial cursor. Use `0-0` when the stream has no records.

Start the receiver with `StreamOffset.create(streamKey, ReadOffset.from(cursor))`. Do not use `StreamOffset.latest()` for repeated polls. Keep the current `replayDepth` value of 50 and the exact `XTRIM MAXLEN` call.

- [ ] **Step 4: Tie reader lifecycle to topic lifecycle.**

Make `open(topic)` await cursor lookup and receiver subscription. Make `close(topic)` dispose the reader, complete the sink, and then delete the stream. Remove `topicXReads` and the obsolete one-shot `getXReadFlux` function.

- [ ] **Step 5: Run the focused integration test.**

Run the Task 1 command again.

Expected: the test passes against the Redis container and asserts every message field listed in Task 1.

### Task 3: Prove XStream Reader Ownership and Shutdown

**Files:**
- Modify: `chat-persistence-xstream/src/test/kotlin/com/demo/chat/test/messaging/XStreamPubSubTests.kt`
- Modify: `chat-persistence-xstream/src/main/kotlin/com/demo/chat/pubsub/impl/memory/messaging/XStreamTopicPubSubService.kt`

- [ ] **Step 1: Add repeated-open coverage.**

Open one topic twice. Send one message. Assert that one listener receives one copy.

- [ ] **Step 2: Add concurrent-open coverage.**

Call `open(topic)` concurrently twice before the send. Collect listener events for one second after the expected message. Assert the listener receives exactly one copy. The test must fail if both calls create a `StreamReceiver` subscription.

- [ ] **Step 3: Add close coverage.**

Subscribe to the listener, open the topic, then close it. Assert that the reader stops and the listener completes. Reopen the topic and prove that a new reader can deliver a new message.

- [ ] **Step 4: Run the focused XStream integration suite.**

Run:

```bash
run_maven -Pintegration -pl chat-persistence-xstream -am \
  -Dtest=XStreamPubSubTests -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: all XStream tests pass. The reader count stays one per topic.

### Task 4: Align Redis Pub/Sub Startup Ownership

**Files:**
- Modify: `chat-persistence-xstream/src/main/kotlin/com/demo/chat/pubsub/impl/memory/messaging/RedisTopicPubSubService.kt`
- Modify: `chat-persistence-xstream/src/test/kotlin/com/demo/chat/test/messaging/RedisPubSubMessagingTests.kt`

- [ ] **Step 1: Add a concurrent-open delivery test.**

Start two `open(topic)` calls concurrently. Send one message after both complete. Assert that the listener receives exactly one copy during a one-second collection window.

- [ ] **Step 2: Replace the check-then-start sequence with shared startup.**

The current `containsKey` check can race before `listenToLater` completes. Store one shared startup publisher per topic with `ConcurrentHashMap.computeIfAbsent`. Cache its subscription-ready completion so all callers await the same Redis `SUBSCRIBE`. Remove the startup entry on close and after startup failure. Keep the `Disposable` for close. This aligns Redis Pub/Sub with XStream because both expose one local source per topic.

- [ ] **Step 3: Run both Redis-backed pubsub suites.**

Run:

```bash
run_maven -Pintegration -pl chat-persistence-xstream -am \
  -Dtest=RedisPubSubMessagingTests,XStreamPubSubTests \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: both suites pass against Redis containers. Each suite receives one copy after concurrent opens.

### Task 5: Check the XStream Track and Commit

**Files:**
- Modify: `docs/BUILD-HEALTH.md` if the measured test counts change.

- [ ] **Step 1: Run both build-health modes.**

Run `build-health.sh` and `build-health.sh --integration`. Update only counts that the gates measure. Do not add a delivery failure to `KNOWN_FAILING`. The parked Elasticsearch module remains its only entry.

- [ ] **Step 2: Run repository checks.**

Run `just check-deps`, `drift check`, and `git diff --check`.

- [ ] **Step 3: Commit the XStream and Redis startup changes.**

Use one commit for XStream and one commit for Redis Pub/Sub. Name `CHAT-flhybnhm` on the XStream commit and `CHAT-scrrknxb` on the Redis commit.

## Acceptance

- XStream listeners receive new messages after `open` completes.
- XStream uses one reader per topic, including concurrent opens.
- XStream uses the same key and hash serializers as the writer.
- XStream close disposes the reader and completes its sink.
- Redis Pub/Sub concurrent opens create one channel subscription.
- Redis and XStream integration suites pass.
- The full gate reports the parked Elasticsearch state accurately.
