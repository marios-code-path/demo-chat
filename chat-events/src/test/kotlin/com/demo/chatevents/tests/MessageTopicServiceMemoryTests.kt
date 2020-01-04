package com.demo.chatevents.tests

import com.demo.chatevents.service.TopicServiceMemory
import org.junit.jupiter.api.BeforeEach
import reactor.core.publisher.Hooks

class MessageTopicServiceMemoryTests : MessageTopicServiceTestBase() {
    @BeforeEach
    fun setUp() {
        Hooks.onOperatorDebug()
        this.topicService = TopicServiceMemory<Any, Any>()
    }
}