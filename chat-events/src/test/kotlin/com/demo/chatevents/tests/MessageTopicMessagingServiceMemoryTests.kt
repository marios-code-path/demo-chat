package com.demo.chatevents.tests

import com.demo.chatevents.service.TopicMessagingServiceMemory
import org.junit.jupiter.api.BeforeEach
import reactor.core.publisher.Hooks
import java.util.*

class MessageTopicMessagingServiceMemoryTests : MessageTopicMessagingServiceTestBase() {
    @BeforeEach
    fun setUp() {
        Hooks.onOperatorDebug()
        this.topicService = TopicMessagingServiceMemory <UUID, String>()
    }
}