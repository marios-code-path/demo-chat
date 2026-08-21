package com.demo.chat.test.persistence.cassandra

import com.demo.chat.config.persistence.cassandra.CorePersistenceServices
import com.demo.chat.persistence.cassandra.repository.AuthMetadataRepository
import com.demo.chat.persistence.cassandra.repository.ChatMessageRepository
import com.demo.chat.persistence.cassandra.repository.ChatUserRepository
import com.demo.chat.persistence.cassandra.repository.KeyValuePairRepository
import com.demo.chat.persistence.cassandra.repository.TopicMembershipRepository
import com.demo.chat.persistence.cassandra.repository.TopicRepository
import com.demo.chat.service.core.IKeyService
import com.demo.chat.test.TestLongKeyService
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * `app.service.core.persistence` names one persistence backend. Cassandra
 * activates only when the selector names it — never by default, and never
 * alongside the in-memory backend.
 *
 * The repositories are mocked because the condition, not the query behaviour,
 * is under test; the configuration stores its dependencies and builds the
 * persistence services lazily, so no Cassandra connection is involved.
 */
class CorePersistenceServicesConditionTests {

    @Suppress("UNCHECKED_CAST")
    @Configuration(proxyBeanMethods = false)
    class CassandraDependencyStubs {
        @Bean
        fun keyService(): IKeyService<Long> = TestLongKeyService()

        @Bean
        fun mapper(): ObjectMapper = ObjectMapper()

        @Bean
        fun userRepo(): ChatUserRepository<Long> = mock(ChatUserRepository::class.java) as ChatUserRepository<Long>

        @Bean
        fun topicRepo(): TopicRepository<Long> = mock(TopicRepository::class.java) as TopicRepository<Long>

        @Bean
        fun messageRepo(): ChatMessageRepository<Long> = mock(ChatMessageRepository::class.java) as ChatMessageRepository<Long>

        @Bean
        fun membershipRepo(): TopicMembershipRepository<Long> = mock(TopicMembershipRepository::class.java) as TopicMembershipRepository<Long>

        @Bean
        fun authmetaRepo(): AuthMetadataRepository<Long> = mock(AuthMetadataRepository::class.java) as AuthMetadataRepository<Long>

        @Bean
        fun keyValueRepo(): KeyValuePairRepository<Long> = mock(KeyValuePairRepository::class.java) as KeyValuePairRepository<Long>
    }

    /**
     * Registers CorePersistenceServices as an annotated class so its
     * @ConditionalOnProperty is evaluated. Do not use withBean() here — a
     * supplied bean bypasses conditions entirely, which is the thing under
     * test.
     */
    private fun runner() = ApplicationContextRunner()
        .withUserConfiguration(CassandraDependencyStubs::class.java)
        .withUserConfiguration(CorePersistenceServices::class.java)

    @Test
    fun `activates when the selector names cassandra`() {
        runner()
            .withPropertyValues("app.service.core.persistence=cassandra")
            .run { context ->
                assertThat(context).hasSingleBean(CorePersistenceServices::class.java)
            }
    }

    @Test
    fun `does not activate when the selector names another implementation`() {
        runner()
            .withPropertyValues("app.service.core.persistence=memory")
            .run { context ->
                assertThat(context).doesNotHaveBean(CorePersistenceServices::class.java)
            }
    }

    @Test
    fun `does not activate when the selector is absent`() {
        runner().run { context ->
            assertThat(context).doesNotHaveBean(CorePersistenceServices::class.java)
        }
    }

    @Test
    fun `does not activate when the selector is the empty string`() {
        runner()
            .withPropertyValues("app.service.core.persistence=")
            .run { context ->
                assertThat(context).doesNotHaveBean(CorePersistenceServices::class.java)
            }
    }
}
