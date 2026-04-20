package com.dealer.service

import com.dealer.config.CacheNames
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
import com.dealer.domain.model.UserAddedToGroupEvent
import com.dealer.exception.ConflictException
import com.dealer.exception.ForbiddenException
import com.dealer.exception.NotFoundException
import com.dealer.metrics.AppMetrics
import com.dealer.repository.GroupMemberRepository
import com.dealer.repository.GroupRepository
import com.dealer.repository.UserRepository
import com.dealer.support.cache.CacheInvalidator
import com.dealer.support.cache.CacheSupport
import com.dealer.support.group.GroupViewFactory
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.util.UUID

@Service
class GroupService(
    private val groupRepository: GroupRepository,
    private val groupMemberRepository: GroupMemberRepository,
    private val userRepository: UserRepository,
    private val cacheSupport: CacheSupport,
    private val cacheInvalidator: CacheInvalidator,
    private val groupViewFactory: GroupViewFactory,
    private val eventPublisher: ApplicationEventPublisher,
    private val appMetrics: AppMetrics,
) {
    private val logger = LoggerFactory.getLogger(GroupService::class.java)
    private val secureRandom = SecureRandom()
    private val base62Chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"

    @Transactional
    fun createGroup(
        ownerId: UUID,
        request: CreateGroupRequest,
    ): GroupDto {
        logger.debug(
            "Creating group for ownerId=$ownerId, name='${request.name.trim()}', currency='${request.currency.trim().uppercase()}'",
        )
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
        appMetrics.incrementGroupCreations()
        logger.info("Group created: groupId=${group.id}, ownerId=$ownerId, currency=${group.currency}")
        return toDto(group, listOf(GroupMember(id = GroupMemberId(group.id, ownerId), role = MemberRole.OWNER)))
    }

    @Transactional(readOnly = true)
    fun getGroup(
        groupId: UUID,
        requesterId: UUID,
    ): GroupDto {
        requireExistingGroup(groupId)
        requireMember(groupId, requesterId)
        return cacheSupport.getOrLoad(CacheNames.GROUP, groupId) { groupViewFactory.buildGroup(groupId) }
    }

    @Transactional
    fun updateGroup(
        groupId: UUID,
        requesterId: UUID,
        request: UpdateGroupRequest,
    ): GroupDto {
        logger.debug(
            "Updating group: groupId=$groupId, requesterId=$requesterId, hasName=${request.name != null}, hasCurrency=${request.currency != null}",
        )
        val group = findGroupOrThrow(groupId)
        requireOwner(group, requesterId)
        request.name?.trim()?.let { group.name = it }
        request.currency
            ?.trim()
            ?.uppercase()
            ?.let { group.currency = it }
        groupRepository.save(group)
        cacheInvalidator.evictGroupViews(groupId)
        val members = groupMemberRepository.findByIdGroupId(groupId)
        appMetrics.incrementGroupUpdates()
        logger.info("Group updated: groupId=$groupId, requesterId=$requesterId, name='${group.name}', currency='${group.currency}'")
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
        cacheInvalidator.evictGroupViews(groupId)
        logger.info("Group deleted: groupId=$groupId, ownerId=$requesterId")
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
        cacheSupport.evict(CacheNames.GROUP, groupId)
        return InviteResponse(group.inviteCode, "dealer://groups/join/${group.inviteCode}")
    }

    @Transactional
    fun joinGroup(
        code: String,
        userId: UUID,
    ): GroupDto {
        logger.debug("Joining group by invite code for userId=$userId")
        val group =
            groupRepository
                .findByInviteCode(code)
                .orElseThrow { NotFoundException("Group not found for invite code") }
        if (groupMemberRepository.existsByIdGroupIdAndIdUserId(group.id, userId)) {
            throw ConflictException("Already a member of this group")
        }
        groupMemberRepository.save(GroupMember(id = GroupMemberId(group.id, userId), role = MemberRole.MEMBER))
        cacheInvalidator.evictGroupViews(group.id)
        val members = groupMemberRepository.findByIdGroupId(group.id)

        eventPublisher.publishEvent(
            UserAddedToGroupEvent(
                groupId = group.id,
                groupName = group.name,
                addedUserId = userId,
                membersIds = members.map { it.id }.filter { it.userId != userId }.toList(),
            ),
        )

        logger.info("User joined group: groupId=${group.id}, userId=$userId")
        return toDto(group, members)
    }

    @Transactional(readOnly = true)
    fun getBalance(
        groupId: UUID,
        requesterId: UUID,
    ): BalanceResponse {
        requireExistingGroup(groupId)
        requireMember(groupId, requesterId)
        return cacheSupport.getOrLoad(CacheNames.GROUP_BALANCE, groupId) { groupViewFactory.buildBalance(groupId) }
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
        cacheInvalidator.evictGroupViews(groupId)
    }

    private fun requireExistingGroup(groupId: UUID) {
        if (!groupRepository.existsById(groupId)) {
            throw NotFoundException("Group not found")
        }
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
