package com.demo.chat.test.index.cassandra

import com.demo.chat.config.index.cassandra.IndexServiceConfiguration
import com.demo.chat.domain.LongUtil
import com.demo.chat.domain.TypeUtil
import com.demo.chat.index.cassandra.repository.AuthMetadataByPrincipalRepository
import com.demo.chat.index.cassandra.repository.AuthMetadataByTargetRepository
import com.demo.chat.index.cassandra.repository.ChatMessageByTopicRepository
import com.demo.chat.index.cassandra.repository.ChatMessageByUserRepository
import com.demo.chat.index.cassandra.repository.ChatUserHandleRepository
import com.demo.chat.index.cassandra.repository.TopicByNameRepository
import com.demo.chat.index.cassandra.repository.TopicMembershipByMemberOfRepository
import com.demo.chat.index.cassandra.repository.TopicMembershipByMemberRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate

/**
 * `app.service.core.index` names one index backend. Cassandra activates only
 * when the selector names it — never by default, and never alongside Lucene.
 *
 * The repositories are mocked because the condition, not the query behaviour,
 * is under test; the configuration stores its dependencies and builds the
 * index services lazily, so no Cassandra connection is involved.
 */
class CassandraIndexBeansConditionTests {

    @Configuration(proxyBeanMethods = false)
    class CassandraDependencyStubs {
        @Bean
        fun typeUtil(): TypeUtil<Long> = LongUtil()

        @Bean
        fun cassandraTemplate(): ReactiveCassandraTemplate = mock(ReactiveCassandraTemplate::class.java)

        @Bean
        fun userHandleRepo(): ChatUserHandleRepository<Long> = mock(ChatUserHandleRepository::class.java) as ChatUserHandleRepository<Long>

        @Bean
        fun nameRepo(): TopicByNameRepository<Long> = mock(TopicByNameRepository::class.java) as TopicByNameRepository<Long>

        @Bean
        fun byMemberRepo(): TopicMembershipByMemberRepository<Long> = mock(TopicMembershipByMemberRepository::class.java) as TopicMembershipByMemberRepository<Long>

        @Bean
        fun byMemberOfRepo(): TopicMembershipByMemberOfRepository<Long> = mock(TopicMembershipByMemberOfRepository::class.java) as TopicMembershipByMemberOfRepository<Long>

        @Bean
        fun byUserRepo(): ChatMessageByUserRepository<Long> = mock(ChatMessageByUserRepository::class.java) as ChatMessageByUserRepository<Long>

        @Bean
        fun byTopicRepo(): ChatMessageByTopicRepository<Long> = mock(ChatMessageByTopicRepository::class.java) as ChatMessageByTopicRepository<Long>

        @Bean
        fun principalRepo(): AuthMetadataByPrincipalRepository<Long> = mock(AuthMetadataByPrincipalRepository::class.java) as AuthMetadataByPrincipalRepository<Long>

        @Bean
        fun targetRepo(): AuthMetadataByTargetRepository<Long> = mock(AuthMetadataByTargetRepository::class.java) as AuthMetadataByTargetRepository<Long>
    }

    /**
     * Registers IndexServiceConfiguration as an annotated class so its
     * @ConditionalOnProperty is evaluated. Do not use withBean() here — a
     * supplied bean bypasses conditions entirely, which is the thing under
     * test.
     */
    private fun runner() = ApplicationContextRunner()
        .withUserConfiguration(CassandraDependencyStubs::class.java)
        .withUserConfiguration(IndexServiceConfiguration::class.java)

    @Test
    fun `activates when the selector names cassandra`() {
        runner()
            .withPropertyValues("app.service.core.index=cassandra")
            .run { context ->
                assertThat(context).hasSingleBean(IndexServiceConfiguration::class.java)
            }
    }

    @Test
    fun `does not activate when the selector names another implementation`() {
        runner()
            .withPropertyValues("app.service.core.index=lucene")
            .run { context ->
                assertThat(context).doesNotHaveBean(IndexServiceConfiguration::class.java)
            }
    }

    @Test
    fun `does not activate when the selector is absent`() {
        runner().run { context ->
            assertThat(context).doesNotHaveBean(IndexServiceConfiguration::class.java)
        }
    }

    @Test
    fun `does not activate when the selector is the empty string`() {
        runner()
            .withPropertyValues("app.service.core.index=")
            .run { context ->
                assertThat(context).doesNotHaveBean(IndexServiceConfiguration::class.java)
            }
    }
}
