package com.demo.deploy.config.initializers

import org.springframework.boot.autoconfigure.rsocket.RSocketMessagingAutoConfiguration
import org.springframework.boot.autoconfigure.rsocket.RSocketRequesterAutoConfiguration
import org.springframework.boot.autoconfigure.rsocket.RSocketServerAutoConfiguration
import org.springframework.boot.autoconfigure.rsocket.RSocketStrategiesAutoConfiguration
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.support.GenericApplicationContext
import java.util.function.Supplier

class RSocketContextInitializer : ApplicationContextInitializer<GenericApplicationContext> {
    override fun initialize(ctx: GenericApplicationContext) {
        ctx.registerBean(RSocketMessagingAutoConfiguration::class.java, Supplier {
            RSocketMessagingAutoConfiguration()
        })
        ctx.registerBean(RSocketRequesterAutoConfiguration::class.java, Supplier {
            RSocketRequesterAutoConfiguration()
        })
        ctx.registerBean(RSocketServerAutoConfiguration::class.java, Supplier {
            RSocketServerAutoConfiguration()
        })
        ctx.registerBean(RSocketStrategiesAutoConfiguration::class.java, Supplier {
            RSocketStrategiesAutoConfiguration()
        })
    }

}