package com.demo.chat.security.service

import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.Key
import com.demo.chat.domain.TypeUtil
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.security.access.ContextIdentity
import com.demo.chat.service.security.AuthorizationService
import com.demo.chat.service.security.RoomOwnerGrant
import com.demo.chat.service.security.policy.ActionContext
import com.demo.chat.service.security.policy.ShippedGrantPolicies
import reactor.core.publisher.Mono

/**
 * The one writer of a room ownership row.
 *
 * **This class applies a named policy.** It applies
 * `ShippedGrantPolicies.ROOM_OWNER`, and [PolicyGrantWriter] holds the storage
 * rules. See `CHAT-qojwcatx`.
 *
 * **`*` is singular per target, so one writer is required.** Two writers would
 * give one room two owners, and the rank would then answer by time alone. See
 * `CHAT-zhjltbky`.
 *
 * **The caller of the security context is the active principal.** A caller
 * with no identity owns no room. `ContextIdentity` answers an empty `Mono` for
 * a denied caller, so the policy never runs. That is not an error.
 *
 * **An anonymous caller owns no room either.** `ContextIdentity` rule 4 answers
 * the `Anon` root key for an `AnonymousAuthenticationToken`, and the RSocket
 * server seam installs that token. The policy names `Anon` in its no-grant set.
 *
 * The `Anon` key is in the actor set of every query. An ownership row for it
 * would therefore make every caller the owner of the room. A close could not
 * reach that row, because an identity is an `ENTITY` and a close names a
 * `DOMAIN_ROOT`, so level 2 of the rank would keep the row. See
 * `docs/ANONYMOUS-AUTHORIZATION.md`.
 *
 * **A repeat by the same owner writes nothing.** The writer finds the live
 * owner row and keeps it. An expired owner row of the same caller is set to
 * never expire in place. Before `CHAT-qojwcatx`, that case wrote a second
 * row. A new room key has no stored row, so `addRoom` never meets that case.
 */
class ContextRoomOwnerGrant<T>(
    private val identity: ContextIdentity<T>,
    authorizationService: AuthorizationService<T, AuthMetadata<T>>,
    rootKeys: RootKeys<T>,
    typeUtil: TypeUtil<T>,
    private val writer: PolicyGrantWriter<T> = PolicyGrantWriter(
        authorizationService, rootKeys, typeUtil, listOf(ShippedGrantPolicies.ROOM_OWNER),
    ),
) : RoomOwnerGrant<T> {

    override fun grantOwner(roomKey: Key<T>): Mono<Void> =
        identity.identity()
            .flatMap { owner -> writer.apply(ShippedGrantPolicies.ROOM_OWNER, ActionContext(owner, roomKey)) }
}
