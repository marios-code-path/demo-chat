package com.demo.chat.config

import com.demo.chat.convert.JsonNodeToAnyConverter
import com.demo.chat.domain.serializers.JacksonModules
import org.springframework.context.annotation.Configuration

@Configuration
open class DefaultChatJacksonModules() : JacksonModules(JsonNodeToAnyConverter, JsonNodeToAnyConverter)