package com.nbp.cobblemon_smartphone.actions

import com.cobblemon.mod.common.CobblemonSounds
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.api.SmartphoneAction
import com.nbp.cobblemon_smartphone.network.packet.OpenWaystonesWarpStonePacket
import net.minecraft.client.Minecraft
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack

object OpenWaystonesAction : SmartphoneAction {
    private const val WAYSTONES_MOD_ID = "waystones"
    private const val WARP_STONE_SUFFIX = "warp_stone"

    override val id = "${CobblemonSmartphone.ID}:waystones_warp_stone"
    override val texture = ResourceLocation.fromNamespaceAndPath(CobblemonSmartphone.ID, "textures/gui/buttons/waystone.png")
    override val hoverTexture = ResourceLocation.fromNamespaceAndPath(CobblemonSmartphone.ID, "textures/gui/buttons/waystone_hover.png")

    override fun onClick() {
        val player = Minecraft.getInstance().player ?: return
        player.playSound(CobblemonSounds.POKEDEX_CLICK, 0.5f, 1f)
        OpenWaystonesWarpStonePacket().sendToServer()
        Minecraft.getInstance().setScreen(null)
    }

    override fun isEnabled(): Boolean {
        if (!CobblemonSmartphone.config.features.enableWaystone) {
            return false
        }

        if (!isWaystonesLoaded()) {
            return false
        }

        val player = Minecraft.getInstance().player ?: return false
        return player.inventory.items.any(::isWarpStone) || isWarpStone(player.offhandItem)
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
}
