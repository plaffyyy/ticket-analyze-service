package com.dealer.security

import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

@Component
class RateLimitKeyResolver {
    fun resolveIp(request: HttpServletRequest): String {
        val forwarded = request.getHeader("X-Forwarded-For")
        val candidate = forwarded?.split(",")?.firstOrNull()?.trim()
        if (!candidate.isNullOrBlank()) {
            return candidate
        }
        return request.remoteAddr ?: "unknown"
    }

    fun resolvePrivateApiKey(request: HttpServletRequest): String {
        val principal = SecurityContextHolder.getContext().authentication?.principal
        return if (principal is String && principal.isNotBlank()) {
            "user:$principal"
        } else {
            "ip:${resolveIp(request)}"
        }
    }
}
