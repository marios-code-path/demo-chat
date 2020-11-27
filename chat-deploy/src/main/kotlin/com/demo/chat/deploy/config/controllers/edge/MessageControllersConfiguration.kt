package com.demo.chat.deploy.config.controllers.edge

import com.demo.chat.controller.edge.MessagingController
import com.demo.chat.domain.Message
import com.demo.chat.service.*
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.stereotype.Controller
import java.util.function.Function

open class MessageControllersConfiguration {

    @Controller
    @MessageMapping("edge.message")
    class MessageExchangeController<T, V, Q>(
            i: IndexService<T, Message<T, V>, Q>,
            p: PersistenceStore<T, Message<T, V>>,
            x: PubSubTopicExchangeService<T, V>,
            reqs: RequestToQueryConverter<T, Q>
    )
        : MessagingController<T, V, Q>(i, p, x, reqs::topicIdToQuery)
}
