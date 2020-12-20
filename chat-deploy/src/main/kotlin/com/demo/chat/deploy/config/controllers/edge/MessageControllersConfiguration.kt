package com.demo.chat.deploy.config.controllers.edge

import com.demo.chat.controller.edge.MessagingController
import com.demo.chat.deploy.config.codec.RequestToQueryConverters
import com.demo.chat.deploy.config.core.IndexServiceConfiguration
import com.demo.chat.deploy.config.core.PersistenceServiceConfiguration
import com.demo.chat.service.*

open class ExchangeControllerConfig<T, V, Q>(
        indexFactory: IndexServiceConfiguration<T, V, Q>,
        persistenceFactory: PersistenceServiceConfiguration<T, V>,
        pubsub: PubSubService<T, V>,
        reqs: RequestToQueryConverters<T, Q>,
) :
        MessagingController<T, V, Q>(
                indexFactory.messageIndex(),
                persistenceFactory.message(),
                pubsub,
                reqs::topicIdToQuery)

