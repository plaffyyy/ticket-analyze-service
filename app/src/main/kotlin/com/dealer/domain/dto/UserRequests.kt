package com.dealer.domain.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class UpdateProfileRequest(
    @field:Size(max = 100)
    val name: String? = null,
    @field:Size(max = 3)
    val currencyDefault: String? = null,
)

data class FcmTokenRequest(
    @field:NotBlank
    val fcmToken: String,
    @field:NotBlank
    @field:Pattern(regexp = "ios|android", message = "must be 'ios' or 'android'")
    val platform: String,
)
