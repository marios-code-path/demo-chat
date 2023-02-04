package com.demo.chat.controller.composite.mapping

import com.demo.chat.domain.ByIdRequest
import com.demo.chat.domain.ByNameRequest
import com.demo.chat.domain.MembershipRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.MessageTopic
import com.demo.chat.domain.TopicMemberships
import com.demo.chat.service.composite.ChatTopicService
import org.springframework.messaging.handler.annotation.MessageMapping
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface ChatTopicServiceMapping<T, V>: ChatTopicService<T, V> {
    @MessageMapping("topic-add")
    override fun addRoom(req: ByNameRequest): Mono<out Key<T>>
    @MessageMapping("topic-rem")
    override fun deleteRoom(req: ByIdRequest<T>): Mono<Void>
    @MessageMapping("topic-list")
    override fun listRooms(): Flux<out MessageTopic<T>>
    @MessageMapping("topic-by-id")
    override fun getRoom(req: ByIdRequest<T>): Mono<out MessageTopic<T>>
    @MessageMapping("topic-by-name")
    override fun getRoomByName(req: ByNameRequest): Mono<out MessageTopic<T>>
    @MessageMapping("topic-join")
    override fun joinRoom(req: MembershipRequest<T>): Mono<Void>
    @MessageMapping("topic-leave")
    override fun leaveRoom(req: MembershipRequest<T>): Mono<Void>
    @MessageMapping("topic-members")
    override fun roomMembers(req: ByIdRequest<T>): Mono<TopicMemberships>
}