package com.nbp.cobblemon_smartphone.network.packet

import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.network.RegistryFriendlyByteBuf

class SyncActionOrderPacket(val order: List<String>) : CobblemonSmartphoneNetworkPacket<SyncActionOrderPacket> {
    companion object {
        val ID = smartphoneResource("sync_action_order")
        fun decode(buffer: RegistryFriendlyByteBuf): SyncActionOrderPacket {
            val count = buffer.readVarInt()
            val order = (0 until count).map { buffer.readUtf() }
            return SyncActionOrderPacket(order)
        }
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeVarInt(order.size)
        order.forEach { buffer.writeUtf(it) }
    }
}
