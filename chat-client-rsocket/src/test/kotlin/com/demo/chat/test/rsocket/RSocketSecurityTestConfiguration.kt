package com.demo.chat.test.rsocket

import org.springframework.boot.rsocket.messaging.RSocketStrategiesCustomizer
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity
import org.springframework.security.config.annotation.rsocket.EnableRSocketSecurity
import org.springframework.security.config.annotation.rsocket.RSocketSecurity
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService
import org.springframework.security.core.userdetails.User
import org.springframework.security.rsocket.core.PayloadSocketAcceptorInterceptor
import org.springframework.security.rsocket.metadata.SimpleAuthenticationEncoder
import org.springframework.web.util.pattern.PathPatternRouteMatcher

@EnableRSocketSecurity
@TestConfiguration
class RSocketSecurityTestConfiguration {

    @Bean
    fun rsocketSecurityAuthentication(security: RSocketSecurity)
            : PayloadSocketAcceptorInterceptor = security
        .simpleAuthentication(Customizer.withDefaults())
        .authorizePayload { authorize ->
            authorize
                .setup()
                .permitAll() // This 'setup' access works on connect!
                .anyExchange()
                .permitAll()
                .anyRequest()
                .permitAll()

        }
        .build()

    @Bean
    fun authentication(): MapReactiveUserDetailsService {
        val user = User.
            withDefaultPasswordEncoder()
            .username("user")
            .password("password")
            .roles("TEST")
            .build()
        return MapReactiveUserDetailsService(user)
    }

    @Bean
    fun rSocketStrategiesCustomizer(): RSocketStrategiesCustomizer =
        RSocketStrategiesCustomizer { strategies ->
            strategies.apply {
                encoder(SimpleAuthenticationEncoder())
                routeMatcher(PathPatternRouteMatcher())
            }
        }
}