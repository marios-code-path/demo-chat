package com.demo.chat.deploy.config.initializers

import org.springframework.boot.actuate.autoconfigure.endpoint.EndpointAutoConfiguration
import org.springframework.boot.actuate.autoconfigure.endpoint.web.WebEndpointAutoConfiguration
import org.springframework.boot.actuate.autoconfigure.endpoint.web.reactive.WebFluxEndpointManagementContextConfiguration
import org.springframework.boot.actuate.autoconfigure.health.HealthEndpointAutoConfiguration
import org.springframework.boot.actuate.autoconfigure.info.InfoEndpointAutoConfiguration
import org.springframework.boot.actuate.autoconfigure.web.reactive.ReactiveManagementContextAutoConfiguration
import org.springframework.context.annotation.Import


@Import(
        WebEndpointAutoConfiguration::class,
        EndpointAutoConfiguration::class,
        WebFluxEndpointManagementContextConfiguration::class,
        ReactiveManagementContextAutoConfiguration::class,
        HealthEndpointAutoConfiguration::class,
        InfoEndpointAutoConfiguration::class
)
class ManagementContextInitializer