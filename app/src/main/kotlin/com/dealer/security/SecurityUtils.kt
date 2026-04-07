package com.dealer.security

import com.dealer.exception.UnauthorizedException
import org.springframework.security.core.context.SecurityContextHolder
import java.util.UUID

object SecurityUtils {
    fun getCurrentUserId(): UUID {
        val principal =
            SecurityContextHolder.getContext().authentication?.principal
                ?: throw UnauthorizedException("Not authenticated")
        return when (principal) {
            is UUID -> principal
            is String -> UUID.fromString(principal)
            else -> throw UnauthorizedException("Invalid authentication principal")
        }
    }
}
