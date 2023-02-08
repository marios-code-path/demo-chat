package com.demo.chat.deploy

import com.demo.chat.config.TypeUtilConfiguration
import com.demo.chat.controller.config.IndexControllersConfiguration
import com.demo.chat.controller.config.KeyControllersConfiguration
import com.demo.chat.controller.config.PersistenceControllersConfiguration
import com.demo.chat.controller.config.composite.MessagingControllerConfiguration
import com.demo.chat.domain.serializers.DefaultChatJacksonModules
import com.demo.chat.index.cassandra.config.IndexServiceConfiguration
import com.demo.chat.persistence.cassandra.config.CoreKeyServices
import com.demo.chat.persistence.cassandra.config.CorePersistenceServices
import com.demo.chat.persistence.cassandra.config.KeyGenConfiguration
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
import org.springframework.boot.autoconfigure.rsocket.RSocketMessagingAutoConfiguration
import org.springframework.boot.autoconfigure.rsocket.RSocketStrategiesAutoConfiguration
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.data.cassandra.repository.config.EnableReactiveCassandraRepositories

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
    IndexServiceConfiguration::class,
    // Controllers
    KeyControllersConfiguration::class,
    IndexControllersConfiguration::class,
    PersistenceControllersConfiguration::class,
    MessagingControllerConfiguration::class
)
@EnableReactiveCassandraRepositories(
    basePackages = [
        "com.demo.chat.persistence.cassandra.repository",
        "com.demo.chat.index.cassandra.repository"
    ]
)
@Configuration
class AppConfiguration