package com.demo.chat.test.persistence

import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.persistence.memory.impl.RootKeyStoreInMemory
import com.demo.chat.service.core.IKeyGenerator
import com.demo.chat.service.core.RootKeyLoader
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.util.concurrent.atomic.AtomicLong

/** Loading and creating the root keys. See `CHAT-avduuqwp` and `CHAT-bafkgkko`. */
class RootKeyLoaderTests {

    @Test
    fun `an empty store gets one root per domain`() {
        val roots = RootKeyLoader(RootKeyStoreInMemory<Long>(), counter()).load().block()!!

        assertThat(roots.keys).containsExactlyInAnyOrderElementsOf(ChatDomain.entries)
        assertThat(roots.values.toSet()).hasSize(ChatDomain.entries.size)
    }

    @Test
    fun `a restart reads the stored roots and creates none`() {
        val store = RootKeyStoreInMemory<Long>()
        val first = RootKeyLoader(store, counter()).load().block()!!
        val second = RootKeyLoader(store, counter(start = 1000)).load().block()!!

        assertThat(second).isEqualTo(first)
    }

    @Test
    fun `two loaders at once agree on one root per domain`() {
        val store = RootKeyStoreInMemory<Long>()
        val both = Mono.zip(
            RootKeyLoader(store, counter(0)).load().subscribeOn(Schedulers.parallel()),
            RootKeyLoader(store, counter(5000)).load().subscribeOn(Schedulers.parallel()),
        ).block()!!

        assertThat(both.t1).isEqualTo(both.t2)
    }

    private fun counter(start: Long = 0): IKeyGenerator<Long> =
        AtomicLong(start).let { n -> object : IKeyGenerator<Long> { override fun nextId() = n.incrementAndGet() } }
}
