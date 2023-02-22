package com.demo.chat.config.controller.composite

import com.demo.chat.config.IndexServiceBeans
import com.demo.chat.config.PersistenceServiceBeans
import com.demo.chat.controller.composite.TopicServiceController
import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.TypeUtil
import com.demo.chat.service.core.MembershipIndexService
import com.demo.chat.service.core.TopicIndexService
import com.demo.chat.service.core.TopicPubSubService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.stereotype.Controller

@ConditionalOnProperty(prefix = "app.controller", name = ["topic"])
@Controller
@MessageMapping("topic")
open class TopicControllerConfiguration<T>(
    s: PersistenceServiceBeans<T, String>,
    x: IndexServiceBeans<T, String, IndexSearchRequest>,
    p: TopicPubSubService<T, String>,
    t: TypeUtil<T>,
) : TopicServiceController<T, String, IndexSearchRequest>(
    topicPersistence = s.topicPersistence(),
    topicIndex = x.topicIndex(),
    pubsub = p,
    userPersistence = s.userPersistence(),
    membershipPersistence = s.membershipPersistence(),
    membershipIndex = x.membershipIndex(),
    emptyDataCodec = { "" },
    topicNameToQuery = { r -> IndexSearchRequest(TopicIndexService.NAME, r.name, 100) },
    memberOfIdToQuery = { req -> IndexSearchRequest(MembershipIndexService.MEMBEROF, t.toString(req.id), 100) },
    memberWithTopicToQuery = { req -> IndexSearchRequest(MembershipIndexService.MEMBER, t.toString(req.uid), 100) },
)