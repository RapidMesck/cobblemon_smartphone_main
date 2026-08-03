package com.nbp.cobblemon_smartphone.network.packet

import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf

/** Result of a [RequestGpsSearchPacket]. [dimension] is the dimension the search actually ran in,
 *  so the client can discard a stale result that arrives after it has already changed dimension
 *  or restarted the search. */
class GpsSearchResultPacket(
    val requestId: Long,
    val status: Status,
    val dimension: String,
    val pos: BlockPos?
) : CobblemonSmartphoneNetworkPacket<GpsSearchResultPacket> {
    enum class Status { FOUND, NOT_FOUND, RATE_LIMITED, ERROR }

    companion object {
        val ID = smartphoneResource("gps_search_result")
        fun decode(buffer: RegistryFriendlyByteBuf): GpsSearchResultPacket {
            val requestId = buffer.readVarLong()
            val status = runCatching { Status.valueOf(buffer.readUtf(32)) }.getOrDefault(Status.ERROR)
            val dimension = buffer.readUtf(64)
            val pos = if (buffer.readBoolean()) buffer.readBlockPos() else null
            return GpsSearchResultPacket(requestId, status, dimension, pos)
        }
    }

    override val id = ID
    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeVarLong(requestId)
        buffer.writeUtf(status.name)
        buffer.writeUtf(dimension, 64)
        buffer.writeBoolean(pos != null)
        pos?.let { buffer.writeBlockPos(it) }
    }
}
