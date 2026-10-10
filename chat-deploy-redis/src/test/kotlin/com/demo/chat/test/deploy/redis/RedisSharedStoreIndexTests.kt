package com.demo.chat.test.deploy.redis

import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.config.PersistenceServiceBeans
import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.UserCreateRequest
import com.demo.chat.service.core.UserIndexService
import com.demo.chat.test.deploy.redis.RedisLuceneFilesRestartTests.Companion.addUser
import com.demo.chat.test.deploy.redis.RedisLuceneFilesRestartTests.Companion.findsHandle
import com.demo.chat.test.deploy.redis.RedisLuceneFilesRestartTests.Companion.startWhenClaimFree
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.context.ConfigurableApplicationContext
import java.time.Duration

/**
 * Two node ids on one Redis store, each with its own Lucene index. See
 * CHAT-lswjobhz, gap 1.
 *
 * A write through node A reaches the shared store and the index of node A. It
 * does not reach the index of node B. The indexes stay in memory here. Index
 * files change nothing, because node B reads the store only at its start.
 *
 * Node ids 50 and 51 belong to this class. See docs/NODEID-CLAIM.md.
 */
@Tag("integration")
class RedisSharedStoreIndexTests {

    private val timeout = Duration.ofSeconds(10)

    @Test
    fun `a write through one node does not reach the index of the other node`() {
        startWhenClaimFree(null, 50).use { a ->
            startWhenClaimFree(null, 51).use { b ->
                a.addUser("sharedwrite")
                val key = a.userIndex().findBy(handle("sharedwrite")).single().block(timeout)!!

                assertThat(b.userStore().get(key).block(timeout)?.handle)
                    .describedAs("the user in the shared store, read through node B").isEqualTo("sharedwrite")
                assertThat(b.findsHandle("sharedwrite"))
                    .describedAs("the index of node B").isFalse()
            }
        }
    }

    /**
     * The duplicate check of `addUser` reads the local index. So node B
     * accepts a handle that node A already stored, and the shared store then
     * holds two users with one handle.
     */
    @Test
    fun `a handle stored through one node is accepted again through the other node`() {
        startWhenClaimFree(null, 50).use { a ->
            startWhenClaimFree(null, 51).use { b ->
                a.addUser("sharedhandle")
                b.addUser("sharedhandle")

                val stored = a.userStore().all().filter { it.handle == "sharedhandle" }.collectList().block(timeout)!!
                assertThat(stored).describedAs("users with the handle in the shared store").hasSize(2)
                assertThat(b.compositeUsers().addUser(UserCreateRequest("again", "sharedhandle", "http://u"))
                    .materialize().block(timeout)!!.throwable)
                    .describedAs("a third add through node B, which now indexes the handle").isNotNull()
            }
        }
    }

    private fun handle(value: String) = IndexSearchRequest("handle", value, 10)

    @Suppress("UNCHECKED_CAST")
    private fun ConfigurableApplicationContext.userIndex() =
        getBean("userIndex") as UserIndexService<Long, IndexSearchRequest>

    @Suppress("UNCHECKED_CAST")
    private fun ConfigurableApplicationContext.userStore() =
        (getBean(PersistenceServiceBeans::class.java) as PersistenceServiceBeans<Long, String>).userPersistence()

    @Suppress("UNCHECKED_CAST")
    private fun ConfigurableApplicationContext.compositeUsers() =
        (getBean(CompositeServiceBeans::class.java) as CompositeServiceBeans<Long, String>).userService()
}
