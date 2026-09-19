# Kafka Pubsub Wire Implementation Plan

> **For agentic workers:** Use `superpowers:executing-plans` to implement this plan task by task. Project policy prohibits subagent-driven development.

**Goal:** Deliver Kafka pubsub messages by decoding records to the declared `Message` interface instead of an anonymous implementation class.

**Architecture:** Keep the JSON body and the Jackson 2 domain deserializer. Disable producer type headers and make the consumer ignore type headers. Pass serializer instances through Reactor Kafka options in both test and production configurations.

**Tech Stack:** Kotlin, Spring Kafka 4.0.7, Reactor Kafka, Jackson 2, Embedded Kafka, JUnit 5.

---

## File Map

- Modify `chat-messaging-kafka/src/test/kotlin/com/demo/chat/test/messaging/KafkaTestConfiguration.kt` to provide the named Jackson 2 mapper and pass codec instances into test sender and receiver options.
- Modify `chat-messaging-kafka/src/test/kotlin/com/demo/chat/test/messaging/KafkaPubSubTests.kt` to pin the body shape, type-header policy, and old-header replay behavior.
- Modify `chat-deploy-kafka/src/main/kotlin/com/demo/chat/config/deploy/kafka/KafkaDeployConfiguration.kt` to pass configured serializer instances into production options.
- Modify `chat-deploy-kafka/src/test/kotlin/com/demo/chat/test/deploy/kafka/KafkaDeploymentTests.kt` to prove delivery through production beans.
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

### Task 1: Prove the Production Kafka Path Fails Before the Repair

**Files:**
- Modify: `chat-deploy-kafka/src/test/kotlin/com/demo/chat/test/deploy/kafka/KafkaDeploymentTests.kt`

- [ ] **Step 1: Add a production-path delivery test.**

Resolve the active `TopicPubSubService` from `GenericApplicationContext`. Use a unique long topic. Subscribe before `open`, then send one message. Assert its ID, sender, destination, data, and record flag. Close the topic in `finally`.

Add these imports:

```kotlin
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
import com.demo.chat.service.core.TopicPubSubService
import reactor.test.StepVerifier
import java.time.Duration
```

Use this test body:

```kotlin
@Suppress("UNCHECKED_CAST")
@Test
fun `production Kafka beans deliver a declared Message`() {
    val pubsub = context.getBean(TopicPubSubService::class.java)
        as TopicPubSubService<Long, String>
    val topic = System.nanoTime()
    val sender = topic + 1
    val message = Message.create(
        MessageKey.create(topic + 2, sender, topic),
        "production-payload",
        true,
    )

    try {
        StepVerifier.create(pubsub.listenTo(topic))
            .then { pubsub.open(topic).block(Duration.ofSeconds(10)) }
            .then { pubsub.sendMessage(message).block(Duration.ofSeconds(10)) }
            .assertNext { actual ->
                assertThat(actual.key.id).isEqualTo(topic + 2)
                assertThat(actual.key.from).isEqualTo(sender)
                assertThat(actual.key.dest).isEqualTo(topic)
                assertThat(actual.data).isEqualTo("production-payload")
                assertThat(actual.record).isTrue()
            }
            .thenCancel()
            .verify(Duration.ofSeconds(15))
    } finally {
        pubsub.close(topic).block(Duration.ofSeconds(10))
    }
}
```

The test class already owns the Embedded Kafka broker, application context, and `assertThat` import.

- [ ] **Step 2: Run the production-path test and confirm failure.**

Run:

