package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.nbp.cobblemon_smartphone.isModLoaded
import com.nbp.cobblemon_smartphone.network.packet.OpenCobblenavPokenavPacket
import com.nbp.cobblemon_smartphone.upgrade.SimulatedItemUse
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack

object OpenCobblenavPokenavHandler : ServerNetworkPacketHandler<OpenCobblenavPokenavPacket> {
    private const val ACTION_ID = "cobblemon_smartphone:cobblenav_pokenav"
    private const val COBBLENAV_NAMESPACE = "cobblenav"
    private const val POKENAV_PATH_PREFIX = "pokenav_item_"
    private const val LEGACY_POKENAV_ITEM = "pokenav_item_old"

    override fun handle(packet: OpenCobblenavPokenavPacket, server: MinecraftServer, player: ServerPlayer) {
        server.execute {
            if (!isModLoaded(COBBLENAV_NAMESPACE)) {
                player.displayClientMessage(Component.translatable("message.nbp.cobblenav.unavailable").withColor(0xfd0100), true)
                return@execute
            }

            // Check if ANY smartphone in the player's possession has the PokeNav upgrade
            if (!SmartphoneHelper.hasUpgradeOnAnySmartphone(player, "upgrade_pokenav", ACTION_ID)) {
                player.displayClientMessage(Component.translatable("message.nbp.cobblenav.no_pokenav_upgrade").withColor(0xfd0100), true)
                return@execute
            }

            // Try to use pokenav from inventory (backward compat)
            val pokenavStack = findPokenavStack(player)
            if (pokenavStack != null) {
                pokenavStack.item.use(player.level(), player, InteractionHand.MAIN_HAND)
                return@execute
            }

            // Fallback: simulate pokenav use via smartphone upgrade
            if (SimulatedItemUse.usePokenav(player)) {
                return@execute
            }

            // Last resort: try command fallback
            try {
                server.commands.performPrefixedCommand(
                    player.createCommandSourceStack(),
                    "cobblenav"
                )
            } catch (_: Exception) {
                player.displayClientMessage(Component.translatable("message.nbp.cobblenav.open_failed").withColor(0xfd0100), true)
            }
        }
    }

    private fun findPokenavStack(player: ServerPlayer): ItemStack? {
        val inventoryStack = player.inventory.items.firstOrNull(::isCobblenavPokenav)
        if (inventoryStack != null) {
            return inventoryStack
        }

        return player.offhandItem.takeIf(::isCobblenavPokenav)
    }

    private fun isCobblenavPokenav(stack: ItemStack): Boolean {
        if (stack.isEmpty) {
            return false
        }

        val itemId = BuiltInRegistries.ITEM.getKey(stack.item)
        return itemId.namespace == COBBLENAV_NAMESPACE
            && itemId.path.startsWith(POKENAV_PATH_PREFIX)
            && itemId.path != LEGACY_POKENAV_ITEM
    }
}
