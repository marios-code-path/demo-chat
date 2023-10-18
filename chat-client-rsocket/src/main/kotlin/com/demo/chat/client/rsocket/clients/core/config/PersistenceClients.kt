package com.demo.chat.client.rsocket.clients.core.config

import com.demo.chat.client.rsocket.clients.core.PersistenceClient
import com.demo.chat.domain.*
import com.demo.chat.service.core.*
import com.demo.chat.service.security.AuthMetaPersistence
import org.springframework.core.ParameterizedTypeReference
import org.springframework.messaging.rsocket.RSocketRequester

class UserPersistenceClient<T>(prefix: String, requester: RSocketRequester) :
    PersistenceClient<T, User<T>>(prefix, requester, ParameterizedTypeReference.forType(User::class.java)),
    UserPersistence<T>

class MessagePersistenceClient<T, V>(prefix: String, requester: RSocketRequester) :
    PersistenceClient<T, Message<T, V>>(prefix, requester, ParameterizedTypeReference.forType(Message::class.java)),
    MessagePersistence<T, V>

class TopicPersistenceClient<T>(prefix: String, requester: RSocketRequester) :
    PersistenceClient<T, MessageTopic<T>>(
        prefix,
        requester,
        ParameterizedTypeReference.forType(MessageTopic::class.java)
    ), TopicPersistence<T>

class MembershipPersistenceClient<T>(prefix: String, requester: RSocketRequester) :
    PersistenceClient<T, TopicMembership<T>>(
        prefix,
        requester,
        ParameterizedTypeReference.forType(TopicMembership::class.java)
    ), MembershipPersistence<T>

class AuthMetadataPersistenceClient<T>(prefix: String, requester: RSocketRequester) :
    PersistenceClient<T, AuthMetadata<T>>(
        prefix,
        requester,
        ParameterizedTypeReference.forType(AuthMetadata::class.java)
    ), AuthMetaPersistence<T>

class KeyValueStoreClient<T>(prefix: String, requester: RSocketRequester) :
    PersistenceClient<T, KeyValuePair<T, Any>>(
        prefix,
        requester,
        ParameterizedTypeReference.forType(KeyValuePair::class.java)
    ), KeyValueStore<T, Any>