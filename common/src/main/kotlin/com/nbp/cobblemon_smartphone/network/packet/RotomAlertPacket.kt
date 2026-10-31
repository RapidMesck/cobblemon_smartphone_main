package com.nbp.cobblemon_smartphone.network.packet

import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.network.RegistryFriendlyByteBuf

class RotomAlertPacket(
    val speciesName: String,
    val isShiny: Boolean,
    val ivPercent: Int,
    val nature: String,
    val distance: Int,
    val isTargetMatch: Boolean,
    val soundAlert: Boolean
) : CobblemonSmartphoneNetworkPacket<RotomAlertPacket> {
    companion object {
        val ID = smartphoneResource("rotom_alert")

        fun decode(buffer: RegistryFriendlyByteBuf): RotomAlertPacket {
            val speciesName = buffer.readUtf(64)
            val isShiny = buffer.readBoolean()
            val ivPercent = buffer.readVarInt()
            val nature = buffer.readUtf(64)
            val distance = buffer.readVarInt()
            val isTargetMatch = buffer.readBoolean()
            val soundAlert = buffer.readBoolean()
            return RotomAlertPacket(speciesName, isShiny, ivPercent, nature, distance, isTargetMatch, soundAlert)
        }
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUtf(speciesName, 64)
        buffer.writeBoolean(isShiny)
        buffer.writeVarInt(ivPercent)
        buffer.writeUtf(nature, 64)
        buffer.writeVarInt(distance)
        buffer.writeBoolean(isTargetMatch)
        buffer.writeBoolean(soundAlert)
    }
}
