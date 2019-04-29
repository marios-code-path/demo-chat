package com.demo.chatevents

import com.demo.chat.domain.*
import com.demo.chat.service.ChatFeedService
import com.demo.chat.service.ChatService
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*

class RedisChatFeedService(val stringCache: ReactiveStringRedisTemplate,
                           val backend: ChatService<Room<RoomKey>, User<UserKey>, TextMessage>) : ChatFeedService {

    override fun getFeedForUser(uid: UUID): Flux<Message<MessageKey, Any>> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun subscribeUser(uid: UUID, feedId: UUID): Mono<Void> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun unsubscribeUser(uid: UUID, feedId: UUID): Mono<Void> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun unsubscribeUserAll(uid: UUID): Mono<Void> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun sendMessageToFeed(message: Message<MessageKey, Any>): Mono<Void> {
        TODO("not implemented")
    }

}