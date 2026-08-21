package com.demo.chat.test.persistence.memory

import com.demo.chat.config.persistence.memory.MemoryPersistenceServices
import com.demo.chat.service.core.IKeyService
import com.demo.chat.test.TestLongKeyService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * `app.service.core.persistence` names one persistence backend. Memory is the
 * in-process default, so it activates when the selector is absent — but never
 * when the selector names a different backend.
 */
class MemoryPersistenceServicesConditionTests {

    @Configuration(proxyBeanMethods = false)
    class KeyServiceStub {
        @Bean
        fun keyService(): IKeyService<Long> = TestLongKeyService()
    }

    /**
     * Registers MemoryPersistenceServices as an annotated class so its
     * @ConditionalOnProperty is evaluated. Do not use withBean() here — a
     * supplied bean bypasses conditions entirely, which is the thing under
     * test.
     */
    private fun runner() = ApplicationContextRunner()
        .withUserConfiguration(KeyServiceStub::class.java)
        .withUserConfiguration(MemoryPersistenceServices::class.java)

    @Test
    fun `activates when the selector names memory`() {
        runner()
            .withPropertyValues("app.service.core.persistence=memory")
            .run { context ->
                assertThat(context).hasSingleBean(MemoryPersistenceServices::class.java)
            }
    }

    @Test
    fun `activates when the selector is absent`() {
        runner().run { context ->
            assertThat(context).hasSingleBean(MemoryPersistenceServices::class.java)
        }
    }

    @Test
    fun `does not activate when the selector names another implementation`() {
        runner()
            .withPropertyValues("app.service.core.persistence=cassandra")
            .run { context ->
                assertThat(context).doesNotHaveBean(MemoryPersistenceServices::class.java)
            }
    }

    @Test
    fun `does not activate when the selector is the empty string`() {
        runner()
            .withPropertyValues("app.service.core.persistence=")
            .run { context ->
                assertThat(context).doesNotHaveBean(MemoryPersistenceServices::class.java)
            }
    }
}
