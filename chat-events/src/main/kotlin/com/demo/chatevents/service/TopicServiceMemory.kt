package com.demo.chatevents.service

import com.demo.chat.domain.*
import com.demo.chat.service.ChatTopicService
import com.demo.chat.service.ChatTopicServiceAdmin
import com.demo.chatevents.topic.TopicManager
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import reactor.core.publisher.*
import reactor.core.scheduler.Schedulers
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 *
 * A topic is a list of members which will receive messages sent to it.
 * A member is a subscriber to a topic
 */
class TopicServiceMemory : ChatTopicService, ChatTopicServiceAdmin {
    private val topicManager: TopicManager = TopicManager()
    private val sourceManager: TopicManager = TopicManager()

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    // map of <topic : [UserInbox]s>
    private val topicMembers: MutableMap<UUID, HashSet<UUID>> = mutableMapOf()

    // map of <UserInbox : [topic]s>
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
            .fromCallable { topicMembers.containsKey(id) }

    override fun getProcessor(id: UUID): FluxProcessor<out Message<TopicMessageKey, Any>, out Message<TopicMessageKey, Any>> =
            topicManager
                    .getTopicProcessor(id)

    override fun receiveOn(stream: UUID): Flux<out Message<TopicMessageKey, Any>> =
            topicManager
                    .getTopicFlux(stream)

    /**
     * Stream Manager becomes subscriber to an upstream from topicXSource
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


    override fun getTopicsByUser(uid: UUID): Flux<UUID> = Flux.fromIterable(
            memberToTopics(uid)
    )

    override fun getUsersBy(id: UUID): Flux<UUID> = Flux.fromIterable(
            topicToMembers(id)
    )

    private fun memberToTopics(memberId: UUID): MutableSet<UUID> =
            memberTopics.getOrPut(memberId) {
                HashSet()
            }

    private fun topicToMembers(topicId: UUID): MutableSet<UUID> =
            topicMembers.getOrPut(topicId) {
                HashSet()
            }

}