package com.demo.chatevents

import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
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
class MemoryChatTopicService : ChatTopicService, ChatTopicServiceAdmin {

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    // All [topic]s
    private val topicProcessors: MutableMap<UUID, DirectProcessor<Message<MessageKey, Any>>> = HashMap()

    private val topicFluxes: MutableMap<UUID, Flux<Message<MessageKey, Any>>> = HashMap()


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

    override fun getTopicProcessor(topicId: UUID): DirectProcessor<Message<MessageKey, Any>> =
            topicProcessors
                    .getOrPut(topicId) {
                        DirectProcessor.create()
                    }

    override fun getTopicStream(topicId: UUID): Flux<Message<MessageKey, Any>> =
            topicFluxes
                    .getOrPut(topicId) {
                        val processor: DirectProcessor<Message<MessageKey, Any>> = getTopicProcessor(topicId)
                        processor
                                .onBackpressureBuffer()
                                .publish()
                                .autoConnect()
                    }

    // how to join multiple streams to have fan-out without iterating through Fluxs
    override fun sendMessageToTopic(message: Message<MessageKey, Any>): Mono<Void> = Mono
            .create<Void> {
                topicToMembers(message.key.roomId)
                        .stream()
                        .forEach { memberId ->
                            getTopicProcessor(memberId).onNext(message)
                        }
                it.success()
            }.then()

    override fun subscribeMember(uid: UUID, topicId: UUID): Mono<Void> = Mono
            .create<Void> {
                topicToMembers(topicId).add(uid)
                memberToTopics(uid).add(topicId)
                it.success()
            }.then()

    override fun unsubscribeMember(uid: UUID, topicId: UUID): Mono<Void> = Mono
            .create<Void> {
                topicToMembers(topicId).remove(uid)
                memberToTopics(uid).remove(topicId)
                it.success()
            }.then()

    override fun unsubscribeMemberAllTopics(uid: UUID): Mono<Void> = Mono
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

    override fun unsubscribeTopicAllMembers(topicId: UUID): Mono<Void> = Mono
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
}