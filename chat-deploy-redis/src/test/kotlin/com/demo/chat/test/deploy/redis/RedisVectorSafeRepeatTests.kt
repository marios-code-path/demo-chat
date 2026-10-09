package com.demo.chat.test.deploy.redis

import com.demo.chat.domain.command.AcceptedCommand
import com.demo.chat.service.command.DomainCommandHandler
import com.demo.chat.service.composite.command.handler.MessageVectorHandler
import com.demo.chat.service.vector.MessageVectorIndexer
import com.demo.chat.test.command.CompositionSafeRepeatBase
import org.junit.jupiter.api.Tag
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource

/**
 * The `V` safe-repeat contract over vector `redis`. The setup of
 * `RedisVectorRecallBootTests`, with its Redis Stack container. It adds the
 * initial users, because a command names the `Admin` identity as its owner.
 */
@TestPropertySource(
    properties = [
        "spring.application.name=redis-vector-safe-repeat",
        "spring.config.additional-location=classpath:/config/userinit.yml",
        "spring.main.web-application-type=reactive",
        "server.port=0",
        "spring.rsocket.server.port=0",
        "app.server.proto=rsocket",
        "app.key.type=long", "app.nodeid=1",
        "app.users.create=true",
        "app.service.core.key=memory",
        "app.service.core.pubsub=redis-pubsub",
        "app.service.core.index=lucene",
        "app.service.core.persistence=memory",
        "app.service.core.secrets=memory",
        "app.service.composite", "app.command.bus=memory",
        "app.service.composite.auth=true",
        "app.service.core.vector=redis",
        "app.service.core.embedding=mock",
        "app.controller.message",
        "app.controller.recall",
        "app.service.security.userdetails",
        "spring.cloud.consul.enabled=false",
        "spring.cloud.consul.discovery.enabled=false",
        "spring.cloud.consul.config.enabled=false",
    ]
)
@SpringBootTest(classes = [RedisVectorRecallBootTests.BootApp::class], webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext
@Tag("integration")
class RedisVectorSafeRepeatTests : CompositionSafeRepeatBase() {
    @Autowired
    lateinit var applicationContext: ApplicationContext

    override fun context(): ApplicationContext = applicationContext

    override val roomName = "saferepeatredisvector"

    @Suppress("UNCHECKED_CAST")
    private val indexer: MessageVectorIndexer<Long>
        get() = context().getBean(MessageVectorIndexer::class.java) as MessageVectorIndexer<Long>

    private val vectorStore: VectorStore
        get() = context().getBean(VectorStore::class.java)

    override fun handler(): DomainCommandHandler<Long, String> = MessageVectorHandler(indexer)

    override fun results(command: AcceptedCommand<Long, String>): Int =
        vectorStore.similaritySearch(SearchRequest.builder().query(command.message.data).topK(50).build())
            .count { it.id == "message:long:${command.message.key.id}" }

    companion object {
        /** The container of the source fixture. No second container starts. */
        @JvmStatic
        @DynamicPropertySource
        fun redisProps(registry: DynamicPropertyRegistry) {
            val redisStack = RedisVectorRecallBootTests.redisStack
            registry.add("spring.redis.host") { redisStack.host }
            registry.add("spring.redis.port") { redisStack.firstMappedPort.toString() }
            registry.add("redis-topics.host") { redisStack.host }
            registry.add("redis-topics.port") { redisStack.firstMappedPort.toString() }
        }
    }
}
