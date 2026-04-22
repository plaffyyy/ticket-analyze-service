package com.dealer.integration

import com.dealer.api.controller.AuthController
import com.dealer.api.controller.TestProtectedController
import com.dealer.config.CacheProperties
import com.dealer.config.RateLimitProperties
import com.dealer.config.SecurityConfig
import com.dealer.domain.dto.AuthResponse
import com.dealer.domain.dto.UserDto
import com.dealer.security.JwtAuthenticationFilter
import com.dealer.security.JwtProvider
import com.dealer.security.RateLimitFilter
import com.dealer.security.RateLimitKeyResolver
import com.dealer.service.AuthService
import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

@WebMvcTest(controllers = [AuthController::class, TestProtectedController::class])
@Import(SecurityConfig::class, JwtAuthenticationFilter::class, RateLimitFilter::class, RateLimitKeyResolver::class)
@EnableConfigurationProperties(value = [RateLimitProperties::class, CacheProperties::class])
@TestPropertySource(
    properties = [
        "app.rate-limit.enabled=true",
        "app.rate-limit.auth.capacity=2",
        "app.rate-limit.auth.refill-tokens=2",
        "app.rate-limit.auth.refill-period=PT1H",
        "app.rate-limit.private-api.capacity=2",
        "app.rate-limit.private-api.refill-tokens=2",
        "app.rate-limit.private-api.refill-period=PT1H",
        "app.rate-limit.public-api.capacity=50",
        "app.rate-limit.public-api.refill-tokens=50",
        "app.rate-limit.public-api.refill-period=PT1H",
    ],
)
class RateLimitIntegrationTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockkBean
    private lateinit var authService: AuthService

    @MockkBean
    private lateinit var jwtProvider: JwtProvider

    @Test
    fun `limit auth endpoint by ip`() {
        every { authService.login(any()) } returns authResponse()

        val request = mapOf("email" to "user@test.com", "password" to "password123")
        repeat(2) {
            mockMvc
                .perform(
                    post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)),
                ).andExpect(status().isOk)
        }

        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isTooManyRequests)
            .andExpect(jsonPath("$.error").value("Too Many Requests"))
    }

    @Test
    fun `limit private endpoint by authenticated user`() {
        repeat(2) {
            mockMvc
                .perform(get("/api/v1/test/protected").with(user("user-1")))
                .andExpect(status().isOk)
        }

        mockMvc
            .perform(get("/api/v1/test/protected").with(user("user-1")))
            .andExpect(status().isTooManyRequests)
    }

    private fun authResponse(): AuthResponse =
        AuthResponse(
            accessToken = "access",
            refreshToken = "refresh",
            user =
                UserDto(
                    id = UUID.fromString("11111111-1111-1111-1111-111111111111"),
                    name = "test",
                    email = "user@test.com",
                    avatarUrl = null,
                    currencyDefault = "USD",
                    transferComment = null,
                ),
        )
}
