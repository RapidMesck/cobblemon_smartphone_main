package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.network.packet.OpenWaystonesWarpStonePacket
import com.nbp.cobblemon_smartphone.upgrade.SimulatedItemUse
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import java.util.UUID

object OpenWaystonesWarpStoneHandler : ServerNetworkPacketHandler<OpenWaystonesWarpStonePacket> {
    private const val ACTION_ID = "cobblemon_smartphone:waystones_warp_stone"
    private const val WAYSTONES_MOD_ID = "waystones"
    private const val WARP_STONE_SUFFIX = "warp_stone"

    private val buttonCooldowns = mutableMapOf<UUID, Long>()

    override fun handle(packet: OpenWaystonesWarpStonePacket, server: MinecraftServer, player: ServerPlayer) {
        server.execute {
            execute(server, player, isNativeAction = true)
        }
    }

    fun execute(server: MinecraftServer, player: ServerPlayer, isNativeAction: Boolean) {
        // Feature toggle and cooldown only apply to the native smartphone action;
        // datapack actions are independent and manage their own cooldown/requirements.
        if (isNativeAction && !CobblemonSmartphone.config.features.enableWaystone) {
            player.displayClientMessage(
                Component.translatable("message.nbp.waystones.disabled").withColor(0xfd0100),
                true
            )
            return
        }

        if (!isWaystonesLoaded()) {
            player.displayClientMessage(
                Component.translatable("message.nbp.waystones.unavailable").withColor(0xfd0100),
                true
            )
            return
        }

        if (isNativeAction) {
            val cooldown = CobblemonSmartphone.config.cooldowns.waystoneButton
            val now = System.currentTimeMillis()
            val lastClick = buttonCooldowns[player.uuid] ?: 0L

            if (now - lastClick < cooldown * 1000L) {
                val remaining = ((cooldown * 1000L - (now - lastClick)) / 1000L).toInt() + 1
                player.displayClientMessage(
                    Component.translatable("message.nbp.waystones.cooldown", remaining).withColor(0xfd0100),
                    true
                )
                return
            }
        }

        if (!SmartphoneHelper.hasUpgradeOnAnySmartphone(player, "upgrade_waystone", ACTION_ID)) {
            player.displayClientMessage(
                Component.translatable("message.nbp.waystones.no_waystone_upgrade").withColor(0xfd0100),
                true
            )
            return
        }

        if (isNativeAction) {
            buttonCooldowns[player.uuid] = System.currentTimeMillis()
        }

        val warpStoneUse = findWarpStoneForUse(player)
        if (warpStoneUse != null) {
            try {
                player.startUsingItem(warpStoneUse.hand)
                warpStoneUse.stack.item.finishUsingItem(warpStoneUse.stack, player.level(), player)
            } catch (_: Exception) {
                player.displayClientMessage(
                    Component.translatable("message.nbp.waystones.open_failed").withColor(0xfd0100),
                    true
                )
            }
            return
        }

        if (SimulatedItemUse.useWaystone(player)) {
            return
        }

        try {
            server.commands.performPrefixedCommand(
                player.createCommandSourceStack(),
                "waystones"
            )
        } catch (_: Exception) {
            player.displayClientMessage(
                Component.translatable("message.nbp.waystones.open_failed").withColor(0xfd0100),
                true
            )
        }
    }

    private fun findWarpStoneForUse(player: ServerPlayer): WarpStoneUse? {
        val mainHand = player.mainHandItem
        if (isWarpStone(mainHand)) {
            return WarpStoneUse(mainHand, InteractionHand.MAIN_HAND)
        }

        val offHand = player.offhandItem
        if (isWarpStone(offHand)) {
            return WarpStoneUse(offHand, InteractionHand.OFF_HAND)
        }

        val inventoryStack = player.inventory.items.firstOrNull(::isWarpStone)
        if (inventoryStack != null) {
            return WarpStoneUse(inventoryStack, InteractionHand.MAIN_HAND)
        }

        return null
    }

    private fun isWarpStone(stack: ItemStack): Boolean {
        if (stack.isEmpty) {
            return false
        }

        val itemId = BuiltInRegistries.ITEM.getKey(stack.item)
        return itemId.namespace == WAYSTONES_MOD_ID && itemId.path.endsWith(WARP_STONE_SUFFIX)
    }

    private fun isWaystonesLoaded(): Boolean {
        return BuiltInRegistries.ITEM.keySet().any { it.namespace == WAYSTONES_MOD_ID }
    }

    private data class WarpStoneUse(val stack: ItemStack, val hand: InteractionHand)
}
