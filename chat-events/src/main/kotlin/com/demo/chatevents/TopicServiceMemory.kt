package com.demo.chatevents

import com.demo.chat.domain.*
import com.demo.chat.service.ChatTopicService
import com.demo.chat.service.ChatTopicServiceAdmin
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
    private val streamManager: StreamManager = StreamManager()
    private val sourceManager: StreamManager = StreamManager()

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    // map of <topic : [UserInbox]s>
    private val topicMembers: MutableMap<UUID, HashSet<UUID>> = mutableMapOf()

    // map of <UserInbox : [topic]s>
    private val memberTopics: MutableMap<UUID, HashSet<UUID>> = mutableMapOf()

    private val topicXSource: MutableMap<UUID, Flux<out Message<TopicMessageKey, Any>>> = ConcurrentHashMap()

    override fun createTopic(topicId: UUID): Mono<Void> = Mono
            .fromCallable {
                topicToMembers(topicId)
                receiveSourcedEvents(topicId)
            }
            .then()

    fun topicExistsOrError(topic: UUID): Mono<Boolean> =
            topicExists(topic)
                    .filter { it == true }
                    .switchIfEmpty(Mono.error(RoomNotFoundException))

    override fun topicExists(topic: UUID): Mono<Boolean> = Mono
            .fromCallable { topicMembers.containsKey(topic) }

    override fun getStreamProcessor(topicId: UUID): FluxProcessor<out Message<TopicMessageKey, Any>, out Message<TopicMessageKey, Any>> =
            streamManager
                    .getStreamProcessor(topicId)

    override fun receiveEvents(stream: UUID): Flux<Message<TopicMessageKey, Any>> =
            streamManager
                    .getStreamFlux(stream)

    /**
     * Stream Manager becomes subscriber to an upstream from topicXSource
     */
    override fun receiveSourcedEvents(streamId: UUID): Flux<out Message<TopicMessageKey, Any>> =
            topicXSource
                    .getOrPut(streamId, {
                        val proc = ReplayProcessor.create<Message<TopicMessageKey, Any>>(1)
                        streamManager.setStreamProcessor(streamId, proc)

                        val reader = streamManager.getStreamFlux(streamId)
                        topicXSource[streamId] = reader

                        reader
                    })

    // how to join multiple streams to have fan-out without iterating through Fluxs
    override fun sendMessageToTopic(topicMessage: Message<TopicMessageKey, Any>): Mono<Void> =
            topicExistsOrError(topicMessage.key.topicId)
                    .map {
                        streamManager
                                .getStreamProcessor(topicMessage.key.topicId)
                                .onNext(topicMessage)
                    }
                    .switchIfEmpty(Mono.error(RoomNotFoundException))
                    .then()


    override fun subscribeToTopic(uid: UUID, topicId: UUID): Mono<Void> =
            topicExistsOrError(topicId)
                    .map {
                        topicToMembers(topicId).add(uid)
                        memberToTopics(uid).add(topicId)
                        streamManager.consumeStream(topicId, uid)
                    }
                    .then()

    override fun unSubscribeFromTopic(uid: UUID, topic: UUID): Mono<Void> = Mono
            .fromCallable {
                topicToMembers(topic).remove(uid)
                memberToTopics(uid).remove(topic)
                streamManager
                        .disconnectFromStream(topic, uid)
            }.then()

    override fun unSubscribeFromAllTopics(uid: UUID): Mono<Void> =
            Flux.fromIterable(memberToTopics(uid))
                    .flatMap { topic ->
                        unSubscribeFromTopic(uid, topic)
                    }
                    .subscribeOn(Schedulers.parallel())
                    .then()

    override fun kickallFromTopic(topic: UUID): Mono<Void> = Flux
            .fromIterable(topicToMembers(topic))
            .flatMap { member ->
                unSubscribeFromTopic(member, topic)
            }
            .subscribeOn(Schedulers.parallel())
            .then()

    override fun closeTopic(topic: UUID): Mono<Void> = Mono
            .fromCallable {
                streamManager.closeStream(topic)
            }.then()


    override fun getMemberTopics(uid: UUID): Flux<UUID> = Flux.fromIterable(
            memberToTopics(uid)
    )

    override fun getTopicMembers(topic: UUID): Flux<UUID> = Flux.fromIterable(
            topicToMembers(topic)
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