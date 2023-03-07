package com.demo.chat.config.deploy.memory

import com.demo.chat.config.IndexServiceBeans
import com.demo.chat.config.PersistenceServiceBeans
import com.demo.chat.config.controller.composite.CompositeServiceBeansDefinition
import com.demo.chat.domain.*
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.secure.ChatUserDetails
import com.demo.chat.secure.access.AccessBroker
import com.demo.chat.service.composite.CompositeServiceBeans
import com.demo.chat.config.controller.composite.CompositeServiceAccessBeansConfiguration
import com.demo.chat.service.core.*
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.core.context.ReactiveSecurityContextHolder

@Configuration
@ConditionalOnProperty("app.service.composite")
class CompositeServiceConfiguration {

    @Bean
    fun <T> compositeServiceBeans(
        persistenceServiceBeans: PersistenceServiceBeans<T, String>,
        indexServiceBeans: IndexServiceBeans<T, String, IndexSearchRequest>,
        topicPubSubService: TopicPubSubService<T, String>,
        typeUtil: TypeUtil<T>,
    ): CompositeServiceBeans<T, String> =
        CompositeServiceBeansDefinition(
            persistenceBeans = persistenceServiceBeans,
            indexBeans = indexServiceBeans,
            pubsub = topicPubSubService,
            typeUtil = typeUtil,
            emptyMessageSupplier = { "" },
            topicIdQueryFunction = { req -> IndexSearchRequest(MessageIndexService.TOPIC, typeUtil.toString(req.id), 100) },
            topicNameQueryFunction = { req -> IndexSearchRequest(TopicIndexService.NAME, req.name, 100) },
            membershipOfIdQueryFunction = { req -> IndexSearchRequest(MembershipIndexService.MEMBEROF, typeUtil.toString(req.id), 100) },
            membershipRequestQueryFunction = { req ->
                IndexSearchRequest(
                    MembershipIndexService.MEMBER,
                    "${typeUtil.toString(req.uid)} AND ${MembershipIndexService.MEMBEROF}:${typeUtil.toString(req.roomId)}",
                    100
                )
            },
            handleQueryFunction = { req -> IndexSearchRequest(UserIndexService.HANDLE, req.name, 100) }
        )

    @Bean
    @ConditionalOnProperty("app.service.composite.security")
    fun <T> springSecurityCompositeServiceAccessBeans(
        accessBroker: AccessBroker<T>,
        rootKeys: RootKeys<T>,
        compositeServiceBeansDefinition: CompositeServiceBeansDefinition<T, String, IndexSearchRequest>
    ): CompositeServiceBeans<T, String> = CompositeServiceAccessBeansConfiguration(
        accessBroker = accessBroker,
        principalKeyPublisher = { ReactiveSecurityContextHolder.getContext()
            .map { it.authentication.principal as ChatUserDetails<T> }
            .map { it.user.key } },
        rootKeys = rootKeys,
        compositeServiceBeansDefinition = compositeServiceBeansDefinition
    )
}