package com.dealer.support.group

import com.dealer.domain.dto.BalanceEntry
import com.dealer.domain.dto.BalanceResponse
import com.dealer.domain.dto.GroupDto
import com.dealer.domain.dto.MemberDto
import com.dealer.domain.model.Group
import com.dealer.domain.model.GroupMember
import com.dealer.exception.NotFoundException
import com.dealer.repository.GroupMemberRepository
import com.dealer.repository.GroupRepository
import com.dealer.repository.TransactionRepository
import com.dealer.repository.UserRepository
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.util.UUID

@Component
class GroupViewFactory(
    private val groupRepository: GroupRepository,
    private val groupMemberRepository: GroupMemberRepository,
    private val userRepository: UserRepository,
    private val transactionRepository: TransactionRepository,
) {
    fun buildGroup(groupId: UUID): GroupDto {
        val group = findGroupOrThrow(groupId)
        val members = groupMemberRepository.findByIdGroupId(groupId)
        return toDto(group, members)
    }

    fun buildBalance(groupId: UUID): BalanceResponse {
        findGroupOrThrow(groupId)

        val members = groupMemberRepository.findByIdGroupId(groupId)
        val transactions = transactionRepository.findByGroupId(groupId)
        val users = userRepository.findAllById(members.map { it.id.userId }).associateBy { it.id }
        val balanceByUserId = members.associate { it.id.userId to BigDecimal.ZERO }.toMutableMap()

        transactions.forEach { tx ->
            balanceByUserId[tx.creditor.id] = (balanceByUserId[tx.creditor.id] ?: BigDecimal.ZERO) + tx.amount
            balanceByUserId[tx.debtor.id] = (balanceByUserId[tx.debtor.id] ?: BigDecimal.ZERO) - tx.amount
        }

        return BalanceResponse(
            groupId = groupId,
            balances =
                balanceByUserId.entries
                    .map { (userId, balance) ->
                        BalanceEntry(userId, users[userId]?.name ?: "Unknown", balance)
                    }.sortedByDescending { it.balance },
        )
    }

    private fun findGroupOrThrow(groupId: UUID): Group =
        groupRepository.findById(groupId).orElseThrow { NotFoundException("Group not found") }

    private fun toDto(
        group: Group,
        members: List<GroupMember>,
    ): GroupDto {
        val users = userRepository.findAllById(members.map { it.id.userId }).associateBy { it.id }

        return GroupDto(
            id = group.id,
            name = group.name,
            ownerId = group.owner.id,
            inviteCode = group.inviteCode,
            currency = group.currency,
            createdAt = group.createdAt,
            members =
                members.map { member ->
                    MemberDto(
                        userId = member.id.userId,
                        name = users[member.id.userId]?.name ?: "Unknown",
                        role = member.role.name.lowercase(),
                    )
                },
        )
    }
}
