package com.demo.chat.client.rsocket.core.impl

import com.demo.chat.client.rsocket.core.IndexClient
import com.demo.chat.domain.*
import org.springframework.messaging.rsocket.RSocketRequester

class UserIndexClient<T, Q>(prefix: String, requester: RSocketRequester) :
        IndexClient<T, User<T>, Q>(prefix, requester)

class MessageIndexClient<T, V, Q>(prefix: String, requester: RSocketRequester) :
        IndexClient<T, Message<T, V>, Q>(prefix, requester)

class TopicIndexClient<T, Q>(prefix: String, requester: RSocketRequester) :
        IndexClient<T, MessageTopic<T>, Q>(prefix, requester)

class MembershipIndexClient<T, Q>(prefix: String, requester: RSocketRequester) :
        IndexClient<T, TopicMembership<T>, Q>(prefix, requester)

class AuthMetaIndexClient<T, Q>(prefix: String, requester: RSocketRequester) :
        IndexClient<T, AuthMetadata<T>, Q>(prefix, requester)