package com.demo.chat.secure.config

import com.demo.chat.domain.*
import com.demo.chat.secure.AuthMetadataPrincipleKeySearch
import com.demo.chat.secure.AuthMetadataTargetKeySearch
import com.demo.chat.secure.AuthSummarizer
import com.demo.chat.secure.service.AbstractAuthenticationService
import com.demo.chat.secure.service.AbstractAuthorizationService
import com.demo.chat.secure.service.ChatAuthenticationManager
import com.demo.chat.service.IndexService
import com.demo.chat.service.PersistenceStore
import com.demo.chat.service.UserIndexService
import com.demo.chat.service.security.AuthorizationService
import com.demo.chat.service.security.SecretsStore
import org.springframework.context.annotation.Bean


open class AuthConfiguration<T>(
    private val keyTypeUtil: TypeUtil<T>,
    private val anonymousKey: Key<T>
) {
    @Bean
    open fun authorizationService(
        authPersist: PersistenceStore<T, AuthMetadata<T>>,
        authIndex: IndexService<T, AuthMetadata<T>, IndexSearchRequest>
    ): AuthorizationService<T, AuthMetadata<T>, AuthMetadata<T>> =
        AbstractAuthorizationService(
            authPersist,
            authIndex,
            AuthMetadataPrincipleKeySearch(keyTypeUtil),
            AuthMetadataTargetKeySearch(keyTypeUtil),
            { anonymousKey },
            { m -> m.key },
            AuthSummarizer { a, b -> keyTypeUtil.compare(a.key.id, b.key.id) }
        )

    @Bean
    open fun chatAuthenticationService(
        userIndex: IndexService<T, User<T>, IndexSearchRequest>,
        secretStore: SecretsStore<T>
    ) = AbstractAuthenticationService(
        userIndex,
        secretStore,
        { input, secure -> input == secure },
        { username: String -> IndexSearchRequest(UserIndexService.HANDLE, username, 1) })

    @Bean
    open fun authenticationManager(
        userIndex: IndexService<T, User<T>, IndexSearchRequest>,
        secretStore: SecretsStore<T>,
        userPersistence: PersistenceStore<T, User<T>>,
        authorizationService: AuthorizationService<T, AuthMetadata<T>, AuthMetadata<T>>
    ) =
        ChatAuthenticationManager(
            keyTypeUtil,
            chatAuthenticationService(userIndex, secretStore),
            userPersistence,
            authorizationService
        )

}