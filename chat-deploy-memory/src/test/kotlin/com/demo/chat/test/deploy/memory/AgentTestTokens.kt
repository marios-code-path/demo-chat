package com.demo.chat.test.deploy.memory

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Date

/** Signs agent tokens for the agent selection tests. See `CHAT-frcrctdp`. */
internal object AgentTestTokens {

    fun createKey(): String {
        val key = ECKeyGenerator(Curve.P_256).keyID("agent-selection").generate()
        val file = Files.createTempFile("agent-selection", ".jwk")
        file.toFile().deleteOnExit()
        Files.writeString(file, key.toJSONString())
        return file.toString()
    }

    /**
     * A negative [expiresInMillis] mints an expired token. Keep it beyond the
     * 60 second clock skew that the decoder allows.
     */
    fun mint(path: String, clientId: String, expiresInMillis: Long = 60_000): String {
        val key = JWK.parse(Files.readString(Paths.get(path))) as ECKey
        val now = System.currentTimeMillis()
        val claims = JWTClaimsSet.Builder()
            .issuer("https://authserv").subject(clientId)
            .claim("client_id", clientId).claim("scope", "chat.mcp")
            .issueTime(Date(minOf(now, now + expiresInMillis) - 1_000))
            .expirationTime(Date(now + expiresInMillis))
            .build()
        val token = SignedJWT(JWSHeader.Builder(JWSAlgorithm.ES256).keyID(key.keyID).build(), claims)
        token.sign(ECDSASigner(key))
        return token.serialize()
    }
}
