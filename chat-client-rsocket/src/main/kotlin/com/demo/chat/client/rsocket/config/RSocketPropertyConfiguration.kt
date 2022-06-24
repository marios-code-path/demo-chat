package com.demo.chat.client.rsocket.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding


@ConfigurationProperties("app.rsocket.config")
@ConstructorBinding
data class AppRSocketProperties(
    val core: CoreRSocketAppProperties
)


@ConfigurationProperties("app.rsocket.client.config")
@ConstructorBinding
data class ClientRSocketProperties(val core: Map<String, RSocketPropertyValue>)

@ConstructorBinding
data class RSocketPropertyValue(override var dest: String? = "", override var prefix: String? = "") : RSocketProperty

@ConstructorBinding
@ConfigurationProperties("app.rsocket.client")
data class RSocketConnectionProperties(
    private val config: Map<String, RSocketProperty>
) {
    fun getKey(str: String): RSocketProperty = config[str]!!
}


@ConstructorBinding
data class CoreRSocketAppProperties(
    override val key: RSocketProperty = RSocketPropertyValue(),
    override val index: RSocketProperty = RSocketPropertyValue(),
    override val persistence: RSocketProperty = RSocketPropertyValue(),
    override val pubsub: RSocketProperty = RSocketPropertyValue(),
) : CoreRSocketProperties