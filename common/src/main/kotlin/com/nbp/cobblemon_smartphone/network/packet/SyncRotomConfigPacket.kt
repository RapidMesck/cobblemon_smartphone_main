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
    val curiosityLanguage: String = "en",
    val enableRotom: Boolean = true,
    val enableDrone: Boolean = true,
    val droneMaxRange: Double = 32.0,
    val droneSpeed: Double = 0.45,
    val droneAllowPhoto: Boolean = true,
    val droneAllowFluids: Boolean = false,
    val droneBlockedDimensions: List<String> = emptyList(),
    val droneEnergyPerSecond: Int = 0,
    val enableSocial: Boolean = true
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
            val enableRotom = if (buffer.isReadable) buffer.readBoolean() else true
            val enableDrone = if (buffer.isReadable) buffer.readBoolean() else true
            val droneMaxRange = if (buffer.readableBytes() >= 8) buffer.readDouble() else 32.0
            val droneSpeed = if (buffer.readableBytes() >= 8) buffer.readDouble() else 0.45
            val droneAllowPhoto = if (buffer.isReadable) buffer.readBoolean() else true
            val droneAllowFluids = if (buffer.isReadable) buffer.readBoolean() else false
            val droneBlockedDimensions = if (buffer.isReadable) {
                val count = buffer.readVarInt().coerceIn(0, 256)
                List(count) { buffer.readUtf(128) }
            } else {
                emptyList()
            }
            val droneEnergyPerSecond = if (buffer.isReadable) buffer.readVarInt() else 0
            val enableSocial = if (buffer.isReadable) buffer.readBoolean() else true
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
                curiosityLanguage = curiosityLanguage,
                enableRotom = enableRotom,
                enableDrone = enableDrone,
                droneMaxRange = droneMaxRange,
                droneSpeed = droneSpeed,
                droneAllowPhoto = droneAllowPhoto,
                droneAllowFluids = droneAllowFluids,
                droneBlockedDimensions = droneBlockedDimensions,
                droneEnergyPerSecond = droneEnergyPerSecond,
                enableSocial = enableSocial
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
                curiosityLanguage = rotom.curiosityLanguage,
                enableRotom = cfg.features.enableRotom,
                enableDrone = rotom.enableDrone,
                droneMaxRange = rotom.droneMaxRange,
                droneSpeed = rotom.droneSpeed,
                droneAllowPhoto = rotom.droneAllowPhoto,
                droneAllowFluids = rotom.droneAllowFluids,
                droneBlockedDimensions = rotom.droneBlockedDimensions,
                droneEnergyPerSecond = rotom.droneEnergyPerSecond,
                enableSocial = cfg.features.enableSocial
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
        buffer.writeBoolean(enableRotom)
        buffer.writeBoolean(enableDrone)
        buffer.writeDouble(droneMaxRange)
        buffer.writeDouble(droneSpeed)
        buffer.writeBoolean(droneAllowPhoto)
        buffer.writeBoolean(droneAllowFluids)
        val dims = droneBlockedDimensions.take(256)
        buffer.writeVarInt(dims.size)
        for (dim in dims) {
            buffer.writeUtf(dim, 128)
        }
        buffer.writeVarInt(droneEnergyPerSecond)
        buffer.writeBoolean(enableSocial)
    }
}
