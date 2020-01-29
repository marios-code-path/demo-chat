package com.demo.chatevents.service

import com.demo.chat.domain.Message
import com.demo.chat.domain.TopicNotFoundException
import com.demo.chat.service.ChatTopicMessagingService
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
class TopicMessagingServiceMemory<T, V> : ChatTopicMessagingService<T, V> {
    private val topicManager: TopicManager<T, V> = TopicManager()

    // map of <topic : [msgInbox]s>
    private val topicMembers: MutableMap<T, HashSet<T>> = mutableMapOf()

    // map of <msgInbox : [topic]s>
    private val memberTopics: MutableMap<T, HashSet<T>> = mutableMapOf()

    private val topicXSource: MutableMap<T, Flux<out Message<T, V>>> = ConcurrentHashMap()

    override fun add(id: T): Mono<Void> = Mono
            .fromCallable {
                topicToMembers(id)
                receiveSourcedEvents(id)
            }
            .then()

    private fun topicExistsOrError(topic: T): Mono<Boolean> =
            exists(topic)
                    .filter { it == true }
                    .switchIfEmpty(Mono.error(TopicNotFoundException))

    override fun exists(topic: T): Mono<Boolean> = Mono
            .fromCallable { topicXSource.containsKey(topic) }

    //  override fun keyExists(topic: EventKey, id: EventKey): Mono<Boolean> = Mono.just(false)

    override fun getProcessor(id: T): FluxProcessor<out Message<T, V>, out Message<T, V>> =
            topicManager
                    .getTopicProcessor(id)

    override fun receiveOn(topic: T): Flux<out Message<T, V>> =
            topicManager
                    .getTopicFlux(topic)

    /**
     * topicManager is a subscriber to an upstream from topicXSource
     * so basically, topicManager.getTopicFlux(id) - where ReplayProcessor backs the flux.
     */
    override fun receiveSourcedEvents(topic: T): Flux<out Message<T, V>> =
            topicXSource
                    .getOrPut(topic, {
                        val proc = ReplayProcessor.create<Message<T, V>>(1)
                        topicManager.setTopicProcessor(topic, proc)

                        val reader = topicManager.getTopicFlux(topic)
                        topicXSource[topic] = reader

                        reader
                    })

    // how to join multiple streams to have fan-out without iterating through Fluxs
    override fun sendMessage(message: Message<T, V>): Mono<Void> {
        val dest = message.key.dest

        return topicExistsOrError(dest)
                .map {
                    topicManager
                            .getTopicProcessor(dest)
                            .onNext(message)
                }
                .switchIfEmpty(Mono.error(TopicNotFoundException))
                .then()
    }

    override fun subscribe(member: T, topic: T): Mono<Void> =
            topicExistsOrError(topic)
                    .map {
                        topicToMembers(topic).add(member)
                        memberToTopics(member).add(topic)
                        topicManager.subscribeTopic(topic, member)
                    }
                    .then()

    override fun unSubscribe(member: T, topic: T): Mono<Void> = Mono
            .fromCallable {
                topicToMembers(topic).remove(member)
                memberToTopics(member).remove(topic)
                topicManager
                        .quitTopic(topic, member)
            }.then()

    override fun unSubscribeAll(member: T): Mono<Void> =
            Flux.fromIterable(memberToTopics(member))
                    .flatMap { topic ->
                        unSubscribe(member, topic)
                    }
                    .subscribeOn(Schedulers.parallel())
                    .then()

    override fun unSubscribeAllIn(topic: T): Mono<Void> = Flux
            .fromIterable(topicToMembers(topic))
            .flatMap { member ->
                unSubscribe(member, topic)
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