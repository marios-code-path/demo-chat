package com.demo.chat.test.service.composite

import com.demo.chat.domain.AnonymousJoinException
import com.demo.chat.domain.ChatException
import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.MembershipRequest
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageTopic
import com.demo.chat.domain.NotFoundException
import com.demo.chat.domain.TopicMembership
import com.demo.chat.domain.User
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.service.composite.impl.TopicServiceImpl
import com.demo.chat.service.core.MembershipIndexService
import com.demo.chat.service.core.MembershipPersistence
import com.demo.chat.service.core.TopicIndexService
import com.demo.chat.service.core.TopicPubSubService
import com.demo.chat.service.core.UserPersistence
import com.demo.chat.service.dummy.DummyIndexService
import com.demo.chat.service.dummy.DummyPersistenceStore
import com.demo.chat.service.security.RoomMemberGrant
import com.demo.chat.service.security.RoomMemberGrantException
import com.demo.chat.test.key.TestVerifiers
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.util.function.Function
import java.util.function.Supplier

/**
 * The join and leave paths and the member `SEND` grant.
 *
 * **The grant write is the last step of each path.** No step compensates
 * another, so a failed grant leaves the membership change in place. See
 * `CHAT-mfveaecc`.
 */
class TopicServiceMemberGrantTests {

    @Test
    fun `a join grants SEND to the member on the room, after the subscribe`() {
        val fixture = Fixture()
        val service = fixture.service(RecordingGrant(fixture.calls))

        service.joinRoom(MembershipRequest(MEMBER.id, ROOM.id)).block()

        assertThat(fixture.calls).containsExactly("subscribe", "grantSend $MEMBER_TEXT $ROOM_TEXT")
        assertThat(fixture.members).describedAs("the stored membership").hasSize(1)
    }

    @Test
    fun `a leave expires SEND for the member on the room, after the unsubscribe`() {
        val fixture = Fixture()
        val service = fixture.service(RecordingGrant(fixture.calls))
        service.joinRoom(MembershipRequest(MEMBER.id, ROOM.id)).block()
        fixture.calls.clear()

        service.leaveRoom(MembershipRequest(MEMBER.id, ROOM.id)).block()

        assertThat(fixture.calls).containsExactly("unSubscribe", "expireSend $MEMBER_TEXT $ROOM_TEXT")
        assertThat(fixture.members).describedAs("the stored membership").isEmpty()
    }

    /** **An absent port writes nothing.** The composition carries no authorization. */
    @Test
    fun `an absent port joins and leaves with no grant`() {
        val fixture = Fixture()
        val service = fixture.service(null)

        StepVerifier.create(service.joinRoom(MembershipRequest(MEMBER.id, ROOM.id))).verifyComplete()
        StepVerifier.create(service.leaveRoom(MembershipRequest(MEMBER.id, ROOM.id))).verifyComplete()

        assertThat(fixture.calls).containsExactly("subscribe", "unSubscribe")
    }

    /** **A failed grant fails the join, and the membership stays.** */
    @Test
    fun `a failed grant fails the join and names the member and the room`() {
        val fixture = Fixture()
        val service = fixture.service(RecordingGrant(fixture.calls, ChatException("the store refused the write")))

        StepVerifier.create(service.joinRoom(MembershipRequest(MEMBER.id, ROOM.id)))
            .verifyErrorSatisfies { error ->
                assertThat(error).isInstanceOf(RoomMemberGrantException::class.java)
                val failure = error as RoomMemberGrantException
                assertThat(failure.memberKey).describedAs("the member key").isEqualTo(MEMBER)
                assertThat(failure.roomKey).describedAs("the room key").isEqualTo(ROOM)
                assertThat(failure.message).isEqualTo(
                    "The room member grant failed for member ${MEMBER.id} in room ${ROOM.id}."
                )
                assertThat(failure.cause).hasMessage("the store refused the write")
            }

        assertThat(fixture.members).describedAs("the stored membership").hasSize(1)
    }

    /** **A join that the room refuses writes no grant.** */
    @Test
    fun `a join to a room with no store row writes no grant`() {
        val fixture = Fixture(storeRoom = false)
        val service = fixture.service(RecordingGrant(fixture.calls))

        StepVerifier.create(service.joinRoom(MembershipRequest(MEMBER.id, ROOM.id)))
            .verifyErrorSatisfies { error -> assertThat(error).isEqualTo(NotFoundException) }

        assertThat(fixture.calls).isEmpty()
    }

    /**
     * **The `Anon` key cannot join a room.** The owner decided this on
     * 2026-10-02. The join check alone allows it, because a key holds every
     * right over itself. So the composite refuses it before any write.
     */
    @Test
    fun `an anonymous join is refused before any write`() {
        val fixture = Fixture()
        val service = fixture.service(RecordingGrant(fixture.calls))

        StepVerifier.create(service.joinRoom(MembershipRequest(FAKE_ROOTS.anon().id, ROOM.id)))
            .verifyErrorSatisfies { error -> assertThat(error).isEqualTo(AnonymousJoinException) }

        assertThat(fixture.members).describedAs("the stored membership").isEmpty()
        assertThat(fixture.calls).describedAs("the subscribe and the grant").isEmpty()
    }

