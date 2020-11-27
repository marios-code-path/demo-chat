package com.demo.chat.deploy.config.controllers.core

import com.demo.chat.controller.core.PubSubServiceController
import com.demo.chat.service.PubSubTopicExchangeService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.stereotype.Controller

open class PubSubControllerConfiguration {
    @Controller
    @MessageMapping("pubsub")
    class PubSubController<T, V>(that: PubSubTopicExchangeService<T, V>) : PubSubServiceController<T, V>(that)
}