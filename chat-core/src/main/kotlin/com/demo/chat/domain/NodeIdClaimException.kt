package com.demo.chat.domain

import java.time.Duration

/**
 * Reports a duplicate node id.
 *
 * The message states the scope, because uniqueness is per key type per
 * store. A message that said "one store" would be false for a redis long
 * deployment beside a redis uuid deployment.
 *
 * The exception carries no backend name. The scope phrase already names the
 * backend.
 */
class NodeIdClaimException(
    val nodeId: NodeId,
    val scope: String,
    val holder: String,
    val ttl: Duration
) : RuntimeException(message(nodeId, scope, holder, ttl)) {

    companion object {
        fun message(nodeId: NodeId, scope: String, holder: String, ttl: Duration): String =
            "app.nodeid=${nodeId.value} is already claimed in the $scope.\n" +
                "Holder: $holder\n" +
                "Two deployments that write to the $scope must not use the same app.nodeid.\n" +
                "Set a different app.nodeid, or stop the other deployment and wait " +
                "${ttl.seconds}s\nfor its lease to expire."
    }
}
