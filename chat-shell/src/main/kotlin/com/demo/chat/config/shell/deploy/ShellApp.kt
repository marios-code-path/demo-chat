package com.demo.chat.config.shell.deploy

import org.springframework.boot.rsocket.messaging.RSocketStrategiesCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.rsocket.EnableRSocketSecurity
import org.springframework.security.rsocket.metadata.SimpleAuthenticationEncoder
import org.springframework.web.reactive.config.EnableWebFlux
import org.springframework.web.util.pattern.PathPatternRouteMatcher
import java.util.*


@EnableRSocketSecurity
@EnableWebFlux
@Configuration
@ComponentScan("com.demo.chat.shell")
class ShellApp {

    @Bean
    fun rSocketStrategiesCustomizer(): RSocketStrategiesCustomizer =
        RSocketStrategiesCustomizer { strategies ->
            strategies.apply {
                encoder(SimpleAuthenticationEncoder())
                routeMatcher(PathPatternRouteMatcher())
            }
        }
}

