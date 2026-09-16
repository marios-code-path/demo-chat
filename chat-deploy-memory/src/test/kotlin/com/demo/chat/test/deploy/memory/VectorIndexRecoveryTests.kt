package com.demo.chat.test.deploy.memory

import com.demo.chat.ChatApp
import com.demo.chat.domain.ByIdRequest
import com.demo.chat.domain.GlobalRecallRequest
import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
import com.demo.chat.domain.RequestToQueryConverters
import com.demo.chat.service.core.MessageIndexService
import com.demo.chat.service.core.MessagePersistence
import com.demo.chat.service.vector.MessageRecallService
import com.demo.chat.service.vector.MessageReindexService
import com.demo.chat.service.vector.VectorIndexPhase
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.TestPropertySource
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Duration

/**
 * A lost index, reproduced without deleting anything.
 *
 * The test writes messages straight to persistence and never indexes them,
 * which is the same end state as a lost storage directory. Deleting a memory
 * mapped directory inside a running process is not a valid substitute.
 *
 * The test activates memory key and memory persistence, so it claims no node
 * id. See docs/NODEID-CLAIM.md.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    classes = [ChatApp::class]
)
// Each test needs an empty index, an empty store, and no prior job. The beans
// hold that state in memory, so a shared context would let test order decide
// the initial conditions.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestPropertySource(
    properties = [
        "spring.config.additional-location=classpath:/config/logging.yml,classpath:/config/management-defaults.yml,classpath:/config/userinit.yml",
        "spring.application.name=test-deployment-vector-recovery", "app.server.proto=rsocket",
        "server.port=0", "spring.rsocket.server.port=0", "app.key.type=long", "app.nodeid=1",
        "app.service.core.key=memory",
        "app.service.core.pubsub=memory", "app.service.core.index=lucene", "app.service.core.persistence=memory",
        "app.service.core.secrets=memory", "app.service.composite", "app.service.composite.auth",
        "app.service.core.vector=embedded", "app.service.core.embedding=mock",
        "app.controller.secrets", "app.controller.key", "app.controller.persistence", "app.controller.index",
        "app.controller.user", "app.controller.message", "app.controller.topic", "app.controller.pubsub",
        "app.controller.recall",
        "app.service.security.userdetails"
    ]
)
class VectorIndexRecoveryTests {

    @Autowired
    lateinit var persistence: MessagePersistence<Long, String>

    // The recall controller delegates to this service and implements the same
    // interface, so the type alone names two beans in a deployment that sets
    // app.controller.recall. The name picks the service.
    @Autowired
    @Qualifier("recallService")
    lateinit var recall: MessageRecallService<Long>

    @Autowired
    lateinit var reindex: MessageReindexService<Long>

    @Autowired
    lateinit var messageIndex: MessageIndexService<Long, String, IndexSearchRequest>

    @Autowired
    lateinit var queryConverters: RequestToQueryConverters<IndexSearchRequest>

    private fun persistOnly(id: Long, text: String): Mono<Void> =
        persistence.add(Message.create(MessageKey.create(id, 10L, 20L), text, true))

    private fun awaitFinished() {
        Flux.interval(Duration.ZERO, Duration.ofMillis(20))
            .map { reindex.status() }
            .filter { status -> !status.running }
            .next()
            .block(Duration.ofSeconds(30))
    }

    // The memory composition binds Q to IndexSearchRequest. A cassandra
    // composition binds it to a map, so this test stays on this deployment.
    private fun messagesOfTopic(topicId: Long): List<Message<Long, String>> =
        messageIndex
            .findBy(queryConverters.topicIdToQuery(ByIdRequest(topicId)))
            .collectList()
            .flatMapMany { keys -> persistence.byIds(keys) }
            .collectList()
            .block(Duration.ofSeconds(10))!!

    @Test
    fun `recall finds persisted messages after one rebuild`() {
        persistOnly(1L, "apple pie recipe").block()
        persistOnly(2L, "banana bread recipe").block()
        persistOnly(3L, "carrot soup recipe").block()

        val before = recall.recallGlobal(GlobalRecallRequest("recipe", 10, 0.0)).block()!!
        Assertions.assertThat(before.hits).isEmpty()
        Assertions.assertThat(before.indexComplete).isFalse()

        reindex.start().block()
        awaitFinished()

        val after = recall.recallGlobal(GlobalRecallRequest("recipe", 10, 0.0)).block()!!
        Assertions.assertThat(after.hits).hasSize(3)
        Assertions.assertThat(after.indexComplete).isTrue()
    }

    @Test
    fun `a job record is readable from its job topic`() {
        reindex.start().block()
        awaitFinished()

        val jobKey = reindex.status().coveringJob!!
        val records = messagesOfTopic(jobKey.id)

        Assertions.assertThat(records).isNotEmpty
        Assertions.assertThat(records.map { it.key.dest }).containsOnly(jobKey.id)
    }

    // Job messages are stored and indexed, so a scan sees them. They must never
    // reach the recall corpus, or a rebuild would index its own output and the
    // corpus would grow on every run.
    @Test
    fun `a job message never enters recall`() {
        persistOnly(1L, "apple pie recipe").block()

        reindex.start().block()
        awaitFinished()

        reindex.start().block()
        awaitFinished()

        // The second run must succeed, not merely finish. The document id comes
        // from the message id, so the second run meets the id the first one
        // wrote. A store that refused the repeat failed here with a duplicate
        // id, and the run reported one failed message.
        val second = reindex.status()
        Assertions.assertThat(second.lastReport!!.attempted).isEqualTo(1L)
        Assertions.assertThat(second.lastReport!!.indexed).isEqualTo(1L)
        Assertions.assertThat(second.lastReport!!.failed).isEqualTo(0L)
        Assertions.assertThat(second.phase).isEqualTo(VectorIndexPhase.COMPLETE)

        // The threshold accepts every document, so this read returns the whole
        // recall corpus. Only the one user message may appear in it.
        val hits = recall.recallGlobal(GlobalRecallRequest("rebuild", 50, 0.0)).block()!!
        Assertions.assertThat(hits.hits.map { it.key.id }).containsExactly(1L)
        Assertions.assertThat(hits.indexComplete).isTrue()
    }
}
