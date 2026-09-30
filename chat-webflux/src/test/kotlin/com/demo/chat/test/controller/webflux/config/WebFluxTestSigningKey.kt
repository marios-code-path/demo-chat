package com.demo.chat.test.controller.webflux.config

import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

/** A temporary ES256 key for application-chain tests. */
internal object WebFluxTestSigningKey {
    private val jwkLocation: String = generate().toUri().toString()

    fun path(): String = Path.of(java.net.URI(jwkLocation)).toString()

    private fun generate(): Path {
        val jwk = ECKeyGenerator(Curve.P_256).keyID(UUID.randomUUID().toString()).generate()
        val file = Files.createTempFile("webflux-test-signing-key", ".jwk")
        file.toFile().deleteOnExit()
        Files.writeString(file, jwk.toJSONString())
        return file
    }
}
