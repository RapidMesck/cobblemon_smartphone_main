package com.nbp.cobblemon_smartphone.client.social

import com.nbp.cobblemon_smartphone.social.SocialNotificationPrefs

/**
 * Client cache of this player's granular Social notification preferences (including Do Not Disturb).
 * Populated from the server on join and updated optimistically when toggled in the Notifications
 * Settings tab.
 */
object SocialMute {
    var prefs: SocialNotificationPrefs = SocialNotificationPrefs.DEFAULT
        private set

    val muted: Boolean
        get() = prefs.doNotDisturb

    fun setPrefs(value: SocialNotificationPrefs) {
        prefs = value
    }

    fun update(transform: (SocialNotificationPrefs) -> SocialNotificationPrefs): SocialNotificationPrefs {
        prefs = transform(prefs)
        return prefs
    }

    fun set(value: Boolean) {
        prefs = prefs.copy(doNotDisturb = value)
    }

    fun toggle(): Boolean {
        prefs = prefs.copy(doNotDisturb = !prefs.doNotDisturb)
        return prefs.doNotDisturb
    }

    fun reset() {
        prefs = SocialNotificationPrefs.DEFAULT
    }
}
