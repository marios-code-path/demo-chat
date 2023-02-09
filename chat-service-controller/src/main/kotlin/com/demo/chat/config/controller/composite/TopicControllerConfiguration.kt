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

@ConditionalOnProperty(prefix = "app.service.composite", name = ["topic"])
@Controller
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