package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.nbp.cobblemon_smartphone.item.SmartphoneItem
import com.nbp.cobblemon_smartphone.network.packet.SaveSkinPacket
import com.nbp.cobblemon_smartphone.skin.SmartphoneSkin
import com.nbp.cobblemon_smartphone.skin.SmartphoneSkinRegistry
import com.nbp.cobblemon_smartphone.skin.isSkinUnlocked
import com.nbp.cobblemon_smartphone.skin.setSkinId
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack

object SaveSkinHandler : ServerNetworkPacketHandler<SaveSkinPacket> {
    override fun handle(packet: SaveSkinPacket, server: MinecraftServer, player: ServerPlayer) {
        server.execute {
            val rawId = packet.skinId.trim()
            val isDefault = rawId.isEmpty() || rawId == SmartphoneSkin.DEFAULT_ID
            if (!isDefault && ResourceLocation.tryParse(rawId) == null) {
                return@execute
            }

            // If defined in a server datapack, enforce unlock/upgrade requirements.
            // If not in datapackSkins, it may be a client-side Resource Pack skin on a dedicated server.
            val datapackSkin = if (isDefault) null else SmartphoneSkinRegistry.getDatapackSkin(rawId)
            val targetSkinId = if (isDefault) SmartphoneSkin.DEFAULT_ID else rawId

            fun canApply(stack: ItemStack): Boolean {
                if (stack.item !is SmartphoneItem) return false
                return datapackSkin == null || stack.isSkinUnlocked(datapackSkin)
            }

            var modified = false

            val mainHand = player.mainHandItem
            if (canApply(mainHand)) {
                mainHand.setSkinId(targetSkinId)
                modified = true
            }

            val offHand = player.offhandItem
            if (canApply(offHand)) {
                offHand.setSkinId(targetSkinId)
                modified = true
            }

            for (stack in player.inventory.items) {
                if (canApply(stack)) {
                    stack.setSkinId(targetSkinId)
                    modified = true
                }
            }

            val compatPhone = SmartphoneHelper.getSmartphoneImpl?.invoke(player)
            if (compatPhone != null && canApply(compatPhone)) {
                compatPhone.setSkinId(targetSkinId)
                modified = true
            }

            if (modified) {
                player.inventoryMenu.broadcastChanges()
                player.containerMenu.broadcastChanges()
            }
        }
    }
}
