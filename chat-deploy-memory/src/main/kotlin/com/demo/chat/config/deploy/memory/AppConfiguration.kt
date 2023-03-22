package com.demo.chat.config.deploy.memory

import com.demo.chat.config.BaseDomainConfiguration
import com.demo.chat.config.controller.composite.MessageServiceController
import com.demo.chat.config.controller.composite.TopicServiceController
import com.demo.chat.config.controller.composite.UserServiceController
import com.demo.chat.config.controller.core.*
import com.demo.chat.service.actuator.RootKeyEndpoint
import com.demo.chat.config.deploy.init.UserInitializationListener
import com.demo.chat.config.persistence.memory.KeyGenConfiguration
import com.demo.chat.config.persistence.memory.MemoryCorePersistenceServices
import com.demo.chat.config.persistence.memory.MemoryKeyServices
import com.demo.chat.config.persistence.memory.MemorySecretsStoreServiceBeans
import com.demo.chat.config.pubsub.memory.MemoryPubSubConfiguration
import com.demo.chat.config.secure.CompositeAuthConfiguration
import com.demo.chat.config.secure.TransportConfiguration
import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.serializers.DefaultChatJacksonModules
import com.demo.chat.index.lucene.config.LuceneIndexBeans
import com.demo.chat.secure.service.ChatUserDetailsService
import com.demo.chat.service.core.UserIndexService
import com.demo.chat.service.core.UserPersistence
import com.demo.chat.service.security.AuthenticationService
import com.demo.chat.service.security.AuthorizationService
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
import org.springframework.boot.autoconfigure.rsocket.RSocketMessagingAutoConfiguration
import org.springframework.boot.autoconfigure.rsocket.RSocketStrategiesAutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity
import org.springframework.security.config.annotation.rsocket.EnableRSocketSecurity

@Import(
    // Serialization
    JacksonAutoConfiguration::class,
    DefaultChatJacksonModules::class,
    RSocketStrategiesAutoConfiguration::class,
    RSocketMessagingAutoConfiguration::class,
    // TYPES
    BaseDomainConfiguration::class,
    // Transport Security
    TransportConfiguration::class,
    // Services
    KeyGenConfiguration::class,
    MemoryKeyServices::class,
    MemoryCorePersistenceServices::class,
    LuceneIndexBeans::class,
    MemorySecretsStoreServiceBeans::class,
    MemoryPubSubConfiguration::class,
    CompositeAuthConfiguration::class,
    CompositeServiceConfiguration::class,
    // Controllers
    KeyControllersConfiguration::class,
    IndexControllersConfiguration::class,
    PersistenceControllersConfiguration::class,
    PubSubControllerConfiguration::class,
    SecretsStoreControllerConfiguration::class,
    // Composite Controllers
    MessageServiceController::class,
    TopicServiceController::class,
    UserServiceController::class,

    UserInitializationListener::class,
    RootKeyEndpoint::class
)
@EnableRSocketSecurity
@EnableReactiveMethodSecurity
@Configuration
open class AppConfiguration {

    @Bean
    fun <T> chatUserDetailsService(
        persist: UserPersistence<T>,
        index: UserIndexService<T, IndexSearchRequest>,
        auth: AuthenticationService<T>,
        authZ: AuthorizationService<T, String>,
    ): ChatUserDetailsService<T, IndexSearchRequest> = ChatUserDetailsService(
        persist, index, auth, authZ
    ) { name -> IndexSearchRequest(UserIndexService.HANDLE, name, 100) }

}
