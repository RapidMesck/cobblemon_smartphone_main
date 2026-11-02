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
    val soundAlert: Boolean,
    val targetEntityId: Int = -1,
    val targetX: Double = 0.0,
    val targetY: Double = 0.0,
    val targetZ: Double = 0.0,
    val speciesId: String = ""
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
            val targetEntityId = if (buffer.isReadable) buffer.readVarInt() else -1
            val hasCoords = buffer.readableBytes() >= 24
            val targetX = if (hasCoords) buffer.readDouble() else 0.0
            val targetY = if (hasCoords) buffer.readDouble() else 0.0
            val targetZ = if (hasCoords) buffer.readDouble() else 0.0
            val speciesId = if (buffer.isReadable) buffer.readUtf(128) else ""
            return RotomAlertPacket(
                speciesName, isShiny, ivPercent, nature, distance, isTargetMatch, soundAlert,
                targetEntityId, targetX, targetY, targetZ, speciesId
            )
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
        buffer.writeVarInt(targetEntityId)
        buffer.writeDouble(targetX)
        buffer.writeDouble(targetY)
        buffer.writeDouble(targetZ)
        buffer.writeUtf(speciesId, 128)
    }
}
