package com.dealer.repository

import com.dealer.domain.model.GroupMember
import com.dealer.domain.model.GroupMemberId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface GroupMemberRepository : JpaRepository<GroupMember, GroupMemberId> {
    fun findByIdGroupId(groupId: UUID): List<GroupMember>

    fun findByIdUserId(userId: UUID): List<GroupMember>

    fun existsByIdGroupIdAndIdUserId(
        groupId: UUID,
        userId: UUID,
    ): Boolean

    fun deleteByIdGroupIdAndIdUserId(
        groupId: UUID,
        userId: UUID,
    )
}
