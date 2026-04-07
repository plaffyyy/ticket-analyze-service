package com.dealer.service

import com.dealer.domain.dto.AuthResponse
import com.dealer.domain.dto.LoginRequest
import com.dealer.domain.dto.RefreshRequest
import com.dealer.domain.dto.RegisterRequest
import com.dealer.domain.dto.TokenResponse
import com.dealer.domain.dto.UserDto
import com.dealer.domain.model.RefreshToken
import com.dealer.domain.model.User
import com.dealer.exception.ConflictException
import com.dealer.exception.NotFoundException
import com.dealer.exception.UnauthorizedException
import com.dealer.repository.RefreshTokenRepository
import com.dealer.repository.UserRepository
import com.dealer.security.JwtProvider
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.OffsetDateTime
import java.util.UUID

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtProvider: JwtProvider,
) {
    @Transactional
    fun register(request: RegisterRequest): AuthResponse {
        if (userRepository.existsByEmail(request.email.trim())) {
            throw ConflictException("Email already in use")
        }
        val user =
            User(
                name = request.name.trim(),
                email = request.email.trim().lowercase(),
                passwordHash = passwordEncoder.encode(request.password),
            )
        val savedUser = userRepository.save(user)
        return issueTokens(savedUser)
    }

    @Transactional
    fun login(request: LoginRequest): AuthResponse {
        val user =
            userRepository
                .findByEmail(request.email.trim().lowercase())
                .orElseThrow { NotFoundException("User not found") }
        if (!passwordEncoder.matches(request.password, user.passwordHash)) {
            throw UnauthorizedException("Invalid credentials")
        }
        return issueTokens(user)
    }

    @Transactional
    fun refresh(request: RefreshRequest): TokenResponse {
        val hash = sha256(request.refreshToken)
        val stored =
            refreshTokenRepository
                .findByTokenHash(hash)
                .orElseThrow { UnauthorizedException("Invalid or expired refresh token") }
        if (stored.expiresAt.isBefore(OffsetDateTime.now())) {
            refreshTokenRepository.delete(stored)
            throw UnauthorizedException("Refresh token expired")
        }
        refreshTokenRepository.delete(stored)
        val user = stored.user
        val rawRefresh = UUID.randomUUID().toString()
        saveRefreshToken(user, rawRefresh)
        return TokenResponse(
            accessToken = jwtProvider.generateAccessToken(user.id, user.email),
            refreshToken = rawRefresh,
        )
    }

    @Transactional
    fun logout(refreshToken: String) {
        val hash = sha256(refreshToken)
        refreshTokenRepository.deleteByTokenHash(hash)
    }

    private fun issueTokens(user: User): AuthResponse {
        val rawRefresh = UUID.randomUUID().toString()
        saveRefreshToken(user, rawRefresh)
        return AuthResponse(
            accessToken = jwtProvider.generateAccessToken(user.id, user.email),
            refreshToken = rawRefresh,
            user = UserDto(user.id, user.name, user.email, user.avatarUrl, user.currencyDefault),
        )
    }

    private fun saveRefreshToken(
        user: User,
        rawToken: String,
    ) {
        val token =
            RefreshToken(
                user = user,
                tokenHash = sha256(rawToken),
                expiresAt = OffsetDateTime.now().plusDays(30),
            )
        refreshTokenRepository.save(token)
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
