package com.demo.chat.test

import org.springframework.boot.autoconfigure.cassandra.CassandraProperties
import org.springframework.context.annotation.Configuration
import org.springframework.data.cassandra.repository.config.EnableReactiveCassandraRepositories

@Configuration
@EnableReactiveCassandraRepositories(basePackages = ["com.demo.chat.index.cassandra.repository"])
class IndexRepositoryTestConfiguration(props: CassandraProperties) : CassandraTestContainerConfiguration(props)