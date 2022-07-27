package com.demo.chat.controller.config.edge

import com.demo.chat.config.IndexServiceBeans
import com.demo.chat.config.PersistenceServiceBeans
import com.demo.chat.controller.edge.MessagingController
import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.TypeUtil
import com.demo.chat.service.MessageIndexService
import com.demo.chat.service.TopicPubSubService
import org.springframework.messaging.handler.annotation.MessageMapping

@MessageMapping("message")
open class MessagingControllerConfiguration<T, V>(
    s: PersistenceServiceBeans<T, V>,
    x: IndexServiceBeans<T, V, IndexSearchRequest>,
    p: TopicPubSubService<T, V>,
    t: TypeUtil<T>
) : MessagingController<T, V, IndexSearchRequest>(
    messageIndex = x.messageIndex(),
    messagePersistence = s.messagePersistence(),
    topicMessaging = p,
    messageIdToQuery = { r -> IndexSearchRequest(MessageIndexService.ID, t.toString(r.id), 100) }
)