package com.nbp.cobblemon_smartphone.network.packet

import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.network.RegistryFriendlyByteBuf
import java.util.UUID

class RotomScanCompletePacket(
    val scannerPlayerUuid: UUID,
    val targetEntityId: Int,
    val speciesName: String,
    val isAlreadyRegistered: Boolean,
    val level: Int,
    val nature: String,
    val ivPercent: Int
) : CobblemonSmartphoneNetworkPacket<RotomScanCompletePacket> {
    companion object {
        val ID = smartphoneResource("rotom_scan_complete")

        fun decode(buffer: RegistryFriendlyByteBuf): RotomScanCompletePacket {
            val scannerPlayerUuid = buffer.readUUID()
            val targetEntityId = buffer.readVarInt()
            val speciesName = buffer.readUtf(64)
            val isAlreadyRegistered = buffer.readBoolean()
            val level = buffer.readVarInt()
            val nature = buffer.readUtf(64)
            val ivPercent = buffer.readVarInt()
            return RotomScanCompletePacket(
                scannerPlayerUuid = scannerPlayerUuid,
                targetEntityId = targetEntityId,
                speciesName = speciesName,
                isAlreadyRegistered = isAlreadyRegistered,
                level = level,
                nature = nature,
                ivPercent = ivPercent
            )
        }
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUUID(scannerPlayerUuid)
        buffer.writeVarInt(targetEntityId)
        buffer.writeUtf(speciesName, 64)
        buffer.writeBoolean(isAlreadyRegistered)
        buffer.writeVarInt(level)
        buffer.writeUtf(nature, 64)
        buffer.writeVarInt(ivPercent)
    }
}
