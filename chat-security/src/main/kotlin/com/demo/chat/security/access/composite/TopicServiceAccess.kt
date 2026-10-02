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

    @PreAuthorize("@chatAccess.hasAccessToDomain('MessageTopic', 'GET_ALL')")
    override fun listRooms(): Flux<out MessageTopic<T>>

    @PreAuthorize("@chatAccess.hasAccessToId(#req.id, 'GET')")
    override fun getRoom(req: ByIdRequest<T>): Mono<out MessageTopic<T>>

    /**
     * **This route carries no access expression, and that is the owner's
     * decision of 2026-10-02.**
     *
     * `ByStringRequest` holds a name and no id. So no target key exists at the
     * check, and no honest check can run here. The route answers every caller
     * that matches a room.
     *
     * **The answer is minimal.** `MessageTopic` carries the room key and the
     * full room name. It carries nothing else. The access condition travels
     * with the operation the caller runs next, and every such operation holds
     * its own check.
     *
     * The explicit deny is gone. It made the route unusable, and it refused
     * the four shell call sites that look a room up by name.
     *
     * **A later filter is a separate decision.** The owner named an
     * 'after-fetch' authorization as the alternative. It filters after the
     * fetch, for a target whose key is not yet known. That work is not part of
     * this route, and the owner ranks it below the first production release.
     */
    override fun getRoomByName(req: ByStringRequest): Mono<out MessageTopic<T>>

    @PreAuthorize("@chatAccess.hasAccessToId(#req.uid, 'JOIN')")
    override fun joinRoom(req: MembershipRequest<T>): Mono<Void>

    @PreAuthorize("@chatAccess.hasAccessToId(#req.roomId, 'JOIN')")
    override fun leaveRoom(req: MembershipRequest<T>): Mono<Void>

    @PreAuthorize("@chatAccess.hasAccessToId(#req.id, 'MEMBERS')")
    override fun roomMembers(req: ByIdRequest<T>): Mono<TopicMemberships>
}