# Message Vector Recall Implementation Plan

> **For agents:** Implement this plan inline, task by task. This repository forbids subagent-driven development. See `CLAUDE.md` and `AGENTS.md`. Steps use checkbox syntax for tracking inside this file. FP issues own the tracking: every task names its FP issue, and `fp issue assign <id> --rev <commit>` attaches each task commit.

**Goal:** Add message vector recall to Demo Chat: send-path write-through to a Spring AI `VectorStore`, and topic / user / global recall over `chat-core` contracts, exposed on RSocket and REST.

**Architecture:** `chat-core` owns the contracts (`MessageRecallService<T>`, `MessageVectorIndexer<T>`, `MessageRecallHit<T>`), the recall request DTOs, request validation, the `Message` to `Document` mapper, and mock test fixtures. Two new provider modules own the store wiring: `chat-vector-simple` (`SimpleVectorStore`, no container) and `chat-vector-redis` (Spring AI `RedisVectorStore`, Redis Stack container). `chat-service-composite` wires the indexer into the send chain and implements the recall service. `chat-service-controller` and `chat-webflux` expose the recall routes. Activation is the selector pair `app.service.core.vector` + `app.service.core.embedding`, checked by a startup validation bean.

**Tech Stack:** Kotlin 1.8.0, Spring Boot 3.3.13, Reactor (`Mono` / `Flux`), Spring AI BOM 1.0.3 (`spring-ai-commons`, `spring-ai-model`, `spring-ai-vector-store`, `spring-ai-redis-store`), Jedis (Redis vector path only), Testcontainers 1.21.4, Maven multi-module.

**Spec:** `docs/superpowers/specs/2026-09-01-message-vector-recall-design.md`

## Global Constraints

- Spring AI BOM is pinned to `org.springframework.ai:spring-ai-bom:1.0.3` in the root pom `dependencyManagement`. Do not use Spring AI `2.0.0` in this sprint.
- `VectorStoreRetriever` does not exist in Spring AI 1.0.3. The spec line "Call `VectorStoreRetriever.similaritySearch(request)`" maps to `VectorStore.similaritySearch(SearchRequest)`. The write path is `VectorStore.add(List<Document>)` and `VectorStore.delete(List<String>)`.
- Every Spring AI blocking call is bridged exactly this way: `Mono.fromCallable { ... }.subscribeOn(Schedulers.boundedElastic())`. Never call a Spring AI blocking method on a request or reactor worker thread.
- Threshold mapping: `threshold = 0.0` means "accept all" and maps to `SearchRequest.SIMILARITY_THRESHOLD_ACCEPT_ALL` via `SearchRequest.Builder.similarityThresholdAll()`. `threshold > 0.0` maps to `similarityThreshold(threshold)`.
- Document id format: `message:<keyType>:<messageId>`. `MessageVectorIndexer.remove(key)` deletes by this id only.
- Metadata fields (all stored as `String`): `kind = "message"`, `messageId`, `topicId`, `userId`, `keyType`.
- Filter expressions (exact strings):
  - Topic: `kind == 'message' && keyType == '<keyType>' && topicId == '<id>'`
  - User: `kind == 'message' && keyType == '<keyType>' && userId == '<id>'`
  - Global: `kind == 'message' && keyType == '<keyType>'`
