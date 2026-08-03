package com.nbp.cobblemon_smartphone.actions

import com.cobblemon.mod.common.CobblemonSounds
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.api.SmartphoneAction
import com.nbp.cobblemon_smartphone.client.gui.GpsScreen
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation

object GpsAction : SmartphoneAction {
    const val UPGRADE_NBT_KEY = "upgrade_gps"

    override val id = "${CobblemonSmartphone.ID}:gps"
    override val texture = ResourceLocation.fromNamespaceAndPath(
        CobblemonSmartphone.ID,
        "textures/gui/buttons/gps.png"
    )
    override val hoverTexture = ResourceLocation.fromNamespaceAndPath(
        CobblemonSmartphone.ID,
        "textures/gui/buttons/gps_hover.png"
    )

    override fun onClick() {
        val player = Minecraft.getInstance().player ?: return
        player.playSound(CobblemonSounds.POKEDEX_CLICK, 0.5f, 1f)
        val color = SmartphoneHelper.contextColor ?: return
        Minecraft.getInstance().setScreen(GpsScreen(color, SmartphoneHelper.contextSmartphone))
    }

    override fun isEnabled(): Boolean {
        if (!CobblemonSmartphone.config.features.enableGps) return false

        val player = Minecraft.getInstance().player ?: return false
        return SmartphoneHelper.satisfiesUpgradeRequirement(player, UPGRADE_NBT_KEY, id)
    }
}
