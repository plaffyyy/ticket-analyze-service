package com.dealer.security

import com.dealer.config.JwtProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.UUID

class JwtProviderTest {

    private fun provider(): JwtProvider {
        val props = JwtProperties().apply {
            secret = "x".repeat(32)
            accessTokenTtl = Duration.ofMinutes(15)
        }
        return JwtProvider(props)
    }

    @Test
    fun `generateAccessToken validates and exposes claims`() {
        val p = provider()
        val uid = UUID.randomUUID()
        val token = p.generateAccessToken(uid, "a@b.com")

        assertTrue(p.validateToken(token))
        assertEquals(uid, p.getUserIdFromToken(token))
        assertEquals("a@b.com", p.getEmailFromToken(token))
    }

    @Test
    fun `validateToken returns false for garbage`() {
        assertFalse(provider().validateToken("not-a-jwt"))
    }
}
