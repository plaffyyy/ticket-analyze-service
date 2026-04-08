package com.dealer.api.controller

import com.dealer.config.CacheProperties
import org.springframework.beans.factory.ObjectProvider
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class HealthController(
    private val jdbcTemplate: JdbcTemplate,
    private val redisTemplateProvider: ObjectProvider<StringRedisTemplate>,
    private val cacheProperties: CacheProperties,
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

        val components = mutableMapOf<String, String>()
        components["db"] = dbStatus

        val redisStatus =
            if (cacheProperties.enabled) {
                runCatching {
                    val pong = redisTemplateProvider.ifAvailable?.execute { connection -> connection.ping() }
                    if (pong == "PONG") "UP" else "DOWN"
                }.getOrElse { "DOWN" }
            } else {
                null
            }

        if (redisStatus != null) {
            components["redis"] = redisStatus
        }

        val allUp = components.values.all { it == "UP" }
        return ResponseEntity
            .status(if (allUp) 200 else 503)
            .body(mapOf("status" to if (allUp) "UP" else "DOWN", "components" to components))
    }
}
