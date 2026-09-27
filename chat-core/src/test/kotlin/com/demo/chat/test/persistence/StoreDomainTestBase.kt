package com.demo.chat.test.persistence

import com.demo.chat.domain.Key
import com.demo.chat.domain.KeyVerificationException
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.service.core.IKeyService
import com.demo.chat.service.core.PersistenceStore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import reactor.test.StepVerifier

/**
 * Every typed store refuses a key of another domain. See `CHAT-avduuqwp`, T5.
 *
 * [keys] mints under [rootKeys], and [store] reads the same roots. A backend
 * subclasses this once for each store, with [entityOf] building an entity for
 * a key and [keyOf] naming the key that the store reads.
 */
@Disabled
abstract class StoreDomainTestBase<T, E : Any>(
    val store: PersistenceStore<T, E>,
    val keys: IKeyService<T>,
    val rootKeys: RootKeys<T>,
    val domain: ChatDomain,
    val entityOf: (Key<T>) -> E,
    val keyOf: (E) -> Key<T>,
) {
    /** A domain that is not [domain]. */
    private fun otherDomain(): ChatDomain =
        if (domain == ChatDomain.MESSAGE_TOPIC) ChatDomain.USER else ChatDomain.MESSAGE_TOPIC

    @Test
    fun `add refuses a key of another domain`() {
        val foreign = keys.key(otherDomain()).block()!!

        StepVerifier.create(store.add(entityOf(foreign))).verifyError(KeyVerificationException::class.java)
        StepVerifier.create(store.get(keyOf(entityOf(foreign)))).verifyComplete()
    }

    @Test
    fun `a read key carries the root of the store domain`() {
        val entity = entityOf(keys.key(domain).block()!!)
        store.add(entity).block()

        assertThat(keyOf(store.get(keyOf(entity)).block()!!).root).isEqualTo(rootKeys.of(domain).id)
    }
}
