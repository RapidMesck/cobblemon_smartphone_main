package com.nbp.cobblemon_smartphone.client.social

import java.util.UUID

/**
 * Client cache of the players this player has individually muted. Synced from the server on join
 * and updated optimistically when a bell is toggled in a DM thread.
 */
object MutedPlayers {
    private val muted = mutableSetOf<UUID>()

    fun set(players: Collection<UUID>) {
        muted.clear()
        muted.addAll(players)
    }

    fun contains(uuid: UUID): Boolean = muted.contains(uuid)

    /** Flips the mute state for [uuid] and returns the new state (true = now muted). */
    fun toggle(uuid: UUID): Boolean {
        val nowMuted = !muted.contains(uuid)
        if (nowMuted) muted.add(uuid) else muted.remove(uuid)
        return nowMuted
    }
}
