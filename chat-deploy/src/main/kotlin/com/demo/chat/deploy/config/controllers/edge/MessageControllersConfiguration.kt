package com.demo.chat.deploy.config.controllers.edge

import com.demo.chat.controller.edge.MessagingController
import com.demo.chat.deploy.config.core.IndexServiceFactory
import com.demo.chat.deploy.config.core.PersistenceServiceFactory
import com.demo.chat.domain.Message
import com.demo.chat.service.*
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.stereotype.Controller
import java.util.function.Function

open class ExchangeControllerConfig<T, V, Q>(
        indexFactory: IndexServiceFactory<T, V, Q>,
        persistenceFactory: PersistenceServiceFactory<T, V>,
        pubsub: PubSubTopicExchangeService<T, V>,
        reqs: RequestToQueryConverter<T, Q>,
) :
        MessagingController<T, V, Q>(
                indexFactory.messageIndex(),
                persistenceFactory.message(), pubsub, reqs::topicIdToQuery)

