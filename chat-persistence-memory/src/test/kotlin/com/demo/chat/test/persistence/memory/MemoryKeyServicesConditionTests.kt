package com.demo.chat.test.persistence.memory

import com.demo.chat.config.persistence.memory.MemoryKeyServices
import com.demo.chat.service.core.IKeyGenerator
import com.demo.chat.test.TestLongKeyService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * `app.service.core.key` names one key-service backend. Memory is the
 * in-process default, so it activates when the selector is absent — but never
 * when the selector names a different backend.
 */
class MemoryKeyServicesConditionTests {

    @Configuration(proxyBeanMethods = false)
    class KeyGeneratorStub {
        @Bean
        fun keyGenerator(): IKeyGenerator<Long> = TestLongKeyService()
    }

    /**
     * Registers MemoryKeyServices as an annotated class so its
     * @ConditionalOnProperty is evaluated. Do not use withBean() here — a
     * supplied bean bypasses conditions entirely, which is the thing under
     * test.
     */
    private fun runner() = ApplicationContextRunner()
        .withUserConfiguration(KeyGeneratorStub::class.java)
        .withUserConfiguration(MemoryKeyServices::class.java)

    @Test
    fun `activates when the selector names memory`() {
        runner()
            .withPropertyValues("app.service.core.key=memory")
            .run { context ->
                assertThat(context).hasSingleBean(MemoryKeyServices::class.java)
            }
    }

    @Test
    fun `activates when the selector is absent`() {
        runner().run { context ->
            assertThat(context).hasSingleBean(MemoryKeyServices::class.java)
        }
    }

    @Test
    fun `does not activate when the selector names another implementation`() {
        runner()
            .withPropertyValues("app.service.core.key=cassandra")
            .run { context ->
                assertThat(context).doesNotHaveBean(MemoryKeyServices::class.java)
            }
    }

    @Test
    fun `does not activate when the selector is the empty string`() {
        runner()
            .withPropertyValues("app.service.core.key=")
            .run { context ->
                assertThat(context).doesNotHaveBean(MemoryKeyServices::class.java)
            }
    }
}
