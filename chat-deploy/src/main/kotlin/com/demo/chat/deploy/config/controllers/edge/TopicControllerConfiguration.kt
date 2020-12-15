package com.demo.chat.deploy.config.controllers.edge

import com.demo.chat.controller.edge.TopicServiceController
import com.demo.chat.deploy.config.codec.RequestToQueryConverters
import com.demo.chat.deploy.config.core.IndexServiceConfiguration
import com.demo.chat.deploy.config.core.PersistenceServiceConfiguration
import com.demo.chat.deploy.config.codec.ValueLiterals
import com.demo.chat.service.PubSubTopicExchangeService

open class TopicControllerConfiguration<T, V, Q>(
        persistenceFactory: PersistenceServiceConfiguration<T, V>,
        indexFactory: IndexServiceConfiguration<T, V, Q>,
        pubsub: PubSubTopicExchangeService<T, V>,
        valueCodecs: ValueLiterals<V>,
        reqs: RequestToQueryConverters<T, Q>,
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