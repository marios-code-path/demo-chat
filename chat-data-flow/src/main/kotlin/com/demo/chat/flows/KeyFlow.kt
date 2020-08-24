package com.demo.chat.flows

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.cloud.dataflow.rest.client.DataFlowOperations
import org.springframework.cloud.dataflow.rest.client.dsl.DeploymentPropertiesBuilder
import org.springframework.cloud.dataflow.rest.client.dsl.StreamApplication
import org.springframework.cloud.dataflow.rest.client.dsl.StreamBuilder
import org.springframework.context.annotation.Bean

class KeyFlow {

    @Autowired
    private lateinit var flowOp: DataFlowOperations

    @Autowired
    private lateinit var builder: StreamBuilder


    @Bean
    fun source(): StreamApplication =
            StreamApplication("http").addProperty("server.port", 9900)

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
    @Bean
    fun deploymentProperties() = DeploymentPropertiesBuilder()
            .memory("log", 512)
            .count("log", 2)
            .put("app.splitter.producer.partitionKeyExpression", "payload")
            .build()


    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            SpringApplicationBuilder(KeyFlow::class.java)
                    .run(*args)
        }
    }

}