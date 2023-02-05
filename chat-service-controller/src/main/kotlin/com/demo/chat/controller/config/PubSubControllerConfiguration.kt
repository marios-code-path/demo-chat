package com.demo.chat.controller.config

import com.demo.chat.controller.core.TopicPubSubServiceController
import com.demo.chat.service.TopicPubSubService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.stereotype.Controller

@Configuration
open class PubSubControllerConfiguration {
    @Controller
    @MessageMapping("pubsub")
    @ConditionalOnProperty(prefix = "app.service.core", name = ["pubsub"])
    class TopicPubSubController<T, V>(that: TopicPubSubService<T, V>) : TopicPubSubServiceController<T, V>(that)
}