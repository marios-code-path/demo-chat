package com.demo.chat.deploy.config.controllers.edge

import com.demo.chat.ByIdRequest
import com.demo.chat.ByNameRequest
import com.demo.chat.controller.edge.TopicServiceController
import com.demo.chat.service.*
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.stereotype.Controller
import java.util.function.Supplier

open class TopicControllerConfiguration {

    @ConditionalOnProperty(prefix = "app.edge", name = ["topic"])
    @Controller
    @MessageMapping("topic")
    class TestTopicController<T, V, Q>(
            topicP: TopicPersistence<T>,
            topicInd: TopicIndexService<T, Q>,
            pubsub: PubSubTopicExchangeService<T, V>,
            userP: UserPersistence<T>,
            membershipP: MembershipPersistence<T>,
            membershipInd: MembershipIndexService<T, Q>,
            emptyDataCodec: Supplier<V>,
            topicNameToQuery: java.util.function.Function<ByNameRequest, Q>,
            membershipIdToQuery: java.util.function.Function<ByIdRequest<T>, Q>,
    ) :
            TopicServiceController<T, V, Q>(
                    topicP,
                    topicInd,
                    pubsub,
                    userP,
                    membershipP,
                    membershipInd,
                    emptyDataCodec,
                    topicNameToQuery,
                    membershipIdToQuery
            )
}