package com.demo.chat.test.controller

import org.springframework.boot.SpringBootConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity
import org.springframework.security.config.annotation.rsocket.EnableRSocketSecurity
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService
import org.springframework.security.core.userdetails.ReactiveUserDetailsService
import org.springframework.security.core.userdetails.User


//@SpringBootConfiguration
//@EnableReactiveMethodSecurity
//@EnableRSocketSecurity
class TestConfigurationAuthorizationServices {

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