```bash
run_maven -pl chat-deploy-kafka -am \
  -Dtest=KafkaDeploymentTests -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: message delivery fails because the default type header names the anonymous class returned by `Message.create`.

### Task 2: Configure the Kafka Test Options with Instances

**Files:**
- Modify: `chat-messaging-kafka/src/test/kotlin/com/demo/chat/test/messaging/KafkaTestConfiguration.kt`

- [ ] **Step 1: Register the production Jackson 2 configuration for this test context.**

Add `@Import(Jackson2MapperConfiguration::class, DefaultChatJacksonModules::class)` to `KafkaTestConfiguration`. Add an `ObjectMapper` constructor parameter with `@Qualifier(JACKSON_2_OBJECT_MAPPER)`. Keep the existing `TypeUtil<String>` and Embedded Kafka bootstrap settings.

- [ ] **Step 2: Pass the producer serializer instance to `SenderOptions`.**

Remove `ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG`. Build the value serializer from the injected mapper and call `setAddTypeInfo(false)`. Pass the instance with `SenderOptions.withValueSerializer`.

```kotlin
val valueSerializer = JsonSerializer<Message<String, String>>(objectMapper).apply {
    setAddTypeInfo(false)
}
return KafkaSender.create(SenderOptions.create(props).withValueSerializer(valueSerializer))
```

- [ ] **Step 3: Pass the consumer deserializer instance to `ReceiverOptions`.**

Remove `ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG` and `JsonDeserializer.TRUSTED_PACKAGES`. Build `JsonDeserializer(Message::class.java, objectMapper)` and call `ignoreTypeHeaders()`. Pass the instance with `ReceiverOptions.withValueDeserializer`.

```kotlin
val valueDeserializer = JsonDeserializer<Message<String, String>>(Message::class.java, objectMapper)
    .apply { ignoreTypeHeaders() }
return ReceiverOptions.create(props).withValueDeserializer(valueDeserializer)
```

- [ ] **Step 4: Run the Kafka delivery tests.**

Run:

```bash
run_maven -pl chat-messaging-kafka -am \
  -Dtest=KafkaPubSubTests,KafkaSenderContractTests,KafkaReceiverContractTests \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: the shared delivery test receives the message and destination through Embedded Kafka. The sender and receiver contract tests remain green.

### Task 3: Pin the Kafka Body and Header Contract

**Files:**
- Modify: `chat-messaging-kafka/src/test/kotlin/com/demo/chat/test/messaging/KafkaPubSubTests.kt`

- [ ] **Step 1: Inject the named mapper into the Kafka test class.**

Add imports for `ObjectMapper`, `JACKSON_2_OBJECT_MAPPER`, and `Qualifier`. Add an `ObjectMapper` constructor parameter with `@Qualifier(JACKSON_2_OBJECT_MAPPER)`. Keep the existing `KafkaTopicPubSubService` parameter and pass it to `PubSubTests`.

- [ ] **Step 2: Test the existing message wrapper.**

Use the injected Jackson 2 mapper. Serialize `Message.create(MessageKey.create(3L, 10L, 20L), "hello", true)`. Assert this body shape: root field `message`; body fields `data`, `record`, and `key`; `key` contains a nested `key` wrapper; the inner key contains `id`, `from`, and `dest`.

- [ ] **Step 3: Test the producer serializer policy.**

Create `JsonSerializer<Message<String, String>>(objectMapper)`. Call `setAddTypeInfo(false)`. Serialize the message and assert that the `__TypeId__` header is absent. Assert that the JSON body keeps the shape from Step 1.

Use the three-argument `serialize(topic, headers, message)` method with `RecordHeaders`. The two-argument overload does not write headers. Check for the type header with `AbstractJavaTypeMapper.DEFAULT_CLASSID_FIELD_NAME`. Add these imports:

```kotlin
import org.apache.kafka.common.header.internals.RecordHeaders
import org.springframework.kafka.support.mapping.AbstractJavaTypeMapper
```

Use `AbstractJavaTypeMapper.DEFAULT_CLASSID_FIELD_NAME` (`__TypeId__`) for the header name. Do not use `JsonSerializer.ADD_TYPE_INFO_HEADERS`, which names the producer setting rather than the type header.

Use this call shape:

```kotlin
val headers = RecordHeaders()
val body = serializer.serialize("topic", headers, message)
assertThat(headers.lastHeader(AbstractJavaTypeMapper.DEFAULT_CLASSID_FIELD_NAME)).isNull()
```

- [ ] **Step 4: Test replay with the old anonymous type header.**

Serialize the existing `message` wrapper body from Step 1 with type headers disabled. Use the three-argument `serialize(topic, headers, message)` overload and `RecordHeaders`. Assert that the body contains `message`, `data`, `record`, and the nested key wrapper. Then add an old anonymous type header named by `AbstractJavaTypeMapper.DEFAULT_CLASSID_FIELD_NAME`, with `message.javaClass.name` encoded as UTF-8. Decode that same body with `JsonDeserializer<Message<String, String>>(Message::class.java, objectMapper)` and call `ignoreTypeHeaders()` inside `apply`. Assert `id=3`, `from=10`, `dest=20`, `data="hello"`, and `record=true`. The three-argument serializer overload is required because the two-argument overload does not write headers.

Add this import for the UTF-8 header value:

