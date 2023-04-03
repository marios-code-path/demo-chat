package com.demo.chat.config.deploy.security

import com.demo.chat.config.SecretsStoreBeans
import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.secure.service.ChatUserDetailsService
import com.demo.chat.service.core.UserIndexService
import com.demo.chat.service.core.UserPersistence
import com.demo.chat.service.security.AuthenticationService
import com.demo.chat.service.security.AuthorizationService
import com.demo.chat.service.security.SecretsStore
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty("app.service.security.userdetails")
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
}