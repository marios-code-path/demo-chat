package com.demo.chat.controller.config

import com.demo.chat.config.IndexServiceBeans
import com.demo.chat.controller.core.IndexServiceController
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageTopic
import com.demo.chat.domain.User
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.stereotype.Controller

open class IndexControllersConfiguration {
    @Controller
    @MessageMapping("index.user")
    class UserIndexController<T, V, Q>(s: IndexServiceBeans<T, V, Q>) : IndexServiceController<T, User<T>, Q>(s.userIndex())

    @Controller
    @MessageMapping("index.message")
    class MessageIndexController<T, V, Q>(s: IndexServiceBeans<T, V, Q>) : IndexServiceController<T, Message<T, V>, Q>(s.messageIndex())

    @Controller
    @MessageMapping("index.topic")
    class TopicIndexController<T, V, Q>(s: IndexServiceBeans<T, V, Q>) : IndexServiceController<T, MessageTopic<T>, Q>(s.topicIndex())
}