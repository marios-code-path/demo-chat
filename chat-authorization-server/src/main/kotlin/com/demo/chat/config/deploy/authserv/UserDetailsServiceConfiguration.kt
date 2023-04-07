package com.demo.chat.config.deploy.authserv

import com.demo.chat.config.SecretsStoreBeans
import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.secure.service.ChatUserDetailsService
import com.demo.chat.service.core.UserIndexService
import com.demo.chat.service.core.UserPersistence
import com.demo.chat.service.security.AuthenticationService
import com.demo.chat.service.security.AuthorizationService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsPasswordService
import org.springframework.security.core.userdetails.UserDetailsService

@Configuration
class UserDetailsServiceConfiguration {
    @Bean
    fun <T> chatUserDetailsService(
        persist: UserPersistence<T>,
        index: UserIndexService<T, IndexSearchRequest>,
        auth: AuthenticationService<T>,
        authZ: AuthorizationService<T, String>,
        secrets: SecretsStoreBeans<T>,
    ): ChatUserDetailsService<T, IndexSearchRequest> = ChatUserDetailsService(
        persist, index, auth, authZ, secrets.secretsStore()
    ) { name -> IndexSearchRequest(UserIndexService.HANDLE, name, 100) }

    @Bean
    fun <T> standardUserDetailsService(uds: ChatUserDetailsService<T, IndexSearchRequest>): UserDetailsService {
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