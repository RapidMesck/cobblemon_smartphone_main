package com.nbp.cobblemon_smartphone.social

import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.server.MinecraftServer
import java.util.UUID

/**
 * Row in the DM list, always from one viewer's perspective: [otherUuid] is the counterpart and
 * [unreadCount] is that viewer's. Sending whole threads just to render the list would be wasteful.
 */
data class DmThreadSummary(
    val otherUuid: UUID,
    val otherName: String,
    val preview: String,
    val lastTimestamp: Long,
    val unreadCount: Int,
    val skinUrl: String? = null
) {
    fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUUID(otherUuid)
        buffer.writeUtf(otherName)
        buffer.writeUtf(preview)
        buffer.writeLong(lastTimestamp)
        buffer.writeVarInt(unreadCount)
        buffer.writeBoolean(skinUrl != null)
        skinUrl?.let { buffer.writeUtf(it) }
    }

    companion object {
        private const val PREVIEW_LENGTH = 64

        fun of(thread: DmThread, viewer: UUID, server: MinecraftServer? = null): DmThreadSummary {
            val other = thread.key.other(viewer)
            val last = thread.lastMessage()
            return DmThreadSummary(
                otherUuid = other,
                otherName = thread.displayNameOf(other),
                preview = last?.text?.take(PREVIEW_LENGTH)?.takeIf { it.isNotBlank() }
                    ?: when {
                        last?.photo != null -> "[Photo]"
                        last?.attachment != null -> "[Pokémon]"
                        else -> ""
                    },
                lastTimestamp = last?.timestamp ?: 0L,
                unreadCount = thread.unreadCountFor(viewer),
                skinUrl = server?.let { SkinUrlResolver.resolve(it, other) }
            )
        }

        fun decode(buffer: RegistryFriendlyByteBuf) = DmThreadSummary(
            otherUuid = buffer.readUUID(),
            otherName = buffer.readUtf(),
            preview = buffer.readUtf(),
            lastTimestamp = buffer.readLong(),
            unreadCount = buffer.readVarInt(),
            skinUrl = if (buffer.readBoolean()) buffer.readUtf() else null
        )
    }
}
