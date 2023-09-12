package com.demo.chat.controller.webflux

import com.demo.chat.config.IndexServiceBeans
import com.demo.chat.controller.webflux.core.mapping.IndexRestMapping
import com.demo.chat.domain.*
import com.demo.chat.service.core.IndexService
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class IndexRestController<T, E, Q>(private val that: IndexService<T, E, Q>) : IndexRestMapping<T, E, Q>,
    IndexService<T, E, Q> by that

@RequestMapping("/index/user")
class UserIndexRestController<T, V, Q : IndexSearchRequest>(s: IndexServiceBeans<T, V, Q>) :
    IndexRestController<T, User<T>, Q>(s.userIndex())

@RequestMapping("/index/message")
class MessageIndexRestController<T, V, Q : IndexSearchRequest>(s: IndexServiceBeans<T, V, Q>) :
    IndexRestController<T, Message<T, V>, Q>(s.messageIndex())

@RequestMapping("/index/membership")
class MembershipIndexRestController<T, V, Q : IndexSearchRequest>(s: IndexServiceBeans<T, V, Q>) :
    IndexRestController<T, TopicMembership<T>, Q>(s.membershipIndex())

@RequestMapping("/index/topic")
class TopicIndexRestController<T, V, Q : IndexSearchRequest>(s: IndexServiceBeans<T, V, Q>) :
    IndexRestController<T, MessageTopic<T>, Q>(s.topicIndex())

@RequestMapping("/index/auth")
class AuthMetadataIndexRestController<T, V, Q : IndexSearchRequest>(s: IndexServiceBeans<T, V, Q>) :
    IndexRestController<T, AuthMetadata<T>, Q>(s.authMetadataIndex())

//@RequestMapping("/index/kv")
//class KVIndexRestController<T, V, Q: IndexSearchRequest>(s: IndexService<T, KeyValuePair<T, Any>, Q>) :
//        IndexRestController<T, KeyValuePair<T, Any>, Q>(s)