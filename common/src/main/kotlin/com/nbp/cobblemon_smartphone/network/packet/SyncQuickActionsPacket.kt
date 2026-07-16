package com.nbp.cobblemon_smartphone.network.packet

import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.network.RegistryFriendlyByteBuf

class SyncQuickActionsPacket(val bindings: Map<Int, String>) : CobblemonSmartphoneNetworkPacket<SyncQuickActionsPacket> {
    companion object {
        val ID = smartphoneResource("sync_quick_actions")
        fun decode(buffer: RegistryFriendlyByteBuf): SyncQuickActionsPacket {
            val count = buffer.readVarInt()
            val bindings = (0 until count).associate { buffer.readVarInt() to buffer.readUtf() }
            return SyncQuickActionsPacket(bindings)
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
