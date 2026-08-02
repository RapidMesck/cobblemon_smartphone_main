package com.nbp.cobblemon_smartphone.network.packet

import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.network.RegistryFriendlyByteBuf

class RequestSpeciesListPacket(val requestId: Long) :
    CobblemonSmartphoneNetworkPacket<RequestSpeciesListPacket> {
    companion object {
        val ID = smartphoneResource("request_species_list")
        fun decode(buffer: RegistryFriendlyByteBuf) = RequestSpeciesListPacket(buffer.readVarLong())
    }

    override val id = ID
    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeVarLong(requestId)
    }
}
