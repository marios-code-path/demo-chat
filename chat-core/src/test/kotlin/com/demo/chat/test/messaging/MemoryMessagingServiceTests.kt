package com.demo.chat.test.messaging

import com.demo.chat.service.impl.memory.messaging.TopicMessagingServiceMemory
import org.junit.jupiter.api.BeforeEach
import reactor.core.publisher.Hooks

class MemoryMessagingServiceTests : MessagingServiceTestBase() {
    @BeforeEach
    fun setUp() {
        Hooks.onOperatorDebug()
        this.topicService = TopicMessagingServiceMemory()
    }
}