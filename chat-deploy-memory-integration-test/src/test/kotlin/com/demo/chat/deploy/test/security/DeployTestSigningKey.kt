package com.demo.chat.deploy.test.security

import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import java.nio.file.Files
import java.util.UUID

internal object DeployTestSigningKey {

    private val jwkPath: String = run {
        val key = ECKeyGenerator(Curve.P_256).keyID(UUID.randomUUID().toString()).generate()
        val file = Files.createTempFile("deploy-test-signing-key", ".jwk")
        file.toFile().deleteOnExit()
        Files.writeString(file, key.toJSONString())
        file.toString()
    }

    fun path(): String = jwkPath

    fun agentToken(): String = TestTokenMinter.mint(jwkPath, "client-under-test", "chat.mcp")

    /** A token for any client and scope. See `CHAT-frcrctdp`. */
    fun mint(clientId: String, scope: String): String = TestTokenMinter.mint(jwkPath, clientId, scope)
}
