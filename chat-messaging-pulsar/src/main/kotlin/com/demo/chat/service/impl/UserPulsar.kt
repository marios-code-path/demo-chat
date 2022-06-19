package com.demo.chat.service.impl

import com.demo.chat.domain.Key
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
import com.demo.chat.domain.User
import com.demo.chat.domain.serializers.DefaultChatJacksonModules
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.pulsar.client.api.AuthenticationFactory
import org.apache.pulsar.client.api.PulsarClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean


@SpringBootApplication
class UserPulsar {
    private val SERVICE_URL = "pulsar+ssl://pulsar-azure-eastus.streaming.datastax.com:6651"

    private val mapper: ObjectMapper = ObjectMapper()

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            runApplication<UserPulsar>(*args)
        }
    }

    @Value("\${chat.pulsar.authParams}")
    private lateinit var PULSAR_TOKEN: String

    @Bean
    fun commandRunner(): ApplicationRunner = ApplicationRunner {
        // Create client object
        val client = PulsarClient.builder()
            .serviceUrl(SERVICE_URL)
            .authentication(
                AuthenticationFactory.token(PULSAR_TOKEN)
            )
            .build()

        // Create producer on a topic
        val producer = client.newProducer()
            .topic("persistent://chatkt-users/default/messages")
            .create()

        mapper.registerModules(
            DefaultChatJacksonModules().messageModule(),
        )

        val user = User.create(
            Key.funKey(1),
            "Mario",
            "mrGray",
            "https://cdn.sanity.io/images/bbnkhnhl/production/e2aedef5d76517dad5e547a48d4fe60713526cb4-312x312.png"
        )
        val message = Message.create(MessageKey.Factory.create(user.key, 2L, 3L), "Hello There", true)

        // Send a message to the topic
        producer.send(mapper.writeValueAsBytes(message))

        //Close the producer
        producer.close()

        // Close the client
        client.close()
    }
}