package com.demo.deploy.config.initializers

import org.springframework.boot.actuate.autoconfigure.endpoint.EndpointAutoConfiguration
import org.springframework.boot.actuate.autoconfigure.endpoint.web.WebEndpointAutoConfiguration
import org.springframework.boot.actuate.autoconfigure.endpoint.web.WebEndpointProperties
import org.springframework.boot.actuate.autoconfigure.endpoint.web.reactive.WebFluxEndpointManagementContextConfiguration
import org.springframework.boot.actuate.autoconfigure.health.HealthEndpointAutoConfiguration
import org.springframework.boot.actuate.autoconfigure.info.InfoEndpointAutoConfiguration
import org.springframework.boot.actuate.autoconfigure.web.reactive.ReactiveManagementContextAutoConfiguration
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.support.GenericApplicationContext
import java.util.function.Supplier


class ManagementContextInitializer : ApplicationContextInitializer<GenericApplicationContext> {
    override fun initialize(ctx: GenericApplicationContext) {

        // To connect with Actuator
        ctx.registerBean(WebEndpointAutoConfiguration::class.java, Supplier {
            WebEndpointAutoConfiguration(ctx, ctx.getBean(WebEndpointProperties::class.java))
        })
        ctx.registerBean(EndpointAutoConfiguration::class.java, Supplier {
            EndpointAutoConfiguration()
        })
        ctx.registerBean(ReactiveManagementContextAutoConfiguration::class.java, Supplier {
            ReactiveManagementContextAutoConfiguration()
        })
        ctx.registerBean(HealthEndpointAutoConfiguration::class.java, Supplier {
            HealthEndpointAutoConfiguration()
        })
        ctx.registerBean(InfoEndpointAutoConfiguration::class.java, Supplier {
            InfoEndpointAutoConfiguration()
        })
        ctx.registerBean(WebFluxEndpointManagementContextConfiguration::class.java, Supplier {
            WebFluxEndpointManagementContextConfiguration()
        })
    }

}