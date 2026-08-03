package com.nbp.cobblemon_smartphone.network.packet

import com.nbp.cobblemon_smartphone.util.StructureDataProvider.StructureInfo
import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.network.RegistryFriendlyByteBuf

class StructureListResponsePacket(
    val requestId: Long,
    val status: Status,
    val structures: List<StructureInfo>
) : CobblemonSmartphoneNetworkPacket<StructureListResponsePacket> {
    enum class Status { SUCCESS, RATE_LIMITED, ERROR }

    companion object {
        val ID = smartphoneResource("structure_list_response")
        fun decode(buffer: RegistryFriendlyByteBuf): StructureListResponsePacket {
            val requestId = buffer.readVarLong()
            val status = runCatching { Status.valueOf(buffer.readUtf(32)) }.getOrDefault(Status.ERROR)
            val count = buffer.readVarInt().coerceIn(0, MAX_STRUCTURES)
            val structures = (0 until count).map {
                StructureInfo(
                    id = buffer.readUtf(128),
                    dimensions = buffer.readList { it.readUtf(64) },
                    sourceMod = buffer.readUtf(64)
                )
            }
            return StructureListResponsePacket(requestId, status, structures)
        }

        private const val MAX_STRUCTURES = 4096
    }

    override val id = ID
    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeVarLong(requestId)
        buffer.writeUtf(status.name)
        val bounded = structures.take(MAX_STRUCTURES)
        buffer.writeVarInt(bounded.size)
        bounded.forEach {
            buffer.writeUtf(it.id, 128)
            buffer.writeCollection(it.dimensions) { buf, dim -> buf.writeUtf(dim, 64) }
            buffer.writeUtf(it.sourceMod, 64)
        }
    }
}
