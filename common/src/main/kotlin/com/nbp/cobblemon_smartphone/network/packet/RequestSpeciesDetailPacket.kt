package com.nbp.cobblemon_smartphone.network.packet

import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.network.RegistryFriendlyByteBuf

class RequestSpeciesDetailPacket(val requestId: Long, val speciesId: String) :
    CobblemonSmartphoneNetworkPacket<RequestSpeciesDetailPacket> {

    constructor(requestId: Long, dexNumber: Int) : this(requestId, dexNumber.toString())

    val dexNumber: Int get() = speciesId.toIntOrNull() ?: 0

    companion object {
        val ID = smartphoneResource("request_species_detail")
        fun decode(buffer: RegistryFriendlyByteBuf): RequestSpeciesDetailPacket {
            val requestId = buffer.readVarLong()
            val speciesId = buffer.readUtf(128)
            return RequestSpeciesDetailPacket(requestId, speciesId)
        }
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeVarLong(requestId)
        buffer.writeUtf(speciesId, 128)
    }
}
