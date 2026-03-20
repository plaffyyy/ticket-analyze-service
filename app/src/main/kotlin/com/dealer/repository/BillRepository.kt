package com.dealer.repository

import com.dealer.domain.model.Bill
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface BillRepository : JpaRepository<Bill, UUID> {
    fun findByGroupId(groupId: UUID): List<Bill>
}
