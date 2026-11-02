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
    val infiniteEnergy: Boolean,
    val enableSocialDataShare: Boolean = true,
    val socialDataShareChance: Double = 0.35,
    val curiosityLanguage: String = "en"
) : CobblemonSmartphoneNetworkPacket<SyncRotomConfigPacket> {
    companion object {
        val ID = smartphoneResource("sync_rotom_config")

        fun decode(buffer: RegistryFriendlyByteBuf): SyncRotomConfigPacket {
            val enableScanner = buffer.readBoolean()
            val enableAutoScanner = buffer.readBoolean()
            val enableManualScanPriority = buffer.readBoolean()
            val enableSocialInteractions = buffer.readBoolean()
            val allowScanAlreadyRegistered = buffer.readBoolean()
            val requireLineOfSight = buffer.readBoolean()
            val revealIvPercentage = buffer.readBoolean()
            val revealNature = buffer.readBoolean()
            val scanRadius = buffer.readDouble()
            val scanDurationTicks = buffer.readVarInt()
            val approachTicks = buffer.readVarInt()
            val returnTicks = buffer.readVarInt()
            val infiniteEnergy = buffer.readBoolean()
            val enableSocialDataShare = if (buffer.isReadable) buffer.readBoolean() else true
            val socialDataShareChance = if (buffer.readableBytes() >= 8) buffer.readDouble() else 0.35
            val curiosityLanguage = if (buffer.isReadable) buffer.readUtf(16) else "en"
            return SyncRotomConfigPacket(
                enableScanner = enableScanner,
                enableAutoScanner = enableAutoScanner,
                enableManualScanPriority = enableManualScanPriority,
                enableSocialInteractions = enableSocialInteractions,
                allowScanAlreadyRegistered = allowScanAlreadyRegistered,
                requireLineOfSight = requireLineOfSight,
                revealIvPercentage = revealIvPercentage,
                revealNature = revealNature,
                scanRadius = scanRadius,
                scanDurationTicks = scanDurationTicks,
                approachTicks = approachTicks,
                returnTicks = returnTicks,
                infiniteEnergy = infiniteEnergy,
                enableSocialDataShare = enableSocialDataShare,
                socialDataShareChance = socialDataShareChance,
                curiosityLanguage = curiosityLanguage
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
                infiniteEnergy = rotom.infiniteEnergy,
                enableSocialDataShare = rotom.enableSocialDataShare,
                socialDataShareChance = rotom.socialDataShareChance,
                curiosityLanguage = rotom.curiosityLanguage
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
        buffer.writeBoolean(enableSocialDataShare)
        buffer.writeDouble(socialDataShareChance)
        buffer.writeUtf(curiosityLanguage, 16)
    }
}
