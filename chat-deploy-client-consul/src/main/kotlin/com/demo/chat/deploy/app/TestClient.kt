package com.demo.chat.deploy.app

import com.demo.chat.client.rsocket.config.*
import com.demo.chat.config.CoreClientBeans
import com.demo.chat.deploy.config.client.AppClientBeansConfiguration
import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.serializers.DefaultChatJacksonModules
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
import org.springframework.boot.autoconfigure.rsocket.RSocketStrategiesAutoConfiguration
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.core.ParameterizedTypeReference
import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.messaging.rsocket.RSocketStrategies
import java.util.concurrent.ConcurrentHashMap

@SpringBootApplication
@Import(
    DefaultChatJacksonModules::class,
    JacksonAutoConfiguration::class,
    RSocketStrategiesAutoConfiguration::class,
)
class TestClient {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            runApplication<TestClient>(*args)
        }
    }

    @Bean
    fun requesterBuilder(strategies: RSocketStrategies): RSocketRequester.Builder =
        RSocketRequester.builder().rsocketStrategies(strategies)

    @Bean
    fun requesterFactory(
        builder: RSocketRequester.Builder,
        strategies: RSocketStrategies,
    ): DefaultRequesterFactory =
        DefaultRequesterFactory(
            builder,
            SecureConnection(),
            coreConnectionProps()
        )

    @Bean
    fun runner() : CommandLineRunner = CommandLineRunner {
        println(coreConnectionProps())
    }

    @ConfigurationProperties("app.rsocket.config.core")
    @Bean
    fun coreConnectionProps():Map<String, RSocketConnectionProperties> = ConcurrentHashMap()

    @Configuration
    class AppRSocketClientBeansConfiguration(clients: CoreClientBeans<Long, String, IndexSearchRequest>) :
        AppClientBeansConfiguration<Long, String, IndexSearchRequest>(
            clients,
            ParameterizedTypeReference.forType(Long::class.java)
        )

    @Bean
    fun coreRSocketClientBeans(
        requesterFactory: RequesterFactory,
        appRSocketProps: AppRSocketProperties
    ) = CoreRSocketClients<Long, String, IndexSearchRequest>(
        requesterFactory,
        appRSocketProps.core,
        ParameterizedTypeReference.forType(Long::class.java)
    )

    val logger = LoggerFactory.getLogger(this::class.java.canonicalName)

}