package com.demo.chat.test.messaging

import com.demo.chat.service.impl.memory.messaging.TopicMessagingServiceMemory
import com.demo.chat.test.TestKeyService
import org.junit.jupiter.api.BeforeEach
import reactor.core.publisher.Hooks
import java.util.function.Supplier

class MemoryMessagingServiceTest : MessagingServiceTestBase<String, String>(
        TopicMessagingServiceMemory(),
        TestKeyService(),
        Supplier { "TEST" }) {

    @BeforeEach
    fun setUp() {
        Hooks.onOperatorDebug()
    }
}