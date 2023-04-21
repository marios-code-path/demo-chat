package com.demo.chat.config.deploy.authserv

import com.demo.chat.secure.service.ChatUserDetailsService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsPasswordService
import org.springframework.security.core.userdetails.UserDetailsService

@Configuration
class UserDetailsServiceConfiguration {

    @Bean
    fun <T> standardUserDetailsService(uds: ChatUserDetailsService<T>): UserDetailsService {
        return object : UserDetailsService, UserDetailsPasswordService {
            override fun loadUserByUsername(username: String): org.springframework.security.core.userdetails.UserDetails? {
                return uds.findByUsername(username).block()
            }

            override fun updatePassword(user: UserDetails, newPassword: String): UserDetails? {
                return uds.updatePassword(user, newPassword).block()
            }
        }
    }
}