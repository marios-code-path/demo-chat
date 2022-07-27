package com.demo.chat.client.rsocket.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding


@ConfigurationProperties("app.rsocket.config")
@ConstructorBinding
data class RSocketAppProperties(
    val core: CoreRSocketAppProperties
)

@ConfigurationProperties("app.rsocket.client")
@ConstructorBinding
data class RSocketClientProperties(val config: Map<String, RSocketPropertyValue>) {
    fun getServiceConfig(str: String): RSocketProperty = config[str]!!
}

@ConstructorBinding
data class RSocketPropertyValue(override var dest: String? = "", override var prefix: String? = "") : RSocketProperty

@ConstructorBinding
data class CoreRSocketAppProperties(
    override val key: RSocketProperty = RSocketPropertyValue(),
    override val index: RSocketProperty = RSocketPropertyValue(),
    override val persistence: RSocketProperty = RSocketPropertyValue(),
    override val pubsub: RSocketProperty = RSocketPropertyValue(),
    override val topic: RSocketProperty = RSocketPropertyValue(),
    override val message: RSocketProperty = RSocketPropertyValue(),
    override val user: RSocketProperty = RSocketPropertyValue()
) : CoreRSocketProperties, EdgeRSocketProperties