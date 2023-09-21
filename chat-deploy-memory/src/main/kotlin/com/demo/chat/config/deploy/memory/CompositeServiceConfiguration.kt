package com.demo.chat.config.deploy.memory

import com.demo.chat.config.IndexServiceBeans
import com.demo.chat.config.PersistenceServiceBeans
import com.demo.chat.config.PubSubServiceBeans
import com.demo.chat.config.service.composite.CompositeServiceBeansDefinition
import com.demo.chat.domain.*
import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.service.core.*
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty("app.service.composite")
class CompositeServiceConfiguration<T>(
    persistenceServiceBeans: PersistenceServiceBeans<T, String>,
    indexServiceBeans: IndexServiceBeans<T, String, IndexSearchRequest>,
    pubsubBeans: PubSubServiceBeans<T, String>,
    typeUtil: TypeUtil<T>,
) : CompositeServiceBeans<T, String> by CompositeServiceBeansDefinition(
    persistenceBeans = persistenceServiceBeans,
    indexBeans = indexServiceBeans,
    pubsub = pubsubBeans.pubSubService(),
    typeUtil = typeUtil,
    emptyMessageSupplier = { "" },
    topicIdQueryFunction = { req -> IndexSearchRequest(MessageIndexService.TOPIC, typeUtil.toString(req.id), 100) },
    topicNameQueryFunction = { req -> IndexSearchRequest(TopicIndexService.NAME, req.name, 100) },
    membershipOfIdQueryFunction = { req ->
        IndexSearchRequest(
            MembershipIndexService.MEMBEROF,
            typeUtil.toString(req.id),
            100
        )
    },
    membershipRequestQueryFunction = { req ->
        IndexSearchRequest(
            MembershipIndexService.MEMBER,
            "${typeUtil.toString(req.uid)} AND ${MembershipIndexService.MEMBEROF}:${typeUtil.toString(req.roomId)}",
            100
        )
    },
    handleQueryFunction = { req -> IndexSearchRequest(UserIndexService.HANDLE, req.name, 100) }
)
//
//@Configuration
//@ConditionalOnProperty("app.service.composite.security")
//class SpringSecurityCompositeServiceAccessBeans<T>(
//    accessBroker: AccessBroker<T>,
//    rootKeys: RootKeys<T>,
//    compositeServiceBeansDefinition: CompositeServiceBeansDefinition<T, String, IndexSearchRequest>
//) : CompositeServiceBeans<T, String> by CompositeServiceAccessBeansConfiguration(
//    accessBroker = accessBroker,
//    principalKeyPublisher = {
//        ReactiveSecurityContextHolder.getContext()
//            .map { it.authentication.principal as ChatUserDetails<T> }
//            .map { it.user.key }
//    },
//    rootKeys = rootKeys,
//    compositeServiceBeansDefinition = compositeServiceBeansDefinition
//)
