package com.demo.deploy.app

import com.demo.deploy.config.initializers.*
import org.slf4j.LoggerFactory
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.actuate.autoconfigure.endpoint.EndpointAutoConfiguration
import org.springframework.boot.actuate.autoconfigure.endpoint.web.WebEndpointAutoConfiguration
import org.springframework.boot.actuate.autoconfigure.endpoint.web.WebEndpointProperties
import org.springframework.boot.actuate.autoconfigure.endpoint.web.reactive.WebFluxEndpointManagementContextConfiguration
import org.springframework.boot.actuate.autoconfigure.health.HealthEndpointAutoConfiguration
import org.springframework.boot.actuate.autoconfigure.info.InfoEndpointAutoConfiguration
import org.springframework.boot.actuate.autoconfigure.web.reactive.ReactiveManagementContextAutoConfiguration
import org.springframework.boot.autoconfigure.rsocket.RSocketMessagingAutoConfiguration
import org.springframework.boot.autoconfigure.rsocket.RSocketRequesterAutoConfiguration
import org.springframework.boot.autoconfigure.rsocket.RSocketServerAutoConfiguration
import org.springframework.boot.autoconfigure.rsocket.RSocketStrategiesAutoConfiguration
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.cloud.client.serviceregistry.ServiceRegistryAutoConfiguration
import org.springframework.cloud.commons.util.InetUtils
import org.springframework.cloud.commons.util.InetUtilsProperties
import org.springframework.cloud.consul.discovery.ConsulCatalogWatchAutoConfiguration
import org.springframework.cloud.consul.discovery.reactive.ConsulReactiveDiscoveryClientConfiguration
import org.springframework.cloud.consul.serviceregistry.ConsulAutoServiceRegistrationAutoConfiguration
import org.springframework.cloud.consul.serviceregistry.ConsulServiceRegistryAutoConfiguration
import org.springframework.cloud.consul.support.ConsulHeartbeatAutoConfiguration
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.annotation.Profile
import org.springframework.context.support.GenericApplicationContext
import java.util.function.Supplier

@Profile("production")
@SpringBootConfiguration
class DeployItForLearning : ApplicationContextInitializer<GenericApplicationContext> {
    override fun initialize(ctx: GenericApplicationContext) {
        ctx.registerBean(CodecsContextInitializer::class.java, Supplier {
            CodecsContextInitializer()
        })
        ctx.registerBean(ManagementContextInitializer::class.java, Supplier {
            ManagementContextInitializer()
        })
        ctx.registerBean(DiscoveryContextInitializer::class.java, Supplier {
            DiscoveryContextInitializer()
        })

        ctx.environment.activeProfiles.forEach { profile ->
            when (profile) {
                "cassandra-cluster" -> {
                 ctx.registerBean(CassandraContextInitializer::class.java, Supplier {
                     CassandraContextInitializer()
                 })
                }
                else -> Unit
            }
        }

        ctx.registerBean(RSocketContextInitializer::class.java, Supplier {
            RSocketContextInitializer()
        })
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            SpringApplicationBuilder(DeployItForLearning::class.java)
                    .initializers(DeployItForLearning())
                    .run(*args)
        }
    }
}