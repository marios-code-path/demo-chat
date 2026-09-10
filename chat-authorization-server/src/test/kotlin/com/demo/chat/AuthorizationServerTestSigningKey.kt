package com.demo.chat

import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import org.springframework.test.context.DynamicPropertyRegistry
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

/**
 * Creates one temporary ES256 key for authorization-server tests.
 *
 * AuthorizationServerConfig reads the key from `app.oauth2.jwk.path`.
 * The repository does not commit `server_keycert.jwk`.
 * No build step runs `gen-dckeys.sh`.
 * The test key has no x5c chain because these tests do not validate one.
 */
internal object AuthorizationServerTestSigningKey {
    private val jwkLocation = generateSigningKey().toUri().toString()

    fun register(registry: DynamicPropertyRegistry) {
        registry.add("app.oauth2.jwk.path") { jwkLocation }
    }

    private fun generateSigningKey(): Path {
        val jwk = ECKeyGenerator(Curve.P_256)
            .keyID(UUID.randomUUID().toString())
            .generate()

        val file = Files.createTempFile("authserver-test-signing-key", ".jwk")
        file.toFile().deleteOnExit()
        Files.writeString(file, jwk.toJSONString())
        return file
    }
}
