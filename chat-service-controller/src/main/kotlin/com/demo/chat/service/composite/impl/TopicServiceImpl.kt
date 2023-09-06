package com.demo.chat.service.composite.impl

import com.demo.chat.domain.*
import com.demo.chat.service.composite.ChatTopicService
import com.demo.chat.service.core.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.function.Function
import java.util.function.Supplier


open class TopicServiceImpl<T, V, Q>(
    private val topicPersistence: TopicPersistence<T>,
    private val topicIndex: TopicIndexService<T, Q>,
    private val pubsub: TopicPubSubService<T, V>,
    private val userPersistence: UserPersistence<T>,
    private val membershipPersistence: MembershipPersistence<T>,
    private val membershipIndex: MembershipIndexService<T, Q>,
    private val emptyDataCodec: Supplier<V>,
    private val topicNameToQuery: Function<ByStringRequest, Q>,
    private val memberOfIdToQuery: Function<ByIdRequest<T>, Q>,
    private val memberWithTopicToQuery: Function<MembershipRequest<T>, Q>
) : ChatTopicService<T, V> {
    val logger: Logger = LoggerFactory.getLogger(this::class.simpleName)

    override fun addRoom(req: ByStringRequest): Mono<out Key<T>> =
        topicPersistence
            .key()
            .map { key -> MessageTopic.create(key, req.name) }
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

    override fun getRoomByName(req: ByStringRequest): Mono<out MessageTopic<T>> =
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
            .map { key -> TopicMembership.create(key.id, req.uid, req.roomId) }
            .flatMapMany { membership ->
                membershipPersistence
                    .add(membership)
                    .then(membershipIndex.add(membership))
                    .then(
                        pubsub.sendMessage(
                            JoinAlert(
                                MessageKey.create(membership.key, req.uid, req.roomId),
                                emptyDataCodec.get()
                            )
                        )
                    )
            }
            .then(pubsub.subscribe(req.uid, req.roomId))
    }

    override fun leaveRoom(req: MembershipRequest<T>): Mono<Void> =
        membershipIndex
            .findBy(memberWithTopicToQuery.apply(req))
            .last()
            .flatMap { key ->
                membershipPersistence.rem(key)
                    .then(
                        pubsub.sendMessage(
                            LeaveAlert(
                                MessageKey.create(key.id, req.uid, req.roomId),
                                emptyDataCodec.get()
                            )
                        )
                    )
                    .then(pubsub.unSubscribe(req.uid, req.roomId))
            }

    override fun roomMembers(req: ByIdRequest<T>): Mono<TopicMemberships> =
        membershipIndex.findBy(memberOfIdToQuery.apply(req))
            .collectList()
            .flatMapMany { membershipList ->
                membershipPersistence.byIds(membershipList)
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