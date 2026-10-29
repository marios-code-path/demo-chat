package com.demo.chat

import com.demo.chat.config.deploy.authserv.AssertionReplayCache
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

/** The replay memory of a client assertion. See `CHAT-wdvjqtpb`. */
class AssertionReplayCacheTests {

    private val later: Instant = Instant.now().plusSeconds(300)

    @Test
    fun `the first claim holds and the repeat does not`() {
        val cache = AssertionReplayCache()

        assertThat(cache.claim("client-a", "jti-1", later)).isTrue()
        assertThat(cache.claim("client-a", "jti-1", later)).isFalse()
        assertThat(cache.size()).isEqualTo(1)
    }

    @Test
    fun `two clients may use the same jti`() {
        val cache = AssertionReplayCache()

        assertThat(cache.claim("client-a", "jti-1", later)).isTrue()
        assertThat(cache.claim("client-b", "jti-1", later)).isTrue()
        assertThat(cache.size()).isEqualTo(2)
    }

    @Test
    fun `an expired assertion never claims`() {
        val cache = AssertionReplayCache()

        assertThat(cache.claim("client-a", "jti-1", Instant.now().minusSeconds(1))).isFalse()
        assertThat(cache.size()).isZero()
    }

    @Test
    fun `a full cache refuses a new assertion`() {
        val cache = AssertionReplayCache(maxEntries = 1)

        assertThat(cache.claim("client-a", "jti-1", later)).isTrue()
        assertThat(cache.claim("client-a", "jti-2", later)).isFalse()
    }
}
