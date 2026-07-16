package com.nbp.cobblemon_smartphone.network.packet

import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.network.RegistryFriendlyByteBuf

class SaveQuickActionsPacket(val bindings: Map<Int, String>) : CobblemonSmartphoneNetworkPacket<SaveQuickActionsPacket> {
    companion object {
        val ID = smartphoneResource("save_quick_actions")
        fun decode(buffer: RegistryFriendlyByteBuf): SaveQuickActionsPacket {
            val count = buffer.readVarInt()
            val bindings = (0 until count).associate { buffer.readVarInt() to buffer.readUtf() }
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
