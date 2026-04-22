package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.network.packet.OpenWaystonesWarpStonePacket
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import java.util.UUID

object OpenWaystonesWarpStoneHandler : ServerNetworkPacketHandler<OpenWaystonesWarpStonePacket> {
    private const val WAYSTONES_MOD_ID = "waystones"
    private const val WARP_STONE_SUFFIX = "warp_stone"

    private val buttonCooldowns = mutableMapOf<UUID, Long>()

    override fun handle(packet: OpenWaystonesWarpStonePacket, server: MinecraftServer, player: ServerPlayer) {
        server.execute {
            if (!CobblemonSmartphone.config.features.enableWaystone) {
                player.displayClientMessage(Component.translatable("message.nbp.waystones.disabled").withColor(0xfd0100), true)
                return@execute
            }

            if (!isWaystonesLoaded()) {
                player.displayClientMessage(Component.translatable("message.nbp.waystones.unavailable").withColor(0xfd0100), true)
                return@execute
            }

            val cooldown = CobblemonSmartphone.config.cooldowns.waystoneButton
            val playerId = player.uuid
            val now = System.currentTimeMillis()
            val lastClick = buttonCooldowns[playerId] ?: 0L

            if (now - lastClick < cooldown * 1000L) {
                val remaining = ((cooldown * 1000L - (now - lastClick)) / 1000L).toInt() + 1
                player.displayClientMessage(Component.translatable("message.nbp.waystones.cooldown", remaining).withColor(0xfd0100), true)
                return@execute
            }

            val warpStoneUse = findWarpStoneForUse(player)
            if (warpStoneUse == null) {
                player.displayClientMessage(Component.translatable("message.nbp.waystones.no_warp_stone_item").withColor(0xfd0100), true)
                return@execute
            }

            buttonCooldowns[playerId] = now

            try {
                player.startUsingItem(warpStoneUse.hand)
                warpStoneUse.stack.item.finishUsingItem(warpStoneUse.stack, player.level(), player)
            } catch (_: Exception) {
                player.displayClientMessage(Component.translatable("message.nbp.waystones.open_failed").withColor(0xfd0100), true)
            }
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
