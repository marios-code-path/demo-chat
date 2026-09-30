package com.demo.chat.config.agent

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

/**
 * One place that turns on the `app.security` binding. A reader finds the
 * required property set from here. See `CHAT-pgpmsgvr`.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AgentSecurityProperties::class)
class AgentSecurityConfiguration
