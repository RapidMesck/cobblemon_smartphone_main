package com.nbp.cobblemon_smartphone.network.packet

import com.nbp.cobblemon_smartphone.social.SocialNotificationView
import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.network.RegistryFriendlyByteBuf

class RequestNotificationsPacket : CobblemonSmartphoneNetworkPacket<RequestNotificationsPacket> {
    companion object {
        val ID = smartphoneResource("request_notifications")
        fun decode(buffer: RegistryFriendlyByteBuf) = RequestNotificationsPacket()
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {}
}

class NotificationListPacket(
    val notifications: List<SocialNotificationView>
) : CobblemonSmartphoneNetworkPacket<NotificationListPacket> {
    companion object {
        val ID = smartphoneResource("notification_list")
        fun decode(buffer: RegistryFriendlyByteBuf): NotificationListPacket {
            val count = buffer.readVarInt()
            val list = (0 until count).map { SocialNotificationView.decode(buffer) }
            return NotificationListPacket(list)
        }
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeVarInt(notifications.size)
        notifications.forEach { it.encode(buffer) }
    }
}

class PushNotificationPacket(
    val notification: SocialNotificationView
) : CobblemonSmartphoneNetworkPacket<PushNotificationPacket> {
    companion object {
        val ID = smartphoneResource("push_notification")
        fun decode(buffer: RegistryFriendlyByteBuf) = PushNotificationPacket(SocialNotificationView.decode(buffer))
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        notification.encode(buffer)
    }
}

class MarkNotificationReadPacket(
    val notificationId: Long
) : CobblemonSmartphoneNetworkPacket<MarkNotificationReadPacket> {
    companion object {
        val ID = smartphoneResource("mark_notification_read")
        fun decode(buffer: RegistryFriendlyByteBuf) = MarkNotificationReadPacket(buffer.readVarLong())
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeVarLong(notificationId)
    }
}
