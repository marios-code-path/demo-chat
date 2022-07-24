package com.demo.chat.deploy.test

import com.demo.chat.test.CassandraTestContainerConfiguration
import org.springframework.boot.autoconfigure.cassandra.CassandraProperties
import org.springframework.context.annotation.Configuration

@Configuration
class AppTestConfiguration(props: CassandraProperties) : CassandraTestContainerConfiguration(props)