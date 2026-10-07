package com.demo.chat.test.deploy.redis

import com.demo.chat.config.PersistenceServiceBeans
import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.NodeIdClaimException
import com.demo.chat.domain.User
import com.demo.chat.domain.UserCreateRequest
import com.demo.chat.service.core.IndexFileAdmin
import com.demo.chat.service.core.UserIndexService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.ConfigurableApplicationContext
import java.nio.file.Path
import java.time.Duration

/**
 * The Lucene indexes of a Redis deployment in files. A restart with intact
 * files reuses them. A store write with no index write makes the next start
 * build. See CHAT-ybtirmgj.
 *
 * Node ids 18 and 19 belong to this class. See docs/NODEID-CLAIM.md. Each test
 * restarts on one node id, because the index path holds the node id. A Redis
 * close does not release the claim (CHAT-ocpojbyy), so the claim TTL is 3
 * seconds and the second start retries until the claim is free.
 */
@Tag("integration")
class RedisLuceneFilesRestartTests {

    private val timeout = Duration.ofSeconds(10)

    companion object {
        private val container = RedisDeployBootTests.redis

        /** A null root keeps the indexes in memory. */
        private fun properties(root: Path?, nodeId: Int) = arrayOf(
            "spring.application.name=redis-lucene-files-test",
            "spring.config.additional-location=classpath:/config/userinit.yml",
            "server.port=0", "spring.rsocket.server.port=0", "app.server.proto=rsocket",
            "app.key.type=long", "app.nodeid=$nodeId", "app.users.create=true",
            "app.service.core.key=redis", "app.service.core.persistence=redis",
            "app.service.core.pubsub=redis-pubsub", "app.service.core.index=lucene",
            "app.service.core.secrets=memory", "app.service.composite", "app.service.composite.auth=true",
            "app.controller.persistence", "app.controller.index", "app.controller.key", "app.controller.pubsub",
            "app.controller.secrets", "app.controller.user", "app.controller.topic", "app.controller.message",
            "app.service.security.userdetails",
            "spring.cloud.consul.enabled=false", "spring.cloud.consul.discovery.enabled=false",
            "spring.cloud.consul.config.enabled=false",
            "redis-topics.host=${container.containerIpAddress}",
            "redis-topics.port=${container.getMappedPort(6379)}",
            "app.nodeid.claim.ttl=3s", "app.nodeid.claim.renew-interval=1s",
            "app.nodeid.claim.safety-margin=1s", "app.nodeid.claim.operation-timeout=500ms",
        ).toList() + listOfNotNull(root?.let { "app.index.lucene.root=$it" })

        fun start(root: Path?, nodeId: Int): ConfigurableApplicationContext =
            SpringApplicationBuilder(RedisDeployBootTests.BootApp::class.java)
                .web(WebApplicationType.NONE)
                .properties(*properties(root, nodeId).toTypedArray())
                .run()

        /** Retries only on a held claim, and for at most 15 seconds. Any other failure ends the test. */
        fun startWhenClaimFree(root: Path?, nodeId: Int): ConfigurableApplicationContext {
            val deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos()
            while (true) {
                try {
                    return start(root, nodeId)
                } catch (e: Exception) {
                    val claimed = generateSequence<Throwable>(e) { it.cause }.any { it is NodeIdClaimException }
                    if (!claimed || System.nanoTime() > deadline) throw e
                    Thread.sleep(500)
                }
            }
        }

        @Suppress("UNCHECKED_CAST")
        fun ConfigurableApplicationContext.addUser(handle: String) {
            val composite = getBean(CompositeServiceBeans::class.java) as CompositeServiceBeans<Long, String>
            composite.userService().addUser(UserCreateRequest("files", handle, "http://u")).block(Duration.ofSeconds(10))
        }
    }

    private fun ConfigurableApplicationContext.report(name: String) =
        getBean(IndexFileAdmin::class.java).reports().single { it.name == name }

    @Suppress("UNCHECKED_CAST")
    private fun ConfigurableApplicationContext.findsHandle(handle: String): Boolean =
        (getBean("userIndex") as UserIndexService<Long, IndexSearchRequest>)
            .findBy(IndexSearchRequest("handle", handle, 10)).collectList().block(timeout)!!.isNotEmpty()

    @Test
    fun `a restart with intact files reuses them`(@TempDir root: Path) {
        startWhenClaimFree(root, 18).use { first -> first.addUser("filesreuse") }
        startWhenClaimFree(root, 18).use { second ->
            val user = second.report("user")
            assertThat(user.outcome).isEqualTo("REUSED")
            assertThat(user.documentsWritten).isEqualTo(0)
            assertThat(second.findsHandle("filesreuse")).isTrue()
        }
    }

    @Test
    fun `a store write with no index write makes the next start build`(@TempDir root: Path) {
        startWhenClaimFree(root, 19).use { first ->
            first.addUser("filesfirst")
            @Suppress("UNCHECKED_CAST")
            val stores = first.getBean(PersistenceServiceBeans::class.java) as PersistenceServiceBeans<Long, String>
            val key = stores.userPersistence().key().block(timeout)!!
            stores.userPersistence().add(User.create(key, "files", "filesstoreonly", "http://u")).block(timeout)
            assertThat(first.findsHandle("filesstoreonly")).isFalse()
        }
        startWhenClaimFree(root, 19).use { second ->
            val user = second.report("user")
            assertThat(user.outcome).isEqualTo("BUILT")
            assertThat(user.reason).isEqualTo("MISMATCH")
            assertThat(second.findsHandle("filesstoreonly")).isTrue()
            assertThat(second.findsHandle("filesfirst")).isTrue()
        }
    }
}
