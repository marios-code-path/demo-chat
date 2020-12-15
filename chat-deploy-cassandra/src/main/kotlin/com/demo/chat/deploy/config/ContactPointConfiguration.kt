package com.demo.chat.deploy.config

import org.springframework.boot.autoconfigure.cassandra.CassandraProperties
import org.springframework.data.cassandra.config.AbstractCassandraConfiguration
import org.springframework.data.cassandra.config.SessionBuilderConfigurer


class ContactPointConfiguration(
        val props: CassandraProperties,
) : AbstractCassandraConfiguration() {
    override fun getSessionBuilderConfigurer(): SessionBuilderConfigurer =
            SessionBuilderConfigurer { sessionBuilder ->
                sessionBuilder // Spring Boot 2.3.0 https://github.com/spring-projects/spring-boot/issues/21487
                        .withAuthCredentials(props.username, props.password)
                        .withLocalDatacenter(props.localDatacenter)
            }

    override fun getKeyspaceName(): String {
        return props.keyspaceName
    }
}