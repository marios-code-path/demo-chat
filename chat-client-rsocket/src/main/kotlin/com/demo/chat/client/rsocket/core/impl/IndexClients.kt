package com.demo.chat.client.rsocket.core.impl

import com.demo.chat.client.rsocket.core.IndexClient
import com.demo.chat.client.rsocket.core.MembershipIndexClient
import com.demo.chat.domain.*
import com.demo.chat.service.MembershipIndexService
import com.demo.chat.service.MessageIndexService
import com.demo.chat.service.TopicIndexService
import com.demo.chat.service.UserIndexService
import com.demo.chat.service.security.AuthMetaIndex
import org.springframework.messaging.rsocket.RSocketRequester

class UserIndexClient<T, Q>(prefix: String, requester: RSocketRequester) :
    IndexClient<T, User<T>, Q>(prefix, requester),
    UserIndexService<T, Q>

class MessageIndexClient<T, V, Q>(prefix: String, requester: RSocketRequester) :
    IndexClient<T, Message<T, V>, Q>(prefix, requester),
    MessageIndexService<T, V, Q>

class TopicIndexClient<T, Q>(prefix: String, requester: RSocketRequester) :
    IndexClient<T, MessageTopic<T>, Q>(prefix, requester),
    TopicIndexService<T, Q>

class MembershipIndexClientImpl<T, Q>(prefix: String, requester: RSocketRequester) :
    MembershipIndexClient<T, Q>(IndexClient(prefix, requester), prefix, requester)

class AuthMetaIndexClient<T, Q>(prefix: String, requester: RSocketRequester) :
    IndexClient<T, AuthMetadata<T>, Q>(prefix, requester),
    AuthMetaIndex<T, Q>