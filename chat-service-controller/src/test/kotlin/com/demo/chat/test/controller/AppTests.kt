package com.demo.chat.test.controller

import com.demo.chat.config.controller.KeyControllersConfiguration
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.autoconfigure.rsocket.RSocketMessagingAutoConfiguration
import org.springframework.boot.autoconfigure.rsocket.RSocketRequesterAutoConfiguration
import org.springframework.boot.autoconfigure.rsocket.RSocketServerAutoConfiguration
import org.springframework.boot.autoconfigure.rsocket.RSocketStrategiesAutoConfiguration
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.security.config.annotation.rsocket.EnableRSocketSecurity
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService
import org.springframework.security.core.userdetails.ReactiveUserDetailsService
import org.springframework.security.core.userdetails.User
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.junit.jupiter.SpringExtension

//@ExtendWith(SpringExtension::class)
class AppTests {

    private lateinit var requesterBuilder: RSocketRequester.Builder

    // TODO Implement basic controller tests!
    //@Test
    @WithMockUser("testuser", roles = ["KEY"])
    fun testLoads() {

    }

    @TestConfiguration
    @EnableRSocketSecurity
    @Import(
        RSocketStrategiesAutoConfiguration::class, RSocketRequesterAutoConfiguration::class,
        RSocketServerAutoConfiguration::class, RSocketMessagingAutoConfiguration::class,
        KeyControllersConfiguration::class
    )
    class TestConfig {
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
}