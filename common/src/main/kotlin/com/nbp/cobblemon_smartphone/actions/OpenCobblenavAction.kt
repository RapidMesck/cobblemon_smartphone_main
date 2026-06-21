package com.nbp.cobblemon_smartphone.actions

import com.cobblemon.mod.common.CobblemonSounds
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.api.SmartphoneAction
import com.nbp.cobblemon_smartphone.isModLoaded
import com.nbp.cobblemon_smartphone.network.packet.OpenCobblenavPokenavPacket
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation

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
        if (!CobblemonSmartphone.config.features.enablePokenav) {
            return false
        }

        if (!isModLoaded(COBBLENAV_NAMESPACE)) {
            return false
        }

        val player = Minecraft.getInstance().player ?: return false
        return SmartphoneHelper.satisfiesUpgradeRequirement(player, "upgrade_pokenav")
    }
}
