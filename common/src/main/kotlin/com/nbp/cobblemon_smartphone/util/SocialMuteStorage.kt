package com.nbp.cobblemon_smartphone.util

import net.minecraft.server.level.ServerPlayer

/**
 * Persists a player's "Do Not Disturb" flag for the Social app using the same [PreferencesSaver]
 * mixin as the action order and quick action bindings. Defaults to false (not muted).
 */
object SocialMuteStorage {
    fun read(player: ServerPlayer): Boolean {
        val prefs = (player as PreferencesSaver).`cobblemonsmartphone$getSavedPreferences`()
        return prefs.getBoolean(PreferencesSaver.SOCIAL_MUTED_KEY)
    }

    fun write(player: ServerPlayer, muted: Boolean) {
        val prefs = (player as PreferencesSaver).`cobblemonsmartphone$getSavedPreferences`()
        prefs.putBoolean(PreferencesSaver.SOCIAL_MUTED_KEY, muted)
    }
}
