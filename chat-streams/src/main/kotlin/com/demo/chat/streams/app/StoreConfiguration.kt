package com.demo.chat.streams.app

import com.demo.chat.streams.functions.MessageSendRequest
import com.demo.chat.streams.functions.MessageTopicRequest
import com.demo.chat.streams.functions.TopicMembershipRequest
import com.demo.chat.streams.functions.UserCreateRequest
import com.demo.chat.domain.*
import com.demo.chat.service.PersistenceStore
import com.demo.chat.service.conflate.KeyEnricherPersistenceStore


class UserCreatePersistence(store: PersistenceStore<Long, User<Long>>) :
    KeyEnricherPersistenceStore<Long, UserCreateRequest, User<Long>>(
        store,
        { req, key -> User.create(key, req.name, req.handle, req.imgUri) })

class TopicCreatePersistence(store: PersistenceStore<Long, MessageTopic<Long>>) :
    KeyEnricherPersistenceStore<Long, MessageTopicRequest, MessageTopic<Long>>(
        store,
        { req, key -> MessageTopic.create(key, req.name) })

class MessageCreatePersistence(store: PersistenceStore<Long, Message<Long, String>>) :
    KeyEnricherPersistenceStore<Long, MessageSendRequest<Long, String>, Message<Long, String>>(
        store,
        { req, key ->
            Message.create(
                MessageKey.create(key.id, req.from, req.dest),
                req.msg,
                true
            )
        })

class MembershipCreatePersistence(store: PersistenceStore<Long, TopicMembership<Long>>) :
    KeyEnricherPersistenceStore<Long, TopicMembershipRequest<Long>, TopicMembership<Long>>(
        store,
        { req, key ->
            TopicMembership.create(key.id, req.kId, req.destId)
        }
    )