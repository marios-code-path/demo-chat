package com.demo.chat.deploy.config.core

import com.demo.chat.codec.JsonNodeAnyDecoder
import com.demo.chat.domain.serializers.JacksonModules
import org.springframework.context.annotation.Configuration

class SerializationConfiguration : JacksonConfiguration()

open class JacksonConfiguration {
    @Configuration
    class AppJacksonModules : JacksonModules(JsonNodeAnyDecoder, JsonNodeAnyDecoder)
}