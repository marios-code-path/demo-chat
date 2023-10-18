package com.demo.chat.config.secure

import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.config.SecretsStoreBeans
import com.demo.chat.secure.service.CoreUserDetailsService
import com.demo.chat.service.security.AuthenticationService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@ConditionalOnProperty("app.service.security.userdetails")
@Configuration
class UserDetailsConfiguration {

    @Bean
    fun <T> chatUserDetailsService(
        compositeServices: CompositeServiceBeans<T, String>,
        secretsBeans: SecretsStoreBeans<T>,
        auth: AuthenticationService<T>,
    ): CoreUserDetailsService<T> = CoreUserDetailsService(compositeServices.userService(), secretsBeans.secretsStore(), auth)

}