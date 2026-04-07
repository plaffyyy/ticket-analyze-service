package com.dealer.repository

import com.dealer.domain.model.BillItemSplit
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface BillItemSplitRepository : JpaRepository<BillItemSplit, UUID> {
    fun findByItemId(itemId: UUID): List<BillItemSplit>

    fun deleteByItemId(itemId: UUID)
}
