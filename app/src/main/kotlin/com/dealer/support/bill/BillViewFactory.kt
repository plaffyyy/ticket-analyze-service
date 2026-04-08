package com.dealer.support.bill

import com.dealer.domain.dto.BillDto
import com.dealer.domain.dto.BillItemDto
import com.dealer.domain.dto.SplitDto
import com.dealer.domain.model.Bill
import com.dealer.domain.model.BillItem
import com.dealer.domain.model.BillItemSplit
import com.dealer.exception.NotFoundException
import com.dealer.repository.BillItemRepository
import com.dealer.repository.BillItemSplitRepository
import com.dealer.repository.BillRepository
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class BillViewFactory(
    private val billRepository: BillRepository,
    private val billItemRepository: BillItemRepository,
    private val billItemSplitRepository: BillItemSplitRepository,
) {
    fun buildBill(billId: UUID): BillDto {
        val bill = findBillOrThrow(billId)
        return buildBill(bill, billItemRepository.findByBillId(billId))
    }

    fun buildGroupBills(groupId: UUID): List<BillDto> {
        val bills = billRepository.findByGroupId(groupId)
        if (bills.isEmpty()) {
            return emptyList()
        }

        val itemsByBillId =
            billItemRepository
                .findByBillIdIn(bills.map { it.id })
                .groupBy { it.bill.id }
        val splitsByItemId = loadSplits(itemsByBillId.values.flatten())

        return bills.map { bill ->
            buildBill(
                bill = bill,
                items = itemsByBillId[bill.id].orEmpty(),
                splitsByItemId = splitsByItemId,
            )
        }
    }

    fun buildBill(
        bill: Bill,
        items: List<BillItem>,
    ): BillDto = buildBill(bill, items, loadSplits(items))

    fun toItemDto(
        item: BillItem,
        splits: List<BillItemSplit>,
    ): BillItemDto =
        BillItemDto(
            id = item.id,
            name = item.name,
            price = item.price,
            quantity = item.quantity,
            splits = splits.map { split -> SplitDto(split.id, split.user.id, split.shareAmount) },
        )

    private fun buildBill(
        bill: Bill,
        items: List<BillItem>,
        splitsByItemId: Map<UUID, List<BillItemSplit>>,
    ): BillDto =
        BillDto(
            id = bill.id,
            groupId = bill.group.id,
            title = bill.title,
            total = bill.total,
            currency = bill.currency,
            status = bill.status.toDbValue(),
            receiptUrl = bill.receiptUrl,
            spunWinnerId = bill.spunWinner?.id,
            createdAt = bill.createdAt,
            items =
                items.map { item ->
                    BillItemDto(
                        id = item.id,
                        name = item.name,
                        price = item.price,
                        quantity = item.quantity,
                        splits =
                            splitsByItemId[item.id]
                                .orEmpty()
                                .map { split -> SplitDto(split.id, split.user.id, split.shareAmount) },
                    )
                },
        )

    private fun findBillOrThrow(billId: UUID): Bill = billRepository.findById(billId).orElseThrow { NotFoundException("Bill not found") }

    private fun loadSplits(items: List<BillItem>): Map<UUID, List<BillItemSplit>> {
        if (items.isEmpty()) {
            return emptyMap()
        }
        return billItemSplitRepository.findByItemIdIn(items.map { it.id }).groupBy { it.item.id }
    }
}
