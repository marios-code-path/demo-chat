package com.demo.chat.test.deploy.memory

import com.demo.chat.ChatApp
import com.integrallis.vectors.spring.ai.JavaVectorsVectorStore
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.ai.document.Document
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.support.GenericApplicationContext
import org.springframework.test.context.TestPropertySource

/**
 * Boots the memory composition root with the embedded vector provider.
 *
 * This is the first place the narrow Vector API flag reach is tested. The
 * incubator flag lives in the chat-vector-embedded module. The store loads
 * the Vector API inside this module's test JVM, so this module carries the
 * flag in its own surefire argLine.
 *
 * The storage path is unset on purpose. The provider then uses a temporary
 * directory, which matches the rebuild on failure decision.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    classes = [ChatApp::class]
)
@TestPropertySource(
    properties = [
        "spring.config.additional-location=classpath:/config/logging.yml,classpath:/config/management-defaults.yml,classpath:/config/userinit.yml",
        "spring.application.name=test-deployment-vector-embedded", "app.server.proto=rsocket",
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
class MemoryEmbeddedVectorRecallBootTests {

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
    fun theVectorStoreIsTheEmbeddedProvider() {
        Assertions
            .assertThat(context.getBean(VectorStore::class.java))
            .isInstanceOf(JavaVectorsVectorStore::class.java)
    }

    /**
     * Exercises the distance kernels. A bean type check alone never touches
     * them, so it cannot show that the store works inside this module.
     */
    @Test
    fun theEmbeddedStoreAddsAndRecalls() {
        val store = context.getBean(VectorStore::class.java)
        store.add(
            listOf(
                Document.builder().id("a").text("apple banana").build(),
                Document.builder().id("b").text("zebra stripe").build(),
            )
        )

        val hits = store.similaritySearch(
            SearchRequest.builder().query("apple banana").topK(2).build()
        )

        Assertions.assertThat(hits).isNotNull
        Assertions.assertThat(hits!!).isNotEmpty
        Assertions.assertThat(hits.first().id).isEqualTo("a")
    }

    @Test
    fun contextLoads() {
    }
}
