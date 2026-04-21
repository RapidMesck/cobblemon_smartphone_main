package com.nbp.cobblemon_smartphone.actions

import com.cobblemon.mod.common.CobblemonSounds
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.api.SmartphoneAction
import com.nbp.cobblemon_smartphone.isModLoaded
import com.nbp.cobblemon_smartphone.network.packet.OpenCobblenavPokenavPacket
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack

object OpenCobblenavAction : SmartphoneAction {
    private const val COBBLENAV_NAMESPACE = "cobblenav"
    private const val POKENAV_PATH_PREFIX = "pokenav_item_"
    private const val LEGACY_POKENAV_ITEM = "pokenav_item_old"

    override val id = "${CobblemonSmartphone.ID}:cobblenav_pokenav"
    override val texture = ResourceLocation.fromNamespaceAndPath(CobblemonSmartphone.ID, "textures/gui/buttons/pokenav.png")
    override val hoverTexture = ResourceLocation.fromNamespaceAndPath(CobblemonSmartphone.ID, "textures/gui/buttons/pokenav_hover.png")

    override fun onClick() {
        val player = Minecraft.getInstance().player ?: return
        player.playSound(CobblemonSounds.POKEDEX_CLICK, 0.5f, 1f)
        OpenCobblenavPokenavPacket().sendToServer()
        Minecraft.getInstance().setScreen(null)
    }

    override fun isEnabled(): Boolean {
        if (!isModLoaded(COBBLENAV_NAMESPACE)) {
            return false
        }

        val player = Minecraft.getInstance().player ?: return false
        return player.inventory.items.any(::isCobblenavPokenav) || isCobblenavPokenav(player.offhandItem)
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