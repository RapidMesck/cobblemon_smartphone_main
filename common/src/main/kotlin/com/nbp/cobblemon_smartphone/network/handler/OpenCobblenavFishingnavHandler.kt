package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.isModLoaded
import com.nbp.cobblemon_smartphone.network.packet.OpenCobblenavFishingnavPacket
import com.nbp.cobblemon_smartphone.upgrade.SimulatedItemUse
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack

object OpenCobblenavFishingnavHandler : ServerNetworkPacketHandler<OpenCobblenavFishingnavPacket> {
    private const val ACTION_ID = "cobblemon_smartphone:cobblenav_fishingnav"
    private const val COBBLENAV_NAMESPACE = "cobblenav"
    private const val FISHINGNAV_ITEM = "fishingnav_item"

    override fun handle(packet: OpenCobblenavFishingnavPacket, server: MinecraftServer, player: ServerPlayer) {
        server.execute {
            execute(server, player, isNativeAction = true)
        }
    }

    fun execute(server: MinecraftServer, player: ServerPlayer, isNativeAction: Boolean) {
        // Feature toggle only applies to the native smartphone action;
        // datapack actions are independent and manage their own requirements.
        if (isNativeAction && !CobblemonSmartphone.config.features.enableFishingnav) {
            player.displayClientMessage(
                Component.translatable("message.nbp.fishingnav.disabled").withColor(0xfd0100),
                true
            )
            return
        }

        if (!isModLoaded(COBBLENAV_NAMESPACE)) {
            player.displayClientMessage(
                Component.translatable("message.nbp.fishingnav.unavailable").withColor(0xfd0100),
                true
            )
            return
        }

        // Check if ANY smartphone in the player's possession has the FishingNav upgrade
        if (!SmartphoneHelper.hasUpgradeOnAnySmartphone(player, "upgrade_fishingnav", ACTION_ID)) {
            player.displayClientMessage(
                Component.translatable("message.nbp.fishingnav.no_fishingnav_upgrade").withColor(0xfd0100), true
            )
            return
        }

        // Try to use fishingnav from inventory (backward compat)
        val fishingnavStack = findFishingnavStack(player)
        if (fishingnavStack != null) {
            fishingnavStack.item.use(player.level(), player, InteractionHand.MAIN_HAND)
            return
        }

        // Fallback: simulate fishingnav use via smartphone upgrade
        if (SimulatedItemUse.useFishingnav(player)) {
            return
        }

        player.displayClientMessage(
            Component.translatable("message.nbp.fishingnav.open_failed").withColor(0xfd0100),
            true
        )
    }

    private fun findFishingnavStack(player: ServerPlayer): ItemStack? {
        val inventoryStack = player.inventory.items.firstOrNull(::isCobblenavFishingnav)
        if (inventoryStack != null) {
            return inventoryStack
        }

        return player.offhandItem.takeIf(::isCobblenavFishingnav)
    }

    private fun isCobblenavFishingnav(stack: ItemStack): Boolean {
        if (stack.isEmpty) {
            return false
        }

        val itemId = BuiltInRegistries.ITEM.getKey(stack.item)
        return itemId.namespace == COBBLENAV_NAMESPACE && itemId.path == FISHINGNAV_ITEM
    }
}
