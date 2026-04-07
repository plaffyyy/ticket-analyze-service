package com.dealer.service

import com.dealer.domain.dto.FcmTokenRequest
import com.dealer.domain.dto.UpdateProfileRequest
import com.dealer.domain.dto.UserDto
import com.dealer.domain.model.Device
import com.dealer.domain.model.Platform
import com.dealer.exception.NotFoundException
import com.dealer.repository.DeviceRepository
import com.dealer.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class UserService(
    private val userRepository: UserRepository,
    private val deviceRepository: DeviceRepository,
) {
    fun getCurrentUser(userId: UUID): UserDto {
        val user =
            userRepository
                .findById(userId)
                .orElseThrow { NotFoundException("User not found") }
        return user.toDto()
    }

    @Transactional
    fun updateProfile(
        userId: UUID,
        request: UpdateProfileRequest,
    ): UserDto {
        val user =
            userRepository
                .findById(userId)
                .orElseThrow { NotFoundException("User not found") }
        request.name?.trim()?.let { user.name = it }
        request.currencyDefault?.trim()?.let { user.currencyDefault = it }
        return userRepository.save(user).toDto()
    }

    @Transactional
    fun saveFcmToken(
        userId: UUID,
        request: FcmTokenRequest,
    ) {
        val user =
            userRepository
                .findById(userId)
                .orElseThrow { NotFoundException("User not found") }
        if (!deviceRepository.existsByUserIdAndFcmToken(userId, request.fcmToken)) {
            val platform = Platform.valueOf(request.platform.uppercase())
            deviceRepository.save(Device(user = user, fcmToken = request.fcmToken, platform = platform))
        }
    }

    @Transactional
    fun removeFcmToken(
        userId: UUID,
        fcmToken: String,
    ) {
        deviceRepository.deleteByUserIdAndFcmToken(userId, fcmToken)
    }
}

fun com.dealer.domain.model.User.toDto() =
    UserDto(
        id = id,
        name = name,
        email = email,
        avatarUrl = avatarUrl,
        currencyDefault = currencyDefault,
    )
