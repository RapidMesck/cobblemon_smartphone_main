package com.nbp.cobblemon_smartphone.network.packet

import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.network.RegistryFriendlyByteBuf

class SaveHiddenActionsPacket(val hidden: List<String>) : CobblemonSmartphoneNetworkPacket<SaveHiddenActionsPacket> {
    companion object {
        val ID = smartphoneResource("save_hidden_actions")
        private const val MAX_ENTRIES = 256
        private const val MAX_STRING_LENGTH = 256

        fun decode(buffer: RegistryFriendlyByteBuf): SaveHiddenActionsPacket {
            val count = buffer.readVarInt().coerceAtMost(MAX_ENTRIES)
            val hidden = (0 until count).map { buffer.readUtf(MAX_STRING_LENGTH) }
            return SaveHiddenActionsPacket(hidden)
        }
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeVarInt(hidden.size)
        hidden.forEach { buffer.writeUtf(it) }
    }
}
