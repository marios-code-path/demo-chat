package com.demo.chat.config.service.composite

import com.demo.chat.config.IndexServiceBeans
import com.demo.chat.config.PersistenceServiceBeans
import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.domain.*
import com.demo.chat.service.composite.impl.MessagingServiceImpl
import com.demo.chat.service.composite.impl.TopicServiceImpl
import com.demo.chat.service.composite.impl.UserServiceImpl
import com.demo.chat.service.core.TopicPubSubService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty("app.service.composite")
class CompositeServiceBeansConfiguration<T, V, Q>(
    val persistenceBeans: PersistenceServiceBeans<T, V>,
    val indexBeans: IndexServiceBeans<T, V, Q>,
    val pubsub: TopicPubSubService<T, V>,
    val typeUtil: TypeUtil<T>,
    private val emptyMessageSupplier: () -> V,
    private val topicIdQueryFunction: (ByIdRequest<T>) -> Q,
    private val topicNameQueryFunction: (ByStringRequest) -> Q,
    private val membershipOfIdQueryFunction: (ByIdRequest<T>) -> Q,
    private val membershipRequestQueryFunction: (MembershipRequest<T>) -> Q,
    private val userHandleQueryFunction: (ByStringRequest) -> Q,
) : CompositeServiceBeans<T, V> {

    @Bean
    override fun messageService() = MessagingServiceImpl(
        messageIndex = indexBeans.messageIndex(),
        messagePersistence = persistenceBeans.messagePersistence(),
        topicMessaging = pubsub,
        topicIdToQuery = topicIdQueryFunction
    )

    @Bean
    override fun topicService() = TopicServiceImpl(
        topicPersistence = persistenceBeans.topicPersistence(),
        topicIndex = indexBeans.topicIndex(),
        pubsub = pubsub,
        userPersistence = persistenceBeans.userPersistence(),
        membershipPersistence = persistenceBeans.membershipPersistence(),
        membershipIndex = indexBeans.membershipIndex(),
        emptyDataCodec = emptyMessageSupplier,
        topicNameToQuery = topicNameQueryFunction,
        memberOfIdToQuery = membershipOfIdQueryFunction,
        memberWithTopicToQuery = membershipRequestQueryFunction
    )

    @Bean
    override fun userService() = UserServiceImpl<T, Q>(
        userPersistence = persistenceBeans.userPersistence(),
        userIndex = indexBeans.userIndex(),
        userHandleToQuery = userHandleQueryFunction
    )
}

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