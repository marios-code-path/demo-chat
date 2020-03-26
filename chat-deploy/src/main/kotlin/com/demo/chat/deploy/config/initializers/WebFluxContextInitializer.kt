package com.demo.chat.deploy.config.initializers

import org.springframework.boot.autoconfigure.web.reactive.HttpHandlerAutoConfiguration
import org.springframework.boot.autoconfigure.web.reactive.ReactiveWebServerFactoryAutoConfiguration
import org.springframework.boot.autoconfigure.web.reactive.WebFluxAutoConfiguration
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.support.GenericApplicationContext
import java.util.function.Supplier

class WebFluxContextInitializer : ApplicationContextInitializer<GenericApplicationContext> {
    override fun initialize(ctx: GenericApplicationContext) {
        // Just for WebFlux to boot
        ctx.registerBean(HttpHandlerAutoConfiguration::class.java, Supplier {
            HttpHandlerAutoConfiguration()
        })
        ctx.registerBean(ReactiveWebServerFactoryAutoConfiguration::class.java, Supplier {
            ReactiveWebServerFactoryAutoConfiguration()
        })
        ctx.registerBean(WebFluxAutoConfiguration::class.java, Supplier {
            WebFluxAutoConfiguration()
        })
    }

}