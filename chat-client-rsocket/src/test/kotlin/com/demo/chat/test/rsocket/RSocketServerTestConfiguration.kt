package com.demo.chat.test.rsocket

import com.demo.chat.convert.JsonNodeToAnyConverter
import com.demo.chat.domain.serializers.JacksonModules
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
import org.springframework.boot.autoconfigure.rsocket.RSocketMessagingAutoConfiguration
import org.springframework.boot.autoconfigure.rsocket.RSocketRequesterAutoConfiguration
import org.springframework.boot.autoconfigure.rsocket.RSocketServerAutoConfiguration
import org.springframework.boot.autoconfigure.rsocket.RSocketStrategiesAutoConfiguration

class TestModules : JacksonModules(JsonNodeToAnyConverter, JsonNodeToAnyConverter)

@SpringBootConfiguration
@ImportAutoConfiguration(
    TestModules::class,
    JacksonAutoConfiguration::class,
    RSocketStrategiesAutoConfiguration::class,
    RSocketServerAutoConfiguration::class,
    RSocketMessagingAutoConfiguration::class,
    RSocketRequesterAutoConfiguration::class,
)
class RSocketServerTestConfiguration