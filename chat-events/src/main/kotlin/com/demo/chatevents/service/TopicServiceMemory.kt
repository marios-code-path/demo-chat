package com.demo.chatevents.service

import com.demo.chat.domain.Message
import com.demo.chat.domain.TopicNotFoundException
import com.demo.chat.service.ChatTopicService
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
class TopicServiceMemory<T, V> : ChatTopicService<T, V> {
    private val topicManager: TopicManager<T, V> = TopicManager()

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    // map of <topic : [msgInbox]s>
    private val topicMembers: MutableMap<T, HashSet<T>> = mutableMapOf()

    // map of <msgInbox : [topic]s>
    private val memberTopics: MutableMap<T, HashSet<T>> = mutableMapOf()

    private val topicXSource: MutableMap<T, Flux<out Message<T, out V>>> = ConcurrentHashMap()

    override fun add(id: T): Mono<Void> = Mono
            .fromCallable {
                topicToMembers(id)
                receiveSourcedEvents(id)
            }
            .then()

    fun topicExistsOrError(topic: T): Mono<Boolean> =
            exists(topic)
                    .filter { it == true }
                    .switchIfEmpty(Mono.error(TopicNotFoundException))

    override fun exists(id: T): Mono<Boolean> = Mono
            .fromCallable { topicXSource.containsKey(id) }

  //  override fun keyExists(topic: EventKey, id: EventKey): Mono<Boolean> = Mono.just(false)

    override fun getProcessor(id: T): FluxProcessor<out Message<T, out V>, out Message<T, out V>> =
            topicManager
                    .getTopicProcessor(id)

    override fun receiveOn(stream: T): Flux<out Message<T, out V>> =
            topicManager
                    .getTopicFlux(stream)

    /**
     * topicManager is a subscriber to an upstream from topicXSource
     * so basically, topicManager.getTopicFlux(id) - where ReplayProcessor backs the flux.
     */
    override fun receiveSourcedEvents(id: T): Flux<out Message<T, out V>> =
            topicXSource
                    .getOrPut(id, {
                        val proc = ReplayProcessor.create<Message<T, out V>>(1)
                        topicManager.setTopicProcessor(id, proc)

                        val reader = topicManager.getTopicFlux(id)
                        topicXSource[id] = reader

                        reader
                    })

    // how to join multiple streams to have fan-out without iterating through Fluxs
    override fun sendMessage(topicMessage: Message<T, out V>): Mono<Void>  {
        val dest = topicMessage.key.dest

        return topicExistsOrError(dest)
                .map {
                    topicManager
                            .getTopicProcessor(dest)
                            .onNext(topicMessage)
                }
                .switchIfEmpty(Mono.error(TopicNotFoundException))
                .then()
    }

    override fun subscribe(uid: T, topicId: T): Mono<Void> =
            topicExistsOrError(topicId)
                    .map {
                        topicToMembers(topicId).add(uid)
                        memberToTopics(uid).add(topicId)
                        topicManager.subscribeTopic(topicId, uid)
                    }
                    .then()

    override fun unSubscribe(uid: T, id: T): Mono<Void> = Mono
            .fromCallable {
                topicToMembers(id).remove(uid)
                memberToTopics(uid).remove(id)
                topicManager
                        .quitTopic(id, uid)
            }.then()

    override fun unSubscribeAll(uid: T): Mono<Void> =
            Flux.fromIterable(memberToTopics(uid))
                    .flatMap { topic ->
                        unSubscribe(uid, topic)
                    }
                    .subscribeOn(Schedulers.parallel())
                    .then()

    override fun unSubscribeAllIn(id: T): Mono<Void> = Flux
            .fromIterable(topicToMembers(id))
            .flatMap { member ->
                unSubscribe(member, id)
            }
            .subscribeOn(Schedulers.parallel())
            .then()

    override fun rem(id: T): Mono<Void> = Mono
            .fromCallable {
                topicManager.closeTopic(id)
            }.then()

    override fun getByUser(uid: T): Flux<T> = Flux.fromIterable(memberToTopics(uid))

    override fun getUsersBy(id: T): Flux<T> = Flux.fromIterable(topicToMembers(id))

    private fun memberToTopics(memberId: T): MutableSet<T> =
            memberTopics.getOrPut(memberId) {
                HashSet()
            }

    private fun topicToMembers(topicId: T): MutableSet<T> =
            topicMembers.getOrPut(topicId) {
                HashSet()
            }
}