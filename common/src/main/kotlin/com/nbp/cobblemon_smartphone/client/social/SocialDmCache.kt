package com.nbp.cobblemon_smartphone.client.social

import com.nbp.cobblemon_smartphone.network.packet.MarkThreadReadPacket
import com.nbp.cobblemon_smartphone.network.packet.RequestThreadListPacket
import com.nbp.cobblemon_smartphone.network.packet.RequestThreadPagePacket
import com.nbp.cobblemon_smartphone.social.DmMessage
import com.nbp.cobblemon_smartphone.social.DmThreadSummary
import java.util.UUID

/**
 * Client-side view of DMs.
 *
 * [openThreadWith] is the reason no server-side session tracking is needed: the server pushes every
 * DM to online recipients and *this* is what decides whether the message lands in an open
 * conversation or turns into a notification.
 */
object SocialDmCache {
    private val threads = mutableListOf<DmThreadSummary>()
    private val messages = mutableListOf<DmMessage>()

    var unreadTotal: Int = 0
        private set
    var openThreadWith: UUID? = null
        private set
    var currentThreadName: String = ""
        private set
    var hasMore: Boolean = false
        private set
    var loading: Boolean = false
        private set
    var threadListHasMore: Boolean = false
        private set

    fun threads(): List<DmThreadSummary> = threads

    /** Messages of the open conversation, oldest-first. */
    fun messages(): List<DmMessage> = messages

    fun setUnreadTotal(total: Int) {
        unreadTotal = total.coerceAtLeast(0)
    }

    fun acceptThreadList(list: List<DmThreadSummary>, hasMore: Boolean, append: Boolean) {
        if (!append) threads.clear()
        val known = threads.mapTo(mutableSetOf()) { it.otherUuid }
        threads.addAll(list.filter { it.otherUuid !in known })
        threads.sortByDescending { it.lastTimestamp }
        threadListHasMore = hasMore
        loading = false
    }

    fun applySummary(summary: DmThreadSummary) {
        threads.removeIf { it.otherUuid == summary.otherUuid }
        threads.add(summary)
        threads.sortByDescending { it.lastTimestamp }
    }

    fun refreshThreads() {
        loading = true
        RequestThreadListPacket().sendToServer()
    }

    fun loadMoreThreads() {
        if (loading || !threadListHasMore) return
        val oldest = threads.lastOrNull() ?: return
        loading = true
        RequestThreadListPacket(oldest.lastTimestamp).sendToServer()
    }

    // --- Open conversation ---

    fun openThread(otherUuid: UUID, otherName: String) {
        openThreadWith = otherUuid
        currentThreadName = otherName
        messages.clear()
        hasMore = false
        loading = true
        RequestThreadPagePacket(otherUuid, 0L).sendToServer()
        MarkThreadReadPacket(otherUuid).sendToServer()
    }

    fun closeThread() {
        openThreadWith = null
        messages.clear()
        hasMore = false
    }

    fun acceptThreadPage(otherUuid: UUID, otherName: String, page: List<DmMessage>, hasMore: Boolean, append: Boolean) {
        // A page for a conversation we already navigated away from is stale.
        if (openThreadWith != otherUuid) return
        currentThreadName = otherName
        if (append) {
            val known = messages.mapTo(mutableSetOf()) { it.id }
            messages.addAll(0, page.filter { it.id !in known })
        } else {
            messages.clear()
            messages.addAll(page)
        }
        this.hasMore = hasMore
        this.loading = false
    }

    fun loadOlder() {
        val other = openThreadWith ?: return
        if (loading || !hasMore) return
        val oldest = messages.firstOrNull() ?: return
        loading = true
        RequestThreadPagePacket(other, oldest.id).sendToServer()
    }

    /** Returns true when the message belonged to the open conversation and was appended. */
    fun acceptIncoming(otherUuid: UUID, message: DmMessage): Boolean {
        if (openThreadWith != otherUuid) return false
        if (messages.none { it.id == message.id }) {
            messages.add(message)
        }
        MarkThreadReadPacket(otherUuid).sendToServer()
        return true
    }

    fun clear() {
        threads.clear()
        messages.clear()
        openThreadWith = null
        unreadTotal = 0
        hasMore = false
        loading = false
        threadListHasMore = false
    }
}
