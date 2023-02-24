package com.demo.chat.test.controller

import com.demo.chat.convert.JsonNodeToAnyConverter
import com.demo.chat.domain.LongUtil
import com.demo.chat.domain.TypeUtil
import com.demo.chat.domain.serializers.JacksonModules
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
import org.springframework.boot.autoconfigure.rsocket.RSocketMessagingAutoConfiguration
import org.springframework.boot.autoconfigure.rsocket.RSocketRequesterAutoConfiguration
import org.springframework.boot.autoconfigure.rsocket.RSocketServerAutoConfiguration
import org.springframework.boot.autoconfigure.rsocket.RSocketStrategiesAutoConfiguration
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity
import org.springframework.security.config.annotation.rsocket.EnableRSocketSecurity
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService
import org.springframework.security.core.userdetails.ReactiveUserDetailsService
import org.springframework.security.core.userdetails.User

class TestModules : JacksonModules(JsonNodeToAnyConverter, JsonNodeToAnyConverter)

@SpringBootConfiguration
@EnableReactiveMethodSecurity
@EnableRSocketSecurity
@Import(
    TestModules::class,
    JacksonAutoConfiguration::class,
    RSocketStrategiesAutoConfiguration::class,
    RSocketMessagingAutoConfiguration::class,
    RSocketServerAutoConfiguration::class,
    RSocketRequesterAutoConfiguration::class,
    CoreKeyServices::class
)
class TestConfig {
    @Bean
    fun typeUtil(): TypeUtil<Long> = LongUtil()

    @Bean
    fun userDetailService(): ReactiveUserDetailsService =
        MapReactiveUserDetailsService(
            User.builder()
                .username("testuser")
                .password("{noop}nopassword")
                .roles("key")
                .build(),
            User.builder()
                .username("anonymous")
                .password("{noop}nopassword")
                .roles("view")
                .build()
        )
}