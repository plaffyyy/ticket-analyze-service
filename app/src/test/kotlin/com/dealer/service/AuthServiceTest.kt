package com.dealer.service

import com.dealer.domain.dto.LoginRequest
import com.dealer.domain.dto.RefreshRequest
import com.dealer.domain.dto.RegisterRequest
import com.dealer.domain.model.RefreshToken
import com.dealer.domain.model.User
import com.dealer.exception.ConflictException
import com.dealer.exception.NotFoundException
import com.dealer.exception.UnauthorizedException
import com.dealer.metrics.AppMetrics
import com.dealer.repository.RefreshTokenRepository
import com.dealer.repository.UserRepository
import com.dealer.security.JwtProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.security.crypto.password.PasswordEncoder
import java.security.MessageDigest
import java.time.OffsetDateTime
import java.util.Optional
import java.util.UUID

private fun sha256(input: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { "%02x".format(it) }
}

class AuthServiceTest {
    private val userRepository = mockk<UserRepository>()
    private val refreshTokenRepository = mockk<RefreshTokenRepository>(relaxed = true)
    private val passwordEncoder = mockk<PasswordEncoder>()
    private val jwtProvider = mockk<JwtProvider>()
    private val appMetrics = mockk<AppMetrics>(relaxed = true)

    private val service =
        AuthService(
            userRepository,
            refreshTokenRepository,
            passwordEncoder,
            jwtProvider,
            appMetrics,
        )

    private fun newUser(
        id: UUID = UUID.randomUUID(),
        email: String = "a@b.com",
    ) = User(
        name = "User",
        email = email,
        passwordHash = "hashed",
    ).apply { this.id = id }

    @Test
    fun `register throws when email exists`() {
        every { userRepository.existsByEmail(any()) } returns true

        assertThrows<ConflictException> {
            service.register(RegisterRequest("Name", "u@x.com", "password12"))
        }
        verify(exactly = 0) { userRepository.save(any()) }
    }

    @Test
    fun `register creates user and returns tokens`() {
        every { userRepository.existsByEmail(any()) } returns false
        every { passwordEncoder.encode(any()) } returns "hash"
        every { jwtProvider.generateAccessToken(any(), any()) } returns "access"
        every { userRepository.save(any()) } answers {
            val u = firstArg<User>()
            u.id = UUID.randomUUID()
            u
        }
        every { refreshTokenRepository.save(any()) } answers { firstArg() }

        val r = service.register(RegisterRequest("Name", "u@x.com", "password12"))

        assertEquals("access", r.accessToken)
        assertNotNull(r.refreshToken)
        assertEquals("u@x.com".lowercase(), r.user.email)
        verify { refreshTokenRepository.save(any()) }
    }

    @Test
    fun `register normalizes email before uniqueness check`() {
        every { userRepository.existsByEmail("user@x.com") } returns false
        every { passwordEncoder.encode("password12") } returns "hash"
        every { jwtProvider.generateAccessToken(any(), any()) } returns "access"
        every { userRepository.save(any()) } answers { firstArg<User>().apply { id = UUID.randomUUID() } }
        every { refreshTokenRepository.save(any()) } answers { firstArg() }

        val result = service.register(RegisterRequest("Name", " User@X.com ", "password12"))

        assertEquals("user@x.com", result.user.email)
        verify { userRepository.existsByEmail("user@x.com") }
    }

    @Test
    fun `login throws when user not found`() {
        every { userRepository.findByEmail(any()) } returns Optional.empty()

        assertThrows<NotFoundException> {
            service.login(LoginRequest("u@x.com", "password12"))
        }
    }

    @Test
    fun `login throws when password invalid`() {
        val u = newUser()
        every { userRepository.findByEmail(any()) } returns Optional.of(u)
        every { passwordEncoder.matches(any(), any()) } returns false

        assertThrows<UnauthorizedException> {
            service.login(LoginRequest(u.email, "wrong"))
        }
    }

    @Test
    fun `login succeeds`() {
        val u = newUser()
        every { userRepository.findByEmail(any()) } returns Optional.of(u)
        every { passwordEncoder.matches(any(), any()) } returns true
        every { jwtProvider.generateAccessToken(any(), any()) } returns "access"
        every { refreshTokenRepository.save(any()) } answers { firstArg() }

        val r = service.login(LoginRequest(u.email, "password12"))

        assertEquals("access", r.accessToken)
        verify { refreshTokenRepository.save(any()) }
    }

    @Test
    fun `login normalizes email before lookup`() {
        val u = newUser(email = "user@x.com")
        every { userRepository.findByEmail("user@x.com") } returns Optional.of(u)
        every { passwordEncoder.matches("password12", u.passwordHash) } returns true
        every { jwtProvider.generateAccessToken(any(), any()) } returns "access"
        every { refreshTokenRepository.save(any()) } answers { firstArg() }

        val r = service.login(LoginRequest(" User@X.com ", "password12"))

        assertEquals("access", r.accessToken)
        verify { userRepository.findByEmail("user@x.com") }
    }

    @Test
    fun `refresh throws when token unknown`() {
        every { refreshTokenRepository.findByTokenHash(any()) } returns Optional.empty()

        assertThrows<UnauthorizedException> {
            service.refresh(RefreshRequest("raw-token"))
        }
    }

    @Test
    fun `refresh throws when expired`() {
        val u = newUser()
        val raw = "refresh-raw"
        val token =
            RefreshToken(
                user = u,
                tokenHash = sha256(raw),
                expiresAt = OffsetDateTime.now().minusSeconds(1),
            )
        every { refreshTokenRepository.findByTokenHash(sha256(raw)) } returns Optional.of(token)

        assertThrows<UnauthorizedException> {
            service.refresh(RefreshRequest(raw))
        }
        verify { refreshTokenRepository.delete(token) }
    }

    @Test
    fun `refresh succeeds`() {
        val u = newUser()
        val raw = "refresh-raw"
        val token =
            RefreshToken(
                user = u,
                tokenHash = sha256(raw),
                expiresAt = OffsetDateTime.now().plusDays(1),
            )
        every { refreshTokenRepository.findByTokenHash(sha256(raw)) } returns Optional.of(token)
        every { jwtProvider.generateAccessToken(u.id, u.email) } returns "new-access"
        every { refreshTokenRepository.save(any()) } answers { firstArg() }

        val r = service.refresh(RefreshRequest(raw))

        assertEquals("new-access", r.accessToken)
        assertNotNull(r.refreshToken)
        verify { refreshTokenRepository.delete(token) }
        verify { refreshTokenRepository.save(any()) }
    }

    @Test
    fun `logout deletes hash`() {
        val raw = "tok"
        every { refreshTokenRepository.deleteByTokenHash(sha256(raw)) } returns Unit

        service.logout(raw)

        verify { refreshTokenRepository.deleteByTokenHash(sha256(raw)) }
    }
}
