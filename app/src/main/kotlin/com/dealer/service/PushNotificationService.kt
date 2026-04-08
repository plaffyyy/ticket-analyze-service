package com.dealer.service

import com.dealer.exception.NotFoundException
import com.dealer.repository.DeviceRepository
import com.dealer.repository.UserRepository
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.MulticastMessage
import com.google.firebase.messaging.Notification
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class PushNotificationService(
    private val userRepository: UserRepository,
    private val deviceRepository: DeviceRepository,
) {
    private val logger = LoggerFactory.getLogger(PushNotificationService::class.java)

    fun sendUserAddedToGroupNotification(
        userId: UUID,
        membersIds: List<UUID>,
        groupId: UUID,
        groupName: String,
    ) {
        val tokens =
            deviceRepository
                .findAllByUserIdIn(membersIds)
                .map { it.fcmToken }

        if (tokens.isEmpty()) return

        if (tokens.size != membersIds.size) {
            logger.warn("FCM tokens not found. Members: ${membersIds.size}, Tokens: ${tokens.size}")
        }

        val user = userRepository.findById(userId).orElseThrow { NotFoundException("User not found") }

        val message =
            MulticastMessage
                .builder()
                .putData("type", "USER_JOINED")
                .putData("groupId", groupId.toString())
                .putData("groupName", groupName)
                .setNotification(
                    Notification
                        .builder()
                        .setTitle("User ${user.name} joined group")
                        .setBody("Group: $groupName")
                        .build(),
                ).addAllTokens(tokens)
                .build()

        val response = FirebaseMessaging.getInstance().sendEachForMulticast(message)

        response.responses.forEachIndexed { index, sendResponse ->
            if (!sendResponse.isSuccessful) {
                val failedToken = tokens[index]
                deviceRepository.deleteByFcmToken(failedToken)
                logger.warn("FCM tokens not found.")
                // TODO: add observability here
            }
        }
    }
}
