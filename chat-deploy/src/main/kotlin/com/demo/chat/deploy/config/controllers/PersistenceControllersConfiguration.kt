package com.demo.chat.deploy.config.controllers

import com.demo.chat.controller.service.PersistenceServiceController
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageTopic
import com.demo.chat.domain.TopicMembership
import com.demo.chat.domain.User
import com.demo.chat.service.MembershipPersistence
import com.demo.chat.service.MessagePersistence
import com.demo.chat.service.TopicPersistence
import com.demo.chat.service.UserPersistence
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.stereotype.Controller


open class PersistenceControllersConfiguration {

    @Controller
    @ConditionalOnProperty(prefix = "app.service", name = ["persistence"])
    @MessageMapping("user")
    class UserPersistenceController<T>(t: UserPersistence<T>) : PersistenceServiceController<T, User<T>>(t)

    @Controller
    @ConditionalOnProperty(prefix = "app.service", name = ["persistence"])
    @MessageMapping("message")
    class MessagePersistenceController<T, E>(t: MessagePersistence<T, E>) : PersistenceServiceController<T, Message<T, E>>(t)

    @Controller
    @ConditionalOnProperty(prefix = "app.service", name = ["persistence"])
    @MessageMapping("topic")
    class TopicPersistenceController<T>(t: TopicPersistence<T>) : PersistenceServiceController<T, MessageTopic<T>>(t)

    @Controller
    @ConditionalOnProperty(prefix = "app.service", name = ["persistence"])
    @MessageMapping("membership")
    class MembershipPersistenceController<T>(t: MembershipPersistence<T>) : PersistenceServiceController<T, TopicMembership<T>>(t)
}