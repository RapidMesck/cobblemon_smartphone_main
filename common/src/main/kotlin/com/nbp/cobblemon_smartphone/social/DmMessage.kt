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
    val timestamp: Long,
    val attachment: PokemonAttachment? = null,
    val photo: SocialPhotoRef? = null
) {
    fun toNbt(): CompoundTag {
        val tag = CompoundTag()
        tag.putLong(ID_KEY, id)
        tag.putUUID(SENDER_KEY, senderUuid)
        tag.putString(TEXT_KEY, text)
        tag.putLong(TIMESTAMP_KEY, timestamp)
        attachment?.let { tag.put(ATTACHMENT_KEY, it.toNbt()) }
        photo?.let { tag.put(PHOTO_KEY, it.toNbt()) }
        return tag
    }

    fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeVarLong(id)
        buffer.writeUUID(senderUuid)
        buffer.writeUtf(text)
        buffer.writeLong(timestamp)
        buffer.writeBoolean(attachment != null)
        attachment?.encode(buffer)
        buffer.writeBoolean(photo != null)
        photo?.let {
            buffer.writeUUID(it.id)
            buffer.writeVarInt(it.width)
            buffer.writeVarInt(it.height)
        }
    }

    companion object {
        private const val ID_KEY = "id"
        private const val SENDER_KEY = "sender"
        private const val TEXT_KEY = "text"
        private const val TIMESTAMP_KEY = "timestamp"
        private const val ATTACHMENT_KEY = "attachment"
        private const val PHOTO_KEY = "photo"

        fun fromNbt(tag: CompoundTag): DmMessage? {
            if (!tag.hasUUID(SENDER_KEY)) return null
            return DmMessage(
                id = tag.getLong(ID_KEY),
                senderUuid = tag.getUUID(SENDER_KEY),
                text = tag.getString(TEXT_KEY),
                timestamp = tag.getLong(TIMESTAMP_KEY),
                attachment = if (tag.contains(ATTACHMENT_KEY)) PokemonAttachment.fromNbt(tag.getCompound(ATTACHMENT_KEY)) else null,
                photo = if (tag.contains(PHOTO_KEY)) SocialPhotoRef.fromNbt(tag.getCompound(PHOTO_KEY)) else null
            )
        }

        fun decode(buffer: RegistryFriendlyByteBuf): DmMessage {
            val id = buffer.readVarLong()
            val sender = buffer.readUUID()
            val text = buffer.readUtf()
            val timestamp = buffer.readLong()
            val attachment = if (buffer.readBoolean()) PokemonAttachment.decode(buffer) else null
            val photo = if (buffer.readBoolean()) SocialPhotoRef(buffer.readUUID(), buffer.readVarInt(), buffer.readVarInt()) else null
            return DmMessage(id, sender, text, timestamp, attachment, photo)
        }
    }
}
