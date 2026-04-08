package com.dealer.repository

import com.dealer.domain.model.Bill
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface BillRepository : JpaRepository<Bill, UUID> {
    fun findByGroupId(groupId: UUID): List<Bill>

    @Query("SELECT b.group.id FROM Bill b WHERE b.id = :billId")
    fun findGroupIdByBillId(
        @Param("billId") billId: UUID,
    ): UUID?
}
