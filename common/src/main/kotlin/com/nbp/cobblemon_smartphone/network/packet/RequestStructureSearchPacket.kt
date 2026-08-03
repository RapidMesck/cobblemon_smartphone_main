package com.nbp.cobblemon_smartphone.network.packet

import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.network.RegistryFriendlyByteBuf

/** Asks the server to locate the nearest instance of [structureId] to the sending player, using the
 *  same vanilla lookup that backs /locate structure (see StructureSearchTask). */
class RequestStructureSearchPacket(val requestId: Long, val structureId: String) :
    CobblemonSmartphoneNetworkPacket<RequestStructureSearchPacket> {
    companion object {
        val ID = smartphoneResource("request_structure_search")
        fun decode(buffer: RegistryFriendlyByteBuf) =
            RequestStructureSearchPacket(buffer.readVarLong(), buffer.readUtf(128))
    }

    override val id = ID
    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeVarLong(requestId)
        buffer.writeUtf(structureId, 128)
    }
}
