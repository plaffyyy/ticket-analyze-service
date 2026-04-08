package com.dealer.support.cache

import com.dealer.config.CacheNames
import com.dealer.repository.GroupMemberRepository
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class CacheInvalidator(
    private val cacheSupport: CacheSupport,
    private val groupMemberRepository: GroupMemberRepository,
) {
    fun evictGroupViews(groupId: UUID) {
        cacheSupport.evict(CacheNames.GROUP, groupId)
        cacheSupport.evict(CacheNames.GROUP_BALANCE, groupId)
        cacheSupport.evict(CacheNames.GROUP_BILLS, groupId)
    }

    fun evictBillViews(
        billId: UUID,
        groupId: UUID,
    ) {
        cacheSupport.evict(CacheNames.BILL, billId)
        cacheSupport.evict(CacheNames.GROUP_BILLS, groupId)
        cacheSupport.evict(CacheNames.GROUP_BALANCE, groupId)
    }

    fun evictUserViews(userId: UUID) {
        cacheSupport.evict(CacheNames.CURRENT_USER, userId)
        groupMemberRepository
            .findByIdUserId(userId)
            .map { it.id.groupId }
            .distinct()
            .forEach { groupId ->
                cacheSupport.evict(CacheNames.GROUP, groupId)
                cacheSupport.evict(CacheNames.GROUP_BALANCE, groupId)
            }
    }
}
