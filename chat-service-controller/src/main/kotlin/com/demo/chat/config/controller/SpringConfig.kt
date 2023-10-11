package com.demo.chat.config.controller

import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity
import org.springframework.security.config.annotation.rsocket.EnableRSocketSecurity

@EnableRSocketSecurity
@EnableReactiveMethodSecurity
@Configuration
class SpringConfig