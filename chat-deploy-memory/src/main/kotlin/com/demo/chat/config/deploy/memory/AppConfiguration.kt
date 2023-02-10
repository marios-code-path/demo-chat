package com.demo.chat.config.deploy.memory

import com.demo.chat.config.TypeUtilConfiguration
import com.demo.chat.config.controller.*
import com.demo.chat.config.controller.composite.MessagingControllerConfiguration
import com.demo.chat.domain.serializers.DefaultChatJacksonModules
import com.demo.chat.index.lucene.config.LuceneIndexBeans
import com.demo.chat.config.persistence.memory.CoreKeyServices
import com.demo.chat.config.persistence.memory.CorePersistenceServices
import com.demo.chat.config.persistence.memory.KeyGenConfiguration
import com.demo.chat.config.persistence.memory.SecretsStoreService
import com.demo.chat.config.pubsub.memory.PubSubConfig
import com.demo.chat.config.secure.AuthConfiguration
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
    TypeUtilConfiguration::class,
    // Transport Security
    TransportConfiguration::class,
    // Services
    KeyGenConfiguration::class,
    CoreKeyServices::class,
    CorePersistenceServices::class,
    LuceneIndexBeans::class,
    SecretsStoreService::class,
    PubSubConfig::class,
    AuthConfiguration::class,
    // Controllers
    KeyControllersConfiguration::class,
    IndexControllersConfiguration::class,
    PersistenceControllersConfiguration::class,
    PubSubControllerConfiguration::class,
    MessagingControllerConfiguration::class,
    SecretsStoreControllerConfiguration::class
)
@Configuration
open class AppConfiguration