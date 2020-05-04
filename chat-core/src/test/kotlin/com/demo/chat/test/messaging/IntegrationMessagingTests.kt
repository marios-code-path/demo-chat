package com.demo.chat.test.messaging

import com.demo.chat.service.messaging.TopicMessagingServiceMemory
import org.junit.jupiter.api.BeforeEach
import reactor.core.publisher.Hooks

class IntegrationMessagingTests : MessagingServiceTestBase() {
    @BeforeEach
    fun setUp() {
        Hooks.onOperatorDebug()
        this.topicService = TopicMessagingServiceMemory()
    }
}