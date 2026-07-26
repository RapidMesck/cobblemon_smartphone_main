package com.nbp.cobblemon_smartphone.client.social

/**
 * Client cache of this player's Social "Do Not Disturb" flag. Populated from the server on join and
 * updated optimistically when toggled from the Social header. Read by the DM notification path to
 * suppress alerts (the unread badge still updates) — incoming calls are blocked server-side.
 */
object SocialMute {
    var muted: Boolean = false
        private set

    fun set(value: Boolean) {
        muted = value
    }

    fun toggle(): Boolean {
        muted = !muted
        return muted
    }
}
