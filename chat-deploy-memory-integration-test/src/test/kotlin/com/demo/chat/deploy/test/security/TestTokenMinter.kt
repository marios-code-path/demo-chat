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
import java.util.Date

internal object TestTokenMinter {

    fun mint(jwkFile: String, clientId: String, scope: String): String {
        val key = JWK.parse(Files.readString(Paths.get(jwkFile))) as ECKey
        val claims = JWTClaimsSet.Builder()
            .issuer("https://authserv")
            .subject(clientId)
            .claim("client_id", clientId)
            .claim("scope", scope)
            .issueTime(Date())
            .expirationTime(Date(System.currentTimeMillis() + 300_000))
            .build()
        val signed = SignedJWT(
            JWSHeader.Builder(JWSAlgorithm.ES256).keyID(key.keyID).type(JOSEObjectType.JWT).build(),
            claims,
        )
        signed.sign(ECDSASigner(key))
        return signed.serialize()
    }
}
