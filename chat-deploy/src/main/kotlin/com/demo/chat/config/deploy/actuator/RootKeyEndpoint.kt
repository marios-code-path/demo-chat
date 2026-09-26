package com.demo.chat.config.deploy.actuator

import com.demo.chat.domain.TypeUtil
import com.demo.chat.domain.knownkey.RootKeySnapshot
import com.demo.chat.domain.knownkey.RootKeys
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.actuate.endpoint.Access
import org.springframework.boot.actuate.endpoint.annotation.Endpoint
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation
import org.springframework.stereotype.Component

/**
 * This endpoint returns the root key snapshot of this node. A node without
 * store access reads it at start. See `CHAT-avduuqwp`.
 */
@Component
@Endpoint(id = "rootkeys", defaultAccess = Access.UNRESTRICTED)
class RootKeyEndpoint<T>(
    private val rootKeys: RootKeys<T>,
    private val typeUtil: TypeUtil<T>,
    @Value("\${app.key.type}") private val keyType: String,
) {
    @ReadOperation
    fun actuateRootKeys(): RootKeySnapshot = RootKeySnapshot.of(rootKeys, keyType, typeUtil)
}
