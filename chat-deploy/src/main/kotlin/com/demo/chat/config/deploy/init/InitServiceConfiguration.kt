package com.demo.chat.config.deploy.init

import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.TypeUtil
import com.demo.chat.service.composite.ChatUserService
import com.demo.chat.service.core.IKeyService
import com.demo.chat.service.core.KeyValueStore
import com.demo.chat.service.init.InitialUsersService
import com.demo.chat.service.init.RootKeyService
import com.demo.chat.service.security.AuthorizationService
import com.demo.chat.service.security.SecretsStore
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class InitServiceConfiguration {

    @Bean
    @ConditionalOnProperty("app.bootstrap", havingValue = "init")
    fun <T> initializeUsersService(
        userService: ChatUserService<T>,
        authorizationService: AuthorizationService<T, AuthMetadata<T>>,
        secretsStore: SecretsStore<T>,
        initializationProperties: InitializationProperties,
        typeUtil: TypeUtil<T>,
    ) = InitialUsersService(
        userService, authorizationService, secretsStore, initializationProperties, typeUtil
    )

    @Bean
    @ConditionalOnProperty("app.bootstrap")
    fun <T> rootKeysService(
        keyService: IKeyService<T>,
        kvStore: KeyValueStore<String, String>,
        mapper: ObjectMapper,
        @Value("\${app.rootkeys.key}") key: String,
    ) = RootKeyService<T>(keyService, kvStore, mapper, key)
}