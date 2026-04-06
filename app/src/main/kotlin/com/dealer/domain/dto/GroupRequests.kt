package com.dealer.domain.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CreateGroupRequest(
    @field:NotBlank
    @field:Size(max = 100)
    val name: String,
    @field:Size(max = 3)
    val currency: String = "USD",
)

data class UpdateGroupRequest(
    @field:Size(max = 100)
    val name: String? = null,
    @field:Size(max = 3)
    val currency: String? = null,
)
