package com.demo.chat.deploy.config.controllers.core

import com.demo.chat.controller.core.IndexServiceController
import com.demo.chat.deploy.config.core.IndexServiceFactory
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageTopic
import com.demo.chat.domain.User
import com.demo.chat.service.IndexService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.stereotype.Controller

open class IndexControllersConfiguration {
    @Controller
    @MessageMapping("index.user")
    class UserIndexController<T, V, Q>(s: IndexServiceFactory<T, V, Q>) : IndexServiceController<T, User<T>, Q>(s.userIndex())

    @Controller
    @MessageMapping("index.message")
    class MessageIndexController<T, V, Q>(s: IndexServiceFactory<T, V, Q>) : IndexServiceController<T, Message<T, V>, Q>(s.messageIndex())

    @Controller
    @MessageMapping("index.topic")
    class TopicIndexController<T, V, Q>(s: IndexServiceFactory<T, V, Q>) : IndexServiceController<T, MessageTopic<T>, Q>(s.topicIndex())
}