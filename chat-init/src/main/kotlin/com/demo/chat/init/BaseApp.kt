package com.demo.chat.init

import com.demo.chat.config.client.rsocket.RSocketClientProperties
import com.demo.chat.init.domain.BootstrapProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication(scanBasePackages = ["com.demo.chat.config"])
class BaseApp {

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            runApplication<InitApp>(*args)
        }
    }
//
//    @Bean
//    @ConditionalOnMissingBean(RequesterFactory::class)
//    fun requesterFactory(
//        builder: RSocketRequester.Builder,
//        connection: TransportFactory,
//        rSocketClientProperties: RSocketClientProperties
//    ): RequesterFactory = DefaultRequesterFactory(builder, connection, rSocketClientProperties)
}