package com.demo.chat.config.secure

import com.demo.chat.config.IndexServiceBeans
import com.demo.chat.config.PersistenceServiceBeans
import com.demo.chat.config.SecretsStoreBeans
import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.Key
import com.demo.chat.domain.knownkey.Anon
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.secure.Summarizer
import com.demo.chat.secure.access.AuthMetadataAccessBroker
import com.demo.chat.secure.service.CoreAuthenticationService
import com.demo.chat.secure.service.CoreReactiveAuthorizationService
import com.demo.chat.service.security.AuthenticationService
import com.demo.chat.service.security.AuthorizationService
import org.springframework.context.annotation.Bean
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.crypto.password.PasswordEncoder

open class BaseAuthConfiguration<T, V, Q>(
    private val rootKeys: RootKeys<T>,
    private val indexServices: IndexServiceBeans<T, V, Q>,
    private val persistServices: PersistenceServiceBeans<T, V>,
    private val secretsStoreBeans: SecretsStoreBeans<T>,
    private val authSummarizer: Summarizer<AuthMetadata<T>, Key<T>>,
    private val authMetaPrincipalSearch: (Key<T>) -> Q,
    private val authMetaTargetSearch: (Key<T>) -> Q,
    private val userHandleSearch: (String) -> Q,
    private val passwordValidator: (String, String) -> Boolean //{ input, secure -> input == secure }
) {

    @Bean
    open fun authorizationService(): AuthorizationService<T, AuthMetadata<T>> =
        CoreReactiveAuthorizationService(
            persistServices.authMetaPersistence(),
            indexServices.authMetadataIndex(),
            authMetaPrincipalSearch,
            authMetaTargetSearch,
            { rootKeys.getRootKey(Anon::class.java) },
            authSummarizer,
        )

    @Bean
    open fun authenticationService(): AuthenticationService<T> = CoreAuthenticationService(
        indexServices.userIndex(),
        secretsStoreBeans.secretsStore(),
        passwordValidator,
        userHandleSearch,
        rootKeys
    )

    @Bean
    open fun encFactory(): PasswordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder()

    @Bean
    open fun accessBroker(authMan: AuthorizationService<T, AuthMetadata<T>>) = AuthMetadataAccessBroker(authMan)
}