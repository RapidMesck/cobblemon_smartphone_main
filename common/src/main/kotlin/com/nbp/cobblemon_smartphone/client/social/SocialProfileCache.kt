package com.nbp.cobblemon_smartphone.client.social

import com.nbp.cobblemon_smartphone.network.packet.RequestProfilePacket
import com.nbp.cobblemon_smartphone.social.SocialPostView
import com.nbp.cobblemon_smartphone.social.SocialProfileView
import java.util.UUID

object SocialProfileCache {
    private val profiles = mutableMapOf<UUID, SocialProfileView>()
    private val postsByProfile = mutableMapOf<UUID, List<SocialPostView>>()
    private val listeners = mutableMapOf<UUID, (SocialProfileView, List<SocialPostView>) -> Unit>()
    private val loading = mutableSetOf<UUID>()

    fun getProfile(uuid: UUID): SocialProfileView? = profiles[uuid]

    fun getPosts(uuid: UUID): List<SocialPostView> = postsByProfile[uuid] ?: emptyList()

    fun isLoading(uuid: UUID): Boolean = loading.contains(uuid)

    fun request(uuid: UUID) {
        loading.add(uuid)
        RequestProfilePacket(uuid).sendToServer()
    }

    fun accept(profile: SocialProfileView, posts: List<SocialPostView>) {
        loading.remove(profile.uuid)
        profiles[profile.uuid] = profile
        postsByProfile[profile.uuid] = posts
        listeners[profile.uuid]?.invoke(profile, posts)
    }

    fun registerListener(uuid: UUID, listener: (SocialProfileView, List<SocialPostView>) -> Unit) {
        listeners[uuid] = listener
    }

    fun unregisterListener(uuid: UUID) {
        listeners.remove(uuid)
    }

    fun clear() {
        profiles.clear()
        postsByProfile.clear()
        listeners.clear()
        loading.clear()
    }
}
