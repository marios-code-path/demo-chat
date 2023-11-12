package com.demo.chat.config.auth

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity

@Configuration
@EnableReactiveMethodSecurity
@ConditionalOnProperty(prefix = "app.service.composite", name = ["auth"])
class MethodSecurityConfiguration {
}