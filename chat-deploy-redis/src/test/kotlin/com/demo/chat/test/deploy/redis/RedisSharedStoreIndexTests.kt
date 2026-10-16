package com.demo.chat.test.deploy.redis

import com.demo.chat.domain.ServiceLeaseException
import com.demo.chat.test.deploy.redis.RedisLuceneFilesRestartTests.Companion.addUser
import com.demo.chat.test.deploy.redis.RedisLuceneFilesRestartTests.Companion.findsHandle
import com.demo.chat.test.deploy.redis.RedisLuceneFilesRestartTests.Companion.start
import com.demo.chat.test.deploy.redis.RedisLuceneFilesRestartTests.Companion.startWhenClaimFree
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Two cores on one Redis store. See CHAT-lswjobhz.
 *
 * Each Lucene index lives in one process, so a second core on the same store
 * would miss the writes of the first. The measurement of 2026-10-09 is in
 * the spec, docs/superpowers/specs/2026-10-09-store-service-lease-design.md.
 * The store service lease now refuses the second core at its start, and
 * this class proves that refusal.
 *
 * Node ids 50 and 51 belong to this class. See docs/NODEID-CLAIM.md.
 */
@Tag("integration")
class RedisSharedStoreIndexTests {

    @Test
    fun `a second core on one Redis fails at start and names the service and the holder`() {
        startWhenClaimFree(null, 50).use { a ->
            val thrown = assertThrows<Exception> { start(null, 51).close() }
            val lease = generateSequence<Throwable>(thrown) { it.cause }.filterIsInstance<ServiceLeaseException>().first()
            assertThat(lease.name).isEqualTo("index:authmeta")
            assertThat(lease.scope).isEqualTo("redis store for key type long")
            assertThat(lease.holder).startsWith("node 50 ")

            a.addUser("sharedcontrol")
            assertThat(a.findsHandle("sharedcontrol")).describedAs("the first core still serves").isTrue()
        }
    }
}
