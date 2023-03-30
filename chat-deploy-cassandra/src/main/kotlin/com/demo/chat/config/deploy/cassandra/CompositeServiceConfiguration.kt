package com.demo.chat.config.deploy.cassandra

import com.demo.chat.config.IndexServiceBeans
import com.demo.chat.config.PersistenceServiceBeans
import com.demo.chat.config.controller.composite.CompositeServiceBeansDefinition
import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.TypeUtil
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
        indexServiceBeans: IndexServiceBeans<T, String, Map<String, String>>,
        topicPubSubService: TopicPubSubService<T, String>,
        typeUtil: TypeUtil<T>,
    ): CompositeServiceBeans<T, String> =
        CompositeServiceBeansDefinition(
            persistenceBeans = persistenceServiceBeans,
            indexBeans = indexServiceBeans,
            pubsub = topicPubSubService,
            typeUtil = typeUtil,
            emptyMessageSupplier = { "" },
            topicIdQueryFunction = { req -> mapOf(Pair(MessageIndexService.TOPIC, typeUtil.toString(req.id))) },
            topicNameQueryFunction = { req -> mapOf(Pair(TopicIndexService.NAME, req.name)) },
            membershipOfIdQueryFunction = { req ->
                mapOf(
                    Pair(
                        MembershipIndexService.MEMBEROF,
                        typeUtil.toString(req.id)
                    )
                )
            },
            membershipRequestQueryFunction = { req ->
                mapOf(
                    Pair(MembershipIndexService.MEMBER, "${typeUtil.toString(req.uid)}"),
                    Pair(MembershipIndexService.MEMBEROF, "${typeUtil.toString(req.roomId)}")
                )
            },
            handleQueryFunction = { req -> mapOf(Pair(UserIndexService.HANDLE, req.name)) }
        )

    @Bean
    @ConditionalOnProperty("app.service.composite.security")
    fun <T> springSecurityCompositeServiceAccessBeans(
        accessBroker: AccessBroker<T>,
        rootKeys: RootKeys<T>,
        compositeServiceBeansDefinition: CompositeServiceBeansDefinition<T, String, IndexSearchRequest>
    ): CompositeServiceBeans<T, String> = CompositeServiceAccessBeansConfiguration(
        accessBroker = accessBroker,
        principalKeyPublisher = {
            ReactiveSecurityContextHolder.getContext()
                .map { it.authentication.principal as ChatUserDetails<T> }
                .map { it.user.key }
        },
        rootKeys = rootKeys,
        compositeServiceBeansDefinition = compositeServiceBeansDefinition
    )
}