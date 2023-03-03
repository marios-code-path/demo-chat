package com.demo.chat.config.secure

import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.TypeUtil
import com.demo.chat.domain.knownkey.AnonymousKey
import com.demo.chat.secure.AuthMetadataPrincipleKeySearch
import com.demo.chat.secure.AuthMetadataTargetKeySearch
import com.demo.chat.secure.AuthSummarizer
import com.demo.chat.secure.access.AuthMetadataAccessBroker
import com.demo.chat.secure.service.AbstractAuthenticationService
import com.demo.chat.secure.service.AuthorizationMetadataService
import com.demo.chat.secure.service.ChatAuthenticationManager
import com.demo.chat.service.core.UserIndexService
import com.demo.chat.service.core.UserPersistence
import com.demo.chat.service.security.AuthMetaIndex
import com.demo.chat.service.security.AuthMetaPersistence
import com.demo.chat.service.security.AuthorizationService
import com.demo.chat.service.security.SecretsStore
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.function.Supplier

@Configuration
@ConditionalOnProperty(prefix = "app.service.composite", name = ["auth"])
class AuthConfiguration<T>(
    private val keyTypeUtil: TypeUtil<T>,
    private val anonKeySupplier: Supplier<AnonymousKey<T>>
) {
    @Bean
    fun authorizationService(
        authPersist: AuthMetaPersistence<T>,
        authIndex: AuthMetaIndex<T, IndexSearchRequest>
    ): AuthorizationService<T, AuthMetadata<T>> =
        AuthorizationMetadataService(
            authPersist,
            authIndex,
            AuthMetadataPrincipleKeySearch(keyTypeUtil),
            AuthMetadataTargetKeySearch(keyTypeUtil),
            anonKeySupplier,
            { m -> m.key },
            AuthSummarizer { a, b -> keyTypeUtil.compare(a.key.id, b.key.id) }
        )

    @Bean
    fun authenticationService(
        userIndex: UserIndexService<T, IndexSearchRequest>,
        secretStore: SecretsStore<T>
    ) = AbstractAuthenticationService(
        userIndex,
        secretStore,
        { input, secure -> input == secure },
        { username: String -> IndexSearchRequest(UserIndexService.HANDLE, username, 1) })

    @Bean
    // This is going to be deprecated on the fact that we
    // can use an authorization server instead.
    fun authenticationManager(
        userIndex: UserIndexService<T, IndexSearchRequest>,
        secretStore: SecretsStore<T>,
        userPersistence: UserPersistence<T>,
        authorizationService: AuthorizationService<T, AuthMetadata<T>>
    ) =
        ChatAuthenticationManager(
            keyTypeUtil,
            authenticationService(userIndex, secretStore),
            userPersistence,
            authorizationService
        )

    @Bean
    fun accessBroker(authMan: AuthorizationService<T, AuthMetadata<T>>) = AuthMetadataAccessBroker(authMan)
}