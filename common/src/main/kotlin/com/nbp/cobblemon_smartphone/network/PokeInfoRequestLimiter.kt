package com.nbp.cobblemon_smartphone.network

import java.util.UUID

/** Small per-player server-side guard against packet spam; it does not cache PokeInfo data. */
class PokeInfoRequestLimiter(private val intervalMillis: Long) {
    private val lastRequest = mutableMapOf<UUID, Long>()

    @Synchronized
    fun tryAcquire(playerId: UUID, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val previous = lastRequest[playerId]
        if (previous != null && nowMillis - previous < intervalMillis) return false
        lastRequest[playerId] = nowMillis
        if (lastRequest.size > 512) {
            val expiry = nowMillis - 10 * 60 * 1000L
            lastRequest.entries.removeIf { it.value < expiry }
        }
        return true
    }
}
