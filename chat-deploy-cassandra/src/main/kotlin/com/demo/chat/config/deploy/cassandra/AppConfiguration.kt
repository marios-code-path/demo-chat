package com.demo.chat.config.deploy.cassandra

import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.data.cassandra.repository.config.EnableReactiveCassandraRepositories

@Import(
    //AstraConfiguration::class,
    //CassandraConfiguration::class,
    //ContactPointConfiguration::class,
    // Serialization
//    JacksonAutoConfiguration::class,
//    DefaultChatJacksonModules::class,
//    RSocketStrategiesAutoConfiguration::class,
//    RSocketMessagingAutoConfiguration::class,
//    // TYPES
//    BaseDomainConfiguration::class,
//    // Services
//    KeyGenConfiguration::class,
//    CoreKeyServices::class,
//    CorePersistenceServices::class,
//    IndexServiceConfiguration::class,
//    CompositeAuthConfiguration::class,
//    // Controllers
//    KeyControllersConfiguration::class,
//    IndexControllersConfiguration::class,
//    PersistenceControllersConfiguration::class,
//    CompositeServiceConfiguration::class
)
@EnableReactiveCassandraRepositories(
    basePackages = [
        "com.demo.chat.persistence.cassandra.repository",
        "com.demo.chat.index.cassandra.repository"
    ]
)
@Configuration
open class AppConfiguration