package com.demo.chat.config.secure

import com.demo.chat.config.IndexServiceBeans
import com.demo.chat.config.PersistenceServiceBeans
import com.demo.chat.config.SecretsStoreBeans
import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.Key
import com.demo.chat.domain.knownkey.AnonymousKey
import com.demo.chat.secure.Summarizer
import com.demo.chat.secure.access.AuthMetadataAccessBroker
import com.demo.chat.secure.service.AbstractAuthenticationService
import com.demo.chat.secure.service.AuthorizationMetadataService
import com.demo.chat.secure.service.ChatAuthenticationManager
import com.demo.chat.service.security.AuthenticationService
import com.demo.chat.service.security.AuthorizationService
import org.springframework.context.annotation.Bean
import java.util.function.Supplier

open class BaseAuthConfiguration<T, V, Q>(
    private val indexServices: IndexServiceBeans<T, V, Q>,
    private val persistServices: PersistenceServiceBeans<T, V>,
    private val anonKeySupplier: Supplier<AnonymousKey<T>>,
    private val secretsStoreBeans: SecretsStoreBeans<T>,
    private val authSummarizer: Summarizer<AuthMetadata<T>, Key<T>>,
    private val authMetaPrincipalSearch: (Key<T>) -> Q,
    private val authMetaTargetSearch: (Key<T>) -> Q,
    private val userHandleSearch: (String) -> Q,
    private val passwordValidator: (String, String) -> Boolean //{ input, secure -> input == secure }
) {
    @Bean
    open fun authorizationService(): AuthorizationService<T, AuthMetadata<T>> =
        AuthorizationMetadataService(
            persistServices.authMetaPersistence(),
            indexServices.authMetadataIndex(),
            authMetaPrincipalSearch,
            authMetaTargetSearch,
            anonKeySupplier,
            authSummarizer,
        )

    @Bean
    open fun authenticationService(): AuthenticationService<T> = AbstractAuthenticationService(
        indexServices.userIndex(),
        secretsStoreBeans.secretsStore(),
        passwordValidator,
        userHandleSearch
    )

    @Bean
    // This is going to be deprecated on the fact that we
    // can use an authorization server instead.
    open fun authenticationManager(authenticationService: AuthenticationService<T>) =
        ChatAuthenticationManager(
            authenticationService,
            persistServices.userPersistence(),
        )

    @Bean
    open fun accessBroker(authMan: AuthorizationService<T, AuthMetadata<T>>) = AuthMetadataAccessBroker(authMan)
}