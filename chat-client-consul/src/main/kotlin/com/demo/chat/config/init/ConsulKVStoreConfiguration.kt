package com.demo.chat.config.init

import com.demo.chat.persistence.consul.ConsulKVStore
import com.ecwid.consul.v1.ConsulClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ConsulKVStoreConfiguration {

    @Value("\${app.kv.prefix}")
    lateinit var kvPrefix: String

    @Bean
    fun consulKVStore(client: ConsulClient) = ConsulKVStore(client, kvPrefix)

}