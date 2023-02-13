package com.demo.chat.init

import org.springframework.context.annotation.Profile
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity

@Profile("shell")
@EnableGlobalMethodSecurity(securedEnabled = true)
class ShellApp