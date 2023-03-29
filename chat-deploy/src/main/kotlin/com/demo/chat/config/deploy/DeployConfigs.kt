package com.demo.chat.config.deploy

import com.demo.chat.config.deploy.init.RootKeyInitializationListeners
import com.demo.chat.config.deploy.init.UserInitPropertiesConfiguration
import com.demo.chat.config.deploy.security.ActuatorWebSecurityConfiguration
import com.demo.chat.config.deploy.security.RSocketServerConfiguration
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

@Configuration
@Import(
    RootKeyInitializationListeners::class,
    UserInitPropertiesConfiguration::class,
    ActuatorWebSecurityConfiguration::class,
    RSocketServerConfiguration::class
)
class DeployConfigs