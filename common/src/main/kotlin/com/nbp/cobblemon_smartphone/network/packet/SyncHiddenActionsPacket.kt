package com.nbp.cobblemon_smartphone.network.packet

import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.network.RegistryFriendlyByteBuf

class SyncHiddenActionsPacket(val hidden: List<String>) : CobblemonSmartphoneNetworkPacket<SyncHiddenActionsPacket> {
    companion object {
        val ID = smartphoneResource("sync_hidden_actions")
        fun decode(buffer: RegistryFriendlyByteBuf): SyncHiddenActionsPacket {
            val count = buffer.readVarInt()
            val hidden = (0 until count).map { buffer.readUtf() }
            return SyncHiddenActionsPacket(hidden)
        }
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeVarInt(hidden.size)
        hidden.forEach { buffer.writeUtf(it) }
    }
}
