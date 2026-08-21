package com.demo.chat.test.persistence.cassandra

import com.demo.chat.config.persistence.cassandra.CoreKeyServices
import com.demo.chat.service.core.IKeyGenerator
import com.demo.chat.test.TestLongKeyService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate

/**
 * `app.service.core.key` names one key-service backend. Cassandra activates
 * only when the selector names it — never by default, and never alongside the
 * in-memory backend.
 *
 * The template is mocked because the condition, not the query behaviour, is
 * under test; the configuration stores it and builds the key service lazily,
 * so no Cassandra connection is involved.
 */
class CoreKeyServicesConditionTests {

    @Configuration(proxyBeanMethods = false)
    class CassandraDependencyStubs {
        @Bean
        fun keyGenerator(): IKeyGenerator<Long> = TestLongKeyService()

        @Bean
        fun cassandraTemplate(): ReactiveCassandraTemplate = mock(ReactiveCassandraTemplate::class.java)
    }

    /**
     * Registers CoreKeyServices as an annotated class so its
     * @ConditionalOnProperty is evaluated. Do not use withBean() here — a
     * supplied bean bypasses conditions entirely, which is the thing under
     * test.
     */
    private fun runner() = ApplicationContextRunner()
        .withUserConfiguration(CassandraDependencyStubs::class.java)
        .withUserConfiguration(CoreKeyServices::class.java)

    @Test
    fun `activates when the selector names cassandra`() {
        runner()
            .withPropertyValues("app.service.core.key=cassandra")
            .run { context ->
                assertThat(context).hasSingleBean(CoreKeyServices::class.java)
            }
    }

    @Test
    fun `does not activate when the selector names another implementation`() {
        runner()
            .withPropertyValues("app.service.core.key=memory")
            .run { context ->
                assertThat(context).doesNotHaveBean(CoreKeyServices::class.java)
            }
    }

    @Test
    fun `does not activate when the selector is absent`() {
        runner().run { context ->
            assertThat(context).doesNotHaveBean(CoreKeyServices::class.java)
        }
    }

    @Test
    fun `does not activate when the selector is the empty string`() {
        runner()
            .withPropertyValues("app.service.core.key=")
            .run { context ->
                assertThat(context).doesNotHaveBean(CoreKeyServices::class.java)
            }
    }
}
