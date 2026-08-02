package com.nbp.cobblemon_smartphone.network.packet

import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.network.RegistryFriendlyByteBuf

class RequestSpeciesDetailPacket(val requestId: Long, val dexNumber: Int) :
    CobblemonSmartphoneNetworkPacket<RequestSpeciesDetailPacket> {
    companion object {
        val ID = smartphoneResource("request_species_detail")
        fun decode(buffer: RegistryFriendlyByteBuf): RequestSpeciesDetailPacket {
            val requestId = buffer.readVarLong()
            val dexNumber = buffer.readVarInt()
            return RequestSpeciesDetailPacket(requestId, dexNumber)
        }
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeVarLong(requestId)
        buffer.writeVarInt(dexNumber)
    }
}
