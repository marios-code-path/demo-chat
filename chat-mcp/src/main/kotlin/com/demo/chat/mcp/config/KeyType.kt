package com.demo.chat.mcp.config

/** The key type of the deployment. The operator states it. */
enum class KeyType {
    LONG,
    UUID,
    ;

    companion object {
        /**
         * Read the configured text.
         *
         * Only `long` and `uuid` are accepted. The comparison is exact, so a
         * different spelling is refused.
         */
        fun of(text: String?): KeyType =
            when (text) {
                null -> throw ConfigException("keyType is required")
                "long" -> LONG
                "uuid" -> UUID
                else -> throw ConfigException("keyType must be exactly long or uuid, not '$text'")
            }
    }
}