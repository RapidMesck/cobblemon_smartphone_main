package com.nbp.cobblemon_smartphone.util

import com.nbp.cobblemon_smartphone.social.SocialNotificationPrefs
import net.minecraft.server.level.ServerPlayer

/**
 * Persists a player's granular Social notification preferences (including Do Not Disturb)
 * using the [PreferencesSaver] mixin. Falls back to the legacy [PreferencesSaver.SOCIAL_MUTED_KEY]
 * boolean when reading older worlds.
 */
object SocialMuteStorage {
    fun readPrefs(player: ServerPlayer): SocialNotificationPrefs {
        val prefs = (player as PreferencesSaver).`cobblemonsmartphone$getSavedPreferences`()
        return if (prefs.contains(PreferencesSaver.SOCIAL_NOTIF_PREFS_KEY)) {
            SocialNotificationPrefs.fromBitmask(prefs.getInt(PreferencesSaver.SOCIAL_NOTIF_PREFS_KEY))
        } else {
            SocialNotificationPrefs(doNotDisturb = prefs.getBoolean(PreferencesSaver.SOCIAL_MUTED_KEY))
        }
    }

    fun writePrefs(player: ServerPlayer, settings: SocialNotificationPrefs) {
        val prefs = (player as PreferencesSaver).`cobblemonsmartphone$getSavedPreferences`()
        prefs.putInt(PreferencesSaver.SOCIAL_NOTIF_PREFS_KEY, settings.toBitmask())
        prefs.putBoolean(PreferencesSaver.SOCIAL_MUTED_KEY, settings.doNotDisturb)
    }

    fun read(player: ServerPlayer): Boolean = readPrefs(player).doNotDisturb

    fun write(player: ServerPlayer, muted: Boolean) {
        writePrefs(player, readPrefs(player).copy(doNotDisturb = muted))
    }
}
