package com.nbp.cobblemon_smartphone.util

import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerPlayer

/**
 * Persists a player's chosen smartphone theme using the [PreferencesSaver] mixin,
 * so it survives across sessions and world joins.
 */
object ThemeStorage {
    fun read(player: ServerPlayer): String {
        val prefs = (player as PreferencesSaver).`cobblemonsmartphone$getSavedPreferences`() ?: return ""
        if (prefs.contains(PreferencesSaver.THEME_KEY, CompoundTag.TAG_STRING.toInt())) {
            val theme = prefs.getString(PreferencesSaver.THEME_KEY)
            if (theme.isNotBlank()) return theme
        }
        if (prefs.contains(PreferencesSaver.WALLPAPER_KEY, CompoundTag.TAG_STRING.toInt())) {
            val wp = prefs.getString(PreferencesSaver.WALLPAPER_KEY)
            if (wp.isNotBlank()) return wp
        }
        return ""
    }

    fun write(player: ServerPlayer, themeId: String) {
        val prefs = (player as PreferencesSaver).`cobblemonsmartphone$getSavedPreferences`() ?: return
        prefs.putString(PreferencesSaver.THEME_KEY, themeId)
        prefs.putString(PreferencesSaver.WALLPAPER_KEY, themeId)
    }
}
