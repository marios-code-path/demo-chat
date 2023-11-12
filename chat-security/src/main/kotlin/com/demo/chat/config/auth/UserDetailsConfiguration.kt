package com.demo.chat.config.auth

import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.config.SecretsStoreBeans
import com.demo.chat.security.service.CoreUserDetailsService
import com.demo.chat.service.security.AuthenticationService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration


@ConditionalOnProperty(prefix = "app.service.composite", name = ["auth"])
@Configuration
class UserDetailsConfiguration {

    @Bean
    fun <T> chatUserDetailsService(
        compositeServices: CompositeServiceBeans<T, String>,
        secretsBeans: SecretsStoreBeans<T>,
        auth: AuthenticationService<T>
    ): CoreUserDetailsService<T> =
        CoreUserDetailsService(compositeServices.userService(), secretsBeans.secretsStore(), auth)

}