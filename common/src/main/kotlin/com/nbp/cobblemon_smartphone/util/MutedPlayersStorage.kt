package com.nbp.cobblemon_smartphone.util

import net.minecraft.nbt.ListTag
import net.minecraft.nbt.NbtUtils
import net.minecraft.nbt.Tag
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

/**
 * Persists the set of players a given player has individually muted, using the same
 * [PreferencesSaver] mixin as the global Do Not Disturb flag. Muting a player suppresses their DM
 * alerts and blocks their calls, on top of (and independent from) the global mute.
 */
object MutedPlayersStorage {
    fun read(player: ServerPlayer): Set<UUID> {
        val prefs = (player as PreferencesSaver).`cobblemonsmartphone$getSavedPreferences`()
        return prefs.getList(PreferencesSaver.MUTED_PLAYERS_KEY, Tag.TAG_INT_ARRAY.toInt())
            .mapNotNull { runCatching { NbtUtils.loadUUID(it) }.getOrNull() }
            .toSet()
    }

    fun write(player: ServerPlayer, players: Set<UUID>) {
        val prefs = (player as PreferencesSaver).`cobblemonsmartphone$getSavedPreferences`()
        val list = ListTag()
        players.forEach { list.add(NbtUtils.createUUID(it)) }
        prefs.put(PreferencesSaver.MUTED_PLAYERS_KEY, list)
    }

    fun setMuted(player: ServerPlayer, target: UUID, muted: Boolean) {
        val current = read(player).toMutableSet()
        val changed = if (muted) current.add(target) else current.remove(target)
        if (changed) write(player, current)
    }

    fun isMuted(player: ServerPlayer, target: UUID): Boolean = read(player).contains(target)
}
