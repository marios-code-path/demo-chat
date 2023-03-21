package com.demo.chat.config.deploy.actuator

import com.demo.chat.domain.Key
import com.demo.chat.domain.knownkey.RootKeys
import org.springframework.boot.actuate.endpoint.annotation.Endpoint
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

@Component
@Endpoint(id = "rootkeys", enableByDefault = true)
class RootKeyEndpoint<T>(private val rootKeys: RootKeys<T>) {

    @ReadOperation
    @Bean
    fun actuateRootKeys(): Map<String, Key<T>> = rootKeys.getMapOfKeyMap()
}