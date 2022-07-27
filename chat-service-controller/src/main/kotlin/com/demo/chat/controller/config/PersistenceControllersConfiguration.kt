package com.demo.chat.controller.config

import com.demo.chat.config.PersistenceServiceBeans
import com.demo.chat.controller.core.PersistenceServiceController
import com.demo.chat.domain.*
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.stereotype.Controller


open class PersistenceControllersConfiguration {

    @Controller
    @MessageMapping("persist.user")
    class UserPersistenceController<T, V>(s: PersistenceServiceBeans<T, V>) :
        PersistenceServiceController<T, User<T>>(s.userPersistence())

    @Controller
    @MessageMapping("persist.message")
    class MessagePersistenceController<T, V>(s: PersistenceServiceBeans<T, V>) :
        PersistenceServiceController<T, Message<T, V>>(s.messagePersistence())

    @Controller
    @MessageMapping("persist.topic")
    class TopicPersistenceController<T, V>(s: PersistenceServiceBeans<T, V>) :
        PersistenceServiceController<T, MessageTopic<T>>(s.topicPersistence())

    @Controller
    @MessageMapping("persist.membership")
    class MembershipPersistenceController<T, V>(s: PersistenceServiceBeans<T, V>) :
        PersistenceServiceController<T, TopicMembership<T>>(s.membershipPersistence())

    @Controller
    @MessageMapping("persist.authmetadata")
    class AuthMetaPersistenceController<T, V>(s: PersistenceServiceBeans<T, V>) :
        PersistenceServiceController<T, AuthMetadata<T>>(s.authMetaPersistence())
}