```kotlin
import java.nio.charset.StandardCharsets
```

- [ ] **Step 5: Run the codec and delivery tests.**

Run:

```bash
run_maven -pl chat-messaging-kafka -am \
  -Dtest=KafkaPubSubTests,KafkaSenderContractTests,KafkaReceiverContractTests \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: the codec tests pass, and the shared delivery test receives the message and destination through Embedded Kafka.

### Task 4: Configure Production Kafka Beans with Instances

**Files:**
- Modify: `chat-deploy-kafka/src/main/kotlin/com/demo/chat/config/deploy/kafka/KafkaDeployConfiguration.kt`

- [ ] **Step 1: Inject the named Jackson 2 mapper.**

Add constructor injection with `@Qualifier(JACKSON_2_OBJECT_MAPPER)`. Keep the existing bootstrap server property.

- [ ] **Step 2: Configure the production producer instance.**

Remove `ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG`. Build `JsonSerializer<Message<Any, Any>>(objectMapper)`, call `setAddTypeInfo(false)`, and pass it to `SenderOptions.withValueSerializer`.

```kotlin
val valueSerializer = JsonSerializer<Message<Any, Any>>(objectMapper).apply {
    setAddTypeInfo(false)
}
return KafkaSender.create(SenderOptions.create(props).withValueSerializer(valueSerializer))
```

- [ ] **Step 3: Configure the production consumer instance.**

Remove `ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG` and `JsonDeserializer.TRUSTED_PACKAGES`. Build `JsonDeserializer<Message<Any, Any>>(Message::class.java, objectMapper)`, call `ignoreTypeHeaders()`, and pass it to `ReceiverOptions.withValueDeserializer`.

```kotlin
val valueDeserializer = JsonDeserializer<Message<Any, Any>>(Message::class.java, objectMapper).apply {
    ignoreTypeHeaders()
}
return ReceiverOptions.create(props).withValueDeserializer(valueDeserializer)
```

- [ ] **Step 4: Run Kafka deployment test compilation.**

Run:

```bash
run_maven -pl chat-deploy-kafka -am \
  -Dtest=KafkaDeploymentTests -Dsurefire.failIfNoSpecifiedTests=false test-compile
```

Expected: the production configuration compiles with serializer instances and Jackson 2 injection.

### Task 5: Prove Delivery Through Production Beans

**Files:**
- Modify: `chat-deploy-kafka/src/test/kotlin/com/demo/chat/test/deploy/kafka/KafkaDeploymentTests.kt`

- [ ] **Step 1: Keep the production-path delivery test from Task 1.**

Do not replace the context-resolved `TopicPubSubService` with a manually constructed service. The test must exercise the beans from `KafkaDeployConfiguration`.

- [ ] **Step 2: Run the production deployment tests.**

Run:

```bash
run_maven -pl chat-deploy-kafka -am \
  -Dtest=KafkaDeploymentTests -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: the test receives the message through the production `KafkaDeployConfiguration` beans and Embedded Kafka.

### Task 6: Check Both Kafka Configuration Sites and Commit

**Files:**
- Modify: `docs/BUILD-HEALTH.md` if the measured test counts change.

- [ ] **Step 1: Search both configurations for class-based value codecs.**

Check that neither Kafka configuration sets `VALUE_SERIALIZER_CLASS_CONFIG` or `VALUE_DESERIALIZER_CLASS_CONFIG`. Check that both pass instances through Reactor Kafka options.

- [ ] **Step 2: Run build-health gates and repository checks.**

Run `build-health.sh` and `build-health.sh --integration`. Update `docs/BUILD-HEALTH.md` with measured counts. Do not add a delivery failure to `KNOWN_FAILING`. The parked Elasticsearch module remains its only entry. Then run `just check-deps`, `drift check`, and `git diff --check`.

- [ ] **Step 3: Commit the Kafka changes.**

Use one commit for serializer code, codec tests, and production-path tests. Name `CHAT-bshubwsl` in the commit message.

## Acceptance

- Both test and production configurations pass serializer instances through Reactor Kafka options.
- Kafka writes no anonymous implementation type header.
- Kafka ignores the old type header and decodes the existing `message` wrapper body to `Message`.
- `KafkaDeploymentTests` proves message delivery through production beans.
- The shared delivery test passes for Kafka, memory, Redis Pub/Sub, and XStream.
- The full gate records the parked Elasticsearch failure and measured test counts.
