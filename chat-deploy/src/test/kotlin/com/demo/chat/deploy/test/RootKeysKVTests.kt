package com.demo.chat.deploy.test

import com.demo.chat.domain.Key
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.assertThat
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.domain.KeyValuePair
import com.demo.chat.domain.TypeUtil
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.service.core.KeyValueStore
import com.demo.chat.service.init.RootKeyService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Import
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Flux
import reactor.core.publisher.Hooks
import reactor.core.publisher.Mono

@ExtendWith(SpringExtension::class)
@Import(RootKeysKVTestsConfig::class)
class RootKeysKVTests {

    @Test
    fun `should translate kv to rootkeys`() {
        Hooks.onOperatorDebug()

        val svc = RootKeyService(TestKVStore(),TypeUtil.LongUtil, "foo")
        val rootKeys = RootKeys<Long>()

        svc.consumeRootKeys(rootKeys)

        assertThat(rootKeys.of(ChatDomain.KEY_VALUE_PAIR).id).isEqualTo(1090429277138866181L)
        assertThat(rootKeys.anon().id).isEqualTo(1090429277138866182L)
    }

    /**
     * A stored map that names an unknown domain is refused. Before
     * `CHAT-avduuqwp` the stale name `KeyDataPair` entered the map silently.
     */
    @Test
    fun `an unknown name in the stored map is refused`() {
        val svc = RootKeyService(TestKVStore(stale = true), TypeUtil.LongUtil, "foo")

        assertThatThrownBy { svc.consumeRootKeys(RootKeys<Long>()) }
            .hasMessageContaining("KeyDataPair")
    }
}

@TestConfiguration
class RootKeysKVTestsConfig {

}

class TestKVStore(private val stale: Boolean = false) : KeyValueStore<String, String> {
    override fun key(): Mono<out Key<String>> = Mono.just(Key.funKey("foo"))

    override fun all(): Flux<out KeyValuePair<String, String>> {
        TODO("Not yet implemented")
    }

    override fun get(key: Key<String>): Mono<out KeyValuePair<String, String>> =Mono.just(
        KeyValuePair.create(
            key,
            (if (stale) "KeyDataPair:\n  id: 7\n  empty: false\n" else "") +
                "User:\n" +
                    "  id: 1090429277138866176\n" +
                    "  empty: false\n" +
                    "Message:\n" +
                    "  id: 1090429277138866177\n" +
                    "  empty: false\n" +
                    "AuthMetadata:\n" +
                    "  id: 1090429277138866180\n" +
                    "  empty: false\n" +
                    "Admin:\n" +
                    "  id: 1090429277138866183\n" +
                    "  empty: false\n" +
                    "MessageTopic:\n" +
                    "  id: 1090429277138866178\n" +
                    "  empty: false\n" +
                    "KeyValuePair:\n" +
                    "  id: 1090429277138866181\n" +
                    "  empty: false\n" +
                    "ConversationEpoch:\n" +
                    "  id: 1090429277138866184\n" +
                    "  empty: false\n" +
                    "FrankingTag:\n" +
                    "  id: 1090429277138866185\n" +
                    "  empty: false\n" +
                    "Anon:\n" +
                    "  id: 1090429277138866182\n" +
                    "  empty: false\n" +
                    "TopicMembership:\n" +
                    "  id: 1090429277138866179\n" +
                    "  empty: false"
        )
    )

    override fun rem(key: Key<String>): Mono<Void> {
        TODO("Not yet implemented")
    }

    override fun add(ent: KeyValuePair<String, String>): Mono<Void> {
        TODO("Not yet implemented")
    }

}