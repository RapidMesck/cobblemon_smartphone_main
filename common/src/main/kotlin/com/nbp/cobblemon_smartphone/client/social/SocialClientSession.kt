package com.nbp.cobblemon_smartphone.client.social

/** Server-provided Social rules plus lifecycle cleanup for client-only presentation state. */
object SocialClientSession {
    data class Capabilities(
        val enabled: Boolean = false,
        val callsEnabled: Boolean = false,
        val maxPostLength: Int = 280,
        val maxMessageLength: Int = 280,
        val postCooldownSeconds: Int = 30,
        val messageCooldownSeconds: Int = 1,
        val threadPageSize: Int = 30
    )

    var capabilities = Capabilities()
        private set

    fun apply(value: Capabilities) {
        capabilities = value
    }

    fun clear() {
        capabilities = Capabilities()
        SocialFeedCache.clear()
        SocialDmCache.clear()
        CallState.reset()
        SocialMute.set(false)
        MutedPlayers.set(emptyList())
        SocialMutationState.clear()
    }
}
