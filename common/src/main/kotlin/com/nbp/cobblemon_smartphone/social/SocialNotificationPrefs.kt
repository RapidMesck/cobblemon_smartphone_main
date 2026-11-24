package com.nbp.cobblemon_smartphone.social

/**
 * Granular per-player notification preferences for the Social app.
 *
 * Serialized compactly as an integer bitmask so it persists cleanly in player NBT
 * ([com.nbp.cobblemon_smartphone.util.PreferencesSaver.SOCIAL_NOTIF_PREFS_KEY]) and
 * syncs over the existing Social mute packets.
 */
data class SocialNotificationPrefs(
    val doNotDisturb: Boolean = false,
    val dms: Boolean = true,
    val calls: Boolean = true,
    val likes: Boolean = true,
    val comments: Boolean = true,
    val follows: Boolean = true,
    val mentions: Boolean = true,
    val sounds: Boolean = true
) {
    fun allowsCalls(): Boolean = !doNotDisturb && calls

    fun allowsDmAlert(): Boolean = !doNotDisturb && dms

    fun allowsSound(): Boolean = !doNotDisturb && sounds

    fun allowsNotification(type: SocialNotificationType): Boolean {
        if (doNotDisturb) return false
        return when (type) {
            SocialNotificationType.LIKE -> likes
            SocialNotificationType.COMMENT -> comments
            SocialNotificationType.FOLLOW -> follows
            SocialNotificationType.MENTION -> mentions
        }
    }

    fun toBitmask(): Int {
        var mask = 0
        if (doNotDisturb) mask = mask or BIT_DND
        if (dms) mask = mask or BIT_DMS
        if (calls) mask = mask or BIT_CALLS
        if (likes) mask = mask or BIT_LIKES
        if (comments) mask = mask or BIT_COMMENTS
        if (follows) mask = mask or BIT_FOLLOWS
        if (mentions) mask = mask or BIT_MENTIONS
        if (sounds) mask = mask or BIT_SOUNDS
        return mask
    }

    companion object {
        private const val BIT_DND = 1 shl 0
        private const val BIT_DMS = 1 shl 1
        private const val BIT_CALLS = 1 shl 2
        private const val BIT_LIKES = 1 shl 3
        private const val BIT_COMMENTS = 1 shl 4
        private const val BIT_FOLLOWS = 1 shl 5
        private const val BIT_MENTIONS = 1 shl 6
        private const val BIT_SOUNDS = 1 shl 7

        val DEFAULT = SocialNotificationPrefs()

        fun fromBitmask(mask: Int): SocialNotificationPrefs = SocialNotificationPrefs(
            doNotDisturb = (mask and BIT_DND) != 0,
            dms = (mask and BIT_DMS) != 0,
            calls = (mask and BIT_CALLS) != 0,
            likes = (mask and BIT_LIKES) != 0,
            comments = (mask and BIT_COMMENTS) != 0,
            follows = (mask and BIT_FOLLOWS) != 0,
            mentions = (mask and BIT_MENTIONS) != 0,
            sounds = (mask and BIT_SOUNDS) != 0
        )
    }
}
