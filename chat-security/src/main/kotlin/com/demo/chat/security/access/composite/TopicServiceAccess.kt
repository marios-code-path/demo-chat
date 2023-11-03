package com.demo.chat.security.access.composite

import com.demo.chat.domain.*
import com.demo.chat.service.composite.ChatTopicService
import org.springframework.security.access.prepost.PreAuthorize
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface TopicServiceAccess<T, V> : ChatTopicService<T, V> {

    @PreAuthorize("@chatAccess.hasAccessToDomain('MessageTopic', 'NEW')")
    override fun addRoom(req: ByStringRequest): Mono<out Key<T>>

    @PreAuthorize("@chatAccess.hasAccessTo(#req.component1(), 'REM')")
    override fun deleteRoom(req: ByIdRequest<T>): Mono<Void>

    @PreAuthorize("@chatAccess.hasAccessToDomain('MessageTopic', 'ALL')")
    override fun listRooms(): Flux<out MessageTopic<T>>

    @PreAuthorize("@chatAccess.hasAccessTo(#req.component1(), 'GET')")
    override fun getRoom(req: ByIdRequest<T>): Mono<out MessageTopic<T>>

    @PreAuthorize("@chatAccess.hasAccessTo(#req.component1(), 'GET')")
    override fun getRoomByName(req: ByStringRequest): Mono<out MessageTopic<T>>

    @PreAuthorize("@chatAccess.hasAccessTo(#req.uid(), 'JOIN')")
    override fun joinRoom(req: MembershipRequest<T>): Mono<Void>

    @PreAuthorize("@chatAccess.hasAccessTo(#req.roomId(), 'JOIN')")
    override fun leaveRoom(req: MembershipRequest<T>): Mono<Void>

    @PreAuthorize("@chatAccess.hasAccessTo(#req.component1(), 'MEMBERS')")
    override fun roomMembers(req: ByIdRequest<T>): Mono<TopicMemberships>
}