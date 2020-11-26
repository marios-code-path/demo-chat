package com.demo.chat.deploy.config.controllers.core

import com.demo.chat.controller.core.IndexServiceController
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageTopic
import com.demo.chat.domain.User
import com.demo.chat.service.IndexService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.stereotype.Controller

open class IndexControllersConfiguration {
    @Controller
    @ConditionalOnProperty(prefix = "app.service", name = ["index", "index.user"])
    @MessageMapping("user")
    class UserIndexController<T, Q>(s: IndexService<T, User<T>, Q>) : IndexServiceController<T, User<T>, Q>(s)

    @Controller
    @ConditionalOnProperty(prefix = "app.service", name = ["index", "index.message"])
    @MessageMapping("message")
    class MessageIndexController<T, V, Q>(s: IndexService<T, Message<T, V>, Q>) : IndexServiceController<T, Message<T, V>, Q>(s)

    @Controller
    @ConditionalOnProperty(prefix = "app.service", name = ["index", "index.topic"])
    @MessageMapping("topic")
    class TopicIndexController<T, Q>(s: IndexService<T, MessageTopic<T>, Q>) : IndexServiceController<T, MessageTopic<T>, Q>(s)
}