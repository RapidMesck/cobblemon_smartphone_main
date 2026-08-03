package com.nbp.cobblemon_smartphone.network.packet

import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.network.RegistryFriendlyByteBuf

class RequestBiomeListPacket(val requestId: Long) :
    CobblemonSmartphoneNetworkPacket<RequestBiomeListPacket> {
    companion object {
        val ID = smartphoneResource("request_biome_list")
        fun decode(buffer: RegistryFriendlyByteBuf) = RequestBiomeListPacket(buffer.readVarLong())
    }

    override val id = ID
    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeVarLong(requestId)
    }
}
