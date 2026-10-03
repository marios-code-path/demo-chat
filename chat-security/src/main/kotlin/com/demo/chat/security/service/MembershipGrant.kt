package com.demo.chat.security.service

import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.Key
import com.demo.chat.domain.TypeUtil
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.service.security.AuthorizationService
import com.demo.chat.service.security.RoomMemberGrant
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Clock

/**
 * The writer of the membership rows: `SEND` and `SUBSCRIBE`. See
 * [RoomMemberGrant], `CHAT-mfveaecc` and `CHAT-lfaajjcj`.
 *
 * **Each permission keeps its own row.** A join writes one `SEND` row and one
 * `SUBSCRIBE` row. A leave expires both. The two permissions never share a row,
 * because the rank groups rows by permission.
 *
 * **One member, one room and one permission keep their rows and change their
 * expiry.** A join sets the expiry of each existing row to 0, or writes one row
 * when none exists. A leave sets the expiry of each live row to the current
 * time. So every row of one member, one room and one permission carries the
 * same expiry after a write.
 *
 * **The rows are changed in place, and not appended.** Level 3 of the
 * `AuthSummarizer` rank orders rows by key id. A uuid key carries no order, so
 * an appended expiry row could lose to an older live row. With one expiry per
 * group, the order of the rows does not change the answer.
 *
 * **A change is a removal and then a write with the same key.** The pair is not
 * atomic. Between the two steps the row is absent, and a check for that member
 * denies. That is the answer of a leave, and a short false refusal during a
 * join.
 *
 * **The owner row is not touched.** This writer reads rows that name `SEND` or
 * `SUBSCRIBE`. The owner row names `*`, and level 1 of the rank keeps it above
 * both. So an owner who leaves can still send and listen.
 */
class MembershipGrant<T>(
    private val authorizationService: AuthorizationService<T, AuthMetadata<T>>,
    private val rootKeys: RootKeys<T>,
    private val typeUtil: TypeUtil<T>,
    private val clock: Clock = Clock.systemUTC(),
) : RoomMemberGrant<T> {

    override fun grantMembership(member: Key<T>, room: Key<T>): Mono<Void> =
        if (!grantable(member)) Mono.empty()
        else Flux.fromIterable(PERMISSIONS)
            .concatMap { permission -> grant(member, room, permission) }
            .then()

    override fun expireMembership(member: Key<T>, room: Key<T>): Mono<Void> =
        if (!grantable(member)) Mono.empty()
        else Mono.defer {
            val now = clock.millis()
            membershipRows(member, room)
                .filter { row -> row.expires == NEVER || row.expires > now }
                .concatMap { row -> replace(row, now) }
                .then()
        }

    private fun grant(member: Key<T>, room: Key<T>, permission: String): Mono<Void> =
        membershipRows(member, room)
            .filter { row -> row.permission == permission }
            .collectList()
            .flatMap { rows ->
                if (rows.isEmpty()) authorizationService.authorize(newRow(member, room, permission), true)
                else Flux.fromIterable(rows)
                    .filter { row -> row.expires != NEVER }
                    .concatMap { row -> replace(row, NEVER) }
                    .then()
            }

    /**
     * **The `Anon` key and the `User` root are in the actor set of every
     * query.** A row for either one would reach every caller.
     */
    private fun grantable(member: Key<T>): Boolean =
        member != rootKeys.anon() && member != rootKeys.of(ChatDomain.USER)

    private fun membershipRows(member: Key<T>, room: Key<T>): Flux<AuthMetadata<T>> =
        authorizationService.getStoredGrants(member, room)
            .filter { row -> row.permission in PERMISSIONS }
            .map { row -> row }

    private fun replace(row: AuthMetadata<T>, expires: Long): Mono<Void> =
        authorizationService.authorize(row, false)
            .then(
                authorizationService.authorize(
                    AuthMetadata.create(row.key, row.principal, row.target, row.permission, row.mute, expires),
                    true,
                )
            )

    // The key is empty, so the service mints it. Only the empty flag is read.
    private fun newRow(member: Key<T>, room: Key<T>, permission: String): AuthMetadata<T> =
        AuthMetadata.create(
            Key.empty(typeUtil.empty(), rootKeys.of(ChatDomain.AUTH_METADATA).id),
            member,
            room,
            permission,
            false,
            NEVER,
        )

    companion object {
        const val SEND = "SEND"
        const val SUBSCRIBE = "SUBSCRIBE"

        /** The rows that one membership holds. */
        val PERMISSIONS: List<String> = listOf(SEND, SUBSCRIBE)

        /** `AuthSummarizer` reads an expiry of 0 as never expiring. */
        const val NEVER = 0L
    }
}
