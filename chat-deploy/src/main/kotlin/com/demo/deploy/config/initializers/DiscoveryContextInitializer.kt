package com.demo.deploy.config.initializers

import org.springframework.cloud.client.serviceregistry.ServiceRegistryAutoConfiguration
import org.springframework.cloud.commons.util.InetUtils
import org.springframework.cloud.commons.util.InetUtilsProperties
import org.springframework.cloud.consul.discovery.ConsulCatalogWatchAutoConfiguration
import org.springframework.cloud.consul.discovery.reactive.ConsulReactiveDiscoveryClientConfiguration
import org.springframework.cloud.consul.serviceregistry.ConsulAutoServiceRegistrationAutoConfiguration
import org.springframework.cloud.consul.serviceregistry.ConsulServiceRegistryAutoConfiguration
import org.springframework.cloud.consul.support.ConsulHeartbeatAutoConfiguration
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.support.GenericApplicationContext
import java.util.function.Supplier

class DiscoveryContextInitializer : ApplicationContextInitializer<GenericApplicationContext> {
    override fun initialize(ctx: GenericApplicationContext) {
        // Discovery / Service Registries
//        ctx.registerBean(ConsulConfigServerAutoConfiguration::class.java, Supplier {
//            ConsulConfigServerAutoConfiguration()
//        })
        ctx.registerBean(ServiceRegistryAutoConfiguration::class.java, Supplier {
            ServiceRegistryAutoConfiguration()
        })
        ctx.registerBean(InetUtilsProperties::class.java, Supplier {
            InetUtilsProperties()
        })
        ctx.registerBean(InetUtils::class.java, Supplier {
            InetUtils(ctx.getBean(InetUtilsProperties::class.java))
        })
        ctx.registerBean(ConsulAutoServiceRegistrationAutoConfiguration::class.java, Supplier {
            ConsulAutoServiceRegistrationAutoConfiguration()
        })
        ctx.registerBean(ConsulServiceRegistryAutoConfiguration::class.java, Supplier {
            ConsulServiceRegistryAutoConfiguration()
        })
        ctx.registerBean(ConsulReactiveDiscoveryClientConfiguration::class.java, Supplier {
            ConsulReactiveDiscoveryClientConfiguration()
        })
        ctx.registerBean(ConsulCatalogWatchAutoConfiguration::class.java, Supplier {
            ConsulCatalogWatchAutoConfiguration()
        })
        ctx.registerBean(ConsulHeartbeatAutoConfiguration::class.java, Supplier {
            ConsulHeartbeatAutoConfiguration()
        })
    }

}