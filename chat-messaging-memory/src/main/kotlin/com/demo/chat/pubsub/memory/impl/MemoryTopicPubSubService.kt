package com.demo.chat.pubsub.memory.impl

import com.demo.chat.domain.Message
import com.demo.chat.domain.NotFoundException
import com.demo.chat.service.core.TopicIndexService
import com.demo.chat.service.core.TopicPubSubService
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.ReplayProcessor
import reactor.core.scheduler.Schedulers
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Memory implementation of PubSubService.
 *
 * This service handles the behaviour of topic record keeping and
 * message distribution using memory as storage.
 *
 * TODO rewrite ti incorporate topicIndex/topicPersistence !
 * */
class MemoryTopicPubSubService<T, V> : TopicPubSubService<T, V> {
    private val streamMgr: ExampleReactiveStreamManager<T, V> = ExampleReactiveStreamManager()

    // map of <topic : [msgInbox]s>
    private val topicMembers: MutableMap<T, HashSet<T>> = mutableMapOf()

    // map of <msgInbox : [topic]s>
    private val memberTopics: MutableMap<T, HashSet<T>> = mutableMapOf()

    private val sinkByTopic: MutableMap<T, Flux<out Message<T, V>>> = ConcurrentHashMap()

    override fun open(topicId: T): Mono<Void> = Mono.create { sink ->
        topicToMembers(topicId)
        sourceOf(topicId)

        sink.success()
    }

    private fun topicExistsOrError(topic: T): Mono<Boolean> =
            exists(topic)
                    .filter { it == true }
                    .switchIfEmpty(Mono.error(NotFoundException))

    override fun exists(topic: T): Mono<Boolean> = Mono
            .fromCallable { sinkByTopic.containsKey(topic) }

    //  override fun keyExists(topic: EventKey, id: EventKey): Mono<Boolean> = Mono.just(false)

    override fun listenTo(topic: T): Flux<out Message<T, V>> =
            streamMgr
                    .getSink(topic)

    /**
     * topicManager is a subscriber to an upstream from topicXSource
     * so basically, topicManager.getTopicFlux(id) - where ReplayProcessor backs the flux.
     */
    private fun sourceOf(topic: T): Flux<out Message<T, V>> =
            sinkByTopic
                    .getOrPut(topic) {
                        val proc = ReplayProcessor.create<Message<T, V>>(1)
                        streamMgr.setSource(topic, proc)

                        val reader = streamMgr.getSink(topic)

                        reader
                    }

    override fun sendMessage(message: Message<T, V>): Mono<Void> {
        val dest = message.key.dest

        return topicExistsOrError(dest)
                .map {
                    streamMgr
                            .getSource(dest)
                            .onNext(message)
                }
                .switchIfEmpty(Mono.error(NotFoundException))
                .then()
    }

    override fun subscribe(member: T, topic: T): Mono<Void> =
            topicExistsOrError(topic)
                    .map {
                        topicToMembers(topic).add(member)
                        memberToTopics(member).add(topic)
                        streamMgr.subscribe(topic, member)
                    }
                    .then()

    override fun unSubscribe(member: T, topic: T): Mono<Void> = Mono
            .fromCallable {
                topicToMembers(topic).remove(member)
                memberToTopics(member).remove(topic)
                streamMgr
                        .unsubscribe(topic, member)
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

    override fun close(topicId: T): Mono<Void> = Mono
            .fromCallable {
                streamMgr.close(topicId)
            }.then()

    override fun getByUser(uid: T): Flux<T> = Flux.fromIterable(memberToTopics(uid))

    override fun getUsersBy(topicId: T): Flux<T> = Flux.fromIterable(topicToMembers(topicId))

    private fun memberToTopics(memberId: T): MutableSet<T> =
            memberTopics.getOrPut(memberId) {
                HashSet()
            }

    private fun topicToMembers(topicId: T): MutableSet<T> =
            topicMembers.getOrPut(topicId) {
                HashSet()
            }
}