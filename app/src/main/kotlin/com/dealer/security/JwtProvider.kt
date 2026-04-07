package com.dealer.security

import com.dealer.config.JwtProperties
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Component
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

@Component
class JwtProvider(
    private val jwtProperties: JwtProperties,
) {
    private val signingKey: SecretKey by lazy {
        Keys.hmacShaKeyFor(jwtProperties.secret.toByteArray(Charsets.UTF_8))
    }

    fun generateAccessToken(
        userId: UUID,
        email: String,
    ): String {
        val now = Date()
        val expiry = Date(now.time + jwtProperties.accessTokenTtl.toMillis())
        return Jwts
            .builder()
            .subject(userId.toString())
            .claim("email", email)
            .issuedAt(now)
            .expiration(expiry)
            .signWith(signingKey)
            .compact()
    }

    fun validateToken(token: String): Boolean =
        runCatching {
            Jwts
                .parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
            true
        }.getOrElse { false }

    fun getUserIdFromToken(token: String): UUID {
        val claims =
            Jwts
                .parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .payload
        return UUID.fromString(claims.subject)
    }

    fun getEmailFromToken(token: String): String {
        val claims =
            Jwts
                .parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .payload
        return claims["email"] as String
    }
}
