package com.demo.chat.test.config

import com.demo.chat.config.controller.SpringConfig
import com.demo.chat.config.rsocket.RSocketSecurityConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.security.autoconfigure.rsocket.RSocketSecurityAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.messaging.rsocket.annotation.support.RSocketMessageHandler
import org.springframework.security.authentication.ReactiveAuthenticationManager
import org.springframework.security.core.Authentication
import org.springframework.boot.rsocket.server.RSocketServerCustomizer
import reactor.core.publisher.Mono

class RSocketSecurityAutoConfigurationDuplicationTests {

    private val runner = ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(RSocketSecurityAutoConfiguration::class.java))
        .withUserConfiguration(
            SpringConfig::class.java,
            RSocketSecurityConfiguration::class.java,
            TestAuthenticationConfiguration::class.java,
        )
        .withPropertyValues(
            "app.server.proto=rsocket",
            "app.service.composite.auth=true",
        )

    @Test
    fun `an app without the RSocket security exclusion installs two server customizers`() {
        runner.run { context ->
            assertThat(context.getBeansOfType(RSocketServerCustomizer::class.java))
                .describedAs("RSocket server customizers")
                .hasSize(2)
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    class TestAuthenticationConfiguration {

        @Bean
        fun authenticationManager(): ReactiveAuthenticationManager =
            ReactiveAuthenticationManager { authentication: Authentication -> Mono.just(authentication) }

        @Bean
        fun rSocketMessageHandler() = RSocketMessageHandler()
    }
}
