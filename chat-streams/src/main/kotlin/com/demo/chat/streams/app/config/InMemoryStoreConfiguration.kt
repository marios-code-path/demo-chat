package com.demo.chat.streams.app.config

import com.demo.chat.config.index.lucene.LuceneIndexBeans
import com.demo.chat.config.memory.InMemoryPersistenceBeans
import com.demo.chat.domain.*
import com.demo.chat.domain.lucene.IndexEntryEncoder
import com.demo.chat.service.IKeyService
import com.demo.chat.service.PersistenceStore
import com.demo.chat.service.conflate.KeyEnricherPersistenceStore
import com.demo.chat.streams.functions.MessageSendRequest
import com.demo.chat.streams.functions.MessageTopicRequest
import com.demo.chat.streams.functions.TopicMembershipRequest
import com.demo.chat.streams.functions.UserCreateRequest
import org.springframework.context.annotation.Bean

open class PersistenceBeans(keyService: IKeyService<Long>) : InMemoryPersistenceBeans<Long, String>(keyService) {
    @Bean
    open fun userPersistence() = UserCreateStore(user())

    @Bean
    open fun topicPersistence() = TopicCreateStore(topic())

    @Bean
    open fun messagePersistence() = MessageCreateStore(message())

    @Bean
    open fun membershipPersistence() = MembershipCreateStore(membership())
}

open class IndexBeans : LuceneIndexBeans<Long>(
    TypeUtil.LongUtil,
    IndexEntryEncoder.ofUser(),
    IndexEntryEncoder.ofMessage(),
    IndexEntryEncoder.ofTopic(),
    IndexEntryEncoder.ofTopicMembership()
) {
    @Bean
    open fun idxUser() = userIndex()

    @Bean
    open fun idxTopic() = topicIndex()

    @Bean
    open fun idxMembership() = membershipIndex()

    @Bean
    open fun idxMessage() = messageIndex()
}

class UserCreateStore(store: PersistenceStore<Long, User<Long>>) :
    KeyEnricherPersistenceStore<Long, UserCreateRequest, User<Long>>(
        store,
        { req, key -> User.create(key, req.name, req.handle, req.imgUri) })

class TopicCreateStore(store: PersistenceStore<Long, MessageTopic<Long>>) :
    KeyEnricherPersistenceStore<Long, MessageTopicRequest, MessageTopic<Long>>(
        store,
        { req, key -> MessageTopic.create(key, req.name) })

class MessageCreateStore(store: PersistenceStore<Long, Message<Long, String>>) :
    KeyEnricherPersistenceStore<Long, MessageSendRequest<Long, String>, Message<Long, String>>(
        store,
        { req, key ->
            Message.create(MessageKey.create(key.id, req.from, req.dest), req.msg, true)
        })

class MembershipCreateStore(store: PersistenceStore<Long, TopicMembership<Long>>) :
    KeyEnricherPersistenceStore<Long, TopicMembershipRequest<Long>, TopicMembership<Long>>(
        store,
        { req, key ->
            TopicMembership.create(key.id, req.principal, req.destination)
        }
    )