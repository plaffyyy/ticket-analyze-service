package com.dealer.service

import com.dealer.domain.dto.AddBillItemRequest
import com.dealer.domain.dto.BillSplitsRequest
import com.dealer.domain.dto.CreateBillRequest
import com.dealer.domain.model.Bill
import com.dealer.domain.model.BillItem
import com.dealer.domain.model.BillItemSplit
import com.dealer.domain.model.BillStatus
import com.dealer.domain.model.Group
import com.dealer.domain.model.GroupMember
import com.dealer.domain.model.GroupMemberId
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
import com.dealer.support.bill.BillViewFactory
import com.dealer.support.cache.CacheInvalidator
import com.dealer.support.cache.CacheSupport
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
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
    private val cacheManager = ConcurrentMapCacheManager()
    private val cacheSupport = CacheSupport(cacheManager)
    private val cacheInvalidator = CacheInvalidator(cacheSupport, groupMemberRepository)
    private val billViewFactory = BillViewFactory(billRepository, billItemRepository, billItemSplitRepository)

    private val service =
        BillService(
            billRepository,
            billItemRepository,
            billItemSplitRepository,
            groupRepository,
            groupMemberRepository,
            userRepository,
            transactionRepository,
            cacheSupport,
            cacheInvalidator,
            billViewFactory,
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

    private fun member(
        groupId: UUID,
        userId: UUID,
    ) = GroupMember(GroupMemberId(groupId, userId))

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
    fun `getGroupBills builds dto via batch queries`() {
        val requester = user()
        val g = group(requester)
        val b = bill(g, requester)
        val item = BillItem(bill = b, name = "coffee", price = BigDecimal("2.50"), quantity = 2).apply { id = UUID.randomUUID() }
        val split = BillItemSplit(item = item, user = requester, shareAmount = BigDecimal("5.00")).apply { id = UUID.randomUUID() }

        every { groupMemberRepository.existsByIdGroupIdAndIdUserId(g.id, requester.id) } returns true
        every { billRepository.findByGroupId(g.id) } returns listOf(b)
        every { billItemRepository.findByBillIdIn(listOf(b.id)) } returns listOf(item)
        every { billItemSplitRepository.findByItemIdIn(listOf(item.id)) } returns listOf(split)

        val result = service.getGroupBills(g.id, requester.id)

        assertEquals(1, result.size)
        assertEquals(1, result.first().items.size)
        assertEquals(
            1,
            result
                .first()
                .items
                .first()
                .splits.size,
        )
        verify(exactly = 0) { billItemRepository.findByBillId(any()) }
    }

    @Test
    fun `getBill throws when requester not member`() {
        val billId = UUID.randomUUID()
        val groupId = UUID.randomUUID()
        val requesterId = UUID.randomUUID()
        every { billRepository.findGroupIdByBillId(billId) } returns groupId
        every { groupMemberRepository.existsByIdGroupIdAndIdUserId(groupId, requesterId) } returns false

        assertThrows<ForbiddenException> {
            service.getBill(billId, requesterId)
        }
    }

    @Test
    fun `updateBill trims title`() {
        val requester = user()
        val g = group(requester)
        val b = bill(g, requester)
        every { billRepository.findById(b.id) } returns Optional.of(b)
        every { groupMemberRepository.existsByIdGroupIdAndIdUserId(g.id, requester.id) } returns true
        every { billItemRepository.findByBillId(b.id) } returns emptyList()

        val result =
            service.updateBill(
                b.id,
                requester.id,
                com.dealer.domain.dto
                    .UpdateBillRequest(" Dinner Updated "),
            )

        assertEquals("Dinner Updated", result.title)
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
    fun `settleBill throws when bill is still open`() {
        val u = user()
        val g = group(u)
        val b = bill(g, u)

        every { billRepository.findById(b.id) } returns Optional.of(b)
        every { groupMemberRepository.existsByIdGroupIdAndIdUserId(g.id, u.id) } returns true

        assertThrows<ConflictException> {
            service.settleBill(b.id, u.id)
        }
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
    fun `updateItem returns existing splits`() {
        val requester = user()
        val splitUser = user()
        val g = group(requester)
        val b = bill(g, requester)
        val item = BillItem(bill = b, name = "old", price = BigDecimal.ONE, quantity = 1).apply { id = UUID.randomUUID() }
        val split = BillItemSplit(item = item, user = splitUser, shareAmount = BigDecimal("2.50")).apply { id = UUID.randomUUID() }

        every { billRepository.findById(b.id) } returns Optional.of(b)
        every { groupMemberRepository.existsByIdGroupIdAndIdUserId(g.id, requester.id) } returns true
        every { groupMemberRepository.findByIdGroupId(g.id) } returns listOf(member(g.id, requester.id), member(g.id, splitUser.id))
        every { billItemRepository.findById(item.id) } returns Optional.of(item)
        every { billItemRepository.findByBillId(b.id) } returns listOf(item)
        every { billItemSplitRepository.findByItemId(item.id) } returns listOf(split)
        every { billItemRepository.save(any()) } answers { firstArg() }

        val result = service.updateItem(b.id, item.id, requester.id, AddBillItemRequest("new", BigDecimal("2.50"), 1))

        assertEquals(1, result.splits.size)
        assertEquals(splitUser.id, result.splits.first().userId)
    }

    @Test
    fun `deleteItem removes splits before deleting item`() {
        val requester = user()
        val g = group(requester)
        val b = bill(g, requester)
        val itemId = UUID.randomUUID()
        every { billRepository.findById(b.id) } returns Optional.of(b)
        every { groupMemberRepository.existsByIdGroupIdAndIdUserId(g.id, requester.id) } returns true
        every { billItemRepository.findByBillId(b.id) } returns emptyList()
        every { billItemSplitRepository.deleteByItemId(itemId) } returns Unit
        every { billItemRepository.deleteById(itemId) } returns Unit

        service.deleteItem(b.id, itemId, requester.id)

        verify { billItemSplitRepository.deleteByItemId(itemId) }
        verify { billItemRepository.deleteById(itemId) }
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
        every { groupMemberRepository.findByIdGroupId(g.id) } returns listOf(member(g.id, u.id))

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

    @Test
    fun `setSplits throws when user missing`() {
        val requester = user()
        val g = group(requester)
        val b = bill(g, requester)
        val item = BillItem(bill = b, name = "pizza", price = BigDecimal("10.00"), quantity = 1).apply { id = UUID.randomUUID() }
        val missingUserId = UUID.randomUUID()

        every { billRepository.findById(b.id) } returns Optional.of(b)
        every { groupMemberRepository.existsByIdGroupIdAndIdUserId(g.id, requester.id) } returns true
        every { billItemSplitRepository.deleteByItemId(item.id) } returns Unit
        every { billItemRepository.findAllById(listOf(item.id)) } returns listOf(item)
        every { userRepository.findAllById(listOf(missingUserId)) } returns emptyList()
        every { groupMemberRepository.findByIdGroupId(g.id) } returns listOf(member(g.id, requester.id))

        assertThrows<NotFoundException> {
            service.setSplits(
                b.id,
                requester.id,
                BillSplitsRequest(listOf(BillSplitsRequest.SplitEntry(item.id, missingUserId, BigDecimal.ONE))),
            )
        }
    }

    @Test
    fun `setSplits replaces existing splits and saves all provided shares`() {
        val requester = user()
        val debtor = user()
        val creditor = user()
        val g = group(requester)
        val b = bill(g, requester)
        val item = BillItem(bill = b, name = "pizza", price = BigDecimal("12.00"), quantity = 1).apply { id = UUID.randomUUID() }

        every { billRepository.findById(b.id) } returns Optional.of(b)
        every { groupMemberRepository.existsByIdGroupIdAndIdUserId(g.id, requester.id) } returns true
        every { billItemSplitRepository.deleteByItemId(item.id) } returns Unit
        every { billItemRepository.findAllById(listOf(item.id)) } returns listOf(item)
        every { userRepository.findAllById(any<List<UUID>>()) } returns listOf(debtor, creditor)
        every { groupMemberRepository.findByIdGroupId(g.id) } returns
            listOf(member(g.id, requester.id), member(g.id, debtor.id), member(g.id, creditor.id))
        every { billItemSplitRepository.save(any()) } answers { firstArg() }

        service.setSplits(
            b.id,
            requester.id,
            BillSplitsRequest(
                listOf(
                    BillSplitsRequest.SplitEntry(item.id, debtor.id, BigDecimal("5.00")),
                    BillSplitsRequest.SplitEntry(item.id, creditor.id, BigDecimal("7.00")),
                ),
            ),
        )

        verify(exactly = 1) { billItemSplitRepository.deleteByItemId(item.id) }
        verify {
            billItemSplitRepository.save(match { it.user.id == debtor.id && it.shareAmount == BigDecimal("5.00") })
            billItemSplitRepository.save(match { it.user.id == creditor.id && it.shareAmount == BigDecimal("7.00") })
        }
    }

    @Test
    fun `setSplits throws when bill is settled`() {
        val requester = user()
        val g = group(requester)
        val b = bill(g, requester).apply { status = BillStatus.SETTLED }
        every { billRepository.findById(b.id) } returns Optional.of(b)
        every { groupMemberRepository.existsByIdGroupIdAndIdUserId(g.id, requester.id) } returns true

        assertThrows<ConflictException> {
            service.setSplits(
                b.id,
                requester.id,
                BillSplitsRequest(emptyList()),
            )
        }
    }

    @Test
    fun `spinWinner stores selected user`() {
        val requester = user()
        val other = user()
        val g = group(requester)
        val b = bill(g, requester)
        every { billRepository.findById(b.id) } returns Optional.of(b)
        every { groupMemberRepository.existsByIdGroupIdAndIdUserId(g.id, requester.id) } returns true
        every { groupMemberRepository.findByIdGroupId(g.id) } returns listOf(member(g.id, requester.id), member(g.id, other.id))
        every { userRepository.findAllById(any<List<UUID>>()) } returns listOf(requester, other)

        val spin = service.spinWinner(b.id, requester.id)

        assertEquals(b.spunWinner?.id, spin.winnerId)
        verify { billRepository.save(b) }
    }

    @Test
    fun `markPaidByWinner creates pending reimbursements and updates status`() {
        val winner = user()
        val debtor = user()
        val g = group(winner)
        val b = bill(g, winner).apply { spunWinner = winner }
        val item = BillItem(bill = b, name = "pizza", price = BigDecimal("12.00"), quantity = 1).apply { id = UUID.randomUUID() }
        val winnerSplit = BillItemSplit(item = item, user = winner, shareAmount = BigDecimal("2.00")).apply { id = UUID.randomUUID() }
        val debtorSplit = BillItemSplit(item = item, user = debtor, shareAmount = BigDecimal("10.00")).apply { id = UUID.randomUUID() }

        every { billRepository.findById(b.id) } returns Optional.of(b)
        every { groupMemberRepository.existsByIdGroupIdAndIdUserId(g.id, winner.id) } returns true
        every { groupMemberRepository.findByIdGroupId(g.id) } returns listOf(member(g.id, winner.id), member(g.id, debtor.id))
        every { billItemRepository.findByBillId(b.id) } returns listOf(item)
        every { billItemSplitRepository.findByItemIdIn(listOf(item.id)) } returns listOf(winnerSplit, debtorSplit)
        every { transactionRepository.existsByBillIdAndStatus(b.id, TransactionStatus.PENDING) } returns true
        every { userRepository.findById(debtor.id) } returns Optional.of(debtor)
        every { billItemRepository.findByBillId(b.id) } returns listOf(item)

        service.markPaidByWinner(b.id, winner.id)

        assertEquals(BillStatus.PAID_BY_WINNER, b.status)
        verify { transactionRepository.save(match { it.debtor.id == debtor.id && it.creditor.id == winner.id }) }
    }

    @Test
    fun `settleTransaction by debtor finalizes bill when last pending is paid`() {
        val winner = user()
        val debtor = user()
        val g = group(winner)
        val b = bill(g, winner).apply { status = BillStatus.PAID_BY_WINNER }
        val tx =
            Transaction(
                bill = b,
                debtor = debtor,
                creditor = winner,
                amount = BigDecimal("10.00"),
                status = TransactionStatus.PENDING,
            ).apply { id = UUID.randomUUID() }

        every { billRepository.findById(b.id) } returns Optional.of(b)
        every { groupMemberRepository.existsByIdGroupIdAndIdUserId(g.id, debtor.id) } returns true
        every { transactionRepository.findByIdAndBillId(tx.id, b.id) } returns tx
        every { transactionRepository.existsByBillIdAndStatus(b.id, TransactionStatus.PENDING) } returns false
        every { billItemRepository.findByBillId(b.id) } returns emptyList()

        service.settleTransaction(b.id, tx.id, debtor.id)

        assertEquals(TransactionStatus.SETTLED, tx.status)
        assertEquals(BillStatus.SETTLED, b.status)
    }
}
