package com.demo.chat.client.rsocket.config

import com.demo.chat.client.rsocket.CoreRSocketClientProperties
import com.demo.chat.client.rsocket.CompositeRSocketClientProperties
import com.demo.chat.client.rsocket.RSocketProperty
import org.springframework.boot.context.properties.ConfigurationProperties


@ConfigurationProperties("app.rsocket.config")
data class RSocketAppProperties
constructor(
    val core: CoreRSocketAppClientProperties
    // TODO refactor composite as separate property
)

@ConfigurationProperties("app.rsocket.client")
data class RSocketClientProperties
constructor(val config: Map<String, RSocketPropertyValue>) {
    fun getServiceConfig(str: String): RSocketProperty = config[str]!!
}

data class RSocketPropertyValue
constructor(
    override var dest: String? = "", override var prefix: String? = ""
) : RSocketProperty

data class CoreRSocketAppClientProperties
constructor(
    override val key: RSocketProperty = RSocketPropertyValue(),
    override val index: RSocketProperty = RSocketPropertyValue(),
    override val persistence: RSocketProperty = RSocketPropertyValue(),
    override val pubsub: RSocketProperty = RSocketPropertyValue(),
    override val secrets: RSocketProperty = RSocketPropertyValue(),
    override val topic: RSocketProperty = RSocketPropertyValue(),
    override val message: RSocketProperty = RSocketPropertyValue(),
    override val user: RSocketProperty = RSocketPropertyValue()
) : CoreRSocketClientProperties, CompositeRSocketClientProperties