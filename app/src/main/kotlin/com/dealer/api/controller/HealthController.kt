package com.dealer.api.controller

import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class HealthController(
    private val jdbcTemplate: JdbcTemplate,
//    private val redisConnectionFactory: RedisConnectionFactory off now, add redis in future
) {
    @GetMapping("/health")
    fun health() = mapOf("status" to "UP")

    @GetMapping("/ready")
    fun ready(): ResponseEntity<Map<String, Any>> {
        val dbStatus =
            runCatching {
                jdbcTemplate.queryForObject("SELECT 1", Int::class.java)
                "UP"
            }.getOrElse { "DOWN" }
        val allUp = dbStatus == "UP"
        return ResponseEntity
            .status(if (allUp) 200 else 503)
            .body(mapOf("status" to if (allUp) "UP" else "DOWN", "components" to mapOf("db" to dbStatus)))
    }
}
