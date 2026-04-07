package com.dealer.api.controller

import com.dealer.domain.dto.BalanceResponse
import com.dealer.domain.dto.BillDto
import com.dealer.domain.dto.CreateGroupRequest
import com.dealer.domain.dto.GroupDto
import com.dealer.domain.dto.InviteResponse
import com.dealer.domain.dto.UpdateGroupRequest
import com.dealer.security.SecurityUtils
import com.dealer.service.BillService
import com.dealer.service.GroupService
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
@RequestMapping("/api/v1/groups")
class GroupController(
    private val groupService: GroupService,
    private val billService: BillService,
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createGroup(
        @Valid @RequestBody request: CreateGroupRequest,
    ): GroupDto = groupService.createGroup(SecurityUtils.getCurrentUserId(), request)

    @GetMapping("/{id}")
    fun getGroup(
        @PathVariable id: UUID,
    ): GroupDto = groupService.getGroup(id, SecurityUtils.getCurrentUserId())

    @PatchMapping("/{id}")
    fun updateGroup(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateGroupRequest,
    ): GroupDto = groupService.updateGroup(id, SecurityUtils.getCurrentUserId(), request)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteGroup(
        @PathVariable id: UUID,
    ) = groupService.deleteGroup(id, SecurityUtils.getCurrentUserId())

    @PostMapping("/{id}/invite")
    fun generateInvite(
        @PathVariable id: UUID,
    ): InviteResponse = groupService.regenerateInvite(id, SecurityUtils.getCurrentUserId())

    @PostMapping("/join/{code}")
    fun joinGroup(
        @PathVariable code: String,
    ): GroupDto = groupService.joinGroup(code, SecurityUtils.getCurrentUserId())

    @GetMapping("/{id}/balance")
    fun getBalance(
        @PathVariable id: UUID,
    ): BalanceResponse = groupService.getBalance(id, SecurityUtils.getCurrentUserId())

    @GetMapping("/{id}/bills")
    fun getGroupBills(
        @PathVariable id: UUID,
    ): List<BillDto> = billService.getGroupBills(id, SecurityUtils.getCurrentUserId())

    @DeleteMapping("/{id}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun removeMember(
        @PathVariable id: UUID,
        @PathVariable userId: UUID,
    ) = groupService.removeMember(id, userId, SecurityUtils.getCurrentUserId())
}
