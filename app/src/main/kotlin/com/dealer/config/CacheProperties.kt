package com.dealer.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "app.cache")
data class CacheProperties(
    val enabled: Boolean = false,
    val ttl: Ttl = Ttl(),
) {
    data class Ttl(
        val currentUser: Duration = Duration.ofMinutes(5),
        val group: Duration = Duration.ofMinutes(10),
        val groupBalance: Duration = Duration.ofMinutes(1),
        val bill: Duration = Duration.ofMinutes(5),
        val groupBills: Duration = Duration.ofMinutes(2),
    )
}
