package com.demo.chat.config.deploy.memory

import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity
import org.springframework.security.config.annotation.rsocket.EnableRSocketSecurity

@Import(JacksonAutoConfiguration::class)
@EnableRSocketSecurity
@EnableReactiveMethodSecurity
@Configuration
open class AppConfiguration {

}