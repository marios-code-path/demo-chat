package com.demo.chat.client.rsocket.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.ConstructorBinding


@ConfigurationProperties("app.rsocket.config")
data class RSocketAppProperties
@ConstructorBinding constructor(
    val core: CoreRSocketAppProperties
)

@ConfigurationProperties("app.rsocket.client")
data class RSocketClientProperties
@ConstructorBinding constructor(val config: Map<String, RSocketPropertyValue>) {
    fun getServiceConfig(str: String): RSocketProperty = config[str]!!
}

data class RSocketPropertyValue
@ConstructorBinding constructor(
    override var dest: String? = "", override var prefix: String? = ""
) : RSocketProperty

data class CoreRSocketAppProperties
@ConstructorBinding constructor(
    override val key: RSocketProperty = RSocketPropertyValue(),
    override val index: RSocketProperty = RSocketPropertyValue(),
    override val persistence: RSocketProperty = RSocketPropertyValue(),
    override val pubsub: RSocketProperty = RSocketPropertyValue(),
    override val topic: RSocketProperty = RSocketPropertyValue(),
    override val message: RSocketProperty = RSocketPropertyValue(),
    override val user: RSocketProperty = RSocketPropertyValue()
) : CoreRSocketProperties, EdgeRSocketProperties