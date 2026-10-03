package com.demo.chat.config.auth

import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.config.SecretsStoreBeans
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.security.service.CoreUserDetailsService
import org.springframework.beans.factory.annotation.Value
import com.demo.chat.service.security.AuthenticationService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration


@ConditionalOnProperty(prefix = "app.service.composite", name = ["auth"], havingValue = "true")
@Configuration
class UserDetailsConfiguration {

    /**
     * `app.security.service-accounts` names the handles that hold
     * `ROLE_SERVICE`. The default is `Service`, which `userinit.yml` declares.
     * See `CHAT-rdlghoqe`.
     */
    @Bean
    fun <T> coreUserDetailsService(
        compositeServices: CompositeServiceBeans<T, String>,
        secretsBeans: SecretsStoreBeans<T>,
        auth: AuthenticationService<T>,
        rootKeys: RootKeys<T>,
        @Value("\${app.security.service-accounts:Service}") serviceAccounts: List<String>,
    ): CoreUserDetailsService<T> =
        CoreUserDetailsService(
            compositeServices.userService(),
            secretsBeans.secretsStore(),
            auth,
            { rootKeys.admin() },
            serviceAccounts.filter { it.isNotBlank() }.toSet(),
        )
}
