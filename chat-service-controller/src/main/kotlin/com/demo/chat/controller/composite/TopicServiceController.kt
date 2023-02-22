package com.demo.chat.controller.composite

import com.demo.chat.controller.composite.mapping.ChatTopicServiceMapping
import com.demo.chat.domain.*
import com.demo.chat.service.*
import com.demo.chat.service.core.*
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
data class JoinAlert<T>(override val key: MessageKey<T>, override val data: String) : Message<T, String> {
    override val record: Boolean
        get() = false
}

@JsonTypeName("LeaveAlert")
data class LeaveAlert<T>(override val key: MessageKey<T>, override val data: String) : Message<T, String> {
    override val record: Boolean
        get() = false
}

open class TopicServiceController<T, V, Q>(
    private val topicPersistence: TopicPersistence<T>,
    private val topicIndex: TopicIndexService<T, Q>,
    private val pubsub: TopicPubSubService<T, V>,
    private val userPersistence: UserPersistence<T>,
    private val membershipPersistence: MembershipPersistence<T>,
    private val membershipIndex: MembershipIndexService<T, Q>,
    private val emptyDataCodec: Supplier<V>,
    private val topicNameToQuery: Function<ByNameRequest, Q>,
    private val memberOfIdToQuery: Function<ByIdRequest<T>, Q>,
) : ChatTopicServiceMapping<T, V> {
    val logger: Logger = LoggerFactory.getLogger(this::class.simpleName)

    override fun addRoom(req: ByNameRequest): Mono<out Key<T>> =
        topicPersistence
            .key()
            .map { key -> MessageTopic.create(key, req.name)}
            .flatMap { room ->
                topicPersistence
                    .add(room)
                    .then(topicIndex.add(room))
                    .then(pubsub.open(room.key.id))
                    .then(Mono.just(room.key))
            }


    override fun deleteRoom(req: ByIdRequest<T>): Mono<Void> =
        topicPersistence
            .get(Key.funKey(req.id))
            .flatMap {
                topicPersistence.rem(it.key)
                    .then(topicIndex.rem(it.key))
                    .then(pubsub.unSubscribeAllIn(it.key.id))
                    .then(pubsub.close(it.key.id))
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

    override fun joinRoom(req: MembershipRequest<T>): Mono<Void> {
        return topicPersistence
            .get(Key.funKey(req.roomId))
            .switchIfEmpty(Mono.error(NotFoundException))
            .then(membershipPersistence.key())
            .flatMapMany { key ->
                membershipPersistence
                    .add(TopicMembership.create(key.id, req.uid, req.roomId))
                    .then(membershipIndex.add(TopicMembership.create(key.id, req.uid, req.roomId)))
                    .then(
                        pubsub
                            .sendMessage(
                                Message.create(
                                    MessageKey.create(key.id, req.uid, req.roomId),
                                    emptyDataCodec.get(), false
                                )
                            )

                    )
            }
            .then(pubsub.subscribe(req.uid, req.roomId))
    }


    override fun leaveRoom(req: MembershipRequest<T>): Mono<Void> =
        membershipPersistence
            .get(Key.funKey(req.roomId))
            .flatMap { m ->
                membershipPersistence.rem(Key.funKey(m.key))
                    .then(
                        pubsub
                            .sendMessage(
                                Message.create(
                                    MessageKey.create(m.key, m.member, m.memberOf),
                                    emptyDataCodec.get(), false
                                )
                            )

                    )
                    .then(pubsub.unSubscribe(m.member, m.memberOf))
            }

    override fun roomMembers(req: ByIdRequest<T>): Mono<TopicMemberships> =
        membershipIndex.findBy(memberOfIdToQuery.apply(req))
            .collectList()
            .flatMapMany { membershipList ->
                membershipPersistence.byIds(membershipList).doOnNext {
                    println("${it.key} ${it.member} : ${it.memberOf}")
                }
            }
            .flatMap { membership ->
                userPersistence
                    .get(Key.funKey(membership.member))
                    .map { user -> TopicMember(user.key.id.toString(), user.handle, user.imageUri) }
            }
            .collectList()
            .map {
                TopicMemberships(it.toSet())
            }
}