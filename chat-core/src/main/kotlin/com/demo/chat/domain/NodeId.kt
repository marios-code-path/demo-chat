package com.demo.chat.domain

/**
 * A validated node id. Ten node bits in [SnowflakeGenerator] give 1024 values.
 *
 * There is no default and no derivation. A deployment states its node id, and
 * two deployments that write to one store must not state the same one.
 */
class NodeId(val value: Int) {

    init {
        require(value in MIN..MAX) { message(value.toString()) }
    }

    override fun equals(other: Any?): Boolean = other is NodeId && other.value == value

    override fun hashCode(): Int = value

    override fun toString(): String = "NodeId($value)"

    companion object {
        const val MIN = 0
        const val MAX = 1023

        fun parse(raw: String?): NodeId {
            val trimmed = raw?.trim()
            require(!trimmed.isNullOrEmpty()) { message(raw) }
            val parsed = trimmed.toIntOrNull()
            require(parsed != null) { message(raw) }
            return NodeId(parsed)
        }

        fun message(raw: String?): String =
            "app.nodeid is required and has no default. " +
                "Set it to an integer in $MIN..$MAX, unique across every deployment " +
                "that writes to this store. Got: ${describe(raw)}"

        // Only a null property is unset. An empty or blank value was supplied, so
        // show it in quotes rather than hide it behind the word unset.
        private fun describe(raw: String?): String = if (raw == null) "unset" else "'" + raw + "'"
    }
}
