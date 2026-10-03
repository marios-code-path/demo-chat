package com.demo.chat.test.config

import com.demo.chat.config.rsocket.RSocketServerConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.mock
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.security.rsocket.core.PayloadSocketAcceptorInterceptor

class RSocketSecurityRequiredTests {

    private val runner = ApplicationContextRunner()
        .withUserConfiguration(RSocketServerConfiguration::class.java)
        .withPropertyValues("app.server.proto=rsocket")
        .withBean(PayloadSocketAcceptorInterceptor::class.java, { mock(PayloadSocketAcceptorInterceptor::class.java) })

    @ParameterizedTest
    @ValueSource(strings = ["absent", "", "false", "TRUE", "invalid"])
    fun `an interceptor cannot bypass the explicit auth property`(value: String) {
        val configured = if (value == "absent") runner else runner.withPropertyValues("app.service.composite.auth=$value")
        configured.run { context ->
            assertThat(context).hasFailed()
            assertThat(context.startupFailure)
                .hasStackTraceContaining("An RSocket server requires app.service.composite.auth=true.")
        }
    }

    @Test
    fun `an explicit true value with an interceptor permits startup`() {
        runner.withPropertyValues("app.service.composite.auth=true").run { context ->
            assertThat(context).hasNotFailed()
            assertThat(context).hasBean("requireRSocketSecurity")
            assertThat(context).hasBean("rSocketStrategiesCustomizer")
            assertThat(context).hasBean("messageHandlerCustomizer")
        }
    }
}
