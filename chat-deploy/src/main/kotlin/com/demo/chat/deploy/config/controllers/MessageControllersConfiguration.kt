package com.demo.chat.deploy.config.controllers

import com.demo.chat.controller.edge.MessagingController
import com.demo.chat.service.MessageIndexService
import com.demo.chat.service.MessagePersistence
import com.demo.chat.service.PubSubTopicExchangeService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.stereotype.Controller

open class MessageControllersConfiguration {

    @Controller
    @MessageMapping("message")
    @ConditionalOnProperty(prefix="app.service", name = ["message"])
    class MessageExchangeController<T, V>(
            i: MessageIndexService<T, V>,
            p: MessagePersistence<T, V>,
            x: PubSubTopicExchangeService<T, V>,
    )
        : MessagingController<T, V>(i, p, x)
}
