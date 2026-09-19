package com.demo.chat.config.deploy.authserv

import com.demo.chat.security.service.CoreUserDetailsService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsPasswordService
import org.springframework.security.core.userdetails.UserDetailsService

/**
 * The blocking view of the reactive user details service.
 *
 * **This module runs on spring-boot-starter-web, so Spring Security asks for
 * the blocking interfaces.** The reactive `CoreUserDetailsService` holds the
 * behaviour, and this adapter only blocks on it.
 *
 * Spring Security 7 adopts JSpecify, so both methods now return a non-null
 * `UserDetails`, and `updatePassword` takes a nullable password. That is the
 * same shape as the Reactor 3.8 and JUnit 6 work.
 *
 * **The null password contract is not decided here.** The owner approved a
 * refusal on 2026-09-18, and `CoreUserDetailsService.updatePassword` carries
 * it: a null password reports `IllegalArgumentException` and never reaches
 * the credential store. This adapter passes the value straight through, so
 * the refusal keeps one home. See CHAT-cophllrg.
 */
@Configuration
class BlockingUserDetailsServiceConfiguration {

    @Bean
    fun <T> authServUserDetailsService(uds: CoreUserDetailsService<T>): UserDetailsService {
        return object : UserDetailsService, UserDetailsPasswordService {

            /**
             * `CoreUserDetailsService.findByUsername` reports
             * `UsernameNotFoundException` for an absent user, so an empty
             * signal here is a broken contract rather than a miss. The
             * failure names it instead of asserting non-null.
             */
            override fun loadUserByUsername(username: String): UserDetails =
                uds.findByUsername(username).block()
                    ?: error("The user details service answered no value for $username")

            override fun updatePassword(user: UserDetails, newPassword: String?): UserDetails =
                uds.updatePassword(user, newPassword).block()
                    ?: error("The user details service answered no value for ${user.username}")
        }
    }
}
