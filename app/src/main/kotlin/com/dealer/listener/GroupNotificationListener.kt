package com.dealer.listener

import com.dealer.domain.model.UserAddedToGroupEvent
import com.dealer.service.PushNotificationService
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class GroupNotificationListener(
    private val pushService: PushNotificationService,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onUserAdded(event: UserAddedToGroupEvent) {
        pushService.sendUserAddedToGroupNotification(
            userId = event.addedUserId,
            membersIds = event.membersIds.map { it.userId },
            groupId = event.groupId,
            groupName = event.groupName,
        )
    }
}
