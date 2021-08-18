package com.demo.chat.deploy.app.memory

import com.demo.chat.deploy.config.codec.IndexSearchRequestConverters
import com.demo.chat.deploy.config.codec.RequestToQueryConverters
import com.demo.chat.deploy.config.codec.ValueLiterals
import com.demo.chat.domain.IndexSearchRequest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.*

@Configuration
class EdgeControllerConfiguration {

    @Bean
    fun requestToIndexSearchRequest(): RequestToQueryConverters<UUID, IndexSearchRequest> =
        IndexSearchRequestConverters()

    @Bean
    fun stringCodecFactory(): ValueLiterals<String> =
            object : ValueLiterals<String> {
                override fun emptyValue() = ""
                override fun fromString(t: String) = t
            }
}