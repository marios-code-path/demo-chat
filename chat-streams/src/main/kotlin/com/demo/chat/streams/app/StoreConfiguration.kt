package com.demo.chat.streams.app

import com.demo.chat.config.KeyServiceBeans
import com.demo.chat.config.index.memory.InMemoryIndexBeans
import com.demo.chat.config.memory.InMemoryPersistenceBeans
import com.demo.chat.domain.*
import com.demo.chat.service.IKeyService
import com.demo.chat.service.MembershipIndexService
import com.demo.chat.service.PersistenceStore
import com.demo.chat.service.conflate.KeyEnricherPersistenceStore
import com.demo.chat.service.impl.lucene.index.IndexEntryEncoder
import com.demo.chat.service.impl.lucene.index.StringToKeyEncoder
import com.demo.chat.service.impl.memory.persistence.KeyServiceInMemory
import com.demo.chat.streams.functions.MessageSendRequest
import com.demo.chat.streams.functions.MessageTopicRequest
import com.demo.chat.streams.functions.TopicMembershipRequest
import com.demo.chat.streams.functions.UserCreateRequest
import org.springframework.context.annotation.Bean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.random.Random

open class MemoryKeyServiceConfiguration : KeyServiceBeans<Long> {
    private val atom = AtomicLong(abs(Random.nextLong()))

    @Bean
    override fun keyService() = KeyServiceInMemory { atom.incrementAndGet() }
}

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

open class IndexBeans : InMemoryIndexBeans<Long, String>(
    StringToKeyEncoder { i -> Key.funKey(i.toLong()) },
    IndexEntryEncoder { t ->
        listOf(
            Pair("key", t.key.id.toString()),
            Pair("handle", t.handle),
            Pair("name", t.name)
        )
    },
    IndexEntryEncoder { t ->
        listOf(
            Pair("key", t.key.id.toString()),
            Pair("text", t.data)
        )
    },
    IndexEntryEncoder { t ->
        listOf(
            Pair("key", t.key.id.toString()),
            Pair("name", t.data)
        )
    },
    IndexEntryEncoder { t ->
        listOf(
            Pair("key", Key.funKey(t.key).toString()),
            Pair(MembershipIndexService.MEMBER, t.member.toString()),
            Pair(MembershipIndexService.MEMBEROF, t.memberOf.toString())
        )
    }) {
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
            TopicMembership.create(key.id, req.pid, req.destId)
        }
    )