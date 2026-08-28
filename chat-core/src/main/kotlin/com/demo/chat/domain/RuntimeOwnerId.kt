package com.demo.chat.domain

import java.net.InetAddress
import java.security.SecureRandom

/**
 * The owner of a node id lease, for one process.
 *
 * The value is unique per process. A restart never reuses it, so a restarted
 * deployment cannot mistake its own stale lease for a live one. The value is
 * readable, so the duplicate node message names a real host and process.
 */
class RuntimeOwnerId(val value: String) {

    override fun toString(): String = value

    companion object {
        private val random = SecureRandom()

        fun generate(applicationName: String): RuntimeOwnerId {
            val host = try {
                InetAddress.getLocalHost().hostName
            } catch (e: Exception) {
                "unknown-host"
            }
            val pid = ProcessHandle.current().pid()
            val suffix = String.format("%08x", random.nextInt())
            return RuntimeOwnerId("$applicationName@$host:$pid#$suffix")
        }
    }
}
