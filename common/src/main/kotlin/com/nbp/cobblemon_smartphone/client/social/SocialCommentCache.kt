package com.nbp.cobblemon_smartphone.client.social

import com.nbp.cobblemon_smartphone.network.packet.RequestCommentsPacket
import com.nbp.cobblemon_smartphone.social.SocialCommentView

object SocialCommentCache {
    private val commentsByPost = mutableMapOf<Long, MutableList<SocialCommentView>>()
    private val listeners = mutableMapOf<Long, (List<SocialCommentView>) -> Unit>()
    private val loading = mutableSetOf<Long>()

    fun commentsFor(postId: Long): List<SocialCommentView> = commentsByPost[postId] ?: emptyList()

    fun isLoading(postId: Long): Boolean = loading.contains(postId)

    fun request(postId: Long) {
        loading.add(postId)
        RequestCommentsPacket(postId).sendToServer()
    }

    fun accept(postId: Long, comments: List<SocialCommentView>) {
        loading.remove(postId)
        commentsByPost[postId] = comments.toMutableList()
        listeners[postId]?.invoke(comments)
    }

    fun registerListener(postId: Long, listener: (List<SocialCommentView>) -> Unit) {
        listeners[postId] = listener
    }

    fun unregisterListener(postId: Long) {
        listeners.remove(postId)
    }

    fun clear() {
        commentsByPost.clear()
        listeners.clear()
        loading.clear()
    }
}
