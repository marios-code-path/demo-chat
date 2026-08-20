package com.demo.chat.config.deploy.e2ee

import com.demo.chat.config.crypto.CryptoServiceBeans
import com.demo.chat.config.presence.PresenceServiceBeans
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.PropertySource

/**
 * Master E2EE deployment configuration — imports crypto and presence beans.
 *
 * Activated when app.service.e2ee.enabled=true.
 * This follows the same @ConditionalOnProperty pattern as the rest of the
 * chat-deploy-* family — nearly every @Configuration in the tree is gated
 * by a property toggle.
 */
@Configuration
@ConditionalOnProperty(prefix = "app.service.e2ee", name = ["enabled"], havingValue = "true")
@Import(CryptoServiceBeans::class, PresenceServiceBeans::class)
@PropertySource("classpath:e2ee-defaults.yml")
class E2EEDeployConfiguration
