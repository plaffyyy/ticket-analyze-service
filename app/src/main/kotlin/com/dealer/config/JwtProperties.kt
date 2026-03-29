package com.dealer.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import java.time.Duration

@Component
@ConfigurationProperties(prefix = "security.jwt")
class JwtProperties {
    lateinit var secret: String
    var accessTokenTtl: Duration = Duration.ofMinutes(15)
    var refreshTokenTtl: Duration = Duration.ofDays(30)
}
