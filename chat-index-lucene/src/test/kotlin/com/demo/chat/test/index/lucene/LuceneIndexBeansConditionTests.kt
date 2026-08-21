package com.demo.chat.test.index.lucene

import com.demo.chat.config.LuceneIndexBeans
import com.demo.chat.domain.LongUtil
import com.demo.chat.domain.TypeUtil
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.ConversionService
import org.springframework.core.convert.support.DefaultConversionService

/**
 * `app.service.core.index` names one index backend. Lucene is the in-process
 * default, so it activates when the selector is absent — but never when the
 * selector names a different backend.
 */
class LuceneIndexBeansConditionTests {

    @Configuration(proxyBeanMethods = false)
    class IndexDependencyStubs {
        @Bean
        fun typeUtil(): TypeUtil<Long> = LongUtil()

        @Bean
        fun conversionService(): ConversionService = DefaultConversionService()
    }

    /**
     * Registers LuceneIndexBeans as an annotated class so its
     * @ConditionalOnProperty is evaluated. Do not use withBean() here — a
     * supplied bean bypasses conditions entirely, which is the thing under
     * test.
     */
    private fun runner() = ApplicationContextRunner()
        .withUserConfiguration(IndexDependencyStubs::class.java)
        .withUserConfiguration(LuceneIndexBeans::class.java)

    @Test
    fun `activates when the selector names lucene`() {
        runner()
            .withPropertyValues("app.service.core.index=lucene")
            .run { context ->
                assertThat(context).hasSingleBean(LuceneIndexBeans::class.java)
            }
    }

    @Test
    fun `activates when the selector is absent`() {
        runner().run { context ->
            assertThat(context).hasSingleBean(LuceneIndexBeans::class.java)
        }
    }

    @Test
    fun `does not activate when the selector names another implementation`() {
        runner()
            .withPropertyValues("app.service.core.index=cassandra")
            .run { context ->
                assertThat(context).doesNotHaveBean(LuceneIndexBeans::class.java)
            }
    }

    @Test
    fun `does not activate when the selector is the empty string`() {
        runner()
            .withPropertyValues("app.service.core.index=")
            .run { context ->
                assertThat(context).doesNotHaveBean(LuceneIndexBeans::class.java)
            }
    }
}
