package com.nbp.cobblemon_smartphone.network.packet

import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.network.RegistryFriendlyByteBuf

/** Periodic progress update for a [RequestGpsSearchPacket]. Sent from server to client while the
 *  spiral search is running so the UI can display a progress indicator. */
class GpsSearchProgressPacket(
    val requestId: Long,
    val currentRing: Int,
    val maxRing: Int
) : CobblemonSmartphoneNetworkPacket<GpsSearchProgressPacket> {

    companion object {
        val ID = smartphoneResource("gps_search_progress")
        fun decode(buffer: RegistryFriendlyByteBuf): GpsSearchProgressPacket {
            val requestId = buffer.readVarLong()
            val currentRing = buffer.readVarInt()
            val maxRing = buffer.readVarInt()
            return GpsSearchProgressPacket(requestId, currentRing, maxRing)
        }
    }

    override val id = ID
    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeVarLong(requestId)
        buffer.writeVarInt(currentRing)
        buffer.writeVarInt(maxRing)
    }
}
