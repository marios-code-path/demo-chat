package com.demo.chat.deploy.app.memory

import com.demo.chat.controller.config.IndexControllersConfiguration
import com.demo.chat.controller.config.KeyControllersConfiguration
import com.demo.chat.controller.config.PersistenceControllersConfiguration
import com.demo.chat.controller.config.PubSubControllerConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration

open class CoreControllerConfiguration {

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