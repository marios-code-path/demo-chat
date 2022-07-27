package com.demo.chat.deploy.memory.config

import com.demo.chat.config.IndexServiceBeans
import com.demo.chat.config.PersistenceServiceBeans
import com.demo.chat.controller.edge.MessagingController
import com.demo.chat.controller.edge.TopicServiceController
import com.demo.chat.controller.edge.UserServiceController
import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.TypeUtil
import com.demo.chat.service.*
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.stereotype.Controller
import java.util.function.Function

open class EdgeControllerConfiguration {

    @Controller
    @ConditionalOnProperty(prefix = "app.service.edge", name = ["user"])
    @MessageMapping("edge.user")
    class UserService<T>(
        s: PersistenceServiceBeans<T, String>,
        x: IndexServiceBeans<T, String, IndexSearchRequest>,
    ) :
        UserServiceController<T, IndexSearchRequest>(
            userPersistence = s.user(),
            userIndex = x.userIndex(),
            userHandleToQuery = Function { r ->
                IndexSearchRequest(UserIndexService.HANDLE, r.handle, 100)
            })

    @Controller
    @ConditionalOnProperty(prefix = "app.service.edge", name = ["topic"])
    @MessageMapping("edge.topic")
    class TopicService<T>(
        s: PersistenceServiceBeans<T, String>,
        x: IndexServiceBeans<T, String, IndexSearchRequest>,
        p: TopicPubSubService<T, String>,
        t: TypeUtil<T>
    ) : TopicServiceController<T, String, IndexSearchRequest>(
        topicPersistence = s.topic(),
        topicIndex = x.topicIndex(),
        messaging = p,
        userPersistence = s.user(),
        membershipPersistence = s.membership(),
        membershipIndex = x.membershipIndex(),
        emptyDataCodec = { "" },
        topicNameToQuery = { r -> IndexSearchRequest(TopicIndexService.NAME, r.name, 100) },
        membershipIdToQuery = { mid -> IndexSearchRequest(MembershipIndexService.MEMBER, t.toString(mid.id), 100) }
    )

    // MessageService
    @Controller
    @ConditionalOnProperty(prefix = "app.service.edge", name = ["message"])
    @MessageMapping("edge.message")
    class MessageService<T>(
        s: PersistenceServiceBeans<T, String>,
        x: IndexServiceBeans<T, String, IndexSearchRequest>,
        p: TopicPubSubService<T, String>,
        t: TypeUtil<T>
    ) : MessagingController<T, String, IndexSearchRequest>(
        messageIndex = x.messageIndex(),
        messagePersistence = s.message(),
        topicMessaging = p,
        messageIdToQuery = { r -> IndexSearchRequest(MessageIndexService.ID, t.toString(r.id), 100) }
    )

}