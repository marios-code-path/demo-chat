package com.demo.chat.config.client.rsocket

import com.demo.chat.domain.NotFoundException
import com.demo.chat.service.client.ClientProperty
import com.demo.chat.service.client.ClientProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@ConditionalOnProperty("app.client.protocol", havingValue = "rsocket")
@EnableConfigurationProperties(RSocketClientProperties::class)
@Configuration
class RSocketPropertyConfiguration

@ConfigurationProperties("app.client.discovery")
data class RSocketClientProperties
 constructor(val config: Map<String, RSocketClientProperty>) : ClientProperties<ClientProperty> {
    override fun getServiceConfig(str: String): ClientProperty {
        if(!config.containsKey(str))
            throw NotFoundException

        return config[str]!!
    }
}

data class RSocketClientProperty
constructor(
    override var dest: String? = "", override var prefix: String? = ""
) : ClientProperty