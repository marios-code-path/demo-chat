package com.demo.chat.controller.config

import com.demo.chat.config.IndexServiceBeans
import com.demo.chat.controller.core.IndexServiceController
import com.demo.chat.controller.core.mapping.IndexServiceMapping
import com.demo.chat.domain.*
import com.demo.chat.service.IndexService
import com.demo.chat.service.security.AuthMetaIndex
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.stereotype.Controller

open class IndexControllersConfiguration {
    @Controller
    @MessageMapping("index.user")
    @ConditionalOnProperty(prefix = "app.service.core.api", name = ["index"])
    class UserIndexController<T, V, Q:IndexSearchRequest>(s: IndexServiceBeans<T, V, Q>) :
        IndexServiceController<T, User<T>, Q>(s.userIndex())

    @Controller
    @MessageMapping("index.message")
    @ConditionalOnProperty(prefix = "app.service.core.api", name = ["index"])
    class MessageIndexController<T, V, Q:IndexSearchRequest>(s: IndexServiceBeans<T, V, Q>) :
        IndexServiceController<T, Message<T, V>, Q>(s.messageIndex())

    @Controller
    @MessageMapping("index.topic")
    @ConditionalOnProperty(prefix = "app.service.core.api", name = ["index"])
    class TopicIndexController<T, V, Q:IndexSearchRequest>(s: IndexServiceBeans<T, V, Q>) :
        IndexServiceController<T, MessageTopic<T>, Q>(s.topicIndex())

    @Controller
    @MessageMapping("index.authmetadata")
    @ConditionalOnProperty(prefix = "app.service.core.api", name = ["index"])
    class AuthMetaIndexController<T, V, Q:IndexSearchRequest>(s: IndexServiceBeans<T, V, Q>) :
        IndexServiceController<T, AuthMetadata<T>, Q>(s.authMetadataIndex())
}