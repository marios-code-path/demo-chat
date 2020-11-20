package com.demo.chat.deploy.config.initializers

import org.springframework.boot.autoconfigure.web.reactive.HttpHandlerAutoConfiguration
import org.springframework.boot.autoconfigure.web.reactive.ReactiveWebServerFactoryAutoConfiguration
import org.springframework.boot.autoconfigure.web.reactive.WebFluxAutoConfiguration
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.annotation.Import
import org.springframework.context.support.GenericApplicationContext
import java.util.function.Supplier

@Import(
        HttpHandlerAutoConfiguration::class,
        ReactiveWebServerFactoryAutoConfiguration::class,
        WebFluxAutoConfiguration::class
)
class WebFluxContextInitializer