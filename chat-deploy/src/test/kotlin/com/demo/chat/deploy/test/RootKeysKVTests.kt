package com.demo.chat.deploy.test

import com.demo.chat.domain.ChatException
import com.demo.chat.domain.Key
import com.demo.chat.domain.TypeUtil
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.service.core.InitializingKVStore
import com.demo.chat.service.init.RootKeyService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.concurrent.ConcurrentHashMap

/**
 * A root key snapshot published to a string keyed store, and read back by a
 * process without store access. See `CHAT-avduuqwp`.
 */
class RootKeysKVTests {

    @Test
    fun `a published snapshot loads into a second process`() {
        val store = MapKVStore()
        RootKeyService(store, TypeUtil.LongUtil, "rootkeys", "long").publishRootKeys(source())

        val consumer = RootKeys<Long>()
        RootKeyService(store, TypeUtil.LongUtil, "rootkeys", "long").consumeRootKeys(consumer)

        assertThat(consumer.domains()).isEqualTo(source().domains())
        assertThat(consumer.anon()).isEqualTo(Key.funKey(901L))
    }

    @Test
    fun `a consumer of another key type refuses the snapshot`() {
        val store = MapKVStore()
        RootKeyService(store, TypeUtil.LongUtil, "rootkeys", "long").publishRootKeys(source())

        assertThatThrownBy { RootKeyService(store, TypeUtil.LongUtil, "rootkeys", "uuid").consumeRootKeys(RootKeys()) }
            .isInstanceOf(ChatException::class.java)
            .hasMessageContaining("uuid")
    }

    @Test
    fun `a missing snapshot fails the consumer`() {
        assertThatThrownBy { RootKeyService(MapKVStore(), TypeUtil.LongUtil, "rootkeys", "long").consumeRootKeys(RootKeys()) }
            .isInstanceOf(ChatException::class.java)
            .hasMessageContaining("rootkeys")
    }

    private fun source(): RootKeys<Long> = RootKeys<Long>().apply {
        loadDomains(ChatDomain.entries.associateWith { Key.funKey(100L + it.ordinal) })
        loadIdentities(Key.funKey(900L), Key.funKey(901L))
    }
}

class MapKVStore : InitializingKVStore {
    private val values = ConcurrentHashMap<String, String>()
    override fun read(name: String): Mono<String> = Mono.justOrEmpty(values[name])
    override fun write(name: String, value: String): Mono<Void> = Mono.fromRunnable { values[name] = value }
    override fun remove(name: String): Mono<Void> = Mono.fromRunnable { values.remove(name) }
    override fun names(): Flux<String> = Flux.fromIterable(values.keys)
}
