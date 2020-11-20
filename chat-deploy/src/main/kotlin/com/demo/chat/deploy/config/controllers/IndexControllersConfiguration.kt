package com.demo.chat.deploy.config.controllers

import com.demo.chat.controller.service.IndexServiceController
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageTopic
import com.demo.chat.domain.User
import com.demo.chat.service.IndexService
import com.demo.chat.service.impl.memory.index.QueryCommand
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.stereotype.Controller

open class IndexControllersConfiguration {
    @Controller
    @ConditionalOnProperty(prefix = "app.service", name = ["index", "index.user"])
    @MessageMapping("user")
    class UserIndexController<T, V>(s: IndexService<T, User<T>, QueryCommand>) : IndexServiceController<T, User<T>, QueryCommand>(s)

    @Controller
    @ConditionalOnProperty(prefix = "app.service", name = ["index", "index.message"])
    @MessageMapping("message")
    class MessageIndexController<T, V>(s: IndexService<T, Message<T, V>, QueryCommand>) : IndexServiceController<T, Message<T, V>, QueryCommand>(s)

    @Controller
    @ConditionalOnProperty(prefix = "app.service", name = ["index", "index.topic"])
    @MessageMapping("topic")
    class TopicIndexController<T, V>(s: IndexService<T, MessageTopic<T>, QueryCommand>) : IndexServiceController<T, MessageTopic<T>, QueryCommand>(s)
}