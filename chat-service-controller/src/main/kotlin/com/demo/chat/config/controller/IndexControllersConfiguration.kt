package com.demo.chat.config.controller

import com.demo.chat.config.IndexServiceBeans
import com.demo.chat.config.controller.rbac.IndexServiceSecurity
import com.demo.chat.controller.core.IndexServiceController
import com.demo.chat.domain.*
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.stereotype.Controller

@Configuration
class IndexControllersConfiguration {
    @Controller
    @MessageMapping("index.user")
    @ConditionalOnProperty(prefix = "app.controller", name = ["index"])
    class UserIndexController<T, V, Q:IndexSearchRequest>(s: IndexServiceBeans<T, V, Q>) :
        IndexServiceSecurity<T, User<T>, Q>, IndexServiceController<T, User<T>, Q>(s.userIndex())

    @Controller
    @MessageMapping("index.message")
    @ConditionalOnProperty(prefix = "app.controller", name = ["index"])
    class MessageIndexController<T, V, Q:IndexSearchRequest>(s: IndexServiceBeans<T, V, Q>) :
        IndexServiceSecurity<T, Message<T, V>, Q>, IndexServiceController<T, Message<T, V>, Q>(s.messageIndex())

    @Controller
    @MessageMapping("index.topic")
    @ConditionalOnProperty(prefix = "app.controller", name = ["index"])
    class TopicIndexController<T, V, Q:IndexSearchRequest>(s: IndexServiceBeans<T, V, Q>) :
        IndexServiceSecurity<T, MessageTopic<T>, Q>, IndexServiceController<T, MessageTopic<T>, Q>(s.topicIndex())

    @Controller
    @MessageMapping("index.authmetadata")
    @ConditionalOnProperty(prefix = "app.controller", name = ["index"])
    class AuthMetaIndexController<T, V, Q:IndexSearchRequest>(s: IndexServiceBeans<T, V, Q>) :
        IndexServiceSecurity<T, AuthMetadata<T>, Q>, IndexServiceController<T, AuthMetadata<T>, Q>(s.authMetadataIndex())
}