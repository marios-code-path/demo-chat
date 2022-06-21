package com.demo.chat.client.rsocket.core.impl

import com.demo.chat.client.rsocket.core.PersistenceClient
import com.demo.chat.domain.*
import org.springframework.core.ParameterizedTypeReference
import org.springframework.messaging.rsocket.RSocketRequester

class UserPersistenceClient<T>(prefix: String, requester: RSocketRequester) :
    PersistenceClient<T, User<T>>(prefix, requester, ParameterizedTypeReference.forType(User::class.java))

class MessagePersistenceClient<T, V>(prefix: String, requester: RSocketRequester) :
    PersistenceClient<T, Message<T, V>>(prefix, requester, ParameterizedTypeReference.forType(Message::class.java))

class TopicPersistenceClient<T>(prefix: String, requester: RSocketRequester) :
    PersistenceClient<T, MessageTopic<T>>(
        prefix,
        requester,
        ParameterizedTypeReference.forType(MessageTopic::class.java)
    )

class MembershipPersistenceClient<T>(prefix: String, requester: RSocketRequester) :
    PersistenceClient<T, TopicMembership<T>>(
        prefix,
        requester,
        ParameterizedTypeReference.forType(TopicMembership::class.java)
    )

class AuthMetadataPersistenceClient<T>(prefix: String, requester: RSocketRequester) :
    PersistenceClient<T, AuthMetadata<T>>(
        prefix,
        requester,
        ParameterizedTypeReference.forType(AuthMetadata::class.java)
    )