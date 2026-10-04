package com.demo.chat.controller.webflux.composite.mapping

import com.demo.chat.controller.webflux.resolve.Resolved
import com.demo.chat.service.core.VerifiedKey
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.domain.*
import com.demo.chat.security.ChatUserDetails
import com.demo.chat.service.composite.ChatTopicService
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * Each path id resolves through the registry before the service runs. See `CHAT-avduuqwp`, D2.
 *
 * **The routing annotations sit here, and not on the controller alone.** Method
 * security proxies a controller that carries an annotated supertype. A JDK
 * dynamic proxy carries the interfaces and not the implementation class, so a
 * class level `@RequestMapping("/topic")` is invisible to
 * `RequestMappingHandlerMapping` and every route answers 404. See `CHAT-znprrzhn`.
 *
 * **Which proxy applies depends on the context.** A `@WebFluxTest` slice makes a
 * JDK proxy, and the slice answered 404 on 2026-10-01. A real launch makes a
 * CGLIB proxy, because Spring Boot forces class proxies, so its routes answered.
 * `spring.aop.proxy-target-class=false` forces a JDK proxy in a real launch, and
 * then every route answered 404. Measured on 2026-10-04. See `CHAT-pjymtozd`.
 * The annotations sit here so that the routes hold under both proxies.
 *
 * **The five facade methods carry their own check.** A facade method calls its
 * member on the same object. That call crosses no proxy, so the member's
 * `@PreAuthorize` never runs, and a denied caller reached the service. Measured
 * on 2026-10-01, under a JDK proxy and under CGLIB. The check repeats here for
 * that reason. See `CHAT-znprrzhn`.
 *
 * **This was the defect that a real launch showed.** Before the checks moved
 * here, a delete by an identity with no `REM` grant reached the service and
 * answered 204. Measured on 2026-10-04 at `447a1312`. See `CHAT-pjymtozd`.
 */
@RestController
@RequestMapping("/topic")
interface ChatTopicServiceRestMapping<T> : ChatTopicService<T, String> {

    @PostMapping(
        "/new", consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @ResponseStatus(HttpStatus.CREATED)
    override fun addRoom(@RequestBody req: ByStringRequest): Mono<out Key<T>>

    @GetMapping("/list", produces = [MediaType.APPLICATION_JSON_VALUE])
    override fun listRooms(): Flux<out MessageTopic<T>>

    @GetMapping("/name/{name}", produces = [MediaType.APPLICATION_JSON_VALUE])
    override fun getRoomByName(@ModelAttribute req: ByStringRequest): Mono<out MessageTopic<T>>

    @GetMapping("/members/{id}", produces = [MediaType.APPLICATION_JSON_VALUE])
    @PreAuthorize("@chatAccess.hasAccessToId(#id.key.id, 'MEMBERS')")
    fun restRoomMembers(@Resolved(ChatDomain.MESSAGE_TOPIC) id: VerifiedKey<T>): Mono<TopicMemberships> =
        roomMembers(ByIdRequest(id.key.id))

    @PutMapping("/leave/{id}")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("@chatAccess.hasAccessToId(#id.key.id, 'JOIN')")
    fun leaveRestRoom(@Resolved(ChatDomain.MESSAGE_TOPIC) id: VerifiedKey<T>, @AuthenticationPrincipal user: ChatUserDetails<T>): Mono<Void> =
        leaveRoom(MembershipRequest(user.user.key.id, id.key.id))

    @PutMapping("/join/{id}")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("@chatAccess.hasAccessToId(#id.key.id, 'JOIN')")
    fun joinRestRoom(@Resolved(ChatDomain.MESSAGE_TOPIC) id: VerifiedKey<T>, @AuthenticationPrincipal user: ChatUserDetails<T>): Mono<Void> =
        joinRoom(MembershipRequest(user.user.key.id, id.key.id))

    @GetMapping("/id/{id}", produces = [MediaType.APPLICATION_JSON_VALUE])
    @PreAuthorize("@chatAccess.hasAccessToId(#id.key.id, 'GET')")
    fun restGetRoom(@Resolved(ChatDomain.MESSAGE_TOPIC) id: VerifiedKey<T>): Mono<out MessageTopic<T>> =
        getRoom(ByIdRequest(id.key.id))

    @DeleteMapping("/id/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@chatAccess.hasAccessToId(#id.key.id, 'REM')")
    fun restDeleteRoom(@Resolved(ChatDomain.MESSAGE_TOPIC) id: VerifiedKey<T>): Mono<Void> =
        deleteRoom(ByIdRequest(id.key.id))

}