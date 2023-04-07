package com.demo.chat.service.composite

import com.demo.chat.domain.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface ChatTopicService<T, V> {
    fun addRoom(req: ByStringRequest): Mono<out Key<T>>
    fun deleteRoom(req: ByIdRequest<T>): Mono<Void>
    fun listRooms(): Flux<out MessageTopic<T>>
    fun getRoom(req: ByIdRequest<T>): Mono<out MessageTopic<T>>
    fun getRoomByName(req: ByStringRequest): Mono<out MessageTopic<T>>
    fun joinRoom(req: MembershipRequest<T>): Mono<Void>
    fun leaveRoom(req: MembershipRequest<T>): Mono<Void>
    fun roomMembers(req: ByIdRequest<T>): Mono<TopicMemberships>
}