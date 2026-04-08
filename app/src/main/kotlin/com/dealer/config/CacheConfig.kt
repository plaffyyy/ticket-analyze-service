package com.dealer.config

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.cache.Cache
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.interceptor.CacheErrorHandler
import org.springframework.cache.support.NoOpCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.cache.RedisCacheConfiguration
import org.springframework.data.redis.cache.RedisCacheManager
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializationContext
import org.springframework.data.redis.serializer.StringRedisSerializer

@Configuration
@EnableCaching
class CacheConfig {
    @Bean
    @ConditionalOnProperty(prefix = "app.cache", name = ["enabled"], havingValue = "true")
    fun cacheManager(
        redisConnectionFactory: RedisConnectionFactory,
        objectMapper: ObjectMapper,
        cacheProperties: CacheProperties,
    ): CacheManager {
        val redisSerializer =
            GenericJackson2JsonRedisSerializer
                .builder()
                .objectMapper(objectMapper.copy().findAndRegisterModules())
                .defaultTyping(true)
                .build()

        val defaultConfig =
            RedisCacheConfiguration
                .defaultCacheConfig()
                .disableCachingNullValues()
                .computePrefixWith { "dealer::$it::" }
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(redisSerializer))

        val cacheConfigs =
            mapOf(
                CacheNames.CURRENT_USER to defaultConfig.entryTtl(cacheProperties.ttl.currentUser),
                CacheNames.GROUP to defaultConfig.entryTtl(cacheProperties.ttl.group),
                CacheNames.GROUP_BALANCE to defaultConfig.entryTtl(cacheProperties.ttl.groupBalance),
                CacheNames.BILL to defaultConfig.entryTtl(cacheProperties.ttl.bill),
                CacheNames.GROUP_BILLS to defaultConfig.entryTtl(cacheProperties.ttl.groupBills),
            )

        return RedisCacheManager
            .builder(redisConnectionFactory)
            .cacheDefaults(defaultConfig)
            .withInitialCacheConfigurations(cacheConfigs)
            .transactionAware()
            .disableCreateOnMissingCache()
            .build()
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.cache", name = ["enabled"], havingValue = "false", matchIfMissing = true)
    fun noOpCacheManager(): CacheManager = NoOpCacheManager()

    @Bean
    fun cacheErrorHandler(): CacheErrorHandler =
        object : CacheErrorHandler {
            private val log = LoggerFactory.getLogger(CacheErrorHandler::class.java)

            override fun handleCacheGetError(
                exception: RuntimeException,
                cache: Cache,
                key: Any,
            ) {
                log.warn("Cache get failed for cache={} key={}", cache.name, key, exception)
            }

            override fun handleCachePutError(
                exception: RuntimeException,
                cache: Cache,
                key: Any,
                value: Any?,
            ) {
                log.warn("Cache put failed for cache={} key={}", cache.name, key, exception)
            }

            override fun handleCacheEvictError(
                exception: RuntimeException,
                cache: Cache,
                key: Any,
            ) {
                log.warn("Cache evict failed for cache={} key={}", cache.name, key, exception)
            }

            override fun handleCacheClearError(
                exception: RuntimeException,
                cache: Cache,
            ) {
                log.warn("Cache clear failed for cache={}", cache.name, exception)
            }
        }
}
