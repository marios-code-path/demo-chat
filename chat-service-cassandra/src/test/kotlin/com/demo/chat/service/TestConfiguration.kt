package com.demo.chat.service

import com.demo.chat.config.ClusterConfigurationCassandra
import com.demo.chat.config.ConfigurationPropertiesCassandra
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.data.cassandra.repository.config.EnableReactiveCassandraRepositories

@Configuration
@ComponentScan("com.demo.chat")
class TestConfiguration : ApplicationContextInitializer<ConfigurableApplicationContext> {

    override fun initialize(applicationContext: ConfigurableApplicationContext) {
        applicationContext.environment.setActiveProfiles("cassandra-persistence")
    }

    @Bean
    fun cassandraProperties(): ConfigurationPropertiesCassandra = CassandraProperties("127.0.0.1",
            9142,
            "chat",
            "com.demo.chat.repository.cassandra")
}

@Configuration
class TestClusterConfiguration(props : ConfigurationPropertiesCassandra) : ClusterConfigurationCassandra(props)

data class CassandraProperties(override val contactPoints: String,
                               override val port: Int,
                               override val keyspace: String,
                               override val basePackages: String) : ConfigurationPropertiesCassandra