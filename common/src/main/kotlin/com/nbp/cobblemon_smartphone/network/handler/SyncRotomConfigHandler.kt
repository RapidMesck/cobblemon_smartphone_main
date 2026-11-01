package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.nbp.cobblemon_smartphone.client.RotomScannerClientState
import com.nbp.cobblemon_smartphone.network.packet.SyncRotomConfigPacket
import net.minecraft.client.Minecraft

object SyncRotomConfigHandler : ClientNetworkPacketHandler<SyncRotomConfigPacket> {
    override fun handle(packet: SyncRotomConfigPacket, client: Minecraft) {
        client.execute {
            RotomScannerClientState.updateConfig(packet)
            val rotom = com.nbp.cobblemon_smartphone.CobblemonSmartphone.config.rotom
            rotom.enableAutoScanner = packet.enableAutoScanner
            rotom.enableManualScanPriority = packet.enableManualScanPriority
            rotom.enableSocialInteractions = packet.enableSocialInteractions
            rotom.allowScanAlreadyRegistered = packet.allowScanAlreadyRegistered
            rotom.requireLineOfSight = packet.requireLineOfSight
            rotom.revealIvPercentage = packet.revealIvPercentage
            rotom.revealNature = packet.revealNature
            rotom.scanRadius = packet.scanRadius
            rotom.scanDurationTicks = packet.scanDurationTicks
            rotom.approachTicks = packet.approachTicks
            rotom.returnTicks = packet.returnTicks
            rotom.infiniteEnergy = packet.infiniteEnergy
            rotom.curiosityLanguage = packet.curiosityLanguage
        }
    }
}
