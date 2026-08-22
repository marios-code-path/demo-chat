package com.demo.chat.test.persistence.redis

import org.springframework.test.context.DynamicPropertyRegistry
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy
import java.time.Duration

/**
 * Shared Redis container for all persistence tests in this package.
 *
 * Started once (on first access) and shared by every test class; each test
 * class exposes it via its own companion @DynamicPropertySource (Spring only
 * discovers @DynamicPropertySource on the test class and its superclasses,
 * so the property source cannot live here). Test isolation is provided by
 * the FLUSHDB in each test base's @BeforeEach.
 */
object RedisTestContainer {

    val container: GenericContainer<*> =
        GenericContainer<Nothing>("redis:5.0.14")
            .apply {
                withExposedPorts(6379)
                withReuse(true)
                waitingFor(
                    LogMessageWaitStrategy()
                        .withRegEx(".*Ready to accept connections.*\\s")
                        .withStartupTimeout(Duration.ofSeconds(60))
                )
                start()
            }

    fun properties(registry: DynamicPropertyRegistry) {
        registry.add("spring.redis.host") { container.containerIpAddress }
        registry.add("spring.redis.port") { container.getMappedPort(6379) }
    }
}