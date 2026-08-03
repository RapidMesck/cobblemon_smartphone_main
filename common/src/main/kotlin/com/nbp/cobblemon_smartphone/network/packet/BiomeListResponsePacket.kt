package com.nbp.cobblemon_smartphone.network.packet

import com.nbp.cobblemon_smartphone.util.GpsDataProvider.BiomeInfo
import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.network.RegistryFriendlyByteBuf

class BiomeListResponsePacket(
    val requestId: Long,
    val status: Status,
    val biomes: List<BiomeInfo>
) : CobblemonSmartphoneNetworkPacket<BiomeListResponsePacket> {
    enum class Status { SUCCESS, RATE_LIMITED, ERROR }

    companion object {
        val ID = smartphoneResource("biome_list_response")
        fun decode(buffer: RegistryFriendlyByteBuf): BiomeListResponsePacket {
            val requestId = buffer.readVarLong()
            val status = runCatching { Status.valueOf(buffer.readUtf(32)) }.getOrDefault(Status.ERROR)
            val count = buffer.readVarInt().coerceIn(0, MAX_BIOMES)
            val biomes = (0 until count).map {
                BiomeInfo(
                    id = buffer.readUtf(128),
                    tags = buffer.readList { it.readUtf(128) },
                    dimensions = buffer.readList { it.readUtf(64) },
                    sourceMod = buffer.readUtf(64)
                )
            }
            return BiomeListResponsePacket(requestId, status, biomes)
        }

        private const val MAX_BIOMES = 4096
    }

    override val id = ID
    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeVarLong(requestId)
        buffer.writeUtf(status.name)
        val bounded = biomes.take(MAX_BIOMES)
        buffer.writeVarInt(bounded.size)
        bounded.forEach {
            buffer.writeUtf(it.id, 128)
            buffer.writeCollection(it.tags) { buf, tag -> buf.writeUtf(tag, 128) }
            buffer.writeCollection(it.dimensions) { buf, dim -> buf.writeUtf(dim, 64) }
            buffer.writeUtf(it.sourceMod, 64)
        }
    }
}
