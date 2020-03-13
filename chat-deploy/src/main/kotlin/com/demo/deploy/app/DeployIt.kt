package com.demo.deploy.app

import com.datastax.driver.core.Cluster
import com.demo.chat.codec.JsonNodeAnyCodec
import com.demo.chat.config.ClusterConfigurationCassandra
import com.demo.chat.config.ConfigurationPropertiesCassandra
import com.demo.chat.controller.service.KeyServiceController
import com.demo.chat.domain.serializers.JacksonModules
import com.demo.deploy.config.KeyServiceConfigurationCassandra
import com.demo.deploy.config.SerializationConfigurationJackson
import com.demo.deploy.config.UUIDKeyGeneratorCassandra
import org.slf4j.LoggerFactory
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.actuate.autoconfigure.endpoint.EndpointAutoConfiguration
import org.springframework.boot.actuate.autoconfigure.web.mappings.MappingsEndpointAutoConfiguration
import org.springframework.boot.actuate.autoconfigure.web.reactive.ReactiveManagementChildContextConfiguration
import org.springframework.boot.actuate.autoconfigure.web.reactive.ReactiveManagementContextAutoConfiguration
import org.springframework.boot.actuate.autoconfigure.web.server.ManagementContextAutoConfiguration
import org.springframework.boot.autoconfigure.cassandra.CassandraAutoConfiguration
import org.springframework.boot.autoconfigure.cassandra.CassandraProperties
import org.springframework.boot.autoconfigure.data.cassandra.CassandraDataAutoConfiguration
import org.springframework.boot.autoconfigure.data.cassandra.CassandraReactiveDataAutoConfiguration
import org.springframework.boot.autoconfigure.http.codec.CodecsAutoConfiguration
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration
import org.springframework.boot.autoconfigure.web.reactive.ReactiveWebServerFactoryAutoConfiguration
import org.springframework.boot.autoconfigure.web.reactive.WebFluxAutoConfiguration
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.support.GenericApplicationContext
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import org.springframework.data.cassandra.repository.config.CassandraRepositoriesRegistrar
import org.springframework.stereotype.Controller
import java.util.*
import java.util.function.Supplier

@SpringBootConfiguration
class DeployIt : ApplicationContextInitializer<GenericApplicationContext> {
    val logger = LoggerFactory.getLogger(this::class.java.canonicalName)
    override fun initialize(ctx: GenericApplicationContext) {
        ctx.registerBean(JacksonModules::class.java, Supplier {
            JacksonModules(JsonNodeAnyCodec, JsonNodeAnyCodec)
        })
        ctx.registerBean(SerializationConfigurationJackson::class.java, Supplier {
            SerializationConfigurationJackson()
        })

        ctx.registerBean(DiscoveryConfiguration::class.java, Supplier {
            DiscoveryConfiguration()
        })

        // Just for WebFlux to boot
        ctx.registerBean(ReactiveWebServerFactoryAutoConfiguration::class.java, Supplier {
            ReactiveWebServerFactoryAutoConfiguration()
        })
        ctx.registerBean(WebFluxAutoConfiguration::class.java, Supplier {
            WebFluxAutoConfiguration()
        })
        ctx.registerBean(CodecsAutoConfiguration::class.java, Supplier {
            CodecsAutoConfiguration()
        })
        ctx.registerBean(ValidationAutoConfiguration::class.java, Supplier {
            ValidationAutoConfiguration()
        })

        // To connect with Actuator
        ctx.registerBean(EndpointAutoConfiguration::class.java, Supplier {
            EndpointAutoConfiguration()
        })
        ctx.registerBean(ReactiveManagementContextAutoConfiguration::class.java, Supplier {
            ReactiveManagementContextAutoConfiguration()
        })
        ctx.registerBean(ReactiveManagementChildContextConfiguration::class.java, Supplier {
            ReactiveManagementChildContextConfiguration()
        })

        ctx.registerBean(ManagementContextAutoConfiguration::class.java, Supplier {
            ManagementContextAutoConfiguration()
        })

        ctx.registerBean(MappingsEndpointAutoConfiguration::class.java, Supplier {
            MappingsEndpointAutoConfiguration()
        })

        ctx.environment.activeProfiles.forEach { profile ->
            when (profile) {
                "cassandra-cluster" -> {
                    ctx.registerBean(ClusterConfigurationCassandra::class.java, Supplier {
                        ClusterConfigurationCassandra(ctx.getBean(ConfigurationPropertiesCassandra::class.java))
                    })
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
                    ctx.registerBean(ClusterConfigurationCassandra.RepositoryConfigurationCassandra::class.java, Supplier {
                        ClusterConfigurationCassandra.RepositoryConfigurationCassandra()
                    })
                }
                else -> Unit
            }
        }

//        ctx.registerBean(AppKeyServiceConfiguration::class.java, Supplier {
//            AppKeyServiceConfiguration(ctx.getBean(ReactiveCassandraTemplate::class.java))
//        })
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

@Controller
class AppKeyServiceConfiguration(template: ReactiveCassandraTemplate) :
        KeyServiceController<UUID>(KeyServiceConfigurationCassandra(template, UUIDKeyGeneratorCassandra()).keyService())
