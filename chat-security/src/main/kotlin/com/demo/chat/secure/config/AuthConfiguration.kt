package com.demo.chat.secure.config

import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.TypeUtil
import com.demo.chat.secure.AuthMetadataPrincipleKeySearch
import com.demo.chat.secure.AuthMetadataTargetKeySearch
import com.demo.chat.secure.AuthSummarizer
import com.demo.chat.secure.service.AbstractAuthenticationService
import com.demo.chat.secure.service.AuthorizationMetadataService
import com.demo.chat.secure.service.ChatAuthenticationManager
import com.demo.chat.service.UserIndexService
import com.demo.chat.service.UserPersistence
import com.demo.chat.service.security.*
import org.springframework.context.annotation.Bean


open class AuthConfiguration<T>(
    private val keyTypeUtil: TypeUtil<T>,
    private val anonymousKey: Key<T>
) {
    @Bean
    open fun authorizationService(
        authPersist: AuthMetaPersistence<T>,
        authIndex: AuthMetaIndex<T, IndexSearchRequest>
    ): AuthorizationService<T, AuthMetadata<T>, IndexSearchRequest> =
        AuthorizationMetadataService(
            authPersist,
            authIndex,
            AuthMetadataPrincipleKeySearch(keyTypeUtil),
            AuthMetadataTargetKeySearch(keyTypeUtil),
            { anonymousKey },
            { m -> m.key },
            AuthSummarizer { a, b -> keyTypeUtil.compare(a.key.id, b.key.id) }
        )

    @Bean
    open fun authenticationService(
        userIndex: UserIndexService<T, IndexSearchRequest>,
        secretStore: UserCredentialSecretsStore<T>
    ) = AbstractAuthenticationService(
        userIndex,
        secretStore,
        { input, secure -> input == secure },
        { username: String -> IndexSearchRequest(UserIndexService.HANDLE, username, 1) })

    @Bean
    open fun authenticationManager(
        userIndex: UserIndexService<T, IndexSearchRequest>,
        secretStore: UserCredentialSecretsStore<T>,
        userPersistence: UserPersistence<T>,
        authorizationService: AuthorizationService<T, AuthMetadata<T>, AuthMetadata<T>>
    ) =
        ChatAuthenticationManager(
            keyTypeUtil,
            authenticationService(userIndex, secretStore),
            userPersistence,
            authorizationService
        )
}