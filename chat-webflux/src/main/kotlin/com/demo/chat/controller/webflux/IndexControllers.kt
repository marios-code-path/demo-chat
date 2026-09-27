package com.demo.chat.controller.webflux

import com.demo.chat.domain.TypeUtil

import com.demo.chat.domain.knownkey.ChatDomain

import com.demo.chat.service.core.KeyVerifier

import com.demo.chat.config.IndexServiceBeans
import com.demo.chat.controller.webflux.core.mapping.IndexRestMapping
import com.demo.chat.domain.*
import com.demo.chat.service.core.IndexService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

open class IndexRestController<T, E, Q>(
    private val that: IndexService<T, E, Q>,
    private val verifier: KeyVerifier<T>,
    private val domain: ChatDomain,
    private val typeUtil: TypeUtil<T>,
) : IndexRestMapping<T, E, Q>,
    IndexService<T, E, Q> by that {
    override fun verifier(): KeyVerifier<T> = verifier
    override fun domain(): ChatDomain = domain
    override fun typeUtil(): TypeUtil<T> = typeUtil
}

@RestController
@RequestMapping("/index/user")
@ConditionalOnProperty(prefix = "app.controller", name = ["index"])
class UserIndexRestController<T, V, Q : IndexSearchRequest>(s: IndexServiceBeans<T, V, Q>, verifier: KeyVerifier<T>, typeUtil: TypeUtil<T>) :
    IndexRestController<T, User<T>, Q>(s.userIndex(), verifier, ChatDomain.USER, typeUtil)

@RestController
@RequestMapping("/index/message")
@ConditionalOnProperty(prefix = "app.controller", name = ["index"])
class MessageIndexRestController<T, V, Q : IndexSearchRequest>(s: IndexServiceBeans<T, V, Q>, verifier: KeyVerifier<T>, typeUtil: TypeUtil<T>) :
    IndexRestController<T, Message<T, V>, Q>(s.messageIndex(), verifier, ChatDomain.MESSAGE, typeUtil)

@RestController
@RequestMapping("/index/membership")
@ConditionalOnProperty(prefix = "app.controller", name = ["index"])
class MembershipIndexRestController<T, V, Q : IndexSearchRequest>(s: IndexServiceBeans<T, V, Q>, verifier: KeyVerifier<T>, typeUtil: TypeUtil<T>) :
    IndexRestController<T, TopicMembership<T>, Q>(s.membershipIndex(), verifier, ChatDomain.TOPIC_MEMBERSHIP, typeUtil)

@RestController
@RequestMapping("/index/topic")
@ConditionalOnProperty(prefix = "app.controller", name = ["index"])
class TopicIndexRestController<T, V, Q : IndexSearchRequest>(s: IndexServiceBeans<T, V, Q>, verifier: KeyVerifier<T>, typeUtil: TypeUtil<T>) :
    IndexRestController<T, MessageTopic<T>, Q>(s.topicIndex(), verifier, ChatDomain.MESSAGE_TOPIC, typeUtil)

@RestController
@RequestMapping("/index/auth")
@ConditionalOnProperty(prefix = "app.controller", name = ["index"])
class AuthMetadataIndexRestController<T, V, Q : IndexSearchRequest>(s: IndexServiceBeans<T, V, Q>, verifier: KeyVerifier<T>, typeUtil: TypeUtil<T>) :
    IndexRestController<T, AuthMetadata<T>, Q>(s.authMetadataIndex(), verifier, ChatDomain.AUTH_METADATA, typeUtil)

@RestController
@RequestMapping("/index/kv")
class KeyValueIndexRestController<T, V, Q : IndexSearchRequest>(s: IndexServiceBeans<T, V, Q>, verifier: KeyVerifier<T>, typeUtil: TypeUtil<T>) :
    IndexRestController<T, KeyValuePair<T, Any>, Q>(s.KVPairIndex(), verifier, ChatDomain.KEY_VALUE_PAIR, typeUtil)