package com.demo.chat.init

import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity

@Profile("shell")
@Configuration
@EnableGlobalMethodSecurity(securedEnabled = true)
class ShellApp