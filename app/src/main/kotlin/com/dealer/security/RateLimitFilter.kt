package com.dealer.security

import com.dealer.config.RateLimitProperties
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil

@Component
class RateLimitFilter(
    private val rateLimitProperties: RateLimitProperties,
    private val keyResolver: RateLimitKeyResolver,
    private val objectMapper: ObjectMapper,
    redisTemplateProvider: ObjectProvider<StringRedisTemplate>,
) : OncePerRequestFilter() {
    private val log = LoggerFactory.getLogger(RateLimitFilter::class.java)
    private val redisTemplate = redisTemplateProvider.ifAvailable
    private val authBuckets = ConcurrentHashMap<String, Bucket>()
    private val privateApiBuckets = ConcurrentHashMap<String, Bucket>()
    private val publicApiBuckets = ConcurrentHashMap<String, Bucket>()

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        if (!rateLimitProperties.enabled) return true
        if (request.method == "OPTIONS") return true
        val path = request.requestURI ?: return true
        return path == "/health" ||
            path == "/ready" ||
            path.startsWith("/swagger-ui") ||
            path.startsWith("/v3/api-docs") ||
            path.startsWith("/actuator")
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val category = requestCategory(request.requestURI ?: "")
        if (category == RequestCategory.SKIP) {
            filterChain.doFilter(request, response)
            return
        }

        val decision = consume(category, request)

        response.setHeader("X-RateLimit-Remaining", decision.remainingTokens.toString())
        response.setHeader("X-RateLimit-Retry-After-Seconds", secondsUntilRefill(decision.retryAfterMillis).toString())

        if (!decision.allowed) {
            val retryAfter = secondsUntilRefill(decision.retryAfterMillis)
            response.status = 429
            response.contentType = MediaType.APPLICATION_JSON_VALUE
            response.setHeader("Retry-After", retryAfter.toString())
            response.writer.write(
                objectMapper.writeValueAsString(
                    mapOf(
                        "status" to 429,
                        "error" to "Too Many Requests",
                        "message" to "Rate limit exceeded",
                        "timestamp" to Instant.now().toString(),
                    ),
                ),
            )
            return
        }

        filterChain.doFilter(request, response)
    }

    private fun consume(
        category: RequestCategory,
        request: HttpServletRequest,
    ): RateLimitDecision {
        val limit = limitFor(category)
        val key = keyFor(category, request)

        if (redisTemplate != null) {
            return runCatching { consumeRedis(limit, key) }
                .onFailure { log.warn("Redis rate-limit failed, fallback to in-memory", it) }
                .getOrElse { consumeLocal(limit, category, request) }
        }
        return consumeLocal(limit, category, request)
    }

    private fun consumeRedis(
        limit: RateLimitProperties.Limit,
        key: String,
    ): RateLimitDecision {
        val template = redisTemplate ?: error("redisTemplate is null")
        val now = System.currentTimeMillis()
        val result =
            template.execute(
                REDIS_TOKEN_BUCKET_SCRIPT,
                listOf(key),
                limit.capacity.toString(),
                limit.refillTokens.toString(),
                limit.refillPeriod.toMillis().toString(),
                now.toString(),
                "1",
                limit.refillStrategy.name,
            )

        val allowed = ((result.getOrNull(0) as? Number)?.toLong() ?: 0L) == 1L
        val remaining = (result.getOrNull(1) as? Number)?.toLong() ?: 0L
        val retryAfterMs = (result.getOrNull(2) as? Number)?.toLong() ?: 0L
        return RateLimitDecision(allowed = allowed, remainingTokens = remaining, retryAfterMillis = retryAfterMs)
    }

    private fun consumeLocal(
        limit: RateLimitProperties.Limit,
        category: RequestCategory,
        request: HttpServletRequest,
    ): RateLimitDecision {
        val bucket =
            when (category) {
                RequestCategory.AUTH ->
                    authBuckets.computeIfAbsent("ip:${keyResolver.resolveIp(request)}:path:${request.requestURI}") { newBucket(limit) }
                RequestCategory.PRIVATE_API ->
                    privateApiBuckets.computeIfAbsent(keyResolver.resolvePrivateApiKey(request)) { newBucket(limit) }
                RequestCategory.PUBLIC_API ->
                    publicApiBuckets.computeIfAbsent("ip:${keyResolver.resolveIp(request)}") { newBucket(limit) }
                RequestCategory.SKIP -> error("SKIP must not be bucketed")
            }
        val probe = bucket.tryConsumeAndReturnRemaining(1)
        return RateLimitDecision(
            allowed = probe.isConsumed,
            remainingTokens = probe.remainingTokens,
            retryAfterMillis = probe.nanosToWaitForRefill / 1_000_000L,
        )
    }

    private fun keyFor(
        category: RequestCategory,
        request: HttpServletRequest,
    ): String =
        when (category) {
            RequestCategory.AUTH -> "rl:auth:ip:${keyResolver.resolveIp(request)}:path:${request.requestURI}"
            RequestCategory.PRIVATE_API -> "rl:private:${keyResolver.resolvePrivateApiKey(request)}"
            RequestCategory.PUBLIC_API -> "rl:public:ip:${keyResolver.resolveIp(request)}"
            RequestCategory.SKIP -> "rl:skip"
        }

    private fun limitFor(category: RequestCategory): RateLimitProperties.Limit =
        when (category) {
            RequestCategory.AUTH -> rateLimitProperties.auth
            RequestCategory.PRIVATE_API -> rateLimitProperties.privateApi
            RequestCategory.PUBLIC_API -> rateLimitProperties.publicApi
            RequestCategory.SKIP -> rateLimitProperties.publicApi
        }

    private fun newBucket(limit: RateLimitProperties.Limit): Bucket {
        val bandwidth =
            when (limit.refillStrategy) {
                RateLimitProperties.RefillStrategy.GREEDY ->
                    Bandwidth
                        .builder()
                        .capacity(limit.capacity)
                        .refillGreedy(limit.refillTokens, limit.refillPeriod)
                        .build()
                RateLimitProperties.RefillStrategy.INTERVALLY ->
                    Bandwidth
                        .builder()
                        .capacity(limit.capacity)
                        .refillIntervally(limit.refillTokens, limit.refillPeriod)
                        .build()
            }
        return Bucket.builder().addLimit(bandwidth).build()
    }

    private fun requestCategory(path: String): RequestCategory =
        when {
            path.startsWith("/api/v1/auth/") -> RequestCategory.AUTH
            path.startsWith("/api/v1/") -> RequestCategory.PRIVATE_API
            else -> RequestCategory.PUBLIC_API
        }

    private fun secondsUntilRefill(retryAfterMillis: Long): Long {
        if (retryAfterMillis <= 0) return 0
        return ceil(retryAfterMillis / 1000.0).toLong()
    }
}

