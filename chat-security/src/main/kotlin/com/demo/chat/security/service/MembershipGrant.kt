package com.demo.chat.security.service

import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.Key
import com.demo.chat.domain.TypeUtil
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.service.security.AuthorizationService
import com.demo.chat.service.security.RoomMemberGrant
import com.demo.chat.service.security.policy.ActionContext
import com.demo.chat.service.security.policy.ShippedGrantPolicies
import reactor.core.publisher.Mono
import java.time.Clock

/**
 * The writer of the membership rows: `SEND` and `SUBSCRIBE`. See
 * [RoomMemberGrant], `CHAT-mfveaecc` and `CHAT-lfaajjcj`.
 *
 * **This class applies a named policy.** A join applies
 * `ShippedGrantPolicies.ROOM_JOIN`, and a leave applies
 * `ShippedGrantPolicies.ROOM_LEAVE`. [PolicyGrantWriter] holds the storage
 * rules. See `CHAT-qojwcatx`.
 *
 * **The member of the request is the active principal.** A caller that holds
 * `JOIN` on another member can join that member, and the member gets the rows.
 *
 * **The `Anon` key and the `User` root get no row.** Both policies name them in
 * their no-grant set.
 *
 * **The owner row is not touched.** Both policies name `SEND` and
 * `SUBSCRIBE`. The owner row names `*`, and level 1 of the rank keeps it above
 * both. So an owner who leaves can still send and listen.
 */
class MembershipGrant<T>(
    authorizationService: AuthorizationService<T, AuthMetadata<T>>,
    rootKeys: RootKeys<T>,
    typeUtil: TypeUtil<T>,
    clock: Clock = Clock.systemUTC(),
    private val writer: PolicyGrantWriter<T> = PolicyGrantWriter(
        authorizationService, rootKeys, typeUtil,
        listOf(ShippedGrantPolicies.ROOM_JOIN, ShippedGrantPolicies.ROOM_LEAVE), clock,
    ),
) : RoomMemberGrant<T> {

    override fun grantMembership(member: Key<T>, room: Key<T>): Mono<Void> =
        writer.apply(ShippedGrantPolicies.ROOM_JOIN, ActionContext(member, room))

    override fun expireMembership(member: Key<T>, room: Key<T>): Mono<Void> =
        writer.apply(ShippedGrantPolicies.ROOM_LEAVE, ActionContext(member, room))
}
