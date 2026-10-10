package com.demo.chat.test

import org.slf4j.LoggerFactory
import org.testcontainers.containers.CassandraContainer
import org.testcontainers.containers.Network
import org.testcontainers.utility.MountableFile
import java.time.Duration

/**
 * One Cassandra container for each test JVM. `CHAT-znodyvcc`.
 *
 * **Each Spring test context started its own container before.** The test
 * classes use different property sets, so one module run made 10 contexts and
 * 10 containers. Spring keeps a context until the JVM ends, so all 10 ran at
 * once. Measured on 2026-10-09: about 15 GiB on a 16 GiB Docker VM.
 *
 * The container starts at the first read and holds both keyspaces. Each
 * context opens its own session to it. Nothing in Spring stops it. The
 * Testcontainers reaper removes it when the JVM ends.
 *
 * **The keyspace scripts start with `DROP KEYSPACE`.** So they run once, here,
 * and never again in the same JVM.
 */
object SharedCassandraContainer {
    const val IMAGE = "cassandra:4.1.3"
    const val PORT = 9042

    /**
     * The heap of one test container. The image sizes the heap from the memory
     * of the Docker VM, which gave 1.4 to 2.0 GiB for each container. The image
     * requires both values together.
     */
    val HEAP = mapOf("MAX_HEAP_SIZE" to "1G", "HEAP_NEWSIZE" to "256M")

    private val log = LoggerFactory.getLogger(SharedCassandraContainer::class.java)

    val container: CassandraContainer<Nothing> by lazy {
        CassandraContainer<Nothing>(IMAGE).apply {
            withExposedPorts(PORT)
            withNetwork(Network.SHARED)
            withEnv(HEAP)
            withStartupTimeout(Duration.ofSeconds(120))
            withInitScript("keyspace-long.cql")
            start()

            // Testcontainers takes one init script. cqlsh applies the second.
            copyFileToContainer(MountableFile.forClasspathResource("keyspace-uuid.cql"), "/tmp/keyspace-uuid.cql")
            val result = execInContainer("cqlsh", "-u", username, "-p", password, "-f", "/tmp/keyspace-uuid.cql")
            check(result.exitCode == 0) { "keyspace-uuid.cql failed: ${result.stderr}" }
            log.info("The shared Cassandra test container started on port {}", getMappedPort(PORT))
        }
    }
}
