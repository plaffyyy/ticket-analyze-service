package com.dealer.listener

import com.dealer.domain.model.GroupMemberId
import com.dealer.domain.model.UserAddedToGroupEvent
import com.dealer.service.PushNotificationService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.util.UUID

class GroupNotificationListenerTest {
    private val pushService = mockk<PushNotificationService>(relaxed = true)
    private val listener = GroupNotificationListener(pushService)

    @Test
    fun `onUserAdded forwards mapped event payload to push service`() {
        val groupId = UUID.randomUUID()
        val addedUserId = UUID.randomUUID()
        val member1 = UUID.randomUUID()
        val member2 = UUID.randomUUID()
        val event =
            UserAddedToGroupEvent(
                groupId = groupId,
                groupName = "Trip",
                addedUserId = addedUserId,
                membersIds =
                    listOf(
                        GroupMemberId(groupId, member1),
                        GroupMemberId(groupId, member2),
                    ),
            )
        every { pushService.sendUserAddedToGroupNotification(any(), any(), any(), any()) } returns Unit

        listener.onUserAdded(event)

        verify {
            pushService.sendUserAddedToGroupNotification(
                userId = addedUserId,
                membersIds = listOf(member1, member2),
                groupId = groupId,
                groupName = "Trip",
            )
        }
    }
}
