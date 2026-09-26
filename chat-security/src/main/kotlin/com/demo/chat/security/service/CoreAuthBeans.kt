package com.demo.chat.security.service

import com.demo.chat.service.core.KeyVerifier

import com.demo.chat.service.core.IKeyService

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
    open fun authorizationService(): AuthorizationService<T, AuthMetadata<T>> =
        CoreAuthorizationService(
            persistServices.authMetaPersistence(),
            indexServices.authMetadataIndex(),
            authMetaPrincipalSearch,
            authMetaTargetSearch,
            { rootKeys.anon() },
            { rootKeys.of(ChatDomain.USER) },
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
    open fun accessBroker(authMan: AuthorizationService<T, AuthMetadata<T>>, keyService: IKeyService<T>) =
        AuthMetadataAccessBroker(authMan, KeyVerifier(keyService, rootKeys))
}