package com.nbp.cobblemon_smartphone.network.packet

import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.network.RegistryFriendlyByteBuf

class SaveQuickActionsPacket(val bindings: Map<Int, String>) : CobblemonSmartphoneNetworkPacket<SaveQuickActionsPacket> {
    companion object {
        val ID = smartphoneResource("save_quick_actions")
        private const val MAX_ENTRIES = 256
        private const val MAX_STRING_LENGTH = 256

        fun decode(buffer: RegistryFriendlyByteBuf): SaveQuickActionsPacket {
            val count = buffer.readVarInt().coerceAtMost(MAX_ENTRIES)
            val bindings = (0 until count).associate { buffer.readVarInt() to buffer.readUtf(MAX_STRING_LENGTH) }
            return SaveQuickActionsPacket(bindings)
        }
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeVarInt(bindings.size)
        bindings.forEach { (slot, actionId) ->
            buffer.writeVarInt(slot)
            buffer.writeUtf(actionId)
        }
    }
}
