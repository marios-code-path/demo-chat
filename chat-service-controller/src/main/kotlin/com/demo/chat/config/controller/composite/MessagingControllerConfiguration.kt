package com.demo.chat.config.controller.composite

import com.demo.chat.config.IndexServiceBeans
import com.demo.chat.config.PersistenceServiceBeans
import com.demo.chat.controller.composite.MessagingController
import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.TypeUtil
import com.demo.chat.service.core.MessageIndexService
import com.demo.chat.service.core.TopicPubSubService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.stereotype.Controller

@ConditionalOnProperty(prefix = "app.controller", name = ["message"])
@Controller
@MessageMapping("message")
class MessagingControllerConfiguration<T, V>(
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