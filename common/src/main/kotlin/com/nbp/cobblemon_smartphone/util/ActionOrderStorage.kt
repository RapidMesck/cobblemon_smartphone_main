package com.nbp.cobblemon_smartphone.util

import net.minecraft.server.level.ServerPlayer

private const val ORDER_DELIMITER = ","

/**
 * Persists a player's preferred smartphone action order using the existing
 * [PreferencesSaver] mixin (backed by [net.minecraft.world.entity.Entity.saveWithoutId]/`load`),
 * so it survives across sessions without needing a separate SavedData store.
 */
object ActionOrderStorage {
    fun read(player: ServerPlayer): List<String> {
        val prefs = (player as PreferencesSaver).`cobblemonsmartphone$getSavedPreferences`()
        val raw = prefs.getString(PreferencesSaver.ACTION_ORDER_KEY)
        if (raw.isBlank()) return emptyList()
        return raw.split(ORDER_DELIMITER).filter { it.isNotBlank() }
    }

    fun write(player: ServerPlayer, order: List<String>) {
        val prefs = (player as PreferencesSaver).`cobblemonsmartphone$getSavedPreferences`()
        prefs.putString(PreferencesSaver.ACTION_ORDER_KEY, order.joinToString(ORDER_DELIMITER))
    }
}
