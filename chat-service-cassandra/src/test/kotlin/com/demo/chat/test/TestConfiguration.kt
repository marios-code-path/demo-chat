package com.demo.chat.test

import com.demo.chat.config.ClusterConfigurationCassandra
import com.demo.chat.config.ConfigurationPropertiesCassandra
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.cassandra.CassandraProperties
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.data.cassandra.config.AbstractReactiveCassandraConfiguration
import org.springframework.data.cassandra.config.CassandraClusterFactoryBean
import org.springframework.data.cassandra.core.mapping.CassandraMappingContext
import org.springframework.data.cassandra.core.mapping.SimpleUserTypeResolver
import org.springframework.data.cassandra.repository.config.EnableReactiveCassandraRepositories

data class CassandraConfigProperties(override val contactPoints: String,
                                     override val port: Int,
                                     override val keyspace: String,
                                     override val basePackages: String,
                                     override val jmxReporting: Boolean) : ConfigurationPropertiesCassandra

@Configuration
@ComponentScan("com.demo.chat")
class TestConfiguration : ApplicationContextInitializer<ConfigurableApplicationContext> {

    private val log: Logger = LoggerFactory.getLogger(this::class.simpleName)

    override fun initialize(applicationContext: ConfigurableApplicationContext) {
        log.error("This is a simple initialize method")
        applicationContext.environment.setActiveProfiles("cassandra-persistence")
    }

//    @Bean
//    fun clusterCassandra(props: CassandraConfigProperties): AbstractReactiveCassandraConfiguration =
//            ClusterConfigurationCassandra(props)

    @Bean // TODO find a random port please :)
    fun cassandraProperties() = CassandraConfigProperties(
            "127.0.0.1",
            9142,
            "chat",
            "com.demo.chat.repository.cassandra",
            false)


    @Bean
    @Throws(Exception::class)
    fun mappingContext(cluster: CassandraClusterFactoryBean): CassandraMappingContext {
        val mappingContext = CassandraMappingContext()
        mappingContext.setUserTypeResolver(SimpleUserTypeResolver(cluster.getObject(), "chat"))
        return mappingContext
    }
}

@Configuration
class TestClusterConfiguration(props: CassandraConfigProperties) : ClusterConfigurationCassandra(props)