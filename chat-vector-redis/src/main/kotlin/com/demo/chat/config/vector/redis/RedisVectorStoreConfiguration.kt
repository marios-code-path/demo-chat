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