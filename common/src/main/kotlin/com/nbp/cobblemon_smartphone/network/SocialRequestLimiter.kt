package com.nbp.cobblemon_smartphone.network

import java.util.UUID

/** Lightweight server-side network limits. Gameplay cooldowns remain separate. */
object SocialRequestLimiter {
    enum class Action(val minimumIntervalMs: Long) {
        FEED_PAGE(250),
        THREAD_LIST(350),
        THREAD_PAGE(200),
        MARK_READ(150),
        LIKE(150),
        MUTE(250),
        CALL_START(1_500),
        CALL_ACTION(150)
    }

    private data class Key(val player: UUID, val action: Action)
    private val lastAccepted = mutableMapOf<Key, Long>()

    @Synchronized
    fun allow(player: UUID, action: Action, now: Long = System.currentTimeMillis()): Boolean {
        val key = Key(player, action)
        val previous = lastAccepted[key]
        if (previous != null && now - previous < action.minimumIntervalMs) return false
        lastAccepted[key] = now
        return true
    }

    @Synchronized
    fun clear(player: UUID) {
        lastAccepted.keys.removeIf { it.player == player }
    }

    @Synchronized
    fun clearAll() = lastAccepted.clear()
}
