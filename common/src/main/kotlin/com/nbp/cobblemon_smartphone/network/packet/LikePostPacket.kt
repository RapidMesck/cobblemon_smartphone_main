package com.nbp.cobblemon_smartphone.network.packet

import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.network.RegistryFriendlyByteBuf

class LikePostPacket(val postId: Long, val liked: Boolean) : CobblemonSmartphoneNetworkPacket<LikePostPacket> {
    companion object {
        val ID = smartphoneResource("like_post")
        fun decode(buffer: RegistryFriendlyByteBuf) = LikePostPacket(buffer.readVarLong(), buffer.readBoolean())
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeVarLong(postId)
        buffer.writeBoolean(liked)
    }
}
