package com.nbp.cobblemon_smartphone.network.packet

import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.network.RegistryFriendlyByteBuf

class SyncRotomConfigPacket(
    val enableScanner: Boolean,
    val enableAutoScanner: Boolean,
    val enableManualScanPriority: Boolean,
    val enableSocialInteractions: Boolean,
    val allowScanAlreadyRegistered: Boolean,
    val requireLineOfSight: Boolean,
    val revealIvPercentage: Boolean,
    val revealNature: Boolean,
    val scanRadius: Double,
    val scanDurationTicks: Int,
    val approachTicks: Int,
    val returnTicks: Int,
    val infiniteEnergy: Boolean
) : CobblemonSmartphoneNetworkPacket<SyncRotomConfigPacket> {
    companion object {
        val ID = smartphoneResource("sync_rotom_config")

        fun decode(buffer: RegistryFriendlyByteBuf): SyncRotomConfigPacket {
            return SyncRotomConfigPacket(
                enableScanner = buffer.readBoolean(),
                enableAutoScanner = buffer.readBoolean(),
                enableManualScanPriority = buffer.readBoolean(),
                enableSocialInteractions = buffer.readBoolean(),
                allowScanAlreadyRegistered = buffer.readBoolean(),
                requireLineOfSight = buffer.readBoolean(),
                revealIvPercentage = buffer.readBoolean(),
                revealNature = buffer.readBoolean(),
                scanRadius = buffer.readDouble(),
                scanDurationTicks = buffer.readVarInt(),
                approachTicks = buffer.readVarInt(),
                returnTicks = buffer.readVarInt(),
                infiniteEnergy = buffer.readBoolean()
            )
        }

        fun fromServerConfig(): SyncRotomConfigPacket {
            val cfg = CobblemonSmartphone.config
            val rotom = cfg.rotom
            return SyncRotomConfigPacket(
                enableScanner = cfg.features.enableScanner,
                enableAutoScanner = rotom.enableAutoScanner,
                enableManualScanPriority = rotom.enableManualScanPriority,
                enableSocialInteractions = rotom.enableSocialInteractions,
                allowScanAlreadyRegistered = rotom.allowScanAlreadyRegistered,
                requireLineOfSight = rotom.requireLineOfSight,
                revealIvPercentage = rotom.revealIvPercentage,
                revealNature = rotom.revealNature,
                scanRadius = rotom.scanRadius,
                scanDurationTicks = rotom.scanDurationTicks,
                approachTicks = rotom.approachTicks,
                returnTicks = rotom.returnTicks,
                infiniteEnergy = rotom.infiniteEnergy
            )
        }
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeBoolean(enableScanner)
        buffer.writeBoolean(enableAutoScanner)
        buffer.writeBoolean(enableManualScanPriority)
        buffer.writeBoolean(enableSocialInteractions)
        buffer.writeBoolean(allowScanAlreadyRegistered)
        buffer.writeBoolean(requireLineOfSight)
        buffer.writeBoolean(revealIvPercentage)
        buffer.writeBoolean(revealNature)
        buffer.writeDouble(scanRadius)
        buffer.writeVarInt(scanDurationTicks)
        buffer.writeVarInt(approachTicks)
        buffer.writeVarInt(returnTicks)
        buffer.writeBoolean(infiniteEnergy)
    }
}
