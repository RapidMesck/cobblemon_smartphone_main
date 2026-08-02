package com.nbp.cobblemon_smartphone.util

import net.minecraft.server.level.ServerPlayer

private const val HIDDEN_DELIMITER = ","

/**
 * Persists the action ids the player chose to hide from the smartphone home screen, using the
 * existing [PreferencesSaver] mixin (backed by [net.minecraft.world.entity.Entity.saveWithoutId]/`load`),
 * the same way [ActionOrderStorage] persists the action order.
 */
object HiddenActionsStorage {
    fun read(player: ServerPlayer): List<String> {
        val prefs = (player as PreferencesSaver).`cobblemonsmartphone$getSavedPreferences`()
        val raw = prefs.getString(PreferencesSaver.HIDDEN_ACTIONS_KEY)
        if (raw.isBlank()) return emptyList()
        return raw.split(HIDDEN_DELIMITER).filter { it.isNotBlank() }
    }

    fun write(player: ServerPlayer, hidden: List<String>) {
        val prefs = (player as PreferencesSaver).`cobblemonsmartphone$getSavedPreferences`()
        prefs.putString(PreferencesSaver.HIDDEN_ACTIONS_KEY, hidden.joinToString(HIDDEN_DELIMITER))
    }
}
