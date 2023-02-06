package com.demo.chat.authserv

import com.demo.chat.config.KeyServiceBeans
import com.demo.chat.index.lucene.config.LuceneIndexBeans
import com.demo.chat.persistence.memory.config.InMemoryPersistenceBeans
import com.demo.chat.persistence.memory.config.LongKeyServiceBeans
import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.TypeUtil
import com.demo.chat.secure.config.AuthConfiguration
import com.demo.chat.secure.service.ChatUserDetailsService
import com.demo.chat.service.core.UserIndexService
import com.demo.chat.service.core.UserPersistence
import com.demo.chat.service.security.AuthenticationService
import com.demo.chat.service.security.AuthorizationService
import com.demo.chat.persistence.memory.impl.SecretsStoreInMemory
import com.demo.chat.service.security.UserCredentialSecretsStore
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import java.util.function.Supplier

@Profile("local-store")
@Configuration
class ChatConfiguration {

    @Bean
    fun longTypeUtil(): TypeUtil<Long> = TypeUtil.LongUtil

    @Bean
    fun anonymousKeySupplier(): Supplier<Key<Long>> = Supplier { Key.funKey(0L) }

    @Bean
    fun <T>passwordStoreInMemory(typeUtil: TypeUtil<T>): UserCredentialSecretsStore<T> = SecretsStoreInMemory()

    @Bean
    fun <T>chatUserDetailsService(
        persist: UserPersistence<T>,
        index: UserIndexService<T, IndexSearchRequest>,
        auth: AuthenticationService<T>,
        authZ: AuthorizationService<T, String, String>
        ,
    ): ChatUserDetailsService<T, IndexSearchRequest> = ChatUserDetailsService(
        persist,index,auth,authZ
    ) { name ->
        IndexSearchRequest(UserIndexService.HANDLE, name, 100)
    }
}


@Configuration
class LongMemoryKeyServiceBeans : LongKeyServiceBeans()

@Configuration
class PersistenceBeans<T>(keyFactory: KeyServiceBeans<T>) :
    InMemoryPersistenceBeans<T, String>(keyFactory.keyService())

@Configuration
class IndexBeans<T>(typeUtil: TypeUtil<T>) : LuceneIndexBeans<T>(
    typeUtil
)

@Configuration
class ChatSecurityConfiguration<T>(typeUtil: TypeUtil<T>,
                                   anonKeySupply: Supplier<Key<T>>) :
    AuthConfiguration<T>(typeUtil, anonKeySupply)
