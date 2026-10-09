package com.demo.chat.test.deploy.memory

import com.demo.chat.ChatApp
import com.demo.chat.domain.command.AcceptedCommand
import com.demo.chat.service.command.DomainCommandHandler
import com.demo.chat.service.composite.command.handler.MessageVectorHandler
import com.demo.chat.service.vector.MessageVectorIndexer
import com.demo.chat.test.command.CompositionSafeRepeatBase
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.TestPropertySource

/**
 * The `V` safe-repeat contract over vector `simple`. The setup of
 * `MemoryVectorRecallBootTests`, with `app.users.create=true`, because a
 * command names the `Admin` identity as its owner.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = [ChatApp::class])
@TestPropertySource(
    properties = [
        "spring.config.additional-location=classpath:/config/logging.yml,classpath:/config/management-defaults.yml,classpath:/config/userinit.yml",
        "spring.application.name=test-deployment-safe-repeat-simple-vector", "app.server.proto=rsocket",
        "server.port=0", "spring.rsocket.server.port=0", "app.key.type=long", "app.nodeid=1",
        "app.service.core.key=memory",
        "app.service.core.pubsub=memory", "app.service.core.index=lucene", "app.service.core.persistence=memory",
        "app.service.core.secrets=memory", "app.service.composite", "app.command.bus=memory", "app.service.composite.auth=true",
        "app.service.core.vector=simple", "app.service.core.embedding=mock",
        "app.controller.secrets", "app.controller.key", "app.controller.persistence", "app.controller.index",
        "app.controller.user", "app.controller.message", "app.controller.topic", "app.controller.pubsub",
        "app.controller.recall",
        "app.service.security.userdetails", "app.users.create=true",
    ]
)
@DirtiesContext
class SimpleVectorSafeRepeatTests : CompositionSafeRepeatBase() {
    @Autowired
    lateinit var applicationContext: ApplicationContext

    override fun context(): ApplicationContext = applicationContext

    override val roomName = "saferepeatsimplevector"

    @Suppress("UNCHECKED_CAST")
    private val indexer: MessageVectorIndexer<Long>
        get() = context().getBean(MessageVectorIndexer::class.java) as MessageVectorIndexer<Long>

    private val vectorStore: VectorStore
        get() = context().getBean(VectorStore::class.java)

    override fun handler(): DomainCommandHandler<Long, String> = MessageVectorHandler(indexer)

    override fun results(command: AcceptedCommand<Long, String>): Int =
        vectorStore.similaritySearch(SearchRequest.builder().query(command.message.data).topK(50).build())
            .count { it.id == "message:long:${command.message.key.id}" }
}
