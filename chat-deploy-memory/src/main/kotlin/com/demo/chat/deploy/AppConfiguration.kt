package com.demo.chat.deploy

import com.demo.chat.config.TypeUtilConfiguration
import com.demo.chat.controller.config.IndexControllersConfiguration
import com.demo.chat.controller.config.KeyControllersConfiguration
import com.demo.chat.controller.config.PersistenceControllersConfiguration
import com.demo.chat.controller.config.PubSubControllerConfiguration
import com.demo.chat.controller.config.composite.MessagingControllerConfiguration
import com.demo.chat.domain.serializers.DefaultChatJacksonModules
import com.demo.chat.index.lucene.config.LuceneIndexBeans
import com.demo.chat.persistence.memory.config.CoreKeyServices
import com.demo.chat.persistence.memory.config.CorePersistenceServices
import com.demo.chat.persistence.memory.config.KeyGenConfiguration
import com.demo.chat.persistence.memory.config.SecretsStoreService
import com.demo.chat.pubsub.memory.config.PubSubConfig
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
    // Services
    KeyGenConfiguration::class,
    CoreKeyServices::class,
    CorePersistenceServices::class,
    LuceneIndexBeans::class,
    SecretsStoreService::class,
    PubSubConfig::class,
    // Controllers
    KeyControllersConfiguration::class,
    IndexControllersConfiguration::class,
    PersistenceControllersConfiguration::class,
    PubSubControllerConfiguration::class,
    MessagingControllerConfiguration::class
)
@Configuration
class AppConfiguration