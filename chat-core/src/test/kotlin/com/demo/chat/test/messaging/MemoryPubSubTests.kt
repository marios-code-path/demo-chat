package com.demo.chat.test.messaging

import com.demo.chat.service.impl.memory.messaging.MemoryPubSubTopicExchange
import com.demo.chat.test.TestStringKeyService
import org.junit.jupiter.api.BeforeEach
import reactor.core.publisher.Hooks
import java.util.function.Supplier

class MemoryPubSubTests : PubSubTests<String, String>(
        MemoryPubSubTopicExchange(),
        TestStringKeyService(),
        Supplier { "TEST" }) {

    @BeforeEach
    fun setUp() {
        Hooks.onOperatorDebug()
    }
}