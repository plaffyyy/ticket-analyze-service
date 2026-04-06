package com.dealer.service

import com.dealer.domain.dto.AddBillItemRequest
import com.dealer.domain.dto.BillDto
import com.dealer.domain.dto.BillItemDto
import com.dealer.domain.dto.BillSplitsRequest
import com.dealer.domain.dto.CreateBillRequest
import com.dealer.domain.dto.SplitDto
import com.dealer.domain.dto.UpdateBillRequest
import com.dealer.domain.model.Bill
import com.dealer.domain.model.BillItem
import com.dealer.domain.model.BillItemSplit
import com.dealer.domain.model.BillStatus
import com.dealer.domain.model.TransactionStatus
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
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
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
) {
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
        return toDto(bill, emptyList())
    }

    @Transactional(readOnly = true)
    fun getBill(
        billId: UUID,
        requesterId: UUID,
    ): BillDto {
        val bill = findBillOrThrow(billId)
        requireMember(bill.group.id, requesterId)
        val items = billItemRepository.findByBillId(billId)
        return toDto(bill, items)
    }

    @Transactional(readOnly = true)
    fun getGroupBills(
        groupId: UUID,
        requesterId: UUID,
    ): List<BillDto> {
        requireMember(groupId, requesterId)
        return billRepository.findByGroupId(groupId).map { bill ->
            toDto(bill, billItemRepository.findByBillId(bill.id))
        }
    }

    @Transactional
    fun updateBill(
        billId: UUID,
        requesterId: UUID,
        request: UpdateBillRequest,
    ): BillDto {
        val bill = findBillOrThrow(billId)
        requireMember(bill.group.id, requesterId)
        requireOpen(bill)
        request.title?.trim()?.let { bill.title = it }
        billRepository.save(bill)
        val items = billItemRepository.findByBillId(billId)
        return toDto(bill, items)
    }

    @Transactional
    fun deleteBill(
        billId: UUID,
        requesterId: UUID,
    ) {
        val bill = findBillOrThrow(billId)
        requireMember(bill.group.id, requesterId)
        billRepository.delete(bill)
    }

    @Transactional
    fun addItem(
        billId: UUID,
        requesterId: UUID,
        request: AddBillItemRequest,
    ): BillItemDto {
        val bill = findBillOrThrow(billId)
        requireMember(bill.group.id, requesterId)
        requireOpen(bill)
        val item = BillItem(bill = bill, name = request.name.trim(), price = request.price, quantity = request.quantity)
        billItemRepository.save(item)
        recalculateTotal(bill)
        return toItemDto(item, emptyList())
    }

    @Transactional
    fun updateItem(
        billId: UUID,
        itemId: UUID,
        requesterId: UUID,
        request: AddBillItemRequest,
    ): BillItemDto {
        val bill = findBillOrThrow(billId)
        requireMember(bill.group.id, requesterId)
        requireOpen(bill)
        val item = billItemRepository.findById(itemId).orElseThrow { NotFoundException("Item not found") }
        item.name = request.name.trim()
        item.price = request.price
        item.quantity = request.quantity
        billItemRepository.save(item)
        recalculateTotal(bill)
        val splits = billItemSplitRepository.findByItemId(itemId)
        return toItemDto(item, splits)
    }

    @Transactional
    fun deleteItem(
        billId: UUID,
        itemId: UUID,
        requesterId: UUID,
    ) {
        val bill = findBillOrThrow(billId)
        requireMember(bill.group.id, requesterId)
        requireOpen(bill)
        billItemSplitRepository.deleteByItemId(itemId)
        billItemRepository.deleteById(itemId)
        recalculateTotal(bill)
    }

    @Transactional
    fun setSplits(
        billId: UUID,
        requesterId: UUID,
        request: BillSplitsRequest,
    ) {
        val bill = findBillOrThrow(billId)
        requireMember(bill.group.id, requesterId)
        requireOpen(bill)
        val itemIds = request.splits.map { it.itemId }.distinct()
        itemIds.forEach { billItemSplitRepository.deleteByItemId(it) }
        val items = billItemRepository.findAllById(itemIds).associateBy { it.id }
        val users = userRepository.findAllById(request.splits.map { it.userId }.distinct()).associateBy { it.id }
        request.splits.forEach { entry ->
            val item = items[entry.itemId] ?: throw NotFoundException("Item ${entry.itemId} not found")
            val user = users[entry.userId] ?: throw NotFoundException("User ${entry.userId} not found")
            billItemSplitRepository.save(BillItemSplit(item = item, user = user, shareAmount = entry.shareAmount))
        }
    }

    @Transactional
    fun settleBill(
        billId: UUID,
        requesterId: UUID,
    ): BillDto {
        val bill = findBillOrThrow(billId)
        requireMember(bill.group.id, requesterId)
        if (bill.status == BillStatus.SETTLED) throw ConflictException("Bill already settled")
        bill.status = BillStatus.SETTLED
        billRepository.save(bill)
        val transactions = transactionRepository.findByBillId(billId)
        val now = OffsetDateTime.now()
        transactions.filter { it.status == TransactionStatus.PENDING }.forEach {
            it.status = TransactionStatus.SETTLED
            it.settledAt = now
            transactionRepository.save(it)
        }
        val items = billItemRepository.findByBillId(billId)
        return toDto(bill, items)
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

    private fun recalculateTotal(bill: Bill) {
        val items = billItemRepository.findByBillId(bill.id)
        bill.total = items.fold(BigDecimal.ZERO) { acc, item -> acc + item.price * BigDecimal(item.quantity) }
        billRepository.save(bill)
    }

    private fun toDto(
        bill: Bill,
        items: List<BillItem>,
    ): BillDto {
        val itemDtos =
            items.map { item ->
                val splits = billItemSplitRepository.findByItemId(item.id)
                toItemDto(item, splits)
            }
        return BillDto(
            id = bill.id,
            groupId = bill.group.id,
            title = bill.title,
            total = bill.total,
            currency = bill.currency,
            status = bill.status.toDbValue(),
            receiptUrl = bill.receiptUrl,
            spunWinnerId = bill.spunWinner?.id,
            createdAt = bill.createdAt,
            items = itemDtos,
        )
    }

    private fun toItemDto(
        item: BillItem,
        splits: List<BillItemSplit>,
    ) = BillItemDto(
        id = item.id,
        name = item.name,
        price = item.price,
        quantity = item.quantity,
        splits = splits.map { SplitDto(it.id, it.user.id, it.shareAmount) },
    )
}
