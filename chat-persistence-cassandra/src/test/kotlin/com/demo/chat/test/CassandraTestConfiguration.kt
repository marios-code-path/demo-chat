package com.demo.chat.test

import org.junit.jupiter.api.TestInstance
import org.springframework.boot.autoconfigure.AutoConfigureOrder
import org.springframework.boot.autoconfigure.cassandra.CassandraProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.data.cassandra.repository.config.EnableReactiveCassandraRepositories

@AutoConfigureOrder
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Configuration
@ComponentScan("com.demo.chat.test")
@EnableReactiveCassandraRepositories(basePackages = ["com.demo.chat.repository.cassandra"])
@EnableConfigurationProperties(CassandraProperties::class)
open class CassandraTestConfiguration(props: CassandraProperties) : CassandraTestContainerConfiguration(props)