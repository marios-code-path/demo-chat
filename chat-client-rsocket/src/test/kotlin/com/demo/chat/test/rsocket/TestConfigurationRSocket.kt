package com.demo.chat.test.rsocket

import com.demo.chat.convert.JsonNodeToAnyConverter
import com.demo.chat.domain.serializers.JacksonModules
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
import org.springframework.boot.autoconfigure.rsocket.RSocketMessagingAutoConfiguration
import org.springframework.boot.autoconfigure.rsocket.RSocketStrategiesAutoConfiguration
import org.springframework.context.annotation.Import


class TestModules : JacksonModules(JsonNodeToAnyConverter, JsonNodeToAnyConverter)

@Import(TestModules::class,
        JacksonAutoConfiguration::class,
        RSocketStrategiesAutoConfiguration::class,
        RSocketMessagingAutoConfiguration::class)
open class TestConfigurationRSocket