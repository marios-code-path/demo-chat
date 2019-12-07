package com.demo.chatevents.service

import com.demo.chat.domain.Message
import com.demo.chat.domain.RoomNotFoundException
import com.demo.chat.domain.TopicMessageKey
import com.demo.chat.service.ChatTopicService
import com.demo.chat.service.ChatTopicServiceAdmin
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import reactor.core.publisher.Flux
import reactor.core.publisher.FluxProcessor
import reactor.core.publisher.Mono
import reactor.core.publisher.ReplayProcessor
import reactor.core.scheduler.Schedulers
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 *
 * A topic is a list of members which will receive messages sent to it.
 * A member is a subscriber to a topic.
 * This service handles the behaviour of topic distribution without using an external
 * resource such as Redis, Kafka, Rabbit, etc...
 *
 * For now, this service is restricted to single-node bound chat-rooms, no-persistence
 */
class TopicServiceMemory : ChatTopicService, ChatTopicServiceAdmin {
    private val topicManager: TopicManager = TopicManager()

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    // map of <topic : [msgInbox]s>
    private val topicMembers: MutableMap<UUID, HashSet<UUID>> = mutableMapOf()

    // map of <msgInbox : [topic]s>
    private val memberTopics: MutableMap<UUID, HashSet<UUID>> = mutableMapOf()

    private val topicXSource: MutableMap<UUID, Flux<out Message<TopicMessageKey, Any>>> = ConcurrentHashMap()

    override fun add(id: UUID): Mono<Void> = Mono
            .fromCallable {
                topicToMembers(id)
                receiveSourcedEvents(id)
            }
            .then()

    fun topicExistsOrError(topic: UUID): Mono<Boolean> =
            exists(topic)
                    .filter { it == true }
                    .switchIfEmpty(Mono.error(RoomNotFoundException))

    override fun exists(id: UUID): Mono<Boolean> = Mono
            .fromCallable { topicXSource.containsKey(id) }

  //  override fun keyExists(topic: EventKey, id: EventKey): Mono<Boolean> = Mono.just(false)

    override fun getProcessor(id: UUID): FluxProcessor<out Message<TopicMessageKey, Any>, out Message<TopicMessageKey, Any>> =
            topicManager
                    .getTopicProcessor(id)

    override fun receiveOn(stream: UUID): Flux<out Message<TopicMessageKey, Any>> =
            topicManager
                    .getTopicFlux(stream)

    /**
     * topicManager is a subscriber to an upstream from topicXSource
     * so basically, topicManager.getTopicFlux(id) - where ReplayProcessor backs the flux.
     */
    override fun receiveSourcedEvents(id: UUID): Flux<out Message<TopicMessageKey, Any>> =
            topicXSource
                    .getOrPut(id, {
                        val proc = ReplayProcessor.create<Message<TopicMessageKey, Any>>(1)
                        topicManager.setTopicProcessor(id, proc)

                        val reader = topicManager.getTopicFlux(id)
                        topicXSource[id] = reader

                        reader
                    })

    // how to join multiple streams to have fan-out without iterating through Fluxs
    override fun sendMessage(topicMessage: Message<TopicMessageKey, Any>): Mono<Void> =
            topicExistsOrError(topicMessage.key.topicId)
                    .map {
                        topicManager
                                .getTopicProcessor(topicMessage.key.topicId)
                                .onNext(topicMessage)
                    }
                    .switchIfEmpty(Mono.error(RoomNotFoundException))
                    .then()


    override fun subscribe(uid: UUID, topicId: UUID): Mono<Void> =
            topicExistsOrError(topicId)
                    .map {
                        topicToMembers(topicId).add(uid)
                        memberToTopics(uid).add(topicId)
                        topicManager.subscribeTopic(topicId, uid)
                    }
                    .then()

    override fun unSubscribe(uid: UUID, id: UUID): Mono<Void> = Mono
            .fromCallable {
                topicToMembers(id).remove(uid)
                memberToTopics(uid).remove(id)
                topicManager
                        .quitTopic(id, uid)
            }.then()

    override fun unSubscribeAll(uid: UUID): Mono<Void> =
            Flux.fromIterable(memberToTopics(uid))
                    .flatMap { topic ->
                        unSubscribe(uid, topic)
                    }
                    .subscribeOn(Schedulers.parallel())
                    .then()

    override fun unSubscribeAllIn(id: UUID): Mono<Void> = Flux
            .fromIterable(topicToMembers(id))
            .flatMap { member ->
                unSubscribe(member, id)
            }
            .subscribeOn(Schedulers.parallel())
            .then()

    override fun rem(id: UUID): Mono<Void> = Mono
            .fromCallable {
                topicManager.closeTopic(id)
            }.then()

    override fun getTopicsByUser(uid: UUID): Flux<UUID> = Flux.fromIterable(memberToTopics(uid))

    override fun getUsersBy(id: UUID): Flux<UUID> = Flux.fromIterable(topicToMembers(id))

    private fun memberToTopics(memberId: UUID): MutableSet<UUID> =
            memberTopics.getOrPut(memberId) {
                HashSet()
            }

    private fun topicToMembers(topicId: UUID): MutableSet<UUID> =
            topicMembers.getOrPut(topicId) {
                HashSet()
            }
}