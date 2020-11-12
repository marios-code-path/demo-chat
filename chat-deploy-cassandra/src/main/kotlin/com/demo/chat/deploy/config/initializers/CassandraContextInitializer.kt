package com.demo.chat.deploy.config.initializers

import com.datastax.oss.driver.api.core.CqlSession
import org.springframework.boot.autoconfigure.cassandra.CassandraAutoConfiguration
import org.springframework.boot.autoconfigure.data.cassandra.CassandraDataAutoConfiguration
import org.springframework.boot.autoconfigure.data.cassandra.CassandraReactiveDataAutoConfiguration
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.support.GenericApplicationContext
import java.util.function.Supplier

class CassandraContextInitializer: ApplicationContextInitializer<GenericApplicationContext> {
    override fun initialize(ctx: GenericApplicationContext) {
        ctx.registerBean(CassandraAutoConfiguration::class.java, Supplier {
            CassandraAutoConfiguration()
        })
        ctx.registerBean(CassandraDataAutoConfiguration::class.java, Supplier {
            CassandraDataAutoConfiguration(ctx.getBean(CqlSession::class.java))
          //  CassandraDataAutoConfiguration(ctx.getBean(CassandraProperties::class.java),
          //          ctx.getBean(Cluster::class.java))
        })
        ctx.registerBean(CassandraReactiveDataAutoConfiguration::class.java, Supplier {
            CassandraReactiveDataAutoConfiguration()
        })    }

}