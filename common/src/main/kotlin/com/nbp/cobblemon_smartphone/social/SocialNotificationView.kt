package com.nbp.cobblemon_smartphone.social

import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.server.MinecraftServer
import java.util.UUID

/**
 * Client projection of a [SocialNotification].
 */
data class SocialNotificationView(
    val id: Long,
    val actorUuid: UUID,
    val actorName: String,
    val type: SocialNotificationType,
    val referenceId: Long,
    val message: String,
    val timestamp: Long,
    val read: Boolean,
    val skinUrl: String? = null
) {
    fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeVarLong(id)
        buffer.writeUUID(actorUuid)
        buffer.writeUtf(actorName)
        buffer.writeVarInt(type.ordinal)
        buffer.writeVarLong(referenceId)
        buffer.writeUtf(message)
        buffer.writeLong(timestamp)
        buffer.writeBoolean(read)
        buffer.writeBoolean(skinUrl != null)
        skinUrl?.let { buffer.writeUtf(it) }
    }

    companion object {
        fun of(notif: SocialNotification, server: MinecraftServer? = null): SocialNotificationView = SocialNotificationView(
            id = notif.id,
            actorUuid = notif.actorUuid,
            actorName = notif.actorName,
            type = notif.type,
            referenceId = notif.referenceId,
            message = notif.message,
            timestamp = notif.timestamp,
            read = notif.read,
            skinUrl = server?.let { SkinUrlResolver.resolve(it, notif.actorUuid) }
        )

        fun decode(buffer: RegistryFriendlyByteBuf): SocialNotificationView {
            val id = buffer.readVarLong()
            val actorUuid = buffer.readUUID()
            val actorName = buffer.readUtf()
            val type = SocialNotificationType.byId(buffer.readVarInt())
            val referenceId = buffer.readVarLong()
            val message = buffer.readUtf()
            val timestamp = buffer.readLong()
            val read = buffer.readBoolean()
            val skinUrl = if (buffer.readBoolean()) buffer.readUtf() else null
            return SocialNotificationView(
                id = id,
                actorUuid = actorUuid,
                actorName = actorName,
                type = type,
                referenceId = referenceId,
                message = message,
                timestamp = timestamp,
                read = read,
                skinUrl = skinUrl
            )
        }
    }
}
