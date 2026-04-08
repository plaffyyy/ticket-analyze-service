package com.dealer.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.firebase")
data class FirebaseProperties(
    val enabled: Boolean = false,
    val messagingTokenPath: String? = null,
) {
    init {
        if (enabled && messagingTokenPath == null) {
            throw IllegalArgumentException("You must specify a firebase messaging token path")
        }
    }
}
