package com.demo.chat.test.domain

import com.demo.chat.domain.ConditionalOnSharedBackend
import com.demo.chat.domain.NodeId
import com.demo.chat.domain.NodeIdClaimGuard
import com.demo.chat.domain.NodeIdClaimGuardConfiguration
import com.demo.chat.domain.NodeIdClaimStore
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.mock.env.MockEnvironment

/**
 * Pins the activation invariant that keeps `app.nodeid` scoped.
 *
 * 1. A claim store configuration imports NodeIdClaimGuardConfiguration.
 * 2. A memory only context contributes no NodeIdClaimStore.
 * 3. No claim store means no guard bean.
 * 4. No guard bean means this design requests no NodeId bean.
 */
class SharedBackendConditionTests {

    @Configuration
    @ConditionalOnSharedBackend("redis")
    @Import(NodeIdClaimGuardConfiguration::class)
    open class RedisClaimConfiguration {
        @Bean
        open fun redisClaimStore(): NodeIdClaimStore = FakeClaimStore("redis")
    }

    private fun contextWith(vararg properties: Pair<String, String>): AnnotationConfigApplicationContext {
        val context = AnnotationConfigApplicationContext()
        val environment = MockEnvironment()
        properties.forEach { environment.setProperty(it.first, it.second) }
        context.environment = environment
        context.register(RedisClaimConfiguration::class.java)
        context.refresh()
        return context
    }

    @Test
    fun `the key selector activates the store`() {
        contextWith("app.service.core.key" to "redis", "app.nodeid" to "7").use { context ->
            Assertions.assertEquals(1, context.getBeansOfType(NodeIdClaimStore::class.java).size)
            Assertions.assertEquals(1, context.getBeansOfType(NodeIdClaimGuard::class.java).size)
        }
    }

    @Test
    fun `the persistence selector activates the store`() {
        contextWith("app.service.core.persistence" to "redis", "app.nodeid" to "7").use { context ->
            Assertions.assertEquals(1, context.getBeansOfType(NodeIdClaimStore::class.java).size)
        }
    }

    @Test
    fun `a memory pair activates nothing`() {
        contextWith(
            "app.service.core.key" to "memory",
            "app.service.core.persistence" to "memory"
        ).use { context ->
            Assertions.assertTrue(context.getBeansOfType(NodeIdClaimStore::class.java).isEmpty())
            Assertions.assertTrue(context.getBeansOfType(NodeIdClaimGuard::class.java).isEmpty())
        }
    }

    @Test
    fun `an unset pair activates nothing and needs no app nodeid`() {
        contextWith().use { context ->
            Assertions.assertTrue(context.getBeansOfType(NodeIdClaimStore::class.java).isEmpty())
            Assertions.assertTrue(context.getBeansOfType(NodeId::class.java).isEmpty())
        }
    }

    @Test
    fun `a cassandra pair does not activate the redis store`() {
        contextWith(
            "app.service.core.key" to "cassandra",
            "app.service.core.persistence" to "cassandra"
        ).use { context ->
            Assertions.assertTrue(context.getBeansOfType(NodeIdClaimStore::class.java).isEmpty())
        }
    }
}
