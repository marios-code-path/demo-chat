package com.demo.chat.deploy.config.properties

import com.demo.chat.client.rsocket.config.CoreRSocketProperties
import com.demo.chat.client.rsocket.config.RSocketProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding


@ConfigurationProperties("app.rsocket.config")
@ConstructorBinding
data class AppRSocketProperties(
        val core: CoreRSocketAppProperties
)

@ConstructorBinding
data class CoreRSocketProperty(override var dest: String? = "", override var prefix: String? = "") : RSocketProperty

@ConstructorBinding
data class CoreRSocketAppProperties(
        override val key: RSocketProperty = CoreRSocketProperty(),
        override val index: RSocketProperty = CoreRSocketProperty(),
        override val persistence: RSocketProperty = CoreRSocketProperty(),
        override val pubsub: RSocketProperty = CoreRSocketProperty(),
) : CoreRSocketProperties