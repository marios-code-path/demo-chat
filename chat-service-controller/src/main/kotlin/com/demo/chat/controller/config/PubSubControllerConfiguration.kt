package com.demo.chat.controller.config

import com.demo.chat.controller.core.TopicPubSubServiceController
import com.demo.chat.service.TopicPubSubService
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.stereotype.Controller

open class PubSubControllerConfiguration {
    @Controller
    @MessageMapping("pubsub")
    class TopicPubSubController<T, V>(that: TopicPubSubService<T, V>) : TopicPubSubServiceController<T, V>(that)
}