package com.demo.chat.deploy.config.properties

import com.demo.chat.client.rsocket.config.RSocketCoreProperties
import com.demo.chat.client.rsocket.config.RSocketProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding


@ConfigurationProperties("app.rsocket.config")
@ConstructorBinding
data class AppConfigurationProperties(
        val core: CoreConfigProperties
)

@ConstructorBinding
data class AppProperty(override var dest: String? = "", override var prefix: String? = "") : RSocketProperty

@ConstructorBinding
data class CoreConfigProperties(
        override val key: RSocketProperty = AppProperty(),
        override val index: RSocketProperty = AppProperty(),
        override val persistence: RSocketProperty = AppProperty(),
        override val pubsub: RSocketProperty = AppProperty(),
) : RSocketCoreProperties
