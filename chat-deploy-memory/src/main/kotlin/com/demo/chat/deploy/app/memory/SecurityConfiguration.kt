package com.demo.chat.deploy.app.memory

import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.TypeUtil
import com.demo.chat.domain.User
import com.demo.chat.secure.AuthMetadataPrincipleKeySearch
import com.demo.chat.secure.AuthMetadataTargetKeySearch
import com.demo.chat.secure.AuthSummarizer
import com.demo.chat.secure.service.AbstractAuthenticationService
import com.demo.chat.secure.service.AbstractAuthorizationService
import com.demo.chat.service.IndexService
import com.demo.chat.service.PersistenceStore
import com.demo.chat.service.UserIndexService
import com.demo.chat.service.security.AuthorizationService
import com.demo.chat.service.security.SecretsStore
import org.springframework.context.annotation.Bean


open class SecurityConfiguration {

    @Bean
    fun authorizationService(
        typeUtil: TypeUtil<Long>,
        authPersist: PersistenceStore<Long, AuthMetadata<Long>>,
        authIndex: IndexService<Long, AuthMetadata<Long>, IndexSearchRequest>
    ): AuthorizationService<Long, AuthMetadata<Long>, AuthMetadata<Long>> =
        AbstractAuthorizationService(
            authPersist,
            authIndex,
            AuthMetadataPrincipleKeySearch(typeUtil),
            AuthMetadataTargetKeySearch(typeUtil),
            { SampleAppSecurityRunner.ANONYMOUS_KEY },
            { m -> m.key },
            AuthSummarizer { a, b -> (a.key.id - b.key.id).toInt() }
        )

    @Bean
    fun chatAuthenticationService(
        userIndex: IndexService<Long, User<Long>, IndexSearchRequest>,
        secretsStore: SecretsStore<Long>
    ) = AbstractAuthenticationService(
        userIndex,
        secretsStore,
        { input, secure -> input == secure },
        { user: String -> IndexSearchRequest(UserIndexService.HANDLE, user, 1) })

    @Bean
    fun authenticationManager(
        userIndex: IndexService<Long, User<Long>, IndexSearchRequest>,
        passStore: SecretsStore<Long>,
        userPersistence: PersistenceStore<Long, User<Long>>,
        authorizationService: AuthorizationService<Long, AuthMetadata<Long>, AuthMetadata<Long>>
    ) =
        SampleAuthenticationManager(
            chatAuthenticationService(userIndex, passStore),
            userPersistence,
            authorizationService
        )

}