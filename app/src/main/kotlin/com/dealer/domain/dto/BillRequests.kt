package com.dealer.domain.dto

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.util.UUID

data class CreateBillRequest(
    @field:NotNull
    val groupId: UUID,
    @field:NotBlank
    @field:Size(max = 255)
    val title: String,
    @field:Size(max = 3)
    val currency: String = "USD",
)

data class UpdateBillRequest(
    @field:Size(max = 255)
    val title: String? = null,
)

data class AddBillItemRequest(
    @field:NotBlank
    @field:Size(max = 255)
    val name: String,
    @field:NotNull
    @field:DecimalMin("0.01")
    val price: BigDecimal,
    @field:Min(1)
    val quantity: Int = 1,
)

data class BillSplitsRequest(
    @field:NotNull
    val splits: List<SplitEntry>,
) {
    data class SplitEntry(
        val itemId: UUID,
        val userId: UUID,
        val shareAmount: BigDecimal,
    )
}
