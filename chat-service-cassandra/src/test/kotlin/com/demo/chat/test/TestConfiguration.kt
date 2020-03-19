package com.demo.chat.test

import com.demo.chat.config.ClusterConfigurationCassandra
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.cassandra.CassandraProperties
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.data.cassandra.config.CassandraClusterFactoryBean
import org.springframework.data.cassandra.core.mapping.CassandraMappingContext
import org.springframework.data.cassandra.core.mapping.SimpleUserTypeResolver


@Configuration
@ComponentScan("com.demo.chat")
class TestConfiguration : ApplicationContextInitializer<ConfigurableApplicationContext> {

    private val log: Logger = LoggerFactory.getLogger(this::class.simpleName)

    override fun initialize(applicationContext: ConfigurableApplicationContext) {
        log.error("This is a simple initialize method")
    }

    @Bean // TODO find a random port please :)
    fun cassandraProperties() = CassandraProperties().apply {
        port = 9142
        keyspaceName = "chat"
        isJmxEnabled = false
        contactPoints.add("127.0.0.1")
    }

    @Bean
    @Throws(Exception::class)
    fun mappingContext(cluster: CassandraClusterFactoryBean): CassandraMappingContext {
        val mappingContext = CassandraMappingContext()
        mappingContext.setUserTypeResolver(SimpleUserTypeResolver(cluster.getObject(), "chat"))
        return mappingContext
    }
}

@Configuration
class TestClusterConfiguration(props: CassandraProperties) : ClusterConfigurationCassandra(props)