package com.dealer.support.cache

import org.springframework.cache.CacheManager
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class CacheSupport(
    private val cacheManager: CacheManager,
) {
    fun <T : Any> getOrLoad(
        cacheName: String,
        key: UUID,
        loader: () -> T,
    ): T {
        runCatching {
            @Suppress("UNCHECKED_CAST")
            (cacheManager.getCache(cacheName)?.get(key)?.get() as? T)?.let { return it }
        }

        val value = loader()
        runCatching { cacheManager.getCache(cacheName)?.put(key, value) }
        return value
    }

    fun evict(
        cacheName: String,
        key: UUID,
    ) {
        runCatching { cacheManager.getCache(cacheName)?.evict(key) }
    }
}
