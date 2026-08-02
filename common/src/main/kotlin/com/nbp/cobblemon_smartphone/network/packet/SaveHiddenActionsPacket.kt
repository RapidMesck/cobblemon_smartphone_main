package com.nbp.cobblemon_smartphone.network.packet

import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.network.RegistryFriendlyByteBuf

class SaveHiddenActionsPacket(val hidden: List<String>) : CobblemonSmartphoneNetworkPacket<SaveHiddenActionsPacket> {
    companion object {
        val ID = smartphoneResource("save_hidden_actions")
        fun decode(buffer: RegistryFriendlyByteBuf): SaveHiddenActionsPacket {
            val count = buffer.readVarInt()
            val hidden = (0 until count).map { buffer.readUtf() }
            return SaveHiddenActionsPacket(hidden)
        }
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeVarInt(hidden.size)
        hidden.forEach { buffer.writeUtf(it) }
    }
}
