package com.demo.chat.test.persistence.redis

import com.demo.chat.domain.Key
import com.demo.chat.service.core.PersistenceStore
import com.demo.chat.test.persistence.PersistenceTestBase
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import reactor.test.StepVerifier
import java.util.function.Supplier

/**
 * Test base for Redis persistence stores whose entities do NOT implement
 * value equality (MessageTopic, Message, TopicMembership, AuthMetadata).
 *
 * Extends [PersistenceTestBase] directly (instead of
 * KeyAwarePersistenceTestBase) because its `add one find one` test asserts
 * full object equality and is final. Here `add one find one` is re-implemented
 * with a field-by-field [assertRoundTrip] assertion, and `add one find by ids`
 * is added back.
 *
 * A FLUSHDB runs before every test so the shared container stays isolated.
 */
open class RedisPersistenceTestBase<K, V>(
    private val stringTemplate: ReactiveStringRedisTemplate,
    v: Supplier<V>,
    s: PersistenceStore<K, V>,
    val keyFromEntity: (V) -> Key<K>,
    private val assertRoundTrip: (original: V, roundTripped: V) -> Unit,
) : PersistenceTestBase<K, V>(v, s) {

    @BeforeEach
    fun `flush redis`() {
        stringTemplate.delete(stringTemplate.keys("*")).block()
    }

    @Test
    fun `add one find one`() {
        val thing = valCodec.get()

        StepVerifier
            .create(
                store.key()
                    .flatMap { store.add(thing) }
                    .then(store.get(keyFromEntity(thing)))
            )
            .assertNext {
                assertRoundTrip(thing, it)
            }
            .verifyComplete()
    }

    @Test
    fun `add one find by ids`() {
        val byIds = store.add(valCodec.get())
            .thenMany(store.all())
            .collectList()
            .flatMapMany { list ->
                val ids = list.map { keyFromEntity(it) }
                store.byIds(ids)
            }

        StepVerifier
            .create(byIds)
            .expectNextCount(1)
            .verifyComplete()
    }
}