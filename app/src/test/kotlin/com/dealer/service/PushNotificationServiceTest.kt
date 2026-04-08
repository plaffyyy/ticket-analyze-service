package com.dealer.service

import com.dealer.domain.model.Device
import com.dealer.domain.model.Platform
import com.dealer.domain.model.User
import com.dealer.exception.NotFoundException
import com.dealer.repository.DeviceRepository
import com.dealer.repository.UserRepository
import com.google.firebase.messaging.BatchResponse
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.SendResponse
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.Optional
import java.util.UUID

class PushNotificationServiceTest {
    private val userRepository = mockk<UserRepository>()
    private val deviceRepository = mockk<DeviceRepository>(relaxed = true)
    private val firebaseMessaging = mockk<FirebaseMessaging>()
    private val service = PushNotificationService(userRepository, deviceRepository)

    @AfterEach
    fun tearDown() {
        unmockkStatic(FirebaseMessaging::class)
    }

    private fun user(
        id: UUID = UUID.randomUUID(),
        name: String = "User",
    ) = User(name, "${name.lowercase()}@mail.com", "hash").apply { this.id = id }

    private fun device(
        token: String,
        owner: User,
    ) = Device(user = owner, fcmToken = token, platform = Platform.ANDROID).apply { id = UUID.randomUUID() }

    @Test
    fun `sendUserAddedToGroupNotification returns when no tokens`() {
        val userId = UUID.randomUUID()
        val members = listOf(UUID.randomUUID(), UUID.randomUUID())
        every { deviceRepository.findAllByUserIdIn(members) } returns emptyList()

        service.sendUserAddedToGroupNotification(userId, members, UUID.randomUUID(), "Trip")

        verify { deviceRepository.findAllByUserIdIn(members) }
        verify(exactly = 0) { userRepository.findById(any()) }
    }

    @Test
    fun `sendUserAddedToGroupNotification throws when added user not found`() {
        val userId = UUID.randomUUID()
        val member = user()
        val members = listOf(member.id)
        every { deviceRepository.findAllByUserIdIn(members) } returns listOf(device("t1", member))
        every { userRepository.findById(userId) } returns Optional.empty()

        assertThrows(NotFoundException::class.java) {
            service.sendUserAddedToGroupNotification(userId, members, UUID.randomUUID(), "Trip")
        }
    }

    @Test
    fun `sendUserAddedToGroupNotification sends multicast with payload`() {
        val addedUser = user(name = "Alice")
        val member1 = user()
        val member2 = user()
        val groupId = UUID.randomUUID()
        val members = listOf(member1.id, member2.id)
        val devices = listOf(device("token-1", member1), device("token-2", member2))
        val response = mockk<BatchResponse>()
        val success = mockk<SendResponse>()

        every { deviceRepository.findAllByUserIdIn(members) } returns devices
        every { userRepository.findById(addedUser.id) } returns Optional.of(addedUser)
        every { success.isSuccessful } returns true
        every { response.responses } returns listOf(success, success)

        mockkStatic(FirebaseMessaging::class)
        every { FirebaseMessaging.getInstance() } returns firebaseMessaging
        val msgSlot = slot<com.google.firebase.messaging.MulticastMessage>()
        every { firebaseMessaging.sendEachForMulticast(capture(msgSlot)) } returns response

        service.sendUserAddedToGroupNotification(addedUser.id, members, groupId, "My Group")

        val msg = msgSlot.captured
        val data = getField<Map<String, String>>(msg, "data")
        val notification = getField<com.google.firebase.messaging.Notification>(msg, "notification")
        val title = getField<String>(notification, "title")
        val body = getField<String>(notification, "body")
        val tokens = getField<List<String>>(msg, "tokens")

        assertEquals("USER_JOINED", data["type"])
        assertEquals(groupId.toString(), data["groupId"])
        assertEquals("My Group", data["groupName"])
        assertEquals("User Alice joined group", title)
        assertEquals("Group: My Group", body)
        assertEquals(listOf("token-1", "token-2"), tokens)
        verify(exactly = 0) { deviceRepository.deleteByFcmToken(any()) }
    }

    @Test
    fun `sendUserAddedToGroupNotification removes failed tokens`() {
        val addedUser = user(name = "Bob")
        val member1 = user()
        val member2 = user()
        val members = listOf(member1.id, member2.id)
        val devices = listOf(device("token-ok", member1), device("token-bad", member2))
        val response = mockk<BatchResponse>()
        val ok = mockk<SendResponse>()
        val fail = mockk<SendResponse>()

        every { deviceRepository.findAllByUserIdIn(members) } returns devices
        every { userRepository.findById(addedUser.id) } returns Optional.of(addedUser)
        every { ok.isSuccessful } returns true
        every { fail.isSuccessful } returns false
        every { response.responses } returns listOf(ok, fail)

        mockkStatic(FirebaseMessaging::class)
        every { FirebaseMessaging.getInstance() } returns firebaseMessaging
        every { firebaseMessaging.sendEachForMulticast(any()) } returns response

        service.sendUserAddedToGroupNotification(addedUser.id, members, UUID.randomUUID(), "Trip")

        verify(exactly = 1) { deviceRepository.deleteByFcmToken("token-bad") }
        verify(exactly = 0) { deviceRepository.deleteByFcmToken("token-ok") }
    }

    private fun <T> getField(
        target: Any,
        name: String,
    ): T {
        val field = target.javaClass.getDeclaredField(name)
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return field.get(target) as T
    }
}
