package com.demo.chat.test.messaging

import com.demo.chat.domain.Key
import com.demo.chat.service.IKeyService
import com.demo.chat.service.impl.memory.messaging.TopicMessagingServiceMemory
import com.demo.chat.test.randomAlphaNumeric
import org.junit.jupiter.api.BeforeEach
import reactor.core.publisher.Hooks
import reactor.core.publisher.Mono
import java.util.function.Supplier

class TestKeyService : IKeyService<String> {
    override fun <S> key(kind: Class<S>): Mono<out Key<String>> =
            Mono.just(Key.funKey(randomAlphaNumeric(10)))

    override fun rem(key: Key<String>): Mono<Void> = Mono.empty()
    override fun exists(key: Key<String>): Mono<Boolean> = Mono.just(true)
}

class MemoryMessagingServiceTest : MessagingServiceTestBase<String, String>(
        TopicMessagingServiceMemory(),
        TestKeyService(),
        Supplier { "TEST" }) {

    @BeforeEach
    fun setUp() {
        Hooks.onOperatorDebug()
    }
}