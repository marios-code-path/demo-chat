package com.demo.chat.deploy.memory

import org.springframework.security.authentication.AbstractUserDetailsReactiveAuthenticationManager
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import reactor.core.publisher.Mono

class ActuatorAuthenticationManager() : AbstractUserDetailsReactiveAuthenticationManager() {

    private val userDetailService = MapReactiveUserDetailsService(
        User.withUsername("actuator")
            .password("{noop}actuator")
            .roles("ADMIN")
            .build()
    )

    override fun retrieveUser(username: String): Mono<UserDetails> {
        return userDetailService.findByUsername(username)
    }
}