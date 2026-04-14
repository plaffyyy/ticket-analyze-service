package com.dealer.service

import com.dealer.config.CacheNames
import com.dealer.domain.dto.AddBillItemRequest
import com.dealer.domain.dto.BillDto
import com.dealer.domain.dto.BillItemDto
import com.dealer.domain.dto.BillSplitsRequest
import com.dealer.domain.dto.CreateBillRequest
import com.dealer.domain.dto.SpinResponse
import com.dealer.domain.dto.UpdateBillRequest
import com.dealer.domain.model.Bill
import com.dealer.domain.model.BillItem
import com.dealer.domain.model.BillItemSplit
import com.dealer.domain.model.BillStatus
import com.dealer.domain.model.GroupMember
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
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.security.SecureRandom
import java.time.OffsetDateTime
import java.util.UUID

@Service
class BillService(
    private val billRepository: BillRepository,
    private val billItemRepository: BillItemRepository,
    private val billItemSplitRepository: BillItemSplitRepository,
    private val groupRepository: GroupRepository,
    private val groupMemberRepository: GroupMemberRepository,
    private val userRepository: UserRepository,
    private val transactionRepository: TransactionRepository,
    private val cacheSupport: CacheSupport,
    private val cacheInvalidator: CacheInvalidator,
    private val billViewFactory: BillViewFactory,
) {
    private val secureRandom = SecureRandom()

    // Creates a bill for a group member.
    @Transactional
    fun createBill(
        creatorId: UUID,
        request: CreateBillRequest,
    ): BillDto {
        val group =
            groupRepository
                .findById(request.groupId)
                .orElseThrow { NotFoundException("Group not found") }
        requireMember(request.groupId, creatorId)
        val creator = userRepository.findById(creatorId).orElseThrow { NotFoundException("User not found") }
        val bill =
            Bill(
                group = group,
                createdBy = creator,
                title = request.title.trim(),
                currency = request.currency.trim().uppercase(),
            )
        billRepository.save(bill)
        cacheSupport.evict(CacheNames.GROUP_BILLS, group.id)
        cacheSupport.evict(CacheNames.GROUP_BALANCE, group.id)
        return toDto(bill, emptyList())
    }

    // Returns bill details if requester is a member.
    @Transactional(readOnly = true)
    fun getBill(
        billId: UUID,
        requesterId: UUID,
    ): BillDto {
        val groupId = billRepository.findGroupIdByBillId(billId) ?: throw NotFoundException("Bill not found")
        requireMember(groupId, requesterId)
        return cacheSupport.getOrLoad(CacheNames.BILL, billId) { billViewFactory.buildBill(billId) }
    }

    // Returns all bills for a group.
    @Transactional(readOnly = true)
    fun getGroupBills(
        groupId: UUID,
        requesterId: UUID,
    ): List<BillDto> {
        requireMember(groupId, requesterId)
        return cacheSupport.getOrLoad(CacheNames.GROUP_BILLS, groupId) { billViewFactory.buildGroupBills(groupId) }
    }

    // Updates an open bill.
    @Transactional
    fun updateBill(
        billId: UUID,
        requesterId: UUID,
        request: UpdateBillRequest,
    ): BillDto {
        val bill = getEditableBill(billId, requesterId)
        request.title?.trim()?.let { bill.title = it }
        billRepository.save(bill)
        cacheInvalidator.evictBillViews(billId, bill.group.id)
        return toDto(bill, billItemRepository.findByBillId(billId))
    }

    // Deletes bill for any group member.
    @Transactional
    fun deleteBill(
        billId: UUID,
        requesterId: UUID,
    ) {
        val bill = findBillForMember(billId, requesterId)
        billRepository.delete(bill)
        cacheInvalidator.evictBillViews(billId, bill.group.id)
    }

    // Randomly chooses and stores payer for this bill.
    @Transactional
    fun spinWinner(
        billId: UUID,
        requesterId: UUID,
    ): SpinResponse {
        val bill = getEditableBill(billId, requesterId)
        if (bill.spunWinner != null) throw ConflictException("Winner is already selected")

        val members = groupMemberRepository.findByIdGroupId(bill.group.id)
        if (members.isEmpty()) throw ConflictException("Group has no members")
        val users = userRepository.findAllById(members.map { it.id.userId })
        if (users.isEmpty()) throw ConflictException("Group has no available users for spin")

        val winner = users[secureRandom.nextInt(users.size)]
        bill.spunWinner = winner
        billRepository.save(bill)
        cacheInvalidator.evictBillViews(billId, bill.group.id)
        return SpinResponse(winnerId = winner.id, winnerName = winner.name)
    }

    // Marks that winner paid the check and creates pending reimbursements.
    @Transactional
    fun markPaidByWinner(
        billId: UUID,
        requesterId: UUID,
    ): BillDto {
        val bill = findBillForMember(billId, requesterId)
        requireStatus(bill, BillStatus.OPEN)

        val winner = bill.spunWinner ?: throw ConflictException("Winner is not selected")
        if (winner.id != requesterId) throw ForbiddenException("Only selected winner can confirm payment")

        val items = billItemRepository.findByBillId(billId)
        if (items.isEmpty()) throw ConflictException("Bill has no items")
        val splits = billItemSplitRepository.findByItemIdIn(items.map { it.id })

        val debtByUserId = calculateDebtsByUser(bill, items, splits)
        transactionRepository.deleteByBillId(billId)

        debtByUserId
            .filter { (debtorId, amount) -> debtorId != winner.id && amount > BigDecimal.ZERO }
            .forEach { (debtorId, amount) ->
                val debtor = findUserById(debtorId)
                transactionRepository.save(
                    Transaction(
                        bill = bill,
                        debtor = debtor,
                        creditor = winner,
                        amount = amount,
                        status = TransactionStatus.PENDING,
                    ),
                )
            }

        bill.status =
            if (transactionRepository.existsByBillIdAndStatus(billId, TransactionStatus.PENDING)) {
                BillStatus.PAID_BY_WINNER
            } else {
                BillStatus.SETTLED
            }
        billRepository.save(bill)

        cacheInvalidator.evictBillViews(billId, bill.group.id)
        return toDto(bill, items)
    }

    // Adds one item into an open bill.
    @Transactional
    fun addItem(
        billId: UUID,
        requesterId: UUID,
        request: AddBillItemRequest,
    ): BillItemDto {
        val bill = getEditableBill(billId, requesterId)
        val item = BillItem(bill = bill, name = request.name.trim(), price = request.price, quantity = request.quantity)
        billItemRepository.save(item)
        recalculateTotal(bill)
        cacheInvalidator.evictBillViews(billId, bill.group.id)
        return billViewFactory.toItemDto(item, emptyList())
    }

    // Updates one item inside an open bill.
    @Transactional
    fun updateItem(
        billId: UUID,
        itemId: UUID,
        requesterId: UUID,
        request: AddBillItemRequest,
    ): BillItemDto {
        val bill = getEditableBill(billId, requesterId)
        val item = billItemRepository.findById(itemId).orElseThrow { NotFoundException("Item not found") }

        item.name = request.name.trim()
        item.price = request.price
        item.quantity = request.quantity

        billItemRepository.save(item)
        recalculateTotal(bill)
        cacheInvalidator.evictBillViews(billId, bill.group.id)
        return billViewFactory.toItemDto(item, billItemSplitRepository.findByItemId(itemId))
    }

    // Deletes one item and its splits from an open bill.
    @Transactional
    fun deleteItem(
        billId: UUID,
        itemId: UUID,
        requesterId: UUID,
    ) {
        val bill = getEditableBill(billId, requesterId)
        billItemSplitRepository.deleteByItemId(itemId)
        billItemRepository.deleteById(itemId)
        recalculateTotal(bill)
        cacheInvalidator.evictBillViews(billId, bill.group.id)
    }

    // Replaces item splits for provided item ids in an open bill.
    @Transactional
    fun setSplits(
        billId: UUID,
        requesterId: UUID,
        request: BillSplitsRequest,
    ) {
        val bill = getEditableBill(billId, requesterId)
        val itemIds = request.splits.map { it.itemId }.distinct()
        val items = billItemRepository.findAllById(itemIds).associateBy { it.id }
        val users = userRepository.findAllById(request.splits.map { it.userId }.distinct()).associateBy { it.id }
        val membersByUserId = groupMemberRepository.findByIdGroupId(bill.group.id).associateBy { it.id.userId }
        val duplicatedAssignments =
            request.splits
                .groupBy { it.itemId to it.userId }
                .filterValues { it.size > 1 }
                .keys

        if (duplicatedAssignments.isNotEmpty()) throw ConflictException("Duplicate split assignment in request")
        validateSplitReferences(itemIds, items, bill)
        validateSplitUsers(users, request, membersByUserId)
        validateSplitAmountsByItem(items, request.splits)
        itemIds.forEach(billItemSplitRepository::deleteByItemId)

        request.splits.forEach { entry ->
            val item = items[entry.itemId] ?: throw NotFoundException("Item ${entry.itemId} not found")
            val user = users[entry.userId] ?: throw NotFoundException("User ${entry.userId} not found")
            billItemSplitRepository.save(BillItemSplit(item = item, user = user, shareAmount = entry.shareAmount))
        }
        cacheInvalidator.evictBillViews(billId, bill.group.id)
    }

    // Settles one debtor reimbursement and auto-finalizes bill when all are paid.
    @Transactional
    fun settleTransaction(
        billId: UUID,
        transactionId: UUID,
        requesterId: UUID,
    ): BillDto {
        val bill = findBillForMember(billId, requesterId)
        if (bill.status == BillStatus.OPEN) throw ConflictException("Bill is not paid by winner yet")

        val transaction =
            transactionRepository.findByIdAndBillId(transactionId, billId)
                ?: throw NotFoundException("Transaction not found")
        if (transaction.status == TransactionStatus.SETTLED) throw ConflictException("Transaction already settled")
        if (transaction.debtor.id != requesterId) {
            throw ForbiddenException("Only transaction debtor can mark reimbursement as paid")
        }

        transaction.status = TransactionStatus.SETTLED
        transaction.settledAt = OffsetDateTime.now()
        transactionRepository.save(transaction)
        settleBillIfNoPendingTransactions(bill)

        cacheInvalidator.evictBillViews(billId, bill.group.id)
        return toDto(bill, billItemRepository.findByBillId(billId))
    }

    // Finalizes bill only when all reimbursements are already settled.
    @Transactional
    fun settleBill(
        billId: UUID,
        requesterId: UUID,
    ): BillDto {
        val bill = findBillForMember(billId, requesterId)
        if (bill.status == BillStatus.SETTLED) throw ConflictException("Bill already settled")
        if (bill.status == BillStatus.OPEN) throw ConflictException("Bill is not paid by winner yet")
        if (transactionRepository.existsByBillIdAndStatus(billId, TransactionStatus.PENDING)) {
            throw ConflictException("Bill has pending reimbursements")
        }
        bill.status = BillStatus.SETTLED
        billRepository.save(bill)

        cacheInvalidator.evictBillViews(billId, bill.group.id)
        return toDto(bill, billItemRepository.findByBillId(billId))
    }

    private fun findBillForMember(
        billId: UUID,
        requesterId: UUID,
    ): Bill {
        val bill = findBillOrThrow(billId)
        requireMember(bill.group.id, requesterId)
        return bill
    }

    private fun getEditableBill(
        billId: UUID,
        requesterId: UUID,
    ): Bill {
        val bill = findBillForMember(billId, requesterId)
        requireOpen(bill)
        return bill
    }

    private fun findBillOrThrow(billId: UUID): Bill = billRepository.findById(billId).orElseThrow { NotFoundException("Bill not found") }

    private fun requireMember(
        groupId: UUID,
        userId: UUID,
    ) {
        if (!groupMemberRepository.existsByIdGroupIdAndIdUserId(groupId, userId)) {
            throw ForbiddenException("Not a member of this group")
        }
    }

    private fun requireOpen(bill: Bill) {
        if (bill.status != BillStatus.OPEN) throw ConflictException("Bill is not open")
    }

    private fun requireStatus(
        bill: Bill,
        status: BillStatus,
    ) {
        if (bill.status != status) throw ConflictException("Bill must be ${status.toDbValue()}")
    }

    private fun recalculateTotal(bill: Bill) {
        val items = billItemRepository.findByBillId(bill.id)
        bill.total = items.fold(BigDecimal.ZERO) { acc, item -> acc + item.price * BigDecimal(item.quantity) }
        billRepository.save(bill)
    }

    private fun settleBillIfNoPendingTransactions(bill: Bill) {
        if (bill.status == BillStatus.SETTLED) return
        if (transactionRepository.existsByBillIdAndStatus(bill.id, TransactionStatus.PENDING)) return
        bill.status = BillStatus.SETTLED
        billRepository.save(bill)
    }

    private fun validateSplitReferences(
        itemIds: List<UUID>,
        items: Map<UUID, BillItem>,
        bill: Bill,
    ) {
        if (items.size != itemIds.size) throw NotFoundException("Some items were not found")
        items.values.forEach { item ->
            if (item.bill.id != bill.id) throw ConflictException("Item ${item.id} does not belong to bill ${bill.id}")
        }
    }

    private fun validateSplitUsers(
        users: Map<UUID, User>,
        request: BillSplitsRequest,
        membersByUserId: Map<UUID, GroupMember>,
    ) {
        val uniqueUserCount =
            request.splits
                .map { it.userId }
                .distinct()
                .size
        if (users.size != uniqueUserCount) {
            throw NotFoundException("Some users were not found")
        }
        users.keys.forEach { userId ->
            if (!membersByUserId.containsKey(userId)) throw ForbiddenException("User $userId is not a member of this group")
        }
    }

    private fun validateSplitAmountsByItem(
        items: Map<UUID, BillItem>,
        splits: List<BillSplitsRequest.SplitEntry>,
    ) {
        val splitsByItemId = splits.groupBy { it.itemId }
        splitsByItemId.forEach { (itemId, entries) ->
            val item = items[itemId] ?: throw NotFoundException("Item $itemId not found")
            val expected = item.price * BigDecimal(item.quantity)
            val actual = entries.fold(BigDecimal.ZERO) { acc, entry -> acc + entry.shareAmount }
            if (actual.compareTo(expected) != 0) {
                throw ConflictException("Split sum for item $itemId must equal item total")
            }
        }
    }

    private fun calculateDebtsByUser(
        bill: Bill,
        items: List<BillItem>,
        splits: List<BillItemSplit>,
    ): Map<UUID, BigDecimal> {
        val membersByUserId = groupMemberRepository.findByIdGroupId(bill.group.id).associateBy { it.id.userId }
        val splitsByItemId = splits.groupBy { it.item.id }
        val debts = mutableMapOf<UUID, BigDecimal>()

        items.forEach { item ->
            val itemSplits = splitsByItemId[item.id].orEmpty()
            if (itemSplits.isEmpty()) throw ConflictException("Item ${item.id} has no splits")
            val expected = item.price * BigDecimal(item.quantity)
            val actual = itemSplits.fold(BigDecimal.ZERO) { acc, split -> acc + split.shareAmount }
            if (actual.compareTo(expected) != 0) {
                throw ConflictException("Split sum for item ${item.id} must equal item total")
            }
            itemSplits.forEach { split ->
                if (!membersByUserId.containsKey(split.user.id)) {
                    throw ForbiddenException("User ${split.user.id} is not a member of this group")
                }
                debts[split.user.id] = (debts[split.user.id] ?: BigDecimal.ZERO) + split.shareAmount
            }
        }
        return debts
    }

    private fun findUserById(userId: UUID): User = userRepository.findById(userId).orElseThrow { NotFoundException("User not found") }

    private fun toDto(
        bill: Bill,
        items: List<BillItem>,
    ): BillDto = billViewFactory.buildBill(bill, items)
}
