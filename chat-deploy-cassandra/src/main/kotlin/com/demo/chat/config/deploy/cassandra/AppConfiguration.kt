package com.demo.chat.config.deploy.cassandra

import com.demo.chat.config.BaseDomainConfiguration
import com.demo.chat.config.controller.core.IndexControllersConfiguration
import com.demo.chat.config.controller.core.KeyControllersConfiguration
import com.demo.chat.config.controller.core.PersistenceControllersConfiguration
import com.demo.chat.config.deploy.cassandra.dse.AstraConfiguration
import com.demo.chat.config.deploy.cassandra.dse.CassandraConfiguration
import com.demo.chat.config.deploy.cassandra.dse.ContactPointConfiguration
import com.demo.chat.config.index.cassandra.IndexServiceConfiguration
import com.demo.chat.config.persistence.cassandra.CoreKeyServices
import com.demo.chat.config.persistence.cassandra.CorePersistenceServices
import com.demo.chat.config.persistence.cassandra.KeyGenConfiguration
import com.demo.chat.config.secure.CompositeAuthConfiguration
import com.demo.chat.domain.serializers.DefaultChatJacksonModules
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
import org.springframework.boot.autoconfigure.rsocket.RSocketMessagingAutoConfiguration
import org.springframework.boot.autoconfigure.rsocket.RSocketStrategiesAutoConfiguration
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.data.cassandra.repository.config.EnableReactiveCassandraRepositories

@Import(
    AstraConfiguration::class,
    CassandraConfiguration::class,
    ContactPointConfiguration::class,
    // Serialization
    JacksonAutoConfiguration::class,
    DefaultChatJacksonModules::class,
    RSocketStrategiesAutoConfiguration::class,
    RSocketMessagingAutoConfiguration::class,
    // TYPES
    BaseDomainConfiguration::class,
    // Services
    KeyGenConfiguration::class,
    CoreKeyServices::class,
    CorePersistenceServices::class,
    IndexServiceConfiguration::class,
    CompositeAuthConfiguration::class,
    // Controllers
    KeyControllersConfiguration::class,
    IndexControllersConfiguration::class,
    PersistenceControllersConfiguration::class,
    CompositeServiceConfiguration::class
)
@EnableReactiveCassandraRepositories(
    basePackages = [
        "com.demo.chat.persistence.cassandra.repository",
        "com.demo.chat.index.cassandra.repository"
    ]
)
@Configuration
open class AppConfiguration