package com.nbp.cobblemon_smartphone.network.packet

import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.network.RegistryFriendlyByteBuf
import java.util.UUID

/**
 * Creates a post.
 *
 * [attachSlot] is a party slot index (-1 for no attachment) — deliberately *not* the Pokémon data
 * itself. The server reads the player's own party at that slot and builds the snapshot, so a
 * modified client cannot forge an attachment it does not own.
 */
class CreatePostPacket(
    val requestId: Long,
    val text: String,
    val attachSlot: Int,
    val showDetails: Boolean,
    val showIvs: Boolean,
    val showEvs: Boolean,
    val showAbility: Boolean,
    val showNature: Boolean,
    val photoId: UUID?
) : CobblemonSmartphoneNetworkPacket<CreatePostPacket> {
    companion object {
        val ID = smartphoneResource("create_post")
        fun decode(buffer: RegistryFriendlyByteBuf) = CreatePostPacket(
            buffer.readVarLong(),
            buffer.readUtf(4_096),
            buffer.readVarInt(),
            buffer.readBoolean(),
            buffer.readBoolean(),
            buffer.readBoolean(),
            buffer.readBoolean(),
            buffer.readBoolean(),
            if (buffer.readBoolean()) buffer.readUUID() else null
        )
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeVarLong(requestId)
        buffer.writeUtf(text, 4_096)
        buffer.writeVarInt(attachSlot)
        buffer.writeBoolean(showDetails)
        buffer.writeBoolean(showIvs)
        buffer.writeBoolean(showEvs)
        buffer.writeBoolean(showAbility)
        buffer.writeBoolean(showNature)
        buffer.writeBoolean(photoId != null)
        photoId?.let(buffer::writeUUID)
    }
}
