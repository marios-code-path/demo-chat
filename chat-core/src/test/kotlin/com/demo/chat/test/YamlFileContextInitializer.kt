package com.demo.chat.test

import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.io.Resource
import java.io.IOException

open class YamlFileContextInitializer(private val locations: String) : ApplicationContextInitializer<ConfigurableApplicationContext> {

        override fun initialize(applicationContext: ConfigurableApplicationContext) {
            try {
                val resource: Resource = applicationContext.getResource(locations)
                val sourceLoader = YamlPropertySourceLoader()
                val yamlTestProperties = sourceLoader.load("yamlTestProperties", resource)
                yamlTestProperties.forEach {
                    applicationContext.environment.propertySources.addLast(it)
                }
            } catch (e: IOException) {
                throw RuntimeException(e)
            }
        }
    }