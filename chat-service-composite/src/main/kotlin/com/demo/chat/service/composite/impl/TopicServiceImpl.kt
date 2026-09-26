package com.demo.chat.service.composite.impl

import com.demo.chat.service.core.MessagePersistence

import com.demo.chat.domain.knownkey.RootKeys

import com.demo.chat.domain.knownkey.ChatDomain

import com.demo.chat.service.core.KeyVerifier

import com.demo.chat.domain.*
import com.demo.chat.service.composite.ChatTopicService
import com.demo.chat.service.core.*
import com.demo.chat.service.vector.JobTopicNames
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.function.Function
import java.util.function.Supplier


open class TopicServiceImpl<T : Any, V, Q>(
    private val topicPersistence: TopicPersistence<T>,
    private val topicIndex: TopicIndexService<T, Q>,
    private val pubsub: TopicPubSubService<T, V>,
    private val userPersistence: UserPersistence<T>,
    private val membershipPersistence: MembershipPersistence<T>,
    private val membershipIndex: MembershipIndexService<T, Q>,
    private val emptyDataCodec: Supplier<V>,
    private val topicNameToQuery: Function<ByStringRequest, Q>,
    private val memberOfIdToQuery: Function<ByIdRequest<T>, Q>,
    private val memberWithTopicToQuery: Function<MembershipRequest<T>, Q>,
    private val messagePersistence: MessagePersistence<T, V>,
    private val verifier: KeyVerifier<T>,
    private val rootKeys: RootKeys<T>,
) : ChatTopicService<T, V> {
    val logger: Logger = LoggerFactory.getLogger(this::class.simpleName)

    // Names are unique. The index schemas tolerate duplicate names, but the
    // product decision is that a name names one room. Enforce it here, at the
    // source, so no backend holds a duplicate and getRoomByName's single() is
    // always safe. Without this, Cassandra orphans the older room in silence
    // and memory throws a raw IllegalStateException. fp issue CHAT-qktlglfa.
    // A job topic is a system record. The vector job service writes topic
    // persistence and the topic index itself, so this method needs no
    // exception for a system caller and rejects the prefix for everyone.
    override fun addRoom(req: ByStringRequest): Mono<out Key<T>> =
        if (JobTopicNames.isJobTopic(req.name)) {
            Mono.error(ChatException("A room name cannot start with '${JobTopicNames.PREFIX}'."))
        } else {
            topicIndex
                .findBy(topicNameToQuery.apply(req))
                .hasElements()
                .flatMap { exists ->
                    if (exists) {
                        Mono.error(DuplicateException)
                    } else {
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
                    }
                }
        }

    override fun deleteRoom(req: ByIdRequest<T>): Mono<Void> =
        verifier.resolve(req.id, ChatDomain.MESSAGE_TOPIC)
            .flatMap { topicPersistence.get(it.key) }
            .flatMap {
                topicPersistence.rem(it.key)
                    .then(topicIndex.rem(it.key))
                    .then(pubsub.unSubscribeAllIn(it.key.id))
                    .then(pubsub.close(it.key.id))
            }
            .then()

    // Job topics are system records and must not reach a user room list. The
    // filter belongs here, not in the store. A rebuild reads
    // TopicPersistence.all() directly and needs every job topic for its
    // exclusion set.
    override fun listRooms(): Flux<out MessageTopic<T>> =
        topicPersistence
            .all()
            .filter { topic -> !JobTopicNames.isJobTopic(topic.data) }

    override fun getRoom(req: ByIdRequest<T>): Mono<out MessageTopic<T>> =
        verifier.resolve(req.id, ChatDomain.MESSAGE_TOPIC)
            .flatMap { topicPersistence.get(it.key) }

    // The first switchIfEmpty must sit before single(). An empty index makes
    // single() throw NoSuchElementException, which would outrun a fallback
    // placed after it. The second switchIfEmpty answers an index hit whose
    // persistence row is gone. fp issue CHAT-fplhtycq.
    override fun getRoomByName(req: ByStringRequest): Mono<out MessageTopic<T>> =
        topicIndex
            .findBy(topicNameToQuery.apply(req))
            .switchIfEmpty(Mono.error(NotFoundException))
            .single()
            .flatMap {
                topicPersistence.get(it)
            }
            .switchIfEmpty(Mono.error(NotFoundException))

    override fun joinRoom(req: MembershipRequest<T>): Mono<Void> {
        return verifier.resolve(req.roomId, ChatDomain.MESSAGE_TOPIC)
            .flatMap { topicPersistence.get(it.key) }
            .switchIfEmpty(Mono.error(NotFoundException))
            .then(membershipPersistence.key())
            .map { key -> TopicMembership.create(key.id, req.uid, req.roomId) }
            .flatMapMany { membership ->
                membershipPersistence
                    .add(membership)
                    .then(membershipIndex.add(membership))
                    // A join alert is a message. It takes its own MESSAGE key. C49.
                    .then(messagePersistence.key())
                    .flatMap { alertKey ->
                        pubsub.sendMessage(
                            JoinAlert(
                                MessageKey.of(alertKey.id, alertKey.root, req.uid, req.roomId),
                                emptyDataCodec.get()
                            )
                        )
                    }
            }
            .then(pubsub.subscribe(req.uid, req.roomId))
    }

    override fun leaveRoom(req: MembershipRequest<T>): Mono<Void> =
        membershipIndex
            .findBy(memberWithTopicToQuery.apply(req))
            .switchIfEmpty(Mono.error(NotFoundException))
            .last()
            .flatMap { key ->
                membershipPersistence.rem(key)
                    // A leave alert is a message. It takes its own MESSAGE key. C50.
                    .then(messagePersistence.key())
                    .flatMap { alertKey ->
                        pubsub.sendMessage(
                            LeaveAlert(
                                MessageKey.of(alertKey.id, alertKey.root, req.uid, req.roomId),
                                emptyDataCodec.get()
                            )
                        )
                    }
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
                    // The member id comes from a stored membership, so it takes the USER root. C51.
                    .get(Key.of(membership.member, rootKeys.of(ChatDomain.USER).id))
                    .map { user -> TopicMember(user.key.id.toString(), user.handle, user.imageUri) }
            }
            .collectList()
            .map {
                TopicMemberships(it.toSet())
            }
}