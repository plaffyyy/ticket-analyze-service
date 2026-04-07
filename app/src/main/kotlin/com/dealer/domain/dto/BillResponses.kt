package com.dealer.domain.dto

import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

data class BillDto(
    val id: UUID,
    val groupId: UUID,
    val title: String,
    val total: BigDecimal,
    val currency: String,
    val status: String,
    val receiptUrl: String?,
    val spunWinnerId: UUID?,
    val createdAt: OffsetDateTime,
    val items: List<BillItemDto>,
)

data class BillItemDto(
    val id: UUID,
    val name: String,
    val price: BigDecimal,
    val quantity: Int,
    val splits: List<SplitDto>,
)

data class SplitDto(
    val id: UUID,
    val userId: UUID,
    val shareAmount: BigDecimal,
)

data class OcrStatusResponse(
    val jobId: String,
    val status: String,
    val billId: UUID? = null,
)

data class SpinResponse(
    val winnerId: UUID,
    val winnerName: String,
)
