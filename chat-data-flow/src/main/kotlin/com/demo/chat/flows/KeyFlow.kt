package com.demo.chat.flows

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.cloud.dataflow.core.ApplicationType
import org.springframework.cloud.dataflow.rest.client.DataFlowOperations
import org.springframework.cloud.dataflow.rest.client.dsl.DeploymentPropertiesBuilder
import org.springframework.cloud.dataflow.rest.client.dsl.Stream
import org.springframework.cloud.dataflow.rest.client.dsl.StreamApplication
import org.springframework.cloud.dataflow.rest.client.dsl.StreamBuilder
import org.springframework.context.annotation.Bean

@SpringBootApplication
class KeyFlow {
    val logger = LoggerFactory.getLogger(this::class.java.simpleName)

    @Autowired
    private lateinit var dataFlowOperations: DataFlowOperations

    @Autowired
    private lateinit var builder: StreamBuilder

    @Bean
    fun source(): StreamApplication =
            StreamApplication("http").addProperty("server.port", 19092)

    @Bean
    fun processor() = StreamApplication("splitter")
            .addProperty("producer.partitionKeyExpression", "payload")

    @Bean
    fun sink() = StreamApplication("log").addDeploymentProperty("count", 2)

    @Bean
    fun woodChuck(deploymentProperties: Map<String, String>) = builder
            .name("woodchuck")
            .definition("http --server.port=9900 | splitter --expression=payload.split(' ') | log")
            .create()
            .deploy(deploymentProperties)

    private fun importApplications() {
        this.dataFlowOperations.appRegistryOperations().register("log", ApplicationType.sink,
                "maven://org.springframework.cloud.stream.app:log-sink-rabbit:2.1.2.RELEASE",
                "maven://org.springframework.cloud.stream.app:log-sink-rabbit:jar:metadata:2.1.2.RELEASE",
                true)
        this.dataFlowOperations.appRegistryOperations().register("splitter", ApplicationType.processor,
                "maven://org.springframework.cloud.stream.app:splitter-processor-rabbit:2.1.1.RELEASE",
                "maven://org.springframework.cloud.stream.app:splitter-processor-rabbit:jar:metadata:2.1.1.RELEASE",
                true)
        this.dataFlowOperations.appRegistryOperations().register("http", ApplicationType.source,
                "maven://org.springframework.cloud.stream.app:http-source-rabbit:2.1.1.RELEASE",
                "maven://org.springframework.cloud.stream.app:http-source-rabbit:jar:metadata:2.1.1.RELEASE",
                true)
    }

    @Bean
    fun deploymentProperties() = DeploymentPropertiesBuilder()
            .memory("log", 512)
            .count("log", 2)
            .put("app.splitter.producer.partitionKeyExpression", "payload")
            .build()

    @Bean
    fun command(deployedStream: Stream) = ApplicationRunner { appArgs ->
        importApplications()

        with(deployedStream) {
            while(status != "deployed") {
                logger.info("Waiting for deploy")
                Thread.sleep(5000)
            }

            logger.info("Stream will run for 2 minutes")
            Thread.sleep(120000)

            logger.info("Destroying stream")
            destroy()
        }
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            SpringApplicationBuilder(KeyFlow::class.java)
                    .run(*args)
        }
    }
}