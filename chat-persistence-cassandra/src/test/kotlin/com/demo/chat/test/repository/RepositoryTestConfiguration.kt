package com.demo.chat.test.repository

import com.demo.chat.test.CassandraTestContainerConfiguration
import org.springframework.boot.autoconfigure.cassandra.CassandraProperties
import org.springframework.context.annotation.Configuration
import org.springframework.data.cassandra.repository.config.EnableReactiveCassandraRepositories

@Configuration
@EnableReactiveCassandraRepositories(basePackages = ["com.demo.chat.repository.cassandra"])
class RepositoryTestConfiguration(props: CassandraProperties) : CassandraTestContainerConfiguration(props)