package com.demo.chat.config.auth

import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity

@Configuration
@EnableReactiveMethodSecurity
class MethodSecurityConfiguration {
}