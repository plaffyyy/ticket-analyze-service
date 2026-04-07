package com.dealer.service

import com.dealer.domain.dto.AddBillItemRequest
import com.dealer.domain.dto.BillSplitsRequest
import com.dealer.domain.dto.CreateBillRequest
import com.dealer.domain.model.Bill
import com.dealer.domain.model.BillItem
import com.dealer.domain.model.BillStatus
import com.dealer.domain.model.Group
import com.dealer.domain.model.Transaction
import com.dealer.domain.model.TransactionStatus
import com.dealer.domain.model.User
import com.dealer.exception.ConflictException
import com.dealer.exception.ForbiddenException
import com.dealer.exception.NotFoundException
import com.dealer.repository.BillItemRepository
import com.dealer.repository.BillItemSplitRepository
import com.dealer.repository.BillRepository
import com.dealer.repository.GroupMemberRepository
import com.dealer.repository.GroupRepository
import com.dealer.repository.TransactionRepository
import com.dealer.repository.UserRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.util.Optional
import java.util.UUID

class BillServiceTest {
    private val billRepository = mockk<BillRepository>(relaxed = true)
    private val billItemRepository = mockk<BillItemRepository>(relaxed = true)
    private val billItemSplitRepository = mockk<BillItemSplitRepository>(relaxed = true)
    private val groupRepository = mockk<GroupRepository>()
    private val groupMemberRepository = mockk<GroupMemberRepository>()
    private val userRepository = mockk<UserRepository>()
    private val transactionRepository = mockk<TransactionRepository>(relaxed = true)

    private val service =
        BillService(
            billRepository,
            billItemRepository,
            billItemSplitRepository,
            groupRepository,
            groupMemberRepository,
            userRepository,
            transactionRepository,
        )

    @BeforeEach
    fun stubSaves() {
        every { billRepository.save(any()) } answers { firstArg<Bill>() }
        every { transactionRepository.save(any()) } answers { firstArg<Transaction>() }
    }

    private fun user(id: UUID = UUID.randomUUID()) = User("N", "e@e.com", "h").apply { this.id = id }

    private fun group(
        owner: User,
        gid: UUID = UUID.randomUUID(),
    ) = Group("G", owner, "CODE", "USD").apply { id = gid }

    private fun bill(
        g: Group,
        creator: User,
        bid: UUID = UUID.randomUUID(),
    ) = Bill(group = g, createdBy = creator, title = "T", currency = "USD").apply {
        id = bid
        status = BillStatus.OPEN
    }

    @Test
    fun `createBill throws when group missing`() {
        every { groupRepository.findById(any()) } returns Optional.empty()

        assertThrows<NotFoundException> {
            service.createBill(UUID.randomUUID(), CreateBillRequest(UUID.randomUUID(), "x", "usd"))
        }
    }

    @Test
    fun `createBill throws when requester not member`() {
        val gid = UUID.randomUUID()
        val u = user()
        val g = group(u, gid)
        every { groupRepository.findById(gid) } returns Optional.of(g)
        every { groupMemberRepository.existsByIdGroupIdAndIdUserId(gid, u.id) } returns false

        assertThrows<ForbiddenException> {
            service.createBill(u.id, CreateBillRequest(gid, "title", "usd"))
        }
    }

    @Test
    fun `createBill persists and returns dto`() {
        val creator = user()
        val gid = UUID.randomUUID()
        val g = group(creator, gid)
        every { groupRepository.findById(gid) } returns Optional.of(g)
        every { groupMemberRepository.existsByIdGroupIdAndIdUserId(gid, creator.id) } returns true
        every { userRepository.findById(creator.id) } returns Optional.of(creator)
        every { billRepository.save(any()) } answers {
            val b = firstArg<Bill>()
            b.id = UUID.randomUUID()
            b
        }
        every { billItemRepository.findByBillId(any()) } returns emptyList()

        val dto = service.createBill(creator.id, CreateBillRequest(gid, " title ", "usd"))

        assertEquals("title", dto.title)
        assertEquals("USD", dto.currency)
        verify { billRepository.save(any()) }
    }

