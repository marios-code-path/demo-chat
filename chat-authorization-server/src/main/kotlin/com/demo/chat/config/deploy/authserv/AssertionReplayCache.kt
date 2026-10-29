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
 * Remembers the `jti` of each accepted client assertion until it expires.
 * A repeat of one `jti` for one client is a replay. See `CHAT-wdvjqtpb`.
 *
 * The cache holds a fixed maximum. At the maximum it refuses the new
 * assertion, because a silent drop would let a replay through. Only an
 * assertion with a valid signature reaches the cache, so a flood needs a
 * trusted key.
 *
 * This store lives in the process. A restart drops it. Use
 * [JdbcAssertionReplayStore] when a restart must keep the record.
 */
class AssertionReplayCache(private val maxEntries: Int = MAX_ENTRIES) : AssertionReplayStore {

    private val entries = ConcurrentHashMap<String, Instant>()

    /**
     * Answer true on the first use of [jti] for [clientId]. Answer false on a
     * repeat, on an expired assertion, and at the cache limit.
     *
     * The lock keeps the size check and the insert one step. Without it,
     * parallel claims can pass the check together and pass the limit.
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
 * A decoder factory that refuses a repeated client assertion. The wrapped
 * factory checks the signature and the claims first, so a forged assertion
 * never reaches the cache.
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
            if (jti.isNullOrBlank()) throw JwtException("The client assertion carries no jti.")
            val expiresAt = jwt.expiresAt ?: throw JwtException("The client assertion carries no exp.")
            if (!cache.claim(client.clientId, jti, expiresAt)) {
                throw JwtException("The client assertion jti was already used.")
            }
            jwt
        }
    }
}
