package com.demo.chat.deploy.memory.config

import com.demo.chat.domain.Key
import com.demo.chat.domain.TypeUtil
import com.demo.chat.secure.config.AuthConfiguration
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity
import java.util.function.Supplier

///**
// * TODO: Refactor till its invisible or autowired by conditionals
// */
//@Configuration
//@Profile("exec-chat")
//class AuthResourceConfiguration<T>(
//    typeUtil: TypeUtil<T>,
//    anonKeySupply: Supplier<Key<T>>
//) : AuthConfiguration<T>(typeUtil, anonKeySupply)
