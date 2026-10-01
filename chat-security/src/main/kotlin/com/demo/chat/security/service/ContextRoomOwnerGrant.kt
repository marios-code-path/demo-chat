package com.demo.chat.security.service

import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.Key
import com.demo.chat.domain.TypeUtil
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.security.AuthSummarizer
import com.demo.chat.security.access.ContextIdentity
import com.demo.chat.service.security.AuthorizationService
import com.demo.chat.service.security.RoomOwnerGrant
import reactor.core.publisher.Mono

/**
 * The one writer of a room ownership row.
 *
 * **`*` is singular per target, so one writer is required.** Two writers would
 * give one room two owners, and the rank would then answer by time alone. See
 * `CHAT-zhjltbky`.
 *
 * **A caller with no identity owns no room.** `ContextIdentity` answers an
 * empty `Mono` for a denied caller, so the `flatMap` never runs. That is not an
 * error, because no authenticated route reaches this call today.
 *
 * **An anonymous caller owns no room either.** `ContextIdentity` rule 4 answers
 * the `Anon` root key for an `AnonymousAuthenticationToken`, and the RSocket
 * server seam installs that token. So a caller with no credential reaches an
 * identity rather than an empty one, and the filter below drops it.
 *
 * The `Anon` key is in the actor set of every query. An ownership row for it
 * would therefore make every caller the owner of the room. A close could not
 * reach that row, because an identity is an `ENTITY` and a close names a
 * `DOMAIN_ROOT`, so level 2 of the rank would keep the row. See
 * `docs/ANONYMOUS-AUTHORIZATION.md`.
 *
 * The expiry is 0, which `AuthSummarizer` reads as never expiring. The shell
 * wrote `Long.MAX_VALUE`, and a sentinel is not needed.
 */
class ContextRoomOwnerGrant<T>(
    private val identity: ContextIdentity<T>,
    private val authorizationService: AuthorizationService<T, AuthMetadata<T>>,
    private val rootKeys: RootKeys<T>,
    private val typeUtil: TypeUtil<T>,
) : RoomOwnerGrant<T> {

    override fun grantOwner(roomKey: Key<T>): Mono<Void> =
        identity.identity()
            .filter { owner -> owner != rootKeys.anon() }
            .flatMap { owner ->
                authorizationService.authorize(
                    // The key is empty, so the service mints it. Only the empty
                    // flag is read; the root below is inert.
                    AuthMetadata.create(
                        Key.empty(typeUtil.empty(), rootKeys.of(ChatDomain.AUTH_METADATA).id),
                        owner,
                        roomKey,
                        AuthSummarizer.WILDCARD,
                        false,
                        0L,
                    ),
                    true,
                )
            }
            .then()
}
