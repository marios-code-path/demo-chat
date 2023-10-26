package com.demo.chat.test.rsocket

import com.demo.chat.convert.JsonNodeToAnyConverter
import com.demo.chat.domain.serializers.JacksonModules
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Import

class TestModules : JacksonModules(JsonNodeToAnyConverter, JsonNodeToAnyConverter)

@SpringBootConfiguration
@EnableAutoConfiguration
@Import(
    TestModules::class,
    RSocketSecurityTestConfiguration::class
)
class RSocketServerTestConfiguration