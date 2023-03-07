package com.demo.chat.config.deploy.memory

import com.demo.chat.config.BaseDomainConfiguration
import com.demo.chat.config.controller.composite.MessageServiceController
import com.demo.chat.config.controller.composite.TopicServiceController
import com.demo.chat.config.controller.composite.UserServiceController
import com.demo.chat.config.controller.core.*
import com.demo.chat.domain.serializers.DefaultChatJacksonModules
import com.demo.chat.index.lucene.config.LuceneIndexBeans
import com.demo.chat.config.persistence.memory.MemoryKeyServices
import com.demo.chat.config.persistence.memory.MemoryCorePersistenceServices
import com.demo.chat.config.persistence.memory.KeyGenConfiguration
import com.demo.chat.config.persistence.memory.MemorySecretsStoreServiceBeans
import com.demo.chat.config.pubsub.memory.MemoryPubSubConfiguration
import com.demo.chat.config.secure.CompositeAuthConfiguration
import com.demo.chat.config.secure.TransportConfiguration
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
import org.springframework.boot.autoconfigure.rsocket.RSocketMessagingAutoConfiguration
import org.springframework.boot.autoconfigure.rsocket.RSocketStrategiesAutoConfiguration
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

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
    UserServiceController::class
)
@Configuration
open class AppConfiguration