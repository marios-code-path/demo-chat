package com.demo.chat.test.controller.webflux.config

import com.demo.chat.config.agent.AgentJwtDecoderFactory
import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.file.Files
import java.nio.file.Path
import java.util.Date
import java.util.UUID

class AgentJwtDecoderFactoryTests {

    private fun tempFile(name: String, content: String): Path {
        val file = Files.createTempFile(name, ".jwk")
        file.toFile().deleteOnExit()
        Files.writeString(file, content)
        return file
    }

    private fun es256Key(): ECKey = ECKeyGenerator(Curve.P_256)
        .keyID(UUID.randomUUID().toString())
        .generate()

    private fun token(key: ECKey, expiry: Date): String {
        val claims = JWTClaimsSet.Builder()
            .issuer("https://authserv")
            .subject("client-under-test")
            .claim("client_id", "client-under-test")
            .claim("scope", "chat.mcp")
            .expirationTime(expiry)
            .build()
        val signed = SignedJWT(
            JWSHeader.Builder(JWSAlgorithm.ES256).keyID(key.keyID).type(JOSEObjectType.JWT).build(),
            claims,
        )
        signed.sign(ECDSASigner(key))
        return signed.serialize()
    }

    @Test
    fun `a token signed by the trusted key decodes`() {
        val key = es256Key()
        val file = tempFile("agent-decoder", key.toJSONString())

        val decoder = AgentJwtDecoderFactory.fromJwkFile(file.toString())
        val jwt = decoder.decode(token(key, Date(System.currentTimeMillis() + 60_000))).block()!!

        assertThat(jwt.claims["client_id"]).isEqualTo("client-under-test")
        assertThat(jwt.claims["scope"]).isEqualTo("chat.mcp")
    }

    @Test
    fun `an expired token is refused`() {
        val key = es256Key()
        val file = tempFile("agent-decoder-expired", key.toJSONString())
        val decoder = AgentJwtDecoderFactory.fromJwkFile(file.toString())

        assertThrows<Exception> {
            decoder.decode(token(key, Date(System.currentTimeMillis() - 60_000))).block()
        }
    }

    @Test
    fun `a token signed by another key is refused`() {
        val trusted = es256Key()
        val other = es256Key()
        val file = tempFile("agent-decoder-other", trusted.toJSONString())
        val decoder = AgentJwtDecoderFactory.fromJwkFile(file.toString())

        assertThrows<Exception> {
            decoder.decode(token(other, Date(System.currentTimeMillis() + 60_000))).block()
        }
    }

    @Test
    fun `a JWK that carries the private key still decodes`() {
        val key = es256Key()
        val file = tempFile("agent-decoder-private", key.toJSONString())
        val decoder = AgentJwtDecoderFactory.fromJwkFile(file.toString())

        val jwt = decoder.decode(token(key, Date(System.currentTimeMillis() + 60_000))).block()!!

        assertThat(jwt.subject).isEqualTo("client-under-test")
    }

    @Test
    fun `an RSA key is refused, and the message names the file`() {
        val rsa = RSAKeyGenerator(2048).keyID(UUID.randomUUID().toString()).generate()
        val file = tempFile("agent-decoder-rsa", rsa.toJSONString())

        val failure = assertThrows<RuntimeException> {
            AgentJwtDecoderFactory.fromJwkFile(file.toString())
        }

        assertThat(failure.message).contains(file.fileName.toString())
    }

    @Test
    fun `a key on another curve is refused, and the message names both curves`() {
        val p384 = ECKeyGenerator(Curve.P_384).keyID(UUID.randomUUID().toString()).generate()
        val file = tempFile("agent-decoder-p384", p384.toJSONString())

        val failure = assertThrows<RuntimeException> {
            AgentJwtDecoderFactory.fromJwkFile(file.toString())
        }

        assertThat(failure.message).contains("P-384").contains("P-256")
    }

    @Test
    fun `an absent file is refused, and the message names the path`() {
        val missing = "/tmp/agent-decoder-does-not-exist-${UUID.randomUUID()}.jwk"

        val failure = assertThrows<RuntimeException> {
            AgentJwtDecoderFactory.fromJwkFile(missing)
        }

        assertThat(failure.message).contains(missing)
    }

    @Test
    fun `a file that is not a JWK is refused`() {
        val file = tempFile("agent-decoder-garbage", "this is not json")

        val failure = assertThrows<RuntimeException> {
            AgentJwtDecoderFactory.fromJwkFile(file.toString())
        }

        assertThat(failure.message).contains(file.fileName.toString())
    }
}
