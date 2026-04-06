package com.dealer.service

import com.dealer.domain.dto.BalanceEntry
import com.dealer.domain.dto.BalanceResponse
import com.dealer.domain.dto.CreateGroupRequest
import com.dealer.domain.dto.GroupDto
import com.dealer.domain.dto.InviteResponse
import com.dealer.domain.dto.MemberDto
import com.dealer.domain.dto.UpdateGroupRequest
import com.dealer.domain.model.Group
import com.dealer.domain.model.GroupMember
import com.dealer.domain.model.GroupMemberId
import com.dealer.domain.model.MemberRole
import com.dealer.exception.ConflictException
import com.dealer.exception.ForbiddenException
import com.dealer.exception.NotFoundException
import com.dealer.repository.GroupMemberRepository
import com.dealer.repository.GroupRepository
import com.dealer.repository.TransactionRepository
import com.dealer.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.security.SecureRandom
import java.util.UUID

@Service
class GroupService(
    private val groupRepository: GroupRepository,
    private val groupMemberRepository: GroupMemberRepository,
    private val userRepository: UserRepository,
    private val transactionRepository: TransactionRepository,
) {
    private val secureRandom = SecureRandom()
    private val base62Chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"

    @Transactional
    fun createGroup(
        ownerId: UUID,
        request: CreateGroupRequest,
    ): GroupDto {
        val owner = userRepository.findById(ownerId).orElseThrow { NotFoundException("User not found") }
        val group =
            Group(
                name = request.name.trim(),
                owner = owner,
                inviteCode = generateInviteCode(),
                currency = request.currency.trim().uppercase(),
            )
        groupRepository.save(group)
        groupMemberRepository.save(
            GroupMember(id = GroupMemberId(group.id, ownerId), role = MemberRole.OWNER),
        )
        return toDto(group, listOf(GroupMember(id = GroupMemberId(group.id, ownerId), role = MemberRole.OWNER)))
    }

    @Transactional(readOnly = true)
    fun getGroup(
        groupId: UUID,
        requesterId: UUID,
    ): GroupDto {
        val group = findGroupOrThrow(groupId)
        requireMember(groupId, requesterId)
        val members = groupMemberRepository.findByIdGroupId(groupId)
        return toDto(group, members)
    }

    @Transactional
    fun updateGroup(
        groupId: UUID,
        requesterId: UUID,
        request: UpdateGroupRequest,
    ): GroupDto {
        val group = findGroupOrThrow(groupId)
        requireOwner(group, requesterId)
        request.name?.trim()?.let { group.name = it }
        request.currency
            ?.trim()
            ?.uppercase()
            ?.let { group.currency = it }
        groupRepository.save(group)
        val members = groupMemberRepository.findByIdGroupId(groupId)
        return toDto(group, members)
    }

    @Transactional
    fun deleteGroup(
        groupId: UUID,
        requesterId: UUID,
    ) {
        val group = findGroupOrThrow(groupId)
        requireOwner(group, requesterId)
        groupRepository.delete(group)
    }

    @Transactional
    fun regenerateInvite(
        groupId: UUID,
        requesterId: UUID,
    ): InviteResponse {
        val group = findGroupOrThrow(groupId)
        requireOwner(group, requesterId)
        group.inviteCode = generateInviteCode()
        groupRepository.save(group)
        return InviteResponse(group.inviteCode, "dealer://groups/join/${group.inviteCode}")
    }

    @Transactional
    fun joinGroup(
        code: String,
        userId: UUID,
    ): GroupDto {
        val group =
            groupRepository
                .findByInviteCode(code)
                .orElseThrow { NotFoundException("Group not found for invite code") }
        if (groupMemberRepository.existsByIdGroupIdAndIdUserId(group.id, userId)) {
            throw ConflictException("Already a member of this group")
        }
        groupMemberRepository.save(GroupMember(id = GroupMemberId(group.id, userId), role = MemberRole.MEMBER))
        val members = groupMemberRepository.findByIdGroupId(group.id)
        return toDto(group, members)
    }

    @Transactional(readOnly = true)
    fun getBalance(
        groupId: UUID,
        requesterId: UUID,
    ): BalanceResponse {
        findGroupOrThrow(groupId)
        requireMember(groupId, requesterId)
        val members = groupMemberRepository.findByIdGroupId(groupId)
        val transactions = transactionRepository.findByGroupId(groupId)

        val balanceMap = mutableMapOf<UUID, BigDecimal>()
        members.forEach { balanceMap[it.id.userId] = BigDecimal.ZERO }

        transactions.forEach { tx ->
            balanceMap[tx.creditor.id] = (balanceMap[tx.creditor.id] ?: BigDecimal.ZERO) + tx.amount
            balanceMap[tx.debtor.id] = (balanceMap[tx.debtor.id] ?: BigDecimal.ZERO) - tx.amount
        }

        val userIds = members.map { it.id.userId }
        val users = userRepository.findAllById(userIds).associateBy { it.id }

        val balances =
            balanceMap.entries
                .map { (userId, balance) ->
                    BalanceEntry(userId, users[userId]?.name ?: "Unknown", balance)
                }.sortedByDescending { it.balance }

        return BalanceResponse(groupId, balances)
    }

    @Transactional
    fun removeMember(
        groupId: UUID,
        targetUserId: UUID,
        requesterId: UUID,
    ) {
        val group = findGroupOrThrow(groupId)
        requireOwner(group, requesterId)
        if (targetUserId == requesterId) throw ForbiddenException("Owner cannot remove themselves")
        if (!groupMemberRepository.existsByIdGroupIdAndIdUserId(groupId, targetUserId)) {
            throw NotFoundException("Member not found in group")
        }
        groupMemberRepository.deleteByIdGroupIdAndIdUserId(groupId, targetUserId)
    }

    private fun findGroupOrThrow(groupId: UUID): Group =
        groupRepository.findById(groupId).orElseThrow { NotFoundException("Group not found") }

    private fun requireMember(
        groupId: UUID,
        userId: UUID,
    ) {
        if (!groupMemberRepository.existsByIdGroupIdAndIdUserId(groupId, userId)) {
            throw ForbiddenException("Not a member of this group")
        }
    }

    private fun requireOwner(
        group: Group,
        userId: UUID,
    ) {
        if (group.owner.id != userId) throw ForbiddenException("Only the group owner can perform this action")
    }

    private fun generateInviteCode(): String {
        var code: String
        do {
            code = (1..8).map { base62Chars[secureRandom.nextInt(base62Chars.length)] }.joinToString("")
        } while (groupRepository.existsByInviteCode(code))
        return code
    }

    private fun toDto(
        group: Group,
        members: List<GroupMember>,
    ): GroupDto {
        val userIds = members.map { it.id.userId }
        val users = userRepository.findAllById(userIds).associateBy { it.id }
        return GroupDto(
            id = group.id,
            name = group.name,
            ownerId = group.owner.id,
            inviteCode = group.inviteCode,
            currency = group.currency,
            createdAt = group.createdAt,
            members =
                members.map { m ->
                    MemberDto(m.id.userId, users[m.id.userId]?.name ?: "Unknown", m.role.name.lowercase())
                },
        )
    }
}
