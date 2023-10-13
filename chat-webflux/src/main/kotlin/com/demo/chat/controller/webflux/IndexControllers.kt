package com.demo.chat.controller.webflux

import com.demo.chat.config.IndexServiceBeans
import com.demo.chat.controller.webflux.core.mapping.IndexRestMapping
import com.demo.chat.domain.*
import com.demo.chat.service.core.IndexService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

open class IndexRestController<T, E, Q>(private val that: IndexService<T, E, Q>) : IndexRestMapping<T, E, Q>,
    IndexService<T, E, Q> by that

@RestController
@RequestMapping("/index/user")
@ConditionalOnProperty(prefix = "app.controller", name = ["index"])
class UserIndexRestController<T, V, Q : IndexSearchRequest>(s: IndexServiceBeans<T, V, Q>) :
    IndexRestController<T, User<T>, Q>(s.userIndex())

@RestController
@RequestMapping("/index/message")
@ConditionalOnProperty(prefix = "app.controller", name = ["index"])
class MessageIndexRestController<T, V, Q : IndexSearchRequest>(s: IndexServiceBeans<T, V, Q>) :
    IndexRestController<T, Message<T, V>, Q>(s.messageIndex())

@RestController
@RequestMapping("/index/membership")
@ConditionalOnProperty(prefix = "app.controller", name = ["index"])
class MembershipIndexRestController<T, V, Q : IndexSearchRequest>(s: IndexServiceBeans<T, V, Q>) :
    IndexRestController<T, TopicMembership<T>, Q>(s.membershipIndex())

@RestController
@RequestMapping("/index/topic")
@ConditionalOnProperty(prefix = "app.controller", name = ["index"])
class TopicIndexRestController<T, V, Q : IndexSearchRequest>(s: IndexServiceBeans<T, V, Q>) :
    IndexRestController<T, MessageTopic<T>, Q>(s.topicIndex())

@RestController
@RequestMapping("/index/auth")
@ConditionalOnProperty(prefix = "app.controller", name = ["index"])
class AuthMetadataIndexRestController<T, V, Q : IndexSearchRequest>(s: IndexServiceBeans<T, V, Q>) :
    IndexRestController<T, AuthMetadata<T>, Q>(s.authMetadataIndex())

@RestController
@RequestMapping("/index/kv")
class KeyValueIndexRestController<T, V, Q: IndexSearchRequest>(s:IndexServiceBeans<T, V, Q>) :
        IndexRestController<T, KeyValuePair<T, Any>, Q>(s.KVPairIndex())