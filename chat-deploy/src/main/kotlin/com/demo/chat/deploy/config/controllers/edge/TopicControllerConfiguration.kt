package com.demo.chat.deploy.config.controllers.edge

import com.demo.chat.controller.edge.TopicServiceController
import com.demo.chat.deploy.config.core.IndexServiceFactory
import com.demo.chat.deploy.config.core.PersistenceServiceFactory
import com.demo.chat.deploy.config.core.ValueCodecFactory
import com.demo.chat.domain.MessageTopic
import com.demo.chat.domain.TopicMembership
import com.demo.chat.domain.User
import com.demo.chat.service.IndexService
import com.demo.chat.service.PersistenceStore
import com.demo.chat.service.PubSubTopicExchangeService
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.stereotype.Controller

open class TopicControllerConfiguration {

   // @Controller
   // @MessageMapping("edge.topic")
    class TopicController<T, V, Q>(
            persistenceFactory: PersistenceServiceFactory<T, Q>,
            indexFactory: IndexServiceFactory<T, V, Q>,
            pubsub: PubSubTopicExchangeService<T, V>,
            valueCodecs: ValueCodecFactory<V>,
            reqs: RequestToQueryConverter<T, Q>
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
}