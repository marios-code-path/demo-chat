package com.demo.chat.test.persistence.redis

import com.demo.chat.domain.Key
import com.demo.chat.service.core.PersistenceStore
import com.demo.chat.test.persistence.KeyAwarePersistenceTestBase
import org.junit.jupiter.api.BeforeEach
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import java.util.function.Supplier

/**
 * Test base for Redis persistence stores whose entities DO implement value
 * equality (User, KeyValuePair) — the stock KeyAwarePersistenceTestBase
 * assertions work as-is. Adds a FLUSHDB before every test so the shared
 * container stays isolated.
 */
open class RedisKeyAwarePersistenceTestBase<K, V : Any>(
    private val stringTemplate: ReactiveStringRedisTemplate,
    v: Supplier<V>,
    s: PersistenceStore<K, V>,
    keyFromEntity: (V) -> Key<K>,
) : KeyAwarePersistenceTestBase<K, V>(v, s, keyFromEntity) {

    @BeforeEach
    fun `flush redis`() {
        stringTemplate.delete(stringTemplate.keys("*")).block()
    }
}
