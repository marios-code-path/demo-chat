package com.demo.chat.deploy.config.properties

import com.demo.chat.client.rsocket.config.CoreRSocketProperties
import com.demo.chat.client.rsocket.config.RSocketProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding


@ConfigurationProperties("app.rsocket.config")
@ConstructorBinding
data class AppRSocketBindings(
        val core: CoreRSocketAppBindings
)

@ConstructorBinding
data class AppRSocketProperty(override var dest: String? = "", override var prefix: String? = "") : RSocketProperty

@ConstructorBinding
data class CoreRSocketAppBindings(
        override val key: RSocketProperty = AppRSocketProperty(),
        override val index: RSocketProperty = AppRSocketProperty(),
        override val persistence: RSocketProperty = AppRSocketProperty(),
        override val pubsub: RSocketProperty = AppRSocketProperty(),
) : CoreRSocketProperties