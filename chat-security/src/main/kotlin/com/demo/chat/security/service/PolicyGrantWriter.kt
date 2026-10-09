package com.demo.chat.security.service

import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.Key
import com.demo.chat.domain.TypeUtil
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.service.security.AuthorizationService
import com.demo.chat.service.security.policy.ActionContext
import com.demo.chat.service.security.policy.GrantExpiry
import com.demo.chat.service.security.policy.GrantIntent
import com.demo.chat.service.security.policy.GrantPolicy
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Clock

/**
 * The writer that maps the intents of a grant policy to `AuthMetadata` rows.
 * See `CHAT-qojwcatx` and `docs/superpowers/specs/2026-10-02-action-grant-policy-model-design.md`.
 *
 * **A policy has no effect until it is enabled.** [apply] writes nothing for a
 * policy that is not in the enabled list.
 *
 * **One principal, one target and one permission keep their rows and change
 * their expiry.** A `NONE` intent sets the expiry of each stored row to 0, or
 * writes one row when none exists. A `NOW` intent sets the expiry of each live
 * row to the time of the action. It writes no row when no live row exists.
 * These are the rules that `MembershipGrant` held before this writer.
 *
 * **The rows are changed in place, and not appended.** Level 3 of the
 * `AuthSummarizer` rank orders rows by key id. A uuid key carries no order, so
 * an appended expiry row could lose to an older live row.
 *
 * **A change is a removal and then a write with the same key.** The pair is not
 * atomic. Between the two steps the row is absent, and a check for that
 * principal denies.
 *
 * **Each new row gets its own grant key.** The row carries an empty key with
 * the `AUTH_METADATA` root, and the store mints the key. A target key never
 * becomes a grant row key.
 *
 * **The `*` row keeps the owner guard.** A new `*` row goes through
 * `AuthorizationService.authorize`, so a second live owner is refused with
 * `SecondOwnerException`.
 */
class PolicyGrantWriter<T>(
    private val authorizationService: AuthorizationService<T, AuthMetadata<T>>,
    private val rootKeys: RootKeys<T>,
    private val typeUtil: TypeUtil<T>,
    enabled: List<GrantPolicy>,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val enabled: Set<GrantPolicy> = enabled.toSet()

    init {
        val repeated = enabled.groupBy { it.name }.filterValues { it.size > 1 }.keys
        require(repeated.isEmpty()) { "Two enabled grant policies share a name: $repeated" }
    }

    /**
     * This method writes the intents of [policy] for one completed action. All
     * `NOW` intents of one call read the same time.
     */
    fun apply(policy: GrantPolicy, action: ActionContext<T>): Mono<Void> =
        if (policy !in enabled) Mono.empty()
        else Mono.defer {
            val now = clock.millis()
            Flux.fromIterable(policy.intents(action, rootKeys))
                .concatMap { intent ->
                    when (intent.expiry) {
                        GrantExpiry.NONE -> grant(intent)
                        GrantExpiry.NOW -> expire(intent, now)
                    }
                }
                .then()
        }

    private fun grant(intent: GrantIntent<T>): Mono<Void> =
        storedRows(intent)
            .collectList()
            .flatMap { rows ->
                if (rows.isEmpty()) authorizationService.authorize(newRow(intent), true)
                else Flux.fromIterable(rows)
                    .filter { row -> row.expires != NEVER }
                    .concatMap { row -> replace(row, NEVER) }
                    .then()
            }

    private fun expire(intent: GrantIntent<T>, now: Long): Mono<Void> =
        storedRows(intent)
            .filter { row -> row.expires == NEVER || row.expires > now }
            .concatMap { row -> replace(row, now) }
            .then()

    private fun storedRows(intent: GrantIntent<T>): Flux<AuthMetadata<T>> =
        authorizationService.getStoredGrants(intent.principal, intent.target)
            .filter { row -> row.permission == intent.permission }
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
    private fun newRow(intent: GrantIntent<T>): AuthMetadata<T> =
        AuthMetadata.create(
            Key.empty(typeUtil.empty(), rootKeys.of(ChatDomain.AUTH_METADATA).id),
            intent.principal,
            intent.target,
            intent.permission,
            false,
            NEVER,
        )

    companion object {
        /** `AuthSummarizer` reads an expiry of 0 as never expiring. */
        const val NEVER = 0L
    }
}
