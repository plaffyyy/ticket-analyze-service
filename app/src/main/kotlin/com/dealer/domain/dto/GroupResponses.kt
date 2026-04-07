package com.dealer.domain.dto

import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

data class GroupDto(
    val id: UUID,
    val name: String,
    val ownerId: UUID,
    val inviteCode: String,
    val currency: String,
    val createdAt: OffsetDateTime,
    val members: List<MemberDto>,
)

data class MemberDto(
    val userId: UUID,
    val name: String,
    val role: String,
)

data class BalanceResponse(
    val groupId: UUID,
    val balances: List<BalanceEntry>,
)

data class BalanceEntry(
    val userId: UUID,
    val name: String,
    val balance: BigDecimal,
)

data class InviteResponse(
    val inviteCode: String,
    val deepLink: String,
)
