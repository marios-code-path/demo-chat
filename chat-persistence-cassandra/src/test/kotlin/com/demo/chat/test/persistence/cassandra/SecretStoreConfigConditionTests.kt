package com.demo.chat.test.persistence.cassandra

import com.demo.chat.config.persistence.cassandra.SecretStoreConfig
import com.demo.chat.persistence.cassandra.repository.KeyCredentialRepository
import com.demo.chat.service.core.IKeyService
import com.demo.chat.test.TestLongKeyService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * `app.service.core.secrets` names one secrets-store backend. Cassandra
 * activates only when the selector names it — never by default, and never
 * alongside the in-memory backend.
 *
 * The credential repository is mocked because the condition, not the query
 * behaviour, is under test; the configuration stores its dependencies and
 * builds the store lazily, so no Cassandra connection is involved.
 */
class SecretStoreConfigConditionTests {

    @Suppress("UNCHECKED_CAST")
    @Configuration(proxyBeanMethods = false)
    class CassandraDependencyStubs {
        @Bean
        fun keyService(): IKeyService<Long> = TestLongKeyService()

        @Bean
        fun credentialRepo(): KeyCredentialRepository<Long> =
            mock(KeyCredentialRepository::class.java) as KeyCredentialRepository<Long>
    }

    /**
     * Registers SecretStoreConfig as an annotated class so its
     * @ConditionalOnProperty is evaluated. Do not use withBean() here — a
     * supplied bean bypasses conditions entirely, which is the thing under
     * test.
     */
    private fun runner() = ApplicationContextRunner()
        .withUserConfiguration(CassandraDependencyStubs::class.java)
        .withUserConfiguration(SecretStoreConfig::class.java)

    @Test
    fun `activates when the selector names cassandra`() {
        runner()
            .withPropertyValues("app.service.core.secrets=cassandra")
            .run { context ->
                assertThat(context).hasSingleBean(SecretStoreConfig::class.java)
            }
    }

    @Test
    fun `does not activate when the selector names another implementation`() {
        runner()
            .withPropertyValues("app.service.core.secrets=memory")
            .run { context ->
                assertThat(context).doesNotHaveBean(SecretStoreConfig::class.java)
            }
    }

    @Test
    fun `does not activate when the selector is absent`() {
        runner().run { context ->
            assertThat(context).doesNotHaveBean(SecretStoreConfig::class.java)
        }
    }

    @Test
    fun `does not activate when the selector is the empty string`() {
        runner()
            .withPropertyValues("app.service.core.secrets=")
            .run { context ->
                assertThat(context).doesNotHaveBean(SecretStoreConfig::class.java)
            }
    }
}
