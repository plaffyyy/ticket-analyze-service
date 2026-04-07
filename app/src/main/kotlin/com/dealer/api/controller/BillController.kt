package com.dealer.api.controller

import com.dealer.domain.dto.AddBillItemRequest
import com.dealer.domain.dto.BillDto
import com.dealer.domain.dto.BillItemDto
import com.dealer.domain.dto.BillSplitsRequest
import com.dealer.domain.dto.CreateBillRequest
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
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createBill(
        @Valid @RequestBody request: CreateBillRequest,
    ): BillDto = billService.createBill(SecurityUtils.getCurrentUserId(), request)

    @GetMapping("/{id}")
    fun getBill(
        @PathVariable id: UUID,
    ): BillDto = billService.getBill(id, SecurityUtils.getCurrentUserId())

    @PatchMapping("/{id}")
    fun updateBill(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateBillRequest,
    ): BillDto = billService.updateBill(id, SecurityUtils.getCurrentUserId(), request)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteBill(
        @PathVariable id: UUID,
    ) = billService.deleteBill(id, SecurityUtils.getCurrentUserId())

    @PatchMapping("/{id}/settle")
    fun settleBill(
        @PathVariable id: UUID,
    ): BillDto = billService.settleBill(id, SecurityUtils.getCurrentUserId())

    @PostMapping("/{id}/items")
    @ResponseStatus(HttpStatus.CREATED)
    fun addItem(
        @PathVariable id: UUID,
        @Valid @RequestBody request: AddBillItemRequest,
    ): BillItemDto = billService.addItem(id, SecurityUtils.getCurrentUserId(), request)

    @PatchMapping("/{id}/items/{itemId}")
    fun updateItem(
        @PathVariable id: UUID,
        @PathVariable itemId: UUID,
        @Valid @RequestBody request: AddBillItemRequest,
    ): BillItemDto = billService.updateItem(id, itemId, SecurityUtils.getCurrentUserId(), request)

    @DeleteMapping("/{id}/items/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteItem(
        @PathVariable id: UUID,
        @PathVariable itemId: UUID,
    ) = billService.deleteItem(id, itemId, SecurityUtils.getCurrentUserId())

    @PostMapping("/{id}/splits")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun setSplits(
        @PathVariable id: UUID,
        @Valid @RequestBody request: BillSplitsRequest,
    ) = billService.setSplits(id, SecurityUtils.getCurrentUserId(), request)
}
