package com.demo.chat.deploy.test.init

import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.Key
import com.demo.chat.service.security.AuthorizationService
import com.demo.chat.test.TestLongKeyGenerator
import com.demo.chat.test.key.TestKeys
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * An authorization store over a map.
 *
 * It stores what `authorize` writes and removes what it revokes, so a test can
 * start `InitialUsersService` twice against one store. A write with an empty
 * key takes a new key, and each new key is higher than the one before it. That
 * is the order of a `Long` snowflake key. See `CHAT-ghwtzgjp`.
 *
 * It holds no owner guard and no summarizer. The test reads the stored rows.
 */
class RecordingAuthorizationService : AuthorizationService<Long, AuthMetadata<Long>> {

    private val keys = TestLongKeyGenerator()

    private val rows: MutableMap<Key<Long>, AuthMetadata<Long>> = linkedMapOf()

    /** Every row that a write stored, in the order of the writes. */
    val writes: MutableList<AuthMetadata<Long>> = mutableListOf()

    /** Every key that a revoke removed, in the order of the revokes. */
    val removals: MutableList<Key<Long>> = mutableListOf()

    /** The stored rows. */
    fun stored(): List<AuthMetadata<Long>> = rows.values.toList()

    /** The stored rows of one principal, target and permission. */
    fun stored(principal: Key<Long>, target: Key<Long>, permission: String): List<AuthMetadata<Long>> =
        rows.values.filter { it.principal == principal && it.target == target && it.permission == permission }

    /** This method stores [row] as given, with no write record. A test seeds the store with it. */
    fun seed(row: AuthMetadata<Long>): AuthMetadata<Long> {
        val stored = if (row.key.empty) withKey(row, TestKeys.key(keys.nextId())) else row
        rows[stored.key] = stored
        return stored
    }

    override fun authorize(authorization: AuthMetadata<Long>, exist: Boolean): Mono<Void> = Mono.fromRunnable {
        if (exist) writes.add(seed(authorization))
        else {
            rows.remove(authorization.key)
            removals.add(authorization.key)
        }
    }

    override fun getStoredGrants(principal: Key<Long>, target: Key<Long>): Flux<AuthMetadata<Long>> =
        Flux.defer { Flux.fromIterable(rows.values.filter { it.principal == principal && it.target == target }) }

    override fun getAuthorizationsForTarget(uid: Key<Long>): Flux<AuthMetadata<Long>> = unsupported()

    override fun getAuthorizationsForPrincipal(uid: Key<Long>): Flux<AuthMetadata<Long>> = unsupported()

    override fun getAuthorizationsAgainst(uidA: Key<Long>, uidB: Key<Long>, permission: String?): Flux<AuthMetadata<Long>> =
        unsupported()

    override fun getAuthorizationsAgainstMany(uidA: Key<Long>, uidB: List<Key<Long>>, permission: String?): Flux<AuthMetadata<Long>> =
        unsupported()

    private fun unsupported(): Flux<AuthMetadata<Long>> =
        Flux.error(UnsupportedOperationException("The initial user start does not read a summarized grant."))

    companion object {
        /** A copy of [row] under [key]. */
        fun withKey(row: AuthMetadata<Long>, key: Key<Long>): AuthMetadata<Long> =
            AuthMetadata.create(key, row.principal, row.target, row.permission, row.mute, row.expires)
    }
}
