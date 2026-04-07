package com.dealer.service

import com.dealer.domain.dto.FcmTokenRequest
import com.dealer.domain.dto.UpdateProfileRequest
import com.dealer.domain.model.Platform
import com.dealer.domain.model.User
import com.dealer.exception.NotFoundException
import com.dealer.repository.DeviceRepository
import com.dealer.repository.UserRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.Optional
import java.util.UUID

class UserServiceTest {
    private val userRepository = mockk<UserRepository>()
    private val deviceRepository = mockk<DeviceRepository>(relaxed = true)
    private val service = UserService(userRepository, deviceRepository)

    private fun user(id: UUID = UUID.randomUUID()) = User("N", "e@e.com", "h").apply { this.id = id }

    @Test
    fun `getCurrentUser throws when missing`() {
        every { userRepository.findById(any()) } returns Optional.empty()

        assertThrows<NotFoundException> {
            service.getCurrentUser(UUID.randomUUID())
        }
    }

    @Test
    fun `getCurrentUser returns dto`() {
        val u = user()
        every { userRepository.findById(u.id) } returns Optional.of(u)

        val dto = service.getCurrentUser(u.id)

        assertEquals(u.id, dto.id)
        assertEquals(u.name, dto.name)
    }

    @Test
    fun `updateProfile throws when user missing`() {
        every { userRepository.findById(any()) } returns Optional.empty()

        assertThrows<NotFoundException> {
            service.updateProfile(UUID.randomUUID(), UpdateProfileRequest(name = "X"))
        }
    }

    @Test
    fun `updateProfile applies fields`() {
        val u = user()
        every { userRepository.findById(u.id) } returns Optional.of(u)
        every { userRepository.save(any()) } answers { firstArg() }

        val dto = service.updateProfile(u.id, UpdateProfileRequest(name = " New ", currencyDefault = "eur"))

        assertEquals("New", dto.name)
        assertEquals("eur", dto.currencyDefault)
    }

    @Test
    fun `saveFcmToken throws when user missing`() {
        every { userRepository.findById(any()) } returns Optional.empty()

        assertThrows<NotFoundException> {
            service.saveFcmToken(UUID.randomUUID(), FcmTokenRequest("tok", "ios"))
        }
    }

    @Test
    fun `saveFcmToken saves when token new`() {
        val u = user()
        every { userRepository.findById(u.id) } returns Optional.of(u)
        every { deviceRepository.existsByUserIdAndFcmToken(u.id, "tok") } returns false
        every { deviceRepository.save(any()) } answers { firstArg() }

        service.saveFcmToken(u.id, FcmTokenRequest("tok", "android"))

        verify {
            deviceRepository.save(
                match {
                    it.fcmToken == "tok" && it.platform == Platform.ANDROID && it.user.id == u.id
                },
            )
        }
    }

    @Test
    fun `saveFcmToken skips when token exists`() {
        val u = user()
        every { userRepository.findById(u.id) } returns Optional.of(u)
        every { deviceRepository.existsByUserIdAndFcmToken(u.id, "tok") } returns true

        service.saveFcmToken(u.id, FcmTokenRequest("tok", "ios"))

        verify(exactly = 0) { deviceRepository.save(any()) }
    }

    @Test
    fun `removeFcmToken delegates`() {
        val uid = UUID.randomUUID()
        every { deviceRepository.deleteByUserIdAndFcmToken(uid, "t") } returns Unit

        service.removeFcmToken(uid, "t")

        verify { deviceRepository.deleteByUserIdAndFcmToken(uid, "t") }
    }
}
