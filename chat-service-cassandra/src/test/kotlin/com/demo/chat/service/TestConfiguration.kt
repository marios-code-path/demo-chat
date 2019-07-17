package com.demo.chat.service

import com.demo.chat.config.CassandraProperties
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration

@Configuration
@ComponentScan("com.demo.chat")
class TestConfiguration : ApplicationContextInitializer<ConfigurableApplicationContext> {

    override fun initialize(applicationContext: ConfigurableApplicationContext) {
        applicationContext.environment.setActiveProfiles("cassandra-persistence")
    }

    @Bean
    fun cassandraProperties() = CassandraProperties()
}