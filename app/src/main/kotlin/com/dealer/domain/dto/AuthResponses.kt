package com.dealer.domain.dto

import java.util.UUID

data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val user: UserDto,
)

data class TokenResponse(
    val accessToken: String,
    val refreshToken: String,
)

data class UserDto(
    val id: UUID,
    val name: String,
    val email: String,
    val avatarUrl: String?,
    val currencyDefault: String,
)
