package com.demo.chat.deploy.cassandra

import com.datastax.oss.driver.api.core.uuid.Uuids
import com.demo.chat.config.KeyServiceBeans
import com.demo.chat.controller.config.IndexControllersConfiguration
import com.demo.chat.controller.config.KeyControllersConfiguration
import com.demo.chat.controller.config.PersistenceControllersConfiguration
import com.demo.chat.deploy.cassandra.config.CassandraIndexServiceConfiguration
import com.demo.chat.deploy.cassandra.config.CassandraPersistenceServiceConfiguration
import com.demo.chat.deploy.cassandra.config.dse.AstraConfiguration
import com.demo.chat.deploy.cassandra.config.dse.ContactPointConfiguration
import com.demo.chat.domain.serializers.DefaultChatJacksonModules
import com.demo.chat.repository.cassandra.*
import com.demo.chat.service.IKeyService
import com.demo.chat.service.persistence.KeyServiceCassandra
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.cassandra.CassandraProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Profile
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import org.springframework.data.cassandra.repository.config.EnableReactiveCassandraRepositories
import java.util.*

@EnableReactiveCassandraRepositories(basePackages = ["com.demo.chat.repository.cassandra"])
class BaseCassandraApp {

    @Configuration
    @Profile("cassandra-astra")
    class AstraClusterConfiguration(
        props: CassandraProperties,
        @Value("\${astra.secure-connect-bundle}")
        connectPath: String,
    ) : AstraConfiguration(props, connectPath)

    @Configuration
    @Profile("cassandra-default", "default")
    class DefaultClusterConfiguration(
        props: CassandraProperties
    ) : ContactPointConfiguration(props)
}