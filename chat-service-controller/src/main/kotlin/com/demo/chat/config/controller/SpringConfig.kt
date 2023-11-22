package com.demo.chat.config.controller

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.rsocket.EnableRSocketSecurity

@Configuration
@EnableRSocketSecurity
@ConditionalOnProperty(prefix = "app.service.composite", name = ["auth"])
class SpringConfig