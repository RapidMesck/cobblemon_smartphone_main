package com.nbp.cobblemon_smartphone.network.packet

import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.network.RegistryFriendlyByteBuf

/**
 * Casts a vote on a post's poll.
 */
class VotePollPacket(
    val postId: Long,
    val optionIndex: Int
) : CobblemonSmartphoneNetworkPacket<VotePollPacket> {
    companion object {
        val ID = smartphoneResource("vote_poll")
        fun decode(buffer: RegistryFriendlyByteBuf) = VotePollPacket(
            buffer.readVarLong(),
            buffer.readVarInt()
        )
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeVarLong(postId)
        buffer.writeVarInt(optionIndex)
    }
}
