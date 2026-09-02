package com.demo.chat.test.vector.redis

import com.demo.chat.config.vector.redis.RedisVectorStoreConfiguration
import com.demo.chat.service.dummy.DummyEmbeddingModel
import com.redis.testcontainers.RedisStackContainer
import java.util.UUID
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.ai.document.Document
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.ai.vectorstore.redis.RedisVectorStore
import org.springframework.ai.vectorstore.redis.RedisVectorStore.MetadataField
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.core.env.MapPropertySource
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