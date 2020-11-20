package com.demo.chat.deploy.config.initializers

import org.springframework.boot.autoconfigure.rsocket.RSocketMessagingAutoConfiguration
import org.springframework.boot.autoconfigure.rsocket.RSocketRequesterAutoConfiguration
import org.springframework.boot.autoconfigure.rsocket.RSocketServerAutoConfiguration
import org.springframework.boot.autoconfigure.rsocket.RSocketStrategiesAutoConfiguration
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.annotation.Import
import org.springframework.context.support.GenericApplicationContext
import java.util.function.Supplier

@Import(RSocketMessagingAutoConfiguration::class,
        RSocketRequesterAutoConfiguration::class,
        RSocketServerAutoConfiguration::class,
        RSocketStrategiesAutoConfiguration::class
)
class RSocketContextInitializer