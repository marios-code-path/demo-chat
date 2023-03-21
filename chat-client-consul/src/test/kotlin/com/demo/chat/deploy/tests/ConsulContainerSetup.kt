package com.demo.chat.deploy.tests

import org.junit.jupiter.api.AfterAll
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.consul.ConsulContainer
import org.testcontainers.containers.Network
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Duration


// some googling:
// https://github.com/spring-cloud/spring-cloud-vault/issues/602#event-4926845049

@Testcontainers
open class ConsulContainerSetup {

    @AfterAll
    fun stopConsul() {
        consulContainer.stop()
        consulContainer.close()
    }

    companion object {
        private val imageName = "consul:1.14.4"

        @Container
        val consulContainer = ConsulContainer(imageName).apply {
            withExposedPorts(8500)
            withReuse(true)
            //withLogConsumer(ContainerUtils.containerLogsConsumer(log))
            withNetwork(Network.SHARED)
            withStartupTimeout(Duration.ofSeconds(60))
            start()
        }

        @JvmStatic
        @DynamicPropertySource
        fun setDynamicProps(propertySource: DynamicPropertyRegistry) {
            propertySource.add("spring.cloud.consul.host") { consulContainer.host }
            propertySource.add("spring.cloud.consul.port") { consulContainer.getMappedPort(8500) }
        }
    }
}