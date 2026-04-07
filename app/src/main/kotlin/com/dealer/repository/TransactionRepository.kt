package com.dealer.repository

import com.dealer.domain.model.Transaction
import com.dealer.domain.model.TransactionStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.util.UUID

@Repository
interface TransactionRepository : JpaRepository<Transaction, UUID> {
    fun findByBillId(billId: UUID): List<Transaction>

    @Query("SELECT t FROM Transaction t WHERE t.debtor.id = :userId OR t.creditor.id = :userId")
    fun findByUserId(
        @Param("userId") userId: UUID,
    ): List<Transaction>

    @Query("SELECT DISTINCT t FROM Transaction t WHERE t.bill.group.id = :groupId")
    fun findByGroupId(
        @Param("groupId") groupId: UUID,
    ): List<Transaction>

    fun findByStatusAndCreatedAtBefore(
        status: TransactionStatus,
        cutoff: OffsetDateTime,
    ): List<Transaction>
}
