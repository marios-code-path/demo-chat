package com.demo.deploy.config.initializers

import com.demo.chat.codec.JsonNodeAnyCodec
import com.demo.chat.domain.serializers.JacksonModules
import org.springframework.boot.autoconfigure.http.codec.CodecsAutoConfiguration
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.support.GenericApplicationContext
import java.util.function.Supplier

class CodecsContextInitializer : ApplicationContextInitializer<GenericApplicationContext> {
    override fun initialize(ctx: GenericApplicationContext) {
        ctx.registerBean(JacksonModules::class.java, Supplier {
            JacksonModules(JsonNodeAnyCodec, JsonNodeAnyCodec)
        })
        ctx.registerBean(JacksonAutoConfiguration::class.java, Supplier {
            JacksonAutoConfiguration()
        })
        ctx.registerBean(CodecsAutoConfiguration::class.java, Supplier {
            CodecsAutoConfiguration()
        })
        ctx.registerBean(ValidationAutoConfiguration::class.java, Supplier {
            ValidationAutoConfiguration()
        })

    }

}