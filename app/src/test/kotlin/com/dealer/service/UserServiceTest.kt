package com.dealer.service

import com.dealer.domain.dto.FcmTokenRequest
import com.dealer.domain.dto.UpdateProfileRequest
import com.dealer.domain.model.Platform
import com.dealer.domain.model.User
import com.dealer.exception.NotFoundException
import com.dealer.repository.DeviceRepository
import com.dealer.repository.GroupMemberRepository
import com.dealer.repository.UserRepository
import com.dealer.support.cache.CacheInvalidator
import com.dealer.support.cache.CacheSupport
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import java.util.Optional
import java.util.UUID

class UserServiceTest {
    private val userRepository = mockk<UserRepository>()
    private val deviceRepository = mockk<DeviceRepository>(relaxed = true)
    private val groupMemberRepository = mockk<GroupMemberRepository>(relaxed = true)
    private val cacheManager = ConcurrentMapCacheManager()
    private val cacheSupport = CacheSupport(cacheManager)
    private val cacheInvalidator = CacheInvalidator(cacheSupport, groupMemberRepository)
    private val service = UserService(userRepository, deviceRepository, cacheSupport, cacheInvalidator)

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
    fun `getCurrentUser uses cache on repeated calls`() {
        val u = user()
        every { userRepository.findById(u.id) } returns Optional.of(u)

        service.getCurrentUser(u.id)
        service.getCurrentUser(u.id)

        verify(exactly = 1) { userRepository.findById(u.id) }
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
        every { groupMemberRepository.findByIdUserId(u.id) } returns emptyList()

        val dto = service.updateProfile(u.id, UpdateProfileRequest(name = " New ", currencyDefault = "eur"))

        assertEquals("New", dto.name)
        assertEquals("eur", dto.currencyDefault)
    }

    @Test
    fun `updateProfile evicts cached current user`() {
        val u = user()
        every { userRepository.findById(u.id) } returns Optional.of(u)
        every { userRepository.save(any()) } answers { firstArg() }
        every { groupMemberRepository.findByIdUserId(u.id) } returns emptyList()

        val cached = service.getCurrentUser(u.id)
        service.updateProfile(u.id, UpdateProfileRequest(name = "Updated"))
        val refreshed = service.getCurrentUser(u.id)

        assertEquals("N", cached.name)
        assertEquals("Updated", refreshed.name)
        verify(exactly = 3) { userRepository.findById(u.id) }
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
