package com.demo.chat.deploy.test.security

import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import java.nio.file.Files
import java.util.UUID

/** A signing key that the deployment does not trust. */
internal object SigningKeys {
    private val otherFile: String = run {
        val key = ECKeyGenerator(Curve.P_256).keyID(UUID.randomUUID().toString()).generate()
        val file = Files.createTempFile("deploy-test-foreign-key", ".jwk")
        file.toFile().deleteOnExit()
        Files.writeString(file, key.toJSONString())
        file.toUri().toString()
    }

    fun otherKeyFile(): String = otherFile
}
