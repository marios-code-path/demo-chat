package com.demo.chat.deploy.cassandra

import com.demo.chat.deploy.cassandra.config.CassandraServiceConfiguration
import com.demo.chat.deploy.cassandra.config.dse.AstraConfiguration
import com.demo.chat.deploy.cassandra.config.dse.ContactPointConfiguration
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.cassandra.CassandraProperties
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.cassandra.repository.config.EnableReactiveCassandraRepositories

@Configuration
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

    @Configuration
    class ServiceConfiguration() : CassandraServiceConfiguration()
}