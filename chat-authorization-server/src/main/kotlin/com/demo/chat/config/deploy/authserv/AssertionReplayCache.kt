package com.demo.chat.config.deploy.authserv

import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtDecoderFactory
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.security.oauth2.server.authorization.authentication.JwtClientAssertionDecoderFactory
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Store the `jti` of each accepted client assertion until it expires.
 * A second use of a `jti` for one client is a replay. See `CHAT-wdvjqtpb`.
 *
 * The cache holds at most [MAX_ENTRIES] records.
 * At that limit, it refuses a new assertion.
 * Removing a record before expiry would allow a replay.
 * The decoder checks signatures before it uses this cache.
 * An agent needs a trusted key to fill the cache.
 *
 * This cache lives in the server process. A restart clears it.
 * Use [JdbcAssertionReplayStore] to keep records after a restart.
 */
class AssertionReplayCache(private val maxEntries: Int = MAX_ENTRIES) : AssertionReplayStore {

    private val entries = ConcurrentHashMap<String, Instant>()

    /**
     * Return true for the first use of [jti] by [clientId].
     * Return false for a repeat, an expired assertion, or a full cache.
     *
     * The lock makes the size check and insert one operation.
     * Without the lock, concurrent claims could exceed the limit.
     */
    @Synchronized
    override fun claim(clientId: String, jti: String, expiresAt: Instant): Boolean {
        val now = Instant.now()
        if (!expiresAt.isAfter(now)) return false
        if (entries.size >= maxEntries) purgeExpired(now)
        if (entries.size >= maxEntries) return false
        return entries.putIfAbsent("$clientId:$jti", expiresAt) == null
    }

    /** The number of remembered assertions. */
    fun size(): Int = entries.size

    private fun purgeExpired(now: Instant) {
        entries.entries.removeIf { it.value.isBefore(now) }
    }

    companion object {
        const val MAX_ENTRIES: Int = 10_000
    }
}

/**
 * Refuse a client assertion when its `jti` was used before.
 * The wrapped factory checks the signature and claims first.
 * A forged assertion cannot reach the replay store.
 */
class ReplayRejectingDecoderFactory(
    private val cache: AssertionReplayStore,
    private val delegate: JwtDecoderFactory<RegisteredClient> = JwtClientAssertionDecoderFactory(),
) : JwtDecoderFactory<RegisteredClient> {

    override fun createDecoder(client: RegisteredClient): JwtDecoder {
        val decoder = delegate.createDecoder(client)
        return JwtDecoder { assertion ->
            val jwt: Jwt = decoder.decode(assertion)
            val jti = jwt.getClaimAsString("jti")
            if (jti.isNullOrBlank()) throw JwtException("The client assertion has no jti.")
            val expiresAt = jwt.expiresAt ?: throw JwtException("The client assertion has no exp.")
            if (!cache.claim(client.clientId, jti, expiresAt)) {
                throw JwtException("The client assertion jti was used before.")
            }
            jwt
        }
    }
}