private data class RateLimitDecision(
    val allowed: Boolean,
    val remainingTokens: Long,
    val retryAfterMillis: Long,
)

private val REDIS_TOKEN_BUCKET_SCRIPT =
    DefaultRedisScript(
        """
        local key = KEYS[1]
        local capacity = tonumber(ARGV[1])
        local refillTokens = tonumber(ARGV[2])
        local refillPeriodMs = tonumber(ARGV[3])
        local nowMs = tonumber(ARGV[4])
        local requested = tonumber(ARGV[5])
        local strategy = ARGV[6]

        local currentTokens = tonumber(redis.call('HGET', key, 'tokens'))
        local lastRefill = tonumber(redis.call('HGET', key, 'lastRefill'))

        if currentTokens == nil then
          currentTokens = capacity
          lastRefill = nowMs
        end

        local elapsed = nowMs - lastRefill
        if elapsed < 0 then elapsed = 0 end

        if strategy == 'INTERVALLY' then
          if elapsed >= refillPeriodMs then
            local periods = math.floor(elapsed / refillPeriodMs)
            currentTokens = math.min(capacity, currentTokens + periods * refillTokens)
            lastRefill = lastRefill + periods * refillPeriodMs
          end
        else
          local regenerated = math.floor((elapsed * refillTokens) / refillPeriodMs)
          if regenerated > 0 then
            currentTokens = math.min(capacity, currentTokens + regenerated)
            local passed = math.floor((regenerated * refillPeriodMs) / refillTokens)
            lastRefill = lastRefill + passed
          end
        end

        local allowed = 0
        local retryAfterMs = 0

        if currentTokens >= requested then
          currentTokens = currentTokens - requested
          allowed = 1
        else
          local needed = requested - currentTokens
          if strategy == 'INTERVALLY' then
            local intervalsNeeded = math.floor((needed + refillTokens - 1) / refillTokens)
            local progress = (nowMs - lastRefill) % refillPeriodMs
            local firstWait = refillPeriodMs - progress
            retryAfterMs = firstWait + (intervalsNeeded - 1) * refillPeriodMs
          else
            retryAfterMs = math.floor((needed * refillPeriodMs + refillTokens - 1) / refillTokens)
          end
        end

        redis.call('HSET', key, 'tokens', currentTokens, 'lastRefill', lastRefill)
        local ttlMs = math.max(refillPeriodMs * 2, 60000)
        redis.call('PEXPIRE', key, ttlMs)

        return {allowed, currentTokens, retryAfterMs}
        """.trimIndent(),
        List::class.java,
    )

private enum class RequestCategory {
    AUTH,
    PRIVATE_API,
    PUBLIC_API,
    SKIP,
}
