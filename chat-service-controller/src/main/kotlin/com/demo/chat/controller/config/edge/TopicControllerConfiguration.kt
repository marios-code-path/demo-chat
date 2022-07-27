package com.demo.chat.controller.config.edge

import com.demo.chat.config.IndexServiceBeans
import com.demo.chat.config.PersistenceServiceBeans
import com.demo.chat.controller.edge.TopicServiceController
import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.TypeUtil
import com.demo.chat.service.MembershipIndexService
import com.demo.chat.service.TopicIndexService
import com.demo.chat.service.TopicPubSubService
import org.springframework.messaging.handler.annotation.MessageMapping

@MessageMapping("topic")
open class TopicControllerConfiguration<T, V>(
    s: PersistenceServiceBeans<T, V>,
    x: IndexServiceBeans<T, V, IndexSearchRequest>,
    p: TopicPubSubService<T, V>,
    t: TypeUtil<T>,
    v: TypeUtil<V>
) : TopicServiceController<T, V, IndexSearchRequest>(
    topicPersistence = s.topicPersistence(),
    topicIndex = x.topicIndex(),
    messaging = p,
    userPersistence = s.userPersistence(),
    membershipPersistence = s.membershipPersistence(),
    membershipIndex = x.membershipIndex(),
    emptyDataCodec = { v.fromString("") },
    topicNameToQuery = { r -> IndexSearchRequest(TopicIndexService.NAME, r.name, 100) },
    membershipIdToQuery = { mid -> IndexSearchRequest(MembershipIndexService.MEMBER, t.toString(mid.id), 100) }
)