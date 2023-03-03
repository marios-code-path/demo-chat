package com.demo.chat.config.controller.composite

import com.demo.chat.controller.composite.mapping.MessageControllerMapping
import com.demo.chat.controller.composite.mapping.TopicServiceControllerMapping
import com.demo.chat.controller.composite.mapping.UserServiceControllerMapping
import com.demo.chat.service.composite.ChatMessageService
import com.demo.chat.service.composite.ChatTopicService
import com.demo.chat.service.composite.ChatUserService
import com.demo.chat.service.composite.CompositeServiceBeans
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.stereotype.Controller

class CompositeControllersConfiguration {

    @ConditionalOnProperty(prefix = "app.controller", name = ["message"])
    @Controller
    @MessageMapping("message")
    class MessagingServiceController<T, V>(b: CompositeServiceBeans<T, V>) :
        MessageControllerMapping<T, V>, ChatMessageService<T, V> by b.messageService()

    @ConditionalOnProperty(prefix = "app.controller", name = ["topic"])
    @Controller
    @MessageMapping("topic")
    class TopicServiceController<T, V>(b: CompositeServiceBeans<T, V>) :
        TopicServiceControllerMapping<T, V>, ChatTopicService<T, V> by b.topicService()

    @ConditionalOnProperty(prefix = "app.controller", name = ["user"])
    @Controller
    @MessageMapping("user")
    class UserServiceController<T, V>(b: CompositeServiceBeans<T, V>) :
        UserServiceControllerMapping<T>, ChatUserService<T> by b.userService()
}