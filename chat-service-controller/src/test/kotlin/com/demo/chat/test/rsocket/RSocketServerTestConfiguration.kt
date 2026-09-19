package com.demo.chat.test.rsocket

import com.demo.chat.convert.JsonNodeToAnyConverter
import com.demo.chat.domain.serializers.JacksonModules
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
import org.springframework.boot.autoconfigure.rsocket.RSocketMessagingAutoConfiguration
import org.springframework.boot.autoconfigure.rsocket.RSocketRequesterAutoConfiguration
import org.springframework.boot.autoconfigure.rsocket.RSocketServerAutoConfiguration
import org.springframework.boot.autoconfigure.rsocket.RSocketStrategiesAutoConfiguration
import com.demo.chat.config.ChatJackson3Modules
import org.springframework.boot.rsocket.messaging.RSocketStrategiesCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.http.codec.json.JacksonJsonDecoder
import org.springframework.http.codec.json.JacksonJsonEncoder
import tools.jackson.databind.json.JsonMapper

class TestModules : JacksonModules(JsonNodeToAnyConverter, JsonNodeToAnyConverter)

/**
 * The RSocket test context.
 *
 * **It states its own codec, because the production one does not apply here.**
 * RSocketServerConfiguration is conditional on app.server.proto being rsocket,
 * and this context does not set that property. So the strategies it would
 * contribute, including the chat domain Jackson 3 codec, are absent and the
 * test has to supply the same thing.
 *
 * Spring Boot 4 decodes with Jackson 3, and the chat domain deserializers were
 * Jackson 2 only. Without the Jackson 3 module a domain payload answers a type
 * definition error. See CHAT-qwmjrixq.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan
@ImportAutoConfiguration(TestModules::class)
class RSocketServerTestConfiguration {

    @Bean
    fun testRSocketStrategiesCustomizer(): RSocketStrategiesCustomizer {
        val mapper = JsonMapper.builder()
            .addModule(ChatJackson3Modules().chatJackson3Module())
            .build()

        return RSocketStrategiesCustomizer { strategies ->
            strategies.apply {
                decoder(JacksonJsonDecoder(mapper))
                encoder(JacksonJsonEncoder(mapper))
            }
        }
    }
}