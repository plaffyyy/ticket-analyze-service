package com.dealer.repository

import com.dealer.domain.model.Device
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface DeviceRepository : JpaRepository<Device, UUID> {
    fun findByUserId(userId: UUID): List<Device>

    fun deleteByUserIdAndFcmToken(
        userId: UUID,
        fcmToken: String,
    )

    fun existsByUserIdAndFcmToken(
        userId: UUID,
        fcmToken: String,
    ): Boolean
}
