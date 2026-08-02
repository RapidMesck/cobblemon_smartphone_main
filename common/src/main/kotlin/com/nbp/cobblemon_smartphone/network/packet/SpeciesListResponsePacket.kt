package com.nbp.cobblemon_smartphone.network.packet

import com.nbp.cobblemon_smartphone.util.PokeInfoDataProvider.SpeciesInfo
import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.network.RegistryFriendlyByteBuf

class SpeciesListResponsePacket(
    val requestId: Long,
    val status: Status,
    val species: List<SpeciesInfo>
) : CobblemonSmartphoneNetworkPacket<SpeciesListResponsePacket> {
    enum class Status { SUCCESS, RATE_LIMITED, ERROR }

    companion object {
        val ID = smartphoneResource("species_list_response")
        fun decode(buffer: RegistryFriendlyByteBuf): SpeciesListResponsePacket {
            val requestId = buffer.readVarLong()
            val status = runCatching { Status.valueOf(buffer.readUtf(32)) }.getOrDefault(Status.ERROR)
            val count = buffer.readVarInt().coerceIn(0, MAX_SPECIES)
            val species = (0 until count).map {
                SpeciesInfo(
                    name = buffer.readUtf(128),
                    dexNumber = buffer.readVarInt(),
                    primaryType = buffer.readUtf(32),
                    secondaryType = if (buffer.readBoolean()) buffer.readUtf(32) else null
                )
            }
            return SpeciesListResponsePacket(requestId, status, species)
        }

        private const val MAX_SPECIES = 4096
    }

    override val id = ID
    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeVarLong(requestId)
        buffer.writeUtf(status.name)
        val bounded = species.take(MAX_SPECIES)
        buffer.writeVarInt(bounded.size)
        bounded.forEach {
            buffer.writeUtf(it.name, 128)
            buffer.writeVarInt(it.dexNumber)
            buffer.writeUtf(it.primaryType, 32)
            buffer.writeBoolean(it.secondaryType != null)
            it.secondaryType?.let { type -> buffer.writeUtf(type, 32) }
        }
    }
}
