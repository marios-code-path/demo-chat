package com.demo.chat.deploy.app.memory

import com.demo.chat.deploy.config.controllers.core.IndexControllersConfiguration
import com.demo.chat.deploy.config.controllers.core.KeyControllersConfiguration
import com.demo.chat.deploy.config.controllers.core.PersistenceControllersConfiguration
import com.demo.chat.deploy.config.controllers.core.PubSubControllerConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration

class CoreControllerConfiguration {

    @Configuration
    @ConditionalOnProperty(prefix = "app.service.core", name = ["index"])
    class IndexControllers : IndexControllersConfiguration()

    @Configuration
    @ConditionalOnProperty(prefix = "app.service.core", name = ["persistence"])
    class PersistenceControllers : PersistenceControllersConfiguration()

    @Configuration
    @ConditionalOnProperty(prefix = "app.service.core", name = ["key"])
    class KeyControllers : KeyControllersConfiguration()

    @Configuration
    @ConditionalOnProperty(prefix = "app.service.core", name = ["pubsub"])
    class PubSubControllers : PubSubControllerConfiguration()
}