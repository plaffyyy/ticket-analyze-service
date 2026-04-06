package com.dealer.api.controller

import com.dealer.domain.dto.AuthResponse
import com.dealer.domain.dto.LoginRequest
import com.dealer.domain.dto.RefreshRequest
import com.dealer.domain.dto.RegisterRequest
import com.dealer.domain.dto.TokenResponse
import com.dealer.service.AuthService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val authService: AuthService,
) {
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    fun register(
        @Valid @RequestBody request: RegisterRequest,
    ): AuthResponse = authService.register(request)

    @PostMapping("/login")
    fun login(
        @Valid @RequestBody request: LoginRequest,
    ): AuthResponse = authService.login(request)

    @PostMapping("/refresh")
    fun refresh(
        @Valid @RequestBody request: RefreshRequest,
    ): TokenResponse = authService.refresh(request)

    @DeleteMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logout(
        @Valid @RequestBody request: RefreshRequest,
    ) = authService.logout(request.refreshToken)
}
