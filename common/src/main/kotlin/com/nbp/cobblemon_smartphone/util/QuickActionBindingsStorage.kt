package com.nbp.cobblemon_smartphone.util

import net.minecraft.server.level.ServerPlayer

private const val ENTRY_DELIMITER = ";"
private const val KEY_VALUE_DELIMITER = ":"

/**
 * Persists a player's quick action hotkey bindings (slot index -> action id) using the existing
 * [PreferencesSaver] mixin, the same way [ActionOrderStorage] persists the action order.
 */
object QuickActionBindingsStorage {
    fun read(player: ServerPlayer): Map<Int, String> {
        val prefs = (player as PreferencesSaver).`cobblemonsmartphone$getSavedPreferences`()
        val raw = prefs.getString(PreferencesSaver.QUICK_ACTIONS_KEY)
        if (raw.isBlank()) return emptyMap()
        return raw.split(ENTRY_DELIMITER)
            .filter { it.isNotBlank() }
            .mapNotNull { entry ->
                val parts = entry.split(KEY_VALUE_DELIMITER, limit = 2)
                val slot = parts.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
                val actionId = parts.getOrNull(1) ?: return@mapNotNull null
                slot to actionId
            }
            .toMap()
    }

    fun write(player: ServerPlayer, bindings: Map<Int, String>) {
        val prefs = (player as PreferencesSaver).`cobblemonsmartphone$getSavedPreferences`()
        val raw = bindings.entries.joinToString(ENTRY_DELIMITER) { (slot, actionId) -> "$slot$KEY_VALUE_DELIMITER$actionId" }
        prefs.putString(PreferencesSaver.QUICK_ACTIONS_KEY, raw)
    }
}
