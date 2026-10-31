package com.nbp.cobblemon_smartphone.network.packet

import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.network.RegistryFriendlyByteBuf

class SetRotomScanPriorityPacket(val entityId: Int) : CobblemonSmartphoneNetworkPacket<SetRotomScanPriorityPacket> {
    companion object {
        val ID = smartphoneResource("set_rotom_scan_priority")

        fun decode(buffer: RegistryFriendlyByteBuf): SetRotomScanPriorityPacket {
            val entityId = buffer.readVarInt()
            return SetRotomScanPriorityPacket(entityId)
        }
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeVarInt(entityId)
    }
}
