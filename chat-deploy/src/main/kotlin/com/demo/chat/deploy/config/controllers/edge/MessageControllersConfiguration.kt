package com.demo.chat.deploy.config.controllers.edge

import com.demo.chat.ByIdRequest
import com.demo.chat.controller.edge.MessagingController
import com.demo.chat.service.MessageIndexService
import com.demo.chat.service.MessagePersistence
import com.demo.chat.service.PubSubTopicExchangeService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.stereotype.Controller
import java.util.function.Function

open class MessageControllersConfiguration {

    @Controller
    @MessageMapping("message")
    @ConditionalOnProperty(prefix="app.edge", name = ["messaging"])
    class MessageExchangeController<T, V, Q>(
            i: MessageIndexService<T, V, Q>,
            p: MessagePersistence<T, V>,
            x: PubSubTopicExchangeService<T, V>,
            queryFn: Function<ByIdRequest<T>, Q>
    )
        : MessagingController<T, V, Q>(i, p, x, queryFn)
}
