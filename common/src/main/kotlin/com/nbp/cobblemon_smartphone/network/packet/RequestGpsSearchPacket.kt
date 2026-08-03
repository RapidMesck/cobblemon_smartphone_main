package com.nbp.cobblemon_smartphone.network.packet

import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.network.RegistryFriendlyByteBuf

/** Asks the server to locate the nearest instance of [biomeId] to the sending player. The actual
 *  search runs against the real chunk generator server-side (see GpsBiomeSearchTask), since the
 *  client only has biome data for chunks it has already loaded. */
class RequestGpsSearchPacket(val requestId: Long, val biomeId: String) :
    CobblemonSmartphoneNetworkPacket<RequestGpsSearchPacket> {
    companion object {
        val ID = smartphoneResource("request_gps_search")
        fun decode(buffer: RegistryFriendlyByteBuf) =
            RequestGpsSearchPacket(buffer.readVarLong(), buffer.readUtf(128))
    }

    override val id = ID
    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeVarLong(requestId)
        buffer.writeUtf(biomeId, 128)
    }
}
