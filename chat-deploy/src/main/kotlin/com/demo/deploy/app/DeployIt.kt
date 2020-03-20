package com.demo.deploy.app

import com.datastax.driver.core.Cluster
import com.demo.chat.codec.JsonNodeAnyCodec
import com.demo.chat.domain.serializers.JacksonModules
import org.slf4j.LoggerFactory
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.actuate.autoconfigure.endpoint.EndpointAutoConfiguration
import org.springframework.boot.actuate.autoconfigure.endpoint.web.WebEndpointAutoConfiguration
import org.springframework.boot.actuate.autoconfigure.endpoint.web.WebEndpointProperties
import org.springframework.boot.actuate.autoconfigure.endpoint.web.reactive.WebFluxEndpointManagementContextConfiguration
import org.springframework.boot.actuate.autoconfigure.health.HealthEndpointAutoConfiguration
import org.springframework.boot.actuate.autoconfigure.info.InfoEndpointAutoConfiguration
import org.springframework.boot.actuate.autoconfigure.web.reactive.ReactiveManagementContextAutoConfiguration
import org.springframework.boot.autoconfigure.cassandra.CassandraAutoConfiguration
import org.springframework.boot.autoconfigure.cassandra.CassandraProperties
import org.springframework.boot.autoconfigure.data.cassandra.CassandraDataAutoConfiguration
import org.springframework.boot.autoconfigure.data.cassandra.CassandraReactiveDataAutoConfiguration
import org.springframework.boot.autoconfigure.http.codec.CodecsAutoConfiguration
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
import org.springframework.boot.autoconfigure.rsocket.RSocketMessagingAutoConfiguration
import org.springframework.boot.autoconfigure.rsocket.RSocketRequesterAutoConfiguration
import org.springframework.boot.autoconfigure.rsocket.RSocketServerAutoConfiguration
import org.springframework.boot.autoconfigure.rsocket.RSocketStrategiesAutoConfiguration
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration
import org.springframework.boot.autoconfigure.web.reactive.HttpHandlerAutoConfiguration
import org.springframework.boot.autoconfigure.web.reactive.ReactiveWebServerFactoryAutoConfiguration
import org.springframework.boot.autoconfigure.web.reactive.WebFluxAutoConfiguration
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
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import java.util.function.Supplier

@Profile("production")
@SpringBootConfiguration
class DeployIt : ApplicationContextInitializer<GenericApplicationContext> {
    val logger = LoggerFactory.getLogger(this::class.java.canonicalName)

    override fun initialize(ctx: GenericApplicationContext) {
        ctx.registerBean(JacksonModules::class.java, Supplier {
            JacksonModules(JsonNodeAnyCodec, JsonNodeAnyCodec)
        })
        ctx.registerBean(JacksonAutoConfiguration::class.java, Supplier {
            JacksonAutoConfiguration()
        })

        ctx.registerBean(CodecsAutoConfiguration::class.java, Supplier {
            CodecsAutoConfiguration()
        })
        ctx.registerBean(ValidationAutoConfiguration::class.java, Supplier {
            ValidationAutoConfiguration()
        })


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

        ctx.environment.activeProfiles.forEach { profile ->
            when (profile) {
                "cassandra-cluster" -> {

//                    ctx.registerBean(ClusterConfigurationCassandra::class.java, Supplier {
//                        ClusterConfigurationCassandra(ctx.getBean(AppCassandraProperties::class.java))
//                    })
                    ctx.registerBean(CassandraAutoConfiguration::class.java, Supplier {
                        CassandraAutoConfiguration()
                    })
                    ctx.registerBean(CassandraDataAutoConfiguration::class.java, Supplier {
                        CassandraDataAutoConfiguration(ctx.getBean(CassandraProperties::class.java),
                                ctx.getBean(Cluster::class.java))
                    })
                    ctx.registerBean(CassandraReactiveDataAutoConfiguration::class.java, Supplier {
                        CassandraReactiveDataAutoConfiguration()
                    })
                }
                else -> Unit
            }
        }

        // APP Configuration for RSocket
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

        // Add your controllers!!!!

    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            SpringApplicationBuilder(DeployIt::class.java)
                    .initializers(DeployIt())
                    .run(*args)
        }
    }
}