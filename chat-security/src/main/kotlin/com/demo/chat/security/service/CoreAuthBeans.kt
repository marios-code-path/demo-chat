package com.demo.chat.security.service

import com.demo.chat.config.KeyServiceBeans

import com.demo.chat.service.core.KeyVerifier


import com.demo.chat.config.IndexServiceBeans
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.config.PersistenceServiceBeans
import com.demo.chat.config.SecretsStoreBeans
import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.Key
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.security.Summarizer
import com.demo.chat.security.access.AuthMetadataAccessBroker
import com.demo.chat.service.security.AuthenticationService
import com.demo.chat.service.security.AuthorizationService
import org.springframework.context.annotation.Bean

open class CoreAuthBeans<T, V, Q>(
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
    open fun authorizationService(keyBeans: KeyServiceBeans<T>): AuthorizationService<T, AuthMetadata<T>> =
        CoreAuthorizationService(
            persistServices.authMetaPersistence(),
            indexServices.authMetadataIndex(),
            authMetaPrincipalSearch,
            authMetaTargetSearch,
            { rootKeys.anon() },
            { rootKeys.of(ChatDomain.USER) },
            authSummarizer,
            KeyVerifier(keyBeans.keyService(), rootKeys),
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
    // A client composition reaches its key service through KeyServiceBeans and
    // registers no IKeyService bean. Every composition registers KeyServiceBeans.
    open fun accessBroker(authMan: AuthorizationService<T, AuthMetadata<T>>, keyBeans: KeyServiceBeans<T>) =
        AuthMetadataAccessBroker(authMan, KeyVerifier(keyBeans.keyService(), rootKeys))
}