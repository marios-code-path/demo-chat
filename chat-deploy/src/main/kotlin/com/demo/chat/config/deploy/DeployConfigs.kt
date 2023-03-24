package com.demo.chat.config.deploy

import com.demo.chat.config.deploy.init.*
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

@Configuration
@Import(
    UserInitPropertiesConfiguration::class,
    RootKeyInitializationListeners::class,
    UserInitializationListener::class,
    HttpRootKeyConsumer::class,
)
class DeployConfigs