package com.demo.chat.config.client.rsocket

import com.demo.chat.client.rsocket.RSocketProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@EnableConfigurationProperties(RSocketClientProperties::class)
@Configuration
class RSocketPropertyConfiguration


@ConfigurationProperties("app.rsocket.client")
data class RSocketClientProperties
constructor(val config: Map<String, RSocketPropertyValue>) {
    fun getServiceConfig(str: String): RSocketProperty = config[str]!!
}

data class RSocketPropertyValue
constructor(
    override var dest: String? = "", override var prefix: String? = ""
) : RSocketProperty