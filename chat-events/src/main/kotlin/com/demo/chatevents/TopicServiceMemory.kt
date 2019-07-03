package com.demo.chatevents

import com.demo.chat.domain.*
import com.demo.chat.service.ChatTopicService
import com.demo.chat.service.ChatTopicServiceAdmin
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import reactor.core.publisher.DirectProcessor
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.util.*

/**
 *
 * A topic is a list of members which will receive messages sent to it.
 * A member is a subscriber to a topic
 */
class TopicServiceMemory : ChatTopicService, ChatTopicServiceAdmin {
    private val streamManager: StreamManager = StreamManager()

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    // map of <topic : [UserInbox]s>
    private val topicMembers: MutableMap<UUID, HashSet<UUID>> = mutableMapOf()

    // map of <UserInbox : [topic]s>
    private val memberTopics: MutableMap<UUID, HashSet<UUID>> = mutableMapOf()


    override fun createTopic(topicId: UUID): Mono<Void> = Mono
            .fromCallable {
                topicToMembers(topicId)
                streamManager.getStreamProcessor(topicId)
            }
            .then()


    override fun topicExists(topic: UUID): Mono<Boolean> = Mono
            .fromCallable { topicMembers.containsKey(topic) }

    override fun getTopicProcessor(topicId: UUID): DirectProcessor<Message<TopicMessageKey, Any>> =
            streamManager
                    .getStreamProcessor(topicId)

    override fun receiveEvents(stream: UUID): Flux<Message<TopicMessageKey, Any>> =
            streamManager
                    .getStreamFlux(stream)

    // how to join multiple streams to have fan-out without iterating through Fluxs
    override fun sendMessageToTopic(topicMessage: Message<TopicMessageKey, Any>): Mono<Void> =
            topicExists(topicMessage.key.topicId)
                    .filter { it == true }
                    .map {
                        streamManager
                                .getStreamProcessor(topicMessage.key.topicId)
                                .onNext(topicMessage)
                    }
                    .switchIfEmpty(Mono.error(RoomNotFoundException))
                    .then()


    override fun subscribeToTopic(uid: UUID, topicId: UUID): Mono<Void> =
            topicExists(topicId)
                    .filter { it == true }
                    .map {
                        topicToMembers(topicId).add(uid)
                        memberToTopics(uid).add(topicId)
                        streamManager.consumeStream(topicId, uid)
                    }
                    .switchIfEmpty(Mono.error(RoomNotFoundException))
                    .then()

    override fun unSubscribeFromTopic(uid: UUID, topic: UUID): Mono<Void> = Mono
            .fromCallable {
                topicToMembers(topic).remove(uid)
                memberToTopics(uid).remove(topic)
                streamManager
                        .disconnectFromStream(topic, uid)
            }.flatMap {
                sendMessageToTopic(LeaveAlert.create(
                        UUID.randomUUID(),
                        topic,
                        uid
                ))
            }

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