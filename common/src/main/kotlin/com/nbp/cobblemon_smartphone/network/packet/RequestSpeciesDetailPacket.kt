package com.nbp.cobblemon_smartphone.network.packet

import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.network.RegistryFriendlyByteBuf

class RequestSpeciesDetailPacket(val dexNumber: Int, val formName: String?) :
    CobblemonSmartphoneNetworkPacket<RequestSpeciesDetailPacket> {
    companion object {
        val ID = smartphoneResource("request_species_detail")
        fun decode(buffer: RegistryFriendlyByteBuf): RequestSpeciesDetailPacket {
            val dexNumber = buffer.readVarInt()
            val hasForm = buffer.readBoolean()
            val formName = if (hasForm) buffer.readUtf() else null
            return RequestSpeciesDetailPacket(dexNumber, formName)
        }
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeVarInt(dexNumber)
        buffer.writeBoolean(formName != null)
        formName?.let { buffer.writeUtf(it) }
    }
}
