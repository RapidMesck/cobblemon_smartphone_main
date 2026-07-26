package com.nbp.cobblemon_smartphone.network.packet

import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.network.RegistryFriendlyByteBuf

/**
 * Creates a post.
 *
 * [attachSlot] is a party slot index (-1 for no attachment) — deliberately *not* the Pokémon data
 * itself. The server reads the player's own party at that slot and builds the snapshot, so a
 * modified client cannot forge an attachment it does not own.
 */
class CreatePostPacket(val text: String, val attachSlot: Int) : CobblemonSmartphoneNetworkPacket<CreatePostPacket> {
    companion object {
        val ID = smartphoneResource("create_post")
        fun decode(buffer: RegistryFriendlyByteBuf) = CreatePostPacket(buffer.readUtf(), buffer.readVarInt())
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUtf(text)
        buffer.writeVarInt(attachSlot)
    }
}
