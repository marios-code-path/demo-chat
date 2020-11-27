package com.demo.chat.deploy.config.controllers.edge

import com.demo.chat.controller.edge.TopicServiceController
import com.demo.chat.deploy.config.factory.ValueCodecFactory
import com.demo.chat.domain.MessageTopic
import com.demo.chat.domain.TopicMembership
import com.demo.chat.domain.User
import com.demo.chat.service.IndexService
import com.demo.chat.service.PersistenceStore
import com.demo.chat.service.PubSubTopicExchangeService
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.stereotype.Controller
import java.util.function.Supplier

open class TopicControllerConfiguration {

    @Controller
    @MessageMapping("edge.topic")
    class TestTopicController<T, V, Q>(
            topicP: PersistenceStore<T, MessageTopic<T>>,
            topicInd: IndexService<T, MessageTopic<T>, Q>,
            pubsub: PubSubTopicExchangeService<T, V>,
            userP: PersistenceStore<T, User<T>>,
            membershipP: PersistenceStore<T, TopicMembership<T>>,
            membershipInd: IndexService<T, TopicMembership<T>, Q>,
            valueCodecs: ValueCodecFactory<V>,
            reqs: RequestToQueryConverter<T, Q>
    ) :
            TopicServiceController<T, V, Q>(
                    topicP,
                    topicInd,
                    pubsub,
                    userP,
                    membershipP,
                    membershipInd,
                    valueCodecs::emptyValue,
                    reqs::topicNameToQuery,
                    reqs::membershipIdToQuery
            )
}