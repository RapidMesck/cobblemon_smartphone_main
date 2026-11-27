package com.nbp.cobblemon_smartphone.client.social

import com.nbp.cobblemon_smartphone.network.packet.RequestProfileListPacket
import com.nbp.cobblemon_smartphone.network.packet.RequestProfilePacket
import com.nbp.cobblemon_smartphone.social.SocialPostView
import com.nbp.cobblemon_smartphone.social.SocialProfileView
import com.nbp.cobblemon_smartphone.social.SocialTrainerSummary
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object SocialProfileCache {
    private data class ListKey(val uuid: UUID, val type: RequestProfileListPacket.ListType)

    private val profiles = ConcurrentHashMap<UUID, SocialProfileView>()
    private val postsByProfile = ConcurrentHashMap<UUID, List<SocialPostView>>()
    private val listeners = ConcurrentHashMap<UUID, (SocialProfileView, List<SocialPostView>) -> Unit>()
    private val loading = ConcurrentHashMap.newKeySet<UUID>()

    private val trainerLists = ConcurrentHashMap<ListKey, List<SocialTrainerSummary>>()
    private val loadingLists = ConcurrentHashMap.newKeySet<ListKey>()
    private val directoryUuid = UUID(0L, 0L)

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

    /** Keeps cached profile post lists in sync when a post is created, edited, liked, voted, or deleted. */
    fun applyPostUpdate(deletedId: Long, post: SocialPostView?) {
        if (deletedId > 0L) {
            postsByProfile.keys.toList().forEach { ownerUuid ->
                val current = postsByProfile[ownerUuid] ?: return@forEach
                val filtered = current.filterNot { it.id == deletedId }
                if (filtered.size != current.size) {
                    postsByProfile[ownerUuid] = filtered
                    profiles[ownerUuid]?.let { p ->
                        val updatedProfile = p.copy(
                            postCount = (p.postCount - 1).coerceAtLeast(0),
                            pinnedPostId = if (p.pinnedPostId == deletedId) 0L else p.pinnedPostId
                        )
                        profiles[ownerUuid] = updatedProfile
                    }
                }
            }
        }
        if (post != null) {
            val current = postsByProfile[post.authorUuid] ?: return
            val idx = current.indexOfFirst { it.id == post.id }
            if (idx >= 0) {
                val updated = current.toMutableList()
                updated[idx] = post
                postsByProfile[post.authorUuid] = updated
            }
        }
    }

    fun toggleLikeLocally(authorUuid: UUID, postId: Long): Boolean? {
        val current = postsByProfile[authorUuid] ?: return null
        val idx = current.indexOfFirst { it.id == postId }
        if (idx < 0) return null
        val post = current[idx]
        val nowLiked = !post.likedByMe
        val updated = current.toMutableList()
        updated[idx] = post.copy(
            likedByMe = nowLiked,
            likeCount = (post.likeCount + if (nowLiked) 1 else -1).coerceAtLeast(0)
        )
        postsByProfile[authorUuid] = updated
        return nowLiked
    }

    fun requestList(targetUuid: UUID, type: RequestProfileListPacket.ListType) {
        val key = ListKey(if (type == RequestProfileListPacket.ListType.DIRECTORY) directoryUuid else targetUuid, type)
        loadingLists.add(key)
        RequestProfileListPacket(targetUuid, type).sendToServer()
    }

    fun requestDirectory() {
        requestList(directoryUuid, RequestProfileListPacket.ListType.DIRECTORY)
    }

    fun acceptList(targetUuid: UUID, type: RequestProfileListPacket.ListType, trainers: List<SocialTrainerSummary>) {
        val key = ListKey(if (type == RequestProfileListPacket.ListType.DIRECTORY) directoryUuid else targetUuid, type)
        loadingLists.remove(key)
        trainerLists[key] = trainers
    }

    fun getList(targetUuid: UUID, type: RequestProfileListPacket.ListType): List<SocialTrainerSummary> {
        val key = ListKey(if (type == RequestProfileListPacket.ListType.DIRECTORY) directoryUuid else targetUuid, type)
        return trainerLists[key] ?: emptyList()
    }

    fun getDirectory(): List<SocialTrainerSummary> =
        getList(directoryUuid, RequestProfileListPacket.ListType.DIRECTORY)

    fun isListLoading(targetUuid: UUID, type: RequestProfileListPacket.ListType): Boolean {
        val key = ListKey(if (type == RequestProfileListPacket.ListType.DIRECTORY) directoryUuid else targetUuid, type)
        return loadingLists.contains(key)
    }

    fun isDirectoryLoading(): Boolean =
        isListLoading(directoryUuid, RequestProfileListPacket.ListType.DIRECTORY)

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
        trainerLists.clear()
        loadingLists.clear()
    }
}
