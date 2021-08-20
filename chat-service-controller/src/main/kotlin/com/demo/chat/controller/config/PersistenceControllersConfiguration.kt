package com.demo.chat.controller.config

import com.demo.chat.config.PersistenceServiceBeans
import com.demo.chat.controller.core.PersistenceServiceController
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageTopic
import com.demo.chat.domain.TopicMembership
import com.demo.chat.domain.User
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.stereotype.Controller


open class PersistenceControllersConfiguration {

    @Controller
    @MessageMapping("persist.user")
    class UserPersistenceController<T, V>(s: PersistenceServiceBeans<T, V>) :
        PersistenceServiceController<T, User<T>>(s.user())

    @Controller
    @MessageMapping("persist.message")
    class MessagePersistenceController<T, V>(s: PersistenceServiceBeans<T, V>) :
        PersistenceServiceController<T, Message<T, V>>(s.message())

    @Controller
    @MessageMapping("persist.topic")
    class TopicPersistenceController<T, V>(s: PersistenceServiceBeans<T, V>) :
        PersistenceServiceController<T, MessageTopic<T>>(s.topic())

    @Controller
    @MessageMapping("persist.membership")
    class MembershipPersistenceController<T, V>(s: PersistenceServiceBeans<T, V>) :
        PersistenceServiceController<T, TopicMembership<T>>(s.membership())
}