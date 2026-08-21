package com.demo.chat.test.pubsub.memory

import com.demo.chat.config.pubsub.memory.MemoryPubSubBeans
import com.demo.chat.domain.LongUtil
import com.demo.chat.domain.TypeUtil
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

class MemoryPubSubBeansConditionTests {

    @Configuration(proxyBeanMethods = false)
    class TypeUtilStub {
        @Bean
        fun typeUtil(): TypeUtil<Long> = LongUtil()
    }

    /**
     * Registers MemoryPubSubBeans as an annotated class so its
     * @ConditionalOnProperty is evaluated. Do not use withBean() here — a
     * supplied bean bypasses conditions entirely, which is the thing under
     * test.
     */
    private fun runner() = ApplicationContextRunner()
        .withUserConfiguration(TypeUtilStub::class.java)
        .withUserConfiguration(MemoryPubSubBeans::class.java)

    @Test
    fun `activates when the selector names memory`() {
        runner()
            .withPropertyValues("app.service.core.pubsub=memory")
            .run { context ->
                assertThat(context).hasSingleBean(MemoryPubSubBeans::class.java)
            }
    }

    @Test
    fun `activates when the selector is absent`() {
        runner().run { context ->
            assertThat(context).hasSingleBean(MemoryPubSubBeans::class.java)
        }
    }

    @Test
    fun `does not activate when the selector names another implementation`() {
        runner()
            .withPropertyValues("app.service.core.pubsub=kafka")
            .run { context ->
                assertThat(context).doesNotHaveBean(MemoryPubSubBeans::class.java)
            }
    }

    @Test
    fun `does not activate when the selector is the empty string`() {
        runner()
            .withPropertyValues("app.service.core.pubsub=")
            .run { context ->
                assertThat(context).doesNotHaveBean(MemoryPubSubBeans::class.java)
            }
    }
}
