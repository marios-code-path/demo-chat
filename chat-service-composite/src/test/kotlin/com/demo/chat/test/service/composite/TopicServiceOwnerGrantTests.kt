package com.demo.chat.test.service.composite

import com.demo.chat.domain.ByStringRequest
import com.demo.chat.domain.ChatException
import com.demo.chat.domain.DuplicateException
import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageTopic
import com.demo.chat.domain.TopicMembership
import com.demo.chat.domain.User
import com.demo.chat.service.composite.impl.TopicServiceImpl
import com.demo.chat.service.core.MembershipIndexService
import com.demo.chat.service.core.MembershipPersistence
import com.demo.chat.service.core.TopicIndexService
import com.demo.chat.service.core.TopicPersistence
import com.demo.chat.service.core.TopicPubSubService
import com.demo.chat.service.core.UserPersistence
import com.demo.chat.service.dummy.DummyIndexService
import com.demo.chat.service.dummy.DummyPersistenceStore
import com.demo.chat.service.security.RoomOwnerGrant
import com.demo.chat.service.security.RoomOwnerGrantException
import com.demo.chat.test.key.TestKeys
import com.demo.chat.test.key.TestVerifiers
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.util.function.Function
import java.util.function.Supplier

/**
 * The room creation path and the owner grant.
 *
 * **The grant write is the last step of `addRoom`.** No step of that chain
 * compensates any other, so a failed grant leaves the room in place. See
 * `CHAT-zhjltbky`.
 */
class TopicServiceOwnerGrantTests {

    /** **The service writes one grant, for the key of the room it created.** */
    @Test
    fun `addRoom writes one grant for the new room`() {
        val fixture = Fixture()
        val service = fixture.service(RecordingGrant())

        val key = service.addRoom(ByStringRequest("general")).block()!!

        assertThat(fixture.grant.calls).containsExactly(key)
    }

    /**
     * **An absent port writes no grant, and it does not fail.** The port is
     * absent exactly when the composition carries no authorization.
     */
    @Test
    fun `an absent port creates the room and writes no grant`() {
        val fixture = Fixture()
        val service = fixture.service(null)

        StepVerifier.create(service.addRoom(ByStringRequest("general")))
            .expectNextCount(1)
            .verifyComplete()

        assertThat(fixture.stored).hasSize(1)
        assertThat(fixture.grant.calls).isEmpty()
    }

    /**
     * **A failed grant write fails the request, and the room stays.**
     *
     * **The failure names the room key**, so an operator can write the missing
     * row by hand. The message is exact here, because a message that named
     * another key would pass a `contains` check.
     *
     * The cause is preserved, because the reason for the refusal is what an
     * operator acts on.
     */
    @Test
    fun `a failed grant write fails addRoom and names the room`() {
        val fixture = Fixture()
        val service = fixture.service(RecordingGrant(failure = ChatException("the store refused the write")))

        StepVerifier.create(service.addRoom(ByStringRequest("general")))
            .verifyErrorSatisfies { error ->
                assertThat(error).describedAs("the failure").isInstanceOf(RoomOwnerGrantException::class.java)
                val room = fixture.stored.single().key
                val grantFailure = error as RoomOwnerGrantException
                assertThat(grantFailure.roomKey).describedAs("the room key field").isEqualTo(room)
                assertThat(grantFailure.message)
                    .describedAs("the message")
                    .isEqualTo(
                        "The room owner grant failed for room ${room.id}. " +
                            "The room exists and it has no owner."
                    )
                assertThat(grantFailure.cause)
                    .describedAs("the cause")
                    .hasMessage("the store refused the write")
            }

        assertThat(fixture.stored).describedAs("the store row").hasSize(1)
        assertThat(fixture.indexed).describedAs("the index row").hasSize(1)
        assertThat(fixture.opened).describedAs("the open topic").hasSize(1)
    }

    /**
     * **A duplicate name writes no second grant.** The name check is
     * check-then-act, so a duplicate must not reach the grant write.
     */
    @Test
    fun `a duplicate name writes no second grant`() {
        val fixture = Fixture()
        val grant = RecordingGrant()
        val service = fixture.service(grant)

        service.addRoom(ByStringRequest("general")).block()
        StepVerifier.create(service.addRoom(ByStringRequest("general")))
            .verifyErrorSatisfies { error ->
                assertThat(error).describedAs("the second add").isEqualTo(DuplicateException)
            }

        assertThat(grant.calls).hasSize(1)
    }

    /** Records every call, and fails on demand. */
    private class RecordingGrant(private val failure: Throwable? = null) : RoomOwnerGrant<Long> {
        val calls: MutableList<Key<Long>> = mutableListOf()

        override fun grantOwner(roomKey: Key<Long>): Mono<Void> {
            calls.add(roomKey)
            return failure?.let { Mono.error(it) } ?: Mono.empty()
        }
    }

    /** The same store, index and pubsub stubs as `TopicServiceReservedNameTests`. */
    private class Fixture {
        val stored: MutableList<MessageTopic<Long>> = mutableListOf()
        val indexed: MutableList<MessageTopic<Long>> = mutableListOf()
        val opened: MutableList<Long> = mutableListOf()
        lateinit var grant: RecordingGrant
        private var nextId = 100L

        val topicPersistence = object : TopicPersistence<Long> {
            override fun key(): Mono<out Key<Long>> = Mono.fromSupplier { TestKeys.key(nextId++) }
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
            override fun open(topicId: Long): Mono<Void> = Mono.fromRunnable { opened.add(topicId) }
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

        /** [grantPort] is null when the test needs a composition with no port. */
        fun service(grantPort: RecordingGrant?): TopicServiceImpl<Long, String, IndexSearchRequest> {
            this.grant = grantPort ?: RecordingGrant()
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
                messagePersistence = FakeMessagePersistence(),
                verifier = TestVerifiers.resolvingNothing(),
                rootKeys = FAKE_ROOTS,
                roomOwnerGrant = grantPort,
            )
        }
    }
}
