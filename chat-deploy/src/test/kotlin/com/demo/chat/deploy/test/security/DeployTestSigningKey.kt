package com.demo.chat.deploy.test.security

import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import org.springframework.test.context.DynamicPropertyRegistry
import java.nio.file.Files
import java.util.UUID

/** The trusted signing key for the deployment security tests. */
internal object DeployTestSigningKey {

    private val jwkPath: String = run {
        val key = ECKeyGenerator(Curve.P_256).keyID(UUID.randomUUID().toString()).generate()
        val file = Files.createTempFile("deploy-test-signing-key", ".jwk")
        file.toFile().deleteOnExit()
        Files.writeString(file, key.toJSONString())
        file.toString()
    }

    fun register(registry: DynamicPropertyRegistry) {
        registry.add("app.security.jwt.jwk-path") { jwkPath }
        registry.add("app.security.agent.client-id") { "client-under-test" }
        registry.add("app.security.agent.username") { "agent-svc" }
        registry.add("app.security.agent.required-scope") { "chat.mcp" }
    }

    fun agentToken(): String = mint("client-under-test", "chat.mcp")

    fun mint(clientId: String, scope: String): String =
        TestTokenMinter.mint(jwkPath, clientId, scope)

    fun mintForAudience(clientId: String, scope: String, audience: String): String =
        TestTokenMinter.mint(jwkPath, clientId, scope, audience)

    fun mintExpired(clientId: String, scope: String): String =
        TestTokenMinter.mint(jwkPath, clientId, scope, expiresInMillis = -1)
}
