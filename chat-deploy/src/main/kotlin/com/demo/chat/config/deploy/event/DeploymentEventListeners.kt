package com.demo.chat.config.deploy.event

import com.demo.chat.deploy.event.StartupAnnouncementEvent
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationListener
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class DeploymentEventListeners {

    @Value("\${spring.application.name}")
    lateinit var appName: String

    @Bean
    fun startupAnnouncementListener() : ApplicationListener<StartupAnnouncementEvent> {
        return ApplicationListener { evt ->
            println("$appName: ${evt.message}")
        }
    }
}