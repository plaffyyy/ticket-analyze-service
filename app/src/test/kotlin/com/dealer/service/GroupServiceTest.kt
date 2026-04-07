package com.dealer.service

import com.dealer.domain.dto.CreateGroupRequest
import com.dealer.domain.model.Group
import com.dealer.domain.model.GroupMember
import com.dealer.domain.model.GroupMemberId
import com.dealer.domain.model.MemberRole
import com.dealer.domain.model.Transaction
import com.dealer.domain.model.TransactionStatus
import com.dealer.domain.model.User
import com.dealer.exception.ConflictException
import com.dealer.exception.ForbiddenException
import com.dealer.exception.NotFoundException
import com.dealer.repository.GroupMemberRepository
import com.dealer.repository.GroupRepository
import com.dealer.repository.TransactionRepository
import com.dealer.repository.UserRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.util.Optional
import java.util.UUID

class GroupServiceTest {
    private val groupRepository = mockk<GroupRepository>()
    private val groupMemberRepository = mockk<GroupMemberRepository>(relaxed = true)
    private val userRepository = mockk<UserRepository>()
    private val transactionRepository = mockk<TransactionRepository>()

    private val service =
        GroupService(
            groupRepository,
            groupMemberRepository,
            userRepository,
            transactionRepository,
        )

    @BeforeEach
    fun stubGroupSave() {
        every { groupRepository.save(any()) } answers { firstArg<Group>() }
        every { groupMemberRepository.save(any()) } answers { firstArg<GroupMember>() }
    }

    private fun user(id: UUID = UUID.randomUUID()) = User("N", "e@e.com", "h").apply { this.id = id }

    private fun group(
        owner: User,
        gid: UUID = UUID.randomUUID(),
    ) = Group("G", owner, "INVITE01", "USD").apply { id = gid }

    @Test
    fun `createGroup saves group and owner membership`() {
        val owner = user()
        every { userRepository.findById(owner.id) } returns Optional.of(owner)
        every { userRepository.findAllById(any()) } returns listOf(owner)
        every { groupRepository.existsByInviteCode(any()) } returns false
        every { groupRepository.save(any()) } answers {
            val g = firstArg<Group>()
            g.id = UUID.randomUUID()
            g
        }

        val dto = service.createGroup(owner.id, CreateGroupRequest(" Name ", "usd"))

        assertEquals("Name", dto.name)
        assertEquals("USD", dto.currency)
        assertTrue(dto.members.any { it.userId == owner.id && it.role == "owner" })
        verify { groupMemberRepository.save(any()) }
    }

    @Test
    fun `getGroup throws when not member`() {
        val gid = UUID.randomUUID()
        val g = group(user(), gid)
        every { groupRepository.findById(gid) } returns Optional.of(g)
        every { groupMemberRepository.existsByIdGroupIdAndIdUserId(gid, any()) } returns false

        assertThrows<ForbiddenException> {
            service.getGroup(gid, UUID.randomUUID())
        }
    }

    @Test
    fun `joinGroup throws when already member`() {
        val u = user()
        val g = group(u)
        every { groupRepository.findByInviteCode("CODE") } returns Optional.of(g)
        every { groupMemberRepository.existsByIdGroupIdAndIdUserId(g.id, u.id) } returns true

        assertThrows<ConflictException> {
            service.joinGroup("CODE", u.id)
        }
    }

    @Test
    fun `removeMember throws when owner removes self`() {
        val owner = user()
        val g = group(owner)
        every { groupRepository.findById(g.id) } returns Optional.of(g)
        every { groupMemberRepository.existsByIdGroupIdAndIdUserId(g.id, owner.id) } returns true

        assertThrows<ForbiddenException> {
            service.removeMember(g.id, owner.id, owner.id)
        }
    }

    @Test
    fun `removeMember throws when target not in group`() {
        val owner = user()
        val other = user()
        val g = group(owner)
        every { groupRepository.findById(g.id) } returns Optional.of(g)
        every { groupMemberRepository.existsByIdGroupIdAndIdUserId(g.id, other.id) } returns false

        assertThrows<NotFoundException> {
            service.removeMember(g.id, other.id, owner.id)
        }
    }

    @Test
    fun `getBalance aggregates creditor and debtor`() {
        val u1 = user()
        val u2 = user()
        val gid = UUID.randomUUID()
        val g = group(u1, gid)
        val bill =
            com.dealer.domain.model
                .Bill(group = g, createdBy = u1, title = "B", currency = "USD")
                .apply { id = UUID.randomUUID() }
        val tx =
            Transaction(
                bill = bill,
                debtor = u1,
                creditor = u2,
                amount = BigDecimal("10.00"),
                status = TransactionStatus.PENDING,
            ).apply { id = UUID.randomUUID() }

        every { groupRepository.findById(gid) } returns Optional.of(g)
        every { groupMemberRepository.existsByIdGroupIdAndIdUserId(gid, u1.id) } returns true
        every { groupMemberRepository.findByIdGroupId(gid) } returns
            listOf(
                GroupMember(GroupMemberId(gid, u1.id), MemberRole.OWNER),
                GroupMember(GroupMemberId(gid, u2.id), MemberRole.MEMBER),
            )
        every { transactionRepository.findByGroupId(gid) } returns listOf(tx)
        every { userRepository.findAllById(any<List<UUID>>()) } returns listOf(u1, u2)

        val bal = service.getBalance(gid, u1.id)

        val byUser = bal.balances.associateBy { it.userId }
        assertEquals(BigDecimal("-10.00"), byUser[u1.id]!!.balance)
        assertEquals(BigDecimal("10.00"), byUser[u2.id]!!.balance)
    }
}
