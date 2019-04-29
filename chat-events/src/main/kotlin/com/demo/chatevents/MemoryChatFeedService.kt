package com.demo.chatevents

import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
import com.demo.chat.service.ChatFeedService
import reactor.core.publisher.DirectProcessor
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*


class MemoryChatFeedService : ChatFeedService {
    private val feeds: MutableMap<UUID, DirectProcessor<Message<MessageKey, Any>>> = mutableMapOf()//ConcurrentHashMap()

    // map of <Destination : Subscribers>
    private val feedMembers: MutableMap<UUID, HashSet<UUID>> = mutableMapOf()

    // map of <Subscribers : Destinations>
    private val memberFeeds: MutableMap<UUID, HashSet<UUID>> = mutableMapOf()

    private fun memberToFeeds(memberId: UUID): MutableSet<UUID> =
            feedMembers.getOrPut(memberId) {
                HashSet()
            }


    fun feedToMembers(feedId: UUID): MutableSet<UUID> =
            feedMembers.getOrPut(feedId) {
                HashSet()
            }

    fun getFeed(feedId: UUID): DirectProcessor<Message<MessageKey, Any>> {
        return feeds.getOrPut(feedId) {

            val feed: DirectProcessor<Message<MessageKey, Any>> =
                    DirectProcessor.create()

            feed
                    .publish()
                    .onBackpressureBuffer()
                    .handle<Message<MessageKey, Any>> { m, sink ->
                        sink.next(m)
                    }

            return feed
        }
    }

    override fun getFeedForUser(uid: UUID): Flux<Message<MessageKey, Any>> =
            getFeed(uid)


    override fun sendMessageToFeed(message: Message<MessageKey, Any>): Mono<Void> {
        feedToMembers(message.key.roomId)
                .stream()
                .forEach {
                    getFeed(it).onNext(message)
                }

        return Mono.empty()
    }

    override fun subscribeUser(uid: UUID, feedId: UUID): Mono<Void> {
        feedToMembers(feedId).add(uid)
        memberToFeeds(uid).add(feedId)

        return Mono.empty()
    }

    override fun unsubscribeUser(uid: UUID, feedId: UUID): Mono<Void> {
        feedToMembers(feedId).remove(uid)
        memberToFeeds(uid).remove(feedId)

        return Mono.empty()
    }

    override fun unsubscribeUserAll(uid: UUID): Mono<Void> {
        memberToFeeds(uid)
                .stream()
                .forEach {
                    feedToMembers(it).remove(uid)
                }

        return Mono.empty()
    }

}