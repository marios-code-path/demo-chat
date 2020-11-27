package com.demo.chat.deploy.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding


@ConstructorBinding
data class AppProperty(override var dest: String? = "", override var prefix: String? = "") : RSocketProperty

@ConfigurationProperties("app.rsocket.config")
@ConstructorBinding
data class AppConfigurationProperties(
        val core: CoreConfigProperties,
        val edge: EdgeConfigProperties
)

@ConstructorBinding
data class CoreConfigProperties(
        override val key: AppProperty = AppProperty(),
        override val index: AppProperty = AppProperty(),
        override val persistence: AppProperty = AppProperty(),
        override val pubsub: AppProperty = AppProperty(),
) : RSocketCoreProperties

@ConstructorBinding
data class EdgeConfigProperties(
        override val topic: RSocketProperty = AppProperty(),
        override val message: RSocketProperty = AppProperty(),
        override val user: RSocketProperty = AppProperty(),
) : RSocketEdgeProperties
