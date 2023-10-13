package com.demo.chat.config.deploy

import com.demo.chat.config.deploy.init.RootKeyInitializationListeners
import com.demo.chat.config.deploy.init.UserInitPropertiesConfiguration
import com.demo.chat.config.deploy.security.ActuatorWebSecurityConfiguration
import com.demo.chat.config.deploy.security.RSocketServerConfiguration
import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.IndexSearchRequestConverters
import com.demo.chat.domain.MapRequestConverters
import com.demo.chat.domain.RequestToQueryConverters
import com.demo.chat.domain.serializers.EmptyMessageUtil
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

@Configuration
@Import(
    RootKeyInitializationListeners::class,
    UserInitPropertiesConfiguration::class,
    ActuatorWebSecurityConfiguration::class,
    RSocketServerConfiguration::class
)
class DeployConfigs {
    @Bean
    fun emptyMessage(): EmptyMessageUtil<String> = object : EmptyMessageUtil<String> {
        override fun invoke(): String = ""
    }
}