package com.nbp.cobblemon_smartphone.network.packet

import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.network.RegistryFriendlyByteBuf
import java.util.UUID

class RotomScanCompletePacket(
    val scannerPlayerUuid: UUID,
    val targetEntityId: Int,
    val speciesName: String,
    val nationalDexId: Int,
    val isAlreadyRegistered: Boolean,
    val level: Int,
    val nature: String,
    val ivPercent: Int,
    val showDialogue: Boolean = true
) : CobblemonSmartphoneNetworkPacket<RotomScanCompletePacket> {
    companion object {
        val ID = smartphoneResource("rotom_scan_complete")

        fun decode(buffer: RegistryFriendlyByteBuf): RotomScanCompletePacket {
            val scannerPlayerUuid = buffer.readUUID()
            val targetEntityId = buffer.readVarInt()
            val speciesName = buffer.readUtf(64)
            val nationalDexId = buffer.readVarInt()
            val isAlreadyRegistered = buffer.readBoolean()
            val level = buffer.readVarInt()
            val nature = buffer.readUtf(64)
            val ivPercent = buffer.readVarInt()
            val showDialogue = if (buffer.isReadable) buffer.readBoolean() else true
            return RotomScanCompletePacket(
                scannerPlayerUuid = scannerPlayerUuid,
                targetEntityId = targetEntityId,
                speciesName = speciesName,
                nationalDexId = nationalDexId,
                isAlreadyRegistered = isAlreadyRegistered,
                level = level,
                nature = nature,
                ivPercent = ivPercent,
                showDialogue = showDialogue
            )
        }
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUUID(scannerPlayerUuid)
        buffer.writeVarInt(targetEntityId)
        buffer.writeUtf(speciesName, 64)
        buffer.writeVarInt(nationalDexId)
        buffer.writeBoolean(isAlreadyRegistered)
        buffer.writeVarInt(level)
        buffer.writeUtf(nature, 64)
        buffer.writeVarInt(ivPercent)
        buffer.writeBoolean(showDialogue)
    }
}
