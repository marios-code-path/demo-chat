package com.demo.chat.authserv

import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.secure.service.ChatUserDetailsService
import com.demo.chat.service.core.UserIndexService
import com.demo.chat.service.core.UserPersistence
import com.demo.chat.service.security.AuthenticationService
import com.demo.chat.service.security.AuthorizationService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Profile("local-store")
@Configuration
class ChatConfiguration {

    @Bean
    fun <T> chatUserDetailsService(
        persist: UserPersistence<T>,
        index: UserIndexService<T, IndexSearchRequest>,
        auth: AuthenticationService<T>,
        authZ: AuthorizationService<T, String, String>,
    ): ChatUserDetailsService<T, IndexSearchRequest> = ChatUserDetailsService(
        persist, index, auth, authZ
    ) { name ->
        IndexSearchRequest(UserIndexService.HANDLE, name, 100)
    }
}
