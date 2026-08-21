package com.demo.chat.test.persistence.memory

import com.demo.chat.config.persistence.memory.MemorySecretsStoreServiceBeans
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner

/**
 * `app.service.core.secrets` names one secrets-store backend. Memory is the
 * in-process default, so it activates when the selector is absent — but never
 * when the selector names a different backend.
 */
class MemorySecretsStoreConditionTests {

    /**
     * Registers MemorySecretsStoreServiceBeans as an annotated class so its
     * @ConditionalOnProperty is evaluated. Do not use withBean() here — a
     * supplied bean bypasses conditions entirely, which is the thing under
     * test.
     */
    private fun runner() = ApplicationContextRunner()
        .withUserConfiguration(MemorySecretsStoreServiceBeans::class.java)

    @Test
    fun `activates when the selector names memory`() {
        runner()
            .withPropertyValues("app.service.core.secrets=memory")
            .run { context ->
                assertThat(context).hasSingleBean(MemorySecretsStoreServiceBeans::class.java)
            }
    }

    @Test
    fun `activates when the selector is absent`() {
        runner().run { context ->
            assertThat(context).hasSingleBean(MemorySecretsStoreServiceBeans::class.java)
        }
    }

    @Test
    fun `does not activate when the selector names another implementation`() {
        runner()
            .withPropertyValues("app.service.core.secrets=cassandra")
            .run { context ->
                assertThat(context).doesNotHaveBean(MemorySecretsStoreServiceBeans::class.java)
            }
    }

    @Test
    fun `does not activate when the selector is the empty string`() {
        runner()
            .withPropertyValues("app.service.core.secrets=")
            .run { context ->
                assertThat(context).doesNotHaveBean(MemorySecretsStoreServiceBeans::class.java)
            }
    }
}
