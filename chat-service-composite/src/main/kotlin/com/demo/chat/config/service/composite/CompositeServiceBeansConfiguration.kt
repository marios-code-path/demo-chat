package com.demo.chat.config.service.composite

import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.config.IndexServiceBeans
import com.demo.chat.config.PersistenceServiceBeans
import com.demo.chat.config.PubSubServiceBeans
import com.demo.chat.domain.*
import com.demo.chat.domain.serializers.EmptyMessageUtil
import com.demo.chat.service.composite.impl.MessagingServiceImpl
import com.demo.chat.service.composite.impl.TopicServiceImpl
import com.demo.chat.service.composite.impl.UserServiceImpl
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty("app.service.composite")
class CompositeServiceBeansConfiguration<T, V, Q>(
    val persistenceBeans: PersistenceServiceBeans<T, V>,
    val indexBeans: IndexServiceBeans<T, V, Q>,
    val pubsub: PubSubServiceBeans<T, V>,
    val typeUtil: TypeUtil<T>,
    private val emptyMessageSupplier: EmptyMessageUtil<V>,
    private val queryConverters: RequestToQueryConverters<Q>,
) : CompositeServiceBeans<T, V> {

    @Bean
    override fun messageService() = MessagingServiceImpl(
        messageIndex = indexBeans.messageIndex(),
        messagePersistence = persistenceBeans.messagePersistence(),
        topicMessaging = pubsub.pubSubService(),
        topicIdToQuery = queryConverters::topicIdToQuery
    )

    @Bean
    override fun topicService() = TopicServiceImpl(
        topicPersistence = persistenceBeans.topicPersistence(),
        topicIndex = indexBeans.topicIndex(),
        pubsub = pubsub.pubSubService(),
        userPersistence = persistenceBeans.userPersistence(),
        membershipPersistence = persistenceBeans.membershipPersistence(),
        membershipIndex = indexBeans.membershipIndex(),
        emptyDataCodec = emptyMessageSupplier,
        topicNameToQuery = queryConverters::topicNameToQuery,
        memberOfIdToQuery = queryConverters::membershipIdToQuery,
        memberWithTopicToQuery = queryConverters::membershipRequestToQuery
    )

    @Bean
    override fun userService() = UserServiceImpl<T, Q>(
        userPersistence = persistenceBeans.userPersistence(),
        userIndex = indexBeans.userIndex(),
        userHandleToQuery = queryConverters::userHandleToQuery
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