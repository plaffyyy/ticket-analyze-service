package com.dealer.repository

import com.dealer.domain.model.Group
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface GroupRepository : JpaRepository<Group, UUID> {
    fun findByInviteCode(inviteCode: String): Optional<Group>

    fun existsByInviteCode(inviteCode: String): Boolean
}
