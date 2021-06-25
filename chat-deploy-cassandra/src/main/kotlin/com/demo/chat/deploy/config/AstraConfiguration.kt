package com.demo.chat.deploy.config

import com.datastax.oss.driver.api.core.config.DefaultDriverOption
import org.springframework.boot.autoconfigure.cassandra.CassandraProperties
import org.springframework.boot.autoconfigure.cassandra.DriverConfigLoaderBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.data.cassandra.config.AbstractCassandraConfiguration
import org.springframework.data.cassandra.config.SessionBuilderConfigurer
import java.nio.file.Paths

class AstraConfiguration(
    val props: CassandraProperties,
    val connectPath: String,
)  : AbstractCassandraConfiguration() {

    override fun getSessionBuilderConfigurer(): SessionBuilderConfigurer =
        SessionBuilderConfigurer { sessionBuilder ->
            sessionBuilder
                .withCloudSecureConnectBundle(Paths.get(connectPath))
                .withAuthCredentials(props.username, props.password)
        }

    @Bean
    fun driverConfigLoaderCustomizer() = DriverConfigLoaderBuilderCustomizer {
        it.without(DefaultDriverOption.CONTACT_POINTS)
    }

    override fun getKeyspaceName(): String {
        return props.keyspaceName
    }
}
