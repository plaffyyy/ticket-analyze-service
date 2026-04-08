package com.dealer.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "app.rate-limit")
data class RateLimitProperties(
    val enabled: Boolean = true,
    val auth: Limit = Limit(),
    val privateApi: Limit = Limit(capacity = 300, refillTokens = 300, refillPeriod = Duration.ofMinutes(1)),
    val publicApi: Limit = Limit(capacity = 120, refillTokens = 120, refillPeriod = Duration.ofMinutes(1)),
) {
    data class Limit(
        val capacity: Long = 30,
        val refillTokens: Long = 30,
        val refillPeriod: Duration = Duration.ofMinutes(1),
        val refillStrategy: RefillStrategy = RefillStrategy.GREEDY,
    )

    enum class RefillStrategy {
        GREEDY,
        INTERVALLY,
    }
}