- Redis isolation: index name `chat:vector:<keyType>:message`, key prefix `chat:vector:<keyType>:message:`, `initializeSchema(true)`, filters always include `keyType`. The vector path uses Jedis (`JedisPooled`); the repo data path stays Lettuce. Redis Stack is required for the Redis vector tests.
- Selectors: `app.service.core.vector` (legal values `mock`, `simple`, `redis`) and `app.service.core.embedding` (legal value `mock`; `local` and `gateway` are reserved and fail startup). Both set activates recall. One set fails startup; the error names both selectors. Both unset leaves recall inactive. No selector uses `matchIfMissing`. This sprint uses `@ConditionalOnProperty` plus a startup validation bean; the `@ProvidesCapability` migration is a later step for the unstarted capability mechanism.
- Controller gate: both the RSocket and the REST recall controllers use `@ConditionalOnProperty(prefix = "app.controller", name = ["recall"])`.
- Request bounds: `limit` default `10`, range `1..50`. `threshold` default `0.0`, range `0.0..1.0`. `query` must not be blank. Violations throw `InvalidRecallRequestException` (extends `ChatException`).
- `message.record == false` (join alerts, leave alerts) skips the vector write: the indexer returns `Mono.empty()` without touching the store.
- Recall returns keys and scores only. No full-message backfill. Recall covers only messages sent while recall is active. No backfill of older messages.
- No new authorization rules. Accepted risk, recorded here per the spec: global recall exposes all indexed message keys to an enabled caller.
- T binding: compositions bind `T` at runtime (`app.key.type=long` in this plan's tests). Controllers and services use erased generics; every `T` value that crosses a boundary goes through `TypeUtil<T>.toString` / `fromString`. Recall request DTOs are sealed `RequestResponse<T>` subclasses, the same pattern as `MembershipRequest<T>`. No new Jackson deserializers.
- Build commands: `mvn -o -pl chat-core,<module> test` — always pair `chat-core` with the module under test. `-o` (offline) is the rule; the single exception is the first artifact fetch in Task 1.
- Nodeid rule (docs/NODEID-CLAIM.md): a process claims a node id only when `app.service.core.key` or `app.service.core.persistence` names `redis` or `cassandra`. All vector tests in this plan use `memory` key and persistence, so they claim nothing. No new row is added to the docs/NODEID-CLAIM.md table; Task 12 documents that the vector tests claim nothing.
- All agent-authored prose (comments, commits, FP issues) uses controlled English per AGENTS.md.
- The deploy composition roots (`chat-deploy-memory`, `chat-deploy-redis`) keep controllers and controller-adjacent modules at compile scope (B7 note). The new vector modules follow the same rule when a deploy root needs them.
- No deploy yml changes: the vector selectors and the `app.controller.recall` flag are test-only in this sprint. No runtime composition has real recall until the gateway embedding integration lands. That is a spec consequence, not a gap.

## File Structure

Created:

- `chat-core/src/main/kotlin/com/demo/chat/service/vector/MessageRecallService.kt` — recall contract.
- `chat-core/src/main/kotlin/com/demo/chat/service/vector/MessageVectorIndexer.kt` — indexer contract + `MessageRecallHit<T>`.
- `chat-core/src/main/kotlin/com/demo/chat/service/vector/MessageDocumentMapper.kt` — `Message<T, String>` to `Document`.
- `chat-core/src/main/kotlin/com/demo/chat/service/dummy/DummyEmbeddingModel.kt` — deterministic mock embedding model (main code, `Dummy*` precedent).
- `chat-core/src/main/kotlin/com/demo/chat/config/MockEmbeddingConfiguration.kt` — `embedding=mock` bean.
- `chat-core/src/main/kotlin/com/demo/chat/config/VectorSelectorValidation.kt` — selector pair check (object + validation configuration).
- `chat-core/src/test/kotlin/com/demo/chat/test/vector/SpringAiApiProbe.kt` — compile-only API probe.
- `chat-core/src/test/kotlin/com/demo/chat/test/vector/MockVectorStore.kt` — in-memory `VectorStore` test fixture (ships in the chat-core test-jar).
- `chat-core/src/test/kotlin/com/demo/chat/test/vector/MessageDocumentMapperTests.kt`
- `chat-core/src/test/kotlin/com/demo/chat/test/vector/MockVectorStoreTests.kt`
- `chat-core/src/test/kotlin/com/demo/chat/test/domain/RecallRequestValidationTests.kt`
- `chat-core/src/test/kotlin/com/demo/chat/test/config/VectorSelectorValidationTests.kt`
- `chat-vector-simple/pom.xml`, `chat-vector-simple/src/main/kotlin/com/demo/chat/config/vector/simple/SimpleVectorStoreConfiguration.kt`, `chat-vector-simple/src/test/kotlin/com/demo/chat/test/vector/simple/SimpleVectorStoreConfigurationTests.kt`
- `chat-vector-redis/pom.xml`, `chat-vector-redis/src/main/kotlin/com/demo/chat/config/vector/redis/RedisVectorStoreConfiguration.kt`, `chat-vector-redis/src/test/kotlin/com/demo/chat/test/vector/redis/RedisVectorStoreConfigurationTests.kt`
- `chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/impl/VectorStoreMessageVectorIndexer.kt`
- `chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/impl/MessageRecallServiceImpl.kt`
- `chat-service-composite/src/main/kotlin/com/demo/chat/config/service/composite/VectorRecallServiceConfiguration.kt`
- `chat-service-composite/src/test/kotlin/com/demo/chat/test/service/composite/MessagingServiceVectorTests.kt` (first test in this module)
- `chat-service-composite/src/test/kotlin/com/demo/chat/test/service/composite/MessageRecallServiceImplTests.kt`
- `chat-service-composite/src/test/kotlin/com/demo/chat/test/config/VectorRecallServiceConfigurationTests.kt`
- `chat-service-controller/src/main/kotlin/com/demo/chat/controller/composite/mapping/MessageRecallControllerMapping.kt`
- `chat-service-controller/src/test/kotlin/com/demo/chat/test/rsocket/controller/MessageRecallControllerTests.kt`
- `chat-webflux/src/main/kotlin/com/demo/chat/controller/webflux/ChatMessageRecallController.kt`
- `chat-webflux/src/test/kotlin/com/demo/chat/test/controller/webflux/composite/MessageRecallRestTests.kt`
- `chat-deploy-memory/src/test/kotlin/com/demo/chat/test/deploy/memory/MemoryVectorRecallBootTests.kt`
- `chat-deploy-redis/src/test/kotlin/com/demo/chat/test/deploy/redis/RedisVectorRecallBootTests.kt`

Modified:

- `pom.xml` — Spring AI BOM import; `chat-vector-simple` and `chat-vector-redis` modules.
- `chat-core/pom.xml` — Spring AI dependencies.
- `chat-core/src/main/kotlin/com/demo/chat/domain/Exception.kt` — `InvalidRecallRequestException`.
- `chat-core/src/main/kotlin/com/demo/chat/domain/RequestResponse.kt` — the three recall request DTOs + `RecallRequestValidation` (sealed class forces same-file subclasses).
- `chat-service-composite/pom.xml` — `spring-ai-vector-store`.
- `chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/impl/MessagingServiceImpl.kt` — optional vector indexer in the send chain.
- `chat-service-composite/src/main/kotlin/com/demo/chat/config/service/composite/CompositeServiceBeansConfiguration.kt` — `ObjectProvider<MessageVectorIndexer<T>>` into `messageService()`.
- `chat-service-controller/src/main/kotlin/com/demo/chat/config/controller/composite/CompositeControllersConfiguration.kt` — recall controller.
- `chat-deploy-memory/pom.xml`, `chat-deploy-redis/pom.xml` — vector module dependencies.
- `forward-register.md`, `docs/BUILD-HEALTH.md` (only if the verifier flags drift) — Task 12.

Unchanged: `chat-index-lucene`, all deploy ymls, `shell-scripts/chat-build`, all existing controller flags.

---

## Task 1: Pin Spring AI 1.0.3 and compile the API probe (CHAT-rbygsuur)

**Files:**
- Modify: `pom.xml` (`dependencyManagement`, lines 108-118)
- Modify: `chat-core/pom.xml` (`dependencies`)
- Create: `chat-core/src/test/kotlin/com/demo/chat/test/vector/SpringAiApiProbe.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: the Spring AI BOM on the build; `org.springframework.ai.document.Document`, `org.springframework.ai.vectorstore.SearchRequest` (test scope in chat-core), `org.springframework.ai.embedding.EmbeddingModel` (compile scope in chat-core) available to every later task.

- [ ] **Step 1: Write the failing probe**

Create `chat-core/src/test/kotlin/com/demo/chat/test/vector/SpringAiApiProbe.kt`:

```kotlin
package com.demo.chat.test.vector

import org.springframework.ai.document.Document
import org.springframework.ai.embedding.Embedding
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.ai.embedding.EmbeddingRequest
import org.springframework.ai.embedding.EmbeddingResponse
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.ai.vectorstore.SimpleVectorStore
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.ai.vectorstore.filter.Filter

/**
 * Compile-only probe for the Spring AI 1.0.3 API surface this feature uses.
 *
 * It never runs. If the BOM is ever repinned, this file is the first thing
 * to fail, and it names the exact accessors that moved.
 */
@Suppress("unused", "UNUSED_PARAMETER")
class SpringAiApiProbe {

    fun documentProbe(doc: Document): Triple<String, Map<String, Any?>, Double?> {
        val id: String = doc.id
        val text: String? = doc.text
        val metadata: Map<String, Any?> = doc.metadata
        val score: Double? = doc.score
        return Triple(id, metadata, score)
    }

    fun searchRequestProbe(query: String, limit: Int, threshold: Double, filter: String): SearchRequest =
        SearchRequest.builder()
            .query(query)
            .topK(limit)
            .similarityThreshold(threshold)
            .filterExpression(filter)
            .build()

    fun acceptAllProbe(query: String): SearchRequest =
        SearchRequest.builder()
            .query(query)
            .similarityThresholdAll()
            .build()

    fun requestGetters(request: SearchRequest): Triple<Int, Double, Filter.Expression?> {
        val topK: Int = request.topK
        val threshold: Double = request.similarityThreshold
        val filter: Filter.Expression? = request.filterExpression
        return Triple(topK, threshold, filter)
    }

    fun acceptAllConstant(): Double = SearchRequest.SIMILARITY_THRESHOLD_ACCEPT_ALL

    fun embeddingRequestProbe(request: EmbeddingRequest): List<String> = request.instructions

    fun embeddingResponseProbe(texts: List<String>): EmbeddingResponse {
        val embeddings = texts.mapIndexed { index, _ -> Embedding(FloatArray(256), index) }
        return EmbeddingResponse(embeddings)
    }

    fun documentBuilderProbe(id: String, text: String, metadata: Map<String, Any?>, score: Double): Document =
        Document.builder()
            .id(id)
            .text(text)
            .metadata(metadata)
            .score(score)
            .build()

    fun storeProbe(vectorStore: VectorStore, request: SearchRequest): List<Document> =
        vectorStore.similaritySearch(request)

    fun simpleStoreProbe(embeddingModel: EmbeddingModel): VectorStore =
        SimpleVectorStore.builder(embeddingModel).build()
}
```

- [ ] **Step 2: Run the probe to verify it fails**

Run: `mvn -o -pl chat-core test-compile`
Expected: FAIL — unresolved references `org.springframework.ai.*` (no Spring AI on the classpath yet).

- [ ] **Step 3: Add the BOM to the root pom**

In `pom.xml`, inside `<dependencyManagement><dependencies>`, after the `spring-cloud-dependencies` entry, add:

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-bom</artifactId>
    <version>1.0.3</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>
```

- [ ] **Step 4: Add the Spring AI dependencies to chat-core**

In `chat-core/pom.xml`, inside `<dependencies>`, add (versions come from the BOM; do not write them):

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-commons</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-model</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-vector-store</artifactId>
    <scope>test</scope>
</dependency>
```

- [ ] **Step 5: Run the probe to verify it passes**

Run (online once, to fetch the Spring AI artifacts; drop `-o` for this run only):
`mvn -pl chat-core test-compile`
Expected: BUILD SUCCESS.

If an accessor in the probe fails to compile, the pinned BOM differs from what this plan assumes. Stop, re-verify the accessor against the `v1.0.3` sources at `github.com/spring-projects/spring-ai`, and adapt the probe and the plan before continuing.

Run: `mvn -o -pl chat-core test`
Expected: PASS (probe compiles; existing tests still pass; offline works now).

- [ ] **Step 6: Commit**

```bash
git add pom.xml chat-core/pom.xml chat-core/src/test/kotlin/com/demo/chat/test/vector/SpringAiApiProbe.kt
git commit -m "feat: pin Spring AI 1.0.3 BOM and add API probe"
```

- [ ] **Step 7: Log the milestone**

```bash
fp comment CHAT-rbygsuur "Task 1 done: BOM 1.0.3 pinned in root pom. chat-core gains spring-ai-commons and spring-ai-model (compile) and spring-ai-vector-store (test). Probe compiles against the pinned BOM. Commit <short-sha>."
```

---

## Task 2: Recall contracts, request DTOs, validation (CHAT-davnyulh)

**Files:**
- Create: `chat-core/src/main/kotlin/com/demo/chat/service/vector/MessageRecallService.kt`
- Create: `chat-core/src/main/kotlin/com/demo/chat/service/vector/MessageVectorIndexer.kt`
- Modify: `chat-core/src/main/kotlin/com/demo/chat/domain/RequestResponse.kt` (append the three DTOs and the validation object)
- Modify: `chat-core/src/main/kotlin/com/demo/chat/domain/Exception.kt`
- Test: `chat-core/src/test/kotlin/com/demo/chat/test/domain/RecallRequestValidationTests.kt`

**Interfaces:**
- Consumes: `RequestResponse<T>` (sealed, `com.demo.chat.domain`), `ChatException`.
- Produces:
  - `MessageRecallService<T> { recallInTopic(TopicRecallRequest<T>): Flux<MessageRecallHit<T>>; recallByUser(UserRecallRequest<T>): Flux<MessageRecallHit<T>>; recallGlobal(GlobalRecallRequest): Flux<MessageRecallHit<T>> }`
  - `MessageVectorIndexer<T> { add(Message<T, String>): Mono<Void>; remove(Key<T>): Mono<Void> }`
  - `MessageRecallHit<T>(key: MessageKey<T>, score: Double?)`
  - `TopicRecallRequest<T>(topicId, query, limit = 10, threshold = 0.0)`, `UserRecallRequest<T>(userId, query, limit = 10, threshold = 0.0)`, `GlobalRecallRequest(query, limit = 10, threshold = 0.0)` — each with `fun validate()`
  - `InvalidRecallRequestException(message: String) : ChatException(message)`
  - Type names: `TopicRecallRequest`, `UserRecallRequest`, `GlobalRecallRequest` (Jackson `type` property values).

- [ ] **Step 1: Write the failing tests**

Create `chat-core/src/test/kotlin/com/demo/chat/test/domain/RecallRequestValidationTests.kt`:

```kotlin
package com.demo.chat.test.domain

import com.demo.chat.domain.GlobalRecallRequest
import com.demo.chat.domain.InvalidRecallRequestException
import com.demo.chat.domain.TopicRecallRequest
import com.demo.chat.domain.UserRecallRequest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class RecallRequestValidationTests {

    @Test
    fun `blank query fails`() {
        assertThatThrownBy { TopicRecallRequest(3L, "   ").validate() }
            .isInstanceOf(InvalidRecallRequestException::class.java)
        assertThatThrownBy { UserRecallRequest(7L, "").validate() }
            .isInstanceOf(InvalidRecallRequestException::class.java)
        assertThatThrownBy { GlobalRecallRequest(" ").validate() }
            .isInstanceOf(InvalidRecallRequestException::class.java)
    }

    @Test
    fun `limit defaults to 10`() {
        assertThat(TopicRecallRequest(3L, "apple").limit).isEqualTo(10)
        assertThat(UserRecallRequest(7L, "apple").limit).isEqualTo(10)
        assertThat(GlobalRecallRequest("apple").limit).isEqualTo(10)
    }

    @Test
    fun `limit below 1 fails`() {
        assertThatThrownBy { TopicRecallRequest(3L, "apple", limit = 0).validate() }
            .isInstanceOf(InvalidRecallRequestException::class.java)
        assertThatThrownBy { TopicRecallRequest(3L, "apple", limit = -1).validate() }
            .isInstanceOf(InvalidRecallRequestException::class.java)
    }

    @Test
    fun `limit above 50 fails`() {
        assertThatThrownBy { TopicRecallRequest(3L, "apple", limit = 51).validate() }
            .isInstanceOf(InvalidRecallRequestException::class.java)
    }

    @Test
    fun `limit at the bounds passes`() {
        TopicRecallRequest(3L, "apple", limit = 1).validate()
        TopicRecallRequest(3L, "apple", limit = 50).validate()
    }

    @Test
    fun `threshold defaults to 0_0`() {
        assertThat(TopicRecallRequest(3L, "apple").threshold).isEqualTo(0.0)
        assertThat(GlobalRecallRequest("apple").threshold).isEqualTo(0.0)
    }

    @Test
    fun `threshold outside 0_0 to 1_0 fails`() {
        assertThatThrownBy { TopicRecallRequest(3L, "apple", threshold = 1.1).validate() }
            .isInstanceOf(InvalidRecallRequestException::class.java)
        assertThatThrownBy { UserRecallRequest(7L, "apple", threshold = -0.1).validate() }
            .isInstanceOf(InvalidRecallRequestException::class.java)
        assertThatThrownBy { GlobalRecallRequest("apple", threshold = 2.0).validate() }
            .isInstanceOf(InvalidRecallRequestException::class.java)
    }

    @Test
    fun `threshold at the bounds passes`() {
        TopicRecallRequest(3L, "apple", threshold = 0.0).validate()
        TopicRecallRequest(3L, "apple", threshold = 1.0).validate()
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn -o -pl chat-core test -Dtest=RecallRequestValidationTests`
Expected: FAIL — `RecallRequests.kt` and `InvalidRecallRequestException` do not exist yet (compile error).

- [ ] **Step 3: Write the contracts and DTOs**

Append to `chat-core/src/main/kotlin/com/demo/chat/domain/Exception.kt`:

```kotlin
class InvalidRecallRequestException(message: String) : ChatException(message)
```

Append to `chat-core/src/main/kotlin/com/demo/chat/domain/RequestResponse.kt` (the file already carries the package and the `JsonTypeName` import). `RequestResponse<T>` is sealed; in Kotlin 1.8 its subclasses must live in the same file, so the recall DTOs go here next to the existing request types:

```kotlin
@JsonTypeName("TopicRecallRequest")
data class TopicRecallRequest<T>(
    val topicId: T,
    val query: String,
    val limit: Int = 10,
    val threshold: Double = 0.0,
) : RequestResponse<T>() {
    fun validate() = RecallRequestValidation.validate(query, limit, threshold)
}

@JsonTypeName("UserRecallRequest")
data class UserRecallRequest<T>(
    val userId: T,
    val query: String,
    val limit: Int = 10,
    val threshold: Double = 0.0,
) : RequestResponse<T>() {
    fun validate() = RecallRequestValidation.validate(query, limit, threshold)
}

@JsonTypeName("GlobalRecallRequest")
data class GlobalRecallRequest(
    val query: String,
    val limit: Int = 10,
    val threshold: Double = 0.0,
) : RequestResponse<Any>() {
    fun validate() = RecallRequestValidation.validate(query, limit, threshold)
}

object RecallRequestValidation {

    fun validate(query: String, limit: Int, threshold: Double) {
        if (query.isBlank()) {
            throw InvalidRecallRequestException("query must not be blank")
        }
        if (limit < 1) {
            throw InvalidRecallRequestException("limit must be at least 1")
        }
        if (limit > 50) {
            throw InvalidRecallRequestException("limit must be at most 50")
        }
        if (threshold < 0.0 || threshold > 1.0) {
            throw InvalidRecallRequestException("threshold must be in 0.0..1.0")
        }
    }
}
```

Create `chat-core/src/main/kotlin/com/demo/chat/service/vector/MessageRecallService.kt`:

```kotlin
package com.demo.chat.service.vector

import com.demo.chat.domain.GlobalRecallRequest
import com.demo.chat.domain.TopicRecallRequest
import com.demo.chat.domain.UserRecallRequest
import reactor.core.publisher.Flux

interface MessageRecallService<T> {
    fun recallInTopic(req: TopicRecallRequest<T>): Flux<MessageRecallHit<T>>
    fun recallByUser(req: UserRecallRequest<T>): Flux<MessageRecallHit<T>>
    fun recallGlobal(req: GlobalRecallRequest): Flux<MessageRecallHit<T>>
}
```

Create `chat-core/src/main/kotlin/com/demo/chat/service/vector/MessageVectorIndexer.kt`:

```kotlin
package com.demo.chat.service.vector

import com.demo.chat.domain.Key
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
import reactor.core.publisher.Mono

interface MessageVectorIndexer<T> {
    fun add(message: Message<T, String>): Mono<Void>
    fun remove(key: Key<T>): Mono<Void>
}

data class MessageRecallHit<T>(
    val key: MessageKey<T>,
    val score: Double?,
)
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `mvn -o -pl chat-core test`
Expected: PASS, including `RecallRequestValidationTests`.

- [ ] **Step 5: Commit**

```bash
git add chat-core/src/main/kotlin/com/demo/chat/service/vector/ chat-core/src/main/kotlin/com/demo/chat/domain/RequestResponse.kt chat-core/src/main/kotlin/com/demo/chat/domain/Exception.kt chat-core/src/test/kotlin/com/demo/chat/test/domain/RecallRequestValidationTests.kt
git commit -m "feat: add vector recall contracts, request DTOs, validation"
```

- [ ] **Step 6: Log the milestone**

```bash
fp comment CHAT-davnyulh "Task 2 done: MessageRecallService, MessageVectorIndexer, MessageRecallHit, the three request DTOs, InvalidRecallRequestException. Request validation tests pass. Commit <short-sha>."
```

---

## Task 3: Message to Document mapper (CHAT-qvzhyeds)

**Files:**
- Create: `chat-core/src/main/kotlin/com/demo/chat/service/vector/MessageDocumentMapper.kt`
- Test: `chat-core/src/test/kotlin/com/demo/chat/test/vector/MessageDocumentMapperTests.kt`

**Interfaces:**
- Consumes: `Message<T, out E>` (`key: MessageKey<T>`, `data: E`), `MessageKey<T>` (`id`, `from`, `dest`), `TypeUtil<T>` (`toString(t)`), `Document` (from Task 1, compile scope).
- Produces: `MessageDocumentMapper<T>(typeUtil: TypeUtil<T>, keyType: String)` with `fun toDocument(message: Message<T, String>): Document` and `fun documentId(messageId: T): String`.

- [ ] **Step 1: Write the failing tests**

Create `chat-core/src/test/kotlin/com/demo/chat/test/vector/MessageDocumentMapperTests.kt`:

```kotlin
package com.demo.chat.test.vector

import com.demo.chat.domain.LongUtil
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
import com.demo.chat.domain.UUIDUtil
import com.demo.chat.service.vector.MessageDocumentMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class MessageDocumentMapperTests {

    private val mapper = MessageDocumentMapper<Long>(LongUtil(), "long")

    private val message: Message<Long, String> =
        Message.create(MessageKey.create(10L, 20L, 30L), "hello apple", true)

    @Test
    fun `message becomes one document with id text and metadata`() {
        val doc = mapper.toDocument(message)

        assertThat(doc.id).isEqualTo("message:long:10")
        assertThat(doc.text).isEqualTo("hello apple")
        assertThat(doc.metadata).containsExactlyInAnyOrderEntriesOf(
            mapOf(
                "kind" to "message",
                "messageId" to "10",
                "topicId" to "30",
                "userId" to "20",
                "keyType" to "long",
            )
        )
    }

    @Test
    fun `document id uses key type and message id`() {
        assertThat(mapper.documentId(42L)).isEqualTo("message:long:42")
    }

    @Test
    fun `uuid keys stringify in id and metadata`() {
        val uuidMapper = MessageDocumentMapper<UUID>(UUIDUtil(), "uuid")
        val messageId = UUID.randomUUID()
        val doc = uuidMapper.toDocument(
            Message.create(
                MessageKey.create(messageId, UUID.randomUUID(), UUID.randomUUID()),
                "hi",
                true,
            )
        )

        assertThat(doc.id).isEqualTo("message:uuid:$messageId")
        assertThat(doc.metadata["messageId"]).isEqualTo(messageId.toString())
        assertThat(doc.metadata["keyType"]).isEqualTo("uuid")
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn -o -pl chat-core test -Dtest=MessageDocumentMapperTests`
Expected: FAIL — `MessageDocumentMapper` does not exist yet.

- [ ] **Step 3: Write the mapper**

Create `chat-core/src/main/kotlin/com/demo/chat/service/vector/MessageDocumentMapper.kt`:

```kotlin
package com.demo.chat.service.vector

import com.demo.chat.domain.Message
import com.demo.chat.domain.TypeUtil
import org.springframework.ai.document.Document

/**
 * Maps one persisted message to the Spring AI document that enters the
 * recall corpus. The document id and the metadata values are the recall
 * contract; the filter expressions in MessageRecallServiceImpl depend on
 * them.
 */
class MessageDocumentMapper<T>(
    private val typeUtil: TypeUtil<T>,
    private val keyType: String,
) {

    fun toDocument(message: Message<T, String>): Document =
        Document.builder()
            .id(documentId(message.key.id))
            .text(message.data)
            .metadata(
                mapOf(
                    "kind" to "message",
                    "messageId" to typeUtil.toString(message.key.id),
                    "topicId" to typeUtil.toString(message.key.dest),
                    "userId" to typeUtil.toString(message.key.from),
                    "keyType" to keyType,
                )
            )
            .build()

    fun documentId(messageId: T): String = "message:$keyType:${typeUtil.toString(messageId)}"
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `mvn -o -pl chat-core test`
Expected: PASS, including `MessageDocumentMapperTests`.

- [ ] **Step 5: Commit**

```bash
git add chat-core/src/main/kotlin/com/demo/chat/service/vector/MessageDocumentMapper.kt chat-core/src/test/kotlin/com/demo/chat/test/vector/MessageDocumentMapperTests.kt
git commit -m "feat: add Message to Document mapper for vector recall"
```

- [ ] **Step 6: Log the milestone**

```bash
fp comment CHAT-qvzhyeds "Task 3 done: MessageDocumentMapper with message:<keyType>:<messageId> ids and the five metadata fields. Mapper tests pass. Commit <short-sha>."
```

---

## Task 4: Mock fixtures — DummyEmbeddingModel and MockVectorStore (CHAT-ilxdjayd)

**Files:**
- Create: `chat-core/src/main/kotlin/com/demo/chat/service/dummy/DummyEmbeddingModel.kt`
- Create: `chat-core/src/main/kotlin/com/demo/chat/config/MockEmbeddingConfiguration.kt`
- Create: `chat-core/src/test/kotlin/com/demo/chat/test/vector/MockVectorStore.kt`
- Test: `chat-core/src/test/kotlin/com/demo/chat/test/vector/MockVectorStoreTests.kt`

**Interfaces:**
- Consumes: `EmbeddingModel`, `EmbeddingRequest`, `EmbeddingResponse`, `Embedding` (spring-ai-model, compile scope from Task 1); `VectorStore`, `SearchRequest`, `Document` (spring-ai-vector-store test scope + spring-ai-commons compile scope).
- Produces:
  - `DummyEmbeddingModel : EmbeddingModel` — deterministic character-bigram vectors, 256 dimensions. Main code, so every module can use it.
  - `MockEmbeddingConfiguration` — `@ConditionalOnProperty(prefix = "app.service.core", name = "embedding", havingValue = "mock")`, bean `mockEmbeddingModel(): EmbeddingModel`.
  - `MockVectorStore : VectorStore` (test-jar) — in-memory, cosine scoring, evaluates a parsed `Filter.Expression` tree over the `&&`/EQ subset the recall service emits, thread-name capture for the boundedElastic test. Consumers: `val ids: List<String>`, `var lastWriteThread: String?`, `var lastSearchThread: String?`, `var lastFilter: Filter.Expression?`, `var lastTopK: Int`, `var lastThreshold: Double`.

- [ ] **Step 1: Write the failing tests**

Create `chat-core/src/test/kotlin/com/demo/chat/test/vector/MockVectorStoreTests.kt`:

```kotlin
package com.demo.chat.test.vector

import com.demo.chat.service.dummy.DummyEmbeddingModel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.ai.document.Document
import org.springframework.ai.vectorstore.SearchRequest

class MockVectorStoreTests {

    private val store = MockVectorStore()

    private fun doc(id: String, text: String, topicId: String) =
        Document.builder()
            .id(id)
            .text(text)
            .metadata(
                mapOf(
                    "kind" to "message",
                    "messageId" to id,
                    "topicId" to topicId,
                    "userId" to "1",
                    "keyType" to "long",
                )
            )
            .build()

    @BeforeEach
    fun seed() {
        store.add(
            listOf(
                doc("m1", "apple banana", "3"),
                doc("m2", "apple pie", "3"),
                doc("m3", "zebra stripe", "3"),
                doc("m4", "apple banana cake", "9"),
            )
        )
    }

    @Test
    fun `shared substrings rank first`() {
        val hits = store.similaritySearch(
            SearchRequest.builder().query("apple banana").topK(4).build()
        )

        assertThat(hits.map { it.id }).first().isEqualTo("m1")
        assertThat(hits.map { it.id }).last().isEqualTo("m3")
    }

    @Test
    fun `filter keeps matching topic only`() {
        val hits = store.similaritySearch(
            SearchRequest.builder()
                .query("apple banana")
                .topK(10)
                .filterExpression("kind == 'message' && keyType == 'long' && topicId == '9'")
                .build()
        )

        assertThat(hits.map { it.id }).containsExactly("m4")
    }

    @Test
    fun `high threshold drops weak matches`() {
        val hits = store.similaritySearch(
            SearchRequest.builder()
                .query("apple banana")
                .topK(10)
                .similarityThreshold(0.9)
                .build()
        )

        assertThat(hits.map { it.id }).contains("m1")
        assertThat(hits.map { it.id }).doesNotContain("m3")
    }

    @Test
    fun `accept all threshold returns everything matching the filter`() {
        val hits = store.similaritySearch(
            SearchRequest.builder()
                .query("apple banana")
                .topK(10)
                .similarityThresholdAll()
                .build()
        )

        assertThat(hits).hasSize(4)
    }

    @Test
    fun `delete removes by document id`() {
        store.delete(listOf("m1", "m3"))

        assertThat(store.ids).containsExactlyInAnyOrder("m2", "m4")
    }

    @Test
    fun `dummy embedding is deterministic and dimensional`() {
        val model = DummyEmbeddingModel()
        val first = model.embed("apple banana")
        val second = model.embed("apple banana")
        val other = model.embed("zebra stripe")

        assertThat(first).isEqualTo(second)
        assertThat(first).isNotEqualTo(other)
        assertThat(first).hasSize(256)
        assertThat(model.dimensions()).isEqualTo(256)
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn -o -pl chat-core test -Dtest=MockVectorStoreTests`
Expected: FAIL — `MockVectorStore` and `DummyEmbeddingModel` do not exist yet.

- [ ] **Step 3: Write the fixtures**

Create `chat-core/src/main/kotlin/com/demo/chat/service/dummy/DummyEmbeddingModel.kt`:

```kotlin
package com.demo.chat.service.dummy

import org.springframework.ai.document.Document
import org.springframework.ai.embedding.Embedding
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.ai.embedding.EmbeddingRequest
import org.springframework.ai.embedding.EmbeddingResponse
import kotlin.math.sqrt

/**
 * Deterministic embedding for tests. Texts become character-bigram vectors,
 * so documents that share substrings score higher than documents that do
 * not. No network, no model download.
 */
class DummyEmbeddingModel : EmbeddingModel {

    companion object {
        const val DIMENSIONS = 256
    }

    override fun call(request: EmbeddingRequest): EmbeddingResponse {
        val embeddings = request.instructions.mapIndexed { index, text ->
            Embedding(bigramVector(text), index)
        }
        return EmbeddingResponse(embeddings)
    }

    override fun embed(document: Document): FloatArray =
        bigramVector(document.text ?: "")

    override fun dimensions(): Int = DIMENSIONS

    private fun bigramVector(text: String): FloatArray {
        val vector = FloatArray(DIMENSIONS)
        val padded = " ${text.lowercase()} "
        for (i in 0 until padded.length - 1) {
            val slot = (padded[i].code * 128 + padded[i + 1].code) % DIMENSIONS
            vector[slot] += 1f
        }
        val norm = sqrt(vector.fold(0f) { acc, v -> acc + v * v })
        if (norm > 0f) {
            for (i in vector.indices) vector[i] /= norm
        }
        return vector
    }
}
```

Create `chat-core/src/main/kotlin/com/demo/chat/config/MockEmbeddingConfiguration.kt`:

```kotlin
package com.demo.chat.config

import com.demo.chat.service.dummy.DummyEmbeddingModel
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty(prefix = "app.service.core", name = ["embedding"], havingValue = "mock")
class MockEmbeddingConfiguration {

    @Bean
    fun mockEmbeddingModel(): EmbeddingModel = DummyEmbeddingModel()
}
```

Create `chat-core/src/test/kotlin/com/demo/chat/test/vector/MockVectorStore.kt`:

```kotlin
package com.demo.chat.test.vector

import org.springframework.ai.document.Document
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.ai.vectorstore.filter.Filter
import kotlin.math.sqrt

/**
 * In-memory VectorStore for unit tests. Cosine scoring over character
 * bigram vectors, the same scheme as DummyEmbeddingModel. Filter support is
 * the subset the recall service emits: EQ clauses joined by AND, evaluated
 * as a parsed Filter.Expression tree. Not thread-safe; tests use it from
 * one thread.
 *
 * Captures the thread that runs add and similaritySearch so tests can prove
 * the boundedElastic bridge, and captures the last search request so tests
 * can assert on the exact filter and topK.
 */
class MockVectorStore : VectorStore {

    private class Entry(val document: Document, val embedding: FloatArray)

    private val entries = LinkedHashMap<String, Entry>()

    var lastWriteThread: String? = null
        private set

    var lastSearchThread: String? = null
        private set

    var lastFilter: Filter.Expression? = null
        private set

    var lastTopK: Int = 0
        private set

    var lastThreshold: Double = 0.0
        private set

    val ids: List<String>
        get() = entries.keys.toList()

    override fun add(documents: List<Document>) {
        lastWriteThread = Thread.currentThread().name
        for (doc in documents) {
            entries[doc.id] = Entry(doc, bigramVector(doc.text ?: ""))
        }
    }

    override fun delete(idsToDrop: List<String>) {
        lastWriteThread = Thread.currentThread().name
        idsToDrop.forEach { entries.remove(it) }
    }

    override fun delete(expression: Filter.Expression) {
        throw UnsupportedOperationException("MockVectorStore does not support filter deletes")
    }

    override fun similaritySearch(request: SearchRequest): List<Document> {
        lastSearchThread = Thread.currentThread().name
        val filter = request.filterExpression
        lastFilter = filter
        lastTopK = request.topK
        lastThreshold = request.similarityThreshold

        val queryEmbedding = bigramVector(request.query ?: "")
        val threshold =
            if (request.similarityThreshold == SearchRequest.SIMILARITY_THRESHOLD_ACCEPT_ALL) 0.0
            else request.similarityThreshold

        return entries.values
            .map { it.document to cosine(queryEmbedding, it.embedding) }
            .filter { (doc, score) -> score >= threshold }
            .filter { (doc, _) -> filter == null || evaluate(filter, doc.metadata) }
            .sortedByDescending { it.second }
            .take(request.topK)
            .map { (doc, score) ->
                doc.mutate()
                    .score(score)
                    .build()
            }
    }

    // The recall service emits only EQ clauses joined by AND. The mock
    // supports exactly that subset and rejects everything else.
    private fun evaluate(expression: Filter.Expression, metadata: Map<String, Any?>): Boolean =
        when (expression.type()) {
            Filter.ExpressionType.AND -> {
                evaluate(expression.left() as Filter.Expression, metadata) &&
                    evaluate(expression.right() as Filter.Expression, metadata)
            }
            Filter.ExpressionType.EQ -> {
                val key = (expression.left() as Filter.Key).key()
                val value = (expression.right() as Filter.Value).value()
                metadata[key]?.toString() == value?.toString()
            }
            else -> throw UnsupportedOperationException("MockVectorStore does not support ${expression.type()}")
        }

    private fun cosine(a: FloatArray, b: FloatArray): Double {
        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denom = sqrt(normA) * sqrt(normB)
        return if (denom == 0.0) 0.0 else dot / denom
    }

    private fun bigramVector(text: String): FloatArray {
        val vector = FloatArray(256)
        val padded = " ${text.lowercase()} "
        for (i in 0 until padded.length - 1) {
            val slot = (padded[i].code * 128 + padded[i + 1].code) % 256
            vector[slot] += 1f
        }
        return vector
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `mvn -o -pl chat-core test`
Expected: PASS, including `MockVectorStoreTests`.

Note: `MockVectorStore` lives in `src/test/kotlin`, so it ships in the chat-core test-jar. Task 7 and Task 8 consume it through the existing `chat-core` test-jar dependency.

- [ ] **Step 5: Commit**

```bash
git add chat-core/src/main/kotlin/com/demo/chat/service/dummy/DummyEmbeddingModel.kt chat-core/src/main/kotlin/com/demo/chat/config/MockEmbeddingConfiguration.kt chat-core/src/test/kotlin/com/demo/chat/test/vector/MockVectorStore.kt chat-core/src/test/kotlin/com/demo/chat/test/vector/MockVectorStoreTests.kt
git commit -m "feat: add deterministic mock embedding model and mock vector store"
```

- [ ] **Step 6: Log the milestone**

```bash
fp comment CHAT-ilxdjayd "Task 4 done: DummyEmbeddingModel (main, 256-dim character bigrams), MockEmbeddingConfiguration gated on embedding=mock, MockVectorStore in the test-jar with filter and thread capture. Deterministic order, filter, threshold, delete tests pass. Commit <short-sha>."
```

---

## Task 5: chat-vector-simple module (CHAT-hfytrwyh)

**Files:**
- Modify: `pom.xml` (`<modules>`)
- Create: `chat-vector-simple/pom.xml`
- Create: `chat-vector-simple/src/main/kotlin/com/demo/chat/config/vector/simple/SimpleVectorStoreConfiguration.kt`
- Test: `chat-vector-simple/src/test/kotlin/com/demo/chat/test/vector/simple/SimpleVectorStoreConfigurationTests.kt`

**Interfaces:**
- Consumes: `SimpleVectorStore` (spring-ai-vector-store, Task 1 BOM), `EmbeddingModel`, `DummyEmbeddingModel` (Task 4, chat-core main).
- Produces: bean `simpleVectorStore(): VectorStore` when `app.service.core.vector=simple`. The module is a provider: it adds nothing to any runtime composition in this sprint (no deploy root depends on it yet; the memory boot test in Task 11 does).

- [ ] **Step 1: Register the module and write its pom**

In the root `pom.xml` `<modules>`, after `<module>chat-index-lucene</module>`, add:

```xml
<module>chat-vector-simple</module>
```

Create `chat-vector-simple/pom.xml` (shape follows `chat-persistence-redis/pom.xml`; Spring AI versions come from the BOM):

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.demo</groupId>
        <artifactId>chat-parent</artifactId>
        <version>0.0.1</version>
    </parent>

    <groupId>com.demo</groupId>
    <artifactId>chat-vector-simple</artifactId>
    <version>0.0.1</version>
    <name>chat-vector-simple</name>
    <description>In-memory SimpleVectorStore provider for local and integration tests</description>

    <dependencies>
        <dependency>
            <groupId>com.demo</groupId>
            <artifactId>chat-core</artifactId>
            <version>0.0.1</version>
        </dependency>
        <dependency>
            <groupId>com.demo</groupId>
            <artifactId>chat-core</artifactId>
            <type>test-jar</type>
            <version>0.0.1</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-vector-store</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter-engine</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <sourceDirectory>${project.basedir}/src/main/kotlin</sourceDirectory>
        <testSourceDirectory>${project.basedir}/src/test/kotlin</testSourceDirectory>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <skip>true</skip>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.jetbrains.kotlin</groupId>
                <artifactId>kotlin-maven-plugin</artifactId>
                <configuration>
                    <args>
                        <arg>-Xjsr305=strict</arg>
                    </args>
                    <compilerPlugins>
                        <plugin>spring</plugin>
                    </compilerPlugins>
                </configuration>
                <dependencies>
                    <dependency>
                        <groupId>org.jetbrains.kotlin</groupId>
                        <artifactId>kotlin-maven-allopen</artifactId>
                        <version>${kotlin.version}</version>
                    </dependency>
                </dependencies>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: Write the failing tests**

Create `chat-vector-simple/src/test/kotlin/com/demo/chat/test/vector/simple/SimpleVectorStoreConfigurationTests.kt`:

```kotlin
package com.demo.chat.test.vector.simple

import com.demo.chat.config.vector.simple.SimpleVectorStoreConfiguration
import com.demo.chat.service.dummy.DummyEmbeddingModel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.ai.document.Document
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.ai.vectorstore.SimpleVectorStore
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.core.env.MapPropertySource

class SimpleVectorStoreConfigurationTests {

    @Test
    fun `vector simple with embedding mock creates the store bean`() {
        val context = AnnotationConfigApplicationContext()
        context.environment.propertySources.addFirst(
            MapPropertySource("test", mapOf("app.service.core.vector" to "simple"))
        )
        context.beanFactory.registerSingleton("embeddingModel", DummyEmbeddingModel())
        context.register(SimpleVectorStoreConfiguration::class.java)
        context.refresh()

        try {
            assertThat(context.getBean(VectorStore::class.java))
                .isInstanceOf(SimpleVectorStore::class.java)
        } finally {
            context.close()
        }
    }

    @Test
    fun `vector selector unset creates no store bean`() {
        val context = AnnotationConfigApplicationContext()
        context.beanFactory.registerSingleton("embeddingModel", DummyEmbeddingModel())
        context.register(SimpleVectorStoreConfiguration::class.java)
        context.refresh()

        try {
            assertThat(context.getBeanNamesForType(VectorStore::class.java)).isEmpty()
        } finally {
            context.close()
        }
    }

    @Test
    fun `simple store stores and recalls message documents`() {
        val store = SimpleVectorStore.builder(DummyEmbeddingModel()).build()
        store.add(
            listOf(
                Document.builder().id("a").text("apple banana")
                    .metadata(mapOf("kind" to "message")).build(),
                Document.builder().id("b").text("zebra stripe")
                    .metadata(mapOf("kind" to "message")).build(),
            )
        )

        val hits = store.similaritySearch(
            SearchRequest.builder().query("apple banana").topK(2).build()
        )

        assertThat(hits).hasSize(2)
        assertThat(hits.first().id).isEqualTo("a")
        assertThat(hits.first().score).isNotNull
    }
}
```

- [ ] **Step 3: Run the tests to verify they fail**

Run (online once is fine here too, until the BOM artifacts are cached by Task 1; offline after):
`mvn -o -pl chat-core,chat-vector-simple test`
Expected: FAIL — `SimpleVectorStoreConfiguration` does not exist yet (compile error in the test module).

- [ ] **Step 4: Write the configuration**

Create `chat-vector-simple/src/main/kotlin/com/demo/chat/config/vector/simple/SimpleVectorStoreConfiguration.kt`:

```kotlin
package com.demo.chat.config.vector.simple

import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.ai.vectorstore.SimpleVectorStore
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty(prefix = "app.service.core", name = ["vector"], havingValue = "simple")
class SimpleVectorStoreConfiguration {

    @Bean
    fun simpleVectorStore(embeddingModel: EmbeddingModel): VectorStore =
        SimpleVectorStore.builder(embeddingModel).build()
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `mvn -o -pl chat-core,chat-vector-simple test`
Expected: PASS, all three tests.

- [ ] **Step 6: Commit**

```bash
git add pom.xml chat-vector-simple/
git commit -m "feat: add chat-vector-simple provider module"
```

- [ ] **Step 7: Log the milestone**

```bash
fp comment CHAT-hfytrwyh "Task 5 done: chat-vector-simple module. SimpleVectorStoreConfiguration gated on vector=simple. Context wiring test and container-free store behavior test pass. Commit <short-sha>."
```

---

## Task 6: chat-vector-redis module (CHAT-sgmwencx)

**Files:**
- Modify: `pom.xml` (`<modules>`)
- Create: `chat-vector-redis/pom.xml`
- Create: `chat-vector-redis/src/main/kotlin/com/demo/chat/config/vector/redis/RedisVectorStoreConfiguration.kt`
- Test: `chat-vector-redis/src/test/kotlin/com/demo/chat/test/vector/redis/RedisVectorStoreConfigurationTests.kt`

**Interfaces:**
- Consumes: `RedisVectorStore`, `MetadataField` (spring-ai-redis-store, Task 1 BOM), `JedisPooled` (redis.clients.jedis, version from the Spring Boot BOM), `EmbeddingModel`, `app.key.type`, `spring.redis.host`, `spring.redis.port`.
- Produces: bean `redisVectorStore(): VectorStore` when `app.service.core.vector=redis`. Index `chat:vector:<keyType>:message`, prefix `chat:vector:<keyType>:message:`, tag metadata fields `kind`, `keyType`, `topicId`, `userId`, `initializeSchema(true)`.

Nodeid note: this test activates no claim store. Key and persistence are not involved; only the vector store talks to Redis. It therefore claims no node id and adds no row to the docs/NODEID-CLAIM.md table.

- [ ] **Step 1: Register the module and write its pom**

In the root `pom.xml` `<modules>`, after `<module>chat-vector-simple</module>`, add:

```xml
<module>chat-vector-redis</module>
```

Create `chat-vector-redis/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.demo</groupId>
        <artifactId>chat-parent</artifactId>
        <version>0.0.1</version>
    </parent>

    <groupId>com.demo</groupId>
    <artifactId>chat-vector-redis</artifactId>
    <version>0.0.1</version>
    <name>chat-vector-redis</name>
    <description>Redis VectorStore provider for the shared runtime. Jedis-backed, Redis Stack required.</description>

    <dependencies>
        <dependency>
            <groupId>com.demo</groupId>
            <artifactId>chat-core</artifactId>
            <version>0.0.1</version>
        </dependency>
        <dependency>
            <groupId>com.demo</groupId>
            <artifactId>chat-core</artifactId>
            <type>test-jar</type>
            <version>0.0.1</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-vector-store</artifactId>
        </dependency>
        <!-- Jedis, not Lettuce: the Spring AI RedisVectorStore is Jedis-backed.
             The repo data path stays Lettuce. Version comes from the Spring
             Boot BOM. -->
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-redis-store</artifactId>
        </dependency>
        <dependency>
            <groupId>redis.clients</groupId>
            <artifactId>jedis</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter-engine</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>testcontainers</artifactId>
            <version>1.21.4</version>
            <scope>test</scope>
        </dependency>
        <!-- Redis's own testcontainers module, as Spring AI 1.0.3's
             RedisVectorStoreIT uses it (com.redis.testcontainers
             .RedisStackContainer). Pins the redis/redis-stack-server
             image; do not guess a tag. -->
        <dependency>
            <groupId>com.redis</groupId>
            <artifactId>testcontainers-redis</artifactId>
            <version>2.2.0</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <sourceDirectory>${project.basedir}/src/main/kotlin</sourceDirectory>
        <testSourceDirectory>${project.basedir}/src/test/kotlin</testSourceDirectory>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <skip>true</skip>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.jetbrains.kotlin</groupId>
                <artifactId>kotlin-maven-plugin</artifactId>
                <configuration>
                    <args>
                        <arg>-Xjsr305=strict</arg>
                    </args>
                    <compilerPlugins>
                        <plugin>spring</plugin>
                    </compilerPlugins>
                </configuration>
                <dependencies>
                    <dependency>
                        <groupId>org.jetbrains.kotlin</groupId>
                        <artifactId>kotlin-maven-allopen</artifactId>
                        <version>${kotlin.version}</version>
                    </dependency>
                </dependencies>
            </plugin>
        </plugins>
    </build>
</project>
```

The test uses `com.redis:testcontainers-redis` (Spring AI's own RedisVectorStoreIT does). The artifact provides `com.redis.testcontainers.RedisStackContainer`, which has no no-arg constructor; instantiate with `DEFAULT_IMAGE_NAME.withTag(DEFAULT_TAG)` to pin the image. Do not guess a tag.

- [ ] **Step 2: Write the failing test**

Create `chat-vector-redis/src/test/kotlin/com/demo/chat/test/vector/redis/RedisVectorStoreConfigurationTests.kt`:

```kotlin
package com.demo.chat.test.vector.redis

import com.demo.chat.service.dummy.DummyEmbeddingModel
import com.redis.testcontainers.RedisStackContainer
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.ai.document.Document
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.ai.vectorstore.redis.RedisVectorStore
import org.springframework.ai.vectorstore.redis.RedisVectorStore.MetadataField
import redis.clients.jedis.JedisPooled

/**
 * Proves the shared runtime vector path: Jedis-backed RedisVectorStore
 * against a real Redis Stack container, with the isolation scheme the
 * deploy uses. No node id claim: key and persistence are not involved, so
 * no claim store activates (docs/NODEID-CLAIM.md).
 */
@Tag("integration")
class RedisVectorStoreConfigurationTests {

    companion object {
        val stack = RedisStackContainer(
            RedisStackContainer.DEFAULT_IMAGE_NAME.withTag(RedisStackContainer.DEFAULT_TAG)
        ).apply { start() }
    }

    private fun store(): VectorStore {
        // One index per test: the container is shared by the class, so a
        // fixed index name would leak documents between tests.
        val index = "chat:vector:long:message-${UUID.randomUUID()}"
        return RedisVectorStore.builder(
            JedisPooled(stack.host, stack.firstMappedPort),
            DummyEmbeddingModel(),
        )
            .indexName(index)
            .prefix("$index:")
            .metadataFields(
                MetadataField.tag("kind"),
                MetadataField.tag("keyType"),
                MetadataField.tag("topicId"),
                MetadataField.tag("userId"),
            )
            .initializeSchema(true)
            .build()
            // Index creation happens in afterPropertiesSet(). Spring calls
            // it for beans; this inline-built store must call it itself.
            .also { it.afterPropertiesSet() }
    }

    private fun messageDoc(id: Long, topic: Long, user: Long, text: String) =
        Document.builder()
            .id("message:long:$id")
            .text(text)
            .metadata(
                mapOf(
                    "kind" to "message",
                    "messageId" to id.toString(),
                    "topicId" to topic.toString(),
                    "userId" to user.toString(),
                    "keyType" to "long",
                )
            )
            .build()

    @Test
    fun `redis store stores and recalls message documents with topic filter`() {
        val vectorStore = store()
        vectorStore.add(
            listOf(
                messageDoc(1, 3, 7, "apple banana"),
                messageDoc(2, 3, 7, "apple pie"),
                messageDoc(3, 9, 7, "zebra stripe"),
            )
        )

        val hits = vectorStore.similaritySearch(
            SearchRequest.builder()
                .query("apple banana")
                .topK(5)
                .filterExpression("kind == 'message' && keyType == 'long' && topicId == '3'")
                .build()
        )

        Assertions.assertThat(hits.map { it.id }).containsExactlyInAnyOrder("message:long:1", "message:long:2")
        Assertions.assertThat(hits.first().score).isNotNull
    }

    @Test
    fun `delete removes by document id`() {
        val vectorStore = store()
        vectorStore.add(listOf(messageDoc(10, 3, 7, "apple banana")))

        vectorStore.delete(listOf("message:long:10"))

        val hits = vectorStore.similaritySearch(
            SearchRequest.builder().query("apple banana").topK(5).build()
        )
        Assertions.assertThat(hits).isEmpty()
    }

    @Test
    fun `configuration creates the store bean for vector redis`() {
        val context = AnnotationConfigApplicationContext()
        context.environment.propertySources.addFirst(
            MapPropertySource(
                "test",
                mapOf(
                    "app.service.core.vector" to "redis",
                    "app.key.type" to "long",
                    "spring.redis.host" to stack.host,
                    "spring.redis.port" to stack.firstMappedPort.toString(),
                )
            )
        )
        context.beanFactory.registerSingleton("embeddingModel", DummyEmbeddingModel())
        context.register(RedisVectorStoreConfiguration::class.java)
        context.refresh()

        try {
            Assertions
                .assertThat(context.getBean(VectorStore::class.java))
                .isInstanceOf(RedisVectorStore::class.java)
        } finally {
            context.close()
        }
    }
}
```

With these imports added at the top of the test file:

```kotlin
import com.demo.chat.config.vector.redis.RedisVectorStoreConfiguration
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.core.env.MapPropertySource
import java.util.UUID
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `mvn -o -pl chat-core,chat-vector-redis test`
Expected: FAIL — `com.demo.chat.config.vector.redis.RedisVectorStoreConfiguration` does not exist yet (compile error). The store-level tests compile against the inline-built store; the red is the configuration reference.

- [ ] **Step 4: Write the configuration**

Create `chat-vector-redis/src/main/kotlin/com/demo/chat/config/vector/redis/RedisVectorStoreConfiguration.kt`:

```kotlin
package com.demo.chat.config.vector.redis

import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.ai.vectorstore.redis.RedisVectorStore
import org.springframework.ai.vectorstore.redis.RedisVectorStore.MetadataField
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import redis.clients.jedis.JedisPooled

/**
 * Shared runtime vector store. Jedis-backed, Redis Stack required.
 *
 * Isolation is per key type: index chat:vector:<keyType>:message and key
 * prefix chat:vector:<keyType>:message:. A metadata field alone does not
 * isolate Redis indexes, so the index name carries the key type, and the
 * recall filters carry keyType as well.
 */
@Configuration
@ConditionalOnProperty(prefix = "app.service.core", name = ["vector"], havingValue = "redis")
class RedisVectorStoreConfiguration {

    @Bean
    fun redisVectorStore(
        embeddingModel: EmbeddingModel,
        environment: Environment,
        @Value("\${app.key.type}") keyType: String,
    ): VectorStore {
        val host = environment.getProperty("spring.redis.host", "localhost")
        val port = environment.getProperty("spring.redis.port", "6379").toInt()
        val jedis = JedisPooled(host, port)

        return RedisVectorStore.builder(jedis, embeddingModel)
            .indexName("chat:vector:$keyType:message")
            .prefix("chat:vector:$keyType:message:")
            .metadataFields(
                MetadataField.tag("kind"),
                MetadataField.tag("keyType"),
                MetadataField.tag("topicId"),
                MetadataField.tag("userId"),
            )
            .initializeSchema(true)
            .build()
    }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `mvn -o -pl chat-core,chat-vector-redis test`
Expected: PASS, all three tests. These need a Docker daemon; that is why they are `@Tag("integration")` and run under `-Pintegration` in CI (docs/BUILD-HEALTH.md verifier loop). A plain `mvn -o -pl chat-core,chat-vector-redis test` skips them.

- [ ] **Step 6: Commit**

```bash
git add pom.xml chat-vector-redis/
git commit -m "feat: add chat-vector-redis provider module"
```

- [ ] **Step 7: Log the milestone**

```bash
fp comment CHAT-sgmwencx "Task 6 done: chat-vector-redis module. RedisVectorStoreConfiguration gated on vector=redis, Jedis-backed, per-keyType index and prefix, explicit schema init. Redis Stack integration tests pass under -Pintegration. Commit <short-sha>."
```

---

## Task 7: Send chain write-through (CHAT-oofefzrx)

**Files:**
- Modify: `chat-service-composite/pom.xml` (add `spring-ai-vector-store`)
- Modify: `chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/impl/MessagingServiceImpl.kt`
- Create: `chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/impl/VectorStoreMessageVectorIndexer.kt`
- Create: `chat-service-composite/src/main/kotlin/com/demo/chat/config/service/composite/VectorRecallServiceConfiguration.kt`
- Modify: `chat-service-composite/src/main/kotlin/com/demo/chat/config/service/composite/CompositeServiceBeansConfiguration.kt`
- Test: `chat-service-composite/src/test/kotlin/com/demo/chat/test/service/composite/MessagingServiceVectorTests.kt` (first test in this module; the `src/test` tree is created here)
- Test: `chat-service-composite/src/test/kotlin/com/demo/chat/test/config/VectorRecallServiceConfigurationTests.kt`

**Interfaces:**
- Consumes: `MessageVectorIndexer<T>`, `MessageDocumentMapper<T>` (Tasks 2-3), `MockVectorStore` (Task 4, test-jar), `MessagePersistence<T, V>`, `MessageIndexService<T, V, Q>`, `TopicPubSubService<T, V>` (chat-core `com.demo.chat.service.core`), `Key.funKey(t)`, `VectorStore`.
- Produces:
  - `MessagingServiceImpl<T, V, Q>` constructor gains a 5th parameter `messageVectorIndexer: MessageVectorIndexer<T>? = null`. Send builds the message once; with an indexer the chain is persistence, index, vector, pubsub in order; without, the chain is byte-identical to today.
  - `VectorStoreMessageVectorIndexer<T>(vectorStore: VectorStore, mapper: MessageDocumentMapper<T>)` — `add` skips `record == false` and bridges on `Schedulers.boundedElastic()`; `remove` deletes by `mapper.documentId(key.id)`.
  - `VectorRecallServiceConfiguration<T, V, Q>(typeUtil: TypeUtil<T>)` — `@ConditionalOnProperty("app.service.composite")`; bean `messageVectorIndexer(): MessageVectorIndexer<T>` gated `@ConditionalOnProperty(prefix = "app.service.core", name = ["vector", "embedding"])`. Task 8 adds the `messageRecallService` bean to this same file.
  - `CompositeServiceBeansConfiguration` gains `ObjectProvider<MessageVectorIndexer<T>>` and passes `.ifAvailable` into `messageService()`.

- [ ] **Step 1: Add the dependency**

In `chat-service-composite/pom.xml` `<dependencies>`, add:

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-vector-store</artifactId>
</dependency>
```

- [ ] **Step 2: Write the failing tests**

Create `chat-service-composite/src/test/kotlin/com/demo/chat/test/service/composite/MessagingServiceVectorTests.kt`:

```kotlin
package com.demo.chat.test.service.composite

import com.demo.chat.domain.ByStringRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.LongUtil
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
import com.demo.chat.domain.MessageSendRequest
import com.demo.chat.service.composite.impl.MessagingServiceImpl
import com.demo.chat.service.composite.impl.VectorStoreMessageVectorIndexer
import com.demo.chat.service.core.MessageIndexService
import com.demo.chat.service.core.MessagePersistence
import com.demo.chat.service.core.TopicPubSubService
import com.demo.chat.service.vector.MessageDocumentMapper
import com.demo.chat.service.vector.MessageVectorIndexer
import com.demo.chat.test.vector.MockVectorStore
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito
import org.mockito.InOrder
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

class MessagingServiceVectorTests {

    private val messageIndex = mock<MessageIndexService<Long, String, Any>>()
    private val messagePersistence = mock<MessagePersistence<Long, String>>()
    private val pubsub = mock<TopicPubSubService<Long, String>>()
    private val store = MockVectorStore()
    private val mapper = MessageDocumentMapper<Long>(LongUtil(), "long")
    private val realIndexer = VectorStoreMessageVectorIndexer<Long>(store, mapper)

    private fun givenKey() {
        BDDMockito.given(messagePersistence.key()).willReturn(Mono.just(Key.funKey(100L)))
        BDDMockito.given(messagePersistence.add(any<Message<Long, String>>())).willReturn(Mono.empty())
        BDDMockito.given(messageIndex.add(any<Message<Long, String>>())).willReturn(Mono.empty())
        BDDMockito.given(pubsub.sendMessage(any<Message<Long, String>>())).willReturn(Mono.empty())
    }

    private fun request() = MessageSendRequest("hello apple", 20L, 30L)

    @Test
    fun `send calls persistence then index then vector then pubsub`() {
        val indexer = mock<MessageVectorIndexer<Long>>()
        BDDMockito.given(indexer.add(any<Message<Long, String>>())).willReturn(Mono.empty())
        givenKey()

        val service = MessagingServiceImpl(
            messageIndex, messagePersistence, pubsub,
            { ByStringRequest("unused") }, indexer
        )

        StepVerifier.create(service.send(request())).expectNext(Key.funKey(100L)).verifyComplete()

        val inOrder: InOrder = Mockito.inOrder(messagePersistence, messageIndex, indexer, pubsub)
        inOrder.verify(messagePersistence).add(any<Message<Long, String>>())
        inOrder.verify(messageIndex).add(any<Message<Long, String>>())
        inOrder.verify(indexer).add(any<Message<Long, String>>())
        inOrder.verify(pubsub).sendMessage(any<Message<Long, String>>())
    }

    @Test
    fun `inactive chain stays three steps when there is no indexer`() {
        givenKey()

        val service = MessagingServiceImpl(
            messageIndex, messagePersistence, pubsub,
            { ByStringRequest("unused") }
        )

        StepVerifier.create(service.send(request())).expectNext(Key.funKey(100L)).verifyComplete()

        val inOrder: InOrder = Mockito.inOrder(messagePersistence, messageIndex, pubsub)
        inOrder.verify(messagePersistence).add(any<Message<Long, String>>())
        inOrder.verify(messageIndex).add(any<Message<Long, String>>())
        inOrder.verify(pubsub).sendMessage(any<Message<Long, String>>())
        Assertions.assertThat(store.ids).isEmpty()
    }

    @Test
    fun `vector failure stops pubsub and fails send`() {
        val failing = mock<MessageVectorIndexer<Long>>()
        BDDMockito.given(failing.add(any<Message<Long, String>>()))
            .willReturn(Mono.error(Exception("vector down")))
        givenKey()

        val service = MessagingServiceImpl(
            messageIndex, messagePersistence, pubsub,
            { ByStringRequest("unused") }, failing
        )

        StepVerifier.create(service.send(request())).expectError().verify()
        Mockito.verify(pubsub, Mockito.never()).sendMessage(any<Message<Long, String>>())
        Mockito.verify(messagePersistence, Mockito.times(1)).add(any<Message<Long, String>>())
    }

    @Test
    fun `record false skips the vector write`() {
        val alert = Message.create(MessageKey.create(100L, 20L, 30L), "joined", false)

        StepVerifier.create(realIndexer.add(alert)).verifyComplete()

        Assertions.assertThat(store.ids).isEmpty()
    }

    @Test
    fun `recorded message enters the store on bounded elastic`() {
        val message = Message.create(MessageKey.create(100L, 20L, 30L), "hello apple", true)

        StepVerifier.create(realIndexer.add(message)).verifyComplete()

        Assertions.assertThat(store.ids).containsExactly("message:long:100")
        Assertions.assertThat(store.lastWriteThread).startsWith("boundedElastic")
    }

    @Test
    fun `remove deletes by document id`() {
        val message = Message.create(MessageKey.create(100L, 20L, 30L), "hello apple", true)
        StepVerifier.create(realIndexer.add(message)).verifyComplete()

        StepVerifier.create(realIndexer.remove(Key.funKey(100L))).verifyComplete()

        Assertions.assertThat(store.ids).isEmpty()
    }
}
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `mvn -o -pl chat-core,chat-service-composite test`
Expected: FAIL — `VectorStoreMessageVectorIndexer` does not exist, and `MessagingServiceImpl` has no 5-argument constructor (compile errors).

- [ ] **Step 4: Write the indexer**

Create `chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/impl/VectorStoreMessageVectorIndexer.kt`:

```kotlin
package com.demo.chat.service.composite.impl

import com.demo.chat.domain.Key
import com.demo.chat.domain.Message
import com.demo.chat.service.vector.MessageDocumentMapper
import com.demo.chat.service.vector.MessageVectorIndexer
import org.springframework.ai.vectorstore.VectorStore
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

/**
 * Bridges the blocking VectorStore to the send chain. Every Spring AI call
 * runs on Schedulers.boundedElastic(). record == false (join and leave
 * alerts) returns success without a write.
 */
class VectorStoreMessageVectorIndexer<T>(
    private val vectorStore: VectorStore,
    private val mapper: MessageDocumentMapper<T>,
) : MessageVectorIndexer<T> {

    override fun add(message: Message<T, String>): Mono<Void> =
        if (!message.record) {
            Mono.empty()
        } else {
            Mono.fromCallable {
                vectorStore.add(listOf(mapper.toDocument(message)))
            }
                .subscribeOn(Schedulers.boundedElastic())
                .then()
        }

    override fun remove(key: Key<T>): Mono<Void> =
        Mono.fromCallable {
            vectorStore.delete(listOf(mapper.documentId(key.id)))
        }
            .subscribeOn(Schedulers.boundedElastic())
            .then()
}
```

- [ ] **Step 5: Modify MessagingServiceImpl**

Replace the constructor and `send` in `chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/impl/MessagingServiceImpl.kt`. `listenTopic` and `messageById` stay unchanged.

Constructor:

```kotlin
open class MessagingServiceImpl<T, V, Q>(
    private val messageIndex: MessageIndexService<T, V, Q>,
    private val messagePersistence: MessagePersistence<T, V>,
    private val pubsub: TopicPubSubService<T, V>,
    private val topicIdToQuery: Function<ByIdRequest<T>, Q>,
    private val messageVectorIndexer: MessageVectorIndexer<T>? = null,
) : ChatMessageService<T, V> {
```

Add the import:

```kotlin
import com.demo.chat.service.vector.MessageVectorIndexer
```

New `send`:

```kotlin
    override fun send(req: MessageSendRequest<T, V>): Mono<out Key<T>> {
        val sending: (T) -> Message<T, V> = {
            Message.create(MessageKey.create(it, req.from, req.dest), req.msg, true)
        }

        return messagePersistence
            .key()
            .flatMap { messageKey ->
                val message = sending(messageKey.id)
                // Each write is deferred. A step starts only after the step
                // before it completes, so a failed write stops the steps
                // that follow it.
                val writes: MutableList<Mono<Void>> = mutableListOf(
                    Mono.defer { messagePersistence.add(message) },
                    Mono.defer { messageIndex.add(message) },
                )
                val indexer = messageVectorIndexer
                if (indexer != null) writes.add(Mono.defer { indexer.add(asText(message)) })
                writes.add(Mono.defer { pubsub.sendMessage(message) })
                Flux.concat(*writes.toTypedArray())
                    .then(Mono.just(messageKey))
            }
    }

    /**
     * The vector indexer takes text messages. Every composition binds V to
     * String, so this cast holds. A composition with a different V must not
     * set the recall selectors.
     */
    @Suppress("UNCHECKED_CAST")
    private fun asText(message: Message<T, V>): Message<T, String> =
        message as Message<T, String>
```

The message is built once and shared by every write. With no indexer the list is persistence, index, pubsub — the exact current chain.

Two details the first draft of this plan missed:

- `MessageVectorIndexer<T>.add` takes `Message<T, String>`, but this class is
  generic in `V`. The `asText` helper bridges the two types.
- Each write must go in `Mono.defer`. A direct call builds the publisher at
  assembly time. An eager call reaches the collaborator even when
  `Flux.concat` never subscribes to that step. The defer makes "a vector
  failure stops pubsub" a real guarantee.

- [ ] **Step 6: Write the vector recall configuration and wire the composite beans**

Create `chat-service-composite/src/main/kotlin/com/demo/chat/config/service/composite/VectorRecallServiceConfiguration.kt`:

```kotlin
package com.demo.chat.config.service.composite

import com.demo.chat.domain.TypeUtil
import com.demo.chat.service.composite.impl.VectorStoreMessageVectorIndexer
import com.demo.chat.service.vector.MessageDocumentMapper
import com.demo.chat.service.vector.MessageVectorIndexer
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Recall wiring for the composite services. The class-level gate is the
 * composite selector; each bean needs both recall selectors set. The
 * VectorStore bean comes from the active vector provider module.
 *
 * Interim capability wiring: @ConditionalOnProperty stands in for
 * @ProvidesCapability until the capability mechanism lands.
 */
@Configuration
@ConditionalOnProperty("app.service.composite")
class VectorRecallServiceConfiguration<T, V, Q>(
    private val typeUtil: TypeUtil<T>,
) {

    @Bean
    @ConditionalOnProperty(prefix = "app.service.core", name = ["vector", "embedding"])
    fun messageVectorIndexer(
        vectorStore: VectorStore,
        @Value("\${app.key.type}") keyType: String,
    ): MessageVectorIndexer<T> =
        VectorStoreMessageVectorIndexer(vectorStore, MessageDocumentMapper(typeUtil, keyType))
}
```

Modify `chat-service-composite/src/main/kotlin/com/demo/chat/config/service/composite/CompositeServiceBeansConfiguration.kt`. Add imports:

```kotlin
import com.demo.chat.service.vector.MessageVectorIndexer
import org.springframework.beans.factory.ObjectProvider
```

Extend the constructor (new last parameter):

```kotlin
class CompositeServiceBeansConfiguration<T, V, Q>(
    val persistenceBeans: PersistenceServiceBeans<T, V>,
    val indexBeans: IndexServiceBeans<T, V, Q>,
    val pubsub: PubSubServiceBeans<T, V>,
    val typeUtil: TypeUtil<T>,
    private val emptyMessageSupplier: EmptyMessageUtil<V>,
    private val queryConverters: RequestToQueryConverters<Q>,
    private val vectorIndexers: ObjectProvider<MessageVectorIndexer<T>>,
) : CompositeServiceBeans<T, V> {
```

Change the `messageService()` bean:

```kotlin
    @Bean
    override fun messageService() = MessagingServiceImpl(
        messageIndex = indexBeans.messageIndex(),
        messagePersistence = persistenceBeans.messagePersistence(),
        pubsub = pubsub.pubSubService(),
        topicIdToQuery = queryConverters::topicIdToQuery,
        messageVectorIndexer = vectorIndexers.ifAvailable,
    )
```

`ObjectProvider.ifAvailable` is null when the pair is unset, so inactive compositions keep the current chain.

- [ ] **Step 7: Write the configuration wiring tests**

Create `chat-service-composite/src/test/kotlin/com/demo/chat/test/config/VectorRecallServiceConfigurationTests.kt`:

```kotlin
package com.demo.chat.test.config

import com.demo.chat.config.service.composite.VectorRecallServiceConfiguration
import com.demo.chat.domain.LongUtil
import com.demo.chat.service.vector.MessageVectorIndexer
import com.demo.chat.test.vector.MockVectorStore
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.core.env.MapPropertySource

class VectorRecallServiceConfigurationTests {

    @Test
    fun `both selectors unset leaves recall inactive`() {
        val context = AnnotationConfigApplicationContext()
        context.environment.propertySources.addFirst(
            MapPropertySource("test", mapOf("app.service.composite" to "true"))
        )
        context.beanFactory.registerSingleton("typeUtil", LongUtil())
        context.beanFactory.registerSingleton("vectorStore", MockVectorStore())
        context.register(VectorRecallServiceConfiguration::class.java)
        context.refresh()

        try {
            Assertions
                .assertThat(context.getBeanNamesForType(MessageVectorIndexer::class.java))
                .isEmpty()
        } finally {
            context.close()
        }
    }

    @Test
    fun `both selectors set creates the indexer bean`() {
        val context = AnnotationConfigApplicationContext()
        context.environment.propertySources.addFirst(
            MapPropertySource(
                "test",
                mapOf(
                    "app.service.composite" to "true",
                    "app.service.core.vector" to "simple",
                    "app.service.core.embedding" to "mock",
                    "app.key.type" to "long",
                )
            )
        )
        context.beanFactory.registerSingleton("typeUtil", LongUtil())
        context.beanFactory.registerSingleton("vectorStore", MockVectorStore())
        context.register(VectorRecallServiceConfiguration::class.java)
        context.refresh()

        try {
            Assertions
                .assertThat(context.getBean(MessageVectorIndexer::class.java))
                .isNotNull
        } finally {
            context.close()
        }
    }

    @Test
    fun `one selector set creates no indexer bean`() {
        val context = AnnotationConfigApplicationContext()
        context.environment.propertySources.addFirst(
            MapPropertySource(
                "test",
                mapOf(
                    "app.service.composite" to "true",
                    "app.service.core.vector" to "simple",
                )
            )
        )
        context.beanFactory.registerSingleton("typeUtil", LongUtil())
        context.beanFactory.registerSingleton("vectorStore", MockVectorStore())
        context.register(VectorRecallServiceConfiguration::class.java)
        context.refresh()

        try {
            Assertions
                .assertThat(context.getBeanNamesForType(MessageVectorIndexer::class.java))
                .isEmpty()
        } finally {
            context.close()
        }
    }
}
```

Note: the "one selector set" case does not fail the context here because `VectorSelectorValidation` (Task 8) is not on this test's classpath scope yet. The startup failure behavior is asserted in Task 8.

- [ ] **Step 8: Run the tests to verify they pass**

Run: `mvn -o -pl chat-core,chat-service-composite test`
Expected: PASS, all tests in the module, including the new send-chain and wiring tests.

- [ ] **Step 9: Commit**

```bash
git add chat-service-composite/pom.xml chat-service-composite/src/
git commit -m "feat: write message documents to the vector store on send"
```

- [ ] **Step 10: Log the milestone**

```bash
fp comment CHAT-oofefzrx "Task 7 done: send chain is persistence, index, vector, pubsub with the message built once. No indexer, chain is unchanged. Vector failure stops pubsub and fails send. record=false skips the write. boundedElastic bridge verified by thread name. Composite wiring via ObjectProvider. Commit <short-sha>."
```

---

## Task 8: Recall service implementation and selector validation (CHAT-mgitmbdo)

**Files:**
- Create: `chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/impl/MessageRecallServiceImpl.kt`
- Create: `chat-core/src/main/kotlin/com/demo/chat/config/VectorSelectorValidation.kt`
- Modify: `chat-service-composite/src/main/kotlin/com/demo/chat/config/service/composite/VectorRecallServiceConfiguration.kt` (add the `messageRecallService` bean)
- Test: `chat-service-composite/src/test/kotlin/com/demo/chat/test/service/composite/MessageRecallServiceImplTests.kt`
- Test: `chat-core/src/test/kotlin/com/demo/chat/test/config/VectorSelectorValidationTests.kt`

**Interfaces:**
- Consumes: `MessageRecallService<T>`, the request DTOs (Task 2), `SearchRequest`, `VectorStore.similaritySearch`, `TypeUtil<T>`, `MessageKey.create(id, from, dest)`, `MockVectorStore` (Task 4).
- Produces:
  - `MessageRecallServiceImpl<T>(vectorStore: VectorStore, typeUtil: TypeUtil<T>, keyType: String) : MessageRecallService<T>` — validates, builds the `SearchRequest` with the exact spec filter, bridges on `Schedulers.boundedElastic()`, maps results to `MessageRecallHit<T>` (key from the document id and the `userId` / `topicId` metadata, score from the document).
  - `VectorSelectorValidation.validate(vector: String?, embedding: String?)` (object) + `VectorSelectorValidationConfiguration` in `com.demo.chat.config` (unconditional; runs in every composition; throws at startup with a message that names both selectors).
  - Bean `messageRecallService(): MessageRecallService<T>` in `VectorRecallServiceConfiguration`, gated on the selector pair.

- [ ] **Step 1: Write the failing validation tests**

Create `chat-core/src/test/kotlin/com/demo/chat/test/config/VectorSelectorValidationTests.kt`:

```kotlin
package com.demo.chat.test.config

import com.demo.chat.config.VectorSelectorValidationConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner

class VectorSelectorValidationTests {

    @Test
    fun `vector set without embedding fails startup naming both selectors`() {
        val failure = failureFor(mapOf("app.service.core.vector" to "simple"))

        assertThat(failure).isNotNull
        assertThat(failure.message)
            .contains("app.service.core.vector")
            .contains("app.service.core.embedding")
    }

    @Test
    fun `embedding set without vector fails startup naming both selectors`() {
        val failure = failureFor(mapOf("app.service.core.embedding" to "mock"))

        assertThat(failure).isNotNull
        assertThat(failure.message)
            .contains("app.service.core.vector")
            .contains("app.service.core.embedding")
    }

    @Test
    fun `reserved gateway embedding fails startup`() {
        val failure = failureFor(
            mapOf("app.service.core.vector" to "redis", "app.service.core.embedding" to "gateway")
        )

        assertThat(failure).isNotNull
        assertThat(failure.message).contains("app.service.core.embedding=gateway")
    }

    @Test
    fun `unknown vector value fails startup`() {
        val failure = failureFor(
            mapOf("app.service.core.vector" to "sqlite", "app.service.core.embedding" to "mock")
        )

        assertThat(failure).isNotNull
        assertThat(failure.message).contains("app.service.core.vector=sqlite")
    }

    @Test
    fun `both unset starts`() {
        runner(emptyMap()).run { context ->
            assertThat(context).hasNotFailed()
        }
    }

    @Test
    fun `legal pairs start`() {
        for ((vector, embedding) in listOf("mock" to "mock", "simple" to "mock", "redis" to "mock")) {
            runner(
                mapOf(
                    "app.service.core.vector" to vector,
                    "app.service.core.embedding" to embedding,
                )
            ).run { context ->
                assertThat(context).hasNotFailed()
            }
        }
    }

    private fun runner(properties: Map<String, String>): ApplicationContextRunner =
        ApplicationContextRunner()
            .withPropertyValues(*properties.map { "${it.key}=${it.value}" }.toTypedArray())
            .withUserConfiguration(VectorSelectorValidationConfiguration::class.java)

    private fun failureFor(properties: Map<String, String>): Throwable {
        var failure: Throwable? = null
        runner(properties).run { context ->
            failure = context.startupFailure
        }
        return failure
            ?: Assertions.fail<Throwable>("expected a startup failure for $properties")
    }
}
```

Two defects in this step became clear during the work. `withPropertyValues`
takes `String...` values in `key=value` form, not a map. A captured `var` does
not smart-cast, so `failureFor` returns a value that is not null.

- [ ] **Step 2: Run the validation tests to verify they fail**

Run: `mvn -o -pl chat-core test -Dtest=VectorSelectorValidationTests`
Expected: FAIL — `VectorSelectorValidationConfiguration` does not exist yet.

- [ ] **Step 3: Write the validation**

Create `chat-core/src/main/kotlin/com/demo/chat/config/VectorSelectorValidation.kt`:

```kotlin
package com.demo.chat.config

import org.springframework.beans.factory.SmartInitializingSingleton
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Startup check for the recall selector pair. The capability mechanism does
 * not exist yet; this bean is the interim gate. Migrate to
 * @ProvidesCapability when it lands.
 */
object VectorSelectorValidation {

    private val legalPairs = setOf(
        "mock" to "mock",
        "simple" to "mock",
        "redis" to "mock",
    )

    fun validate(vector: String?, embedding: String?) {
        val vectorSet = !vector.isNullOrBlank()
        val embeddingSet = !embedding.isNullOrBlank()
        if (!vectorSet && !embeddingSet) return

        if (vectorSet != embeddingSet) {
            throw IllegalStateException(
                "Recall selector pair incomplete: app.service.core.vector=$vector, " +
                    "app.service.core.embedding=$embedding. Both selectors must be set together."
            )
        }

        if (vector to embedding !in legalPairs) {
            throw IllegalStateException(
                "Illegal recall selector pair: app.service.core.vector=$vector, " +
                    "app.service.core.embedding=$embedding. Legal pairs: " +
                    "vector=mock with embedding=mock, vector=simple with embedding=mock, " +
                    "vector=redis with embedding=mock."
            )
        }
    }
}

// The module does not enable the Kotlin all-open compiler plugin. A
// configuration class must be open, like BaseDomainConfiguration.
@Configuration
open class VectorSelectorValidationConfiguration(
    @Value("\${app.service.core.vector:}") vector: String,
    @Value("\${app.service.core.embedding:}") embedding: String,
) {

    private val vectorSelector = vector
    private val embeddingSelector = embedding

    @Bean
    open fun vectorSelectorValidation(): SmartInitializingSingleton =
        SmartInitializingSingleton {
            VectorSelectorValidation.validate(vectorSelector, embeddingSelector)
        }
}
```

Three defects in this step became clear during the work. `SmartInitializingSingleton`
lives in `org.springframework.beans.factory`. `chat-core/pom.xml` declares no
Spring all-open compiler plugin, so the class and the bean method need `open`.
A constructor parameter is not visible in a member function body, so the class
holds each selector in a property.

- [ ] **Step 4: Run the validation tests to verify they pass**

Run: `mvn -o -pl chat-core test`
Expected: PASS, including `VectorSelectorValidationTests`.

- [ ] **Step 5: Write the failing recall service tests**

Create `chat-service-composite/src/test/kotlin/com/demo/chat/test/service/composite/MessageRecallServiceImplTests.kt`:

```kotlin
package com.demo.chat.test.service.composite

import com.demo.chat.domain.GlobalRecallRequest
import com.demo.chat.domain.InvalidRecallRequestException
import com.demo.chat.domain.LongUtil
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
import com.demo.chat.domain.TopicRecallRequest
import com.demo.chat.domain.UserRecallRequest
import com.demo.chat.service.composite.impl.MessageRecallServiceImpl
import com.demo.chat.service.vector.MessageDocumentMapper
import com.demo.chat.test.vector.MockVectorStore
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.ai.vectorstore.filter.FilterExpressionTextParser
import reactor.test.StepVerifier

class MessageRecallServiceImplTests {

    private val store = MockVectorStore()
    private val mapper = MessageDocumentMapper<Long>(LongUtil(), "long")
    private val service = MessageRecallServiceImpl<Long>(store, LongUtil(), "long")
    private val parser = FilterExpressionTextParser()

    @BeforeEach
    fun seed() {
        store.add(
            listOf(
                mapper.toDocument(Message.create(MessageKey.create(1L, 10L, 100L), "apple banana", true)),
                mapper.toDocument(Message.create(MessageKey.create(2L, 10L, 100L), "apple pie", true)),
                mapper.toDocument(Message.create(MessageKey.create(3L, 20L, 100L), "zebra stripe", true)),
                mapper.toDocument(Message.create(MessageKey.create(4L, 20L, 200L), "apple banana cake", true)),
            )
        )
    }

    @Test
    fun `topic recall builds the topic filter and returns only that topic`() {
        val hits = StepVerifier.create(service.recallInTopic(TopicRecallRequest(100L, "apple banana")))
            .record()
            .verifyComplete()
            .actual()

        Assertions.assertThat(store.lastFilter)
            .isEqualTo(parser.parse("kind == 'message' && keyType == 'long' && topicId == '100'"))
        Assertions.assertThat(store.lastTopK).isEqualTo(10)
        Assertions.assertThat(store.lastThreshold).isEqualTo(0.0)
        // Seeds 1, 2, and 3 are all in topic 100.
        Assertions.assertThat(hits.map { it.key.dest }).containsOnly(100L)
        Assertions.assertThat(hits.map { it.key.id }).containsExactlyInAnyOrder(1L, 2L, 3L)
        Assertions.assertThat(hits).allSatisfy { Assertions.assertThat(it.score).isNotNull() }
    }

    @Test
    fun `user recall builds the user filter and returns only that user`() {
        val hits = StepVerifier.create(service.recallByUser(UserRecallRequest(10L, "apple banana")))
            .record()
            .verifyComplete()
            .actual()

        Assertions.assertThat(store.lastFilter)
            .isEqualTo(parser.parse("kind == 'message' && keyType == 'long' && userId == '10'"))
        Assertions.assertThat(hits.map { it.key.from }).containsOnly(10L)
        Assertions.assertThat(hits.map { it.key.id }).containsExactlyInAnyOrder(1L, 2L)
    }

    @Test
    fun `global recall builds the global filter and returns both topics`() {
        val hits = StepVerifier.create(service.recallGlobal(GlobalRecallRequest("apple banana")))
            .record()
            .verifyComplete()
            .actual()

        Assertions.assertThat(store.lastFilter)
            .isEqualTo(parser.parse("kind == 'message' && keyType == 'long'"))
        // All four seeds pass the global filter with the accept-all threshold.
        Assertions.assertThat(hits.map { it.key.id }).containsExactlyInAnyOrder(1L, 2L, 3L, 4L)
    }

    @Test
    fun `hits carry the message key and score only`() {
        val hits = StepVerifier.create(service.recallInTopic(TopicRecallRequest(200L, "apple banana")))
            .record()
            .verifyComplete()
            .actual()

        Assertions.assertThat(hits).hasSize(1)
        val hit = hits.single()
        Assertions.assertThat(hit.key.id).isEqualTo(4L)
        Assertions.assertThat(hit.key.from).isEqualTo(20L)
        Assertions.assertThat(hit.key.dest).isEqualTo(200L)
        Assertions.assertThat(hit.score).isNotNull
    }

    @Test
    fun `high threshold filters weak matches`() {
        val hits = StepVerifier.create(
            service.recallInTopic(TopicRecallRequest(100L, "apple banana", threshold = 0.9))
        )
            .record()
            .verifyComplete()
            .actual()

        Assertions.assertThat(store.lastThreshold).isEqualTo(0.9)
        Assertions.assertThat(hits.map { it.key.id }).contains(1L)
        Assertions.assertThat(hits.map { it.key.id }).doesNotContain(3L)
    }

    @Test
    fun `limit is passed as topK`() {
        service.recallGlobal(GlobalRecallRequest("apple banana", limit = 3)).blockLast()

        Assertions.assertThat(store.lastTopK).isEqualTo(3)
    }

    @Test
    fun `invalid request fails with InvalidRecallRequestException`() {
        StepVerifier.create(service.recallInTopic(TopicRecallRequest(100L, "  ")))
            .expectError(InvalidRecallRequestException::class.java)
            .verify()
        StepVerifier.create(service.recallGlobal(GlobalRecallRequest("q", limit = 51)))
            .expectError(InvalidRecallRequestException::class.java)
            .verify()
    }

    @Test
    fun `search runs on bounded elastic`() {
        service.recallGlobal(GlobalRecallRequest("apple banana")).blockLast()

        Assertions.assertThat(store.lastSearchThread).startsWith("boundedElastic")
    }
}
```

- [ ] **Step 6: Run the recall tests to verify they fail**

Run: `mvn -o -pl chat-core,chat-service-composite test -Dtest=MessageRecallServiceImplTests`
Expected: FAIL — `MessageRecallServiceImpl` does not exist yet.

- [ ] **Step 7: Write the recall service**

Create `chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/impl/MessageRecallServiceImpl.kt`:

```kotlin
package com.demo.chat.service.composite.impl

import com.demo.chat.domain.GlobalRecallRequest
import com.demo.chat.domain.MessageKey
import com.demo.chat.domain.TopicRecallRequest
import com.demo.chat.domain.TypeUtil
import com.demo.chat.domain.UserRecallRequest
import com.demo.chat.service.vector.MessageRecallHit
import com.demo.chat.service.vector.MessageRecallService
import org.springframework.ai.document.Document
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.ai.vectorstore.VectorStore
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

/**
 * Read-only recall. Returns keys and scores only; the caller reloads full
 * messages through existing persistence. Covers only messages sent while
 * recall was active; no backfill.
 */
class MessageRecallServiceImpl<T>(
    private val vectorStore: VectorStore,
    private val typeUtil: TypeUtil<T>,
    private val keyType: String,
) : MessageRecallService<T> {

    override fun recallInTopic(req: TopicRecallRequest<T>): Flux<MessageRecallHit<T>> {
        req.validate()
        val filter = "kind == 'message' && keyType == '$keyType' && topicId == '${typeUtil.toString(req.topicId)}'"
        return search(req.query, req.limit, req.threshold, filter)
    }

    override fun recallByUser(req: UserRecallRequest<T>): Flux<MessageRecallHit<T>> {
        req.validate()
        val filter = "kind == 'message' && keyType == '$keyType' && userId == '${typeUtil.toString(req.userId)}'"
        return search(req.query, req.limit, req.threshold, filter)
    }

    override fun recallGlobal(req: GlobalRecallRequest): Flux<MessageRecallHit<T>> {
        req.validate()
        val filter = "kind == 'message' && keyType == '$keyType'"
        return search(req.query, req.limit, req.threshold, filter)
    }

    private fun search(query: String, limit: Int, threshold: Double, filter: String): Flux<MessageRecallHit<T>> {
        val builder = SearchRequest.builder()
            .query(query)
            .topK(limit)
        if (threshold == 0.0) {
            builder.similarityThresholdAll()
        } else {
            builder.similarityThreshold(threshold)
        }
        val request = builder.filterExpression(filter).build()

        return Mono.fromCallable { vectorStore.similaritySearch(request) }
            .subscribeOn(Schedulers.boundedElastic())
            .flatMapMany { documents -> Flux.fromIterable(documents.map { toHit(it) }) }
    }

    private fun toHit(document: Document): MessageRecallHit<T> {
        // Document id format: message:<keyType>:<messageId>.
        val messageId = document.id.substringAfterLast(':')
        val metadata = document.metadata
        return MessageRecallHit(
            key = MessageKey.create(
                typeUtil.fromString(messageId),
                typeUtil.fromString(metadata["userId"].toString()),
                typeUtil.fromString(metadata["topicId"].toString()),
            ),
            score = document.score,
        )
    }
}
```

- [ ] **Step 8: Add the recall service bean**

In `chat-service-composite/src/main/kotlin/com/demo/chat/config/service/composite/VectorRecallServiceConfiguration.kt`, add the import:

```kotlin
import com.demo.chat.service.composite.impl.MessageRecallServiceImpl
import com.demo.chat.service.vector.MessageRecallService
```

Add the bean below `messageVectorIndexer`:

```kotlin
    @Bean
    @ConditionalOnProperty(prefix = "app.service.core", name = ["vector", "embedding"])
    fun messageRecallService(
        vectorStore: VectorStore,
        @Value("\${app.key.type}") keyType: String,
    ): MessageRecallService<T> =
        MessageRecallServiceImpl(vectorStore, typeUtil, keyType)
```

- [ ] **Step 9: Run the tests to verify they pass**

Run: `mvn -o -pl chat-core,chat-service-composite test`
Expected: PASS, all tests in both modules.

- [ ] **Step 10: Commit**

```bash
git add chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/impl/MessageRecallServiceImpl.kt chat-service-composite/src/main/kotlin/com/demo/chat/config/service/composite/VectorRecallServiceConfiguration.kt chat-core/src/main/kotlin/com/demo/chat/config/VectorSelectorValidation.kt chat-service-composite/src/test/kotlin/com/demo/chat/test/service/composite/MessageRecallServiceImplTests.kt chat-core/src/test/kotlin/com/demo/chat/test/config/VectorSelectorValidationTests.kt
git commit -m "feat: implement vector recall and selector pair validation"
```

- [ ] **Step 11: Log the milestone**

```bash
fp comment CHAT-mgitmbdo "Task 8 done: MessageRecallServiceImpl with the spec filter strings, threshold mapping, boundedElastic bridge, keys-plus-scores hits. VectorSelectorValidation fails startup on incomplete and illegal pairs, naming both selectors. Commit <short-sha>."
```

---

## Task 9: RSocket recall mappings (CHAT-orgbfaue)

**Files:**
- Create: `chat-service-controller/src/main/kotlin/com/demo/chat/controller/composite/mapping/MessageRecallControllerMapping.kt`
- Modify: `chat-service-controller/src/main/kotlin/com/demo/chat/config/controller/composite/CompositeControllersConfiguration.kt`
- Test: `chat-service-controller/src/test/kotlin/com/demo/chat/test/recall/controller/MessageRecallControllerTests.kt`

**Interfaces:**
- Consumes: `MessageRecallService<T>` (Task 2), the request DTOs and `MessageRecallHit` (shared DTOs — the same classes REST uses, per spec), `RSocketTestBase` (test), `RSocketServerTestConfiguration` (test).
- Produces: routes `message-recall-topic`, `message-recall-user`, `message-recall-global` (flat, no class prefix — the existing message controller has the class prefix `message`, the recall controller must not).

- [ ] **Step 1: Write the failing test**

Create `chat-service-controller/src/test/kotlin/com/demo/chat/test/recall/controller/MessageRecallControllerTests.kt` (pattern: `SecretsControllerTests`).

Defect found during execution. The first draft put this test in
`com.demo.chat.test.rsocket.controller`. `RSocketServerTestConfiguration`
carries a bare `@ComponentScan`, which roots at `com.demo.chat.test.rsocket`.
Every `@Controller` under that package enters every RSocket test context. The
test controller then leaked into `SecretsControllerTests`, and
`TestSecretStoreController` leaked into this test. Two failures followed: a
circular reference on `testSecretStoreController`, because no `SecretsStore`
bean was present. Keep the test outside the scan root. Add a `SecretsStore`
mock, because the scan still supplies the secrets test controller.

```kotlin
package com.demo.chat.test.recall.controller

import com.demo.chat.controller.composite.mapping.MessageRecallControllerMapping
import com.demo.chat.domain.MessageKey
import com.demo.chat.domain.TopicRecallRequest
import com.demo.chat.service.security.SecretsStore
import com.demo.chat.service.vector.MessageRecallHit
import com.demo.chat.service.vector.MessageRecallService
import com.demo.chat.test.anyObject
import com.demo.chat.test.rsocket.RSocketServerTestConfiguration
import com.demo.chat.test.rsocket.RSocketTestBase
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.BDDMockito
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.stereotype.Controller
import org.springframework.test.context.ContextConfiguration
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

@ContextConfiguration(
    classes = [
        TestMessageRecallController::class,
        RSocketServerTestConfiguration::class,
    ]
)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MessageRecallControllerTests : RSocketTestBase("user", "password") {

    @MockBean
    private lateinit var recallService: MessageRecallService<Long>

    @MockBean
    private lateinit var secretsStore: SecretsStore<Long>

    @Test
    fun `recall topic route returns hits over the shared DTOs`() {
        BDDMockito
            .given(recallService.recallInTopic(anyObject()))
            .willReturn(
                Flux.just(
                    MessageRecallHit(MessageKey.create(10L, 20L, 30L), 0.9)
                )
            )

        StepVerifier.create(
            requester
                .route("message-recall-topic")
                .data(Mono.just(TopicRecallRequest(30L, "apple")), TopicRecallRequest::class.java)
                .retrieveFlux(MessageRecallHit::class.java)
        )
            .assertNext { hit ->
                Assertions.assertThat(hit.score).isEqualTo(0.9)
                Assertions.assertThat(hit.key.id).isEqualTo(10L)
                Assertions.assertThat(hit.key.from).isEqualTo(20L)
                Assertions.assertThat(hit.key.dest).isEqualTo(30L)
            }
            .verifyComplete()
    }
}

@Controller
class TestMessageRecallController<T>(private val that: MessageRecallService<T>) :
    MessageRecallControllerMapping<T>, MessageRecallService<T> by that
```

Defect found during execution. The first draft used
`MessageRecallControllerMapping<T> by that`. Kotlin delegation needs a
delegate of the delegated interface. `that` is only a `MessageRecallService`.
Use the two-part form of the repository, as `TestSecretStoreController` does.

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -o -pl chat-core,chat-service-controller test`
Expected: FAIL — `MessageRecallControllerMapping` does not exist yet (compile error).

- [ ] **Step 3: Write the mapping and the controller**

Create `chat-service-controller/src/main/kotlin/com/demo/chat/controller/composite/mapping/MessageRecallControllerMapping.kt`:

```kotlin
package com.demo.chat.controller.composite.mapping

import com.demo.chat.domain.GlobalRecallRequest
import com.demo.chat.domain.TopicRecallRequest
import com.demo.chat.domain.UserRecallRequest
import com.demo.chat.service.vector.MessageRecallHit
import com.demo.chat.service.vector.MessageRecallService
import org.springframework.messaging.handler.annotation.MessageMapping
import reactor.core.publisher.Flux

interface MessageRecallControllerMapping<T> : MessageRecallService<T> {

    @MessageMapping("message-recall-topic")
    override fun recallInTopic(req: TopicRecallRequest<T>): Flux<MessageRecallHit<T>>

    @MessageMapping("message-recall-user")
    override fun recallByUser(req: UserRecallRequest<T>): Flux<MessageRecallHit<T>>

    @MessageMapping("message-recall-global")
    override fun recallGlobal(req: GlobalRecallRequest): Flux<MessageRecallHit<T>>
}
```

Append to `chat-service-controller/src/main/kotlin/com/demo/chat/config/controller/composite/CompositeControllersConfiguration.kt`. Add imports:

```kotlin
import com.demo.chat.controller.composite.mapping.MessageRecallControllerMapping
import com.demo.chat.service.vector.MessageRecallService
```

Add the controller (no class-level `@MessageMapping`; the method routes are the full routes):

```kotlin
@ConditionalOnProperty(prefix = "app.controller", name = ["recall"])
@Controller
class MessageRecallController<T>(
    recallService: MessageRecallService<T>,
) : MessageRecallControllerMapping<T>, MessageRecallService<T> by recallService
```

The two-part delegation form is necessary here too. See the defect note in
Step 1.

A composition that enables `app.controller.recall` must also enable the selector pair, or startup fails on the missing `MessageRecallService` bean — the same failure style as the existing controllers.

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn -o -pl chat-core,chat-service-controller test`
Expected: PASS, including `MessageRecallControllerTests`.

- [ ] **Step 5: Commit**

```bash
git add chat-service-controller/src/main/kotlin/com/demo/chat/controller/composite/mapping/MessageRecallControllerMapping.kt chat-service-controller/src/main/kotlin/com/demo/chat/config/controller/composite/CompositeControllersConfiguration.kt chat-service-controller/src/test/kotlin/com/demo/chat/test/recall/controller/MessageRecallControllerTests.kt
git commit -m "feat: expose message vector recall on RSocket"
```

- [ ] **Step 6: Log the milestone**

```bash
fp comment CHAT-orgbfaue "Task 9 done: RSocket routes message-recall-topic, message-recall-user, message-recall-global. Flat routes, no class prefix. Controller gated on app.controller.recall. Shared DTO round trip verified. Commit <short-sha>."
```

---

## Task 10: REST recall mappings (CHAT-epljcauq)

**Files:**
- Create: `chat-webflux/src/main/kotlin/com/demo/chat/controller/webflux/ChatMessageRecallController.kt`
- Test: `chat-webflux/src/test/kotlin/com/demo/chat/test/controller/webflux/composite/MessageRecallRestTests.kt`

**Interfaces:**
- Consumes: `MessageRecallService<T>` (Task 2), the shared request DTOs, `WebFluxTestConfiguration` (test), `MessageRecallHit` JSON: `key` is serialized through the existing `MessageKey` handling, `score` is a nullable number.
- Produces: `POST /message/recall/topic`, `POST /message/recall/user`, `POST /message/recall/global`, each returning NDJSON of `MessageRecallHit<T>` (a `Flux`, consistent with `listenTopic`).

- [ ] **Step 1: Write the failing test**

Create `chat-webflux/src/test/kotlin/com/demo/chat/test/controller/webflux/composite/MessageRecallRestTests.kt` (pattern: `MessageRestTestBase`, minus RestDocs — the recall endpoints are not contract-documented this sprint). `MessageKey` serializes as a `WRAPPER_OBJECT` named `key`, so the test deserializes each NDJSON line into `MessageRecallHit<Long>` through the app mapper (the `MessageKeyDeserializer` from `WebFluxTestConfiguration`'s imported Jackson modules handles it) instead of asserting raw JSON paths.

Defect found during execution. The first draft used mockito-kotlin `any()`.
The `chat-webflux` module has no mockito-kotlin dependency. Use the
repository helper `com.demo.chat.test.anyObject`, as `MessageRestTestBase`
does.

```kotlin
package com.demo.chat.test.controller.webflux.composite

import com.demo.chat.controller.webflux.ChatMessageRecallController
import com.demo.chat.domain.MessageKey
import com.demo.chat.service.vector.MessageRecallHit
import com.demo.chat.service.vector.MessageRecallService
import com.demo.chat.test.anyObject
import com.demo.chat.test.controller.webflux.config.WebFluxTestConfiguration
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Flux

@WebFluxTest
@ContextConfiguration(
    classes = [WebFluxTestConfiguration::class, ChatMessageRecallController::class]
)
@TestPropertySource(properties = ["app.controller.recall"])
class MessageRecallRestTests {

    @Autowired
    private lateinit var client: WebTestClient

    @Autowired
    private lateinit var mapper: ObjectMapper

    @MockBean
    private lateinit var recallService: MessageRecallService<Long>

    @Test
    fun `recall topic returns NDJSON hits`() {
        BDDMockito
            .given(recallService.recallInTopic(anyObject()))
            .willReturn(
                Flux.just(
                    MessageRecallHit(MessageKey.create(10L, 20L, 30L), 0.9),
                    MessageRecallHit(MessageKey.create(11L, 20L, 30L), 0.5),
                )
            )

        val response = client
            .post()
            .uri("/message/recall/topic")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"type":"TopicRecallRequest","topicId":30,"query":"apple"}""")
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_NDJSON)
            .expectBody(String::class.java)
            .returnResult()

        val lines = response.responseBody!!.trim().lines()
        Assertions.assertThat(lines).hasSize(2)
        val first = mapper.readValue<MessageRecallHit<Long>>(lines[0])
        Assertions.assertThat(first.key.id).isEqualTo(10L)
        Assertions.assertThat(first.key.from).isEqualTo(20L)
        Assertions.assertThat(first.key.dest).isEqualTo(30L)
        Assertions.assertThat(first.score).isEqualTo(0.9)
    }

    @Test
    fun `recall user and global routes exist`() {
        BDDMockito
            .given(recallService.recallByUser(anyObject()))
            .willReturn(Flux.just(MessageRecallHit(MessageKey.create(10L, 20L, 30L), 0.9)))
        BDDMockito
            .given(recallService.recallGlobal(anyObject()))
            .willReturn(Flux.just(MessageRecallHit(MessageKey.create(10L, 20L, 30L), 0.9)))

        client.post().uri("/message/recall/user")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"type":"UserRecallRequest","userId":20,"query":"apple"}""")
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_NDJSON)

        client.post().uri("/message/recall/global")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"type":"GlobalRecallRequest","query":"apple"}""")
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_NDJSON)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -o -pl chat-core,chat-webflux test`
Expected: FAIL — `ChatMessageRecallController` does not exist yet (compile error).

- [ ] **Step 3: Write the controller**

Create `chat-webflux/src/main/kotlin/com/demo/chat/controller/webflux/ChatMessageRecallController.kt`:

```kotlin
package com.demo.chat.controller.webflux

import com.demo.chat.domain.GlobalRecallRequest
import com.demo.chat.domain.TopicRecallRequest
import com.demo.chat.domain.UserRecallRequest
import com.demo.chat.service.vector.MessageRecallHit
import com.demo.chat.service.vector.MessageRecallService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux

@RestController
@RequestMapping("/message/recall")
@ConditionalOnProperty(prefix = "app.controller", name = ["recall"])
class ChatMessageRecallController<T>(
    private val recallService: MessageRecallService<T>,
) {

    @PostMapping("/topic", produces = [MediaType.APPLICATION_NDJSON_VALUE])
    fun recallInTopic(@RequestBody req: TopicRecallRequest<T>): Flux<MessageRecallHit<T>> =
        recallService.recallInTopic(req)

    @PostMapping("/user", produces = [MediaType.APPLICATION_NDJSON_VALUE])
    fun recallByUser(@RequestBody req: UserRecallRequest<T>): Flux<MessageRecallHit<T>> =
        recallService.recallByUser(req)

    @PostMapping("/global", produces = [MediaType.APPLICATION_NDJSON_VALUE])
    fun recallGlobal(@RequestBody req: GlobalRecallRequest): Flux<MessageRecallHit<T>> =
        recallService.recallGlobal(req)
}
```

Request bodies deserialize through the sealed `RequestResponse<T>` type info (`type` property, `@JsonTypeName` values from Task 2). No new deserializers.

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn -o -pl chat-core,chat-webflux test`
Expected: PASS, including `MessageRecallRestTests`.

- [ ] **Step 5: Commit**

```bash
git add chat-webflux/src/main/kotlin/com/demo/chat/controller/webflux/ChatMessageRecallController.kt chat-webflux/src/test/kotlin/com/demo/chat/test/controller/webflux/composite/MessageRecallRestTests.kt
git commit -m "feat: expose message vector recall on REST"
```

- [ ] **Step 6: Log the milestone**

```bash
fp comment CHAT-epljcauq "Task 10 done: POST /message/recall/topic, /user, /global. NDJSON hits, shared DTOs, gated on app.controller.recall. Tests pass. Commit <short-sha>."
```

---

## Task 11: Deploy boot tests (CHAT-uzsskeym)

**Files:**
- Modify: `chat-deploy-memory/pom.xml` (add `chat-vector-simple`)
- Modify: `chat-deploy-redis/pom.xml` (add `chat-vector-redis`)
- Test: `chat-deploy-memory/src/test/kotlin/com/demo/chat/test/deploy/memory/MemoryVectorRecallBootTests.kt`
- Test: `chat-deploy-redis/src/test/kotlin/com/demo/chat/test/deploy/redis/RedisVectorRecallBootTests.kt`

**Interfaces:**
- Consumes: `ChatApp` (chat-deploy, via chat-deploy-memory's `chat-deploy` dependency), the full memory flag set from `MemoryDeploymentTests`, the Redis boot pattern from `RedisDeployBootTests`, `RedisStackContainer` (Task 6 dependency), `app.service.core.vector`, `app.service.core.embedding`, `app.controller.recall`.
- Produces: proof that the composition roots boot with recall active: the `MessageRecallService` bean exists end to end (selector pair, provider module, composite wiring, controller gate).

Nodeid notes: the memory test claims nothing (memory key, memory persistence — the docs/NODEID-CLAIM.md row already says "1, and they claim nothing"). The Redis test uses memory key and memory persistence with only the vector store pointing at Redis, so it also claims nothing. Neither test adds a row to the table.

- [ ] **Step 1: Add the module dependencies**

In `chat-deploy-memory/pom.xml` `<dependencies>`, add (compile scope, B7 note: boot needs the provider at runtime classpath):

```xml
<dependency>
    <groupId>com.demo</groupId>
    <artifactId>chat-vector-simple</artifactId>
    <version>0.0.1</version>
</dependency>
```

In `chat-deploy-redis/pom.xml` `<dependencies>`, add:

```xml
<dependency>
    <groupId>com.demo</groupId>
    <artifactId>chat-vector-redis</artifactId>
    <version>0.0.1</version>
</dependency>
<dependency>
    <groupId>com.redis</groupId>
    <artifactId>testcontainers-redis</artifactId>
    <version>2.2.0</version>
    <scope>test</scope>
</dependency>
```

The deploy modules resolve the other modules from the local repository. Run
`mvn -o -pl chat-core,chat-vector-simple,chat-vector-redis,chat-service-composite,chat-service-controller,chat-webflux install -DskipTests`
before the boot tests. A stale installed artifact hides a new bean, and the
boot test then fails on a missing `messageRecallService`.

- [ ] **Step 2: Write the memory boot test**

Create `chat-deploy-memory/src/test/kotlin/com/demo/chat/test/deploy/memory/MemoryVectorRecallBootTests.kt` (flags mirror `MemoryDeploymentTests`, plus the recall selectors and gate):

```kotlin
package com.demo.chat.test.deploy.memory

import com.demo.chat.ChatApp
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.support.GenericApplicationContext
import org.springframework.test.context.TestPropertySource

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    classes = [ChatApp::class]
)
@TestPropertySource(
    properties = [
        "spring.config.additional-location=classpath:/config/logging.yml,classpath:/config/management-defaults.yml,classpath:/config/userinit.yml",
        "spring.application.name=test-deployment-vector", "app.server.proto=rsocket",
        "server.port=0", "spring.rsocket.server.port=0", "app.key.type=long", "app.nodeid=1",
        "app.service.core.key=memory",
        "app.service.core.pubsub=memory", "app.service.core.index=lucene", "app.service.core.persistence=memory",
        "app.service.core.secrets=memory", "app.service.composite", "app.service.composite.auth",
        "app.service.core.vector=simple", "app.service.core.embedding=mock",
        "app.controller.secrets", "app.controller.key", "app.controller.persistence", "app.controller.index",
        "app.controller.user", "app.controller.message", "app.controller.topic", "app.controller.pubsub",
        "app.controller.recall",
        "app.service.security.userdetails"
    ]
)
class MemoryVectorRecallBootTests {

    @Autowired
    private lateinit var context: GenericApplicationContext

    @Test
    fun recallServiceIsActive() {
        Assertions
            .assertThat(context.containsBean("messageRecallService"))
            .isTrue
    }

    @Test
    fun messageVectorIndexerIsActive() {
        Assertions
            .assertThat(context.containsBean("messageVectorIndexer"))
            .isTrue
    }

    @Test
    fun contextLoads() {
    }
}
```

- [ ] **Step 3: Run the memory boot test**

Run: `mvn -o -pl chat-core,chat-deploy-memory test -Dtest=MemoryVectorRecallBootTests`
Expected: PASS. No container.

Defect found during execution. This is the first context that loads
`MockEmbeddingConfiguration`, and the class was final. The `chat-core` module
does not enable the Kotlin all-open compiler plugin, so Spring rejects the
class. Make the class and the `@Bean` method `open`, like
`VectorSelectorValidationConfiguration`.

- [ ] **Step 4: Write the Redis boot test**

Defects found during execution, all three in this test:

1. The container class is `com.redis.testcontainers.RedisStackContainer`, not
   `org.testcontainers.containers.redis.RedisStackContainer`. Add the test
   dependency `com.redis:testcontainers-redis:2.2.0` to
   `chat-deploy-redis/pom.xml`, as `chat-vector-redis` does. Build the
   container with an explicit image and tag. Do not guess a tag.
2. The module ships no memory messaging provider, so
   `app.service.core.pubsub=memory` fails with no `PubSubServiceBeans` bean.
   Use `app.service.core.pubsub=redis-pubsub`, as `RedisDeployBootTests` does.
3. The redis pubsub reads `redis-topics.host` and `redis-topics.port`. Add
   both to the dynamic properties. They point at the same container.

Create `chat-deploy-redis/src/test/kotlin/com/demo/chat/test/deploy/redis/RedisVectorRecallBootTests.kt` (pattern: `RedisDeployBootTests`; key, persistence, pubsub, index, secrets all memory so no node id claim activates — only the vector store talks to Redis Stack):

```kotlin
package com.demo.chat.test.deploy.redis

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import com.redis.testcontainers.RedisStackContainer

/**
 * Boot verification for recall on the Redis vector backend.
 *
 * Key, persistence, pubsub, index, and secrets all use memory selectors, so
 * no node id claim activates (docs/NODEID-CLAIM.md): a claim needs a redis or
 * cassandra key or persistence selector. Only the vector store talks to the
 * Redis Stack container.
 */
@TestPropertySource(
    properties = [
        "spring.application.name=redis-vector-boot-test",
        "spring.main.web-application-type=reactive",
        "server.port=0",
        "spring.rsocket.server.port=0",
        "app.server.proto=rsocket",
        "app.key.type=long", "app.nodeid=1",
        "app.service.core.key=memory",
        "app.service.core.pubsub=redis-pubsub",
        "app.service.core.index=lucene",
        "app.service.core.persistence=memory",
        "app.service.core.secrets=memory",
        "app.service.composite",
        "app.service.composite.auth",
        "app.service.core.vector=redis",
        "app.service.core.embedding=mock",
        "app.controller.message",
        "app.controller.recall",
        "spring.cloud.consul.enabled=false",
        "spring.cloud.consul.discovery.enabled=false",
        "spring.cloud.consul.config.enabled=false"
    ]
)
@SpringBootTest(classes = [RedisVectorRecallBootTests.BootApp::class], webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class RedisVectorRecallBootTests {

    @Autowired
    lateinit var context: ApplicationContext

    @Test
    fun recallServiceIsActive() {
        Assertions
            .assertThat(context.containsBean("messageRecallService"))
            .isTrue
    }

    @Test
    fun contextLoads() {
    }

    companion object {
        val redisStack = RedisStackContainer(
            RedisStackContainer.DEFAULT_IMAGE_NAME.withTag(RedisStackContainer.DEFAULT_TAG)
        ).apply { start() }

        @JvmStatic
        @DynamicPropertySource
        fun redisProps(registry: DynamicPropertyRegistry) {
            registry.add("spring.redis.host") { redisStack.host }
            registry.add("spring.redis.port") { redisStack.firstMappedPort.toString() }
            registry.add("redis-topics.host") { redisStack.host }
            registry.add("redis-topics.port") { redisStack.firstMappedPort.toString() }
        }
    }

    /**
     * Mirrors ChatApp (com.demo.chat.ChatApp) — test-only, since chat-deploy
     * is not on this module's classpath.
     */
    @SpringBootApplication(proxyBeanMethods = false, scanBasePackages = ["com.demo.chat.config"])
    class BootApp
}
```

- [ ] **Step 5: Run the boot tests**

Run: `mvn -o -pl chat-core,chat-deploy-memory test -Dtest=MemoryVectorRecallBootTests`
Expected: PASS.

Run (needs a Docker daemon; the `-Pintegration` profile un-excludes the `integration` tag):
`mvn -o -Pintegration -pl chat-core,chat-deploy-redis test -Dtest=RedisVectorRecallBootTests`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add chat-core/src/main/kotlin/com/demo/chat/config/MockEmbeddingConfiguration.kt chat-deploy-memory/pom.xml chat-deploy-redis/pom.xml chat-deploy-memory/src/test/kotlin/com/demo/chat/test/deploy/memory/MemoryVectorRecallBootTests.kt chat-deploy-redis/src/test/kotlin/com/demo/chat/test/deploy/redis/RedisVectorRecallBootTests.kt
git commit -m "test: boot the composition roots with vector recall active"
```

- [ ] **Step 7: Log the milestone**

```bash
fp comment CHAT-uzsskeym "Task 11 done: memory boot test (no container) and redis boot test (Redis Stack, memory key and persistence, no node id claim) both boot with the recall service and indexer beans active. Commit <short-sha>."
```

---

## Task 12: Docs, build health, drift (CHAT-pdacflic)

**Files:**
- Modify: `forward-register.md`
- Modify: `docs/BUILD-HEALTH.md` (only if the verifier flags drift)
- Modify: `docs/NODEID-CLAIM.md` (one sentence in the Testing note, pointing at the no-claim reason)

**Interfaces:**
- Consumes: everything from Tasks 1-11.
- Produces: the register entries that keep the docs accurate for future agents; a green build health check in default and integration modes.

- [ ] **Step 1: Add the forward register entries**

Append to `forward-register.md` (follow the file's existing entry style; read it first):

```markdown
## Message vector recall (2026-09-01)

- New modules `chat-vector-simple` and `chat-vector-redis` provide the `VectorStore` bean. They are gated on `app.service.core.vector`. No deploy yml sets the selector yet: the vector and embedding selectors and the `app.controller.recall` flag are test-only until the gateway embedding integration lands.
- Interim capability wiring: `VectorSelectorValidationConfiguration` (chat-core) fails startup when `app.service.core.vector` and `app.service.core.embedding` are set as an incomplete or illegal pair. `@ConditionalOnProperty` stands in for `@ProvidesCapability` until the capability mechanism lands.
- The Redis vector path is Jedis-backed (Spring AI `RedisVectorStore`). The repo data path stays Lettuce. Redis Stack is required for the Redis vector tests.
- Vector tests claim no node id: they activate memory key and persistence. See docs/NODEID-CLAIM.md.
```

- [ ] **Step 2: Note the no-claim fact in the node id doc**

In `docs/NODEID-CLAIM.md`, in the "Testing note" section, after the table, add:

```markdown
The vector recall tests (chat-vector-redis, chat-deploy-redis
`RedisVectorRecallBootTests`) activate no claim store: key and persistence
are memory. Only the vector store touches Redis. They claim nothing and hold
no row in the table above.
```

- [ ] **Step 3: Run build health in default mode**

Run: `./shell-scripts/build-health.sh`
Expected: no drift output. If the script reports failing modules or a stale "Current state" section, update `docs/BUILD-HEALTH.md` to match the verifier output, and re-run until quiet.

- [ ] **Step 4: Run build health in integration mode**

Run (needs a Docker daemon): `./shell-scripts/build-health.sh --integration`
Expected: no drift output. Update `docs/BUILD-HEALTH.md` the same way if it reports drift.

- [ ] **Step 5: Drift check**

Run: `drift refs docs/superpowers/plans/2026-09-01-message-vector-recall.md`
Expected: no stale bindings.

Run: `drift check`
Expected: ok. If stale prose is reported, update the prose first, then relink per AGENTS.md drift discipline. Never relink stale prose.

- [ ] **Step 6: Full default build sanity**

Run: `mvn -o -pl chat-core,chat-vector-simple,chat-vector-redis,chat-service-composite,chat-service-controller,chat-webflux,chat-deploy-memory test`
Expected: PASS across all modules.

- [ ] **Step 7: Commit**

```bash
git add forward-register.md docs/NODEID-CLAIM.md docs/BUILD-HEALTH.md
git commit -m "docs: register the message vector recall structure"
```

- [ ] **Step 8: Close out the FP issues**

```bash
fp comment CHAT-pdacflic "Task 12 done: forward register entries added. Node id doc notes the no-claim fact. Build health quiet in default and integration mode. drift check ok. Commit <short-sha>."
fp issue assign CHAT-pdacflic --rev <task-12-sha>
fp issue update --status done CHAT-pdacflic
```

Assign and close Tasks 1-11 the same way as each lands (each task's final step logs its comment; add `fp issue assign <id> --rev <sha>` and `fp issue update --status done <id>` at each task close):

```bash
fp issue assign CHAT-rbygsuur --rev <sha>
fp issue assign CHAT-davnyulh --rev <sha>
fp issue assign CHAT-qvzhyeds --rev <sha>
fp issue assign CHAT-ilxdjayd --rev <sha>
fp issue assign CHAT-hfytrwyh --rev <sha>
fp issue assign CHAT-sgmwencx --rev <sha>
fp issue assign CHAT-oofefzrx --rev <sha>
fp issue assign CHAT-mgitmbdo --rev <sha>
fp issue assign CHAT-orgbfaue --rev <sha>
fp issue assign CHAT-epljcauq --rev <sha>
fp issue assign CHAT-uzsskeym --rev <sha>
```

---

## Verification Map (spec requirement to task)

| Spec requirement | Task |
|---|---|
| Spring AI BOM 1.0.3 pinned; 2.0.0 banned | Global Constraints, Task 1 |
| API probe compiles (all listed accessors) | Task 1 |
| `VectorStore` abstraction; `SearchRequest` for query, limit, threshold, filters | Tasks 7, 8 |
| `VectorStoreRetriever` line maps to `VectorStore.similaritySearch` | Global Constraints |
| Blocking bridge on `Schedulers.boundedElastic()` | Tasks 7, 8 (+ thread-name tests) |
| `MessageRecallService<T>` in chat-core | Task 2 |
| `MessageVectorIndexer<T>` in chat-core | Task 2 |
| `MessageRecallHit<T>(key: MessageKey<T>, score: Double?)` | Task 2 |
| Keys-and-scores-only recall; no backfill | Task 8, Global Constraints |
| Request DTOs + defaults + bounds | Task 2 (+ request tests) |
| `InvalidRecallRequestException extends ChatException` | Task 2 |
| Doc id `message:<keyType>:<messageId>`; remove by id | Tasks 3, 7 |
| Metadata fields; filter strings | Tasks 3, 8 |
| `record == false` skips the vector write | Task 7 |
| `mock` / `simple` / `redis` vector values; `mock` embedding; `local`, `gateway` reserved, fail startup | Tasks 4, 5, 6, 8 |
| Legal matrix; one-selector-set fails startup naming both; both-unset inactive; no `matchIfMissing` | Task 8 (+ Task 7 wiring tests) |
| Interim `@ConditionalOnProperty` + startup validation bean; `@ProvidesCapability` later | Tasks 7, 8, Task 12 register |
| Module ownership (core / composite / controller / webflux / lucene unchanged) | All tasks; `chat-index-lucene` untouched |
| Send chain order, build-once, vector failure stops pubsub, no compensation | Task 7 |
| `CHAT-ruduojeu` records fanout follow-up (out of scope) | Global Constraints (spec open follow-up unchanged) |
| Threshold 0.0 → `SIMILARITY_THRESHOLD_ACCEPT_ALL`; > 0 → `similarityThreshold` | Task 8 |
| RSocket routes `message-recall-*` | Task 9 |
| REST routes `POST /message/recall/*` | Task 10 |
| Shared DTOs on both surfaces | Tasks 9, 10 |
| Both controller kinds gated `app.controller.recall` | Tasks 9, 10 |
| No new auth; accepted risk recorded | Global Constraints |
| Mock `VectorStore` for service tests; `SimpleVectorStore` local; Redis shared | Tasks 4, 5, 6 |
| Redis Stack required; explicit schema init; index and prefix isolation; `keyType` in filters | Task 6 |
| Do not cross-prove simple vs redis | Tasks 5, 6 (separate tests) |
| Mapper / request / service / startup / boundedElastic / integration test list | Tasks 1-11 |
| Build health default and integration | Task 12 |
