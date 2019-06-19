package com.demo.chatevents

import com.demo.chat.domain.Message
import com.demo.chat.domain.TopicMessageKey
import com.demo.chat.service.ChatTopicService
import com.demo.chat.service.ChatTopicServiceAdmin
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import reactor.core.publisher.DirectProcessor
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*

/**
 *
 * A topic is a list of members which will receive messages sent to it.
 * A member is a subscriber to a topic
 */
class ChatTopicInMemoryService : ChatTopicService, ChatTopicServiceAdmin {

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    // All [topic]s
    private val topicProcessors: MutableMap<UUID, DirectProcessor<Message<TopicMessageKey, Any>>> = HashMap()

    private val topicFluxes: MutableMap<UUID, Flux<Message<TopicMessageKey, Any>>> = HashMap()


    // map of <topic : [UserInbox]s>
    private val topicMembers: MutableMap<UUID, HashSet<UUID>> = mutableMapOf()

    // map of <UserInbox : [topic]s>
    private val memberTopics: MutableMap<UUID, HashSet<UUID>> = mutableMapOf()

    private fun memberToTopics(memberId: UUID): MutableSet<UUID> =
            memberTopics.getOrPut(memberId) {
                HashSet()
            }

    private fun topicToMembers(topicId: UUID): MutableSet<UUID> =
            topicMembers.getOrPut(topicId) {
                HashSet()
            }

    override fun getMemberTopics(uid: UUID): List<UUID> =
            memberTopics.getOrElse(uid) { HashSet() }.toList()

    override fun getTopicMembers(uid: UUID): List<UUID> =
            topicMembers.getOrElse(uid) { HashSet() }.toList()

    override fun getTopicProcessor(topicId: UUID): DirectProcessor<Message<TopicMessageKey, Any>> =
            topicProcessors
                    .getOrPut(topicId) {
                        DirectProcessor.create()
                    }

    override fun receiveTopicEvents(topicId: UUID): Flux<Message<TopicMessageKey, Any>> =
            topicFluxes
                    .getOrPut(topicId) {
                        val processor: DirectProcessor<Message<TopicMessageKey, Any>> = getTopicProcessor(topicId)
                        processor
                                .onBackpressureBuffer()
                                .publish()
                                .autoConnect()
                    }

    // how to join multiple streams to have fan-out without iterating through Fluxs
    override fun sendMessageToTopic(topicMessage: Message<TopicMessageKey, Any>): Mono<Void> = Mono
            .create<Void> {
                topicToMembers(topicMessage.key.topicId)
                        .stream()
                        .forEach { memberId ->
                            getTopicProcessor(memberId).onNext(topicMessage)
                        }
                it.success()
            }.then()

    override fun subscribeToTopic(uid: UUID, topicId: UUID): Mono<Void> = Mono
            .create<Void> {
                topicToMembers(topicId).add(uid)
                memberToTopics(uid).add(topicId)
                it.success()
            }.then()

    override fun unSubscribeFromTopic(uid: UUID, topicId: UUID): Mono<Void> = Mono
            .create<Void> {
                topicToMembers(topicId).remove(uid)
                memberToTopics(uid).remove(topicId)
                it.success()
            }.then()

    override fun unSubscribeFromAllTopics(uid: UUID): Mono<Void> = Mono
            .create<Void> {
                memberToTopics(uid)
                        .stream()
                        .forEach { id ->
                            topicToMembers(id).remove(uid)
                        }
                // ORDER MATTERS ( dont remove reference before completing stream )
                memberTopics.remove(uid)
                topicFluxes.remove(uid)
                getTopicProcessor(uid).sink().complete()
                topicProcessors.remove(uid)
                it.success()
            }.then()

    override fun kickallFromTopic(topicId: UUID): Mono<Void> = Mono
            .create<Void> {
                topicToMembers(topicId)
                        .stream()
                        .forEach { id ->
                            memberToTopics(id).remove(topicId)
                        }
                topicMembers.remove(topicId)
                getTopicProcessor(topicId).sink().complete()
                it.success()
            }.then()

    override fun closeTopic(topic: UUID): Mono<Void> = Mono.empty()
}