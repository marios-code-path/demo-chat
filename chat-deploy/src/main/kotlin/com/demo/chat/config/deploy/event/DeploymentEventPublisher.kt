package com.demo.chat.config.deploy.event

import org.springframework.context.ApplicationEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.ApplicationEventPublisherAware
import org.springframework.context.annotation.Configuration

@Configuration
class DeploymentEventPublisher: ApplicationEventPublisherAware {

    fun publishEvent(event: ApplicationEvent) {
        publisher.publishEvent(event)
    }

    override fun setApplicationEventPublisher(applicationEventPublisher: ApplicationEventPublisher) {
        this.publisher = applicationEventPublisher
    }

    lateinit var publisher: ApplicationEventPublisher
}