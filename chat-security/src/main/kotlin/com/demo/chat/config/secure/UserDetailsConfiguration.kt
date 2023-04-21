package com.demo.chat.config.secure

import com.demo.chat.config.SecretsStoreBeans
import com.demo.chat.secure.service.ChatUserDetailsService
import com.demo.chat.service.composite.ChatUserService
import com.demo.chat.service.security.AuthenticationService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@ConditionalOnProperty("app.service.security.userdetails")
@Configuration
class UserDetailsConfiguration {

    @Bean
    fun <T> chatUserDetailsService(
        userService: ChatUserService<T>,
        secretsBeans: SecretsStoreBeans<T>,
        auth: AuthenticationService<T>,
    ): ChatUserDetailsService<T> = ChatUserDetailsService(userService, secretsBeans.secretsStore(), auth)
}