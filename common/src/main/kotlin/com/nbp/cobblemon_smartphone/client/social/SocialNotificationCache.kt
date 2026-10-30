package com.nbp.cobblemon_smartphone.client.social

import com.nbp.cobblemon_smartphone.network.packet.MarkNotificationReadPacket
import com.nbp.cobblemon_smartphone.network.packet.RequestNotificationsPacket
import com.nbp.cobblemon_smartphone.social.SocialNotificationView

object SocialNotificationCache {
    private val notifications = mutableListOf<SocialNotificationView>()
    private val listeners = mutableListOf<(List<SocialNotificationView>) -> Unit>()
    var unreadCount: Int = 0
        private set
    var loading: Boolean = false
        private set

    fun notifications(): List<SocialNotificationView> = notifications

    fun refresh() {
        loading = true
        RequestNotificationsPacket().sendToServer()
    }

    fun acceptList(list: List<SocialNotificationView>) {
        loading = false
        notifications.clear()
        notifications.addAll(list)
        unreadCount = notifications.count { !it.read }
        notifyListeners()
    }

    fun markAllRead() = markRead(0L)

    fun push(notif: SocialNotificationView) {
        notifications.add(0, notif)
        if (!notif.read) unreadCount++
        notifyListeners()
    }

    fun markRead(id: Long) {
        if (id == 0L) {
            notifications.indices.forEach { i ->
                notifications[i] = notifications[i].copy(read = true)
            }
            unreadCount = 0
        } else {
            val index = notifications.indexOfFirst { it.id == id }
            if (index >= 0 && !notifications[index].read) {
                notifications[index] = notifications[index].copy(read = true)
                unreadCount = (unreadCount - 1).coerceAtLeast(0)
            }
        }
        MarkNotificationReadPacket(id).sendToServer()
        notifyListeners()
    }

    fun registerListener(listener: (List<SocialNotificationView>) -> Unit) {
        listeners.add(listener)
    }

    fun unregisterListener(listener: (List<SocialNotificationView>) -> Unit) {
        listeners.remove(listener)
    }

    private fun notifyListeners() {
        listeners.forEach { it.invoke(notifications) }
    }

    fun clear() {
        notifications.clear()
        listeners.clear()
        unreadCount = 0
    }
}
