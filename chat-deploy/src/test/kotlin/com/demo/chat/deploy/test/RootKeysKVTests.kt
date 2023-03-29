package com.demo.chat.deploy.test

import com.demo.chat.domain.Key
import com.demo.chat.domain.KeyDataPair
import com.demo.chat.domain.TypeUtil
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.service.core.KeyValueStore
import com.demo.chat.service.init.RootKeyService
import com.demo.chat.test.YamlFileContextInitializer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Import
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
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

        println(RootKeyService.rootKeySummary(rootKeys))
    }
}

@TestConfiguration
class RootKeysKVTestsConfig {

}

class TestKVStore : KeyValueStore<String, String> {
    override fun key(): Mono<out Key<String>> = Mono.just(Key.funKey("foo"))

    override fun all(): Flux<out KeyDataPair<String, String>> {
        TODO("Not yet implemented")
    }

    override fun get(key: Key<String>): Mono<out KeyDataPair<String, String>> =Mono.just(
        KeyDataPair.create(
            key,
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
                    "KeyDataPair:\n" +
                    "  id: 1090429277138866181\n" +
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

    override fun add(ent: KeyDataPair<String, String>): Mono<Void> {
        TODO("Not yet implemented")
    }

}