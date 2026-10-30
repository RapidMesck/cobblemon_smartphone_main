package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.nbp.cobblemon_smartphone.item.SmartphoneItem
import com.nbp.cobblemon_smartphone.network.packet.SaveThemePacket
import com.nbp.cobblemon_smartphone.network.packet.SyncThemePacket
import com.nbp.cobblemon_smartphone.theme.setTheme
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper
import com.nbp.cobblemon_smartphone.util.ThemeStorage
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object SaveThemeHandler : ServerNetworkPacketHandler<SaveThemePacket> {
    override fun handle(packet: SaveThemePacket, server: MinecraftServer, player: ServerPlayer) {
        server.execute {
            // Persist in player entity preferences so it survives across sessions
            ThemeStorage.write(player, packet.themeId)

            var modified = false

            // Update held item if it is a smartphone
            val mainHand = player.mainHandItem
            if (mainHand.item is SmartphoneItem) {
                mainHand.setTheme(packet.themeId)
                modified = true
            }

            val offHand = player.offhandItem
            if (offHand.item is SmartphoneItem) {
                offHand.setTheme(packet.themeId)
                modified = true
            }

            // Update all smartphones in inventory
            for (stack in player.inventory.items) {
                if (stack.item is SmartphoneItem) {
                    stack.setTheme(packet.themeId)
                    modified = true
                }
            }

            // Check compat slots (Trinkets/Curios)
            val compatPhone = SmartphoneHelper.getSmartphoneImpl?.invoke(player)
            if (compatPhone != null && compatPhone.item is SmartphoneItem) {
                compatPhone.setTheme(packet.themeId)
                modified = true
            }

            if (modified) {
                player.inventoryMenu.broadcastChanges()
                player.containerMenu.broadcastChanges()
            }

            // Sync back to client to update client cache
            SyncThemePacket(packet.themeId).sendToPlayer(player)
        }
    }
}
