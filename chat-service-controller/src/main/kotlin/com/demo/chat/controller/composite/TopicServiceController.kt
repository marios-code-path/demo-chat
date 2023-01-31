package com.demo.chat.controller.composite

import com.demo.chat.ByIdRequest
import com.demo.chat.ByNameRequest
import com.demo.chat.MembershipRequest
import com.demo.chat.controller.composite.mapping.ChatTopicServiceMapping
import com.demo.chat.domain.*
import com.demo.chat.service.*
import com.fasterxml.jackson.annotation.JsonTypeName
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.function.Function
import java.util.function.Supplier

// TODO: Just listen to the membership stream rather than
// bundling extra data types.
// TODO: Extract pubsub into interface for wrapping classes
@JsonTypeName("JoinAlert")
data class JoinAlert<T, V>(override val key: MessageKey<T>, override val data: V) : Message<T, V> {
    override val record: Boolean
        get() = false
}

@JsonTypeName("LeaveAlert")
data class LeaveAlert<T, V>(override val key: MessageKey<T>, override val data: V) : Message<T, V> {
    override val record: Boolean
        get() = false
}

open class TopicServiceController<T, V, Q>(
    private val topicPersistence: TopicPersistence<T>,
    private val topicIndex: TopicIndexService<T, Q>,
    private val messaging: TopicPubSubService<T, V>,
    private val userPersistence: UserPersistence<T>,
    private val membershipPersistence: MembershipPersistence<T>,
    private val membershipIndex: MembershipIndexService<T, Q>,
    private val emptyDataCodec: Supplier<V>,
    private val topicNameToQuery: Function<ByNameRequest, Q>,
    private val membershipIdToQuery: Function<ByIdRequest<T>, Q>,
) : ChatTopicServiceMapping<T, V> {
    val logger: Logger = LoggerFactory.getLogger(this::class.simpleName)

    override fun addRoom(req: ByNameRequest): Mono<out Key<T>> =
            topicPersistence
                    .key()
                    .flatMap { key ->
                        val room = MessageTopic.create(Key.funKey(key.id), req.name)
                        topicPersistence
                                .add(room)
                                .flatMap {
                                    topicIndex.add(room)
                                }
                                .then(messaging.open(key.id))
                                .map { key }
                    }

    override fun deleteRoom(req: ByIdRequest<T>): Mono<Void> =
            topicPersistence
                    .get(Key.funKey(req.id))
                    .flatMap {
                        topicPersistence.rem(it.key)
                                .then(topicIndex.rem(it.key))
                                .then(messaging.unSubscribeAllIn(it.key.id))
                                .then(messaging.close(it.key.id))
                    }
                    .then()

    override fun listRooms(): Flux<out MessageTopic<T>> =
            topicPersistence
                    .all()

    override fun getRoom(req: ByIdRequest<T>): Mono<out MessageTopic<T>> =
            topicPersistence
                    .get(Key.funKey(req.id))

    override fun getRoomByName(req: ByNameRequest): Mono<out MessageTopic<T>> =
            topicIndex
                    .findBy(topicNameToQuery.apply(req))
                    .single()
                    .flatMap {
                        topicPersistence.get(it)
                    }

    override fun joinRoom(req: MembershipRequest<T>): Mono<Void> =
            topicPersistence
                    .get(Key.funKey(req.roomId))
                    .switchIfEmpty(Mono.error(NotFoundException))
                    .then(
                            membershipPersistence
                                    .key()
                                    .flatMap { key ->
                                        membershipPersistence
                                                .add(TopicMembership.create(key.id, req.roomId, req.uid))
                                                .thenMany(messaging
                                                        .sendMessage(JoinAlert(MessageKey.create(key.id, req.roomId, req.uid),
                                                                emptyDataCodec.get()))
                                                        .then(messaging.subscribe(req.uid, req.roomId)))
                                                .then()
                                    }
                    )


    override fun leaveRoom(req: MembershipRequest<T>): Mono<Void> =
            membershipPersistence
                    .get(Key.funKey(req.roomId))
                    .flatMap { m ->
                        membershipPersistence.rem(Key.funKey(m.key))
                                .thenMany(messaging
                                        .sendMessage(LeaveAlert(MessageKey.create(m.key, m.member, m.memberOf),
                                                emptyDataCodec.get()))
                                        .then(messaging.unSubscribe(m.member, m.memberOf)))
                                .then()

                    }

    override fun roomMembers(req: ByIdRequest<T>): Mono<TopicMemberships> =
            membershipIndex.findBy(membershipIdToQuery.apply(req))
                    .collectList()
                    .flatMapMany { membershipList ->
                        membershipPersistence.byIds(membershipList)
                    }
                    .flatMap { membership ->
                        userPersistence
                                .get(Key.funKey(membership.member))
                                .map { user ->
                                    TopicMember(user.key.id.toString(), user.handle, user.imageUri)
                                }
                    }
                    .collectList()
                    .map {
                        TopicMemberships(it.toSet())
                    }
}