package com.demo.chat.config.agent

import org.springframework.core.env.PropertyResolver

/**
 * The core REST controllers that a REST launch refuses. See `CHAT-bnnkhgbd`.
 *
 * **No access check guards these controllers.** The REST chain requires one
 * authority, the agent scope, so any valid agent token reached every route. A
 * persistence write took any sender, into any room, and it bypassed the `SEND`
 * check of the composite. Measured on 2026-10-04.
 *
 * The RSocket side refuses these routes at the seam, with `ROLE_SERVICE` or
 * `ROLE_ADMIN` (`CHAT-rdlghoqe`). An agent token never holds either role, so
 * that rule would refuse every REST caller. The owner chose to refuse the
 * combination at startup on 2026-10-04.
 */
object CoreRestControllers {

    /** The `app.controller` switches of the core REST controllers. */
    val SWITCHES = listOf("persistence", "index", "key", "secrets", "pubsub")

    /**
     * The switches that mount a controller. Each controller reads its switch
     * with `@ConditionalOnProperty` and no `havingValue`. So a present value
     * mounts it, an empty value included, unless the value is `false`.
     */
    fun enabled(properties: PropertyResolver): List<String> = SWITCHES.filter { name ->
        properties.getProperty("app.controller.$name")
            ?.let { !it.equals("false", ignoreCase = true) }
            ?: false
    }

    fun requireAbsent(properties: PropertyResolver) {
        val enabled = enabled(properties)
        check(enabled.isEmpty()) {
            "A REST launch refuses the core REST controllers, because no access check guards them. " +
                "Remove ${enabled.joinToString { "app.controller.$it" }}. " +
                "Seed through the composite routes instead. See CHAT-bnnkhgbd."
        }
    }
}
