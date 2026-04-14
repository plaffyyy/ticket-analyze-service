package com.dealer.api.controller

import com.dealer.domain.dto.AddBillItemRequest
import com.dealer.domain.dto.BillDto
import com.dealer.domain.dto.BillItemDto
import com.dealer.domain.dto.BillSplitsRequest
import com.dealer.domain.dto.CreateBillRequest
import com.dealer.domain.dto.SpinResponse
import com.dealer.domain.dto.UpdateBillRequest
import com.dealer.security.SecurityUtils
import com.dealer.service.BillService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/bills")
class BillController(
    private val billService: BillService,
) {
    // Creates a new bill in a group.
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createBill(
        @Valid @RequestBody request: CreateBillRequest,
    ): BillDto = billService.createBill(SecurityUtils.getCurrentUserId(), request)

    // Returns full bill details with items and splits.
    @GetMapping("/{id}")
    fun getBill(
        @PathVariable id: UUID,
    ): BillDto = billService.getBill(id, SecurityUtils.getCurrentUserId())

    // Updates editable bill fields.
    @PatchMapping("/{id}")
    fun updateBill(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateBillRequest,
    ): BillDto = billService.updateBill(id, SecurityUtils.getCurrentUserId(), request)

    // Deletes a bill.
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteBill(
        @PathVariable id: UUID,
    ) = billService.deleteBill(id, SecurityUtils.getCurrentUserId())

    // Finalizes bill after all reimbursements are settled.
    @PatchMapping("/{id}/settle")
    fun settleBill(
        @PathVariable id: UUID,
    ): BillDto = billService.settleBill(id, SecurityUtils.getCurrentUserId())

    // Randomly chooses a group member who pays the full check.
    @PostMapping("/{id}/spin")
    fun spinWinner(
        @PathVariable id: UUID,
    ): SpinResponse = billService.spinWinner(id, SecurityUtils.getCurrentUserId())

    // Marks that selected winner paid the check and creates reimbursements.
    @PatchMapping("/{id}/paid-by-winner")
    fun markPaidByWinner(
        @PathVariable id: UUID,
    ): BillDto = billService.markPaidByWinner(id, SecurityUtils.getCurrentUserId())

    // Marks one debtor->winner reimbursement as paid.
    @PatchMapping("/{id}/transactions/{transactionId}/settle")
    fun settleReimbursement(
        @PathVariable id: UUID,
        @PathVariable transactionId: UUID,
    ): BillDto = billService.settleTransaction(id, transactionId, SecurityUtils.getCurrentUserId())

    // Adds one item into an open bill.
    @PostMapping("/{id}/items")
    @ResponseStatus(HttpStatus.CREATED)
    fun addItem(
        @PathVariable id: UUID,
        @Valid @RequestBody request: AddBillItemRequest,
    ): BillItemDto = billService.addItem(id, SecurityUtils.getCurrentUserId(), request)

    // Updates an existing bill item.
    @PatchMapping("/{id}/items/{itemId}")
    fun updateItem(
        @PathVariable id: UUID,
        @PathVariable itemId: UUID,
        @Valid @RequestBody request: AddBillItemRequest,
    ): BillItemDto = billService.updateItem(id, itemId, SecurityUtils.getCurrentUserId(), request)

    // Removes one item and all its splits.
    @DeleteMapping("/{id}/items/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteItem(
        @PathVariable id: UUID,
        @PathVariable itemId: UUID,
    ) = billService.deleteItem(id, itemId, SecurityUtils.getCurrentUserId())

    // Replaces splits for the provided item ids.
    @PostMapping("/{id}/splits")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun setSplits(
        @PathVariable id: UUID,
        @Valid @RequestBody request: BillSplitsRequest,
    ) = billService.setSplits(id, SecurityUtils.getCurrentUserId(), request)
}
