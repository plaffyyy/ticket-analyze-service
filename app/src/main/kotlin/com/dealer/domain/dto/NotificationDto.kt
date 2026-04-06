package com.dealer.domain.dto

import java.time.OffsetDateTime
import java.util.UUID

data class NotificationDto(
    val id: UUID,
    val type: String,
    val title: String,
    val body: String,
    val payloadJson: String?,
    val isRead: Boolean,
    val createdAt: OffsetDateTime,
)