    @Test
    fun `settleBill throws when already settled`() {
        val u = user()
        val g = group(u)
        val b = bill(g, u).apply { status = BillStatus.SETTLED }
        every { billRepository.findById(b.id) } returns Optional.of(b)
        every { groupMemberRepository.existsByIdGroupIdAndIdUserId(g.id, u.id) } returns true

        assertThrows<ConflictException> {
            service.settleBill(b.id, u.id)
        }
    }

    @Test
    fun `settleBill marks pending transactions settled`() {
        val u = user()
        val g = group(u)
        val b = bill(g, u)
        val tx =
            Transaction(
                bill = b,
                debtor = u,
                creditor = user(),
                amount = BigDecimal.ONE,
                status = TransactionStatus.PENDING,
            ).apply { id = UUID.randomUUID() }

        every { billRepository.findById(b.id) } returns Optional.of(b)
        every { groupMemberRepository.existsByIdGroupIdAndIdUserId(g.id, u.id) } returns true
        every { transactionRepository.findByBillId(b.id) } returns listOf(tx)
        every { billItemRepository.findByBillId(b.id) } returns emptyList()

        service.settleBill(b.id, u.id)

        assertEquals(TransactionStatus.SETTLED, tx.status)
        assertEquals(BillStatus.SETTLED, b.status)
        verify { transactionRepository.save(tx) }
    }

    @Test
    fun `addItem throws when bill not open`() {
        val u = user()
        val g = group(u)
        val b = bill(g, u).apply { status = BillStatus.SETTLED }
        every { billRepository.findById(b.id) } returns Optional.of(b)
        every { groupMemberRepository.existsByIdGroupIdAndIdUserId(g.id, u.id) } returns true

        assertThrows<ConflictException> {
            service.addItem(
                b.id,
                u.id,
                AddBillItemRequest("coffee", BigDecimal("2.00"), 1),
            )
        }
    }

    @Test
    fun `addItem recalculates total`() {
        val u = user()
        val g = group(u)
        val b = bill(g, u)
        every { billRepository.findById(b.id) } returns Optional.of(b)
        every { groupMemberRepository.existsByIdGroupIdAndIdUserId(g.id, u.id) } returns true
        every { billItemRepository.save(any()) } answers {
            val it = firstArg<BillItem>()
            it.id = UUID.randomUUID()
            it
        }
        every { billItemRepository.findByBillId(b.id) } returns
            listOf(
                BillItem(bill = b, name = "x", price = BigDecimal("3.00"), quantity = 2).apply { id = UUID.randomUUID() },
            )

        service.addItem(b.id, u.id, AddBillItemRequest("x", BigDecimal("3.00"), 2))

        assertEquals(BigDecimal("6.00"), b.total)
        verify { billRepository.save(b) }
    }

    @Test
    fun `setSplits throws when item missing`() {
        val u = user()
        val g = group(u)
        val b = bill(g, u)
        val missingItemId = UUID.randomUUID()
        every { billRepository.findById(b.id) } returns Optional.of(b)
        every { groupMemberRepository.existsByIdGroupIdAndIdUserId(g.id, u.id) } returns true
        every { billItemSplitRepository.deleteByItemId(any()) } returns Unit
        every { billItemRepository.findAllById(any()) } returns emptyList()
        every { userRepository.findAllById(any()) } returns listOf(u)

        assertThrows<NotFoundException> {
            service.setSplits(
                b.id,
                u.id,
                BillSplitsRequest(
                    splits =
                        listOf(
                            BillSplitsRequest.SplitEntry(missingItemId, u.id, BigDecimal.ONE),
                        ),
                ),
            )
        }
    }
}
