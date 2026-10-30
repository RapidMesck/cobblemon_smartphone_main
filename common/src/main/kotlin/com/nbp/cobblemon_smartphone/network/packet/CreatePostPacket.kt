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
    val photoId: UUID?,
    val pollQuestion: String? = null,
    val pollOptions: List<String> = emptyList()
) : CobblemonSmartphoneNetworkPacket<CreatePostPacket> {
    companion object {
        val ID = smartphoneResource("create_post")
        fun decode(buffer: RegistryFriendlyByteBuf): CreatePostPacket {
            val reqId = buffer.readVarLong()
            val text = buffer.readUtf(4_096)
            val attachSlot = buffer.readVarInt()
            val showDetails = buffer.readBoolean()
            val showIvs = buffer.readBoolean()
            val showEvs = buffer.readBoolean()
            val showAbility = buffer.readBoolean()
            val showNature = buffer.readBoolean()
            val photoId = if (buffer.readBoolean()) buffer.readUUID() else null
            val hasPoll = buffer.readBoolean()
            val (pollQuestion, pollOptions) = if (hasPoll) {
                val q = buffer.readUtf()
                val count = buffer.readVarInt()
                val opts = (0 until count).map { buffer.readUtf() }
                q to opts
            } else {
                null to emptyList()
            }
            return CreatePostPacket(
                reqId, text, attachSlot, showDetails, showIvs, showEvs, showAbility, showNature, photoId,
                pollQuestion, pollOptions
            )
        }
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
        buffer.writeBoolean(pollQuestion != null)
        pollQuestion?.let {
            buffer.writeUtf(it)
            buffer.writeVarInt(pollOptions.size)
            pollOptions.forEach { opt -> buffer.writeUtf(opt) }
        }
    }
}
