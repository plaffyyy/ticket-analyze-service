package com.dealer.repository

import com.dealer.domain.model.BillItem
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface BillItemRepository : JpaRepository<BillItem, UUID> {
    fun findByBillId(billId: UUID): List<BillItem>

    fun deleteByBillId(billId: UUID)
}
