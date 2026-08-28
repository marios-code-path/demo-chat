package com.demo.chat.domain

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment

/**
 * Supplies the validated [NodeId].
 *
 * This class is in `com.demo.chat.domain` on purpose. `com.demo.chat.config` is
 * component-scanned by every deployment, so a copy placed there would supply the
 * bean in processes that generate no keys and would fail their startup.
 *
 * Read the raw value from [Environment]. Do not use `@Value("\${app.nodeid}")`.
 * Spring resolves a placeholder before it injects a field, so an unset property
 * fails placeholder resolution and [NodeId.parse] never reports the missing value.
 */
@Configuration
open class NodeIdConfiguration {

    @Bean
    open fun nodeId(environment: Environment): NodeId =
        NodeId.parse(environment.getProperty("app.nodeid"))
}
