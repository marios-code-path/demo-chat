package com.demo.chat.security.access.composite

import com.demo.chat.domain.*
import com.demo.chat.service.composite.ChatTopicService
import org.springframework.security.access.prepost.PreAuthorize
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface TopicServiceAccess<T, V> : ChatTopicService<T, V> {

    @PreAuthorize("@chatAccess.hasAccessToDomain('MessageTopic', 'NEW')")
    override fun addRoom(req: ByStringRequest): Mono<out Key<T>>

    @PreAuthorize("@chatAccess.hasAccessToId(#req.id, 'REM')")
    override fun deleteRoom(req: ByIdRequest<T>): Mono<Void>

    @PreAuthorize("@chatAccess.hasAccessToDomain('MessageTopic', 'ALL')")
    override fun listRooms(): Flux<out MessageTopic<T>>

    @PreAuthorize("@chatAccess.hasAccessToId(#req.id, 'GET')")
    override fun getRoom(req: ByIdRequest<T>): Mono<out MessageTopic<T>>

    /**
     * **This route denies every caller, and it cannot do otherwise.**
     * `ByStringRequest` holds a name and no id, so there is no target key at
     * the check. The owner decided on 2026-10-01 that the route stays
     * fail-closed. An unguarded route would expose room reads, and no grant
     * could protect it.
     *
     * **It is unusable until `CHAT-dgjhljbl` lands.** That issue resolves a
     * name to a room key and restores a target check.
     */
    @PreAuthorize("false")
    override fun getRoomByName(req: ByStringRequest): Mono<out MessageTopic<T>>

    @PreAuthorize("@chatAccess.hasAccessToId(#req.uid, 'JOIN')")
    override fun joinRoom(req: MembershipRequest<T>): Mono<Void>

    @PreAuthorize("@chatAccess.hasAccessToId(#req.roomId, 'JOIN')")
    override fun leaveRoom(req: MembershipRequest<T>): Mono<Void>

    @PreAuthorize("@chatAccess.hasAccessToId(#req.id, 'MEMBERS')")
    override fun roomMembers(req: ByIdRequest<T>): Mono<TopicMemberships>
}