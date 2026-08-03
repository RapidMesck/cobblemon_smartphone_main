package com.nbp.cobblemon_smartphone.network.packet

import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.network.RegistryFriendlyByteBuf

class RequestStructureListPacket(val requestId: Long) :
    CobblemonSmartphoneNetworkPacket<RequestStructureListPacket> {
    companion object {
        val ID = smartphoneResource("request_structure_list")
        fun decode(buffer: RegistryFriendlyByteBuf) = RequestStructureListPacket(buffer.readVarLong())
    }

    override val id = ID
    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeVarLong(requestId)
    }
}
