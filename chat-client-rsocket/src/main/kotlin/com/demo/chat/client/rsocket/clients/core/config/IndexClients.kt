package com.demo.chat.client.rsocket.clients.core.config

import com.demo.chat.client.rsocket.clients.core.IndexClient
import com.demo.chat.client.rsocket.clients.core.MembershipIndexClient
import com.demo.chat.domain.*
import com.demo.chat.service.core.KeyValueIndexService
import com.demo.chat.service.core.MessageIndexService
import com.demo.chat.service.core.TopicIndexService
import com.demo.chat.service.core.UserIndexService
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

class KeyValueIndexClient<T, Q>(prefix: String, requester: RSocketRequester) :
    IndexClient<T, KeyValuePair<T, Any>, Q>(prefix, requester),
    KeyValueIndexService<T, Q>