package com.demo.chat.test.deploy.redis

import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.ConfigurableApplicationContext

/** The launch surface of `RedisGrantRestartTests`, for the Redis safe-repeat classes. */
object RedisSafeRepeatLaunch {
    fun start(name: String, nodeId: Int): ConfigurableApplicationContext {
        val container = RedisDeployBootTests.redis
        val args = listOf(
            "spring.application.name=$name",
            "spring.config.additional-location=classpath:/config/userinit.yml",
            "server.port=0",
            "spring.rsocket.server.port=0",
            "app.server.proto=rsocket",
            "app.key.type=long",
            "app.nodeid=$nodeId",
            "app.users.create=true",
            "app.service.core.key=redis",
            "app.service.core.persistence=redis",
            "app.service.core.pubsub=redis-pubsub",
            "app.service.core.index=lucene",
            "app.service.core.secrets=memory",
            "app.service.composite",
            "app.service.composite.auth=true",
            "app.command.bus=memory",
            "app.controller.persistence",
            "app.controller.index",
            "app.controller.key",
            "app.controller.pubsub",
            "app.controller.secrets",
            "app.controller.user",
            "app.controller.topic",
            "app.controller.message",
            "app.service.security.userdetails",
            "spring.cloud.consul.enabled=false",
            "spring.cloud.consul.discovery.enabled=false",
            "spring.cloud.consul.config.enabled=false",
            "redis-topics.host=${container.containerIpAddress}",
            "redis-topics.port=${container.getMappedPort(6379)}",
        ).map { "--$it" }
        return SpringApplicationBuilder(RedisDeployBootTests.BootApp::class.java)
            .web(WebApplicationType.NONE)
            .run(*args.toTypedArray())
    }
}
