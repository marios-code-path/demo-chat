package com.demo.chat.deploy.memory.config

import com.demo.chat.config.IndexServiceBeans
import com.demo.chat.config.PersistenceServiceBeans
import com.demo.chat.controller.config.edge.EdgeUserControllerConfiguration
import com.demo.chat.controller.config.edge.MessagingControllerConfiguration
import com.demo.chat.controller.config.edge.TopicControllerConfiguration
import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.StringUtil
import com.demo.chat.domain.TypeUtil
import com.demo.chat.service.TopicPubSubService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Controller

open class EdgeControllerConfiguration {
    // TODO : Convert for parameter Q (is IndexSearchRequest now )  push references up to App deployment (rather than down in chat-service-controller )
    @Controller
    @ConditionalOnProperty(prefix = "app.service.edge", name = ["user"])
    class UserService<T>(
        s: PersistenceServiceBeans<T, String>,
        x: IndexServiceBeans<T, String, IndexSearchRequest>,
    ) : EdgeUserControllerConfiguration<T, String>(s, x)

    @Controller
    @ConditionalOnProperty(prefix = "app.service.edge", name = ["topic"])
    class TopicService<T>(
        s: PersistenceServiceBeans<T, String>,
        x: IndexServiceBeans<T, String, IndexSearchRequest>,
        p: TopicPubSubService<T, String>,
        t: TypeUtil<T>
    ) : TopicControllerConfiguration<T, String>(s, x, p, t, StringUtil())

    @Controller
    @ConditionalOnProperty(prefix = "app.service.edge", name = ["message"])
    class MessageService<T>(
        s: PersistenceServiceBeans<T, String>,
        x: IndexServiceBeans<T, String, IndexSearchRequest>,
        p: TopicPubSubService<T, String>,
        t: TypeUtil<T>
    ) : MessagingControllerConfiguration<T, String>(s, x, p, t)
}