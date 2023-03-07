package com.demo.chat.config.controller.composite

import com.demo.chat.config.IndexServiceBeans
import com.demo.chat.config.PersistenceServiceBeans
import com.demo.chat.domain.ByIdRequest
import com.demo.chat.domain.ByStringRequest
import com.demo.chat.domain.MembershipRequest
import com.demo.chat.domain.TypeUtil
import com.demo.chat.service.composite.CompositeServiceBeans
import com.demo.chat.service.composite.impl.MessagingServiceImpl
import com.demo.chat.service.composite.impl.TopicServiceImpl
import com.demo.chat.service.composite.impl.UserServiceImpl
import com.demo.chat.service.core.TopicPubSubService

class CompositeServiceBeansDefinition<T, V, Q>(
    val persistenceBeans: PersistenceServiceBeans<T, V>,
    val indexBeans: IndexServiceBeans<T, V, Q>,
    val pubsub: TopicPubSubService<T, V>,
    val typeUtil: TypeUtil<T>,
    private val emptyMessageSupplier: () -> V,
    private val topicIdQueryFunction: (ByIdRequest<T>) -> Q,
    private val topicNameQueryFunction: (ByStringRequest) -> Q,
    private val membershipOfIdQueryFunction: (ByIdRequest<T>) -> Q,
    private val membershipRequestQueryFunction: (MembershipRequest<T>) -> Q,
    private val handleQueryFunction: (ByStringRequest) -> Q,
) : CompositeServiceBeans<T, V> {

    override fun messageService() = MessagingServiceImpl(
        messageIndex = indexBeans.messageIndex(),
        messagePersistence = persistenceBeans.messagePersistence(),
        topicMessaging = pubsub,
        topicIdToQuery = topicIdQueryFunction
    )

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

    override fun userService() = UserServiceImpl<T, Q>(
        userPersistence = persistenceBeans.userPersistence(),
        userIndex = indexBeans.userIndex(),
        userHandleToQuery = handleQueryFunction
    )
}