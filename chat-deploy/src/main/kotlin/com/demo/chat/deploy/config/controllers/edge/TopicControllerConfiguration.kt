package com.demo.chat.deploy.config.controllers.edge

import com.demo.chat.controller.edge.TopicServiceController
import com.demo.chat.deploy.config.core.IndexServiceFactory
import com.demo.chat.deploy.config.core.PersistenceServiceFactory
import com.demo.chat.deploy.config.core.ValueLiterals
import com.demo.chat.service.PubSubTopicExchangeService

open class TopicControllerConfiguration<T, V, Q>(
        persistenceFactory: PersistenceServiceFactory<T, V>,
        indexFactory: IndexServiceFactory<T, V, Q>,
        pubsub: PubSubTopicExchangeService<T, V>,
        valueCodecs: ValueLiterals<V>,
        reqs: RequestToQueryConverter<T, Q>,
) :
        TopicServiceController<T, V, Q>(
                persistenceFactory.topic(),
                indexFactory.topicIndex(),
                pubsub,
                persistenceFactory.user(),
                persistenceFactory.membership(),
                indexFactory.membershipIndex(),
                valueCodecs::emptyValue,
                reqs::topicNameToQuery,
                reqs::membershipIdToQuery
        )