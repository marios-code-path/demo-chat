package com.demo.chat.config.controller.core

import com.demo.chat.config.PersistenceServiceBeans
import com.demo.chat.controller.core.KeyValueStoreController
import com.demo.chat.controller.core.PersistenceServiceController
import com.demo.chat.domain.*
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.stereotype.Controller

@Configuration
open class PersistenceControllersConfiguration {

    @Controller
    @MessageMapping("persist.user")
    @ConditionalOnProperty(prefix = "app.controller", name = ["persistence"])
    class UserPersistenceController<T, V>(s: PersistenceServiceBeans<T, V>) :
        PersistenceServiceController<T, User<T>>(s.userPersistence())

    @Controller
    @MessageMapping("persist.message")
    @ConditionalOnProperty(prefix = "app.controller", name = ["persistence"])
    class MessagePersistenceController<T, V>(s: PersistenceServiceBeans<T, V>) :
        PersistenceServiceController<T, Message<T, V>>(s.messagePersistence())

    @Controller
    @MessageMapping("persist.topic")
    @ConditionalOnProperty(prefix = "app.controller", name = ["persistence"])
    class TopicPersistenceController<T, V>(s: PersistenceServiceBeans<T, V>) :
        PersistenceServiceController<T, MessageTopic<T>>(s.topicPersistence())

    @Controller
    @MessageMapping("persist.membership")
    @ConditionalOnProperty(prefix = "app.controller", name = ["persistence"])
    class MembershipPersistenceController<T, V>(s: PersistenceServiceBeans<T, V>) :
        PersistenceServiceController<T, TopicMembership<T>>(s.membershipPersistence())

    @Controller
    @MessageMapping("persist.authmetadata")
    @ConditionalOnProperty(prefix = "app.controller", name = ["persistence"])
    class AuthMetaPersistenceController<T, V>(s: PersistenceServiceBeans<T, V>) :
        PersistenceServiceController<T, AuthMetadata<T>>(s.authMetaPersistence())

    @Controller
    @MessageMapping("persist.keyvalue")
    @ConditionalOnProperty(prefix = "app.controller", name = ["persistence"])
    class KeyValuePersistenceController<T, V>(s: PersistenceServiceBeans<T, V>) :
        KeyValueStoreController<T>(s.keyValuePersistence())
}