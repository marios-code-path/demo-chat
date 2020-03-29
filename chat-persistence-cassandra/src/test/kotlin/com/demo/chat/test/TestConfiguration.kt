package com.demo.chat.test

import com.demo.chat.config.ClusterConfigurationCassandra
import com.demo.chat.config.ConfigurationPropertiesCassandra
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.cassandra.CassandraProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.data.cassandra.config.CassandraClusterFactoryBean
import org.springframework.data.cassandra.core.mapping.CassandraMappingContext
import org.springframework.data.cassandra.core.mapping.SimpleUserTypeResolver
import org.springframework.data.cassandra.repository.config.EnableReactiveCassandraRepositories


@Configuration
@ComponentScan("com.demo.chat.test")
@EnableReactiveCassandraRepositories(basePackages = ["com.demo.chat.repository.cassandra"])
@EnableConfigurationProperties(CassandraProperties::class, CassandraConfigProperties::class)
class TestConfiguration : ApplicationContextInitializer<ConfigurableApplicationContext> {

    private val log: Logger = LoggerFactory.getLogger(this::class.simpleName)

    override fun initialize(applicationContext: ConfigurableApplicationContext) {

    }

    @Bean
    @Throws(Exception::class)
    fun mappingContext(cluster: CassandraClusterFactoryBean): CassandraMappingContext {
        val mappingContext = CassandraMappingContext()
        mappingContext.setUserTypeResolver(SimpleUserTypeResolver(cluster.getObject(), "chat"))
        return mappingContext
    }
}

@ConstructorBinding
@ConfigurationProperties(prefix = "spring.data.cassandra")
data class CassandraConfigProperties(override val basePackages: String) : ConfigurationPropertiesCassandra

@Configuration
class TestClusterConfiguration(props: CassandraProperties, config: CassandraConfigProperties) : ClusterConfigurationCassandra(props, config)