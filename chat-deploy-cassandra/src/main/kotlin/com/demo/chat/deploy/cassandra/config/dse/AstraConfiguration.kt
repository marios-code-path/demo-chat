package com.demo.chat.deploy.cassandra.config.dse

import com.datastax.oss.driver.api.core.config.DefaultDriverOption
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.cassandra.CassandraProperties
import org.springframework.boot.autoconfigure.cassandra.DriverConfigLoaderBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.cassandra.config.AbstractCassandraConfiguration
import org.springframework.data.cassandra.config.SessionBuilderConfigurer
import java.nio.file.Paths

@Configuration
@Profile("cassandra-astra")
class AstraConfiguration(
    val props: CassandraProperties,
    @Value("\${astra.secure-connect-bundle}")
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