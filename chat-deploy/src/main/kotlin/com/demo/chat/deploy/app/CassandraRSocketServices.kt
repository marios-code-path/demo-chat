package com.demo.chat.deploy.app

import com.demo.chat.deploy.config.initializers.*
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.annotation.Profile
import org.springframework.context.support.GenericApplicationContext
import java.util.function.Supplier

@Profile("production")
@SpringBootConfiguration
class CassandraRSocketServices : ApplicationContextInitializer<GenericApplicationContext> {
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
            SpringApplicationBuilder(CassandraRSocketServices::class.java)
                    .initializers(CassandraRSocketServices())
                    .run(*args)
        }
    }
}