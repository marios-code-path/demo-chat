package com.demo.chat.test.vector.redis

import com.demo.chat.config.vector.redis.RedisVectorStoreConfiguration
import com.demo.chat.domain.EmbeddingIdentity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * The index name and the key prefix carry the key type and the identity.
 *
 * A metadata field does not isolate a redis index, which is why the key type
 * is already in the name. The identity joins it for the same reason.
 */
class RedisVectorNamesTests {

    @Test
    fun `the index name carries the key type and the identity`() {
        assertThat(
            RedisVectorStoreConfiguration.indexNameFor("long", EmbeddingIdentity("acme-e5"))
        ).isEqualTo("chat:vector:long:acme-e5:message")
    }

    @Test
    fun `the prefix carries the key type and the identity`() {
        assertThat(
            RedisVectorStoreConfiguration.prefixFor("long", EmbeddingIdentity("acme-e5"))
        ).isEqualTo("chat:vector:long:acme-e5:message:")
    }

    @Test
    fun `the prefix is the index name with one separator`() {
        val index = RedisVectorStoreConfiguration.indexNameFor("uuid", EmbeddingIdentity("m2"))
        val prefix = RedisVectorStoreConfiguration.prefixFor("uuid", EmbeddingIdentity("m2"))

        assertThat(prefix).isEqualTo("$index:")
    }

    @Test
    fun `two identities give two index names`() {
        assertThat(RedisVectorStoreConfiguration.indexNameFor("long", EmbeddingIdentity("one")))
            .isNotEqualTo(RedisVectorStoreConfiguration.indexNameFor("long", EmbeddingIdentity("two")))
    }

    @Test
    fun `two key types give two index names`() {
        assertThat(RedisVectorStoreConfiguration.indexNameFor("long", EmbeddingIdentity("one")))
            .isNotEqualTo(RedisVectorStoreConfiguration.indexNameFor("uuid", EmbeddingIdentity("one")))
    }
}
