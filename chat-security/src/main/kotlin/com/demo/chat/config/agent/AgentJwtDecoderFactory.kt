package com.demo.chat.config.agent

import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jwt.SignedJWT
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder
import reactor.core.publisher.Flux
import java.nio.file.Files
import java.nio.file.Paths
import java.util.function.Function

/** Builds the local decoder that trusts the public half of one JWK file. */
object AgentJwtDecoderFactory {

    fun fromJwkFile(path: String): NimbusReactiveJwtDecoder {
        val file = Paths.get(path)
        val json = try {
            Files.readString(file)
        } catch (e: Exception) {
            throw IllegalStateException(
                "The JWK file '${file.fileName}' is not readable at '$path'. Cause: ${e.message}"
            ).apply { initCause(e) }
        }

        val parsed = try {
            JWK.parse(json)
        } catch (e: Exception) {
            throw IllegalStateException(
                "The file '${file.fileName}' at '$path' is not a JWK. Cause: ${e.message}"
            ).apply { initCause(e) }
        }

        val publicJwk = parsed.toPublicJWK()
            ?: throw IllegalStateException("The JWK in '${file.fileName}' at '$path' holds no public key.")

        if (publicJwk !is ECKey) {
            throw IllegalStateException(
                "The JWK in '${file.fileName}' at '$path' is a ${publicJwk.keyType.value} key. " +
                    "This deployment trusts an EC key for ${SignatureAlgorithm.ES256.name}."
            )
        }

        if (publicJwk.curve != Curve.P_256) {
            throw IllegalStateException(
                "The JWK in '${file.fileName}' at '$path' is on curve ${publicJwk.curve.name}. " +
                    "${SignatureAlgorithm.ES256.name} requires ${Curve.P_256.name}."
            )
        }

        val jwkSource = Function<SignedJWT, Flux<JWK>> { Flux.just(publicJwk) }
        return NimbusReactiveJwtDecoder.withJwkSource(jwkSource)
            .jwsAlgorithm(SignatureAlgorithm.ES256)
            .build()
    }
}
