package com.nbp.cobblemon_smartphone.client.social

import com.nbp.cobblemon_smartphone.network.packet.RequestFeedPagePacket
import com.nbp.cobblemon_smartphone.social.SocialPostView

/**
 * Client-side view of the feed. Filled by [com.nbp.cobblemon_smartphone.network.handler.FeedPageHandler];
 * the screen only reads from here so it survives being closed and reopened.
 */
object SocialFeedCache {
    private val posts = mutableListOf<SocialPostView>()

    var hasMore: Boolean = false
        private set
    var loading: Boolean = false
        private set

    fun posts(): List<SocialPostView> = posts

    fun isEmpty(): Boolean = posts.isEmpty()

    fun accept(page: List<SocialPostView>, hasMore: Boolean, append: Boolean) {
        if (append) {
            // Guard against a duplicate page arriving twice (double request, resend).
            val known = posts.mapTo(mutableSetOf()) { it.id }
            posts.addAll(page.filter { it.id !in known })
        } else {
            posts.clear()
            posts.addAll(page)
        }
        this.hasMore = hasMore
        this.loading = false
    }

    /** Requests the newest page, replacing whatever is cached. */
    fun refresh() {
        loading = true
        RequestFeedPagePacket(0L).sendToServer()
    }

    /** Requests the next page of older posts. No-op while a request is already in flight. */
    fun loadMore() {
        if (loading || !hasMore) return
        val oldest = posts.lastOrNull() ?: return refresh()
        loading = true
        RequestFeedPagePacket(oldest.id).sendToServer()
    }

    /**
     * Optimistic local like toggle so the heart reacts instantly; the server is the source of
     * truth and the next page load reconciles.
     */
    fun toggleLikeLocally(postId: Long): Boolean? {
        val index = posts.indexOfFirst { it.id == postId }
        if (index == -1) return null
        val post = posts[index]
        val liked = !post.likedByMe
        posts[index] = post.copy(
            likedByMe = liked,
            likeCount = (post.likeCount + if (liked) 1 else -1).coerceAtLeast(0)
        )
        return liked
    }

    fun applyUpdate(deletedId: Long, post: SocialPostView?) {
        if (deletedId > 0) posts.removeIf { it.id == deletedId }
        if (post != null) {
            val index = posts.indexOfFirst { it.id == post.id }
            if (index >= 0) posts[index] = post else posts.add(0, post)
        }
    }

    fun clear() {
        posts.clear()
        hasMore = false
        loading = false
    }
}
