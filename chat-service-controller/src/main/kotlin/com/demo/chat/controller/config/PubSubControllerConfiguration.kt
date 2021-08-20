package com.demo.chat.controller.config

import com.demo.chat.controller.core.PubSubServiceController
import com.demo.chat.service.PubSubService
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.stereotype.Controller

open class PubSubControllerConfiguration {
    @Controller
    @MessageMapping("pubsub")
    class PubSubController<T, V>(that: PubSubService<T, V>) : PubSubServiceController<T, V>(that)
}