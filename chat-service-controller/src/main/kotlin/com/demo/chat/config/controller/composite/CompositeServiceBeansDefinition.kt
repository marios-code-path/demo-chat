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
    val s: PersistenceServiceBeans<T, V>,
    val x: IndexServiceBeans<T, V, Q>,
    val p: TopicPubSubService<T, V>,
    val t: TypeUtil<T>,
    private val emptyMessageSupplier: () -> V,
    private val topicIdQueryFunction: (ByIdRequest<T>) -> Q,
    private val topicNameQueryFunction: (ByStringRequest) -> Q,
    private val membershipOfIdQueryFunction: (ByIdRequest<T>) -> Q,
    private val membershipRequestQueryFunction: (MembershipRequest<T>) -> Q,
    private val handleQueryFunction: (ByStringRequest) -> Q,
) : CompositeServiceBeans<T, V> {

    override fun messageService() = MessagingServiceImpl(
        messageIndex = x.messageIndex(),
        messagePersistence = s.messagePersistence(),
        topicMessaging = p,
        topicIdToQuery = topicIdQueryFunction
    )

    override fun topicService() = TopicServiceImpl(
        topicPersistence = s.topicPersistence(),
        topicIndex = x.topicIndex(),
        pubsub = p,
        userPersistence = s.userPersistence(),
        membershipPersistence = s.membershipPersistence(),
        membershipIndex = x.membershipIndex(),
        emptyDataCodec = emptyMessageSupplier,
        topicNameToQuery = topicNameQueryFunction,
        memberOfIdToQuery = membershipOfIdQueryFunction,
        memberWithTopicToQuery = membershipRequestQueryFunction
    )

    override fun userService() = UserServiceImpl<T, Q>(
        userPersistence = s.userPersistence(),
        userIndex = x.userIndex(),
        userHandleToQuery = handleQueryFunction
    )
}