    /** Records every call into one shared list, and fails on demand. */
    private class RecordingGrant(
        private val calls: MutableList<String>,
        private val failure: Throwable? = null,
    ) : RoomMemberGrant<Long> {

        override fun grantSend(member: Key<Long>, room: Key<Long>): Mono<Void> = record("grantSend", member, room)

        override fun expireSend(member: Key<Long>, room: Key<Long>): Mono<Void> = record("expireSend", member, room)

        private fun record(name: String, member: Key<Long>, room: Key<Long>): Mono<Void> = Mono.defer {
            calls.add("$name ${text(member)} ${text(room)}")
            failure?.let { Mono.error(it) } ?: Mono.empty()
        }
    }

    private class Fixture(storeRoom: Boolean = true) {
        val calls: MutableList<String> = mutableListOf()
        val members: MutableList<TopicMembership<Long>> = mutableListOf()
        private val topics = FakeTopicPersistence().also { store ->
            if (storeRoom) store.saved.add(MessageTopic.create(ROOM, "general"))
        }
        private var nextMembership = 300L

        private val membershipPersistence = object : DummyPersistenceStore<Long, TopicMembership<Long>>(),
            MembershipPersistence<Long> {
            override fun key(): Mono<out Key<Long>> = Mono.fromSupplier { Key.of(nextMembership++, -1L) }
            override fun add(ent: TopicMembership<Long>): Mono<Void> = Mono.fromRunnable { members.add(ent) }
            override fun rem(key: Key<Long>): Mono<Void> = Mono.fromRunnable { members.removeIf { it.key == key.id } }
        }

        private val membershipIndex = object : DummyIndexService<Long, TopicMembership<Long>, IndexSearchRequest>(),
            MembershipIndexService<Long, IndexSearchRequest> {
            override fun size(query: IndexSearchRequest): Mono<Long> = Mono.just(members.size.toLong())
            override fun add(entity: TopicMembership<Long>): Mono<Void> = Mono.empty()
            override fun findBy(query: IndexSearchRequest): Flux<out Key<Long>> =
                Flux.defer { Flux.fromIterable(members.map { Key.of(it.key, -1L) }) }
        }

        private val pubsub = object : TopicPubSubService<Long, String> {
            override fun open(topicId: Long): Mono<Void> = Mono.empty()
            override fun close(topicId: Long): Mono<Void> = Mono.empty()
            override fun getByUser(uid: Long): Flux<Long> = Flux.empty()
            override fun getUsersBy(topicId: Long): Flux<Long> = Flux.empty()
            override fun subscribe(member: Long, topic: Long): Mono<Void> =
                Mono.fromRunnable { calls.add("subscribe") }
            override fun unSubscribe(member: Long, topic: Long): Mono<Void> =
                Mono.fromRunnable { calls.add("unSubscribe") }
            override fun unSubscribeAll(member: Long): Mono<Void> = Mono.empty()
            override fun unSubscribeAllIn(topic: Long): Mono<Void> = Mono.empty()
            override fun sendMessage(message: Message<Long, String>): Mono<Void> = Mono.empty()
            override fun listenTo(topic: Long): Flux<out Message<Long, String>> = Flux.empty()
            override fun exists(topic: Long): Mono<Boolean> = Mono.just(true)
        }

        fun service(grant: RecordingGrant?): TopicServiceImpl<Long, String, IndexSearchRequest> = TopicServiceImpl(
            topicPersistence = topics,
            topicIndex = object : DummyIndexService<Long, MessageTopic<Long>, IndexSearchRequest>(),
                TopicIndexService<Long, IndexSearchRequest> {},
            pubsub = pubsub,
            userPersistence = object : DummyPersistenceStore<Long, User<Long>>(), UserPersistence<Long> {},
            membershipPersistence = membershipPersistence,
            membershipIndex = membershipIndex,
            emptyDataCodec = Supplier { "" },
            topicNameToQuery = Function { req -> IndexSearchRequest("name", req.name, 100) },
            memberOfIdToQuery = Function { IndexSearchRequest("memberOf", "", 100) },
            memberWithTopicToQuery = Function { IndexSearchRequest("member", "", 100) },
            messagePersistence = FakeMessagePersistence(),
            verifier = TestVerifiers.holding(FAKE_ROOTS, listOf(MEMBER, ROOM, FAKE_ROOTS.anon())),
            rootKeys = FAKE_ROOTS,
            roomMemberGrant = grant,
        )
    }

    private companion object {
        val MEMBER: Key<Long> = Key.of(6L, fakeRoot(ChatDomain.USER))
        val ROOM: Key<Long> = Key.of(7L, fakeRoot(ChatDomain.MESSAGE_TOPIC))

        val MEMBER_TEXT = text(MEMBER)
        val ROOM_TEXT = text(ROOM)

        /** The id and the root, because `Key.toString` answers the id alone. */
        fun text(key: Key<Long>): String = "${key.id}/${key.root}"
    }
}
