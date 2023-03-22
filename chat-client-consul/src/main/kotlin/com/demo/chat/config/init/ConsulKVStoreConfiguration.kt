package com.demo.chat.config.init

import com.demo.chat.persistence.consul.ConsulKVStore
import com.ecwid.consul.v1.ConsulClient
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean

class ConsulKVStoreConfiguration {

    @Value("\${app.kv.consul.prefix}")
    lateinit var kvPrefix: String

    @Bean
    fun consulKVStore(client: ConsulClient, mapper: ObjectMapper) = ConsulKVStore(client, kvPrefix)

}