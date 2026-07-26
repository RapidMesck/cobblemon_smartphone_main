package com.nbp.cobblemon_smartphone.social

import net.minecraft.nbt.CompoundTag
import net.minecraft.network.RegistryFriendlyByteBuf
import java.util.UUID

/**
 * One message inside a [DmThread]. Ids come from a single server-wide counter so ordering and
 * read-tracking are stable comparisons rather than timestamp guesses.
 */
data class DmMessage(
    val id: Long,
    val senderUuid: UUID,
    val text: String,
    val timestamp: Long
) {
    fun toNbt(): CompoundTag {
        val tag = CompoundTag()
        tag.putLong(ID_KEY, id)
        tag.putUUID(SENDER_KEY, senderUuid)
        tag.putString(TEXT_KEY, text)
        tag.putLong(TIMESTAMP_KEY, timestamp)
        return tag
    }

    fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeVarLong(id)
        buffer.writeUUID(senderUuid)
        buffer.writeUtf(text)
        buffer.writeLong(timestamp)
    }

    companion object {
        private const val ID_KEY = "id"
        private const val SENDER_KEY = "sender"
        private const val TEXT_KEY = "text"
        private const val TIMESTAMP_KEY = "timestamp"

        fun fromNbt(tag: CompoundTag): DmMessage? {
            if (!tag.hasUUID(SENDER_KEY)) return null
            return DmMessage(
                id = tag.getLong(ID_KEY),
                senderUuid = tag.getUUID(SENDER_KEY),
                text = tag.getString(TEXT_KEY),
                timestamp = tag.getLong(TIMESTAMP_KEY)
            )
        }

        fun decode(buffer: RegistryFriendlyByteBuf) = DmMessage(
            id = buffer.readVarLong(),
            senderUuid = buffer.readUUID(),
            text = buffer.readUtf(),
            timestamp = buffer.readLong()
        )
    }
}
