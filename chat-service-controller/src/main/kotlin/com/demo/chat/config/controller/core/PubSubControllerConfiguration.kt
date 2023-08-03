package com.demo.chat.config.controller.core

import com.demo.chat.config.PubSubServiceBeans
import com.demo.chat.controller.core.TopicPubSubServiceController
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.stereotype.Controller


@Controller
@MessageMapping("pubsub")
@ConditionalOnProperty(prefix = "app.controller", name = ["pubsub"])
class TopicPubSubController<T, V>(pubsubBeans: PubSubServiceBeans<T, V>) :
    TopicPubSubServiceController<T, V>(pubsubBeans.pubSubService())