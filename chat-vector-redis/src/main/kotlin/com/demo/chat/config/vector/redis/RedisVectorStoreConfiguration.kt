package com.demo.chat.config.vector.redis

import com.demo.chat.domain.EmbeddingIdentity
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.ai.vectorstore.redis.RedisVectorStore
import org.springframework.ai.vectorstore.redis.RedisVectorStore.MetadataField
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import redis.clients.jedis.RedisClient

/**
 * Shared runtime vector store. Jedis-backed, Redis Stack required.
 *
 * **Two Redis clients run in one deployment, and that is the design.** This
 * module uses Jedis, because the Spring AI vector store API requires it. The
 * persistence path uses Lettuce through ReactiveRedisConnectionFactory,
 * because it serves reactive operations. The two share the Redis endpoint and
 * nothing else. A shared client would couple unrelated modules, and a Jedis
 * call is blocking, so it must not enter the reactive path.
 *
 * **Spring AI 2.0 changed the client type, not the design.** The builder took
 * JedisPooled under Spring AI 1.0.3. It takes redis.clients.jedis.RedisClient
 * now, read from the compiled
 * RedisVectorStore.builder(RedisClient, EmbeddingModel) on 2026-09-18. Jedis 7
 * supplies that type, and RedisClient.create(host, port) builds one. So the
 * repair follows the API rather than choosing a client. See CHAT-qkkxvpsb.
 *
 * Isolation is per key type and per embedding identity. The index is
 * chat:vector:<keyType>:<identity>:message and the key prefix is
 * chat:vector:<keyType>:<identity>:message:. A metadata field alone does not
 * isolate Redis indexes, so the index name carries both values, and the recall
 * filters carry keyType as well.
 *
 * An index that an earlier build wrote orphans. Its name has no identity
 * segment, and no read reaches it again. The corpus is a derived cache, so a
 * rebuild replaces it.
 */
@Configuration
@ConditionalOnProperty(prefix = "app.service.core", name = ["vector"], havingValue = "redis")
class RedisVectorStoreConfiguration {

    @Bean
    fun redisVectorStore(
        embeddingModel: EmbeddingModel,
        environment: Environment,
        identity: EmbeddingIdentity,
        @Value("\${app.key.type}") keyType: String,
    ): VectorStore {
        val host = environment.getProperty("spring.redis.host", "localhost")
        val port = environment.getProperty("spring.redis.port", "6379").toInt()

        // The vector client is private to this module and to this bean. It is
        // never a Spring bean, so nothing else can reach it. Only the endpoint
        // properties are shared with the persistence path.
        val jedis = RedisClient.create(host, port)

        return RedisVectorStore.builder(jedis, embeddingModel)
            .indexName(indexNameFor(keyType, identity))
            .prefix(prefixFor(keyType, identity))
            .metadataFields(
                MetadataField.tag("kind"),
                MetadataField.tag("keyType"),
                MetadataField.tag("topicId"),
                MetadataField.tag("userId"),
            )
            .initializeSchema(true)
            .build()
    }

    companion object {
        fun indexNameFor(keyType: String, identity: EmbeddingIdentity): String =
            "chat:vector:$keyType:${identity.value}:message"

        fun prefixFor(keyType: String, identity: EmbeddingIdentity): String =
            "${indexNameFor(keyType, identity)}:"
    }
}