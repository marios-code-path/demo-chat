package com.demo.chatevents.tests

import com.demo.chat.service.ChatTopicServiceAdmin
import com.demo.chatevents.TopicServiceMemory
import org.junit.jupiter.api.BeforeEach
import reactor.core.publisher.Hooks

class TopicServiceMemoryTests : TopicServiceTestBase() {


    @BeforeEach
    fun setUp() {
        Hooks.onOperatorDebug()
        this.topicService = TopicServiceMemory()
        topicAdmin = topicService as ChatTopicServiceAdmin
    }


}