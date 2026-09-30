package com.demo.chat.deploy.test.security

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import java.nio.file.Files
import java.nio.file.Paths
import java.net.URI
import java.util.Date

/** Mints short-lived ES256 tokens from the trusted test key. */
internal object TestTokenMinter {

    fun mint(
        jwkFile: String,
        clientId: String,
        scope: String,
        audience: String? = null,
        expiresInMillis: Long = 300_000,
    ): String {
        val path = if (jwkFile.startsWith("file:")) Paths.get(URI(jwkFile)) else Paths.get(jwkFile)
        val key = JWK.parse(Files.readString(path)) as ECKey
        val claims = JWTClaimsSet.Builder()
            .issuer("https://authserv")
            .subject(clientId)
            .claim("client_id", clientId)
            .claim("scope", scope)
            .issueTime(Date())
            .expirationTime(Date(System.currentTimeMillis() + expiresInMillis))
            .apply { if (audience != null) audience(audience) }
            .build()
        val signed = SignedJWT(
            JWSHeader.Builder(JWSAlgorithm.ES256)
                .keyID(key.keyID)
                .type(JOSEObjectType.JWT)
                .build(),
            claims,
        )
        signed.sign(ECDSASigner(key))
        return signed.serialize()
    }
}
