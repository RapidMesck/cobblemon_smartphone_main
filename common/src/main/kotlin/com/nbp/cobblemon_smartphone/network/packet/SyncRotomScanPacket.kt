package com.nbp.cobblemon_smartphone.network.packet

import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.network.RegistryFriendlyByteBuf
import java.util.UUID

class SyncRotomScanPacket(
    val scannerPlayerUuid: UUID,
    val targetEntityId: Int,
    val phase: Int, // 0 = APPROACHING, 1 = SCANNING, 2 = RETURNING, -1 = IDLE
    val progressTicks: Int,
    val totalTicks: Int,
    val isPrioritized: Boolean,
    val isAlreadyRegistered: Boolean
) : CobblemonSmartphoneNetworkPacket<SyncRotomScanPacket> {
    companion object {
        val ID = smartphoneResource("sync_rotom_scan")

        const val PHASE_APPROACHING = 0
        const val PHASE_SCANNING = 1
        const val PHASE_RETURNING = 2
        const val PHASE_IDLE = -1

        fun decode(buffer: RegistryFriendlyByteBuf): SyncRotomScanPacket {
            val scannerPlayerUuid = buffer.readUUID()
            val targetEntityId = buffer.readVarInt()
            val phase = buffer.readVarInt()
            val progressTicks = buffer.readVarInt()
            val totalTicks = buffer.readVarInt()
            val isPrioritized = buffer.readBoolean()
            val isAlreadyRegistered = buffer.readBoolean()
            return SyncRotomScanPacket(
                scannerPlayerUuid = scannerPlayerUuid,
                targetEntityId = targetEntityId,
                phase = phase,
                progressTicks = progressTicks,
                totalTicks = totalTicks,
                isPrioritized = isPrioritized,
                isAlreadyRegistered = isAlreadyRegistered
            )
        }
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUUID(scannerPlayerUuid)
        buffer.writeVarInt(targetEntityId)
        buffer.writeVarInt(phase)
        buffer.writeVarInt(progressTicks)
        buffer.writeVarInt(totalTicks)
        buffer.writeBoolean(isPrioritized)
        buffer.writeBoolean(isAlreadyRegistered)
    }
}
