package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.nbp.cobblemon_smartphone.client.gui.AppStoreScreen
import com.nbp.cobblemon_smartphone.client.gui.SmartphoneSettingsScreen
import com.nbp.cobblemon_smartphone.network.packet.InstallUpgradeResultPacket
import net.minecraft.client.Minecraft

object InstallUpgradeResultHandler : ClientNetworkPacketHandler<InstallUpgradeResultPacket> {
    override fun handle(packet: InstallUpgradeResultPacket, client: Minecraft) {
        client.execute {
            when (val screen = client.screen) {
                is AppStoreScreen -> screen.onInstallResult(packet.upgradeKey, packet.success, packet.messageKey)
                is SmartphoneSettingsScreen -> screen.onBatteryInstallResult(
                    packet.upgradeKey,
                    packet.success,
                    packet.messageKey,
                    packet.addedCapacity
                )
            }
        }
    }
}
