package com.demo.chat.test.service.composite

import com.demo.chat.domain.ByIdRequest
import com.demo.chat.domain.ByStringRequest
import com.demo.chat.domain.ChatException
import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.Message
import com.demo.chat.domain.MembershipRequest
import com.demo.chat.domain.MessageTopic
import com.demo.chat.domain.TopicMembership
import com.demo.chat.domain.User
import com.demo.chat.service.composite.ChatTopicService
import com.demo.chat.service.composite.impl.TopicServiceImpl
import com.demo.chat.service.core.MembershipIndexService
import com.demo.chat.service.core.MembershipPersistence
import com.demo.chat.service.core.TopicIndexService
import com.demo.chat.service.core.TopicPersistence
import com.demo.chat.service.core.TopicPubSubService
import com.demo.chat.service.core.UserPersistence
import com.demo.chat.service.dummy.DummyIndexService
import com.demo.chat.service.dummy.DummyPersistenceStore
import com.demo.chat.service.vector.JobTopicNames
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant
import java.util.function.Function
import java.util.function.Supplier

class TopicServiceReservedNameTests {
    private val jobName = JobTopicNames.nameFor(1, "long", Instant.EPOCH, "abc")

    @Test
    fun `addRoom rejects a reserved name`() {
        val service = topicServiceUnderTest()

        StepVerifier
            .create(service.addRoom(ByStringRequest(jobName)))
            .verifyError(ChatException::class.java)
    }

    @Test
    fun `listRooms omits a reserved topic`() {
        val service = topicServiceUnderTest(
            existing = listOf(
                MessageTopic.create(Key.funKey(1L), "general"),
                MessageTopic.create(Key.funKey(2L), jobName),
            )
        )

        StepVerifier
            .create(service.listRooms())
            .assertNext { topic -> Assertions.assertThat(topic.data).isEqualTo("general") }
            .verifyComplete()
    }

    private fun topicServiceUnderTest(
        existing: List<MessageTopic<Long>> = emptyList(),
    ): ChatTopicService<Long, String> {
        val stored = existing.toMutableList()
        val indexed = mutableListOf<MessageTopic<Long>>()
        var nextId = 100L

        val topicPersistence = object : TopicPersistence<Long> {
            override fun key(): Mono<out Key<Long>> = Mono.fromSupplier { Key.funKey(nextId++) }
            override fun add(ent: MessageTopic<Long>): Mono<Void> = Mono.fromRunnable { stored.add(ent) }
            override fun rem(key: Key<Long>): Mono<Void> = Mono.fromRunnable { stored.removeIf { it.key == key } }
            override fun get(key: Key<Long>): Mono<out MessageTopic<Long>> =
                Mono.justOrEmpty(stored.firstOrNull { it.key == key })
            override fun all(): Flux<out MessageTopic<Long>> = Flux.fromIterable(stored.toList())
        }

        val topicIndex = object : TopicIndexService<Long, IndexSearchRequest> {
            override fun add(entity: MessageTopic<Long>): Mono<Void> = Mono.fromRunnable { indexed.add(entity) }
            override fun rem(key: Key<Long>): Mono<Void> = Mono.fromRunnable { indexed.removeIf { it.key == key } }
            override fun findBy(query: IndexSearchRequest): Flux<out Key<Long>> =
                Flux.fromIterable(indexed.filter { it.data == query.second }.map { it.key })
            override fun findUnique(query: IndexSearchRequest): Mono<out Key<Long>> = findBy(query).singleOrEmpty()
        }

        val pubsub = object : TopicPubSubService<Long, String> {
            override fun open(topicId: Long): Mono<Void> = Mono.empty()
            override fun close(topicId: Long): Mono<Void> = Mono.empty()
            override fun getByUser(uid: Long): Flux<Long> = Flux.empty()
            override fun getUsersBy(topicId: Long): Flux<Long> = Flux.empty()
            override fun subscribe(member: Long, topic: Long): Mono<Void> = Mono.empty()
            override fun unSubscribe(member: Long, topic: Long): Mono<Void> = Mono.empty()
            override fun unSubscribeAll(member: Long): Mono<Void> = Mono.empty()
            override fun unSubscribeAllIn(topic: Long): Mono<Void> = Mono.empty()
            override fun sendMessage(message: Message<Long, String>): Mono<Void> = Mono.empty()
            override fun listenTo(topic: Long): Flux<out Message<Long, String>> = Flux.empty()
            override fun exists(topic: Long): Mono<Boolean> = Mono.just(true)
        }

        return TopicServiceImpl(
            topicPersistence = topicPersistence,
            topicIndex = topicIndex,
            pubsub = pubsub,
            userPersistence = object : DummyPersistenceStore<Long, User<Long>>(), UserPersistence<Long> {},
            membershipPersistence = object : DummyPersistenceStore<Long, TopicMembership<Long>>(),
                MembershipPersistence<Long> {},
            membershipIndex = object : DummyIndexService<Long, TopicMembership<Long>, IndexSearchRequest>(),
                MembershipIndexService<Long, IndexSearchRequest> {
                override fun size(query: IndexSearchRequest): Mono<Long> = Mono.just(0L)
            },
            emptyDataCodec = Supplier { "" },
            topicNameToQuery = Function { req -> IndexSearchRequest("name", req.name, 100) },
            memberOfIdToQuery = Function { IndexSearchRequest("memberOf", "", 100) },
            memberWithTopicToQuery = Function { IndexSearchRequest("member", "", 100) },
        )
    }
}
